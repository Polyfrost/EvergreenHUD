package org.polyfrost.evergreenhud.client.hud.armor

import org.polyfrost.evergreenhud.client.hud.item.HotbarSeams

private const val LAST = 21
private const val EDGE_TOLERANCE = 0.25f

internal fun <T : Any> anchoredNeighbours(
    self: T,
    pieces: List<T>,
    parentOf: (T) -> T?,
    drawn: (T) -> Boolean,
): List<T> {
    fun nearestDrawnParent(piece: T): T? {
        var parent = parentOf(piece)
        var steps = pieces.size
        while (parent != null && !drawn(parent) && steps-- > 0) parent = parentOf(parent)
        return parent?.takeIf(drawn)
    }

    val parent = nearestDrawnParent(self)
    return pieces.filter { it !== self && drawn(it) && (it === parent || nearestDrawnParent(it) === self) }
}

internal fun drawnRect(box: FloatArray, applied: HotbarSeams, border: Float): FloatArray = floatArrayOf(
    box[0] - if (applied.cutLeft) border else 0f,
    box[1] - if (applied.cutTop) border else 0f,
    box[2],
    box[3],
)

internal fun seams(nominalSelf: FloatArray, nominalOthers: List<FloatArray>, px: Float): HotbarSeams {
    val tolerance = EDGE_TOLERANCE * px

    fun covered(col: Int, row: Int): Boolean {
        val cx = nominalSelf[0] + (col + 0.5f) * px
        val cy = nominalSelf[1] + (row + 0.5f) * px
        return nominalOthers.any {
            cx > it[0] - tolerance && cx < it[0] + it[2] + tolerance &&
                cy > it[1] - tolerance && cy < it[1] + it[3] + tolerance
        }
    }

    fun open(col: Int, row: Int): Boolean {
        val dx = when (col) {
            0 -> -1
            LAST -> 1
            else -> 0
        }
        val dy = when (row) {
            0 -> -1
            LAST -> 1
            else -> 0
        }
        return when {
            dx != 0 && dy != 0 -> covered(col + dx, row) && covered(col, row + dy) && covered(col + dx, row + dy)
            dy == 0 -> covered(col + dx, row) && covered(col + dx, row - 1) && covered(col + dx, row + 1)
            else -> covered(col, row + dy) && covered(col - 1, row + dy) && covered(col + 1, row + dy)
        }
    }

    fun mask(range: IntRange, isOpen: (Int) -> Boolean): Int =
        range.fold(0) { mask, i -> if (isOpen(i)) mask or (1 shl i) else mask }

    return HotbarSeams(
        top = mask(0..LAST) { open(it, 0) },
        bottom = mask(0..LAST) { open(it, LAST) },
        left = mask(1 until LAST) { open(0, it) },
        right = mask(1 until LAST) { open(LAST, it) },
        cutTop = (0..LAST).all { covered(it, -1) },
        cutBottom = (0..LAST).all { covered(it, LAST + 1) },
        cutLeft = (0..LAST).all { covered(-1, it) },
        cutRight = (0..LAST).all { covered(LAST + 1, it) },
    )
}
