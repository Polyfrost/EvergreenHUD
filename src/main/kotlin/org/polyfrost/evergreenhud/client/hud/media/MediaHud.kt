package org.polyfrost.evergreenhud.client.hud.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.skia.Rect
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.evergreenhud.client.utils.media.MediaCommand
import org.polyfrost.evergreenhud.client.utils.media.MediaInfo
import org.polyfrost.evergreenhud.client.utils.media.MediaService
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.MouseInputEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import kotlin.math.abs
import kotlin.math.exp
import kotlin.time.Duration.Companion.milliseconds

private const val SNAP_DISTANCE_MS = 2_000.0
private const val SMOOTHING_MS = 250.0
private const val SCROLL_STEP_MS = 16L
private const val ACCENT_ALBUM_ART = 1
private const val ACCENT_ALBUM_AVERAGE = 2
private const val SCROLL_BOUNCE = 1
private const val TEXT_CUSTOM = 1
private const val TEXT_ALBUM_ART = 2
private const val TEXT_ALBUM_AVERAGE = 3
private const val POP_MS = 220f
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
    @Switch(title = "Show Album Art")
    var showAlbumArt = true

    @Switch(title = "Concentric Album Art Corners", description = "Round the album art to match the background's corners.")
    var concentricArt = true

    @Slider(title = "Album Art Corner Radius", min = 0f, max = MediaCardLayout.ART_SIZE / 2f, step = 1f)
    var artCornerRadius = MediaCardLayout.ART_RADIUS

    @Dropdown(
        title = "Title Scroll",
        description = "How a title too long for the card scrolls.",
        options = ["Loop", "Back and Forth"],
    )
    var titleScroll = 0

    @Dropdown(
        title = "Button Style",
        description = "Material puts the skip buttons in tonal circles and squares off the play button while playing.",
        options = ["Classic", "Material"],
    )
    var buttonStyle = BUTTONS_CLASSIC

    @Dropdown(title = "Seek Bar Position", options = ["Below", "Above", "Hidden"], subcategory = "Seek Bar")
    var seekPosition = SEEK_BELOW

    @Slider(title = "Seek Bar Width", description = "How much of the card's width the seek bar spans, in percent.", min = 10f, max = 100f, step = 5f, subcategory = "Seek Bar")
    var seekWidth = 100f

    @Dropdown(title = "Seek Bar Style", options = ["Rounded", "Square", "Wave"], subcategory = "Seek Bar")
    var seekStyle = SEEK_ROUNDED

    @Switch(title = "Show Time", subcategory = "Seek Bar")
    var showTimes = true

    @Dropdown(
        title = "Accent Source",
        description = "Where the play button and progress bar get their color.",
        options = ["Accent Color", "Album Art", "Album Art Average"],
        subcategory = "Colors",
    )
    var accentSource = ACCENT_ALBUM_ART

    @Color(title = "Accent Color", description = "Color of the play button and the progress bar.", subcategory = "Colors")
    var accentColor = PolyColor(0xFF7CE0A8.toInt())

    @Dropdown(
        title = "Text Color Source",
        description = "Where the title, artist and time get their color.",
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

    @Slider(title = "Album Text Saturation", description = "Scales the saturation of the album color, in percent.", min = 0f, max = 200f, step = 5f, subcategory = "Colors")
    var albumTextSaturation = 100f

    @Slider(title = "Album Text Lightness", description = "Moves the album color toward white (positive) or black (negative).", min = -100f, max = 100f, step = 5f, subcategory = "Colors")
    var albumTextLightness = 0f

    @Switch(title = "Custom Control Colors", subcategory = "Colors")
    var customControls = false

    @Color(title = "Skip Button Color", subcategory = "Colors")
    var controlColor = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Play / Pause Icon Color", subcategory = "Colors")
    var glyphColor = PLAY_GLYPH.copy()

    @Switch(title = "Album Art Background", description = "Draws the album art blurred behind the card, under the background color.", subcategory = "Background")
    var artBackground = false

    @Slider(title = "Album Art Blur", min = 0f, max = 20f, step = 1f, subcategory = "Background")
    var artBackgroundBlur = 8f

    @Slider(title = "Album Art Opacity", min = 0f, max = 100f, step = 5f, subcategory = "Background")
    var artBackgroundOpacity = 100f

    @Switch(title = "Only Show in GUIs", description = "Hides the card unless a screen such as chat or the inventory is open.", subcategory = "Visibility")
    var onlyInGuis = false

    @Switch(title = "Pop In Animation", description = "Fades and scales the card in when a screen opens.", subcategory = "Visibility")
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

    private var hovered = mutableIntStateOf(-1)

    private var scrollMs = mutableLongStateOf(0L)

    @Transient
    private var scrollStart = 0L

    @Transient
    private var shownPosition = 0.0

    @Transient
    private var shownAt = 0L

    @Transient
    private var shownTitle: String? = null

    @Transient
    private var handlers = ArrayList<EventHandler<*>>(2)

    @Transient
    private var acquired = false

    override fun defaultPosition(): Pair<Float, Float> =
        HudManager.guiScreenWidth - scaledWidth - VANILLA_EFFECTS_MARGIN to DEFAULT_Y

    override fun showByDefault(): Boolean = true

    override fun canMergeBackground(): Boolean = true

    override fun setup() {
        super.setup()
        tree?.getProp("staticW")?.addMetadata("default", naturalWidth())
        tree?.getProp("staticH")?.addMetadata("default", naturalHeight())
        showWhen("artCornerRadius") { !concentricArt }
        for (option in listOf("titleColor", "artistColor", "timeColor")) showWhen(option, hide = true) { textSource == TEXT_CUSTOM }
        for (option in listOf("albumTextSaturation", "albumTextLightness")) showWhen(option, hide = true) { textSource == TEXT_ALBUM_ART || textSource == TEXT_ALBUM_AVERAGE }
        for (option in listOf("controlColor", "glyphColor")) showWhen(option) { customControls }
        for (option in listOf("artBackgroundBlur", "artBackgroundOpacity")) showWhen(option) { artBackground }
        showWhen("popIn") { onlyInGuis }
        if (!isReal) return
        val options = listOf(
            "showAlbumArt", "concentricArt", "artCornerRadius", "titleScroll", "buttonStyle",
            "seekPosition", "seekWidth", "seekStyle", "showTimes",
            "accentSource", "accentColor", "textSource", "titleColor", "artistColor", "timeColor",
            "albumTextSaturation", "albumTextLightness", "customControls", "controlColor", "glyphColor",
            "artBackground", "artBackgroundBlur", "artBackgroundOpacity", "onlyInGuis", "popIn",
        )
        for (option in options) updateWhenChanged(option)
        if (acquired) return
        acquired = true
        MediaService.acquire()
        handlers.add(eventHandler { (x, y): MouseInputEvent.Moved -> hovered.intValue = buttonAt(x, y) })
        handlers.add(eventHandler { (button, state): MouseInputEvent ->
            if (button <= 1 && state == MouseInputEvent.PRESSED) click()
        })
    }

    private fun showWhen(option: String, hide: Boolean = false, condition: () -> Boolean) {
        val off = if (hide) Property.Display.HIDDEN else Property.Display.DISABLED
        addDependency(option, null) { if (condition()) Property.Display.SHOWN else off }
    }

    override fun remove() {
        for (handler in handlers) handler.unregister()
        handlers.clear()
        if (acquired) MediaService.release()
        acquired = false
    }

    override fun update(): Boolean {
        if (hovered.intValue != -1 && !screenOpen()) hovered.intValue = -1
        val next = read()
        val now = System.currentTimeMillis()
        if (next?.title != card.value?.title) scrollStart = now
        scrollMs.longValue = if (next != null && titleOverflows(next.title, cardFont(), if (isStatic) cardTextWidth(next) else MediaCardLayout.MAX_TEXT_WIDTH)) now - scrollStart else 0L
        if (onlyInGuis && !screenOpen()) popStart = now
        pop.floatValue = if (onlyInGuis && popIn && !HudManager.isEditing) ((now - popStart) / POP_MS).coerceIn(0f, 1f) else 1f
        val nextStyle = next?.let(::cardStyle)
        val changed = next != card.value || nextStyle != style.value
        card.value = next
        style.value = nextStyle
        return changed
    }

    override fun updateFrequency(): Long = SCROLL_STEP_MS.milliseconds.inWholeNanoseconds

    override fun shouldShow(): Boolean = card.value != null && (!onlyInGuis || screenOpen())

    private fun read(): MediaCardState? {
        val live = if (isReal) MediaService.current else EXAMPLE
        val info = live ?: EXAMPLE.takeIf { HudManager.isEditing } ?: return null
        val position = if (info === EXAMPLE) info.positionMs else smoothPosition(info)
        return MediaCardState(
            title = info.title,
            artist = info.artist,
            playing = info.playing,
            positionMs = position,
            lengthMs = info.lengthMs,
            canPrevious = info.canPrevious,
            canNext = info.canNext,
            canToggle = info.canToggle,
            art = if (isReal && live != null) MediaService.art else null,
            artAccent = when (accentSource) {
                ACCENT_ALBUM_ART -> albumColor(average = false)
                ACCENT_ALBUM_AVERAGE -> albumColor(average = true)
                else -> null
            },
        )
    }

    private fun albumColor(average: Boolean): Int? {
        if (!isReal || MediaService.current == null) return null
        return if (average) MediaService.artAverage else MediaService.artAccent
    }

    private fun cardStyle(state: MediaCardState): MediaCardStyle = MediaCardStyle(
        showArt = showAlbumArt,
        showTimes = showTimes,
        seekPosition = seekPosition,
        seekWidth = seekWidth / 100f,
        seekStyle = seekStyle,
        buttonStyle = buttonStyle,
        bounce = titleScroll == SCROLL_BOUNCE,
        artRadius = artRadius(cardScale),
        colors = cardColors(state),
    )

    private fun cardColors(state: MediaCardState): MediaCardColors {
        val album = when (textSource) {
            TEXT_ALBUM_ART -> albumColor(average = false)
            TEXT_ALBUM_AVERAGE -> albumColor(average = true)
            else -> null
        }
        val base = album?.let { PolyColor(adjustColor(it, albumTextSaturation / 100f, albumTextLightness / 100f)) }
            ?: PolyColor(textColor, textChroma, textChromaSpeed)
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

    private val baseScale: Float get() = textScale.coerceAtLeast(0.01f)

    private val cardScale: Float
        get() = if (isStatic) {
            ((staticH - padTop - padBottom) / (MediaCardLayout.height(seekPosition) + 2f * MediaCardLayout.INSET)).coerceAtLeast(0.01f)
        } else {
            baseScale
        }

    private fun naturalWidth(): Float {
        val state = card.value ?: read() ?: return 0f
        val text = textWidth(state, cardFont())
        return padLeft + padRight + (MediaCardLayout.width(showAlbumArt, text) + 2f * MediaCardLayout.INSET) * baseScale
    }

    private fun naturalHeight(): Float = padTop + padBottom + (MediaCardLayout.height(seekPosition) + 2f * MediaCardLayout.INSET) * baseScale

    private fun cardTextWidth(state: MediaCardState): Float {
        if (!isStatic) return textWidth(state, cardFont())
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

    private fun buttonAt(windowX: Float, windowY: Float): Int {
        if (card.value == null || hidden || HudManager.isEditing) return -1
        val screen = Platform.screen()
        val gx = windowX * HudManager.guiScreenWidth / screen.windowWidth().coerceAtLeast(1)
        val gy = windowY * HudManager.guiScreenHeight / screen.windowHeight().coerceAtLeast(1)
        val scale = cardScale
        val hudScale = effectiveScale.coerceAtLeast(0.01f)
        val inset = MediaCardLayout.INSET * scale
        val lx = ((gx - x) / hudScale - padLeft - inset) / scale
        val ly = ((gy - y) / hudScale - padTop - inset) / scale
        return MediaCardLayout.buttonAt(lx, ly, showAlbumArt, cardTextWidth(card.value ?: return -1), seekPosition)
    }

    private fun click() {
        val state = card.value ?: return
        if (!screenOpen()) return
        when (hovered.intValue) {
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
        PolyBox(modifier = modifier) {
            MediaCard(
                state = current,
                style = cardStyle,
                fontName = cardFont(),
                shadow = showShadow,
                hovered = hovered.intValue,
                scrollMs = scrollMs.longValue,
                scale = scale,
                textWidth = textWidth,
                backdrop = backdrop,
                pop = pop.floatValue,
            )
        }
    }

    private fun backdrop(state: MediaCardState, cardStyle: MediaCardStyle, scale: Float, inset: Float, textWidth: Float): MediaCardBackdrop? {
        if (!artBackground && !(onlyInGuis && popIn)) return null
        if (bgMerged) return null
        val boxW = if (isStatic) staticW else padLeft + padRight + 2f * inset + MediaCardLayout.width(cardStyle.showArt, textWidth) * scale
        val boxH = if (isStatic) staticH else padTop + padBottom + 2f * inset + MediaCardLayout.height(cardStyle.seekPosition) * scale
        val left = -(padLeft + inset) / scale
        val top = -(padTop + inset) / scale
        val showColor = showBackground && hasBackground()
        return MediaCardBackdrop(
            bounds = Rect.makeXYWH(left, top, boxW / scale, boxH / scale),
            radius = if (showColor || artBackground) bgRadius / scale else 0f,
            color = if (showColor) PolyColor(bgColor, bgChroma, bgChromaSpeed) else null,
            art = if (artBackground) state.art else null,
            artBlur = artBackgroundBlur,
            artAlpha = artBackgroundOpacity / 100f,
        )
    }

    override fun clone(): Hud = (super.clone() as MediaHud).also {
        it.accentColor = accentColor.copy()
        it.titleColor = titleColor.copy()
        it.artistColor = artistColor.copy()
        it.timeColor = timeColor.copy()
        it.controlColor = controlColor.copy()
        it.glyphColor = glyphColor.copy()
        it._staticW = mutableStateOf(staticW)
        it._staticH = mutableStateOf(staticH)
        it.card = mutableStateOf(null)
        it.style = mutableStateOf(null)
        it.pop = mutableFloatStateOf(1f)
        it.popStart = 0L
        it.hovered = mutableIntStateOf(-1)
        it.scrollMs = mutableLongStateOf(0L)
        it.scrollStart = 0L
        it.shownPosition = 0.0
        it.shownAt = 0L
        it.shownTitle = null
        it.handlers = ArrayList(2)
        it.acquired = false
    }
}

private fun adjustColor(argb: Int, saturation: Float, lightness: Float): Int {
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
