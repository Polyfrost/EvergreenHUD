package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import org.polyfrost.evergreenhud.config.HudConfig
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class RealLifeDate : HudConfig("IRL Date", "evergreenhud/irldate.json", false) {
    @HUD(name = "Main")
    var hud = RealLifeDateHud()

    init {
        initialize()
    }

    class RealLifeDateHud : SingleTextHud("Date", true, 120, 20) {

        /*
        now even more flexible than the one irl date hud element
        that biscuit made for tommy in tommyhud.jar!
        - erymanthus
        */

        @Switch(name = "Full Length Date")
        var fullLengthDate = false

        @Switch(name = "Two Digit Day")
        var twoDigitDay = false

        @Switch(name = "Month Before Date")
        var monthBeforeDate = false

        @Switch(name = "Show Day of Week")
        var dayOfWeek = false

        @Switch(name = "Show Year")
        var showYear = false

        fun determineFormatter(): DateTimeFormatter {
            val stringBuilder = StringBuilder()
            if (dayOfWeek) {
                if (fullLengthDate) {
                    stringBuilder.append("E") //EEEE, MMMM dd | EEEE, dd MMMM
                }
                stringBuilder.append("EEE, ") //EEE, MMM d | EEE, d MMM
            }

            if (monthBeforeDate) {
                if (fullLengthDate) {
                    stringBuilder.append("M") //MMMM dd
                }
                stringBuilder.append("MMM ") //MMM d
            }

            stringBuilder.append("d")
            if (twoDigitDay) { stringBuilder.append("d") } //dd

            if (!monthBeforeDate) {
                stringBuilder.append(" MMM") //d MMM
                if (fullLengthDate) {
                    stringBuilder.append("M") //dd MMMM
                }
            }

            if (showYear) {
                stringBuilder.append(" YYYY")
            }

            return DateTimeFormatter.ofPattern(stringBuilder.toString())
        }

        override fun getText(example: Boolean): String = determineFormatter().format(ZonedDateTime.now())
    }
}