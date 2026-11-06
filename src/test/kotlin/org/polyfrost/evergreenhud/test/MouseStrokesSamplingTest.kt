package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.mouse.accumulatedYaw
import org.polyfrost.evergreenhud.client.hud.mouse.appendSpline
import org.polyfrost.evergreenhud.client.hud.mouse.isSampleDue
import org.polyfrost.evergreenhud.client.hud.mouse.nextSampleAfter
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

class MouseStrokesSamplingTest {

    private fun sampledFrames(fps: Int, seconds: Int = 1, originNs: Long = 10_000_000_000L, jitterNs: Long = 0L): List<Int> {
        val random = Random(fps)
        var deadline = Long.MIN_VALUE
        val sampled = ArrayList<Int>()
        for (frame in 0 until fps * seconds) {
            val jitter = if (jitterNs == 0L) 0L else random.nextLong(-jitterNs, jitterNs + 1)
            val now = originNs + frame * 1_000_000_000L / fps + jitter
            if (isSampleDue(deadline, now)) {
                deadline = nextSampleAfter(deadline, now)
                sampled += frame
            }
        }
        return sampled
    }

    private fun samplesPerSecond(fps: Int): Int = sampledFrames(fps).size

    @Test
    fun samplesAboutSixtyTimesASecondAboveSixtyFps() {
        for (fps in listOf(75, 120, 144, 165, 240, 500)) {
            val samples = samplesPerSecond(fps)
            assertTrue(samples in 57..61, "$fps fps gave $samples samples")
        }
    }

    @Test
    fun samplesEveryFrameBelowSixtyFps() {
        assertEquals(30, samplesPerSecond(30))
        assertEquals(45, samplesPerSecond(45))
    }

    @Test
    fun samplesEvenlyOnDivisorsOfTheFrameRateDespiteJitter() {
        for ((fps, framesPerSample) in listOf(60 to 1, 120 to 2, 240 to 4)) {
            val gaps = sampledFrames(fps, seconds = 20, jitterNs = 1_000_000L).drop(1).zipWithNext { a, b -> b - a }
            assertEquals(setOf(framesPerSample), gaps.toSet(), "$fps fps")
        }
    }

    @Test
    fun samplesWhenTheClockStartsNegative() {
        assertTrue(sampledFrames(120, originNs = -10_000_000_000L).size in 57..61)
    }

    @Test
    fun fastTurnKeepsItsDirectionBetweenSamples() {
        val headings = listOf(0f, 50f, 100f, 150f, 200f)
        assertEquals(200f, headings.zipWithNext().fold(0f) { pending, (last, yaw) -> accumulatedYaw(pending, last, yaw) })
    }

    @Test
    fun yawDeltaCrossesTheWrapSeam() {
        assertEquals(20f, accumulatedYaw(0f, 170f, -170f))
    }

    private fun spline(xs: FloatArray, ys: FloatArray): FloatArray {
        val out = FloatArray((xs.size * 8 + 1) * 2)
        out[0] = xs[0]
        out[1] = ys[0]
        var size = 2
        for (i in 0 until xs.size - 1) size = appendSpline(out, size, xs, ys, i, xs.size)
        return out.copyOf(size)
    }

    @Test
    fun smallSegmentsStayStraightAndLongOnesAreCapped() {
        val tiny = spline(floatArrayOf(0f, 0.4f, 0.8f), floatArrayOf(0f, 0.1f, 0.2f))
        assertEquals(3 * 2, tiny.size)
        val long = spline(floatArrayOf(0f, 40f), floatArrayOf(0f, 0f))
        assertEquals(9 * 2, long.size)
    }

    @Test
    fun tightCurveStaysCloseToItsCircle() {
        val radius = 4f
        val turnPerSample = 0.6f
        val xs = FloatArray(10) { radius * cos(it * turnPerSample) }
        val ys = FloatArray(10) { radius * sin(it * turnPerSample) }
        val line = spline(xs, ys)

        var worst = 0f
        for (i in 4 until line.size - 6 step 2) {
            val midX = (line[i] + line[i + 2]) / 2f
            val midY = (line[i + 1] + line[i + 3]) / 2f
            worst = max(worst, abs(hypot(midX, midY) - radius))
        }
        assertTrue(worst < 0.1f, "chord midpoints are $worst off the curve")
    }
}
