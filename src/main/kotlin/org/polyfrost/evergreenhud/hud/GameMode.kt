package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.hypixel.HypixelUtils
import cc.polyfrost.oneconfig.utils.hypixel.LocrawUtil
import java.util.*

class GameMode : Config(Mod("Game Mode", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/gamemode.json", false) {

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