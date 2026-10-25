package org.polyfrost.evergreenhud.client.hud.battery

import androidx.compose.runtime.Composable
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.RRect
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor

const val CELL_WIDTH = 24f
const val CELL_HEIGHT = 11f

private const val BODY_WIDTH = 22f
private const val BODY_RADIUS = 2.5f
private const val RING = 1f
private const val FILL_INSET = 2f
private const val FILL_RADIUS = 1f
private const val MIN_FILL = 1.5f
private const val NUB_GAP = 0.5f
private const val NUB_WIDTH = 1.5f
private const val NUB_HEIGHT = 5f
private const val BOLT_WIDTH = 5f
private const val BOLT_HEIGHT = 7f
private const val BOLT_CUTOUT = 1.6f

private val BOLT = floatArrayOf(
    0.62f, 0f,
    0f, 0.58f,
    0.45f, 0.58f,
    0.36f, 1f,
    1f, 0.40f,
    0.55f, 0.40f,
)

@Composable
fun BatteryCell(
    percent: Int,
    fill: PolyColor,
    outline: PolyColor,
    bolt: PolyColor?,
    scale: Float = 1f,
    modifier: PolyModifier = PolyModifier.size(CELL_WIDTH * scale, CELL_HEIGHT * scale),
) {
    PolyCanvas(modifier) { x, y, _, height ->
        val unit = height / CELL_HEIGHT
        save()
        translate(x, y)
        scale(unit, unit)
        canvas.saveLayer(null, null)

        paint.mode = PaintMode.FILL
        paint.color = outline.argb
        canvas.drawDRRect(
            RRect.makeXYWH(0f, 0f, BODY_WIDTH, CELL_HEIGHT, BODY_RADIUS),
            RRect.makeXYWH(RING, RING, BODY_WIDTH - RING * 2, CELL_HEIGHT - RING * 2, BODY_RADIUS - RING),
            paint,
        )
        rect(
            BODY_WIDTH + NUB_GAP, (CELL_HEIGHT - NUB_HEIGHT) / 2f,
            NUB_WIDTH, NUB_HEIGHT,
            outline, NUB_WIDTH / 2f,
        )

        val track = BODY_WIDTH - FILL_INSET * 2
        if (percent > 0) {
            val width = (track * percent / 100f).coerceIn(MIN_FILL, track)
            rect(FILL_INSET, FILL_INSET, width, CELL_HEIGHT - FILL_INSET * 2, fill, FILL_RADIUS)
        }

        if (bolt != null) {
            val left = (BODY_WIDTH - BOLT_WIDTH) / 2f
            val top = (CELL_HEIGHT - BOLT_HEIGHT) / 2f
            PathBuilder().use { builder ->
                for (i in BOLT.indices step 2) {
                    val x = left + BOLT[i] * BOLT_WIDTH
                    val y = top + BOLT[i + 1] * BOLT_HEIGHT
                    if (i == 0) builder.moveTo(x, y) else builder.lineTo(x, y)
                }
                builder.closePath()
                builder.detach().use { path ->
                    Paint().use { cutout ->
                        cutout.isAntiAlias = true
                        cutout.blendMode = BlendMode.CLEAR
                        cutout.mode = PaintMode.STROKE
                        cutout.strokeWidth = BOLT_CUTOUT
                        cutout.strokeJoin = PaintStrokeJoin.ROUND
                        canvas.drawPath(path, cutout)
                    }
                    paint.color = bolt.argb
                    canvas.drawPath(path, paint)
                }
            }
        }

        canvas.restore()
        restore()
    }
}
