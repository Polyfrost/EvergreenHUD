package org.polyfrost.evergreenhud.client.utils.media

import org.apache.commons.lang3.SystemUtils
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

data class MediaInfo(
    val player: String,
    val title: String,
    val artist: String,
    val artUrl: String?,
    val playing: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canToggle: Boolean,
    val artBytes: ByteArray? = null,
    val sampledAtMs: Long = System.currentTimeMillis(),
) {
    fun positionAt(nowMs: Long): Long {
        val elapsed = if (playing) (nowMs - sampledAtMs).coerceAtLeast(0L) else 0L
        val position = positionMs + elapsed
        return if (lengthMs > 0) position.coerceAtMost(lengthMs) else position
    }
}

enum class MediaCommand { Previous, Toggle, Next }

interface MediaBackend {
    fun poll(): MediaInfo?

    fun send(player: String, command: MediaCommand)

    fun close() {}

    companion object {
        fun get(): MediaBackend? = when {
            SystemUtils.IS_OS_LINUX -> LinuxMedia.takeIf { it.isAvailable() }
            SystemUtils.IS_OS_WINDOWS -> WindowsMedia.takeIf { it.isAvailable() }
            SystemUtils.IS_OS_MAC -> MacMedia.takeIf { it.isAvailable() }
            else -> null
        }
    }
}

fun List<MediaInfo>.pickActive(lastActive: String?): MediaInfo? =
    firstOrNull { it.playing } ?: firstOrNull { it.player == lastActive } ?: firstOrNull()

fun runProcess(command: List<String>, timeoutSeconds: Long = 3L): String? = runCatching {
    val process = ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start()
    process.outputStream.close()
    val out = StringBuffer()
    val reader = thread(isDaemon = true) { runCatching { out.append(process.inputStream.bufferedReader().readText()) } }
    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return null
    }
    reader.join(1000L)
    if (process.exitValue() == 0) out.toString() else null
}.getOrNull()
