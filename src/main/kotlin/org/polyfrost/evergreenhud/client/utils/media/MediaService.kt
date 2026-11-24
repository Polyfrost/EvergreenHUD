package org.polyfrost.evergreenhud.client.utils.media

import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.evergreenhud.client.hooks.KeyframeSync
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.URI
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

private const val BLUR_MAX_SIDE = 128

class BlurSource(val art: PolyBitmap, val width: Int, val height: Int, val pixels: FloatArray)

fun blurFactor(art: PolyBitmap): Int = ceil(maxOf(art.width, art.height) / BLUR_MAX_SIDE.toFloat()).toInt().coerceAtLeast(1)

fun blurSource(art: PolyBitmap): BlurSource {
    val factor = blurFactor(art)
    val width = (art.width / factor).coerceAtLeast(1)
    val height = (art.height / factor).coerceAtLeast(1)
    val pixels = FloatArray(width * height * 4)
    val factorX = minOf(factor, art.width)
    val factorY = minOf(factor, art.height)
    val area = factorX * factorY
    for (y in 0 until height) for (x in 0 until width) {
        val dst = (y * width + x) * 4
        for (sy in 0 until factorY) for (sx in 0 until factorX) {
            val src = ((y * factorY + sy) * art.width + x * factorX + sx) * 4
            val alpha = (art.pixels[src + 3].toInt() and 0xFF) / 255f
            for (c in 0..2) pixels[dst + c] += (art.pixels[src + c].toInt() and 0xFF) * alpha / area
            pixels[dst + 3] += alpha * 255f / area
        }
    }
    return BlurSource(art, width, height, pixels)
}

object MediaService {
    private const val POLL_MS = 1000L
    private const val MAX_ART_BYTES = 4 * 1024 * 1024
    private const val ART_TIMEOUT_MS = 3000
    private const val SAMPLE = 24
    private const val MIN_VIVID = 2.0
    private const val MIN_BRIGHTNESS = 170.0
    private const val INFO_CHANNEL = "evergreenhud:media"
    private const val ART_CHANNEL = "evergreenhud:media_art"
    private val ART_SCHEMES = setOf("http", "https", "file")

    private val backend: MediaBackend? by lazy { MediaBackend.get() }

    private val executor by lazy {
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "EvergreenHUD Media").apply { isDaemon = true }
        }
    }

    private var task: ScheduledFuture<*>? = null
    private var users = 0
    private var artKey: String? = null

    @Volatile
    private var liveCurrent: MediaInfo? = null

    @Volatile
    private var liveArt: Artwork? = null

    val current: MediaInfo? get() = if (KeyframeSync.replaying) Replayed.info() else liveCurrent

    val art: PolyBitmap? get() = artwork()?.image

    val artAccent: Int? get() = artwork()?.accent

    val artAverage: Int? get() = artwork()?.average

    private fun artwork(): Artwork? = if (KeyframeSync.replaying) Replayed.art() else liveArt

    private class Artwork(val image: PolyBitmap, val accent: Int, val average: Int?, val blurSource: BlurSource, val base64: String)

    val artBlurSource: BlurSource? get() = artwork()?.blurSource

    @Volatile
    var sources: List<String> = emptyList()

    fun isSupported(): Boolean = backend != null

    @Synchronized
    fun acquire() {
        val backend = backend ?: return
        if (users++ > 0) return
        KeyframeSync.register(INFO_CHANNEL) { liveCurrent?.let(::encodeInfo) }
        KeyframeSync.register(ART_CHANNEL) { liveArt?.base64 }
        task = executor.scheduleWithFixedDelay({ refresh(backend) }, 0L, POLL_MS, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun release() {
        if (users == 0 || --users > 0) return
        task?.cancel(false)
        executor.execute { backend?.close() }
        task = null
        liveCurrent = null
        liveArt = null
        artKey = null
    }

    fun send(command: MediaCommand) {
        val backend = backend ?: return
        if (KeyframeSync.replaying) return
        val player = liveCurrent?.player ?: return
        executor.execute {
            runCatching { backend.send(player, command) }
            refresh(backend)
        }
    }

    private fun refresh(backend: MediaBackend) {
        val info = runCatching { backend.poll() }.getOrNull()?.takeIf { it.matchesSource() }
        liveCurrent = info
        loadArt(info)
    }

    private fun loadArt(info: MediaInfo?) {
        val key = info?.artUrl ?: info?.artBytes?.let { "${info.player}|${info.title}|${info.artist}" }
        if (key == artKey) return
        artKey = key
        liveArt = null
        if (info == null || key == null) return
        liveArt = runCatching { artwork(info.artBytes ?: fetch(info.artUrl!!)) }.getOrNull()
    }

    private fun artwork(bytes: ByteArray): Artwork? {
        val image = PolyBitmap.decode(bytes) ?: return null
        return Artwork(image, vividColor(image), averageColor(image), blurSource(image), Base64.getEncoder().encodeToString(bytes))
    }

    private object Replayed {
        private var infoValue: String? = null
        private var info: MediaInfo? = null
        private var artValue: String? = null
        private var art: Artwork? = null

        fun info(): MediaInfo? {
            val value = KeyframeSync.replayed(INFO_CHANNEL)
            if (value != infoValue) {
                infoValue = value
                info = value?.let { runCatching { decodeInfo(it) }.getOrNull() }
            }
            return info
        }

        fun art(): Artwork? {
            val value = KeyframeSync.replayed(ART_CHANNEL)
            if (value != artValue) {
                artValue = value
                art = value?.let { runCatching { artwork(Base64.getDecoder().decode(it)) }.getOrNull() }
            }
            return art
        }
    }

    private fun encodeInfo(info: MediaInfo): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use {
            it.writeUTF(info.player)
            it.writeUTF(info.title)
            it.writeUTF(info.artist)
            it.writeBoolean(info.playing)
            it.writeLong(info.positionMs)
            it.writeLong(info.lengthMs)
            it.writeBoolean(info.canPrevious)
            it.writeBoolean(info.canNext)
            it.writeBoolean(info.canToggle)
            it.writeLong(info.sampledAtMs)
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    private fun decodeInfo(value: String): MediaInfo = DataInputStream(ByteArrayInputStream(Base64.getDecoder().decode(value))).use {
        MediaInfo(
            player = it.readUTF(),
            title = it.readUTF(),
            artist = it.readUTF(),
            artUrl = null,
            playing = it.readBoolean(),
            positionMs = it.readLong(),
            lengthMs = it.readLong(),
            canPrevious = it.readBoolean(),
            canNext = it.readBoolean(),
            canToggle = it.readBoolean(),
            sampledAtMs = it.readLong(),
        )
    }

    private fun fetch(url: String): ByteArray {
        val uri = URI(url)
        require(uri.scheme in ART_SCHEMES)
        val connection = uri.toURL().openConnection().apply {
            connectTimeout = ART_TIMEOUT_MS
            readTimeout = ART_TIMEOUT_MS
        }
        return connection.getInputStream().use { it.readNBytes(MAX_ART_BYTES) }
    }

    private fun averageColor(image: PolyBitmap): Int? {
        val pixels = image.pixels
        var r = 0L
        var g = 0L
        var b = 0L
        for (i in pixels.indices step 4) {
            r += pixels[i].toInt() and 0xFF
            g += pixels[i + 1].toInt() and 0xFF
            b += pixels[i + 2].toInt() and 0xFF
        }
        val count = pixels.size / 4L
        if (count == 0L) return null
        return 0xFF shl 24 or ((r / count).toInt() shl 16) or ((g / count).toInt() shl 8) or (b / count).toInt()
    }

    private fun vividColor(image: PolyBitmap): Int {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var total = 0.0
        var plainR = 0.0
        var plainG = 0.0
        var plainB = 0.0
        for (y in 0 until SAMPLE) for (x in 0 until SAMPLE) {
            val i = ((y * 2 + 1) * image.height / (SAMPLE * 2) * image.width + (x * 2 + 1) * image.width / (SAMPLE * 2)) * 4
            val cr = image.pixels[i].toInt() and 0xFF
            val cg = image.pixels[i + 1].toInt() and 0xFF
            val cb = image.pixels[i + 2].toInt() and 0xFF
            val weight = (maxOf(cr, cg, cb) - minOf(cr, cg, cb)) / 255.0
            val w = weight * weight
            r += cr * w
            g += cg * w
            b += cb * w
            total += w
            plainR += cr
            plainG += cg
            plainB += cb
        }
        val vivid = total >= MIN_VIVID
        val divisor = if (vivid) total else SAMPLE * SAMPLE.toDouble()
        var rf = (if (vivid) r else plainR) / divisor
        var gf = (if (vivid) g else plainG) / divisor
        var bf = (if (vivid) b else plainB) / divisor
        val brightest = maxOf(rf, gf, bf)
        if (brightest > 0.0 && brightest < MIN_BRIGHTNESS) {
            val lift = MIN_BRIGHTNESS / brightest
            rf *= lift
            gf *= lift
            bf *= lift
        }
        return 0xFF shl 24 or (rf.toInt().coerceIn(0, 255) shl 16) or (gf.toInt().coerceIn(0, 255) shl 8) or bf.toInt().coerceIn(0, 255)
    }
}
