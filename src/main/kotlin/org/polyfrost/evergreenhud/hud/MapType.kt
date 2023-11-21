package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.utils.PinkuluAPIManager
import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.hypixel.HypixelUtils
import cc.polyfrost.oneconfig.utils.hypixel.LocrawUtil

class MapType : Config(Mod("Map Type", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/maptype.json", false) {

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