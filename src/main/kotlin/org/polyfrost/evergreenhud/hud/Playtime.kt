package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.hook.PlaytimeHook
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import org.polyfrost.evergreenhud.config.HudConfig

class Playtime: HudConfig(Mod("Playtime", ModType.HUD), "evergreenhud/playtime.json", false) {
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