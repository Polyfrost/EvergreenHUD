package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.config.elements.SubConfig
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.utils.dsl.*
import org.lwjgl.util.vector.Vector2f
import org.polyfrost.evergreenhud.config.HudConfig
import java.util.*
import kotlin.math.*

class Clock : HudConfig("Clock", "evergreenhud/clock.json", false) {

    @HUD(
        name = "Main"
    )
    var hud = ClockHud()

    init {
        initialize()
    }

    class ClockHud : BasicHud(true) {

        var padding = 4f

        @Exclude
        val radius = 40f

        @Exclude
        var caches: ArrayList<LineInfo> = ArrayList()

        init {
            runAsync {
                caches.clear()
                for (i in 0..59) {
                    caches.add(LineInfo(i * 6.0, radius, radius - if (i % 5 == 0) 8 else 4, 1.5f))
                }
            }
        }

        @Color(
            name = "Hour Color"
        )
        var hourColor = OneColor(255, 255, 255)

        @Color(
            name = "Minute Color"
        )
        var minuteColor = OneColor(255, 255, 255)

        @Color(
            name = "Second Color"
        )
        var secondColor = OneColor(255, 0, 0, 255)

        @Color(
            name = "Lines Color"
        )
        var linesColor = OneColor(255, 255, 255)

        private fun degreeToPosition(degree: Number, length: Float): Vector2f {
            val radian = Math.toRadians(degree.toDouble())
            return Vector2f(sin(radian).toFloat() * length, -cos(radian).toFloat() * length)
        }

        override fun drawAll(matrices: UMatrixStack?, example: Boolean) {
            paddingX = padding
            paddingY = padding
            super.drawAll(matrices, example)
        }

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            val calendar = Calendar.getInstance()
            val sec = calendar[Calendar.SECOND] + calendar[Calendar.MILLISECOND] / 1000f
            val min = calendar[Calendar.MINUTE] + sec / 60f
            val hr = calendar[Calendar.HOUR] + min / 60f
            val hour = LineInfo(hr * 30.0, 0f, 12f, 1.5)
            val minute = LineInfo(min * 6.0, 0f, 21f, 1)
            val second = LineInfo(sec * 6.0, -6f, 30f, 0.5)
            nanoVG(true) {
                translate(x + radius * scale, y + radius * scale)
                scale(scale, scale)
                for (line in caches) {
                    drawLine(this, line, linesColor)
                }
                drawLine(this, hour, hourColor)
                drawLine(this, minute, minuteColor)
                drawLine(this, second, secondColor)
                drawCircle(0, 0, 2, linesColor.rgb)
                if (shouldDrawBackground() && background && border) nanoVGHelper.drawHollowEllipse(this.instance, 0f, 0f, radius + padding, radius + padding, borderColor.rgb, borderSize)
            }
        }

        override fun drawBackground(x: Float, y: Float, width: Float, height: Float, scale: Float) {
            nanoVG(true) {
                drawCircle(x + (radius + padding) * scale, y + (radius + padding) * scale, (radius + padding) * scale, bgColor.rgb)
            }
        }

        private fun drawLine(vg: VG, lineInfo: LineInfo, color: OneColor) {
            nanoVGHelper.rotate(vg.instance, lineInfo.degree)
            vg.drawLine(0, - lineInfo.start, 0, - lineInfo.end, lineInfo.width, color.rgb)
            nanoVGHelper.rotate(vg.instance, - lineInfo.degree)
        }

        override fun getWidth(scale: Float, example: Boolean): Float {
            return radius * 2 * scale
        }

        override fun getHeight(scale: Float, example: Boolean): Float {
            return radius * 2 * scale
        }

    }

    data class LineInfo(var degree: Double, var start: Float, var end: Float, var width: Number)
}