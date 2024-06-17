package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.libs.universal.UMinecraft
import org.polyfrost.evergreenhud.config.HudConfig
import java.text.SimpleDateFormat
import java.util.*

class InGameTime : HudConfig("In Game Time", "evergreenhud/ingametime.json", false) {
    @HUD(name = "Main")
    var hud = InGameTimeHud()

    init {
        initialize()
    }

    class InGameTimeHud : SingleTextHud("Time", true, 400, 10) {

        @Switch(name = "Twelve Hour Time")
        var twelveHour = false

        override fun getText(example: Boolean): String {
            UMinecraft.getWorld()?.let {
                // ticks to ticks in day to seconds to millis plus six hours (time 0 = 6am)
                val date = Date(it.worldTime / 20 * 1000 + 21_600_000) // 6 hours == 21,600,000 milliseconds
                return SimpleDateFormat(if (twelveHour) "hh:mm a" else "HH:mm")
                    .format(date).uppercase()
            }
            return "06:00 AM"
        }
    }
}