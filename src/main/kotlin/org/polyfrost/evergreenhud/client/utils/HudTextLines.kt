package org.polyfrost.evergreenhud.client.utils

import androidx.compose.runtime.Composable
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.background
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.layout.PolyInsets
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.PolyFont
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud

private const val LINE_GAP = 2f

private const val POPPINS_SIZE = 8f

fun Hud.applyCase(text: String, caseType: Int = this.caseType): String = when (caseType) {
    1 -> text.uppercase()
    2 -> text.lowercase()
    else -> text
}

@Composable
fun Hud.HudTextLines(lines: List<String>) {
    val cased = lines.map(::applyCase)

    val padInsets = PolyInsets(padLeft, padTop, padRight, padBottom)
    val isStaticValid = staticWidth && staticW > 0f && staticH > 0f

    val outerModifier = if (showBackground) {
        val bgModifier = PolyModifier.background(PolyColor(bgColor, bgChroma, bgChromaSpeed), bgRadius)
        if (isStaticValid) bgModifier.size(staticW, staticH).padding(padInsets)
        else bgModifier.padding(padInsets)
    } else {
        if (isStaticValid) PolyModifier.size(staticW, staticH).padding(padInsets)
        else PolyModifier.padding(padInsets)
    }

    val lineAlign = if (isStaticValid) alignment else PolyAlign.Left

    PolyBox(modifier = outerModifier) {
        PolyColumn(
            gap = LINE_GAP * textScale,
            modifier = if (isStaticValid) PolyModifier.align(alignment) else PolyModifier,
        ) {
            for (line in cased) Line(line, lineAlign)
        }
    }
}

@Composable
private fun Hud.Line(line: String, alignment: PolyAlign) {
    val fg = PolyColor(textColor, textChroma, textChromaSpeed)

    if (font == Font.Poppins) {
        val polyFont = PolyFont(getPoppinsFontName(), POPPINS_SIZE * textScale)
        val metrics = polyFont.metrics
        val width = polyFont.measureTextWidth(line).coerceAtLeast(1f)
        val height = metrics.descent - metrics.ascent

        PolyCanvas(PolyModifier.size(width, height).align(alignment)) { x, y, _, _ ->
            val baseline = y - metrics.ascent
            if (showShadow) {
                val shadowCol = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed)
                text(line, x + shadowOffsetX, baseline + shadowOffsetY, shadowCol, polyFont)
                underline(this@Line, x + shadowOffsetX, baseline + shadowOffsetY, width, shadowCol, polyFont)
            }
            text(line, x, baseline, fg, polyFont)
            underline(this@Line, x, baseline, width, fg, polyFont)
        }
    } else {
        val formatted = buildString {
            if (textBold) append("§l")
            if (textItalic) append("§o")
            if (textUnderline) append("§n")
            append(line)
            if (textBold || textItalic || textUnderline) append("§r")
        }

        PolyMcText(
            text = formatted,
            color = fg,
            shadow = showShadow,
            scale = textScale,
            modifier = PolyModifier.align(alignment),
        )
    }
}

private fun RenderContext.underline(hud: Hud, x: Float, baseline: Float, width: Float, color: PolyColor, font: PolyFont) {
    if (!hud.textUnderline) return
    val position = font.metrics.underlinePosition
    val thickness = font.metrics.underlineThickness
    line(x, baseline + position, x + width, baseline + position, color, thickness)
}

private const val FALLBACK_CAP_HEIGHT = 0.7f

private val capHeightRatios = ConcurrentHashMap<String, Float>()

val PolyFont.capHeight: Float
    get() = size * capHeightRatios.getOrPut(name) { FontManager.fontData(name)?.let(::readCapHeightRatio) ?: FALLBACK_CAP_HEIGHT }

private fun readCapHeightRatio(data: ByteArray): Float? = runCatching {
    val buf = ByteBuffer.wrap(data)
    var head = -1
    var os2 = -1
    for (i in 0 until (buf.getShort(4).toInt() and 0xFFFF)) {
        val record = 12 + i * 16
        when (String(data, record, 4, Charsets.US_ASCII)) {
            "head" -> head = buf.getInt(record + 8)
            "OS/2" -> os2 = buf.getInt(record + 8)
        }
    }
    if (head < 0 || os2 < 0 || (buf.getShort(os2).toInt() and 0xFFFF) < 2) return null
    val unitsPerEm = buf.getShort(head + 18).toInt() and 0xFFFF
    val capHeight = buf.getShort(os2 + 88).toInt()
    if (unitsPerEm == 0 || capHeight <= 0) null else capHeight.toFloat() / unitsPerEm
}.getOrNull()
