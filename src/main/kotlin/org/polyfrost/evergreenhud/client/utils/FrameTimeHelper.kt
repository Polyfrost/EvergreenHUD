package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.event.v1.EventManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.Event
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import kotlin.math.ceil

object FrameTimeHelper {
    private const val WINDOW_NANOS = 1_000_000_000L

    private const val LOWS_WINDOW_NANOS = 10_000_000_000L

    private const val SAMPLE_NANOS = 100_000_000L

    private const val LOWS_SAMPLE_NANOS = 1_000_000_000L

    private const val LOWS_REQUEST_NANOS = 10_000_000_000L

    private const val BASELINE_SMOOTHING = 0.004

    private var lastTime = System.nanoTime()
    private val frameTimes = FrameWindow(WINDOW_NANOS)
    private val lowsFrameTimes = FrameWindow(LOWS_WINDOW_NANOS)
    private var lastSample = 0L
    private var lastLowsSample = 0L
    private var low1 = 0.0
    private var low01 = 0.0

    @Volatile
    private var lowsWantedUntil = 0L

    @JvmStatic
    @Volatile
    var latest: FrameData = FrameData.EMPTY
        private set

    @JvmStatic
    @Volatile
    var baselineFps: Double = 0.0
        private set

    fun requestLows() {
        lowsWantedUntil = System.nanoTime() + LOWS_REQUEST_NANOS
    }

    fun initialize() {
        eventHandler { _: FramebufferRenderEvent.End ->
            val now = System.nanoTime()
            val frameTime = now - lastTime
            lastTime = now

            frameTimes.add(frameTime)
            lowsFrameTimes.add(frameTime)

            if (now - lastSample < SAMPLE_NANOS) return@eventHandler
            lastSample = now
            val n = frameTimes.size
            if (n < 2) return@eventHandler

            val sorted = frameTimes.snapshot()
            sorted.sort(0, n)
            val sum = frameTimes.sum
            val consistency = (sorted[n - 1] - sorted[0]).toDouble() / n / sum
            val avg = sum.toDouble() / n
            val p50 = percentile(sorted, n, 0.50)
            val p95 = percentile(sorted, n, 0.95)
            val p99 = percentile(sorted, n, 0.99)

            if (now - lowsWantedUntil < 0 && now - lastLowsSample >= LOWS_SAMPLE_NANOS) {
                lastLowsSample = now
                val lowsN = lowsFrameTimes.size
                val frames = lowsFrameTimes.snapshot()
                val count1 = ceil(lowsN * 0.01).toInt().coerceAtLeast(1)
                val count01 = ceil(lowsN * 0.001).toInt().coerceAtLeast(1)
                low1 = worstAverage(frames, 0, lowsN, count1)
                low01 = worstAverage(frames, lowsN - count1, lowsN, count01)
            }

            val data = FrameData(consistency, avg, p50, p95, p99, low1, low01, n)
            latest = data

            val fps = data.averageFps
            if (fps > 0.0) {
                baselineFps = if (baselineFps <= 0.0) fps else baselineFps + (fps - baselineFps) * BASELINE_SMOOTHING
            }

            EventManager.INSTANCE.post(FrameDataEvent(data))
        }
    }

    private fun percentile(sorted: LongArray, n: Int, percentile: Double): Double {
        val idx = n * percentile - 1
        val l = idx.toInt().coerceAtLeast(0)
        val u = (l + 1).coerceAtMost(n - 1)
        val fractional = idx - l
        val lower = sorted[l].toDouble()
        val higher = sorted[u].toDouble()
        return lower + (higher - lower) * fractional
    }

    internal fun worstAverage(a: LongArray, from: Int, to: Int, count: Int): Double {
        select(a, from, to, to - count)
        var sum = 0.0
        for (i in to - count until to) sum += a[i]
        return sum / count
    }

    private fun select(a: LongArray, from: Int, to: Int, k: Int) {
        var lo = from
        var hi = to - 1
        while (lo < hi) {
            val pivot = a[(lo + hi) ushr 1]
            var i = lo
            var j = hi
            while (i <= j) {
                while (a[i] < pivot) i++
                while (a[j] > pivot) j--
                if (i <= j) {
                    val t = a[i]; a[i] = a[j]; a[j] = t
                    i++; j--
                }
            }
            if (k <= j) hi = j else if (k >= i) lo = i else return
        }
    }

    internal class FrameWindow(private val maxNanos: Long) {
        private var buf = LongArray(1024)
        private var scratch = LongArray(0)
        private var head = 0
        var size = 0
            private set
        var sum = 0L
            private set

        fun add(frameTime: Long) {
            if (size == buf.size) buf = copyInto(LongArray(buf.size * 2)).also { head = 0 }
            buf[(head + size) % buf.size] = frameTime
            size++
            sum += frameTime
            while (size > 2 && sum > maxNanos) {
                sum -= buf[head]
                head = (head + 1) % buf.size
                size--
            }
        }

        fun snapshot(): LongArray {
            if (scratch.size < size) scratch = LongArray(buf.size)
            return copyInto(scratch)
        }

        private fun copyInto(dest: LongArray): LongArray {
            for (i in 0 until size) dest[i] = buf[(head + i) % buf.size]
            return dest
        }
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
