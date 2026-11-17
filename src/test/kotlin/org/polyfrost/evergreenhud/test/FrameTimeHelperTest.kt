package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.utils.FrameTimeHelper
import kotlin.random.Random

class FrameTimeHelperTest {

    @Test
    fun worstAverageMatchesSort() {
        val random = Random(1)
        for (n in listOf(1, 2, 7, 100, 999, 10_000)) {
            val frames = LongArray(n) { random.nextLong(1, if (n % 2 == 0) 5 else 50_000_000) }
            val sorted = frames.sortedArray()
            val count1 = (n + 99) / 100
            val count01 = (n + 999) / 1000
            val expected1 = sorted.takeLast(count1).average()
            val expected01 = sorted.takeLast(count01).average()

            assertEquals(expected1, FrameTimeHelper.worstAverage(frames, 0, n, count1), 1e-6, "1% n=$n")
            assertEquals(expected01, FrameTimeHelper.worstAverage(frames, n - count1, n, count01), 1e-6, "0.1% n=$n")
            assertArrayEquals(sorted, frames.sortedArray(), "values lost n=$n")
        }
    }

    @Test
    fun windowKeepsNewestFramesAcrossGrowth() {
        val window = FrameTimeHelper.FrameWindow(5_000)
        for (t in 1..5_000) window.add(if (t <= 4_000) 1 else 2)
        assertEquals(4_000, window.size)
        assertEquals(5_000, window.sum)
        val snapshot = window.snapshot()
        assertEquals(1L, snapshot[0])
        assertEquals(2L, snapshot[window.size - 1])
        assertEquals(3_000, (0 until window.size).count { snapshot[it] == 1L })
    }
}
