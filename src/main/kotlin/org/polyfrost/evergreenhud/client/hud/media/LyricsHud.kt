package org.polyfrost.evergreenhud.client.hud.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.evergreenhud.client.utils.applyCase
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.evergreenhud.client.utils.media.KUGOU
import org.polyfrost.evergreenhud.client.utils.media.LRCLIB
import org.polyfrost.evergreenhud.client.utils.media.LRC_RED
import org.polyfrost.evergreenhud.client.utils.media.LyricLine
import org.polyfrost.evergreenhud.client.utils.media.Lyrics
import org.polyfrost.evergreenhud.client.utils.media.LyricsService
import org.polyfrost.evergreenhud.client.utils.media.MediaInfo
import org.polyfrost.evergreenhud.client.utils.media.MediaService
import org.polyfrost.evergreenhud.client.utils.media.PROVIDERS
import org.polyfrost.evergreenhud.client.utils.media.parseLrc
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.DraggableList
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Number
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.platform.v1.Platform
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

private const val FONT_SIZE = 9f
private const val INSET = MediaCardLayout.INSET
private const val CREDIT_SIZE = 4.5f
private const val ROW_HEIGHT = 11f
private const val LINE_GAP = 5f
private const val MIN_WIDTH = 60f
private const val MAX_WIDTH = 220f
private const val FRAME_MS = 16L

private const val FADE_MS = 320f
private const val HEIGHT_SMOOTHING_MS = 120.0
private const val POP_MS = 220f

private const val SPRING_DAMPING = 0.9f
private const val SPRING_OMEGA = 11f
private const val SPRING_SETTLED_MS = 1500f
private const val SCROLL_STAGGER_MS = 45f
private const val IDLE_SCALE = 0.95f

private const val LIFT = 0.5f
private const val SUNG_LIFT = 0.6f
private const val GLOW_ALPHA = 0.3f
private const val GLOW_SPREAD = 0.7f
private const val FEATHER = 4f
private const val FEATHER_STEPS = 4
private const val LETTER_MIN_MS = 1000L
private const val MIN_MOTION_MS = 450L
private const val LETTER_MAX = 12
private const val LETTER_SCALE = 0.08f
private const val LETTER_LIFT = 0.6f

private const val PAUSE_MS = 4000L
private const val PAUSE_DELAY_MS = 800L

private const val UNSUNG_ALPHA = 0.5f
private const val OTHER_ALPHA = 0.35f
private const val DISTANT_FADE = 0.3f
private const val MIN_DISTANT_ALPHA = 0.25f

private const val SNAP_DISTANCE_MS = 1_500.0
private const val SMOOTHING_MS = 250.0

private const val TEXT_CUSTOM = 1
private const val TEXT_ALBUM_ART = 2
private const val TEXT_ALBUM_AVERAGE = 3

private const val HIGHLIGHT_ACCENT = 1
private const val HIGHLIGHT_ALBUM_ART = 2
private const val HIGHLIGHT_ALBUM_AVERAGE = 3

private const val EXAMPLE_LOOP_MS = 14_000L
private val EXAMPLE: Lyrics = parseLrc(
    """
    [00:00.40]Lyrics follow along
    [00:03.00]word by word as you listen
    [00:09.30]synced to your music
    """.trimIndent(),
)!!

private class Placed(val startMs: Long, val endMs: Long, val text: String, val x: Float, val ink: Float)

private class LineLayout(val rows: List<List<Placed>>) {
    val height: Float get() = rows.size.coerceAtLeast(1) * ROW_HEIGHT
    fun rowWidth(row: List<Placed>): Float = row.lastOrNull()?.let { it.x + it.ink } ?: 0f
}

private data class LyricsColors(val sung: PolyColor, val unsung: PolyColor, val other: PolyColor)

private data class GlyphMark(
    val text: String,
    val cx: Float,
    val baseline: Float,
    val width: Float,
    val scale: Float,
    val base: Int,
    val sung: Int,
    val progress: Float,
    val glow: Float,
)


private data class LyricsFrame(
    val width: Float,
    val height: Float,
    val marks: List<GlyphMark>,
    val fontName: String?,
    val shadow: Boolean,
    val pop: Float,
    val art: PolyBitmap?,
    val credit: String?,
    val creditColor: Int,
)

class LyricsHud : Hud(
    id = "lyrics.json",
    title = "Lyrics",
    category = Category.INFO,
) {
    @Number(title = "Lines Before", min = 0f, max = 5f)
    var linesBefore = 1

    @Number(title = "Lines After", min = 0f, max = 5f)
    var linesAfter = 1

    @Switch(title = "Match Media Player Width", description = "Same width as the Media Player.")
    var matchMediaWidth = true

    @Switch(title = "Wrap Text", description = "Wraps long lines. Off cuts them off at the edge.")
    var wrapText = true

    @Switch(title = "Word Sync", description = "Highlights word by word. Off highlights whole lines.")
    var wordSync = true

    @Switch(
        title = "Estimate Word Timing",
        description = "Guesses word timing for lyrics that only time whole lines.",
    )
    var estimateWords = true

    @Switch(title = "Word Motion", description = "Animates words as they are sung.")
    var wordBounce = true

    @Switch(title = "Word Glow", description = "Glows words as they are sung.")
    var wordGlow = true

    @Slider(title = "Timing Offset", description = "In milliseconds. Raise if lyrics are late.", min = -2000f, max = 2000f, step = 50f)
    var offsetMs = 0f

    @Dropdown(
        title = "Text Color Source",
        description = "Color of the lyrics.",
        options = ["Text Color", "Custom", "Album Art", "Album Art Average"],
        subcategory = "Colors",
    )
    var textSource = 0

    @Color(title = "Upcoming Words Color", description = "Unsung words in the current line.", subcategory = "Colors")
    var unsungColor = PolyColor(0x80FFFFFF.toInt())

    @Color(title = "Other Lines Color", subcategory = "Colors")
    var otherColor = PolyColor(0x59FFFFFF)

    @Slider(title = "Album Text Saturation", description = "In percent.", min = 0f, max = 200f, step = 5f, subcategory = "Colors")
    var albumTextSaturation = 100f

    @Slider(title = "Album Text Lightness", description = "Positive is lighter, negative is darker.", min = -100f, max = 100f, step = 5f, subcategory = "Colors")
    var albumTextLightness = 0f

    @Dropdown(
        title = "Highlight Source",
        description = "Color of sung words.",
        options = ["Same as Text", "Accent Color", "Album Art", "Album Art Average"],
        subcategory = "Colors",
    )
    var highlightSource = 0

    @Color(title = "Accent Color", subcategory = "Colors")
    var accentColor = PolyColor(0xFF7CE0A8.toInt())

    @DraggableList(
        title = "Providers",
        description = "Lyrics sites to try, in order.",
        subcategory = "Providers",
        checkable = true,
        options = [LRC_RED, LRCLIB, KUGOU],
    )
    var providers = PROVIDERS.copyOf()

    @Switch(title = "Show Provider", description = "Shows where the lyrics came from.", subcategory = "Providers")
    var showProvider = false

    @Switch(title = "Album Art Background", description = "Blurred album art behind the lyrics.", subcategory = "Background")
    var artBackground = false

    @Slider(title = "Album Art Blur", min = 0f, max = 20f, step = 1f, subcategory = "Background")
    var artBackgroundBlur = 8f

    @Slider(title = "Album Art Opacity", min = 0f, max = 100f, step = 5f, subcategory = "Background")
    var artBackgroundOpacity = 100f

    @Switch(title = "Only Show in GUIs", description = "Only shows while a screen like chat is open.", subcategory = "Visibility")
    var onlyInGuis = false

    @Switch(title = "Pop In Animation", description = "Fades the lyrics in when a screen opens.", subcategory = "Visibility")
    var popIn = true

    private var _staticW = mutableStateOf(0f)
    override var staticW: Float get() = _staticW.value; set(v) { _staticW.value = v }

    private var _staticH = mutableStateOf(0f)
    override var staticH: Float get() = _staticH.value; set(v) { _staticH.value = v }

    private var frame = mutableStateOf<LyricsFrame?>(null)

    @Transient
    private var layouts = IdentityHashMap<LyricLine, LineLayout>()

    @Transient
    private var layoutKey: Any? = null

    @Transient
    private var songWidth = 0f

    @Transient
    private var songWidthKey: Any? = null

    @Transient
    private var shownHeight = 0f

    @Transient
    private var shownPosition = 0.0

    @Transient
    private var shownAt = 0L

    @Transient
    private var shownTitle: String? = null

    @Transient
    private var popStart = 0L

    @Transient
    private var acquired = false

    override fun defaultPosition(): Pair<Float, Float> =
        (HudManager.guiScreenWidth - scaledWidth) / 2f to HudManager.guiScreenHeight * 0.62f

    override fun canMergeBackground(): Boolean = true

    override fun setup() {
        super.setup()
        tree?.getProp("staticW")?.addMetadata("default", naturalWidth())
        tree?.getProp("staticH")?.addMetadata("default", naturalHeight())
        showWhen("wordBounce") { wordSync }
        showWhen("wordGlow") { wordSync }
        showWhen("estimateWords") { wordSync }
        for (option in listOf("unsungColor", "otherColor")) showWhen(option, hide = true) { textSource == TEXT_CUSTOM }
        for (option in listOf("albumTextSaturation", "albumTextLightness")) {
            showWhen(option, hide = true) { textSource == TEXT_ALBUM_ART || textSource == TEXT_ALBUM_AVERAGE || highlightSource == HIGHLIGHT_ALBUM_ART || highlightSource == HIGHLIGHT_ALBUM_AVERAGE }
        }
        showWhen("accentColor", hide = true) { highlightSource == HIGHLIGHT_ACCENT }
        for (option in listOf("artBackgroundBlur", "artBackgroundOpacity")) showWhen(option) { artBackground }
        showWhen("popIn") { onlyInGuis }
        if (isReal) {
            applyProviders()
            addCallback("providers") { applyProviders() }
        }
        if (!isReal || acquired) return
        acquired = true
        MediaService.acquire()
    }

    private fun applyProviders() {
        LyricsService.providers = providers.toList()
    }

    private fun showWhen(option: String, hide: Boolean = false, condition: () -> Boolean) {
        val off = if (hide) Property.Display.HIDDEN else Property.Display.DISABLED
        addDependency(option, null) { if (condition()) Property.Display.SHOWN else off }
    }

    override fun remove() {
        if (acquired) MediaService.release()
        acquired = false
    }

    override fun updateFrequency(): Long = FRAME_MS.milliseconds.inWholeNanoseconds

    override fun shouldShow(): Boolean = frame.value != null && (!onlyInGuis || screenOpen())

    override fun update(): Boolean {
        val now = System.currentTimeMillis()
        if (onlyInGuis && !screenOpen()) popStart = now
        val next = buildFrame(now)
        val changed = next != frame.value
        frame.value = next
        return changed
    }

    private fun buildFrame(now: Long): LyricsFrame? {
        val info = if (isReal) MediaService.current else null
        val live = if (isReal) LyricsService.lyricsFor(info) else null
        val lyrics = live ?: EXAMPLE.takeIf { !isReal || HudManager.isEditing } ?: return null
        val position = if (live != null && info != null) smoothPosition(info, now) + offsetMs.toLong() else now % EXAMPLE_LOOP_MS
        val fontName = cardFont()
        val label = Label(FONT_SIZE, fontName)
        val width = contentWidth(lyrics, label)
        val key = listOf(lyrics, width, fontName, caseType, wrapText)
        if (key != layoutKey) {
            layoutKey = key
            layouts.clear()
        }

        val lines = lyrics.lines
        val current = lyrics.lineAt(position).coerceAtLeast(0)
        val first = (current - linesBefore).coerceAtLeast(0)
        val last = (current + linesAfter).coerceAtMost(lines.lastIndex)
        fun height(i: Int): Float = layout(lines[i], label, width).height

        val targetHeight = if (isStatic) {
            ((staticH - padTop - padBottom) / baseScale - 2f * INSET).coerceAtLeast(ROW_HEIGHT)
        } else {
            (first..last).sumOf { height(it).toDouble() }.toFloat() + LINE_GAP * (last - first)
        }
        shownHeight = if (isStatic || shownHeight <= 0f) targetHeight else {
            shownHeight + (targetHeight - shownHeight) * (1f - exp(-FRAME_MS / HEIGHT_SMOOTHING_MS).toFloat())
        }

        val drawFirst = (first - 1).coerceAtLeast(0)
        val drawLast = (last + 1).coerceAtMost(lines.lastIndex)
        val offsets = FloatArray(drawLast - drawFirst + 2)
        for (i in drawFirst..drawLast) offsets[i - drawFirst + 1] = offsets[i - drawFirst] + height(i) + LINE_GAP
        fun offset(i: Int): Float = offsets[i - drawFirst]
        fun anchor(index: Int): Float =
            ((index - linesBefore).coerceAtLeast(0) until index.coerceAtLeast(0)).sumOf { (height(it) + LINE_GAP).toDouble() }.toFloat()
        val sinceLine = (position - lines[current].startMs).toFloat()

        val colors = colors()
        val marks = ArrayList<GlyphMark>()
        val align = alignFraction()
        val capHeight = label.capHeight
        val wordLevel = wordSync && (lyrics.wordSynced || estimateWords)
        val motion = wordLevel && wordBounce
        val glowing = wordLevel && wordGlow
        for (i in drawFirst..drawLast) {
            val line = lines[i]
            val settled = anchor(current) + offset(i) - offset(current)
            val scroll = if (current == 0) 1f else spring(sinceLine - SCROLL_STAGGER_MS * (i - current + 1).coerceAtLeast(0))
            val top = if (current == 0) settled else lerp(anchor(current - 1) + offset(i) - offset(current - 1), settled, scroll)
            val visibility = when {
                i < current - linesBefore -> 1f - scroll
                i > current + linesAfter -> 0f
                i == current + linesAfter -> scroll
                else -> 1f
            }.coerceIn(0f, 1f)
            if (visibility <= 0f) continue
            val emphasis = when (i) {
                current -> ((position - line.startMs) / FADE_MS).coerceIn(0f, 1f) * pauseFade(line, lines.getOrNull(i + 1), position)
                current - 1 -> 1f - ((position - lines[current].startMs) / FADE_MS).coerceIn(0f, 1f)
                else -> 0f
            }
            val distance = abs(i - current)
            val dim = if (distance <= 1) 1f else (1f - DISTANT_FADE * (distance - 1)).coerceAtLeast(MIN_DISTANT_ALPHA)
            val other = colors.other.argb.withAlphaScale(dim * visibility)
            val base = mix(other, colors.unsung.argb.withAlphaScale(visibility), emphasis)
            val sung = mix(other, colors.sung.argb.withAlphaScale(visibility), emphasis)
            val lineScale = if (motion) lerp(IDLE_SCALE, 1f, emphasis) else 1f
            val anchorX = width * align
            fun place(x: Float): Float = anchorX + (x - anchorX) * lineScale

            val layout = layout(line, label, width)
            for ((r, row) in layout.rows.withIndex()) {
                val rowOffset = (width - layout.rowWidth(row)) * align
                val baseline = top + (r * ROW_HEIGHT + (ROW_HEIGHT + capHeight) / 2f) * lineScale
                for (word in row) {
                    val progress = when {
                        emphasis <= 0f -> 0f
                        !wordLevel -> if (position >= line.startMs) 1f else 0f
                        else -> ((position - word.startMs).toFloat() / (word.endMs - word.startMs)).coerceIn(0f, 1f)
                    }
                    val settle = if (emphasis <= 0f || !wordLevel) progress else {
                        ((position - word.startMs).toFloat() / maxOf(word.endMs - word.startMs, MIN_MOTION_MS)).coerceIn(0f, 1f)
                    }
                    val scale = lineScale * (if (motion) lerp(1f, wordScale(settle), emphasis) else 1f)
                    val lift = if (motion) LIFT * wordLift(settle) * emphasis else 0f
                    val glow = if (glowing) wordGlow(settle) * emphasis else 0f
                    val cx = place(rowOffset + word.x + word.ink / 2f)
                    val trimmed = word.text.trimEnd()
                    val held = motion && progress > 0f && progress < 1f && word.endMs - word.startMs >= LETTER_MIN_MS && trimmed.length in 2..LETTER_MAX
                    if (!held) {
                        marks.add(GlyphMark(trimmed, cx, baseline - lift, word.ink, scale, base, sung, progress, glow))
                        continue
                    }
                    val active = progress * trimmed.length
                    val envelope = (minOf(progress, 1f - progress) * 6f).coerceAtMost(1f)
                    var x = 0f
                    for ((k, char) in trimmed.withIndex()) {
                        val letter = char.toString()
                        val letterWidth = label.width(letter)
                        val falloff = envelope / (1f + abs(k + 0.5f - active).pow(2.8f))
                        val letterScale = scale * (1f + LETTER_SCALE * falloff)
                        val letterCx = cx + (x + letterWidth / 2f - word.ink / 2f) * scale
                        val letterGlow = if (glowing) maxOf(glow, falloff) else 0f
                        marks.add(GlyphMark(letter, letterCx, baseline - lift - LETTER_LIFT * falloff, letterWidth, letterScale, base, sung, (active - k).coerceIn(0f, 1f), letterGlow))
                        x += letterWidth
                    }
                }
            }
        }

        return LyricsFrame(
            width = width,
            height = shownHeight,
            marks = marks,
            fontName = fontName,
            shadow = showShadow,
            pop = if (onlyInGuis && popIn && !HudManager.isEditing) ((now - popStart) / POP_MS).coerceIn(0f, 1f) else 1f,
            art = if (artBackground && live != null) MediaService.art else null,
            credit = live?.source?.takeIf { showProvider && it.isNotEmpty() },
            creditColor = colors.other.argb,
        )
    }

    private fun pauseFade(line: LyricLine, next: LyricLine?, position: Long): Float {
        val gap = (next?.startMs ?: Long.MAX_VALUE) - line.endMs
        if (gap < PAUSE_MS) return 1f
        return 1f - ((position - line.endMs - PAUSE_DELAY_MS) / FADE_MS).coerceIn(0f, 1f)
    }

    private fun layout(line: LyricLine, label: Label, maxWidth: Float): LineLayout = layouts.getOrPut(line) {
        val rows = ArrayList<List<Placed>>()
        var row = ArrayList<Placed>()
        var x = 0f
        for (word in line.words) {
            val text = applyCase(word.text)
            val ink = label.width(text.trimEnd())
            if (wrapText && row.isNotEmpty() && x + ink > maxWidth) {
                rows.add(row)
                row = ArrayList()
                x = 0f
            }
            row.add(Placed(word.startMs, word.endMs, text, x, ink))
            x += label.width(text)
        }
        if (row.isNotEmpty()) rows.add(row)
        LineLayout(rows)
    }

    private fun contentWidth(lyrics: Lyrics, label: Label): Float {
        if (isStatic) return ((staticW - padLeft - padRight) / baseScale - 2f * INSET).coerceAtLeast(1f)
        val media = MediaHud.shownWidth
        if (matchMediaWidth && media > 0f) return ((media / effectiveScale.coerceAtLeast(0.01f) - padLeft - padRight) / baseScale - 2f * INSET).coerceAtLeast(MIN_WIDTH)
        val key = listOf(lyrics, label.width("M"), caseType)
        if (key != songWidthKey) {
            songWidthKey = key
            songWidth = lyrics.lines.maxOfOrNull { label.width(applyCase(it.text.trimEnd())) } ?: 0f
        }
        return songWidth.coerceIn(MIN_WIDTH, if (wrapText) MAX_WIDTH else Float.MAX_VALUE)
    }

    private fun colors(): LyricsColors {
        val text = when (textSource) {
            TEXT_ALBUM_ART -> albumColor(average = false)
            TEXT_ALBUM_AVERAGE -> albumColor(average = true)
            else -> null
        } ?: PolyColor(textColor, textChroma, textChromaSpeed)
        val sung = when (highlightSource) {
            HIGHLIGHT_ACCENT -> accentColor
            HIGHLIGHT_ALBUM_ART -> albumColor(average = false) ?: text
            HIGHLIGHT_ALBUM_AVERAGE -> albumColor(average = true) ?: text
            else -> text
        }
        if (textSource == TEXT_CUSTOM) return LyricsColors(sung, unsungColor, otherColor)
        return LyricsColors(sung, text.multiplyAlpha(UNSUNG_ALPHA), text.multiplyAlpha(OTHER_ALPHA))
    }

    private fun albumColor(average: Boolean): PolyColor? {
        if (!isReal || MediaService.current == null) return null
        val argb = (if (average) MediaService.artAverage else MediaService.artAccent) ?: return null
        return PolyColor(adjustColor(argb, albumTextSaturation / 100f, albumTextLightness / 100f))
    }

    private fun smoothPosition(info: MediaInfo, now: Long): Long {
        val target = info.positionAt(now).toDouble()
        val dt = (now - shownAt).coerceIn(0L, 1_000L).toDouble()
        shownAt = now
        val jumped = info.title != shownTitle || !info.playing || abs(target - shownPosition) > SNAP_DISTANCE_MS
        shownTitle = info.title
        shownPosition = if (jumped) target else {
            val predicted = shownPosition + dt
            maxOf(predicted + (target - predicted) * (1.0 - exp(-dt / SMOOTHING_MS)), shownPosition)
        }
        return shownPosition.toLong()
    }

    private fun alignFraction(): Float = when (alignment) {
        PolyAlign.TopLeft, PolyAlign.Left, PolyAlign.BottomLeft -> 0f
        PolyAlign.TopRight, PolyAlign.Right, PolyAlign.BottomRight -> 1f
        else -> 0.5f
    }

    private val isStatic: Boolean get() = staticWidth && staticW > 0f && staticH > 0f

    private val baseScale: Float get() = textScale.coerceAtLeast(0.01f)

    private fun naturalWidth(): Float = padLeft + padRight + ((frame.value?.width ?: MAX_WIDTH) + 2f * INSET) * baseScale

    private fun naturalHeight(): Float =
        padTop + padBottom + ((frame.value?.height ?: ((linesBefore + linesAfter + 1) * (ROW_HEIGHT + LINE_GAP) - LINE_GAP)) + 2f * INSET) * baseScale

    private fun cardFont(): String? = if (font == Font.Minecraft) null else getPoppinsFontName()

    private fun screenOpen(): Boolean = Platform.screen().current<Any?>() != null

    @Composable
    override fun Content() {
        val current = frame.value ?: return
        val scale = baseScale
        val backdrop = backdrop(current, scale)
        val box = if (backdrop != null) PolyModifier else hudBackground()
        val sized = if (isStatic) box.size(staticW, staticH) else box
        val bounds = boxBounds(current, scale)
        val inset = INSET * scale
        PolyBox(modifier = sized.padding(padLeft + inset, padTop + inset, padRight + inset, padBottom + inset)) {
            PolyCanvas(PolyModifier.size(current.width * scale, current.height * scale)) { x, y, _, h ->
                val unit = h / current.height.coerceAtLeast(0.01f)
                save()
                translate(x, y)
                scale(unit, unit)
                val popping = current.pop < 1f
                if (popping) beginPop(current.pop, backdrop?.bounds ?: Rect.makeWH(current.width, current.height))
                if (backdrop != null) drawBackdrop(backdrop)
                save()
                clipRect(bounds.left, bounds.top, bounds.width, bounds.height)
                drawMarks(current)
                restore()
                if (popping) restore()
                restore()
            }
        }
    }

    private fun boxBounds(current: LyricsFrame, scale: Float): Rect {
        val boxW = if (isStatic) staticW else padLeft + padRight + (current.width + 2f * INSET) * scale
        val boxH = if (isStatic) staticH else padTop + padBottom + (current.height + 2f * INSET) * scale
        return Rect.makeXYWH(-padLeft / scale - INSET, -padTop / scale - INSET, boxW / scale, boxH / scale)
    }

    private fun backdrop(current: LyricsFrame, scale: Float): MediaCardBackdrop? {
        if (!artBackground && !(onlyInGuis && popIn)) return null
        val merged = if (bgMerged) mediaMergePiece(padLeft + INSET * scale, padTop + INSET * scale, scale) ?: return null else null
        if (merged != null && (!artBackground || current.art == null)) return null
        val showColor = merged == null && showBackground && hasBackground()
        val bounds = boxBounds(current, scale)
        return MediaCardBackdrop(
            bounds = bounds,
            radius = if (showColor || artBackground) bgRadius / scale else 0f,
            color = if (showColor) PolyColor(bgColor, bgChroma, bgChromaSpeed) else null,
            art = current.art,
            artBlur = artBackgroundBlur,
            artAlpha = artBackgroundOpacity / 100f,
            artBounds = merged?.artBounds ?: bounds,
            roundedCorners = merged?.roundedCorners ?: ALL_CORNERS,
        )
    }

    override fun clone(): Hud = (super.clone() as LyricsHud).also {
        it.unsungColor = unsungColor.copy()
        it.otherColor = otherColor.copy()
        it.accentColor = accentColor.copy()
        it._staticW = mutableStateOf(staticW)
        it._staticH = mutableStateOf(staticH)
        it.frame = mutableStateOf(null)
        it.layouts = IdentityHashMap()
        it.layoutKey = null
        it.songWidth = 0f
        it.songWidthKey = null
        it.shownHeight = 0f
        it.shownPosition = 0.0
        it.shownAt = 0L
        it.shownTitle = null
        it.popStart = 0L
        it.acquired = false
    }
}

private val GLOW_OFFSETS = listOf(-GLOW_SPREAD to 0f, GLOW_SPREAD to 0f, 0f to -GLOW_SPREAD, 0f to GLOW_SPREAD)

private fun RenderContext.drawMarks(frame: LyricsFrame) {
    val label = Label(FONT_SIZE, frame.fontName, frame.shadow)
    val plain = Label(FONT_SIZE, frame.fontName)
    for (mark in frame.marks) {
        save()
        translate(mark.cx, mark.baseline)
        if (mark.scale != 1f) scale(mark.scale, mark.scale)
        drawGlyph(mark, label, plain)
        restore()
    }
    val credit = frame.credit ?: return
    if (!frame.creditColor.visible) return
    val small = Label(CREDIT_SIZE, frame.fontName)
    with(small) { draw(credit, frame.width - small.width(credit), frame.height + INSET - CREDIT_SIZE / 3f, PolyColor(frame.creditColor)) }
}

private fun RenderContext.drawGlyph(mark: GlyphMark, label: Label, plain: Label) {
    val x = -mark.width / 2f
    if (mark.glow > 0f) {
        val halo = mark.sung.withAlphaScale(GLOW_ALPHA * mark.glow)
        if (halo.visible) for ((dx, dy) in GLOW_OFFSETS) with(plain) { draw(mark.text, x + dx, dy, PolyColor(halo)) }
    }
    if (mark.progress < 1f && mark.base.visible) with(label) { draw(mark.text, x, 0f, PolyColor(mark.base)) }
    if (mark.progress <= 0f || !mark.sung.visible) return
    if (mark.progress >= 1f) {
        with(label) { draw(mark.text, x, 0f, PolyColor(mark.sung)) }
        return
    }
    val edge = -FEATHER + (mark.width + FEATHER) * mark.progress
    fun slice(from: Float, to: Float, color: Int) {
        val left = from.coerceAtLeast(0f)
        val right = to.coerceAtMost(mark.width + 1f)
        if (right <= left || !color.visible) return
        save()
        clipRect(x + left, -ROW_HEIGHT * 2f, right - left, ROW_HEIGHT * 3f)
        with(plain) { draw(mark.text, x, 0f, PolyColor(color)) }
        restore()
    }
    slice(-1f, edge, mark.sung)
    val step = FEATHER / FEATHER_STEPS
    for (k in 0 until FEATHER_STEPS) slice(edge + k * step, edge + (k + 1) * step, mark.sung.withAlphaScale(1f - (k + 0.5f) / FEATHER_STEPS))
}

private fun spring(ms: Float): Float {
    if (ms <= 0f) return 0f
    if (ms >= SPRING_SETTLED_MS) return 1f
    val t = ms / 1000f
    val damped = SPRING_OMEGA * sqrt(1f - SPRING_DAMPING * SPRING_DAMPING)
    val decay = exp(-SPRING_DAMPING * SPRING_OMEGA * t)
    return 1f - decay * (cos(damped * t) + SPRING_DAMPING * SPRING_OMEGA / damped * sin(damped * t))
}

private fun curve(t: Float, vararg keys: Float): Float {
    if (t <= keys[0]) return keys[1]
    for (i in 2 until keys.size step 2) {
        if (t > keys[i]) continue
        val f = (t - keys[i - 2]) / (keys[i] - keys[i - 2])
        return lerp(keys[i - 1], keys[i + 1], f * f * (3f - 2f * f))
    }
    return keys.last()
}

private fun wordScale(progress: Float): Float = curve(progress, 0f, 0.985f, 0.7f, 1.02f, 1f, 1f)

private fun wordLift(progress: Float): Float = curve(progress, 0f, 0f, 0.9f, 1f, 1f, SUNG_LIFT)

private fun wordGlow(progress: Float): Float = curve(progress, 0f, 0f, 0.15f, 1f, 0.6f, 1f, 1f, 0f)

private val Int.visible: Boolean get() = (this ushr 24) >= 8

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun Int.withAlphaScale(scale: Float): Int =
    ((((this ushr 24) and 0xFF) * scale).roundToInt().coerceIn(0, 255) shl 24) or (this and 0xFFFFFF)

private fun mix(a: Int, b: Int, t: Float): Int {
    if (t <= 0f) return a
    if (t >= 1f) return b
    var out = 0
    for (shift in 0..24 step 8) {
        val from = (a ushr shift) and 0xFF
        val to = (b ushr shift) and 0xFF
        out = out or ((from + (to - from) * t).roundToInt() shl shift)
    }
    return out
}
