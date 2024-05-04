package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.utils.FrameTimeHelper
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.hud.SingleTextHud
import net.minecraft.client.Minecraft
import org.polyfrost.evergreenhud.config.HudConfig
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class FPS : HudConfig(Mod("FPS", ModType.HUD), "evergreenhud/fps.json", false) {
    @HUD(name = "FPS", category = "FPS")
    var fps = FPSHud()

    @HUD(name = "Frame Consistency", category = "Frame Consistency")
    var frameConsistency = FrameConsistencyHud()

    @HUD(name = "Frame Time", category = "Frame Time")
    var frameTime = FrameTimeHud()

    init {
        initialize()
    }

    class FPSHud : SingleTextHud("FPS", true, 60, 50) {
        @Switch(
            name = "Update Fast"
        )
        var updateFast = false

        @Dropdown(
            name = "Average Method",
            options = ["Mean", "Median", "99th Percentile", "95th Percentile"]
        )
        var averageMethod = 0

        private fun average(list: List<Double>): Double = when (averageMethod) {
            0 -> list.average()
            1 -> percentile(list, 0.5)
            2 -> percentile(list, 0.99)
            3 -> percentile(list, 0.95)
            else -> 0.0
        }

        private fun percentile(list: List<Double>, percentile: Double): Double {
            val index = ceil(list.size * percentile).toInt() - 1
            return list.sorted()[index]
        }

        override fun getText(example: Boolean): String {
            return if (updateFast) {
                val fps = (1000 / (average(FrameTimeHelper.frameTimes).takeUnless { it.isNaN() } ?: 1.0)).roundToInt().toString()
                fps
            } else {
                Minecraft.getDebugFPS().toString()
            }
        }
    }

    class FrameConsistencyHud : SingleTextHud("Frame Consistency", false) {

        private fun List<Double>.consistency(): Double {
            if (this.size <= 1) return 0.0
            var change = 0.0
            var count = 0
            var previous: Double? = null
            this.forEach {
                if (previous != null) {
                    change += abs(it - previous!!)
                    count++
                }
                previous = it
            }
            return change / count / this.sum()
        }

        override fun getText(example: Boolean): String {
            val consistency = ((1 - FrameTimeHelper.frameTimes.consistency()) * 100).roundToInt()
            return "$consistency%"
        }
    }

    class FrameTimeHud : SingleTextHud("Frame Time", false) {

        @Dropdown(
            name = "Average Method",
            options = ["Mean", "Median", "99th Percentile", "95th Percentile"]
        )
        var averageMethod = 0

        private fun average(list: List<Double>): Double = when (averageMethod) {
            0 -> list.average()
            1 -> percentile(list, 0.5)
            2 -> percentile(list, 0.99)
            3 -> percentile(list, 0.95)
            else -> 0.0
        }

        private fun percentile(list: List<Double>, percentile: Double): Double {
            val index = ceil(list.size * percentile).toInt() - 1
            return list.sorted()[index]
        }

        override fun getText(example: Boolean): String {
            // converts average to FPS
            val frameTime = (average(FrameTimeHelper.frameTimes).takeUnless { it.isNaN() } ?: 1.0).roundToInt()
            return frameTime.toString() + "ms"
        }
    }
}
