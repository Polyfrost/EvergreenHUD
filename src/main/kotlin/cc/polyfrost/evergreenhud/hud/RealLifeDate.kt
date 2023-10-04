package cc.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.SingleTextHud
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class RealLifeDate : Config(Mod("IRL Date", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/irldate.json", false) {
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
            var baseString = "d" //d
            if (twoDigitDay) { baseString = "d" + baseString } //dd
            if (monthBeforeDate) {
                baseString = "MMM " + baseString //MMM d
                if (fullLengthDate) {
                    baseString = "M" + baseString //MMMM dd
                }
            } else {
                baseString = baseString + " MMM" //d MMM
                if (fullLengthDate) {
                    baseString = baseString + "M" //dd MMMM
                }
            }
            if (dayOfWeek) {
                baseString = "EEE, " + baseString //EEE, MMM d | EEE, d MMM
                if (fullLengthDate) {
                    baseString = "E" + baseString //EEEE, MMMM dd | EEEE, dd MMMM
                }
            }
            if (showYear) {
                baseString = baseString + " YYYY"
            }

            return DateTimeFormatter.ofPattern(baseString)!!
        }

        override fun getText(example: Boolean): String = determineFormatter().format(ZonedDateTime.now())
    }
}