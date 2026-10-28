package org.polyfrost.evergreenhud.client.utils

import androidx.compose.runtime.Composable
import org.jetbrains.skia.Font as SkiaFont
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.layout.PolyInsets
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import kotlin.math.max

private const val LINE_GAP = 2f

private const val POPPINS_SIZE = 8f

@Composable
fun Hud.HudStyledLines(lines: List<List<StyledRun>>, alignColumns: Boolean = false) =
    HudStyledCells(lines.map { line -> line.map { it.asCell() } }, alignColumns)

@Composable
fun Hud.HudStyledCells(lines: List<List<StyledCell>>, alignColumns: Boolean = false) {
    val cased = lines.mapRunText(::applyCase)

    val padInsets = PolyInsets(padLeft, padTop, padRight, padBottom)
    val isStaticValid = staticWidth && staticW > 0f && staticH > 0f

    val bgModifier = hudBackground()
    val outerModifier =
        if (isStaticValid) bgModifier.size(staticW, staticH).padding(padInsets)
        else bgModifier.padding(padInsets)

    val lineAlign = if (isStaticValid) alignment else PolyAlign.Left
    val skiaFont = if (font == Font.Poppins) FontManager.getFont(POPPINS_SIZE * textScale, getPoppinsFontName()) else null
    val columnOffsets = if (alignColumns) columnOffsets(cased, skiaFont) else null

    PolyBox(modifier = outerModifier) {
        PolyColumn(
            gap = LINE_GAP * textScale,
            modifier = if (isStaticValid) PolyModifier.align(alignment) else PolyModifier,
        ) {
            for (line in cased) StyledLine(line, lineAlign, skiaFont, columnOffsets)
        }
    }
}

private fun List<List<StyledCell>>.mapRunText(transform: (String) -> String): List<List<StyledCell>> =
    map { line -> line.map { cell -> StyledCell(cell.runs.map { it.copy(text = transform(it.text)) }) } }

private fun Hud.runFont(run: StyledRun, skiaFont: SkiaFont?): SkiaFont? {
    if (skiaFont == null) return null
    if (!run.bold && !run.italic) return skiaFont

    val base = if (run.bold) "poppins-bold" else getPoppinsFontName().removeSuffix("-italic")
    val name = if (run.italic || textItalic) "$base-italic" else base
    return FontManager.getFont(POPPINS_SIZE * textScale, name)
}

private fun Hud.mcRunText(run: StyledRun): String = buildString {
    if (run.bold || textBold) append("§l")
    if (run.italic || textItalic) append("§o")
    append(run.text)
}

private fun Hud.runWidth(run: StyledRun, skiaFont: SkiaFont?): Float =
    if (skiaFont != null) (runFont(run, skiaFont) ?: skiaFont).measureTextWidth(run.text)
    else McFontQueue.measureTextWidth(mcRunText(run), textScale)

private fun Hud.cellWidths(cells: List<StyledCell>, skiaFont: SkiaFont?): List<Float> =
    cells.map { cell -> cell.runs.sumOf { runWidth(it, skiaFont).toDouble() }.toFloat() }

private fun Hud.columnOffsets(lines: List<List<StyledCell>>, skiaFont: SkiaFont?): List<Float> {
    val columns = lines.maxOfOrNull { it.size } ?: return emptyList()
    val widths = FloatArray(columns)
    for (line in lines) {
        val lineWidths = cellWidths(line, skiaFont)
        for (i in lineWidths.indices) widths[i] = max(widths[i], lineWidths[i])
    }

    var offset = 0f
    return List(columns) { i ->
        val start = offset
        offset += widths[i]
        start
    }
}

@Composable
private fun Hud.StyledLine(cells: List<StyledCell>, alignment: PolyAlign, skiaFont: SkiaFont?, columnOffsets: List<Float>?) {
    val fg = PolyColor(textColor, textChroma, textChromaSpeed)
    val widths = cellWidths(cells, skiaFont)
    val cellOffsets = columnOffsets ?: widths.runningFold(0f) { acc, w -> acc + w }
    val width = (cells.indices.maxOfOrNull { cellOffsets[it] + widths[it] } ?: 0f).coerceAtLeast(1f)

    val placed = ArrayList<Pair<StyledRun, Float>>()
    for ((i, cell) in cells.withIndex()) {
        var offset = cellOffsets[i]
        for (run in cell.runs) {
            placed.add(run to offset)
            offset += runWidth(run, skiaFont)
        }
    }

    if (skiaFont != null) {
        val metrics = skiaFont.metrics
        val height = metrics.descent - metrics.ascent

        PolyCanvas(PolyModifier.size(width, height).align(alignment)) { x, y, _, _ ->
            val baseline = y - metrics.ascent
            if (showShadow) {
                val shadowCol = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed)
                for ((run, offset) in placed) {
                    text(run.text, x + offset + shadowOffsetX, baseline + shadowOffsetY, shadowCol, runFont(run, skiaFont) ?: skiaFont)
                }
            }
            for ((run, offset) in placed) {
                val color = run.color ?: fg
                text(run.text, x + offset, baseline, color, runFont(run, skiaFont) ?: skiaFont)
            }
        }
    } else {
        val texts = placed.map { (run, _) -> mcRunText(run) }
        val height = McFontQueue.measureTextHeight(textScale)

        PolyCanvas(PolyModifier.size(width, height).align(alignment)) { x, y, _, _ ->
            val renderer = McFontQueue.renderer ?: return@PolyCanvas
            for ((i, entry) in placed.withIndex()) {
                val (run, offset) = entry
                val color = run.color?.argb ?: fg.argb
                renderer(canvas, texts[i], x + offset, y, color, showShadow, textScale)
            }
        }
    }
}
