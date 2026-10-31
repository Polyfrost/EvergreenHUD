package org.polyfrost.evergreenhud.client.hud.battery

import androidx.compose.runtime.Composable
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.fillShape
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D

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
        rectStroke(
            RING / 2f, RING / 2f, BODY_WIDTH - RING, CELL_HEIGHT - RING,
            outline, RING, BODY_RADIUS - RING / 2f,
        )
        rect(
            BODY_WIDTH + NUB_GAP, (CELL_HEIGHT - NUB_HEIGHT) / 2f,
            NUB_WIDTH, NUB_HEIGHT,
            outline, NUB_WIDTH / 2f,
        )

        val boltShape = if (bolt != null) boltShape() else null
        val track = BODY_WIDTH - FILL_INSET * 2
        if (percent > 0) {
            val width = (track * percent / 100f).coerceIn(MIN_FILL, track)
            if (boltShape == null) {
                rect(FILL_INSET, FILL_INSET, width, CELL_HEIGHT - FILL_INSET * 2, fill, FILL_RADIUS)
            } else {
                val area = Area(
                    RoundRectangle2D.Float(FILL_INSET, FILL_INSET, width, CELL_HEIGHT - FILL_INSET * 2, FILL_RADIUS * 2, FILL_RADIUS * 2),
                )
                area.subtract(Area(BasicStroke(BOLT_CUTOUT, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND).createStrokedShape(boltShape)))
                area.subtract(Area(boltShape))
                fillShape(area, fill)
            }
        }

        if (bolt != null && boltShape != null) fillShape(boltShape, bolt)

        restore()
    }
}

private fun boltShape(): Shape {
    val left = (BODY_WIDTH - BOLT_WIDTH) / 2f
    val top = (CELL_HEIGHT - BOLT_HEIGHT) / 2f
    val path = Path2D.Float()
    for (i in BOLT.indices step 2) {
        val x = left + BOLT[i] * BOLT_WIDTH
        val y = top + BOLT[i + 1] * BOLT_HEIGHT
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.closePath()
    return path
}
