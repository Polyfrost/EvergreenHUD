package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.polyfrost.evergreenhud.config.HudConfig

class Day: HudConfig("Day", "evergreenhud/day.json", false) {
    @HUD(name = "Main")
    var hud = DayHud()

    init {
        initialize()
    }

    class DayHud : SingleTextHud("Day", true, 400, 30) {
        override fun getText(example: Boolean): String {
            if (mc.theWorld == null) return "0"
            return (mc.theWorld.worldTime / 24000L).toString()
        }
    }
}