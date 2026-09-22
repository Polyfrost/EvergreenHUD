package org.polyfrost.evergreenhud.client.utils

import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Rect
import kotlin.math.roundToInt

fun snapToPixels(canvas: Canvas, x: Float, y: Float, w: Float, h: Float): Rect {
    val m = canvas.localToDeviceAsMatrix33.mat
    val scaleX = m[0]
    val scaleY = m[4]
    if (m[1] != 0f || m[3] != 0f || scaleX == 0f || scaleY == 0f) return Rect.makeXYWH(x, y, w, h)
    val transX = m[2]
    val transY = m[5]

    fun snap(start: Float, size: Float, scale: Float, trans: Float): Pair<Float, Float> {
        val from = (start * scale + trans).roundToInt()
        val to = ((start + size) * scale + trans).roundToInt()
        // rounding can put both edges on the same pixel, resulting in 0px
        val kept = if (to != from || size == 0f) to else from + 1
        return (from - trans) / scale to (kept - trans) / scale
    }

    val (left, right) = snap(x, w, scaleX, transX)
    val (top, bottom) = snap(y, h, scaleY, transY)
    return Rect.makeLTRB(left, top, right, bottom)
}
