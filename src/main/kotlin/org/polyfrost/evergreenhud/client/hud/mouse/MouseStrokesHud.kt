package org.polyfrost.evergreenhud.client.hud.mouse

import org.polyfrost.evergreenhud.client.utils.EvergreenHud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.PathBuilder
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.clip
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.evergreenhud.client.hud.shape.oval
import org.polyfrost.evergreenhud.client.utils.Facing
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.math.abs
import kotlin.math.exp

private const val DECAY_RATE = 6f

private const val MAX_FRAME_SECONDS = 0.1f

private const val MAX_TRAIL_POINTS = 512

private const val MIN_POINT_DISTANCE = 0.35f

private const val FADE_STEPS = 16

private const val PREVIEW_X = 0.45f

private const val PREVIEW_Y = -0.3f

private const val DEFAULT_SIDE = 48f

class MouseStrokesHud : EvergreenHud(
    id = "mousestrokes.json",
    title = "Mouse Strokes",
    category = Category.INFO,
) {
    @Switch(title = "Draw Middle Dot", description = "Draws a dot in the center (duh).")
    var drawMiddleDot = true

    @Dropdown(
        title = "Dot Type",
        description = "Dot draws a dot where the mouse is, Snake draws the path of the mouse.",
        options = ["Dot", "Snake"],
    )
    var dotType = DOT

    @Slider(title = "Sensitivity", description = "How sensitive the \"dot\" is.", min = 0.1F, max = 5F, step = 0.1F)
    var sensitivity = 1f

    @Slider(
        title = "Scale",
        description = "How much room there is for the dot (doesn't scale the hud element).",
        min = 10F, max = 200F, step = 5F,
    )
    var scale = 60f

    @Slider(
        title = "Snake Length",
        description = "How long the snake's tail lasts, in milliseconds.",
        min = 100F, max = 3000F, step = 50F,
    )
    var snakeMs = 750f

    @Slider(
        title = "Dot Radius",
        description = "Radius of the moving dot, as a percentage of the hud's smallest side.",
        min = 1F, max = 30F, step = 0.5F,
    )
    var dotRadius = 9f

    @Slider(
        title = "Middle Dot Radius",
        description = "Radius of the centre dot, as a percentage of the hud's smallest side.",
        min = 1F, max = 30F, step = 0.5F,
    )
    var middleDotRadius = 5f

    @Slider(
        title = "Snake Thickness",
        description = "Thickness of the snake, as a percentage of the hud's smallest side.",
        min = 1F, max = 30F, step = 0.5F,
    )
    var snakeThickness = 5f

    @Switch(title = "Draw Snake Head", description = "Draws a dot at the head of the snake so it's easier to follow.")
    var drawSnakeHead = true

    @Slider(
        title = "Snake Head Radius",
        description = "Radius of the snake's head, as a percentage of the hud's smallest side.",
        min = 1F, max = 30F, step = 0.5F,
    )
    var snakeHeadRadius = 4.5f

    @Color(title = "Dot Color", description = "Center dot color.")
    var dotColor = PolyColor(0xFFFF5555.toInt())

    @Color(title = "Snake Head Color", description = "Colour of the dot at the head of the snake.")
    var snakeHeadColor = PolyColor(0xFF55FFFF.toInt())

    @Transient
    private val trail = ArrayDeque<TrailPoint>()

    @Transient
    private var lastYaw = Float.NaN

    @Transient
    private var lastPitch = 0f

    @Transient
    private var lastFrameMs = 0L

    private var _staticW = mutableStateOf(DEFAULT_SIDE)
    override var staticW: Float get() = _staticW.value; set(v) { _staticW.value = v }

    private var _staticH = mutableStateOf(DEFAULT_SIDE)
    override var staticH: Float get() = _staticH.value; set(v) { _staticH.value = v }

    override fun setup() {
        super.setup()
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", DEFAULT_SIDE)
        tree?.getProp("staticH")?.addMetadata("default", DEFAULT_SIDE)

        if (isReal) {
            hideIf("snakeMs") { dotType != SNAKE }
            hideIf("snakeThickness") { dotType != SNAKE }
            hideIf("dotRadius") { dotType != DOT }
            hideIf("middleDotRadius") { !drawMiddleDot }
            hideIf("drawSnakeHead") { dotType != SNAKE }
            hideIf("snakeHeadRadius") { dotType != SNAKE || !drawSnakeHead }
            hideIf("snakeHeadColor") { dotType != SNAKE || !drawSnakeHead }
        }
    }

    override fun minimumSize(): Pair<Float, Float> = 16f to 16f

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun update(): Boolean = false

    override fun remove() {
        trail.clear()
        lastYaw = Float.NaN
    }

    override fun clone(): Hud = (super.clone() as MouseStrokesHud).apply {
        _staticW = mutableStateOf(this@MouseStrokesHud.staticW)
        _staticH = mutableStateOf(this@MouseStrokesHud.staticH)
        dotColor = dotColor.copy()
        snakeHeadColor = snakeHeadColor.copy()
    }

    @Composable
    override fun Content() {
        val position = remember { mutableStateOf(if (isReal) Offset(0f, 0f) else Offset(PREVIEW_X, PREVIEW_Y)) }
        if (isReal) {
            LaunchedEffect(Unit) {
                while (true) {
                    withFrameMillis { nowMs -> position.value = step(nowMs) }
                }
            }
        }
        val offset by position

        val modifier = hudBackground(PolyModifier.size(scaledWidth, scaledHeight))
        PolyBox(modifier = modifier.clip(bgRadius)) {
            PolyCanvas(PolyModifier.size(scaledWidth, scaledHeight)) { x, y, w, h ->
                draw(offset, x, y, w, h)
            }
        }
    }

    private fun step(nowMs: Long): Offset {
        val deltaSeconds = if (lastFrameMs == 0L) 0f else ((nowMs - lastFrameMs) / 1000f).coerceIn(0f, MAX_FRAME_SECONDS)
        lastFrameMs = nowMs

        val player = mc.player
        val yaw = player?.yRot ?: 0f
        val pitch = player?.xRot ?: 0f
        if (player == null || lastYaw.isNaN()) {
            lastYaw = yaw
            lastPitch = pitch
        }

        val deltaYaw = Facing.wrapDegrees(yaw - lastYaw)
        val deltaPitch = pitch - lastPitch
        lastYaw = yaw
        lastPitch = pitch

        val decay = exp(-DECAY_RATE * deltaSeconds)
        val last = trail.lastOrNull()
        val x = ((last?.x ?: 0f) * scale * decay + deltaYaw * sensitivity).coerceIn(-scale, scale) / scale
        val y = ((last?.y ?: 0f) * scale * decay + deltaPitch * sensitivity).coerceIn(-scale, scale) / scale

        trail.addLast(TrailPoint(x, y, nowMs))
        val oldest = nowMs - snakeMs.toLong()
        while (trail.size > 1 && (trail.first().timeMs < oldest || trail.size > MAX_TRAIL_POINTS)) {
            trail.removeFirst()
        }

        return Offset(x, y)
    }

    private fun RenderContext.draw(offset: Offset, x: Float, y: Float, w: Float, h: Float) {
        if (w <= 0f || h <= 0f) return

        val side = minOf(w, h)
        val snake = dotType == SNAKE && isReal
        val radius = side * (if (snake) snakeThickness / 2f else dotRadius) / 100f
        val centreX = x + w / 2f
        val centreY = y + h / 2f
        val usableX = (w / 2f - radius).coerceAtLeast(0f)
        val usableY = (h / 2f - radius).coerceAtLeast(0f)
        val fg = PolyColor(textColor, textChroma, textChromaSpeed)

        if (snake) {
            snake(centreX, centreY, usableX, usableY, radius * 2f, fg)
            if (drawSnakeHead) {
                val head = trail.lastOrNull()
                dot(
                    centreX + (head?.x ?: offset.x) * usableX,
                    centreY + (head?.y ?: offset.y) * usableY,
                    side * snakeHeadRadius / 100f,
                    snakeHeadColor,
                )
            }
        } else {
            dot(centreX + offset.x * usableX, centreY + offset.y * usableY, radius, fg)
        }

        if (drawMiddleDot) {
            dot(centreX, centreY, side * middleDotRadius / 100f, dotColor)
        }
    }

    private fun RenderContext.snake(
        centreX: Float,
        centreY: Float,
        usableX: Float,
        usableY: Float,
        width: Float,
        color: PolyColor,
    ) {
        if (trail.size < 2 || width <= 0f) return

        val oldest = trail.first().timeMs
        val span = (trail.last().timeMs - oldest).toFloat()
        val xs = FloatArray(trail.size)
        val ys = FloatArray(trail.size)
        val ages = FloatArray(trail.size)
        var count = 0

        for (point in trail) {
            val px = centreX + point.x * usableX
            val py = centreY + point.y * usableY
            val age = if (span <= 0f) 1f else (point.timeMs - oldest) / span
            if (count > 0 &&
                abs(px - xs[count - 1]) < MIN_POINT_DISTANCE &&
                abs(py - ys[count - 1]) < MIN_POINT_DISTANCE
            ) {
                ages[count - 1] = age
                continue
            }
            xs[count] = px
            ys[count] = py
            ages[count] = age
            count++
        }

        if (count < 2) {
            dot(xs[0], ys[0], width / 2f, color)
            return
        }

        paint.mode = PaintMode.STROKE
        paint.strokeWidth = width
        paint.strokeCap = PaintStrokeCap.ROUND
        paint.strokeJoin = PaintStrokeJoin.ROUND

        PathBuilder().use { builder ->
            var start = 0
            while (start < count - 1) {
                val step = fadeStep(ages[start + 1])
                var end = start + 1
                while (end < count - 1 && fadeStep(ages[end + 1]) == step) end++

                builder.reset()
                builder.moveTo(xs[start], ys[start])
                for (i in start until end) builder.splineTo(xs, ys, i, count)
                builder.detach().use { path ->
                    paint.color = color.withAlphaScaled((step + 1).toFloat() / FADE_STEPS).argb
                    canvas.drawPath(path, paint)
                }

                start = end
            }
        }

        paint.mode = PaintMode.FILL
        paint.strokeCap = PaintStrokeCap.BUTT
        paint.strokeJoin = PaintStrokeJoin.MITER
    }

    private fun fadeStep(age: Float): Int =
        (age * FADE_STEPS).toInt().coerceIn(0, FADE_STEPS - 1)

    private fun PathBuilder.splineTo(xs: FloatArray, ys: FloatArray, i: Int, count: Int) {
        val previous = (i - 1).coerceAtLeast(0)
        val next = (i + 2).coerceAtMost(count - 1)
        cubicTo(
            xs[i] + (xs[i + 1] - xs[previous]) / 6f, ys[i] + (ys[i + 1] - ys[previous]) / 6f,
            xs[i + 1] - (xs[next] - xs[i]) / 6f, ys[i + 1] - (ys[next] - ys[i]) / 6f,
            xs[i + 1], ys[i + 1],
        )
    }

    private fun RenderContext.dot(centreX: Float, centreY: Float, radius: Float, color: PolyColor) {
        oval(centreX - radius, centreY - radius, radius * 2f, radius * 2f, color)
    }

    private fun PolyColor.withAlphaScaled(factor: Float): PolyColor {
        val alpha = (((rawArgb ushr 24) and 0xFF) * factor.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return PolyColor((rawArgb and 0x00FFFFFF) or (alpha shl 24), chroma, chromaSpeed)
    }

    private data class Offset(val x: Float, val y: Float)

    private data class TrailPoint(val x: Float, val y: Float, val timeMs: Long)

    companion object {
        private const val DOT = 0
        private const val SNAKE = 1
    }
}
