package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.potion.EffectComponentSettings
import org.polyfrost.evergreenhud.client.hud.potion.isDurationColorWindow
import org.polyfrost.compose.render.PolyColor

class DurationColorTest {
    @Test
    fun `fractional threshold includes boundary and excludes invalid durations`() {
        assertTrue(isDurationColorWindow(47, false, 2.35f))
        assertFalse(isDurationColorWindow(48, false, 2.35f))
        assertTrue(isDurationColorWindow(1, false, 2.35f))
        assertFalse(isDurationColorWindow(0, false, 3f))
        assertFalse(isDurationColorWindow(-1, true, 3f))
        assertFalse(isDurationColorWindow(20, true, 3f))
        for (threshold in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertFalse(isDurationColorWindow(1, false, threshold))
        }
    }

    @Test
    fun `effect settings copy custom threshold and colors independently`() {
        val original = EffectComponentSettings().apply {
            timedColorEnabled = true
            timedColorThreshold = 2.35f
            timedNameColor = PolyColor(0xFFFF0000.toInt())
            timedDurationColor = PolyColor(0xFF0000FF.toInt())
        }
        val copy = EffectComponentSettings().also { it.copyFrom(original) }
        assertTrue(copy.timedColorEnabled)
        assertEquals(2.35f, copy.timedColorThreshold)
        assertEquals(original.timedNameColor.argb, copy.timedNameColor.argb)
        assertEquals(original.timedDurationColor.argb, copy.timedDurationColor.argb)
        assertNotSame(original.timedNameColor, copy.timedNameColor)
        assertNotSame(original.timedDurationColor, copy.timedDurationColor)
    }
}
