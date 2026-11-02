package org.polyfrost.evergreenhud.client.hud.clock

import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.evergreenhud.client.utils.SpacedTextHud
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val PREVIEW_TIME = LocalTime.of(10, 9, 30)

class DigitalClockHud : SpacedTextHud(
    id = "digital_clock.json",
    title = "Digital Clock",
    category = Category.INFO,
    prefix = "Clock: ",
) {
    @Switch(title = "24-Hour Format")
    var twentyFourHour = true

    @Switch(title = "Show Seconds")
    var showSeconds = true

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun setup() {
        super.setup()
        if (isReal) {
            updateWhenChanged("twentyFourHour")
            updateWhenChanged("showSeconds")
        }
    }

    override fun getText(): String = (if (isReal) LocalTime.now() else PREVIEW_TIME).format(formatter())

    override fun updateFrequency(): Long =
        (if (showSeconds) 500.milliseconds else 1.seconds).inWholeNanoseconds

    private fun formatter(): DateTimeFormatter {
        val pattern = buildString {
            append(if (twentyFourHour) "HH:mm" else "hh:mm")
            if (showSeconds) append(":ss")
            if (!twentyFourHour) append(" a")
        }
        return DateTimeFormatter.ofPattern(pattern)
    }
}
