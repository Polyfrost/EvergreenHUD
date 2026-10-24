package org.polyfrost.evergreenhud.client.hud.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.evergreenhud.client.utils.media.MediaCommand
import org.polyfrost.evergreenhud.client.utils.media.MediaInfo
import org.polyfrost.evergreenhud.client.utils.media.MediaService
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
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
import kotlin.time.Duration.Companion.milliseconds

private const val SNAP_DISTANCE_MS = 2_000.0
private const val SMOOTHING_MS = 250.0
private const val SCROLL_STEP_MS = 16L
private const val ACCENT_ALBUM_ART = 1
private const val SCROLL_BOUNCE = 1

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

    @Switch(title = "Show Time")
    var showTimes = true

    @Dropdown(
        title = "Title Scroll",
        description = "How a title too long for the card scrolls.",
        options = ["Loop", "Back and Forth"],
    )
    var titleScroll = 0

    @Dropdown(
        title = "Accent Source",
        description = "Where the play button and progress bar get their color. Album Art falls back to the accent color when there is no usable cover.",
        options = ["Accent Color", "Album Art"],
    )
    var accentSource = ACCENT_ALBUM_ART

    @Color(title = "Accent Color", description = "Color of the play button and the progress bar.")
    var accentColor = PolyColor(0xFF7CE0A8.toInt())

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

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun setup() {
        super.setup()
        tree?.getProp("staticW")?.addMetadata("default", naturalWidth())
        tree?.getProp("staticH")?.addMetadata("default", naturalHeight())
        if (!isReal) return
        for (option in listOf("showAlbumArt", "showTimes", "titleScroll", "accentSource", "accentColor")) updateWhenChanged(option)
        if (acquired) return
        acquired = true
        MediaService.acquire()
        handlers.add(eventHandler { (x, y): MouseInputEvent.Moved -> hovered.intValue = buttonAt(x, y) })
        handlers.add(eventHandler { (button, state): MouseInputEvent ->
            if (button <= 1 && state == MouseInputEvent.PRESSED) click()
        })
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
        if (next == card.value) return false
        card.value = next
        return true
    }

    override fun updateFrequency(): Long = SCROLL_STEP_MS.milliseconds.inWholeNanoseconds

    override fun shouldShow(): Boolean = card.value != null

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
            art = if (showAlbumArt && isReal && live != null) MediaService.art else null,
            artAccent = if (accentSource == ACCENT_ALBUM_ART && isReal && live != null) MediaService.artAccent else null,
        )
    }

    private fun smoothPosition(info: MediaInfo): Long {
        val now = System.currentTimeMillis()
        val target = info.positionAt(now).toDouble()
        val dt = (now - shownAt).coerceIn(0L, 1_000L).toDouble()
        shownAt = now
        val jumped = info.title != shownTitle || !info.playing || kotlin.math.abs(target - shownPosition) > SNAP_DISTANCE_MS
        shownTitle = info.title
        if (jumped) {
            shownPosition = target
        } else {
            val predicted = shownPosition + dt
            val eased = predicted + (target - predicted) * (1.0 - kotlin.math.exp(-dt / SMOOTHING_MS))
            shownPosition = maxOf(eased, shownPosition)
        }
        if (info.lengthMs > 0) shownPosition = shownPosition.coerceAtMost(info.lengthMs.toDouble())
        return shownPosition.toLong()
    }

    private val isStatic: Boolean get() = staticWidth && staticW > 0f && staticH > 0f

    private val baseScale: Float get() = textScale.coerceAtLeast(0.01f)

    private val cardScale: Float
        get() = if (isStatic) {
            ((staticH - padTop - padBottom) / (MediaCardLayout.HEIGHT + 2f * MediaCardLayout.INSET)).coerceAtLeast(0.01f)
        } else {
            baseScale
        }

    private fun naturalWidth(): Float {
        val state = card.value ?: read() ?: return 0f
        val text = textWidth(state, cardFont())
        return padLeft + padRight + (MediaCardLayout.width(showAlbumArt, text) + 2f * MediaCardLayout.INSET) * baseScale
    }

    private fun naturalHeight(): Float = padTop + padBottom + (MediaCardLayout.HEIGHT + 2f * MediaCardLayout.INSET) * baseScale

    private fun cardTextWidth(state: MediaCardState): Float {
        if (!isStatic) return textWidth(state, cardFont())
        val inner = (staticW - padLeft - padRight) / cardScale - 2f * MediaCardLayout.INSET
        return (inner - MediaCardLayout.textX(showAlbumArt)).coerceAtLeast(MediaCardLayout.MIN_TEXT_WIDTH)
    }

    private fun artRadius(scale: Float): Float {
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
        return MediaCardLayout.buttonAt(lx, ly, showAlbumArt, cardTextWidth(card.value ?: return -1))
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
        val scale = cardScale
        val inset = MediaCardLayout.INSET * scale
        val background = if (isStatic) hudBackground().size(staticW, staticH) else hudBackground()
        val modifier = background.padding(padLeft + inset, padTop + inset, padRight + inset, padBottom + inset)
        PolyBox(modifier = modifier) {
            MediaCard(
                state = current,
                showArt = showAlbumArt,
                showTimes = showTimes,
                text = PolyColor(textColor, textChroma, textChromaSpeed),
                accent = current.artAccent?.let { PolyColor(it) } ?: accentColor,
                fontName = cardFont(),
                shadow = showShadow,
                hovered = hovered.intValue,
                scrollMs = scrollMs.longValue,
                bounce = titleScroll == SCROLL_BOUNCE,
                scale = scale,
                artRadius = artRadius(scale),
                textWidth = cardTextWidth(current),
            )
        }
    }

    override fun clone(): Hud = (super.clone() as MediaHud).also {
        it.accentColor = accentColor.copy()
        it._staticW = mutableStateOf(staticW)
        it._staticH = mutableStateOf(staticH)
        it.card = mutableStateOf(null)
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
