package org.polyfrost.evergreenhud.client.hud.media

import androidx.compose.runtime.Composable
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.PolyFont
import org.polyfrost.compose.render.PolyPath
import org.polyfrost.compose.render.PolyRenderer
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.evergreenhud.client.hud.shape.oval
import org.polyfrost.evergreenhud.client.utils.capHeight
import org.polyfrost.evergreenhud.client.utils.fillShape
import java.awt.BasicStroke
import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

const val BUTTON_PREVIOUS = 0
const val BUTTON_TOGGLE = 1
const val BUTTON_NEXT = 2

const val SEEK_BELOW = 0
const val SEEK_ABOVE = 1
const val SEEK_HIDDEN = 2

const val SEEK_ROUNDED = 0
const val SEEK_SQUARE = 1
const val SEEK_WAVE = 2

const val BUTTONS_CLASSIC = 0
const val BUTTONS_MATERIAL = 1

data class MediaCardState(
    val title: String,
    val artist: String,
    val playing: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canToggle: Boolean,
    val art: PolyBitmap?,
    val artAccent: Int?,
)

data class MediaCardColors(
    val title: PolyColor,
    val artist: PolyColor,
    val time: PolyColor,
    val controls: PolyColor,
    val glyph: PolyColor,
    val accent: PolyColor,
)

data class MediaCardStyle(
    val showArt: Boolean,
    val showTimes: Boolean,
    val seekPosition: Int,
    val seekWidth: Float,
    val seekStyle: Int,
    val buttonStyle: Int,
    val cutoutGlyph: Boolean,
    val bounce: Boolean,
    val artRadius: Float,
    val colors: MediaCardColors,
)

data class MediaCardBackdrop(
    val bounds: Rect,
    val radius: Float,
    val color: PolyColor?,
    val art: PolyBitmap?,
    val artBlur: Float,
    val artAlpha: Float,
)

data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    companion object {
        fun makeXYWH(x: Float, y: Float, w: Float, h: Float) = Rect(x, y, x + w, y + h)
        fun makeWH(w: Float, h: Float) = Rect(0f, 0f, w, h)
    }
}

object MediaCardLayout {
    const val INSET = 6f
    const val ART_SIZE = 38f
    const val ART_RADIUS = 6f
    const val MIN_TEXT_WIDTH = 72f
    const val MAX_TEXT_WIDTH = 160f
    const val PROGRESS_HEIGHT = 6f
    const val BUTTON_RADIUS = 9f

    private const val PROGRESS_GAP = 7f
    private const val ART_GAP = 8f
    private const val BUTTON_SPACING = 24f
    private const val HIT_RADIUS = 11.5f

    fun textX(showArt: Boolean): Float = if (showArt) ART_SIZE + ART_GAP else 0f

    fun width(showArt: Boolean, textWidth: Float): Float = textX(showArt) + textWidth

    fun contentTop(seek: Int): Float = if (seek == SEEK_ABOVE) PROGRESS_HEIGHT + PROGRESS_GAP else 0f

    fun progressTop(seek: Int): Float = if (seek == SEEK_ABOVE) 0f else ART_SIZE + PROGRESS_GAP

    fun height(seek: Int): Float = if (seek == SEEK_HIDDEN) ART_SIZE else ART_SIZE + PROGRESS_GAP + PROGRESS_HEIGHT

    fun buttonY(seek: Int): Float = contentTop(seek) + ART_SIZE - BUTTON_RADIUS

    fun buttonX(button: Int, showArt: Boolean, textWidth: Float): Float =
        textX(showArt) + textWidth / 2f + (button - BUTTON_TOGGLE) * BUTTON_SPACING

    fun buttonAt(x: Float, y: Float, showArt: Boolean, textWidth: Float, seek: Int): Int {
        for (button in BUTTON_PREVIOUS..BUTTON_NEXT) {
            if (hypot(x - buttonX(button, showArt, textWidth), y - buttonY(seek)) <= HIT_RADIUS) return button
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
const val DIM_ALPHA = 0.6f
private const val TRACK_ALPHA = 0.2f
private const val WAVE_STROKE = 1.6f
private const val WAVE_AMPLITUDE = 1.3f
private const val WAVE_LENGTH = 8f
private const val WAVE_PERIOD_MS = 1600f
private const val WAVE_STEP = 0.5f
private const val POP_FROM_SCALE = 0.9f
private const val MATERIAL_PLAYING_RADIUS = 5f
private const val TONAL_ALPHA = 0.22f
private const val TONAL_HOVER_ALPHA = 0.34f
private const val MATERIAL_SKIP_RADIUS = 7.5f
private const val MATERIAL_TOGGLE_ICON = 14f / 24f
private const val MATERIAL_SKIP_ICON = 12f / 24f
private const val ICON_STROKE = 1f
private const val ICON_INSET = ICON_STROKE / 2f
private const val ICON_TIP_INSET = 0.8f
val PLAY_GLYPH = PolyColor(0xFF101014.toInt())
private var blurredArt: PolyBitmap? = null
private var blurredKey: Triple<PolyBitmap, Rect, Int>? = null

@Composable
fun MediaCard(
    state: MediaCardState,
    style: MediaCardStyle,
    fontName: String?,
    shadow: Boolean,
    hovered: Int,
    scrollMs: Long,
    scale: Float,
    textWidth: Float = textWidth(state, fontName),
    backdrop: MediaCardBackdrop? = null,
    pop: Float = 1f,
) {
    val width = MediaCardLayout.width(style.showArt, textWidth)
    val height = MediaCardLayout.height(style.seekPosition)
    PolyCanvas(PolyModifier.size(width * scale, height * scale)) { x, y, _, h ->
        val unit = h / height
        save()
        translate(x, y)
        scale(unit, unit)

        val popping = pop < 1f
        if (popping) beginPop(pop, backdrop?.bounds ?: Rect.makeWH(width, height))
        if (backdrop != null) drawBackdrop(backdrop)

        val colors = style.colors
        save()
        translate(0f, MediaCardLayout.contentTop(style.seekPosition))
        if (style.showArt) drawArt(state.art, colors.title, style.artRadius)
        val left = MediaCardLayout.textX(style.showArt)
        drawLabels(state, left, textWidth, colors, fontName, shadow, scrollMs, style.bounce)
        for (button in BUTTON_PREVIOUS..BUTTON_NEXT) {
            val enabled = when (button) {
                BUTTON_PREVIOUS -> state.canPrevious
                BUTTON_NEXT -> state.canNext
                else -> state.canToggle
            }
            drawButton(button, MediaCardLayout.buttonX(button, style.showArt, textWidth), MediaCardLayout.buttonY(SEEK_BELOW), state.playing, enabled, hovered == button, colors, style.buttonStyle == BUTTONS_MATERIAL, style.cutoutGlyph)
        }
        restore()
        if (style.seekPosition != SEEK_HIDDEN) drawProgress(state, width, MediaCardLayout.progressTop(style.seekPosition), style, fontName, shadow)

        if (popping) restore()
        restore()
    }
}

private fun RenderContext.beginPop(progress: Float, bounds: Rect) {
    val inverse = 1f - progress.coerceIn(0f, 1f)
    val eased = 1f - inverse * inverse * inverse
    saveLayerAlpha(eased)
    val s = POP_FROM_SCALE + (1f - POP_FROM_SCALE) * eased
    translate(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f)
    scale(s, s)
    translate(-(bounds.left + bounds.width / 2f), -(bounds.top + bounds.height / 2f))
}

private fun RenderContext.drawBackdrop(backdrop: MediaCardBackdrop) {
    val bounds = backdrop.bounds
    val art = backdrop.art
    if (art != null && backdrop.artAlpha > 0f) {
        save()
        clipRRect(bounds.left, bounds.top, bounds.width, bounds.height, backdrop.radius)
        val bleed = backdrop.artBlur * 3f
        val target = Rect(bounds.left - bleed, bounds.top - bleed, bounds.right + bleed, bounds.bottom + bleed)
        val crop = coverCrop(art, target.width / target.height)
        val tint = PolyColor.WHITE.withAlpha((backdrop.artAlpha * 255f).roundToInt())
        image(blurred(art, crop, backdrop.artBlur * crop.width / target.width), target.left, target.top, target.width, target.height, 0f, 0f, 1f, 1f, tint)
        restore()
    }
    backdrop.color?.let { rect(bounds.left, bounds.top, bounds.width, bounds.height, it, backdrop.radius) }
}

private fun blurred(art: PolyBitmap, crop: Rect, sigma: Float): PolyBitmap {
    val left = crop.left.roundToInt().coerceIn(0, art.width - 1)
    val top = crop.top.roundToInt().coerceIn(0, art.height - 1)
    val width = crop.width.roundToInt().coerceIn(1, art.width - left)
    val height = crop.height.roundToInt().coerceIn(1, art.height - top)
    val key = Triple(art, Rect.makeXYWH(left.toFloat(), top.toFloat(), width.toFloat(), height.toFloat()), (sigma * 4f).roundToInt())
    if (key == blurredKey) blurredArt?.let { return it }
    val pixels = FloatArray(width * height * 4)
    for (y in 0 until height) for (x in 0 until width) {
        val src = ((top + y) * art.width + left + x) * 4
        val dst = (y * width + x) * 4
        val alpha = (art.pixels[src + 3].toInt() and 0xFF) / 255f
        for (c in 0..2) pixels[dst + c] = (art.pixels[src + c].toInt() and 0xFF) * alpha
        pixels[dst + 3] = alpha * 255f
    }
    if (key.third > 0) {
        val radius = ((sqrt(4f * sigma * sigma + 1f) - 1f) / 2f).roundToInt().coerceAtLeast(1)
        val scratch = FloatArray(pixels.size)
        repeat(3) {
            boxBlur(pixels, scratch, width, height, radius, 4, width * 4)
            boxBlur(scratch, pixels, height, width, radius, width * 4, 4)
        }
    }
    val out = ByteArray(width * height * 4)
    for (i in 0 until width * height) {
        val alpha = pixels[i * 4 + 3]
        if (alpha <= 0f) continue
        for (c in 0..2) out[i * 4 + c] = (pixels[i * 4 + c] * 255f / alpha).roundToInt().coerceIn(0, 255).toByte()
        out[i * 4 + 3] = alpha.roundToInt().coerceIn(0, 255).toByte()
    }
    return PolyBitmap(width, height, out).also {
        blurredArt = it
        blurredKey = key
    }
}

private fun boxBlur(src: FloatArray, dst: FloatArray, lines: Int, length: Int, radius: Int, lineStride: Int, step: Int) {
    val window = radius * 2 + 1
    for (line in 0 until lines) {
        val base = line * lineStride
        for (c in 0..3) {
            var sum = 0f
            for (i in -radius..radius) sum += src[base + i.coerceIn(0, length - 1) * step + c]
            for (i in 0 until length) {
                dst[base + i * step + c] = sum / window
                sum += src[base + (i + radius + 1).coerceAtMost(length - 1) * step + c] - src[base + (i - radius).coerceAtLeast(0) * step + c]
            }
        }
    }
}

private fun coverCrop(art: PolyBitmap, aspect: Float): Rect {
    val w = art.width.toFloat()
    val h = art.height.toFloat()
    return if (w / h > aspect) {
        val cw = h * aspect
        Rect.makeXYWH((w - cw) / 2f, 0f, cw, h)
    } else {
        val ch = w / aspect
        Rect.makeXYWH(0f, (h - ch) / 2f, w, ch)
    }
}

private fun RenderContext.drawArt(art: PolyBitmap?, text: PolyColor, radius: Float) {
    val size = MediaCardLayout.ART_SIZE
    save()
    clipRRect(0f, 0f, size, size, radius)
    if (art == null) {
        rect(0f, 0f, size, size, text.multiplyAlpha(0.12f))
        drawNote(size / 2f, size / 2f, text.multiplyAlpha(0.5f))
    } else {
        val side = minOf(art.width, art.height).toFloat()
        val u = (art.width - side) / 2f / art.width
        val v = (art.height - side) / 2f / art.height
        image(art, 0f, 0f, size, size, u, v, 1f - u, 1f - v, PolyColor.WHITE)
    }
    restore()
}

private fun RenderContext.drawNote(cx: Float, cy: Float, color: PolyColor) {
    oval(cx - 6f, cy + 3f, 6f, 4.6f, color)
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
    private val poly: PolyFont? = fontName?.let { PolyFont(it, size) }
    private val mcScale = size / MC_LINE_HEIGHT

    val capHeight: Float get() = poly?.capHeight ?: (MC_CAP_HEIGHT * mcScale)

    fun width(text: String): Float = poly?.measureTextWidth(text) ?: McFontQueue.measureTextWidth(text, mcScale)

    fun RenderContext.draw(text: String, x: Float, baseline: Float, color: PolyColor) {
        if (poly != null) return text(text, x, baseline, color, poly)
        mcText(text, x, baseline - MC_CAP_HEIGHT * mcScale, color.argb, shadow, mcScale)
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
    colors: MediaCardColors,
    fontName: String?,
    shadow: Boolean,
    scrollMs: Long,
    bounce: Boolean,
) {
    val text = colors.title
    val title = Label(TITLE_SIZE, fontName, shadow)
    val titleWidth = title.width(state.title)
    if (titleWidth <= textWidth) {
        with(title) { draw(state.title, left, TITLE_BASELINE, text) }
    } else {
        save()
        clipRect(left, -MediaCardLayout.INSET, textWidth, ARTIST_BASELINE + MediaCardLayout.INSET)
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
    with(artist) { draw(fit(state.artist, artist, textWidth), left, ARTIST_BASELINE, colors.artist) }
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
    top: Float,
    style: MediaCardStyle,
    fontName: String?,
    shadow: Boolean,
) {
    val colors = style.colors
    val mid = top + MediaCardLayout.PROGRESS_HEIGHT / 2f
    val font = Label(TIME_SIZE, fontName, shadow)
    val known = state.lengthMs > 0

    val span = width * style.seekWidth.coerceIn(0f, 1f)
    val start = (width - span) / 2f
    var barLeft = start
    var barRight = start + span
    if (style.showTimes) {
        val left = formatTime(state.positionMs)
        val right = if (known) formatTime(state.lengthMs) else "--:--"
        val baseline = mid + font.capHeight / 2f
        with(font) {
            draw(left, barLeft, baseline, colors.time)
            draw(right, barRight - width(right), baseline, colors.time)
        }
        val digit = "0123456789".maxBy { font.width(it.toString()) }
        val template = formatTime(maxOf(state.positionMs, state.lengthMs)).replace(Regex("\\d"), digit.toString())
        val slot = maxOf(font.width(template), font.width(right))
        barLeft += slot + TIME_GAP
        barRight -= slot + TIME_GAP
    }

    val barWidth = (barRight - barLeft).coerceAtLeast(0f)
    val track = colors.title.multiplyAlpha(TRACK_ALPHA)
    val fraction = if (known) (state.positionMs.toFloat() / state.lengthMs).coerceIn(0f, 1f) else 0f
    val filled = barWidth * fraction
    when (style.seekStyle) {
        SEEK_WAVE -> drawWave(state, barLeft, barWidth, filled, mid, known, track, colors.accent)
        else -> {
            val radius = if (style.seekStyle == SEEK_SQUARE) 0f else BAR_HEIGHT / 2f
            val barTop = mid - BAR_HEIGHT / 2f
            rect(barLeft, barTop, barWidth, BAR_HEIGHT, track, radius)
            if (!known) return
            if (filled > 0f) rect(barLeft, barTop, filled, BAR_HEIGHT, colors.accent, radius)
            if (style.seekStyle == SEEK_SQUARE) {
                rect(barLeft + filled - KNOB_RADIUS, mid - KNOB_RADIUS, KNOB_RADIUS * 2f, KNOB_RADIUS * 2f, colors.accent)
            } else {
                circle(barLeft + filled, mid, KNOB_RADIUS, colors.accent)
            }
        }
    }
}

private fun RenderContext.drawWave(
    state: MediaCardState,
    left: Float,
    width: Float,
    filled: Float,
    mid: Float,
    known: Boolean,
    track: PolyColor,
    accent: PolyColor,
) {
    if (filled < width && track.alpha > 0) {
        renderer.strokePolyline(floatArrayOf(left + filled, mid, left + width, mid), false, track.argb, WAVE_STROKE, PolyRenderer.CAP_ROUND, PolyRenderer.JOIN_ROUND)
    }
    if (known && filled > 0f && accent.alpha > 0) {
        val amplitude = if (state.playing) WAVE_AMPLITUDE else 0f
        val phase = state.positionMs / WAVE_PERIOD_MS * 2f * PI.toFloat()
        val points = ArrayList<Float>()
        var dx = 0f
        while (true) {
            points.add(left + dx)
            points.add(mid + amplitude * sin(dx / WAVE_LENGTH * 2f * PI.toFloat() - phase))
            if (dx >= filled) break
            dx = (dx + WAVE_STEP).coerceAtMost(filled)
        }
        renderer.strokePolyline(points.toFloatArray(), false, accent.argb, WAVE_STROKE, PolyRenderer.CAP_ROUND, PolyRenderer.JOIN_ROUND)
    }
    if (!known) return
    val knobHeight = MediaCardLayout.PROGRESS_HEIGHT
    rect(left + filled - WAVE_STROKE / 2f, mid - knobHeight / 2f, WAVE_STROKE, knobHeight, accent, WAVE_STROKE / 2f)
}

private fun RenderContext.drawButton(
    button: Int,
    cx: Float,
    cy: Float,
    playing: Boolean,
    enabled: Boolean,
    hovered: Boolean,
    colors: MediaCardColors,
    material: Boolean,
    cutout: Boolean,
) {
    val radius = MediaCardLayout.BUTTON_RADIUS
    val fade = if (enabled) 1f else DISABLED_ALPHA
    if (button == BUTTON_TOGGLE) {
        val accent = colors.accent
        val glyph = if (cutout) PLAY_GLYPH else colors.glyph.multiplyAlpha(fade)
        val fill = (if (hovered && enabled) accent.lighten(0.18f) else accent).multiplyAlpha(fade)
        if (cutout) {
            val body: Shape = if (material) {
                val corner = (if (playing) MATERIAL_PLAYING_RADIUS else radius) * 2f
                RoundRectangle2D.Float(cx - radius, cy - radius, 2f * radius, 2f * radius, corner, corner)
            } else {
                Ellipse2D.Float(cx - radius, cy - radius, 2f * radius, 2f * radius)
            }
            val area = Area(body)
            area.subtract(toggleGlyph(cx, cy, playing, material))
            if (fill.alpha > 0) fillShape(area, fill)
            return
        }
        if (material) {
            rect(cx - radius, cy - radius, 2f * radius, 2f * radius, fill, if (playing) MATERIAL_PLAYING_RADIUS else radius)
        } else {
            circle(cx, cy, radius, fill)
        }
        if (material) {
            val u = MATERIAL_TOGGLE_ICON
            if (playing) {
                rect(cx - 6f * u, cy - 7f * u, 4f * u, 14f * u, glyph, u)
                rect(cx + 2f * u, cy - 7f * u, 4f * u, 14f * u, glyph, u)
            } else {
                path(
                    glyph, fill = true, stroke = ICON_STROKE,
                    cx - 4f * u + ICON_INSET, cy - 7f * u + ICON_INSET,
                    cx - 4f * u + ICON_INSET, cy + 7f * u - ICON_INSET,
                    cx + 7f * u - ICON_TIP_INSET, cy,
                )
            }
        } else if (playing) {
            rect(cx - 3.4f, cy - 4f, 2.6f, 8f, glyph, 1f)
            rect(cx + 0.8f, cy - 4f, 2.6f, 8f, glyph, 1f)
        } else {
            path(
                glyph, fill = true, stroke = 1.2f,
                cx - 2.6f, cy - 3.6f,
                cx - 2.6f, cy + 3.6f,
                cx + 3.8f, cy,
            )
        }
        return
    }

    val controls = colors.controls
    if (material) {
        val tonal = colors.accent.multiplyAlpha((if (hovered && enabled) TONAL_HOVER_ALPHA else TONAL_ALPHA) * fade)
        val skip = MATERIAL_SKIP_RADIUS
        rect(cx - skip, cy - skip, 2f * skip, 2f * skip, tonal, skip)
    } else if (hovered && enabled) {
        circle(cx, cy, radius, controls.multiplyAlpha(HOVER_ALPHA))
    }
    val color = controls.multiplyAlpha(fade)
    val dir = if (button == BUTTON_PREVIOUS) -1f else 1f
    if (material) {
        val u = MATERIAL_SKIP_ICON
        rect(cx + dir * 5f * u - u, cy - 6f * u, 2f * u, 12f * u, color, 0.4f)
        path(
            color, fill = true, stroke = ICON_STROKE,
            cx - dir * (6f * u - ICON_INSET), cy - 6f * u + ICON_INSET,
            cx - dir * (6f * u - ICON_INSET), cy + 6f * u - ICON_INSET,
            cx + dir * (2.5f * u - ICON_TIP_INSET), cy,
        )
        return
    }
    rect(cx + dir * 4.6f - 0.8f, cy - 3.8f, 1.6f, 7.6f, color, 0.8f)
    path(
        color, fill = true, stroke = 1.2f,
        cx - dir * 4.2f, cy - 3.6f,
        cx - dir * 4.2f, cy + 3.6f,
        cx + dir * 2.6f, cy,
    )
}

private fun RenderContext.path(color: PolyColor, fill: Boolean, stroke: Float, vararg points: Float) {
    if (color.alpha == 0) return
    if (fill) {
        val path = PolyPath()
        for (i in points.indices step 2) {
            if (i == 0) path.moveTo(points[i], points[i + 1]) else path.lineTo(points[i], points[i + 1])
        }
        path.close()
        fillPath(path, color)
    }
    if (stroke > 0f) renderer.strokePolyline(points, true, color.argb, stroke, PolyRenderer.CAP_BUTT, PolyRenderer.JOIN_ROUND)
}

private fun RenderContext.circle(cx: Float, cy: Float, radius: Float, color: PolyColor) =
    rect(cx - radius, cy - radius, radius * 2f, radius * 2f, color, radius)

private fun toggleGlyph(cx: Float, cy: Float, playing: Boolean, material: Boolean): Area {
    val glyph = Area()
    if (playing) {
        val u = if (material) MATERIAL_TOGGLE_ICON else 1f
        val bars = if (material) {
            floatArrayOf(cx - 6f * u, cy - 7f * u, 4f * u, 14f * u, cx + 2f * u, cy - 7f * u, 4f * u, 14f * u)
        } else {
            floatArrayOf(cx - 3.4f, cy - 4f, 2.6f, 8f, cx + 0.8f, cy - 4f, 2.6f, 8f)
        }
        val corner = 2f * u
        for (i in bars.indices step 4) glyph.add(Area(RoundRectangle2D.Float(bars[i], bars[i + 1], bars[i + 2], bars[i + 3], corner, corner)))
        return glyph
    }
    val u = MATERIAL_TOGGLE_ICON
    val points = if (material) {
        floatArrayOf(
            cx - 4f * u + ICON_INSET, cy - 7f * u + ICON_INSET,
            cx - 4f * u + ICON_INSET, cy + 7f * u - ICON_INSET,
            cx + 7f * u - ICON_TIP_INSET, cy,
        )
    } else {
        floatArrayOf(cx - 2.6f, cy - 3.6f, cx - 2.6f, cy + 3.6f, cx + 3.8f, cy)
    }
    val triangle = Path2D.Float()
    triangle.moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) triangle.lineTo(points[i], points[i + 1])
    triangle.closePath()
    glyph.add(Area(triangle))
    val stroke = if (material) ICON_STROKE else 1.2f
    glyph.add(Area(BasicStroke(stroke, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND).createStrokedShape(triangle)))
    return glyph
}

fun formatTime(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
