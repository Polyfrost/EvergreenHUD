package org.polyfrost.evergreenhud.client.utils.media

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.io.ByteArrayOutputStream

object WindowsMedia : MediaBackend {
    private const val MANAGER_CLASS = "Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager"
    private const val EPOCH_DELTA_MS = 11_644_473_600_000L
    private const val TICKS_PER_MS = 10_000L
    private const val MAX_ART_BYTES = 4 * 1024 * 1024
    private const val ART_ATTEMPTS = 5
    private const val CHUNK = 64 * 1024
    private const val ASYNC_TIMEOUT_MS = 2000L

    private val IID_MANAGER_STATICS = guid("2050c4ee-11a0-57de-aed7-c97c70338245")
    private val IID_ASYNC_INFO = guid("00000036-0000-0000-c000-000000000046")
    private val IID_STREAM = guid("0000000c-0000-0000-c000-000000000046")

    private val combase: Combase by lazy { Native.loadLibrary("combase", Combase::class.java) as Combase }
    private val shcore: Shcore by lazy { Native.loadLibrary("shcore", Shcore::class.java) as Shcore }

    private val initialized = ThreadLocal<Boolean>()
    private var manager: Com? = null
    private var lastActive: String? = null
    private val art = HashMap<String, CachedArt>()

    private class CachedArt(val key: String, val bytes: ByteArray?, val attempts: Int)

    private interface Combase : Library {
        fun RoInitialize(initType: Int): Int
        fun RoGetActivationFactory(classId: Pointer, iid: Pointer, factory: PointerByReference): Int
        fun WindowsCreateString(source: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer?): Int
        fun WindowsGetStringRawBuffer(string: Pointer, length: IntByReference?): Pointer?
    }

    private interface Shcore : Library {
        fun CreateStreamOverRandomAccessStream(stream: Pointer, iid: Pointer, result: PointerByReference): Int
    }

    private class Com(val pointer: Pointer) : AutoCloseable {
        private fun slot(index: Int): Function =
            Function.getFunction(pointer.getPointer(0).getPointer(index.toLong() * Native.POINTER_SIZE))

        fun call(index: Int, vararg args: Any?): Int = slot(index).invokeInt(arrayOf<Any?>(pointer, *args))

        fun child(index: Int, vararg args: Any?): Com? {
            val result = PointerByReference()
            if (call(index, *args, result) < 0) return null
            return result.value?.let(::Com)
        }

        fun string(index: Int): String {
            val result = PointerByReference()
            if (call(index, result) < 0) return ""
            val handle = result.value ?: return ""
            return try {
                val length = IntByReference()
                val buffer = combase.WindowsGetStringRawBuffer(handle, length)
                if (buffer == null) "" else String(buffer.getCharArray(0, length.value))
            } finally {
                combase.WindowsDeleteString(handle)
            }
        }

        fun long(index: Int): Long {
            val value = Memory(8)
            return if (call(index, value) >= 0) value.getLong(0) else 0L
        }

        fun bool(index: Int): Boolean {
            val value = Memory(1)
            return call(index, value) >= 0 && value.getByte(0).toInt() != 0
        }

        fun int(index: Int): Int {
            val value = Memory(4)
            return if (call(index, value) >= 0) value.getInt(0) else 0
        }

        fun queryInterface(iid: Memory): Com? {
            val result = PointerByReference()
            if (call(0, iid, result) < 0) return null
            return result.value?.let(::Com)
        }

        fun await(): Com? = awaited { child(8) }

        fun awaitBool(): Boolean = awaited { bool(8) } == true

        private fun <T> awaited(results: () -> T?): T? {
            val info = queryInterface(IID_ASYNC_INFO) ?: return null
            val status = info.use {
                val deadline = System.currentTimeMillis() + ASYNC_TIMEOUT_MS
                var current = it.int(7)
                while (current == 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(2)
                    current = it.int(7)
                }
                current
            }
            return if (status == 1) results() else null
        }

        override fun close() {
            call(2)
        }
    }

    fun isAvailable(): Boolean {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
        val version = System.getProperty("os.version").orEmpty().split('.')
        return (version.getOrNull(0)?.toIntOrNull() ?: 0) >= 10
    }

    @Synchronized
    override fun poll(): MediaInfo? {
        val sessions = runCatching { sessions() }.getOrNull() ?: return null
        val chosen = sessions.pickActive(lastActive)
        if (chosen?.playing == true) lastActive = chosen.player
        return chosen
    }

    @Synchronized
    override fun send(player: String, command: MediaCommand) {
        val slot = when (command) {
            MediaCommand.Previous -> 17
            MediaCommand.Toggle -> 20
            MediaCommand.Next -> 16
        }
        runCatching {
            forEachSession { session ->
                if (session.string(6) == player) session.child(slot)?.use { it.awaitBool() }
            }
        }
    }

    @Synchronized
    override fun close() {
        manager?.close()
        manager = null
        art.clear()
    }

    private fun sessions(): List<MediaInfo> {
        val infos = ArrayList<MediaInfo>()
        forEachSession { session -> read(session)?.let(infos::add) }
        art.keys.retainAll(infos.map { it.player }.toSet())
        return infos
    }

    private fun forEachSession(action: (Com) -> Unit) {
        val manager = manager() ?: return
        manager.child(7)?.use { list ->
            repeat(list.int(7)) { index ->
                list.child(6, index)?.use(action)
            }
        }
    }

    private fun read(session: Com): MediaInfo? {
        val id = session.string(6)
        val properties = session.child(7)?.use { it.await() } ?: return null
        val title: String
        val artist: String
        val album: String
        val thumbnail: Com?
        properties.use {
            title = it.string(6)
            artist = it.string(9)
            album = it.string(10)
            thumbnail = it.child(15)
        }

        thumbnail.use {
            if (title.isEmpty() && artist.isEmpty()) return null
            return MediaInfo(
                player = id,
                title = title.ifEmpty { id },
                artist = artist,
                artUrl = null,
                artBytes = artFor(id, "$title|$artist|$album", it),
                playing = false,
                positionMs = 0L,
                lengthMs = 0L,
                canPrevious = false,
                canNext = false,
                canToggle = false,
            ).withPlayback(session)
        }
    }

    private fun MediaInfo.withPlayback(session: Com): MediaInfo {
        var playing = false
        var next = false
        var previous = false
        var toggle = false
        session.child(9)?.use { info ->
            playing = info.int(7) == 4
            info.child(6)?.use { controls ->
                next = controls.bool(12)
                previous = controls.bool(13)
                toggle = controls.bool(16)
            }
        }

        var position = 0L
        var length = 0L
        session.child(8)?.use { timeline ->
            val start = timeline.long(6)
            length = (timeline.long(7) - start) / TICKS_PER_MS
            position = (timeline.long(10) - start) / TICKS_PER_MS
            val updated = timeline.long(11)
            if (playing && updated > 0) {
                val delta = System.currentTimeMillis() - (updated / TICKS_PER_MS - EPOCH_DELTA_MS)
                if (delta in 1 until 86_400_000L) position += delta
            }
        }
        length = length.coerceAtLeast(0L)
        position = if (length > 0) position.coerceIn(0L, length) else position.coerceAtLeast(0L)

        return copy(
            playing = playing,
            positionMs = position,
            lengthMs = length,
            canPrevious = previous,
            canNext = next,
            canToggle = toggle,
            sampledAtMs = System.currentTimeMillis(),
        )
    }

    private fun artFor(id: String, key: String, thumbnail: Com?): ByteArray? {
        val cached = art[id]
        if (cached != null && cached.key == key && (cached.bytes != null || cached.attempts >= ART_ATTEMPTS)) {
            return cached.bytes
        }
        val bytes = thumbnail?.let { runCatching { readThumbnail(it) }.getOrNull() }
        val attempts = if (cached?.key == key) cached.attempts + 1 else 1
        art[id] = CachedArt(key, bytes, attempts)
        return bytes
    }

    private fun readThumbnail(reference: Com): ByteArray? {
        val randomAccess = reference.child(6)?.use { it.await() } ?: return null
        randomAccess.use {
            val result = PointerByReference()
            if (shcore.CreateStreamOverRandomAccessStream(it.pointer, IID_STREAM, result) < 0) return null
            val stream = Com(result.value ?: return null)
            stream.use {
                val out = ByteArrayOutputStream()
                val chunk = Memory(CHUNK.toLong())
                val read = Memory(4)
                while (out.size() < MAX_ART_BYTES) {
                    val hr = stream.call(3, chunk, CHUNK, read)
                    val count = read.getInt(0)
                    if (hr < 0 || count <= 0) break
                    out.write(chunk.getByteArray(0, count))
                }
                return out.toByteArray().takeIf { it.isNotEmpty() }
            }
        }
    }

    private fun manager(): Com? {
        manager?.let { return it }
        if (initialized.get() != true) {
            combase.RoInitialize(1)
            initialized.set(true)
        }
        val className = PointerByReference()
        if (combase.WindowsCreateString(WString(MANAGER_CLASS), MANAGER_CLASS.length, className) < 0) return null
        try {
            val factory = PointerByReference()
            if (combase.RoGetActivationFactory(className.value, IID_MANAGER_STATICS, factory) < 0) return null
            val statics = Com(factory.value ?: return null)
            val created = statics.use { it.child(6)?.use { op -> op.await() } }
            return created?.also { manager = it }
        } finally {
            combase.WindowsDeleteString(className.value)
        }
    }

    private fun guid(text: String): Memory {
        val hex = text.replace("-", "")
        val memory = Memory(16)
        fun int(offset: Int, digits: Int, size: Int, from: Int) {
            val value = hex.substring(from, from + digits).toLong(16)
            when (size) {
                4 -> memory.setInt(offset.toLong(), value.toInt())
                2 -> memory.setShort(offset.toLong(), value.toShort())
            }
        }
        int(0, 8, 4, 0)
        int(4, 4, 2, 8)
        int(6, 4, 2, 12)
        for (i in 0 until 8) memory.setByte(8L + i, hex.substring(16 + i * 2, 18 + i * 2).toInt(16).toByte())
        return memory
    }
}
