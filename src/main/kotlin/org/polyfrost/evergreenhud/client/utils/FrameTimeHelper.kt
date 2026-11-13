package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.Event
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent

object FrameTimeHelper {
    private const val WINDOW_NANOS = 1_000_000_000L

    private const val LOWS_WINDOW_NANOS = 10_000_000_000L

    private const val SAMPLE_NANOS = 100_000_000L

    private const val BASELINE_SMOOTHING = 0.004

    private var lastTime = System.nanoTime()
    private val frameTimes = ArrayDeque<Long>()
    private var windowSum = 0L
    private val lowsFrameTimes = ArrayDeque<Long>()
    private var lowsWindowSum = 0L
    private var lastSample = 0L

    @JvmStatic
    @Volatile
    var latest: FrameData = FrameData.EMPTY
        private set

    @JvmStatic
    @Volatile
    var baselineFps: Double = 0.0
        private set

    fun initialize() {
        eventHandler { _: FramebufferRenderEvent.End ->
            val now = System.nanoTime()
            val frameTime = now - lastTime
            lastTime = now

            frameTimes.addLast(frameTime)
            windowSum += frameTime
            while (frameTimes.size > 2 && windowSum > WINDOW_NANOS) {
                windowSum -= frameTimes.removeFirst()
            }
            lowsFrameTimes.addLast(frameTime)
            lowsWindowSum += frameTime
            while (lowsFrameTimes.size > 2 && lowsWindowSum > LOWS_WINDOW_NANOS) {
                lowsWindowSum -= lowsFrameTimes.removeFirst()
            }

            if (now - lastSample < SAMPLE_NANOS) return@eventHandler
            lastSample = now
            if (frameTimes.size < 2) return@eventHandler

            val sorted = frameTimes.toLongArray()
            sorted.sort()
            val sum = windowSum
            val consistency = (sorted.last() - sorted.first()).toDouble() / sorted.size / sum
            val avg = sum.toDouble() / sorted.size
            val p50 = percentile(sorted, 0.50)
            val p95 = percentile(sorted, 0.95)
            val p99 = percentile(sorted, 0.99)
            val lowsSorted = lowsFrameTimes.toLongArray()
            lowsSorted.sort()
            val low1 = worstAverage(lowsSorted, 0.01)
            val low01 = worstAverage(lowsSorted, 0.001)
            val data = FrameData(consistency, avg, p50, p95, p99, low1, low01, sorted.size)
            latest = data

            val fps = data.averageFps
            if (fps > 0.0) {
                baselineFps = if (baselineFps <= 0.0) fps else baselineFps + (fps - baselineFps) * BASELINE_SMOOTHING
            }

            EventManager.INSTANCE.post(FrameDataEvent(data))
        }
    }

    private fun percentile(sorted: LongArray, percentile: Double): Double {
        val idx = sorted.size * percentile - 1
        val l = idx.toInt().coerceAtLeast(0)
        val u = (l + 1).coerceAtMost(sorted.size - 1)
        val fractional = idx - l
        val lower = sorted[l].toDouble()
        val higher = sorted[u].toDouble()
        return lower + (higher - lower) * fractional
    }

    private fun worstAverage(sorted: LongArray, fraction: Double): Double {
        val count = Math.ceil(sorted.size * fraction).toInt().coerceAtLeast(1)
        var sum = 0.0
        for (i in sorted.size - count until sorted.size) sum += sorted[i]
        return sum / count
    }

    data class FrameData(
        val consistency: Double,
        val mean: Double,
        val median: Double,
        val p95: Double,
        val p99: Double,
        val low1: Double,
        val low01: Double,
        val nframes: Int
    ) {
        val averageFps get() = fps(mean)
        val medianFps get() = fps(median)
        val meanMillis get() = mean / 1_000_000.0
        val medianMillis get() = median / 1_000_000.0
        val p95Fps get() = fps(p95)
        val p99Fps get() = fps(p99)
        val low1Fps get() = fps(low1)
        val low01Fps get() = fps(low01)
        val consistencyPercent get() = (1.0 - consistency) * 100.0

        private fun fps(nanos: Double) = if (nanos > 0.0) 1_000_000_000.0 / nanos else 0.0

        companion object {
            @JvmField
            val EMPTY = FrameData(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }
    }

    data class FrameDataEvent(val data: FrameData) : Event
}
