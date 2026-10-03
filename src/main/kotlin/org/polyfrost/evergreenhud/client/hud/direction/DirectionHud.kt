package org.polyfrost.evergreenhud.client.hud.direction

import org.polyfrost.evergreenhud.client.utils.EvergreenHud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.absoluteAt
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.clip
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.Facing
import org.polyfrost.evergreenhud.client.utils.cameraYaw
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import kotlin.math.abs
import kotlin.math.roundToInt

private const val FONT = 8f

private const val LINE = 10f

private const val LABEL = 24f

private const val TICK_INTERVAL = 15

private const val TICK_STRIP = 0.3f

private const val MINOR_TICK = 0.5f

private const val FADE_START = 0.55f

private const val PREVIEW_BEARING = 22.5f

private const val DEFAULT_WIDTH = 140f

private const val DEFAULT_HEIGHT = 24f

class DirectionHud : EvergreenHud(
    id = "direction.json",
    title = "Direction",
    category = Category.PLAYER,
) {
    @Slider(
        title = "Field of View",
        description = "How many degrees of the compass are visible at once. Lower values scroll faster.",
        min = 45F, max = 270F, step = 5F
    )
    var span = 120f

    @Switch(title = "Show Intercardinals", description = "Show NE, SE, SW and NW alongside N, E, S and W.")
    var showIntercardinals = true

    @Switch(title = "Show Ticks")
    var showTicks = true

    @Switch(title = "Show Heading", description = "Show the exact bearing in degrees above the compass.")
    var showHeading = false

    @Switch(title = "Fade Edges", description = "Fade labels out as they reach the edge of the compass.")
    var fadeEdges = true

    @Color(title = "Marker Color")
    var markerColor = PolyColor(0xFFFF5555.toInt())

    @Color(title = "Tick Color")
    var tickColor = PolyColor(0xFFAAAAAA.toInt())

    private var _staticW = mutableStateOf(DEFAULT_WIDTH)
    override var staticW: Float get() = _staticW.value; set(v) { _staticW.value = v }

    private var _staticH = mutableStateOf(DEFAULT_HEIGHT)
    override var staticH: Float get() = _staticH.value; set(v) { _staticH.value = v }

    override fun setup() {
        super.setup()
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", DEFAULT_WIDTH)
        tree?.getProp("staticH")?.addMetadata("default", DEFAULT_HEIGHT)
    }

    private val compassWidth: Float get() = if (staticWidth) scaledWidth else DEFAULT_WIDTH

    private val compassHeight: Float get() = if (staticWidth) scaledHeight else DEFAULT_HEIGHT

    override fun minimumSize(): Pair<Float, Float> = 32f to 12f

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun update(): Boolean = false

    override fun clone(): Hud = (super.clone() as DirectionHud).apply {
        _staticW = mutableStateOf(this@DirectionHud.staticW)
        _staticH = mutableStateOf(this@DirectionHud.staticH)
        markerColor = markerColor.copy()
        tickColor = tickColor.copy()
    }

    @Composable
    override fun Content() {
        val bearingState = remember { mutableFloatStateOf(PREVIEW_BEARING) }
        LaunchedEffect(Unit) {
            while (true) {
                withFrameMillis {
                    val yaw = cameraYaw ?: return@withFrameMillis
                    bearingState.floatValue = Facing.bearingOf(yaw)
                }
            }
        }
        val bearing by bearingState

        val modifier = hudBackground(PolyModifier.size(compassWidth, compassHeight))
        PolyBox(modifier = modifier.clip(bgRadius)) {
            Compass(bearing)
        }
    }

    @Composable
    private fun Compass(bearing: Float) {
        val width = compassWidth
        val fg = PolyColor(textColor, textChroma, textChromaSpeed)

        val headingHeight = if (showHeading) LINE * textScale else 0f
        val ribbonHeight = compassHeight - headingHeight
        val tickStrip = if (showTicks) ribbonHeight * TICK_STRIP else 0f

        if (showHeading) {
            Label("${bearing.roundToInt() % 360}°", fg, width, headingHeight, 0f, 0f)
        }

        Ticks(bearing, width, ribbonHeight, headingHeight, tickStrip)

        val centreX = width / 2f
        val pixelsPerDegree = width / span
        val halfSpan = span / 2f
        val labelWidth = LABEL * textScale

        for (facing in Facing.entries) {
            if (!showIntercardinals && !facing.isCardinal) continue

            val delta = Facing.wrapDegrees(facing.bearing - bearing)
            if (abs(delta) > halfSpan) continue

            Label(
                text = facing.abbreviated,
                color = fg.faded(delta, halfSpan),
                width = labelWidth,
                height = ribbonHeight - tickStrip,
                x = centreX + delta * pixelsPerDegree - labelWidth / 2f,
                y = headingHeight,
            )
        }
    }

    @Composable
    private fun Ticks(bearing: Float, width: Float, height: Float, top: Float, tickStrip: Float) {
        PolyCanvas(PolyModifier.size(width, height).absoluteAt(0f, top)) { x, y, w, h ->
            val centreX = x + w / 2f
            val bottom = y + h
            val halfSpan = span / 2f
            val pixelsPerDegree = w / span

            if (showTicks) {
                var heading = 0
                while (heading < 360) {
                    val delta = Facing.wrapDegrees(heading - bearing)
                    if (abs(delta) <= halfSpan) {
                        val tickHeight = if (heading % 45 == 0) tickStrip else tickStrip * MINOR_TICK
                        val tickX = centreX + delta * pixelsPerDegree
                        line(tickX, bottom - tickHeight, tickX, bottom, tickColor.faded(delta, halfSpan), 1f)
                    }
                    heading += TICK_INTERVAL
                }
            }

            line(centreX, y, centreX, bottom, markerColor, 1f)
        }
    }

    @Composable
    private fun Label(text: String, color: PolyColor, width: Float, height: Float, x: Float, y: Float) {
        PolyBox(modifier = PolyModifier.size(width, height).absoluteAt(x, y)) {
            if (font == Font.Minecraft) {
                PolyMcText(
                    text,
                    color = color,
                    shadow = showShadow,
                    scale = textScale,
                    modifier = PolyModifier.align(PolyAlign.Center),
                )
            } else {
                PolyText(
                    text,
                    color = color,
                    fontSize = FONT * textScale,
                    font = getPoppinsFontName(),
                    shadow = showShadow,
                    shadowColor = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed),
                    shadowOffset = shadowOffsetX,
                    modifier = PolyModifier.align(PolyAlign.Center),
                )
            }
        }
    }

    private fun PolyColor.faded(delta: Float, halfSpan: Float): PolyColor {
        if (!fadeEdges || halfSpan <= 0f) return this
        val distance = abs(delta) / halfSpan
        if (distance <= FADE_START) return this

        val factor = 1f - (distance - FADE_START) / (1f - FADE_START)
        val alpha = (((rawArgb ushr 24) and 0xFF) * factor).roundToInt().coerceIn(0, 255)
        return PolyColor((rawArgb and 0x00FFFFFF) or (alpha shl 24), chroma, chromaSpeed)
    }
}
