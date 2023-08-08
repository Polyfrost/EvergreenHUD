package cc.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc

class ECounter: Config(Mod("E Counter", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/ecounter.json", false) {
    @HUD(name = "Main")
    var hud = ECounterHUD()

    init {
        initialize()
    }

    class ECounterHUD: SingleTextHud("E", true, 400, 70) {

        @Switch(
                name = "Simplified"
        )
        var simplified = true

        override fun getText(example: Boolean): String {
            if (mc.thePlayer == null) return "Unknown"
            return if (simplified) mc.renderGlobal.debugInfoEntities.split("/")[0].replace("E: ", "")
                else mc.renderGlobal.debugInfoEntities.split(",")[0].replace("E: ", "")
        }
    }

}