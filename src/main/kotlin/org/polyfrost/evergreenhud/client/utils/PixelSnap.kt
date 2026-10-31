package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.compose.render.RenderContext
import kotlin.math.roundToInt

fun snapToPixels(ctx: RenderContext, x: Float, y: Float, w: Float, h: Float): FloatArray {
    val m = ctx.deviceTransform()
    if (m == null || m[0] == 0f || m[1] == 0f) return floatArrayOf(x, y, x + w, y + h)
    val scaleX = m[0]
    val scaleY = m[1]
    val transX = m[2]
    val transY = m[3]

    fun snap(start: Float, size: Float, scale: Float, trans: Float): Pair<Float, Float> {
        val from = (start * scale + trans).roundToInt()
        val to = ((start + size) * scale + trans).roundToInt()
        // rounding can put both edges on the same pixel, resulting in 0px
        val kept = if (to != from || size == 0f) to else from + 1
        return (from - trans) / scale to (kept - trans) / scale
    }

    val (left, right) = snap(x, w, scaleX, transX)
    val (top, bottom) = snap(y, h, scaleY, transY)
    return floatArrayOf(left, top, right, bottom)
}
