@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud.armor

import androidx.compose.runtime.snapshots.Snapshot
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor
import org.polyfrost.oneconfig.api.hud.v1.HudGroups
import org.polyfrost.oneconfig.api.hud.v1.HudPresetAction
import org.polyfrost.oneconfig.api.hud.v1.HudPresetArrangement
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import kotlin.math.abs

internal object ArmorLayout {
    const val DEFAULT_GAP = 5f
    private const val DEFAULT_SPACING_TOLERANCE = 0.01f

    private data class ChainAnchors(val target: HudAnchor, val self: HudAnchor, val pin: HudAnchor)

    private data class Shape(val vertical: Boolean, val reversed: Boolean)

    val actions = listOf(
        action("horizontal", "Horizontal", "Line the armor pieces of this group up in a row.", ::arrangeHorizontal),
        action("vertical", "Vertical", "Stack the armor pieces of this group in a column.") { rearrange(it, vertical = true, reversed = shape(it).reversed) },
        action("reverse", "Reverse Order", "Flip the order of the armor pieces of this group.") {
            val shape = shape(it)
            rearrange(it, shape.vertical, !shape.reversed)
        },
    )

    private fun anchorsFor(vertical: Boolean, reversed: Boolean, textPosition: Int): ChainAnchors {
        if (!vertical) {
            return if (reversed) ChainAnchors(HudAnchor.Left, HudAnchor.Right, HudAnchor.Right)
            else ChainAnchors(HudAnchor.Right, HudAnchor.Left, HudAnchor.Left)
        }
        val (top, bottom) = when (textPosition) {
            TEXT_RIGHT -> HudAnchor.TopLeft to HudAnchor.BottomLeft
            TEXT_LEFT -> HudAnchor.TopRight to HudAnchor.BottomRight
            else -> HudAnchor.Top to HudAnchor.Bottom
        }
        return if (reversed) ChainAnchors(top, bottom, bottom) else ChainAnchors(bottom, top, top)
    }

    private fun shapeOf(target: HudAnchor): Shape = Shape(
        vertical = target != HudAnchor.Left && target != HudAnchor.Right,
        reversed = target in REVERSED_TARGETS,
    )

    fun isVertical(pieces: List<ArmorPieceHud>): Boolean {
        if (pieces.size < 2) return false
        val spreadX = pieces.maxOf { it.x + it.scaledWidth / 2f } - pieces.minOf { it.x + it.scaledWidth / 2f }
        val spreadY = pieces.maxOf { it.y + it.scaledHeight / 2f } - pieces.minOf { it.y + it.scaledHeight / 2f }
        return spreadY > spreadX
    }

    private fun shape(pieces: List<ArmorPieceHud>): Shape {
        val ordered = inOrder(pieces)
        val second = ordered.getOrNull(1)
        return if (second != null && ordered.any { it === second.anchorParent }) shapeOf(second.anchorPoint)
        else Shape(isVertical(ordered), false)
    }

    fun gap(pieces: List<ArmorPieceHud>, spacing: Float = DEFAULT_GAP): Float {
        val root = inOrder(pieces).firstOrNull() ?: return 0f
        return if (root.isVanilla() || pieces.all { it.showBackground && it.mergeBackground }) 0f
        else spacing * root.textScale * root.effectiveScale
    }

    fun currentSpacing(pieces: List<ArmorPieceHud>): Float? {
        if (pieces.size < 2 || !isIntact(pieces)) return null
        val ordered = inOrder(pieces)
        val unit = ordered[0].textScale * ordered[0].effectiveScale
        if (unit <= 0f) return null
        val offset = if (shape(pieces).vertical) ordered[1].anchorOffsetY else ordered[1].anchorOffsetX
        return abs(offset) / unit
    }

    fun keptGap(pieces: List<ArmorPieceHud>): Float {
        val spacing = currentSpacing(pieces) ?: return gap(pieces)
        if (spacing < DEFAULT_SPACING_TOLERANCE || abs(spacing - DEFAULT_GAP) < DEFAULT_SPACING_TOLERANCE) return gap(pieces)
        val root = inOrder(pieces).first()
        return spacing * root.textScale * root.effectiveScale
    }

    fun chain(pieces: List<ArmorPieceHud>, vertical: Boolean, reversed: Boolean, gap: Float) {
        val ordered = inOrder(pieces)
        val root = ordered.firstOrNull() ?: return
        val anchors = anchorsFor(vertical, reversed, root.textPosition)
        for (piece in ordered) {
            if (ordered.any { it === piece.anchorParent }) piece.clearAnchor()
        }
        if (root.anchorParent == null && root.growthAnchor != anchors.pin) root.setGrowthAnchorKeepingPosition(anchors.pin)
        val step = if (reversed) -gap else gap
        for ((previous, piece) in ordered.zipWithNext()) {
            piece.anchorTo(previous, anchors.target, anchors.self)
            piece.anchorOffsetX = if (vertical) 0f else step
            piece.anchorOffsetY = if (vertical) step else 0f
        }
    }

    fun isIntact(pieces: List<ArmorPieceHud>): Boolean =
        inOrder(pieces).zipWithNext().all { (previous, piece) -> piece.anchorParent === previous }

    fun rechain(pieces: List<ArmorPieceHud>) {
        if (pieces.size < 2 || !isIntact(pieces)) return
        val shape = shape(pieces)
        Snapshot.withMutableSnapshot { chain(pieces, shape.vertical, shape.reversed, keptGap(pieces)) }
    }

    fun fractionX(point: HudAnchor): Float = when (point) {
        HudAnchor.TopLeft, HudAnchor.Left, HudAnchor.BottomLeft -> 0f
        HudAnchor.TopRight, HudAnchor.Right, HudAnchor.BottomRight -> 1f
        else -> 0.5f
    }

    fun fractionY(point: HudAnchor): Float = when (point) {
        HudAnchor.TopLeft, HudAnchor.Top, HudAnchor.TopRight -> 0f
        HudAnchor.BottomLeft, HudAnchor.Bottom, HudAnchor.BottomRight -> 1f
        else -> 0.5f
    }

    private fun arrangeHorizontal(pieces: List<ArmorPieceHud>) {
        val ordered = inOrder(pieces)
        val root = ordered.first()
        if (!ordered.all { it.isAtDefaultPosition }) return rearrange(pieces, vertical = false, reversed = shape(pieces).reversed)
        val reversed = !root.mainHandOnRight()
        chain(ordered, vertical = false, reversed = reversed, gap = gap(ordered))
        val pin = anchorsFor(vertical = false, reversed = reversed, textPosition = root.textPosition).pin
        val (x, y) = root.rowStart()
        val (width, height) = root.nominalSize()
        shift(root, x + fractionX(pin) * width - root.anchorPointX(pin), y + fractionY(pin) * height - root.anchorPointY(pin))
    }

    private fun rearrange(pieces: List<ArmorPieceHud>, vertical: Boolean, reversed: Boolean) {
        val ordered = inOrder(pieces)
        val root = ordered.first()
        val pin = anchorsFor(vertical, reversed, root.textPosition).pin
        val before = HudGroups.bounds(ordered)
        chain(ordered, vertical, reversed, keptGap(ordered))
        val after = HudGroups.bounds(ordered)
        if (before == null || after == null || root.anchorParent != null) return
        shift(
            root,
            before[0] + fractionX(pin) * before[2] - (after[0] + fractionX(pin) * after[2]),
            before[1] + fractionY(pin) * before[3] - (after[1] + fractionY(pin) * after[3]),
        )
    }

    fun shift(root: ArmorPieceHud, dx: Float, dy: Float) = root.setAbsolutePosition(root.x + dx, root.y + dy)

    private fun inOrder(pieces: List<ArmorPieceHud>): List<ArmorPieceHud> = pieces.sortedBy { it.piece.ordinal }

    private fun action(id: String, name: String, description: String, arrange: (List<ArmorPieceHud>) -> Unit) =
        HudPresetAction(
            id,
            name,
            HudPresetArrangement { huds ->
                val pieces = huds.filterIsInstance<ArmorPieceHud>()
                if (pieces.size >= 2) arrange(pieces)
            },
            description,
        )

    private val REVERSED_TARGETS = setOf(HudAnchor.Left, HudAnchor.TopLeft, HudAnchor.Top, HudAnchor.TopRight)
}
