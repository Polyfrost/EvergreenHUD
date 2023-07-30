package cc.polyfrost.evergreenhud.hud

import cc.polyfrost.evergreenhud.hook.PlaytimeHook
import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.SingleTextHud

class Playtime: Config(Mod("Playtime", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/playtime.json", false) {
    @HUD(name = "Main")
    var hud = PlaytimeHud()

    init {
        initialize()
    }

    class PlaytimeHud: SingleTextHud("Playtime", true, 0, 130) {
        @Switch(name = "Show Seconds")
        var seconds = true
        override fun getText(example: Boolean): String {
            val timePlayed = System.currentTimeMillis() - PlaytimeHook.startTime
            val formattedTime = if (seconds) {
                String.format("%d:%02d:%02d", timePlayed / 3600000, (timePlayed % 3600000) / 60000, (timePlayed % 60000) / 1000)
            } else {
                String.format("%d:%02d", timePlayed / 3600000, (timePlayed % 3600000) / 60000)
            }
            return formattedTime
        }
    }
}