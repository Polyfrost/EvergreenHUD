package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.utils.PinkuluAPIManager
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc
import cc.polyfrost.oneconfig.utils.hypixel.*
import org.polyfrost.evergreenhud.config.HudConfig

class HeightLimit : HudConfig(Mod("Height Limit", ModType.HUD), "evergreenhud/heightlimit.json", false) {

    @HUD(
        name = "Main"
    )
    var hud = HeightLimitHud()

    init {
        initialize()
    }

    class HeightLimitHud : SingleTextHud("Height Limit", true, 180, 150) {

        @Switch(
            name = "Hide If Not In-Game or Supported"
        )
        var hide = true

        @Switch(
            name = "Show Distance To Limit"
        )
        var showDistance = false

        override fun getText(example: Boolean): String {
            return PinkuluAPIManager.getMapHeight()?.let { if (showDistance) it - mc.thePlayer.position.y else it }?.toString() ?: "Unknown"
        }

        override fun shouldShow(): Boolean {
            return super.shouldShow() && (!hide || (HypixelUtils.INSTANCE.isHypixel && LocrawUtil.INSTANCE.isInGame && LocrawUtil.INSTANCE.locrawInfo?.mapName?.isNotBlank() == true && PinkuluAPIManager.getMapHeight() != null)) && mc.thePlayer != null && mc.theWorld != null
        }
    }
}