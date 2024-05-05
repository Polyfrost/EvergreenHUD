package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.hypixel.*
import org.polyfrost.evergreenhud.config.HudConfig

class GameType : HudConfig("Game Type", "evergreenhud/gametype.json", false) {

    @HUD(
        name = "Main"
    )
    var hud = GameTypeHud()

    class GameTypeHud : SingleTextHud("Game Type", true, 180, 130) {

        @Switch(
            name = "Hide If Not In-Game or Supported"
        )
        var hide = true

        override fun getText(example: Boolean): String {
            return LocrawUtil.INSTANCE.locrawInfo?.rawGameType ?: "Unknown"
        }

        override fun shouldShow(): Boolean {
            return super.shouldShow() && (!hide || (HypixelUtils.INSTANCE.isHypixel && LocrawUtil.INSTANCE.isInGame && LocrawUtil.INSTANCE.locrawInfo?.rawGameType?.isNotBlank() == true))
        }
    }
}