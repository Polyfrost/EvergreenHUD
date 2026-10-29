package org.polyfrost.evergreenhud.client.utils.media

import org.polyfrost.compose.render.PolyBitmap
import java.net.URI
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

object MediaService {
    private const val POLL_MS = 1000L
    private const val MAX_ART_BYTES = 4 * 1024 * 1024
    private const val ART_TIMEOUT_MS = 3000
    private const val SAMPLE = 24
    private const val MIN_VIVID = 2.0
    private const val MIN_BRIGHTNESS = 170.0
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
    var current: MediaInfo? = null
        private set

    @Volatile
    var art: PolyBitmap? = null
        private set

    @Volatile
    var artAccent: Int? = null
        private set

    @Volatile
    var artAverage: Int? = null
        private set

    fun isSupported(): Boolean = backend != null

    @Synchronized
    fun acquire() {
        val backend = backend ?: return
        if (users++ > 0) return
        task = executor.scheduleWithFixedDelay({ refresh(backend) }, 0L, POLL_MS, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun release() {
        if (users == 0 || --users > 0) return
        task?.cancel(false)
        executor.execute { backend?.close() }
        task = null
        current = null
        art = null
        artAccent = null
        artAverage = null
        artKey = null
    }

    fun send(command: MediaCommand) {
        val backend = backend ?: return
        val player = current?.player ?: return
        executor.execute {
            runCatching { backend.send(player, command) }
            refresh(backend)
        }
    }

    private fun refresh(backend: MediaBackend) {
        val info = runCatching { backend.poll() }.getOrNull()
        current = info
        loadArt(info)
    }

    private fun loadArt(info: MediaInfo?) {
        val key = info?.artUrl ?: info?.artBytes?.let { "${info.player}|${info.title}|${info.artist}" }
        if (key == artKey) return
        artKey = key
        art = null
        artAccent = null
        artAverage = null
        if (info == null || key == null) return
        val image = runCatching {
            val bytes = info.artBytes ?: fetch(info.artUrl!!)
            PolyBitmap.decode(bytes)
        }.getOrNull()
        artAccent = image?.let(::vividColor)
        artAverage = image?.let(::averageColor)
        art = image
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
