@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.composables.NodeBox
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.SCHEMA_CONTENT_SCALE
import org.polyfrost.evergreenhud.client.utils.addHudSchema
import org.polyfrost.evergreenhud.client.utils.applyCase
import org.polyfrost.evergreenhud.client.utils.migrateContentScale
import org.polyfrost.evergreenhud.client.utils.migrateHudSchema
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.copy
import org.polyfrost.evergreenhud.client.utils.media.MediaCommand
import org.polyfrost.evergreenhud.client.utils.media.MediaInfo
import org.polyfrost.evergreenhud.client.utils.media.MediaService
import org.polyfrost.evergreenhud.client.utils.media.cleanTitle
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.HudMouseButton
import org.polyfrost.oneconfig.api.hud.v1.clickRegion
import org.polyfrost.oneconfig.api.hud.v1.hoverRegion
import org.polyfrost.oneconfig.api.hud.v1.isClickRegionHovered
import org.polyfrost.oneconfig.api.hud.v1.removeClickRegion
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp

private const val SNAP_DISTANCE_MS = 2_000.0
private const val SMOOTHING_MS = 250.0
private const val SCROLL_STEP_MS = 16L
private const val IDLE_STEP_MS = 100L
private const val NANOS_PER_MS = 1_000_000L
private const val SCROLL_STEP_NS = SCROLL_STEP_MS * NANOS_PER_MS
private const val IDLE_STEP_NS = IDLE_STEP_MS * NANOS_PER_MS
private const val ACCENT_ALBUM_ART = 1
private const val ACCENT_ALBUM_AVERAGE = 2
private const val SCROLL_BOUNCE = 1
private const val TEXT_CUSTOM = 1
private const val TEXT_ALBUM_ART = 2
private const val TEXT_ALBUM_AVERAGE = 3
private const val POP_MS = 220f
private const val DRAWN_GRACE_NS = 200_000_000L

private const val SEEK_STEPS = 2000L

private const val MAX_POSITION_STEP_MS = 250L
private const val VANILLA_EFFECTS_MARGIN = 1f
//? if > 1.8.9 {
private const val VANILLA_EFFECTS_BOTTOM = 51f
private const val VANILLA_EFFECTS_GAP = 2f
private const val DEFAULT_Y = VANILLA_EFFECTS_BOTTOM + VANILLA_EFFECTS_GAP
//?} else
//private const val DEFAULT_Y = VANILLA_EFFECTS_MARGIN

private fun mediaBind(command: MediaCommand, allowed: (MediaInfo) -> Boolean): OneConfigKeybind =
    KeybindHelper.builder()
        .action { pressed: Boolean ->
            val current = MediaService.current
            if (pressed && current != null && allowed(current)) MediaService.send(command)
            true
        }
        .register()

internal fun stepPosition(positionMs: Long, lengthMs: Long): Long {
    if (lengthMs > 0L && positionMs >= lengthMs) return lengthMs
    val step = if (lengthMs > 0L) (lengthMs / SEEK_STEPS).coerceIn(1L, MAX_POSITION_STEP_MS) else 1000L
    return positionMs - positionMs % 1000L % step
}

private val EXAMPLE = MediaInfo(
    player = "example",
    title = "Midnight City",
    artist = "M83",
    artUrl = null,
    playing = true,
    positionMs = 83_000L,
    lengthMs = 243_000L,
    canPrevious = true,
    canNext = true,
    canToggle = true,
)

class MediaHud : Hud(
    id = "media.json",
    title = "Media Player",
    category = Category.INFO,
) {
    @Text(
        title = "Sources",
        description = "Player names to show, comma separated. Empty shows all.",
    )
    var sources = ""

    @Switch(title = "Clean Title", description = "Removes extras like \"(Official Video)\".")
    var cleanTitles = true

    @Switch(title = "Show Album Art")
    var showAlbumArt = true

    @Switch(title = "Concentric Album Art Corners", description = "Matches the art's corners to the background.")
    var concentricArt = true

    @Slider(title = "Album Art Corner Radius", min = 0f, max = MediaCardLayout.ART_SIZE / 2f, step = 1f)
    var artCornerRadius = MediaCardLayout.ART_RADIUS

    @Dropdown(
        title = "Title Scroll",
        description = "How long titles scroll.",
        options = ["Loop", "Back and Forth"],
    )
    var titleScroll = 0

    @Dropdown(
        title = "Button Style",
        description = "Material adds circles behind the skip buttons.",
        options = ["Classic", "Material"],
    )
    var buttonStyle = BUTTONS_CLASSIC

    @Switch(title = "Cutout Play / Pause Icon", description = "Lets the background show through the icon.")
    var cutoutGlyph = false

    @Dropdown(title = "Seek Bar Position", options = ["Below", "Above", "Hidden"], subcategory = "Seek Bar")
    var seekPosition = SEEK_BELOW

    @Slider(title = "Seek Bar Width", description = "Percent of the card's width.", min = 10f, max = 100f, step = 5f, subcategory = "Seek Bar")
    var seekWidth = 100f

    @Dropdown(title = "Seek Bar Style", options = ["Rounded", "Square", "Wave"], subcategory = "Seek Bar")
    var seekStyle = SEEK_ROUNDED

    @Switch(title = "Show Time", subcategory = "Seek Bar")
    var showTimes = true

    @Dropdown(
        title = "Accent Source",
        description = "Color of the play button and seek bar.",
        options = ["Accent Color", "Album Art", "Album Art Average"],
        subcategory = "Colors",
    )
    var accentSource = ACCENT_ALBUM_ART

    @Color(title = "Accent Color", description = "Play button and seek bar.", subcategory = "Colors")
    var accentColor = PolyColor(0xFF7CE0A8.toInt())

    @Dropdown(
        title = "Text Color Source",
        description = "Color of the title, artist and time.",
        options = ["Text Color", "Custom", "Album Art", "Album Art Average"],
        subcategory = "Colors",
    )
    var textSource = 0

    @Color(title = "Title Color", subcategory = "Colors")
    var titleColor = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Artist Color", subcategory = "Colors")
    var artistColor = PolyColor(0x99FFFFFF.toInt())

    @Color(title = "Time Color", subcategory = "Colors")
    var timeColor = PolyColor(0x99FFFFFF.toInt())

    @Slider(title = "Album Text Saturation", description = "In percent.", min = 0f, max = 200f, step = 5f, subcategory = "Colors")
    var albumTextSaturation = 100f

    @Slider(title = "Album Text Lightness", description = "Positive is lighter, negative is darker.", min = -100f, max = 100f, step = 5f, subcategory = "Colors")
    var albumTextLightness = 0f

    @Switch(title = "Custom Control Colors", subcategory = "Colors")
    var customControls = false

    @Color(title = "Skip Button Color", subcategory = "Colors")
    var controlColor = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Play / Pause Icon Color", subcategory = "Colors")
    var glyphColor = PLAY_GLYPH.copy()

    @Switch(title = "Album Art Background", description = "Blurred album art behind the card.", subcategory = "Background")
    var artBackground = false

    @Slider(title = "Album Art Blur", min = 0f, max = 20f, step = 1f, subcategory = "Background")
    var artBackgroundBlur = 8f

    @Slider(title = "Album Art Opacity", min = 0f, max = 100f, step = 5f, subcategory = "Background")
    var artBackgroundOpacity = 100f

    @Switch(title = "Only Show in GUIs", description = "Only shows while a screen like chat is open.", subcategory = "Visibility")
    var onlyInGuis = false

    @Switch(title = "Pop In Animation", description = "Fades the card in when a screen opens.", subcategory = "Visibility")
    var popIn = true

    @Keybind(title = "Play / Pause Key")
    var playPauseKey = mediaBind(MediaCommand.Toggle) { it.canToggle }

    @Keybind(title = "Next Track Key")
    var nextKey = mediaBind(MediaCommand.Next) { it.canNext }

    @Keybind(title = "Previous Track Key")
    var previousKey = mediaBind(MediaCommand.Previous) { it.canPrevious }

    private var _staticW = mutableStateOf(0f)
    override var staticW: Float get() = _staticW.value; set(v) { _staticW.value = v }

    private var _staticH = mutableStateOf(0f)
    override var staticH: Float get() = _staticH.value; set(v) { _staticH.value = v }

    private var card = mutableStateOf<MediaCardState?>(null)

    private var style = mutableStateOf<MediaCardStyle?>(null)

    private var pop = mutableFloatStateOf(1f)

    @Transient
    private var popStart = 0L

    @Transient
    private var lastDrawCheck = 0L

    private var regionsShown = mutableStateOf(false)

    private var scrollOffset = mutableFloatStateOf(0f)

    @Transient
    private var updateStepNs = SCROLL_STEP_NS

    @Transient
    private var livePosition = false

    @Transient
    private var textTitle: String? = null

    @Transient
    private var textArtist: String? = null

    @Transient
    private var textClean = false

    @Transient
    private var textCase = 0

    @Transient
    private var shownTitleText = ""

    @Transient
    private var shownArtistText = ""

    @Transient
    private var measuredTitle: String? = null

    @Transient
    private var measuredArtist: String? = null

    @Transient
    private var measuredFont: String? = null

    @Transient
    private var measuredRevision = -1

    @Transient
    private var measuredTitleWidth = 0f

    @Transient
    private var measuredTextWidth = 0f

    @Transient
    private var styleDirty = true

    @Transient
    private var styleArtRadius = Float.NaN

    @Transient
    private var styleAlbum: Int? = null

    @Transient
    private var styleAccent: Int? = null

    @Transient
    private var styleTextColor = 0

    @Transient
    private var styleTextChroma = false

    @Transient
    private var styleTextChromaSpeed = Float.NaN

    @Transient
    private var regionsDeclared = false

    @Transient
    private var regionScale = Float.NaN

    @Transient
    private var regionPadLeft = Float.NaN

    @Transient
    private var regionPadTop = Float.NaN

    @Transient
    private var regionTextWidth = Float.NaN

    @Transient
    private var regionShowArt = false

    @Transient
    private var regionSeek = -1

    @Transient
    private var regionEnabled = -1

    @Transient
    private var scrollStart = 0L

    @Transient
    private var scrollTitle: String? = null

    @Transient
    private var offscreen = false

    @Transient
    private var shownPosition = 0.0

    @Transient
    private var shownAt = 0L

    @Transient
    private var shownTitle: String? = null

    @Transient
    private var acquired = false

    override fun defaultPosition(): Pair<Float, Float> =
        HudManager.guiScreenWidth - scaledWidth - VANILLA_EFFECTS_MARGIN to DEFAULT_Y

    override fun showByDefault(): Boolean = true

    override fun canMergeBackground(): Boolean = true

    override fun addToSerialized(tree: Tree) {
        super.addToSerialized(tree)
        tree.addHudSchema()
    }

    override fun migrateLoaded(saved: Tree): Boolean = migrateHudSchema(saved) { from ->
        if (from < SCHEMA_CONTENT_SCALE) migrateContentScale()
    }

    override fun setup() {
        super.setup()
        tree?.getProp("staticW")?.addMetadata("default", naturalWidth())
        tree?.getProp("staticH")?.addMetadata("default", naturalHeight())
        showWhen("artCornerRadius") { !concentricArt }
        for (option in listOf("titleColor", "artistColor", "timeColor")) showWhen(option, hide = true) { textSource == TEXT_CUSTOM }
        for (option in listOf("albumTextSaturation", "albumTextLightness")) showWhen(option, hide = true) { textSource == TEXT_ALBUM_ART || textSource == TEXT_ALBUM_AVERAGE }
        for (option in listOf("controlColor", "glyphColor")) showWhen(option) { customControls && (option != "glyphColor" || !cutoutGlyph) }
        for (option in listOf("artBackgroundBlur", "artBackgroundOpacity")) showWhen(option) { artBackground }
        showWhen("popIn") { onlyInGuis }
        if (!isReal) return
        val options = listOf(
            "cleanTitles", "showAlbumArt", "concentricArt", "artCornerRadius", "titleScroll", "buttonStyle", "cutoutGlyph",
            "seekPosition", "seekWidth", "seekStyle", "showTimes",
            "accentSource", "accentColor", "textSource", "titleColor", "artistColor", "timeColor",
            "albumTextSaturation", "albumTextLightness", "customControls", "controlColor", "glyphColor",
            "artBackground", "artBackgroundBlur", "artBackgroundOpacity", "onlyInGuis", "popIn",
        )
        for (option in options) {
            addCallback(option) {
                styleDirty = true
                updateAndRecalculate()
            }
        }
        applySources()
        addCallback("sources") { applySources() }
        if (acquired) return
        acquired = true
        MediaService.acquire()
    }

    private fun applySources() {
        MediaService.sources = sources.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun showWhen(option: String, hide: Boolean = false, condition: () -> Boolean) {
        val off = if (hide) Property.Display.HIDDEN else Property.Display.DISABLED
        addDependency(option, null) { if (condition()) Property.Display.SHOWN else off }
    }

    override fun remove() {
        clearButtons()
        if (acquired) MediaService.release()
        acquired = false
        shownWidth = 0f
    }

    override fun update(): Boolean {
        val now = System.currentTimeMillis()
        val wasOffscreen = offscreen
        offscreen = onlyInGuis && !screenOpen()
        if (offscreen) popStart = now else if (wasOffscreen) popStart = maxOf(popStart, now - SCROLL_STEP_MS)
        pop.floatValue = if (onlyInGuis && popIn && !HudManager.isEditing) ((now - popStart) / POP_MS).coerceIn(0f, 1f) else 1f
        val next = read()
        if (isReal) shownWidth = if (hidden || next == null) 0f else scaledWidth
        trackTitle(next?.title, now)
        if (offscreen) {
            updateStepNs = IDLE_STEP_NS
            return false
        }
        var scrolling = false
        scrollOffset.floatValue = if (next == null) 0f else {
            measure(next)
            val textWidth = if (isStatic) cardTextWidth(next) else MediaCardLayout.MAX_TEXT_WIDTH
            scrolling = measuredTitleWidth > textWidth
            titleScrollOffset(measuredTitleWidth, textWidth, now - scrollStart, titleScroll == SCROLL_BOUNCE)
        }
        val nextStyle = next?.let(::cachedStyle)
        val changed = next != card.value || nextStyle != style.value
        card.value = next
        style.value = nextStyle
        syncButtons(next)
        updateStepNs = nextStepNs(next, changed || scrolling || pop.floatValue < 1f)
        return changed
    }

    private fun nextStepNs(state: MediaCardState?, animating: Boolean): Long {
        if (animating || HudManager.isEditing) return SCROLL_STEP_NS
        if (state == null || !livePosition || !state.playing || seekPosition == SEEK_HIDDEN) return IDLE_STEP_NS
        if (seekStyle == SEEK_WAVE) return SCROLL_STEP_NS
        return msUntilNextStep(shownPosition, state.lengthMs).coerceIn(SCROLL_STEP_MS, IDLE_STEP_MS) * NANOS_PER_MS
    }

    private fun msUntilNextStep(position: Double, lengthMs: Long): Long {
        val ms = position.toLong()
        if (lengthMs > 0L && ms >= lengthMs) return Long.MAX_VALUE
        val step = if (lengthMs > 0L) (lengthMs / SEEK_STEPS).coerceIn(1L, MAX_POSITION_STEP_MS) else 1000L
        val second = ms % 1000L
        var next = minOf(ms - second % step + step, ms - second + 1000L)
        if (lengthMs > 0L) next = minOf(next, lengthMs)
        return ceil(next - position).toLong()
    }

    private fun measure(state: MediaCardState) {
        val font = cardFont()
        val revision = FontManager.revision
        if (state.title === measuredTitle && state.artist === measuredArtist && font == measuredFont && revision == measuredRevision) return
        measuredTitle = state.title
        measuredArtist = state.artist
        measuredFont = font
        measuredRevision = revision
        measuredTitleWidth = titleWidth(state.title, font)
        measuredTextWidth = textWidth(measuredTitleWidth, state.artist, font)
    }

    private fun trackTitle(title: String?, now: Long) {
        if (title == scrollTitle) return
        scrollTitle = title
        scrollStart = now
    }

    override fun updateFrequency(): Long = if (onlyInGuis && offscreen == screenOpen()) -1L else updateStepNs

    override fun shouldShow(): Boolean {
        lastDrawCheck = System.nanoTime()
        return card.value != null && (!onlyInGuis || screenOpen())
    }

    private fun drawn(): Boolean = isVisible.value && System.nanoTime() - lastDrawCheck < DRAWN_GRACE_NS

    private fun read(): MediaCardState? {
        val live = if (isReal) MediaService.current else EXAMPLE
        val info = live ?: EXAMPLE.takeIf { HudManager.isEditing } ?: return null
        livePosition = info !== EXAMPLE
        val position = if (info === EXAMPLE) info.positionMs else steppedPosition(smoothPosition(info), info)
        shownText(info.title, info.artist)
        val title = shownTitleText
        val artist = shownArtistText
        val art = if (isReal && live != null) MediaService.art else null
        val artAccent = when (accentSource) {
            ACCENT_ALBUM_ART -> albumColor(average = false)
            ACCENT_ALBUM_AVERAGE -> albumColor(average = true)
            else -> null
        }
        val previous = card.value
        if (
            previous != null && previous.title === title && previous.artist === artist && previous.playing == info.playing &&
            previous.positionMs == position && previous.lengthMs == info.lengthMs && previous.canPrevious == info.canPrevious &&
            previous.canNext == info.canNext && previous.canToggle == info.canToggle && previous.art === art && previous.artAccent == artAccent
        ) return previous
        return MediaCardState(
            title = title,
            artist = artist,
            playing = info.playing,
            positionMs = position,
            lengthMs = info.lengthMs,
            canPrevious = info.canPrevious,
            canNext = info.canNext,
            canToggle = info.canToggle,
            art = art,
            artAccent = artAccent,
        )
    }

    private fun shownText(title: String, artist: String) {
        val case = caseType
        if (title == textTitle && artist == textArtist && cleanTitles == textClean && case == textCase) return
        textTitle = title
        textArtist = artist
        textClean = cleanTitles
        textCase = case
        shownTitleText = applyCase(if (cleanTitles) cleanTitle(title) else title)
        shownArtistText = applyCase(artist)
    }

    private fun albumColor(average: Boolean): Int? {
        if (!isReal || MediaService.current == null) return null
        return if (average) MediaService.artAverage else MediaService.artAccent
    }

    private fun cachedStyle(state: MediaCardState): MediaCardStyle {
        val radius = artRadius(cardScale)
        val album = textAlbumColor()
        val current = style.value
        val color = textColor
        val chroma = textChroma
        val chromaSpeed = textChromaSpeed
        if (
            isReal && !styleDirty && current != null && radius == styleArtRadius && album == styleAlbum && state.artAccent == styleAccent &&
            color == styleTextColor && chroma == styleTextChroma && chromaSpeed == styleTextChromaSpeed
        ) return current
        styleDirty = false
        styleArtRadius = radius
        styleAlbum = album
        styleAccent = state.artAccent
        styleTextColor = color
        styleTextChroma = chroma
        styleTextChromaSpeed = chromaSpeed
        return cardStyle(state, radius, album)
    }

    private fun cardStyle(state: MediaCardState, radius: Float, album: Int?): MediaCardStyle = MediaCardStyle(
        showArt = showAlbumArt,
        showTimes = showTimes,
        seekPosition = seekPosition,
        seekWidth = seekWidth / 100f,
        seekStyle = seekStyle,
        buttonStyle = buttonStyle,
        cutoutGlyph = cutoutGlyph,
        bounce = titleScroll == SCROLL_BOUNCE,
        artRadius = radius,
        colors = cardColors(state, album),
    )

    private fun textAlbumColor(): Int? = when (textSource) {
        TEXT_ALBUM_ART -> albumColor(average = false)
        TEXT_ALBUM_AVERAGE -> albumColor(average = true)
        else -> null
    }

    private fun cardColors(state: MediaCardState, album: Int?): MediaCardColors {
        val base = album?.let { PolyColor(adjustColor(it, albumTextSaturation / 100f, albumTextLightness / 100f)) }
            ?: textPolyColor
        val custom = textSource == TEXT_CUSTOM
        val title = if (custom) titleColor else base
        return MediaCardColors(
            title = title,
            artist = if (custom) artistColor else base.multiplyAlpha(DIM_ALPHA),
            time = if (custom) timeColor else base.multiplyAlpha(DIM_ALPHA),
            controls = if (customControls) controlColor else title,
            glyph = if (customControls) glyphColor else PLAY_GLYPH,
            accent = state.artAccent?.let { PolyColor(it) } ?: accentColor,
        )
    }

    private fun steppedPosition(positionMs: Long, info: MediaInfo): Long {
        if (seekStyle == SEEK_WAVE || !info.playing) return positionMs
        return stepPosition(positionMs, info.lengthMs)
    }

    private fun smoothPosition(info: MediaInfo): Long {
        val now = System.currentTimeMillis()
        val target = info.positionAt(now).toDouble()
        val dt = (now - shownAt).coerceIn(0L, 1_000L).toDouble()
        shownAt = now
        val jumped = info.title != shownTitle || !info.playing || abs(target - shownPosition) > SNAP_DISTANCE_MS
        shownTitle = info.title
        if (jumped) {
            shownPosition = target
        } else {
            val predicted = shownPosition + dt
            val eased = predicted + (target - predicted) * (1.0 - exp(-dt / SMOOTHING_MS))
            shownPosition = maxOf(eased, shownPosition)
        }
        if (info.lengthMs > 0) shownPosition = shownPosition.coerceAtMost(info.lengthMs.toDouble())
        return shownPosition.toLong()
    }

    private val isStatic: Boolean get() = staticWidth && staticW > 0f && staticH > 0f

    private val cardScale: Float
        get() = if (isStatic) {
            ((staticH - padTop - padBottom) / (MediaCardLayout.height(seekPosition) + 2f * MediaCardLayout.INSET)).coerceAtLeast(0.01f)
        } else {
            1f
        }

    private fun naturalWidth(): Float {
        val state = card.value ?: read() ?: return 0f
        val text = textWidth(state, cardFont())
        return padLeft + padRight + MediaCardLayout.width(showAlbumArt, text) + 2f * MediaCardLayout.INSET
    }

    private fun naturalHeight(): Float = padTop + padBottom + MediaCardLayout.height(seekPosition) + 2f * MediaCardLayout.INSET

    private fun cardTextWidth(state: MediaCardState): Float {
        if (!isStatic) {
            measure(state)
            return measuredTextWidth
        }
        val inner = (staticW - padLeft - padRight) / cardScale - 2f * MediaCardLayout.INSET
        return (inner - MediaCardLayout.textX(showAlbumArt)).coerceAtLeast(MediaCardLayout.MIN_TEXT_WIDTH)
    }

    private fun artRadius(scale: Float): Float {
        if (!concentricArt) return artCornerRadius
        if (!hasBackground()) return MediaCardLayout.ART_RADIUS
        val gap = minOf(padLeft, padTop) / scale + MediaCardLayout.INSET
        return (bgRadius / scale - gap).coerceAtLeast(0f)
    }

    private fun cardFont(): String? = if (font == Font.Minecraft) null else getPoppinsFontName()

    private fun screenOpen(): Boolean = Platform.screen().current<Any?>() != null

    private fun syncButtons(state: MediaCardState?) {
        if (!isReal || state == null || hidden) {
            clearButtons()
            return
        }
        val scale = cardScale
        val textWidth = cardTextWidth(state)
        val left = padLeft
        val top = padTop
        val showArt = showAlbumArt
        val seek = seekPosition
        var enabled = 0
        for (button in BUTTON_PREVIOUS..BUTTON_NEXT) if (enabled(state, button)) enabled = enabled or (1 shl button)
        if (
            regionsDeclared && scale == regionScale && left == regionPadLeft && top == regionPadTop && textWidth == regionTextWidth &&
            showArt == regionShowArt && seek == regionSeek && enabled == regionEnabled
        ) return
        regionScale = scale
        regionPadLeft = left
        regionPadTop = top
        regionTextWidth = textWidth
        regionShowArt = showArt
        regionSeek = seek
        regionEnabled = enabled
        val inset = MediaCardLayout.INSET * scale
        for (button in BUTTON_PREVIOUS..BUTTON_NEXT) {
            val bounds = MediaCardLayout.buttonBounds(button, showArt, textWidth, seek)
            val x = left + inset + bounds.left * scale
            val y = top + inset + bounds.top * scale
            val w = bounds.width * scale
            val h = bounds.height * scale
            if (enabled and (1 shl button) != 0) {
                clickRegion(BUTTON_REGIONS[button], x, y, w, h, anyScreen = true, buttons = CLICK_BUTTONS, expireAfterMillis = Long.MAX_VALUE) { activate(button) }
            } else {
                hoverRegion(BUTTON_REGIONS[button], x, y, w, h, anyScreen = true, expireAfterMillis = Long.MAX_VALUE)
            }
        }
        if (!regionsDeclared) {
            regionsDeclared = true
            regionsShown.value = true
        }
    }

    private fun clearButtons() {
        if (!regionsDeclared) return
        regionsDeclared = false
        for (region in BUTTON_REGIONS) removeClickRegion(region)
        regionsShown.value = false
    }

    private fun hoveredButton(): Int {
        if (!regionsShown.value) return -1
        var over = -1
        for (button in BUTTON_PREVIOUS..BUTTON_NEXT) if (isClickRegionHovered(BUTTON_REGIONS[button])) over = button
        return over
    }

    private fun enabled(state: MediaCardState, button: Int): Boolean = when (button) {
        BUTTON_PREVIOUS -> state.canPrevious
        BUTTON_TOGGLE -> state.canToggle
        BUTTON_NEXT -> state.canNext
        else -> false
    }

    private fun activate(button: Int) {
        val state = card.value ?: return
        if (!drawn()) return
        when (button) {
            BUTTON_PREVIOUS -> if (state.canPrevious) MediaService.send(MediaCommand.Previous)
            BUTTON_TOGGLE -> if (state.canToggle) MediaService.send(MediaCommand.Toggle)
            BUTTON_NEXT -> if (state.canNext) MediaService.send(MediaCommand.Next)
        }
    }

    @Composable
    override fun Content() {
        val current = card.value ?: return
        val cardStyle = style.value ?: return
        val scale = cardScale
        val inset = MediaCardLayout.INSET * scale
        val textWidth = cardTextWidth(current)
        val backdrop = backdrop(current, cardStyle, scale, inset, textWidth)
        val box = if (backdrop != null) PolyModifier else hudBackground()
        val sized = if (isStatic) box.size(staticW, staticH) else box
        val modifier = sized.padding(padLeft + inset, padTop + inset, padRight + inset, padBottom + inset)
        NodeBox(modifier = modifier) {
            MediaCard(
                state = current,
                style = cardStyle,
                fontName = cardFont(),
                shadow = showShadow,
                hovered = hoveredButton(),
                scrollOffset = scrollOffset.floatValue,
                scale = scale,
                textWidth = textWidth,
                backdrop = backdrop,
                pop = pop.floatValue,
            )
        }
    }

    private fun backdrop(state: MediaCardState, cardStyle: MediaCardStyle, scale: Float, inset: Float, textWidth: Float): MediaCardBackdrop? {
        if (!artBackground && !(onlyInGuis && popIn)) return null
        val merged = if (bgMerged) mediaMergePiece(padLeft + inset, padTop + inset, scale) ?: return null else null
        if (merged != null && (!artBackground || state.art == null)) return null
        val boxW = if (isStatic) staticW else padLeft + padRight + 2f * inset + MediaCardLayout.width(cardStyle.showArt, textWidth) * scale
        val boxH = if (isStatic) staticH else padTop + padBottom + 2f * inset + MediaCardLayout.height(cardStyle.seekPosition) * scale
        val left = -(padLeft + inset) / scale
        val top = -(padTop + inset) / scale
        val showColor = merged == null && showBackground && hasBackground()
        val bounds = Rect.makeXYWH(left, top, boxW / scale, boxH / scale)
        return MediaCardBackdrop(
            bounds = bounds,
            radius = if (showColor || artBackground) bgRadius / scale else 0f,
            color = if (showColor) bgPolyColor else null,
            art = if (artBackground) state.art else null,
            artBlur = artBackgroundBlur,
            artAlpha = artBackgroundOpacity / 100f,
            artBounds = merged?.artBounds ?: bounds,
            roundedCorners = merged?.roundedCorners ?: ALL_CORNERS,
        )
    }

    companion object {
        @Volatile
        internal var shownWidth = 0f

        private val BUTTON_REGIONS = listOf("media_previous", "media_toggle", "media_next")

        private val CLICK_BUTTONS = setOf(HudMouseButton.Left, HudMouseButton.Right)
    }

    override fun onCloned() {
        card = mutableStateOf(null)
        style = mutableStateOf(null)
        pop = mutableFloatStateOf(1f)
        popStart = 0L
        regionsShown = mutableStateOf(false)
        scrollOffset = mutableFloatStateOf(0f)
        updateStepNs = SCROLL_STEP_NS
        livePosition = false
        textTitle = null
        textArtist = null
        textClean = false
        textCase = 0
        shownTitleText = ""
        shownArtistText = ""
        measuredTitle = null
        measuredArtist = null
        measuredFont = null
        measuredRevision = -1
        measuredTitleWidth = 0f
        measuredTextWidth = 0f
        styleDirty = true
        styleArtRadius = Float.NaN
        styleAlbum = null
        styleAccent = null
        styleTextColor = 0
        styleTextChroma = false
        styleTextChromaSpeed = Float.NaN
        regionsDeclared = false
        regionScale = Float.NaN
        regionPadLeft = Float.NaN
        regionPadTop = Float.NaN
        regionTextWidth = Float.NaN
        regionShowArt = false
        regionSeek = -1
        regionEnabled = -1
        scrollStart = 0L
        scrollTitle = null
        offscreen = false
        shownPosition = 0.0
        shownAt = 0L
        shownTitle = null
        acquired = false
    }
}

internal fun adjustColor(argb: Int, saturation: Float, lightness: Float): Int {
    val r = (argb ushr 16 and 0xFF) / 255f
    val g = (argb ushr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else delta / (1f - abs(2f * l - 1f))
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * ((g - b) / delta).mod(6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    val shifted = if (lightness >= 0f) l + (1f - l) * lightness else l * (1f + lightness)
    val alpha = (argb ushr 24 and 0xFF) / 255f
    return PolyColor.hsl(h, (s * saturation).coerceIn(0f, 1f), shifted.coerceIn(0f, 1f), alpha).argb
}
