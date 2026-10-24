package org.polyfrost.evergreenhud.client.utils.media

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object MacMedia : MediaBackend {
    private const val PERL = "/usr/bin/perl"
    private const val DITTO = "/usr/bin/ditto"
    private const val RESOURCES = "/assets/evergreenhud/media/macos/"
    private const val SCRIPT_RESOURCE = "${RESOURCES}mediaremote-adapter.pl"
    private const val FRAMEWORK_RESOURCE = "${RESOURCES}MediaRemoteAdapter.framework.zip"
    private const val FALLBACK_POLL_MS = 2000L
    private const val BACKOFF_START_MS = 1000L
    private const val BACKOFF_MAX_MS = 60_000L
    private const val HEALTHY_RUN_MS = 300_000L
    private const val MAX_RECORD_CHARS = 12 * 1024 * 1024
    private const val STREAM_GIVE_UP = 3
    private const val COMMAND_TOGGLE = 2
    private const val COMMAND_NEXT = 4
    private const val COMMAND_PREVIOUS = 5

    private class Adapter(val script: File, val framework: File) {
        fun command(vararg args: String) = listOf(PERL, script.path, framework.path) + args
    }

    private val adapter: Adapter? by lazy { runCatching(::locate).getOrNull() }

    @Volatile
    private var payload: JsonObject? = null

    @Volatile
    private var streaming = false

    private var streamThread: Thread? = null
    private var process: Process? = null
    private var failedRuns = 0
    private var fallbackAt = 0L
    private var artSource: String? = null
    private var artBytes: ByteArray? = null

    fun isAvailable(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Mac") && File(PERL).canExecute() && adapter != null

    @Synchronized
    override fun poll(): MediaInfo? {
        if (streamThread == null && failedRuns < STREAM_GIVE_UP) startStream()
        if (failedRuns >= STREAM_GIVE_UP && !streaming) pollOnce()
        return payload?.let { parse(it, System.currentTimeMillis()) }
    }

    override fun send(player: String, command: MediaCommand) {
        val adapter = adapter ?: return
        val id = when (command) {
            MediaCommand.Previous -> COMMAND_PREVIOUS
            MediaCommand.Toggle -> COMMAND_TOGGLE
            MediaCommand.Next -> COMMAND_NEXT
        }
        runProcess(adapter.command("send", id.toString()))
    }

    @Synchronized
    override fun close() {
        val running = streamThread
        streamThread = null
        process?.destroyForcibly()
        process = null
        running?.interrupt()
        streaming = false
        failedRuns = 0
        payload = null
    }

    fun parse(json: JsonObject, nowMs: Long): MediaInfo? {
        val title = json.string("title").orEmpty()
        val artist = json.string("artist").orEmpty()
        if (title.isEmpty() && artist.isEmpty()) return null

        val playing = json["playing"]?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
        val elapsed = json.long("elapsedTimeMicros") / 1000L
        val timestamp = json.long("timestampEpochMicros") / 1000L
        val rate = json["playbackRate"]?.takeIf { it.isJsonPrimitive }?.asDouble ?: 1.0
        val position = if (playing && timestamp > 0L) elapsed + ((nowMs - timestamp).coerceAtLeast(0L) * rate).toLong() else elapsed
        val length = json.long("durationMicros") / 1000L
        val player = json.string("bundleIdentifier") ?: "macos"

        return MediaInfo(
            player = player,
            title = title.ifEmpty { player },
            artist = artist,
            artUrl = null,
            artBytes = artwork(json.string("artworkData")),
            playing = playing,
            positionMs = if (length > 0L) position.coerceIn(0L, length) else position.coerceAtLeast(0L),
            lengthMs = length,
            canPrevious = true,
            canNext = true,
            canToggle = true,
            sampledAtMs = nowMs,
        )
    }

    fun merge(current: JsonObject?, line: String): JsonObject? {
        val record = runCatching { JsonParser.parseString(line) }.getOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            ?: return current
        val update = record["payload"]?.takeIf { it.isJsonObject }?.asJsonObject ?: return current
        val merged = if (record["diff"]?.asBoolean == true && current != null) current.deepCopy() else JsonObject()
        for ((key, value) in update.entrySet()) {
            if (value.isJsonNull) merged.remove(key) else merged.add(key, value)
        }
        return merged
    }

    private fun artwork(base64: String?): ByteArray? {
        if (base64 == null) return null
        if (base64 != artSource) {
            artSource = base64
            artBytes = runCatching { Base64.getMimeDecoder().decode(base64) }.getOrNull()
        }
        return artBytes
    }

    private fun startStream() {
        val adapter = adapter ?: return
        streamThread = thread(isDaemon = true, name = "EvergreenHUD Media Stream") {
            var backoff = BACKOFF_START_MS
            while (!Thread.currentThread().isInterrupted) {
                val started = System.currentTimeMillis()
                runCatching { runStream(adapter) }
                streaming = false
                if (Thread.currentThread().isInterrupted) return@thread
                if (System.currentTimeMillis() - started >= HEALTHY_RUN_MS) {
                    backoff = BACKOFF_START_MS
                    synchronized(this) { failedRuns = 0 }
                } else {
                    synchronized(this) { failedRuns++ }
                }
                if (failedRuns >= STREAM_GIVE_UP) return@thread
                runCatching { Thread.sleep(backoff) }.onFailure { return@thread }
                backoff = (backoff * 2).coerceAtMost(BACKOFF_MAX_MS)
            }
        }
    }

    private fun runStream(adapter: Adapter) {
        val started = ProcessBuilder(adapter.command("stream", "--micros", "--debounce=100"))
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        process = started
        started.outputStream.close()
        started.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (line.length > MAX_RECORD_CHARS) continue
                streaming = true
                payload = merge(payload, line)
            }
        }
        started.waitFor(1, TimeUnit.SECONDS)
    }

    private fun pollOnce() {
        val adapter = adapter ?: return
        val now = System.currentTimeMillis()
        if (now - fallbackAt < FALLBACK_POLL_MS) return
        fallbackAt = now
        val output = runProcess(adapter.command("get", "--micros"), timeoutSeconds = 5L)
        payload = output?.let { runCatching { JsonParser.parseString(it) }.getOrNull() }
            ?.takeIf(JsonElement::isJsonObject)?.asJsonObject
    }

    private fun locate(): Adapter? {
        val script = System.getenv("MEDIAREMOTE_ADAPTER_SCRIPT_PATH")?.let(::File)
        val framework = System.getenv("MEDIAREMOTE_ADAPTER_FRAMEWORK_PATH")?.let(::File)
        if (script != null && framework != null) {
            return Adapter(script, framework).takeIf { script.isFile && framework.isDirectory }
        }

        val zip = MacMedia::class.java.getResourceAsStream(FRAMEWORK_RESOURCE)?.use { it.readAllBytes() } ?: return null
        val scriptBytes = MacMedia::class.java.getResourceAsStream(SCRIPT_RESOURCE)?.use { it.readAllBytes() } ?: return null

        val hash = MessageDigest.getInstance("SHA-256").digest(zip + scriptBytes).take(8).joinToString("") { "%02x".format(it) }
        val root = File(System.getProperty("java.io.tmpdir"), "evergreenhud-mediaremote").resolve(hash)
        val readyScript = root.resolve("mediaremote-adapter.pl")
        val readyFramework = root.resolve("MediaRemoteAdapter.framework")
        if (readyScript.isFile && readyFramework.isDirectory) return Adapter(readyScript, readyFramework)

        val staging = Files.createTempDirectory("evergreenhud-mediaremote-").toFile()
        try {
            val archive = staging.resolve("framework.zip").apply { writeBytes(zip) }
            staging.resolve("mediaremote-adapter.pl").writeBytes(scriptBytes)
            val extracted = ProcessBuilder(DITTO, "-x", "-k", archive.path, staging.path).start().waitFor() == 0
            if (!extracted || !staging.resolve("MediaRemoteAdapter.framework").isDirectory) return null
            archive.delete()
            root.parentFile.mkdirs()
            runCatching { Files.move(staging.toPath(), root.toPath(), StandardCopyOption.ATOMIC_MOVE) }
        } finally {
            staging.deleteRecursively()
        }
        return Adapter(readyScript, readyFramework).takeIf { readyScript.isFile && readyFramework.isDirectory }
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }

    private fun JsonObject.long(key: String): Long =
        get(key)?.takeIf { it.isJsonPrimitive }?.asDouble?.toLong() ?: 0L
}
