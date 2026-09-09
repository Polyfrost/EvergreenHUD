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
    fun `re-eat window starts at thirty ticks and excludes expired and infinite effects`() {
        assertFalse(isGappleReEatWindow(true, 31, false))
        assertTrue(isGappleReEatWindow(true, 30, false))
        assertTrue(isGappleReEatWindow(true, 1, false))
        assertFalse(isGappleReEatWindow(true, 0, false))
        assertFalse(isGappleReEatWindow(true, -1, true))
        assertFalse(isGappleReEatWindow(true, 30, true))
        assertFalse(isGappleReEatWindow(false, 30, false))
    }
}
