package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.ClientPlaceBlockEvent
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.polyfrost.evergreenhud.config.HudConfig

class PlaceCount: HudConfig(Mod("Block Place Count", ModType.HUD), "evergreenhud/placecount.json", false) {
    @HUD(name = "Main")
    var hud = PlaceCountHud()

    init {
        initialize()
    }

    class PlaceCountHud : SingleTextHud("Blocks", true, 120, 30) {
        @Slider(
            name = "Interval",
            min = 500F,
            max = 3000F
        )
        var interval = 1000

        init {
            EventManager.INSTANCE.register(this)
        }

        private val blockCount = ArrayDeque<Long>()

        @Subscribe
        private fun onTick(event: TickEvent) {
            if (event.stage == Stage.START) {
                val currentTime = System.currentTimeMillis()
                if (!blockCount.isEmpty()) {
                    while ((currentTime - blockCount.first()) > interval) {
                        blockCount.removeFirst()
                        if (blockCount.isEmpty()) break
                    }
                }
            }
        }

        @Subscribe
        private fun onBlockPlace(event: ClientPlaceBlockEvent) {
            if (event.player == mc.thePlayer) {
                blockCount.addLast(System.currentTimeMillis())
            }
        }

        override fun getText(example: Boolean): String {
            return blockCount.size.toString()
        }

    }
}