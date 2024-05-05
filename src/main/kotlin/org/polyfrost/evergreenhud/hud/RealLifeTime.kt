package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import org.polyfrost.evergreenhud.config.HudConfig
import java.text.SimpleDateFormat
import java.util.*

class RealLifeTime : HudConfig("IRL Time", "evergreenhud/irltime.json", false) {
    @HUD(name = "Main")
    var hud = RealLifeTimeHud()

    class RealLifeTimeHud : SingleTextHud("Time", true, 120, 10) {

        @Switch(name = "Twelve Hour Time")
        var twelveHour = false

        @Switch(name = "Seconds")
        var seconds = false

        override fun getText(example: Boolean): String = SimpleDateFormat(String.format(if (twelveHour) "hh:mm%s a" else "HH:mm%s", if (seconds) ":ss" else ""))
            .format(Calendar.getInstance().time).uppercase()
    }
}