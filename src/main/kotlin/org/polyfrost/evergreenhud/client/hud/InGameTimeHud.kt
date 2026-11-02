package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.time.Duration.Companion.milliseconds

private const val PREVIEW_DAY_TIME = 8500L

class InGameTimeHud : CachedTextHud(
    title = "In Game Time",
    category = Category.INFO,
) {
    @Switch(title = "Twelve Hour Time")
    var twelveHour = false

    override fun setup() {
        super.setup()
        if (isReal) {
            updateWhenChanged("twelveHour")
        }
    }

    override fun getText(): String {
        //? if < 26
        //val dayTime = mc.level?.dayTime ?: 0L
        //? if >= 26
        val dayTime = mc.level?.overworldClockTime ?: 0L
        val time = ((if (isReal) dayTime else PREVIEW_DAY_TIME) + 6000L) % 24000L
        val seconds = (time * 3.6).toLong()
        val minutes = (seconds % 3600L) / 60L
        val hours = (seconds / 3600L) % 24L
        val realHours = if (twelveHour) {
            if (hours % 12L == 0L) 12L else hours % 12L
        } else {
            hours
        }

        val sb = StringBuilder()

        fun appendNumber(num: Long) {
            if (sb.isNotEmpty()) sb.append(":")
            sb.append(num.toString().padStart(2, '0'))
        }

        appendNumber(realHours)
        appendNumber(minutes)

        if (twelveHour) {
            sb.append(if (realHours < 12L) " AM" else " PM")
        }

        return sb.toString()
    }

    override fun updateFrequency(): Long {
        return 500.milliseconds.inWholeNanoseconds
    }
}
