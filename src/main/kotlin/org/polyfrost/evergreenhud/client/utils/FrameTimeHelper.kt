package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.Event
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent

object FrameTimeHelper {
    private const val WINDOW_NANOS = 1_000_000_000L

    private var lastTime = System.nanoTime()
    private val frameTimes = ArrayDeque<Long>()
    private var windowSum = 0L
    private var tickCount = 0

    @JvmStatic
    @Volatile
    var latest: FrameData = FrameData.EMPTY
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
        }

        eventHandler { _: TickEvent.End ->
            tickCount++
            if (tickCount > 10) {
                tickCount = 0
                if (frameTimes.size > 1) {
                    val sorted = frameTimes.toLongArray()
                    sorted.sort()
                    val sum = windowSum
                    val consistency = (sorted.last() - sorted.first()).toDouble() / sorted.size / sum
                    val avg = sum.toDouble() / sorted.size
                    val p50 = percentile(sorted, 0.50)
                    val p95 = percentile(sorted, 0.95)
                    val p99 = percentile(sorted, 0.99)
                    latest = FrameData(consistency, avg, p50, p95, p99, sorted.size)
                    EventManager.INSTANCE.post(FrameDataEvent(latest))
                }
            }
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

    data class FrameData(
        val consistency: Double,
        val mean: Double,
        val median: Double,
        val p95: Double,
        val p99: Double,
        val nframes: Int
    ) {
        val meanFps get() = fps(mean)
        val medianFps get() = fps(median)
        val meanMillis get() = mean / 1_000_000.0
        val medianMillis get() = median / 1_000_000.0
        val p95Millis get() = p95 / 1_000_000.0
        val p99Millis get() = p99 / 1_000_000.0
        val consistencyPercent get() = (1.0 - consistency) * 100.0

        private fun fps(nanos: Double) = if (nanos > 0.0) 1_000_000_000.0 / nanos else 0.0

        companion object {
            @JvmField
            val EMPTY = FrameData(0.0, 0.0, 0.0, 0.0, 0.0, 0)
        }
    }

    data class FrameDataEvent(val data: FrameData) : Event
}
