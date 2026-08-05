package org.polyfrost.evergreenhud.client.hud


//? if > 1.8.9 {
import net.minecraft.network.protocol.common.ClientboundPingPacket
//?} else
//import net.minecraft.network.packet.s2c.play.InventoryMenuConfirmS2CPacket
import net.minecraft.network.protocol.game.ClientboundSetTimePacket
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.ServerChangedEvent
import org.polyfrost.evergreenhud.client.utils.GenericNumberHud
import org.polyfrost.evergreenhud.client.utils.quality
import org.polyfrost.evergreenhud.client.utils.qualityColor
import org.polyfrost.evergreenhud.client.utils.replace
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.PacketEvent

class TpsHud : GenericNumberHud(
    title = "TPS",
    category = Category.INFO,
) {
    @Text(title = "Format String", description = "Use #tps for the current tps, #avg for the average, #low for the lowest. Average and lowest reset when you change server.")
    private var formatString = "#tps"

    @Switch(title = "Color By Value", description = "Colours the value green at 20 TPS, fading to red at 15 and below.", subcategory = "Colors")
    private var colorByValue = false

    private var lastUpdated = 0L

    private var useTickPings = false

    private val pingTimes = ArrayDeque<Long>()

    private var sampleCount = 0L
    private var sampleSum = 0.0
    private var lowest = 0f

    private val recent = ArrayDeque<Float>()

    override fun setup() {
        super.setup()

        eventHandler { (ip, _, _): ServerChangedEvent ->
            useTickPings = isHypixel(ip)
            lastUpdated = 0L
            pingTimes.clear()
            resetStats()
        }

        eventHandler { (packet): PacketEvent.Receive ->
            when (packet) {
                is ClientboundSetTimePacket -> if (!useTickPings) onTimeUpdate()
                //? if > 1.8.9 {
                is ClientboundPingPacket -> if (useTickPings) onTickPing()
                //?} else
                //is InventoryMenuConfirmS2CPacket -> if (useTickPings && packet.menuId == 0 && !packet.accepted) onTickPing()
            }
        }

        if (isReal) {
            updateWhenChanged("formatString")
            updateWhenChanged("colorByValue")
        }
    }

    private fun onTimeUpdate() {
        val now = System.currentTimeMillis()
        val previous = lastUpdated
        lastUpdated = now
        if (previous == 0L) return
        val timeTaken = now - previous
        if (timeTaken <= 0L) return
        record(20000f / timeTaken)
    }

    private fun onTickPing() {
        val now = System.currentTimeMillis()
        pingTimes.addLast(now)

        while (pingTimes.size > 2 && now - pingTimes.first() > WINDOW_MS) {
            pingTimes.removeFirst()
        }

        val span = now - pingTimes.first()
        if (pingTimes.size < 2 || span <= 0L) return
        record((pingTimes.size - 1) * 1000f / span)
    }

    private fun record(measured: Float) {
        recent.addLast(measured)
        while (recent.size > SAMPLE_WINDOW) recent.removeFirst()
        val tps = median(recent).coerceIn(0f, PERFECT_TPS)
        lowest = if (sampleCount == 0L) tps else minOf(lowest, tps)
        sampleCount++
        sampleSum += tps
        value = tps
        updateWithText(render(tps))
    }

    private fun render(tps: Float): String {
        val average = if (sampleCount == 0L) tps else (sampleSum / sampleCount).toFloat()
        return StringBuilder().append(formatString)
            .replace("#tps", format(tps))
            .replace("#avg", format(average))
            .replace("#low", format(if (sampleCount == 0L) tps else lowest))
            .toString()
    }

    override fun valueColor(): PolyColor? {
        if (!colorByValue || recent.isEmpty()) return null
        return qualityColor(quality(value, LOW_TPS, PERFECT_TPS))
    }

    private fun resetStats() {
        sampleCount = 0L
        sampleSum = 0.0
        lowest = 0f
        recent.clear()
    }

    private companion object {
        const val WINDOW_MS = 1000L
        const val PERFECT_TPS = 20f

        const val LOW_TPS = 15f

        const val SAMPLE_WINDOW = 5

        fun median(samples: Collection<Float>): Float {
            val sorted = samples.sorted()
            val mid = sorted.size / 2
            return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2f else sorted[mid]
        }

        fun isHypixel(ip: String?): Boolean {
            val host = ip?.substringBefore(':')?.trimEnd('.')?.lowercase() ?: return false
            return host == "hypixel.net" || host.endsWith(".hypixel.net")
        }
    }
}
