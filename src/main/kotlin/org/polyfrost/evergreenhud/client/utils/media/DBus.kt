package org.polyfrost.evergreenhud.client.utils.media

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path

class DBusException(message: String) : IOException(message)

class DBusMessage(
    val type: Int,
    val serial: Int,
    val replySerial: Int,
    val member: String?,
    val error: String?,
    val signature: String,
    val body: List<Any?>,
)

class DBusConnection private constructor(private val channel: SocketChannel) : Closeable {
    private val selector = Selector.open().also { channel.register(it, SelectionKey.OP_READ) }
    private var serial = 0

    var onSignal: (DBusMessage) -> Unit = {}

    @Synchronized
    fun call(
        destination: String,
        path: String,
        iface: String,
        member: String,
        vararg args: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): List<Any?> {
        val id = send(destination, path, iface, member, args)
        val deadline = System.nanoTime() + timeoutMs * 1_000_000L
        while (true) {
            val message = read(deadline)
            when {
                message.type == METHOD_RETURN && message.replySerial == id -> return message.body
                message.type == ERROR && message.replySerial == id ->
                    throw DBusException("${message.error}: ${message.body.firstOrNull()}")
                message.type == SIGNAL -> onSignal(message)
            }
        }
    }

    @Synchronized
    fun drain() {
        while (selector.selectNow() > 0) {
            selector.selectedKeys().clear()
            val message = read(System.nanoTime() + DEFAULT_TIMEOUT_MS * 1_000_000L)
            if (message.type == SIGNAL) onSignal(message)
        }
    }

    override fun close() {
        runCatching { selector.close() }
        runCatching { channel.close() }
    }

    private fun send(destination: String, path: String, iface: String, member: String, args: Array<out String>): Int {
        val id = ++serial
        val body = Writer().apply { args.forEach(::string) }.bytes()
        val fields = Writer().apply {
            field(FIELD_PATH, 'o', path)
            field(FIELD_INTERFACE, 's', iface)
            field(FIELD_MEMBER, 's', member)
            field(FIELD_DESTINATION, 's', destination)
            if (args.isNotEmpty()) {
                align(8)
                byte(FIELD_SIGNATURE)
                byte(1)
                byte('g'.code)
                byte(0)
                byte(args.size)
                args.forEach { _ -> byte('s'.code) }
                byte(0)
            }
        }.bytes()

        val header = Writer().apply {
            byte('l'.code)
            byte(METHOD_CALL)
            byte(0)
            byte(1)
            int(body.size)
            int(id)
            int(fields.size)
            raw(fields)
            align(8)
        }.bytes()

        write(ByteBuffer.wrap(header + body))
        return id
    }

    private fun write(buffer: ByteBuffer) {
        val deadline = System.nanoTime() + DEFAULT_TIMEOUT_MS * 1_000_000L
        while (buffer.hasRemaining()) {
            if (channel.write(buffer) == 0) {
                if (System.nanoTime() > deadline) throw DBusException("write timed out")
                Thread.sleep(1)
            }
        }
    }

    private fun read(deadline: Long): DBusMessage {
        val fixed = readBytes(16, deadline)
        val order = if (fixed[0] == 'l'.code.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
        val head = ByteBuffer.wrap(fixed).order(order)
        val bodyLength = head.getInt(4)
        val fieldsLength = head.getInt(12)
        if (bodyLength < 0 || bodyLength > MAX_MESSAGE || fieldsLength < 0 || fieldsLength > MAX_MESSAGE) {
            throw DBusException("bad message length")
        }
        val bodyStart = (16 + fieldsLength + 7) and 7.inv()
        return decode(fixed + readBytes(bodyStart + bodyLength - 16, deadline))
    }

    private fun readBytes(count: Int, deadline: Long): ByteArray {
        val buffer = ByteBuffer.allocate(count)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) throw DBusException("connection closed")
            if (!buffer.hasRemaining()) break
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) throw DBusException("timed out")
            selector.select((remaining / 1_000_000L).coerceAtLeast(1L))
            selector.selectedKeys().clear()
        }
        return buffer.array()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 2000L
        private const val MAX_MESSAGE = 16 * 1024 * 1024

        const val METHOD_CALL = 1
        const val METHOD_RETURN = 2
        const val ERROR = 3
        const val SIGNAL = 4

        private const val FIELD_PATH = 1
        private const val FIELD_INTERFACE = 2
        private const val FIELD_MEMBER = 3
        private const val FIELD_ERROR_NAME = 4
        private const val FIELD_REPLY_SERIAL = 5
        private const val FIELD_DESTINATION = 6
        private const val FIELD_SIGNATURE = 8

        fun decode(whole: ByteArray): DBusMessage {
            val order = if (whole[0] == 'l'.code.toByte()) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN
            val head = ByteBuffer.wrap(whole).order(order)
            val bodyStart = (16 + head.getInt(12) + 7) and 7.inv()
            val reader = Reader(ByteBuffer.wrap(whole).order(order))
            reader.position = 12
            var replySerial = 0
            var member: String? = null
            var error: String? = null
            var signature = ""
            @Suppress("UNCHECKED_CAST")
            for (field in reader.read("a(yv)") as List<List<Any?>>) {
                when ((field[0] as Number).toInt()) {
                    FIELD_ERROR_NAME -> error = field[1] as String
                    FIELD_REPLY_SERIAL -> replySerial = (field[1] as Number).toInt()
                    FIELD_MEMBER -> member = field[1] as String
                    FIELD_SIGNATURE -> signature = field[1] as String
                }
            }
            reader.position = bodyStart
            return DBusMessage(
                type = head.get(1).toInt(),
                serial = head.getInt(8),
                replySerial = replySerial,
                member = member,
                error = error,
                signature = signature,
                body = reader.readAll(signature),
            )
        }

        fun session(): DBusConnection {
            val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
            try {
                channel.connect(UnixDomainSocketAddress.of(sessionBusPath()))
                authenticate(channel)
                channel.configureBlocking(false)
                return DBusConnection(channel).also { it.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "Hello") }
            } catch (e: Throwable) {
                runCatching { channel.close() }
                throw e
            }
        }

        private fun sessionBusPath(): Path {
            val address = System.getenv("DBUS_SESSION_BUS_ADDRESS").orEmpty()
            address.split(';')
                .firstOrNull { it.startsWith("unix:") }
                ?.removePrefix("unix:")
                ?.split(',')
                ?.firstOrNull { it.startsWith("path=") }
                ?.let { return Path.of(it.removePrefix("path=")) }

            val runtime = System.getenv("XDG_RUNTIME_DIR") ?: "/run/user/${uid()}"
            return Path.of(runtime, "bus")
        }

        private fun uid(): Int = Files.getAttribute(Path.of("/proc/self"), "unix:uid") as Int

        private fun authenticate(channel: SocketChannel) {
            val hexUid = uid().toString().toByteArray().joinToString("") { "%02x".format(it) }
            channel.write(ByteBuffer.wrap("\u0000AUTH EXTERNAL $hexUid\r\n".toByteArray()))

            val line = StringBuilder()
            val one = ByteBuffer.allocate(1)
            while (!line.endsWith("\r\n")) {
                one.clear()
                if (channel.read(one) <= 0) throw DBusException("auth failed")
                line.append(Char(one[0].toInt()))
                if (line.length > 512) throw DBusException("auth failed")
            }
            if (!line.startsWith("OK")) throw DBusException("auth rejected: ${line.trim()}")
            channel.write(ByteBuffer.wrap("BEGIN\r\n".toByteArray()))
        }
    }
}

private class Writer {
    private val out = ByteArrayOutputStream()

    fun bytes(): ByteArray = out.toByteArray()

    fun align(n: Int) {
        while (out.size() % n != 0) out.write(0)
    }

    fun byte(value: Int) = out.write(value)

    fun raw(bytes: ByteArray) = out.write(bytes)

    fun int(value: Int) {
        align(4)
        for (shift in 0..24 step 8) out.write(value shr shift and 0xFF)
    }

    fun string(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        int(bytes.size)
        out.write(bytes)
        out.write(0)
    }

    fun field(code: Int, type: Char, value: String) {
        align(8)
        byte(code)
        byte(1)
        byte(type.code)
        byte(0)
        string(value)
    }
}

class Reader(private val buffer: ByteBuffer) {
    var position: Int
        get() = buffer.position()
        set(value) {
            buffer.position(value)
        }

    fun readAll(signature: String): List<Any?> {
        val values = ArrayList<Any?>()
        var i = 0
        while (i < signature.length) {
            val end = typeEnd(signature, i)
            values += read(signature.substring(i, end))
            i = end
        }
        return values
    }

    fun read(type: String): Any? = when (type[0]) {
        'y' -> buffer.get().toInt() and 0xFF
        'b' -> uint() != 0L
        'n', 'q' -> {
            align(2)
            buffer.short.toInt()
        }
        'i' -> {
            align(4)
            buffer.int
        }
        'u', 'h' -> uint()
        'x', 't' -> {
            align(8)
            buffer.long
        }
        'd' -> {
            align(8)
            buffer.double
        }
        's', 'o' -> string(uint().toInt())
        'g' -> string(buffer.get().toInt() and 0xFF)
        'v' -> read(signature())
        'a' -> array(type.substring(1))
        '(', '{' -> {
            align(8)
            val fields = ArrayList<Any?>()
            var i = 1
            while (i < type.length - 1) {
                val end = typeEnd(type, i)
                fields += read(type.substring(i, end))
                i = end
            }
            fields
        }
        else -> throw DBusException("unsupported type $type")
    }

    private fun array(element: String): Any {
        val length = uint().toInt()
        align(alignment(element[0]))
        val end = buffer.position() + length
        if (element[0] == '{') {
            val map = LinkedHashMap<Any?, Any?>()
            while (buffer.position() < end) {
                val entry = read(element) as List<*>
                map[entry[0]] = entry[1]
            }
            return map
        }
        val list = ArrayList<Any?>()
        while (buffer.position() < end) list += read(element)
        return list
    }

    private fun signature(): String = string(buffer.get().toInt() and 0xFF)

    private fun string(length: Int): String {
        if (length < 0 || length > buffer.remaining()) throw DBusException("bad string length")
        val bytes = ByteArray(length)
        buffer.get(bytes)
        buffer.get()
        return String(bytes, Charsets.UTF_8)
    }

    private fun uint(): Long {
        align(4)
        return buffer.int.toLong() and 0xFFFFFFFFL
    }

    private fun align(n: Int) {
        buffer.position((buffer.position() + n - 1) and (n - 1).inv())
    }

    private fun alignment(type: Char): Int = when (type) {
        'y', 'g', 'v' -> 1
        'n', 'q' -> 2
        'x', 't', 'd', '(', '{' -> 8
        else -> 4
    }

    private fun typeEnd(signature: String, start: Int): Int = when (signature[start]) {
        'a' -> typeEnd(signature, start + 1)
        '(', '{' -> {
            var i = start + 1
            while (signature[i] != ')' && signature[i] != '}') i = typeEnd(signature, i)
            i + 1
        }
        else -> start + 1
    }
}
