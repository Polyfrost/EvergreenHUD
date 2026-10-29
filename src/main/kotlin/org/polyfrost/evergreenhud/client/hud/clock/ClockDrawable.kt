package org.polyfrost.evergreenhud.client.hud.clock

import androidx.compose.runtime.Composable
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.PolyFont
import org.polyfrost.compose.render.RenderContext
import java.lang.Math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

private val SECOND_HAND = PolyColor.rgba(170, 170, 170, 255)
private val MINUTE_HAND = PolyColor.rgba(255, 255, 255, 255)
private val HOUR_HAND = PolyColor.rgba(120, 180, 255, 255)
private val DETAIL = PolyColor.rgba(255, 255, 255, 255)

private class Outline(val hw: Float, val hh: Float, val cornerRadius: Float?) {
    private val inscribed = minOf(hw, hh)
    private val corner = cornerRadius?.coerceIn(0f, inscribed) ?: inscribed

    fun distance(dx: Float, dy: Float): Float {
        val tx = if (abs(dx) < 1e-5f) Float.MAX_VALUE else hw / abs(dx)
        val ty = if (abs(dy) < 1e-5f) Float.MAX_VALUE else hh / abs(dy)
        val edge = minOf(tx, ty)
        if (abs(dx) * edge <= hw - corner || abs(dy) * edge <= hh - corner) return edge

        val ccx = sign(dx) * (hw - corner)
        val ccy = sign(dy) * (hh - corner)
        val b = dx * ccx + dy * ccy
        val disc = b * b - (ccx * ccx + ccy * ccy - corner * corner)
        return if (disc < 0f) edge else b + sqrt(disc)
    }
}

@Composable
fun Clock(
    millisOfDay: () -> Long,
    handWidth: Float = 2f,
    ticking: Boolean = false,
    fiveMinuteMarks: Boolean = false,
    minuteMarks: Boolean = false,
    numbers: Boolean = false,
    hourHandColor: PolyColor = HOUR_HAND,
    minuteHandColor: PolyColor = MINUTE_HAND,
    secondHandColor: PolyColor = SECOND_HAND,
    detailColor: PolyColor = DETAIL,
    fontName: String? = null,
    cornerRadius: Float? = null,
    modifier: PolyModifier = PolyModifier
        .size(200f, 200f)
) {
    PolyCanvas(modifier) { _, _, width, height ->

        val radius = minOf(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        val outline = Outline(width / 2f, height / 2f, cornerRadius)

        if (fiveMinuteMarks || minuteMarks) {
            marks(cx, cy, radius, outline, handWidth, fiveMinuteMarks, minuteMarks, detailColor)
        }
        if (numbers) numbers(cx, cy, radius, outline, fiveMinuteMarks || minuteMarks, detailColor, fontName)

        val millis = millisOfDay()
        val secs = if (ticking) floor(millis / 1000.0) else millis / 1000.0

        val secondAngle = (2 * PI * (secs % 60.0 / 60.0) - PI / 2).toFloat()

        val minuteAngle = (2 * PI * ((secs / 60.0) % 60.0 / 60.0) - PI / 2).toFloat()

        val hourAngle = (2 * PI * ((secs / 3600.0) % 12.0 / 12.0) - PI / 2).toFloat()

        val secondLength = radius * 0.9f
        val minuteLength = radius * 0.75f
        val hourLength = radius * 0.4f

        line(
            cx,
            cy,
            cx + secondLength * cos(secondAngle),
            cy + secondLength * sin(secondAngle),
            secondHandColor,
            handWidth * 0.6f
        )

        line(
            cx,
            cy,
            cx + minuteLength * cos(minuteAngle),
            cy + minuteLength * sin(minuteAngle),
            minuteHandColor,
            handWidth * 0.9f
        )

        line(
            cx,
            cy,
            cx + hourLength * cos(hourAngle),
            cy + hourLength * sin(hourAngle),
            hourHandColor,
            handWidth
        )
    }
}

private fun RenderContext.marks(
    cx: Float,
    cy: Float,
    radius: Float,
    outline: Outline,
    handWidth: Float,
    fiveMinute: Boolean,
    minute: Boolean,
    color: PolyColor,
) {
    for (i in 0 until 60) {
        val onFive = i % 5 == 0
        if (if (onFive) !fiveMinute else !minute) continue

        val quarter = i % 15 == 0
        val length = radius * when {
            quarter -> 0.15f
            onFive -> 0.09f
            else -> 0.05f
        }
        val width = handWidth * when {
            quarter -> 0.7f
            onFive -> 0.4f
            else -> 0.25f
        }
        val angle = (2 * PI * (i / 60.0) - PI / 2).toFloat()
        val dx = cos(angle)
        val dy = sin(angle)
        val outer = outline.distance(dx, dy) - radius * 0.03f
        val inner = outer - length
        line(cx + inner * dx, cy + inner * dy, cx + outer * dx, cy + outer * dy, color, width)
    }
}

private fun RenderContext.numbers(
    cx: Float,
    cy: Float,
    radius: Float,
    outline: Outline,
    marks: Boolean,
    color: PolyColor,
    fontName: String?,
) {
    val font = if (fontName != null) PolyFont(fontName, radius * 0.2f) else PolyFont.default(radius * 0.2f)
    val metrics = font.metrics
    val inset = radius * if (marks) 0.32f else 0.2f

    for (hour in 1..12) {
        val angle = (2 * PI * (hour / 12.0) - PI / 2).toFloat()
        val dx = cos(angle)
        val dy = sin(angle)
        val ring = (outline.distance(dx, dy) - inset).coerceAtLeast(0f)
        val text = hour.toString()
        val x = cx + ring * dx - font.measureTextWidth(text) / 2f
        val y = cy + ring * dy - (metrics.ascent + metrics.descent) / 2f
        text(text, x, y, color, font)
    }
}
