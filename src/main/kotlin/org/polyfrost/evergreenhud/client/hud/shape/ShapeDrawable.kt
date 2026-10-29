package org.polyfrost.evergreenhud.client.hud.shape

import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.PolyPath
import org.polyfrost.compose.render.PolyRenderer
import org.polyfrost.compose.render.RenderContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private const val START_ANGLE = -PI / 2.0

private const val CIRCLE_SEGMENTS = 96

fun polygonPath(cx: Float, cy: Float, rx: Float, ry: Float, sides: Int): FloatArray =
    buildPath(sides) { i ->
        val angle = START_ANGLE + i * 2.0 * PI / sides
        (cx + rx * cos(angle).toFloat()) to (cy + ry * sin(angle).toFloat())
    }

fun starPath(cx: Float, cy: Float, rx: Float, ry: Float, points: Int, innerRatio: Float): FloatArray =
    buildPath(points * 2) { i ->
        val ratio = if (i % 2 == 0) 1f else innerRatio
        val angle = START_ANGLE + i * PI / points
        (cx + rx * ratio * cos(angle).toFloat()) to (cy + ry * ratio * sin(angle).toFloat())
    }

private inline fun buildPath(vertices: Int, vertex: (Int) -> Pair<Float, Float>): FloatArray {
    val points = FloatArray(vertices * 2)
    for (i in 0 until vertices) {
        val (x, y) = vertex(i)
        points[i * 2] = x
        points[i * 2 + 1] = y
    }
    return points
}

private fun arcPoints(x: Float, y: Float, w: Float, h: Float, startDegrees: Float, sweepDegrees: Float, closed: Boolean): FloatArray {
    val segments = ceil(CIRCLE_SEGMENTS * abs(sweepDegrees) / 360f).toInt().coerceAtLeast(2)
    val count = if (closed) segments else segments + 1
    val cx = x + w / 2f
    val cy = y + h / 2f
    val start = Math.toRadians(startDegrees.toDouble())
    val sweep = Math.toRadians(sweepDegrees.toDouble())
    return buildPath(count) { i ->
        val angle = start + sweep * i / segments
        (cx + w / 2f * cos(angle).toFloat()) to (cy + h / 2f * sin(angle).toFloat())
    }
}

fun RenderContext.fillPath(points: FloatArray, color: PolyColor) {
    if (color.alpha == 0 || points.size < 6) return
    val path = PolyPath()
    path.moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) path.lineTo(points[i], points[i + 1])
    path.close()
    fillPath(path, color)
}

fun RenderContext.strokePath(points: FloatArray, color: PolyColor, strokeWidth: Float) {
    if (color.alpha == 0) return
    renderer.strokePolyline(points, true, color.argb, strokeWidth, PolyRenderer.CAP_BUTT, PolyRenderer.JOIN_MITER)
}

fun RenderContext.oval(x: Float, y: Float, w: Float, h: Float, color: PolyColor) {
    if (color.alpha == 0) return
    fillPath(arcPoints(x, y, w, h, 0f, 360f, true), color)
}

fun RenderContext.ovalStroke(x: Float, y: Float, w: Float, h: Float, color: PolyColor, strokeWidth: Float) {
    if (color.alpha == 0) return
    strokePath(arcPoints(x, y, w, h, 0f, 360f, true), color, strokeWidth)
}

fun RenderContext.arcStroke(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    startAngle: Float,
    sweep: Float,
    color: PolyColor,
    thickness: Float,
) {
    if (color.alpha == 0) return
    val inset = thickness / 2f
    val closed = abs(sweep) >= 360f
    val points = arcPoints(x + inset, y + inset, w - thickness, h - thickness, startAngle - 90f, if (closed) 360f else sweep, closed)
    renderer.strokePolyline(points, closed, color.argb, thickness, PolyRenderer.CAP_BUTT, PolyRenderer.JOIN_ROUND)
}
