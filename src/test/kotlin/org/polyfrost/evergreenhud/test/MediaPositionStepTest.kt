package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.media.stepPosition

class MediaPositionStepTest {

    @Test
    fun labelSecondStaysExact() {
        for (length in listOf(181_000L, 243_500L, 600_000L)) {
            for (position in 0L..length) {
                assertEquals(position / 1000L, stepPosition(position, length) / 1000L, "$position of $length")
            }
        }
    }

    @Test
    fun trackEndIsExact() {
        assertEquals(181_500L, stepPosition(181_500L, 181_500L))
    }

    @Test
    fun unknownLengthStepsWholeSeconds() {
        assertEquals(61_000L, stepPosition(61_500L, 0L))
    }

    @Test
    fun redrawsStayBounded() {
        val length = 181_000L
        val distinct = (0L..length).map { stepPosition(it, length) }.distinct().size
        assertTrue(distinct <= 2000 + length / 1000L + 1, "$distinct updates")
    }
}
