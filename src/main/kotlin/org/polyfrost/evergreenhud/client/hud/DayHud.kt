package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.time.Duration.Companion.seconds

class DayHud : CachedTextHud(
    title = "Day",
    category = Category.INFO,
    defaultText = "0"
) {
    override fun getText(): String {
        if (!isReal) return "42"
        //? if < 26
        //return mc.level?.dayTime?.div(24000L)?.toString() ?: "0"
        //? if >= 26
        return mc.level?.overworldClockTime?.div(24000L)?.toString() ?: "0"
    }

    override fun updateFrequency(): Long = 1.seconds.inWholeNanoseconds
}
