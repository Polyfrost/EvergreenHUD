package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.ClientPlaceBlockEvent
import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.evergreenhud.client.utils.fastRemoveIfReversed
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.hud.v1.TextHud
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.time.Duration.Companion.milliseconds

class PlaceCountHud : CachedTextHud(
    title = "Block Place Count",
    category = Category.COMBAT,
    prefix = "Blocks: ",
) {

    @Slider(title = "Interval (ms)", min = 500F, max = 3000F)
    var interval = 1000

    private val blockCount: ArrayList<Long> = ArrayList()

    override fun setup() {
        super.setup()

        if (isReal) {
            eventHandler { event: ClientPlaceBlockEvent ->
                if (event.player == mc.player) {
                    blockCount.add(System.nanoTime())
                    updateAndRecalculate()
                }
            }
        }
    }

    override fun getText(): String {
        if (!isReal) return "7/s"
        val time = System.nanoTime()
        val max = interval * 1_000_000L
        blockCount.fastRemoveIfReversed { time - it > max }
        return blockCount.size.toString() + "/s"
    }

    override fun updateFrequency(): Long {
        return 50.milliseconds.inWholeNanoseconds
    }

}
