package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.utils.PinkuluAPIManager
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.hypixel.*
import org.polyfrost.evergreenhud.config.HudConfig

class MapType : HudConfig("Map Type", "evergreenhud/maptype.json", false) {

    @HUD(
        name = "Main"
    )
    var hud = MapTypeHud()

    init {
        initialize()
    }

    class MapTypeHud : SingleTextHud("Map Type", true, 180, 110) {

        @Switch(
            name = "Hide If Not In-Game or Supported"
        )
        var hide = true

        override fun getText(example: Boolean): String {
            return PinkuluAPIManager.getMapPool() ?: "Unknown"
        }

        override fun shouldShow(): Boolean {
            return super.shouldShow() && (!hide || (HypixelUtils.INSTANCE.isHypixel && LocrawUtil.INSTANCE.isInGame && LocrawUtil.INSTANCE.locrawInfo?.mapName?.isNotBlank() == true && PinkuluAPIManager.getMapPool() != null))
        }
    }
}