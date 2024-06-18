package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.utils.*
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.polyfrost.evergreenhud.config.HudConfig

class Yaw : HudConfig("Yaw", "evergreenhud/yaw.json", false) {
    @HUD(name = "Main")
    var hud = YawHud()

    init {
        initialize()
    }

    class YawHud : SingleTextHud("Yaw", true, 180, 70) {

        @Slider(name = "Accuracy", min = 0F, max = 10F)
        var accuracy = 2

        @Switch(name = "Trailing Zeros")
        var trailingZeros = true

        override fun getText(example: Boolean): String {
            // decimal format again 
            return decimalFormat(accuracy, trailingZeros).format(mc.thePlayer?.rotationYaw?.let { Facing.wrapDegrees(it) } ?: 0f)
        }
    }
}