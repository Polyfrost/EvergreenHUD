package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc

class ECounter: Config(Mod("E Counter", ModType.HUD), "evergreenhud/ecounter.json", false) {
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

            val delimiter = if (simplified) '/' else ','
            return mc.renderGlobal.debugInfoEntities.substringAfter("E: ").substringBefore(delimiter)
        }
    }

}