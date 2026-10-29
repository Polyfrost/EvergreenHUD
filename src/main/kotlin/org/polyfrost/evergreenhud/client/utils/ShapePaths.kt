package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.PolyPath
import org.polyfrost.compose.render.RenderContext
import java.awt.Shape
import java.awt.geom.PathIterator

private const val FLATNESS = 0.02

fun Shape.toPolyPath(): PolyPath {
    val path = PolyPath()
    val iterator = getPathIterator(null, FLATNESS)
    val coords = FloatArray(6)
    while (!iterator.isDone) {
        when (iterator.currentSegment(coords)) {
            PathIterator.SEG_MOVETO -> path.moveTo(coords[0], coords[1])
            PathIterator.SEG_LINETO -> path.lineTo(coords[0], coords[1])
            PathIterator.SEG_CLOSE -> path.close()
        }
        iterator.next()
    }
    return path
}

fun RenderContext.fillShape(shape: Shape, color: PolyColor) = fillPath(shape.toPolyPath(), color)
