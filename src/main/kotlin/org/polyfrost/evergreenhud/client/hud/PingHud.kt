package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.ServerChangedEvent
import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.evergreenhud.client.utils.quality
import org.polyfrost.evergreenhud.client.utils.qualityColor
import org.polyfrost.evergreenhud.client.utils.replace
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.time.Duration.Companion.milliseconds

class PingHud : CachedTextHud(
    title = "Ping",
    category = Category.INFO,
) {
    companion object {
        private const val MS_PER_TICK = 50

        private const val BEST_PING = 30f

        private const val WORST_PING = 200f

        @JvmStatic
        fun sampleIntervalTicks(): Int {
            var interval = -1
            for (hud in HudManager.activeInstances) {
                if (hud !is PingHud || hud.hidden || !hud.shouldShow()) continue
                if (interval == -1 || hud.sampleIntervalTicks < interval) interval = hud.sampleIntervalTicks
            }
            return interval
        }
    }

    @Slider(title = "Update Rate (ms)", min = 250F, max = 5000F, step = 250F)
    var updateRate = 500

    @Text(title = "Format String", description = "Use #ping for the current ping, #avg for the average, #high for the highest. Average and highest reset when you change server.")
    private var formatString = "#pingms"

    @Switch(title = "Color By Value", description = "Colours the value green at 30ms or less, fading to red at 200ms and above.", subcategory = "Colors")
    private var colorByValue = false

    private var sampleCount = 0L
    private var sampleSum = 0.0
    private var highest = 0L
    private var lastPing: Long? = null

    private val sampleIntervalTicks get() = (updateRate / MS_PER_TICK).coerceAtLeast(1)

    override fun shouldShow(): Boolean = !mc.hasSingleplayerServer()

    override fun setup() {
        super.setup()
        eventHandler { _: ServerChangedEvent ->
            resetStats()
        }

        if (isReal) {
            updateWhenChanged("formatString")
            updateWhenChanged("colorByValue")
        }
    }

    override fun updateFrequency(): Long {
        return updateRate.milliseconds.inWholeNanoseconds
    }

    override fun getText(): String {
        val ping = if (isReal) measuredPing() ?: serverReportedPing() else null
        lastPing = ping
        if (ping == null) return formatString.replace("#ping", "-1").replace("#avg", "-1").replace("#high", "-1")
        return render(ping)
    }

    private fun render(ping: Long): String {
        highest = if (sampleCount == 0L) ping else maxOf(highest, ping)
        sampleCount++
        sampleSum += ping
        val average = (sampleSum / sampleCount).toLong()
        return StringBuilder().append(formatString)
            .replace("#ping", ping.toString())
            .replace("#avg", average.toString())
            .replace("#high", highest.toString())
            .toString()
    }

    override fun valueColor(): PolyColor? {
        val ping = lastPing
        if (!colorByValue || ping == null) return null
        return qualityColor(quality(ping.toFloat(), WORST_PING, BEST_PING))
    }

    private fun resetStats() {
        sampleCount = 0L
        sampleSum = 0.0
        highest = 0L
        lastPing = null
    }

    private fun measuredPing(): Long? {
        if (mc.connection == null) return null
        val logger = mc.debugOverlay?.pingLogger ?: return null
        val size = logger.size()
        return if (size == 0) null else logger.get(size - 1)
    }

    private fun serverReportedPing(): Long? =
        mc.player?.uuid?.let { mc.connection?.getPlayerInfo(it)?.latency?.toLong() }
}
