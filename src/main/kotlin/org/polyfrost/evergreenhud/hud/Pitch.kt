package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.utils.decimalFormat
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.polyfrost.evergreenhud.config.HudConfig

class Pitch: HudConfig("Pitch", "evergreenhud/pitch.json", false) {
    @HUD(name = "Main")
    var hud = PitchHud()

    class PitchHud: SingleTextHud("Pitch", true, 180, 50) {

        @Slider(
            name = "Accuracy",
            min = 0F,
            max = 8F
        )
        var accuracy = 2

        @Switch(
            name = "Trailing Zeros"
        )
        var trailingZeros = true

        override fun getText(example: Boolean): String {
            return decimalFormat(accuracy, trailingZeros).format(mc.thePlayer?.rotationPitch ?: 0f )
        }

    }
}