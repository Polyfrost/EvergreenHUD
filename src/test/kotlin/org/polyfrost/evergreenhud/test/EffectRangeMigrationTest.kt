package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.potion.EffectComponentSettings
import org.polyfrost.evergreenhud.client.hud.potion.PER_OPTION_OVERRIDES
import org.polyfrost.evergreenhud.client.hud.potion.RANGE_LISTS
import org.polyfrost.evergreenhud.client.hud.potion.allowsInRanges
import org.polyfrost.evergreenhud.client.hud.potion.migrateRanges

class EffectRangeMigrationTest {

    private fun settings(duration: FloatArray, amplifier: FloatArray) = EffectComponentSettings().apply {
        durationRange = duration
        amplifierRange = amplifier
    }

    @Test
    fun v1ZeroRangeMeansUnrestricted() {
        for (version in listOf(0, PER_OPTION_OVERRIDES)) {
            val loaded = settings(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f))
            loaded.migrateRanges(version)
            assertArrayEquals(floatArrayOf(), loaded.durationRange, "duration, v$version")
            assertArrayEquals(floatArrayOf(), loaded.amplifierRange, "amplifier, v$version")
            assertTrue(loaded.amplifierRange.allowsInRanges(3f))
        }
    }

    @Test
    fun v2ZeroRangeIsLevelOneOnlyFilter() {
        val loaded = settings(floatArrayOf(0f, 0f), floatArrayOf(0f, 0f))
        loaded.migrateRanges(RANGE_LISTS)
        assertArrayEquals(floatArrayOf(0f, 0f), loaded.amplifierRange)
        assertArrayEquals(floatArrayOf(0f, 0f), loaded.durationRange)
        assertTrue(loaded.amplifierRange.allowsInRanges(0f))
        assertFalse(loaded.amplifierRange.allowsInRanges(1f))
    }

    @Test
    fun v2ValueRoundTripsAcrossRepeatedLoads() {
        val loaded = settings(floatArrayOf(), floatArrayOf(0f, 0f, 2f, 4f))
        repeat(3) { loaded.migrateRanges(RANGE_LISTS) }
        assertArrayEquals(floatArrayOf(0f, 0f, 2f, 4f), loaded.amplifierRange)
    }

    @Test
    fun v1RealRangesAreKept() {
        val loaded = settings(floatArrayOf(5f, 30f), floatArrayOf(1f, 3f))
        loaded.migrateRanges(PER_OPTION_OVERRIDES)
        assertArrayEquals(floatArrayOf(5f, 30f), loaded.durationRange)
        assertArrayEquals(floatArrayOf(1f, 3f), loaded.amplifierRange)
    }
}
