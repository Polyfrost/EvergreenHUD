package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.hypixel.*
import org.polyfrost.evergreenhud.config.HudConfig
import java.util.*

class GameMode : HudConfig("Game Mode", "evergreenhud/gamemode.json", false) {

    @HUD(
        name = "Main"
    )
    var hud = GameModeHud()

    init {
        initialize()
    }

    class GameModeHud : SingleTextHud("Game Mode", true, 180, 150) {

        @Switch(
            name = "Hide If Not In-Game or Supported"
        )
        var hide = true

        override fun getText(example: Boolean): String {
            return (LocrawUtil.INSTANCE.locrawInfo?.gameMode ?: "Unknown").replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(
                    Locale.ENGLISH
                ) else it.toString()
            }
        }

        override fun shouldShow(): Boolean {
            return super.shouldShow() && (!hide || (HypixelUtils.INSTANCE.isHypixel && LocrawUtil.INSTANCE.isInGame && LocrawUtil.INSTANCE.locrawInfo?.gameMode?.isNotBlank() == true))
        }
    }
}