package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.utils.dsl.*
import org.lwjgl.util.vector.Vector2f
import org.polyfrost.evergreenhud.config.HudConfig
import java.util.*
import kotlin.math.*

class Clock : HudConfig(Mod("Clock", ModType.HUD), "evergreenhud/clock.json", false) {

    @HUD(
        name = "Main"
    )
    var hud = ClockHud()

    init {
        initialize()
    }

    class ClockHud : BasicHud() {

        var padding = 4f

        @Exclude
        val radius = 40f

        @Exclude
        var caches: ArrayList<LineInfo> = ArrayList()

        init {
            runAsync {
                caches.clear()
                for (i in 0..59) {
                    val start = degreeToPosition(i * 6, radius)
                    val end = degreeToPosition(i * 6, radius - if (i % 5 == 0) 8 else 4)
                    caches.add(LineInfo(start, end, 1.5f))
                }
            }
        }

        @Color(
            name = "Hour Color"
        )
        var hourColor = OneColor(0, 0, 0, 255)

        @Color(
            name = "Minute Color"
        )
        var minuteColor = OneColor(0, 0, 0, 255)

        @Color(
            name = "Second Color"
        )
        var secondColor = OneColor(255, 0, 0, 255)

        @Color(
            name = "Lines Color"
        )
        var linesColor = OneColor(0, 0, 0, 255)

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
            val start = Vector2f(0f, 0f)
            val hour = LineInfo(start, degreeToPosition(hr * 30, 12f), 1.5)
            val minute = LineInfo(start, degreeToPosition(min * 6, 21f), 1)
            val second = LineInfo(degreeToPosition(sec * 6, -6f), degreeToPosition(sec * 6, 30f), 0.5f)
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
            vg.drawLine(lineInfo.start.x, lineInfo.start.y, lineInfo.end.x, lineInfo.end.y, lineInfo.width, color.rgb)
        }

        override fun getWidth(scale: Float, example: Boolean): Float {
            return radius * 2 * scale
        }

        override fun getHeight(scale: Float, example: Boolean): Float {
            return radius * 2 * scale
        }

    }

    data class LineInfo(var start: Vector2f, var end: Vector2f, var width: Number)
}