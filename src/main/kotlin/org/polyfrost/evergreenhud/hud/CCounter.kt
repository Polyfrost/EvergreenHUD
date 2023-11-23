package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc

class CCounter: Config(Mod("C Counter", ModType.HUD), "evergreenhud/ccounter.json", false) {
    @HUD(name = "Main")
    var hud = CCounterHud()

    init {
        initialize()
    }

    class CCounterHud: SingleTextHud("C", true, 400, 70) {

        @Switch(
                name = "Simplified"
        )
        var simplified = true

        override fun getText(example: Boolean): String {
            if (mc.thePlayer == null) return "Unknown"
            return if (simplified) mc.renderGlobal.debugInfoRenders.split("/")[0].replace("C: ", "")
                else mc.renderGlobal.debugInfoRenders.split(" ")[1]
        }
    }

}