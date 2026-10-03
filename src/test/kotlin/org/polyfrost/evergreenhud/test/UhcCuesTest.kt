package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.utils.isFacingOrigin
import org.polyfrost.evergreenhud.client.utils.isGappleReEatWindow

class UhcCuesTest {
    @Test
    fun `origin heading works in each cardinal direction and quadrant`() {
        for ((x, z, yaw) in listOf(
            Triple(100.0, 0.0, 90.0), Triple(-100.0, 0.0, -90.0),
            Triple(0.0, 100.0, 180.0), Triple(0.0, -100.0, 0.0),
            Triple(100.0, 100.0, 135.0), Triple(-100.0, 100.0, -135.0),
            Triple(100.0, -100.0, 45.0), Triple(-100.0, -100.0, -45.0),
        )) {
            assertTrue(isFacingOrigin(x, z, yaw, 0f))
            assertFalse(isFacingOrigin(x, z, yaw + 180.0, 5f))
        }
    }

    @Test
    fun `tolerance includes boundary and wraps around north`() {
        assertTrue(isFacingOrigin(0.0, 100.0, -175.0, 5f))
        assertTrue(isFacingOrigin(0.0, 100.0, 175.0, 5f))
        assertFalse(isFacingOrigin(0.0, 100.0, -174.99, 5f))
        assertTrue(isFacingOrigin(0.0, 100.0, -535.0, 5f))
        assertFalse(isFacingOrigin(0.0, 0.0, 0.0, 180f))
    }

    @Test
    fun `re-eat window adapts to regeneration level`() {
        // Amplifiers are zero-based. II starts at 3.0s, III at 2.35s.
        for ((amplifier, threshold) in listOf(0 to 81, 1 to 60, 2 to 47, 3 to 37, 4 to 34, 5 to 32, 6 to 32, 255 to 32)) {
            assertFalse(isGappleReEatWindow(true, threshold + 1, false, amplifier))
            assertTrue(isGappleReEatWindow(true, threshold, false, amplifier))
            assertTrue(isGappleReEatWindow(true, 1, false, amplifier))
        }
    }

    @Test
    fun `ten second regen keeps every healing tick with a 200 ms reaction delay`() {
        for ((amplifier, interval) in listOf(1 to 25, 2 to 12)) {
            val start = (200 downTo 1).first { isGappleReEatWindow(true, it, false, amplifier) }
            val remainingAtFinish = start - 4 - 32
            val allHeals = (200 downTo 1).filter { it % interval == 0 }
            assertTrue(allHeals.all { it > remainingAtFinish })
            // Starting one tick earlier would finish on the last heal's tick.
            assertTrue(remainingAtFinish + 1 == allHeals.last())
        }
    }

    @Test
    fun `re-eat window excludes expired infinite and non-regeneration effects`() {
        assertFalse(isGappleReEatWindow(true, 0, false, 1))
        assertFalse(isGappleReEatWindow(true, -1, true, 1))
        assertFalse(isGappleReEatWindow(true, 30, true, 1))
        assertFalse(isGappleReEatWindow(false, 30, false, 1))
    }
    @Test
    fun `regeneration levels use independent configurable cue thresholds`() {
        assertTrue(isGappleReEatWindow(true, 80, false, 1, 4f, 1f))
        assertFalse(isGappleReEatWindow(true, 81, false, 1, 4f, 1f))
        assertTrue(isGappleReEatWindow(true, 20, false, 2, 4f, 1f))
        assertFalse(isGappleReEatWindow(true, 21, false, 2, 4f, 1f))
        assertFalse(isGappleReEatWindow(true, 1, false, 1, 0f, 1f))
        assertFalse(isGappleReEatWindow(true, 20, true, 2, 4f, 1f))
    }

}
