package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.polyfrost.evergreenhud.config.HudConfig

class ECounter: HudConfig("E Counter", "evergreenhud/ecounter.json", false) {
    @HUD(name = "Main")
    var hud = ECounterHUD()

    init {
        initialize()
    }

    class ECounterHUD : SingleTextHud("E", true, 400, 90) {

        @Switch(
                name = "Simplified"
        )
        var simplified = true

        override fun getText(example: Boolean): String {
            if (mc.thePlayer == null) return "Unknown"

            // just read the field directly with a mixin like in CCounter
            val delimiter = if (simplified) '/' else ','
            return mc.renderGlobal.debugInfoEntities.substringAfter("E: ").substringBefore(delimiter)
        }
    }

}