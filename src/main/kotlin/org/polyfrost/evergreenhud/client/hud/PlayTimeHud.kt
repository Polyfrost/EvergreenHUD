package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

private val PREVIEW_TIME = 1.hours + 23.minutes + 45.seconds

class PlayTimeHud : CachedTextHud(
    title = "Play Time",
    category = Category.INFO,
    prefix = "Time Played: ",
) {
    private val start = System.nanoTime()

    @Switch(title = "Show Seconds")
    var seconds = true

    override fun setup() {
        super.setup()

        if (isReal) {
            updateWhenChanged("seconds")
        }
    }

    override fun getText(): String {
        val time = if (isReal) (System.nanoTime() - start).nanoseconds else PREVIEW_TIME
        return time.toComponents { hours: Long, minutes: Int, seconds: Int, _: Int ->
            buildString {
                fun addNumber(number: Number) {
                    if (!isEmpty()) append(":")
                    append(number.toString().padStart(2, '0'))
                }
                addNumber(hours)
                addNumber(minutes)
                if (this@PlayTimeHud.seconds) addNumber(seconds)
            }
        }
    }

    override fun updateFrequency(): Long {
        return 1.seconds.inWholeNanoseconds
    }
}
