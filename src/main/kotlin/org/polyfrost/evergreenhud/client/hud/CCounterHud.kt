package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.time.Duration.Companion.seconds

class CCounterHud : CachedTextHud(
    title = "C Counter",
    category = Category.INFO,
    prefix = "C: ",
    defaultText = "0"
) {
    override fun getText(): String {
        if (!isReal) return "187"
        if (mc.level == null) return "0"
        //? if = 1.8.9 {
        /*return mc.worldRenderer.chunkDebugInfo.substringAfter("C: ").substringBefore('/')
        *///?} else if < 26.2 {
        /*return mc.levelRenderer.countRenderedSections().toString()
        *///?} else
        return mc.levelExtractor.countRenderedSections().toString()
    }

    override fun updateFrequency(): Long = 1.seconds.inWholeNanoseconds
}
