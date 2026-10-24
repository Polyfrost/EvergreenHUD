package org.polyfrost.evergreenhud.client.hud.media

import androidx.compose.runtime.Composable
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.Font
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.jetbrains.skia.Rect
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import kotlin.math.hypot

const val BUTTON_PREVIOUS = 0
const val BUTTON_TOGGLE = 1
const val BUTTON_NEXT = 2

data class MediaCardState(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canToggle: Boolean,
    val art: Image?,
    val artAccent: Int?,
)

object MediaCardLayout {
    const val INSET = 6f
    const val ART_SIZE = 38f
    const val ART_RADIUS = 6f
    const val MIN_TEXT_WIDTH = 72f
    const val MAX_TEXT_WIDTH = 160f
    const val PROGRESS_TOP = ART_SIZE + 7f
    const val PROGRESS_HEIGHT = 6f
    const val HEIGHT = PROGRESS_TOP + PROGRESS_HEIGHT
    const val BUTTON_RADIUS = 9f

    private const val ART_GAP = 8f
    private const val BUTTON_SPACING = 24f
    private const val HIT_RADIUS = 11.5f

    fun textX(showArt: Boolean): Float = if (showArt) ART_SIZE + ART_GAP else 0f

    fun width(showArt: Boolean, textWidth: Float): Float = textX(showArt) + textWidth

    fun buttonY(): Float = ART_SIZE - BUTTON_RADIUS

    fun buttonX(button: Int, showArt: Boolean, textWidth: Float): Float =
        textX(showArt) + textWidth / 2f + (button - BUTTON_TOGGLE) * BUTTON_SPACING

    fun buttonAt(x: Float, y: Float, showArt: Boolean, textWidth: Float): Int {
        for (button in BUTTON_PREVIOUS..BUTTON_NEXT) {
            if (hypot(x - buttonX(button, showArt, textWidth), y - buttonY()) <= HIT_RADIUS) return button
        }
        return -1
    }
}

private const val TITLE_SIZE = 8f
private const val ARTIST_SIZE = 7f
private const val TIME_SIZE = 6f
private const val TITLE_BASELINE = 7f
private const val SCROLL_PAUSE_MS = 2000L
private const val SCROLL_SPEED = 0.02f
private const val SCROLL_GAP = 24f
private const val ARTIST_BASELINE = 16f
private const val TIME_GAP = 4f
private const val BAR_HEIGHT = 2.5f
private const val KNOB_RADIUS = 2.2f
private const val DISABLED_ALPHA = 0.3f
private const val HOVER_ALPHA = 0.16f
private const val DIM_ALPHA = 0.6f
private const val TRACK_ALPHA = 0.2f
private val PLAY_GLYPH = PolyColor(0xFF101014.toInt())
private val ART_SAMPLING = FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE)

@Composable
fun MediaCard(
    state: MediaCardState,
    showArt: Boolean,
    showTimes: Boolean,
    text: PolyColor,
    accent: PolyColor,
    fontName: String?,
    shadow: Boolean,
    hovered: Int,
    scrollMs: Long,
    bounce: Boolean,
    scale: Float,
    artRadius: Float = MediaCardLayout.ART_RADIUS,
    textWidth: Float = textWidth(state, fontName),
) {
    val width = MediaCardLayout.width(showArt, textWidth)
    PolyCanvas(PolyModifier.size(width * scale, MediaCardLayout.HEIGHT * scale)) { x, y, _, height ->
        val unit = height / MediaCardLayout.HEIGHT
        save()
        translate(x, y)
        scale(unit, unit)
        paint.isAntiAlias = true

        if (showArt) drawArt(state.art, text, artRadius)
        val left = MediaCardLayout.textX(showArt)
        drawLabels(state, left, textWidth, text, fontName, shadow, scrollMs, bounce)
        for (button in BUTTON_PREVIOUS..BUTTON_NEXT) {
            val enabled = when (button) {
                BUTTON_PREVIOUS -> state.canPrevious
                BUTTON_NEXT -> state.canNext
                else -> state.canToggle
            }
            drawButton(button, MediaCardLayout.buttonX(button, showArt, textWidth), MediaCardLayout.buttonY(), state.playing, enabled, hovered == button, text, accent)
        }
        drawProgress(state, width, showTimes, text, accent, fontName, shadow)

        restore()
    }
}

private fun RenderContext.drawArt(art: Image?, text: PolyColor, radius: Float) {
    val size = MediaCardLayout.ART_SIZE
    save()
    clipRRect(0f, 0f, size, size, radius)
    if (art == null) {
        rect(0f, 0f, size, size, text.multiplyAlpha(0.12f))
        drawNote(size / 2f, size / 2f, text.multiplyAlpha(0.5f))
    } else {
        val side = minOf(art.width, art.height).toFloat()
        val src = Rect.makeXYWH((art.width - side) / 2f, (art.height - side) / 2f, side, side)
        paint.color = 0xFFFFFFFF.toInt()
        paint.mode = PaintMode.FILL
        canvas.drawImageRect(art, src, Rect.makeWH(size, size), ART_SAMPLING, paint, true)
    }
    restore()
}

private fun RenderContext.drawNote(cx: Float, cy: Float, color: PolyColor) {
    paint.color = color.argb
    paint.mode = PaintMode.FILL
    canvas.drawOval(Rect.makeXYWH(cx - 6f, cy + 3f, 6f, 4.6f), paint)
    rect(cx - 1.4f, cy - 8f, 1.6f, 12.4f, color, 0.8f)
    path(
        color, fill = true, stroke = 0f,
        cx, cy - 8f,
        cx + 6f, cy - 5.5f,
        cx + 5.4f, cy - 1.6f,
        cx + 0.2f, cy - 3.6f,
    )
}

private class Label(size: Float, fontName: String?, private val shadow: Boolean = false) {
    private val skia: Font? = fontName?.let { FontManager.getFont(size, it) }
    private val mcScale = size / MC_LINE_HEIGHT

    val capHeight: Float get() = skia?.metrics?.capHeight ?: (MC_CAP_HEIGHT * mcScale)

    fun width(text: String): Float = skia?.measureTextWidth(text) ?: McFontQueue.measureTextWidth(text, mcScale)

    fun RenderContext.draw(text: String, x: Float, baseline: Float, color: PolyColor) {
        if (skia != null) return text(text, x, baseline, color, skia)
        val renderer = McFontQueue.renderer ?: return
        renderer(canvas, text, x, baseline - MC_CAP_HEIGHT * mcScale, color.argb, shadow, mcScale)
    }
}

private const val MC_LINE_HEIGHT = 9f
private const val MC_CAP_HEIGHT = 7f

fun textWidth(state: MediaCardState, fontName: String?): Float {
    val title = Label(TITLE_SIZE, fontName).width(state.title)
    val artist = Label(ARTIST_SIZE, fontName).width(state.artist)
    return maxOf(title, artist).coerceIn(MediaCardLayout.MIN_TEXT_WIDTH, MediaCardLayout.MAX_TEXT_WIDTH)
}

fun titleOverflows(title: String, fontName: String?, maxWidth: Float = MediaCardLayout.MAX_TEXT_WIDTH): Boolean =
    Label(TITLE_SIZE, fontName).width(title) > maxWidth

private fun RenderContext.drawLabels(
    state: MediaCardState,
    left: Float,
    textWidth: Float,
    text: PolyColor,
    fontName: String?,
    shadow: Boolean,
    scrollMs: Long,
    bounce: Boolean,
) {
    val title = Label(TITLE_SIZE, fontName, shadow)
    val titleWidth = title.width(state.title)
    if (titleWidth <= textWidth) {
        with(title) { draw(state.title, left, TITLE_BASELINE, text) }
    } else {
        save()
        canvas.clipRect(Rect.makeLTRB(left, -MediaCardLayout.INSET, left + textWidth, ARTIST_BASELINE), true)
        if (bounce) {
            val overflow = titleWidth - textWidth
            val legMs = SCROLL_PAUSE_MS + (overflow / SCROLL_SPEED).toLong()
            val t = scrollMs % (legMs * 2)
            val slid = ((t % legMs - SCROLL_PAUSE_MS).coerceAtLeast(0L) * SCROLL_SPEED).coerceAtMost(overflow)
            val offset = if (t < legMs) slid else overflow - slid
            with(title) { draw(state.title, left - offset, TITLE_BASELINE, text) }
        } else {
            val lap = titleWidth + SCROLL_GAP
            val lapMs = SCROLL_PAUSE_MS + (lap / SCROLL_SPEED).toLong()
            val offset = ((scrollMs % lapMs - SCROLL_PAUSE_MS).coerceAtLeast(0L) * SCROLL_SPEED).coerceAtMost(lap)
            with(title) { draw(state.title, left - offset, TITLE_BASELINE, text) }
            with(title) { draw(state.title, left - offset + lap, TITLE_BASELINE, text) }
        }
        restore()
    }
    if (state.artist.isEmpty()) return
    val artist = Label(ARTIST_SIZE, fontName, shadow)
    with(artist) { draw(fit(state.artist, artist, textWidth), left, ARTIST_BASELINE, text.multiplyAlpha(DIM_ALPHA)) }
}

private fun fit(text: String, font: Label, width: Float): String {
    if (font.width(text) <= width) return text
    var end = text.length
    while (end > 1 && font.width(text.substring(0, end).trimEnd() + "…") > width) end--
    return text.substring(0, end).trimEnd() + "…"
}

private fun RenderContext.drawProgress(
    state: MediaCardState,
    width: Float,
    showTimes: Boolean,
    text: PolyColor,
    accent: PolyColor,
    fontName: String?,
    shadow: Boolean,
) {
    val top = MediaCardLayout.PROGRESS_TOP
    val mid = top + MediaCardLayout.PROGRESS_HEIGHT / 2f
    val font = Label(TIME_SIZE, fontName, shadow)
    val known = state.lengthMs > 0
    val dim = text.multiplyAlpha(DIM_ALPHA)

    var barLeft = 0f
    var barRight = width
    if (showTimes) {
        val left = formatTime(state.positionMs)
        val right = if (known) formatTime(state.lengthMs) else "--:--"
        val baseline = mid + font.capHeight / 2f
        with(font) {
            draw(left, 0f, baseline, dim)
            draw(right, width - width(right), baseline, dim)
        }
        barLeft = font.width(left) + TIME_GAP
        barRight = width - font.width(right) - TIME_GAP
    }

    val barWidth = (barRight - barLeft).coerceAtLeast(0f)
    val barTop = mid - BAR_HEIGHT / 2f
    rect(barLeft, barTop, barWidth, BAR_HEIGHT, text.multiplyAlpha(TRACK_ALPHA), BAR_HEIGHT / 2f)
    if (known) {
        val fraction = (state.positionMs.toFloat() / state.lengthMs).coerceIn(0f, 1f)
        val filled = barWidth * fraction
        if (filled > 0f) rect(barLeft, barTop, filled, BAR_HEIGHT, accent, BAR_HEIGHT / 2f)
        paint.color = accent.argb
        paint.mode = PaintMode.FILL
        canvas.drawCircle(barLeft + filled, mid, KNOB_RADIUS, paint)
    }
}

private fun RenderContext.drawButton(
    button: Int,
    cx: Float,
    cy: Float,
    playing: Boolean,
    enabled: Boolean,
    hovered: Boolean,
    text: PolyColor,
    accent: PolyColor,
) {
    val fade = if (enabled) 1f else DISABLED_ALPHA
    if (button == BUTTON_TOGGLE) {
        val fill = (if (hovered && enabled) accent.lighten(0.18f) else accent).multiplyAlpha(fade)
        paint.color = fill.argb
        paint.mode = PaintMode.FILL
        canvas.drawCircle(cx, cy, MediaCardLayout.BUTTON_RADIUS, paint)
        if (playing) {
            rect(cx - 3.4f, cy - 4f, 2.6f, 8f, PLAY_GLYPH, 1f)
            rect(cx + 0.8f, cy - 4f, 2.6f, 8f, PLAY_GLYPH, 1f)
        } else {
            path(
                PLAY_GLYPH, fill = true, stroke = 1.2f,
                cx - 2.6f, cy - 3.6f,
                cx - 2.6f, cy + 3.6f,
                cx + 3.8f, cy,
            )
        }
        return
    }

    if (hovered && enabled) {
        paint.color = text.multiplyAlpha(HOVER_ALPHA).argb
        paint.mode = PaintMode.FILL
        canvas.drawCircle(cx, cy, MediaCardLayout.BUTTON_RADIUS, paint)
    }
    val color = text.multiplyAlpha(fade)
    val dir = if (button == BUTTON_PREVIOUS) -1f else 1f
    rect(cx + dir * 4.6f - 0.8f, cy - 3.8f, 1.6f, 7.6f, color, 0.8f)
    path(
        color, fill = true, stroke = 1.2f,
        cx - dir * 4.2f, cy - 3.6f,
        cx - dir * 4.2f, cy + 3.6f,
        cx + dir * 2.6f, cy,
    )
}

private fun RenderContext.path(color: PolyColor, fill: Boolean, stroke: Float, vararg points: Float) {
    PathBuilder().use { builder ->
        for (i in points.indices step 2) {
            if (i == 0) builder.moveTo(points[i], points[i + 1]) else builder.lineTo(points[i], points[i + 1])
        }
        builder.closePath()
        builder.detach().use { path ->
            paint.color = color.argb
            if (fill) {
                paint.mode = PaintMode.FILL
                canvas.drawPath(path, paint)
            }
            if (stroke > 0f) {
                paint.mode = PaintMode.STROKE
                paint.strokeWidth = stroke
                paint.strokeJoin = PaintStrokeJoin.ROUND
                canvas.drawPath(path, paint)
                paint.mode = PaintMode.FILL
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
