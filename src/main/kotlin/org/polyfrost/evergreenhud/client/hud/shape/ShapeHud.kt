package org.polyfrost.evergreenhud.client.hud.shape

import org.polyfrost.evergreenhud.client.utils.EvergreenHud
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.skia.Path
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Hud

class ShapeHud : EvergreenHud(
    id = "shape.json",
    title = "Shape",
    category = Category.INFO,
) {
    @Dropdown(title = "Shape", options = ["Rectangle", "Ellipse", "Triangle", "Polygon", "Star", "Ring"])
    var shape = RECTANGLE

    @Color(title = "Color")
    var color = PolyColor(0xFFFFFFFF.toInt())

    @Slider(title = "Corner Radius", min = 0F, max = 32F, step = 1F)
    var cornerRadius = 0f

    @Slider(title = "Sides", min = 3F, max = 12F, step = 1F)
    var sides = 6

    @Slider(title = "Points", min = 3F, max = 12F, step = 1F)
    var points = 5

    @Slider(title = "Inner Radius", description = "Size of the star's inner points, as a percentage of its outer points.", min = 5F, max = 95F, step = 5F)
    var innerRadius = 45f

    @Slider(title = "Ring Thickness", description = "Thickness of the ring, as a percentage of its radius.", min = 5F, max = 100F, step = 5F)
    var ringThickness = 20f

    @Slider(title = "Ring Start", description = "Where the ring begins, in degrees clockwise from the top.", min = 0F, max = 359F, step = 1F)
    var ringStart = 0f

    @Slider(title = "Ring Sweep", description = "How far the ring travels, in degrees. Use 360 for a full ring.", min = 1F, max = 360F, step = 1F)
    var ringSweep = 360f

    @Slider(title = "Rotation", min = 0F, max = 359F, step = 1F)
    var rotation = 0f

    @Switch(title = "Outline")
    var outline = false

    @Color(title = "Outline Color")
    var outlineColor = PolyColor(0xFF000000.toInt())

    @Slider(title = "Outline Width", min = 0.5F, max = 8F, step = 0.5F)
    var outlineWidth = 1f

    private var _staticW = mutableStateOf(DEFAULT_SIDE)
    override var staticW: Float get() = _staticW.value; set(v) { _staticW.value = v }

    private var _staticH = mutableStateOf(DEFAULT_SIDE)
    override var staticH: Float get() = _staticH.value; set(v) { _staticH.value = v }

    @Transient
    private var cachedPath: Path? = null

    @Transient
    private var cachedKey: PathKey? = null

    init {
        showBackground = false
    }

    override fun setup() {
        super.setup()
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", DEFAULT_SIDE)
        tree?.getProp("staticH")?.addMetadata("default", DEFAULT_SIDE)

        if (isReal) {
            hideIf("cornerRadius") { shape != RECTANGLE }
            hideIf("sides") { shape != POLYGON }
            hideIf("points") { shape != STAR }
            hideIf("innerRadius") { shape != STAR }
            hideIf("ringThickness") { shape != RING }
            hideIf("ringStart") { shape != RING }
            hideIf("ringSweep") { shape != RING }
            hideIf("rotation") { shape == RING }
            hideIf("outline") { shape == RING }
            hideIf("outlineColor") { shape == RING || !outline }
            hideIf("outlineWidth") { shape == RING || !outline }
        }
    }

    override fun minimumSize(): Pair<Float, Float> = 2f to 2f

    override fun canMergeBackground(): Boolean = true

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun update(): Boolean = false

    override fun remove() {
        cachedPath?.close()
        cachedPath = null
        cachedKey = null
    }

    override fun clone(): Hud = (super.clone() as ShapeHud).apply {
        _staticW = mutableStateOf(this@ShapeHud.staticW)
        _staticH = mutableStateOf(this@ShapeHud.staticH)
        cachedPath = null
        cachedKey = null
        color = color.copy()
        outlineColor = outlineColor.copy()
    }

    @Composable
    override fun Content() {
        val sizeModifier = PolyModifier.size(scaledWidth, scaledHeight)
        if (showBackground) {
            PolyBox(modifier = hudBackground(sizeModifier)) {
                Shape(sizeModifier)
            }
        } else {
            Shape(sizeModifier)
        }
    }

    @Composable
    private fun Shape(modifier: PolyModifier) {
        PolyCanvas(modifier) { x, y, width, height -> draw(x, y, width, height) }
    }

    private fun RenderContext.draw(x: Float, y: Float, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return

        val centreX = x + width / 2f
        val centreY = y + height / 2f

        save()
        if (rotation != 0f && shape != RING) canvas.rotate(rotation, centreX, centreY)

        if (shape == RING) {
            val radius = minOf(width, height) / 2f
            val thickness = (radius * ringThickness / 100f).coerceIn(0.5f, radius)
            arcStroke(x, y, width, height, ringStart, ringSweep, color, thickness)
            restore()
            return
        }

        val stroke = if (outline) outlineWidth else 0f
        val left = x + stroke / 2f
        val top = y + stroke / 2f
        val w = (width - stroke).coerceAtLeast(0.1f)
        val h = (height - stroke).coerceAtLeast(0.1f)

        if (shape == RECTANGLE) {
            rect(left, top, w, h, color, cornerRadius)
            if (stroke > 0f) rectStroke(left, top, w, h, outlineColor, stroke, cornerRadius)
        } else if (shape == ELLIPSE) {
            oval(left, top, w, h, color)
            if (stroke > 0f) ovalStroke(left, top, w, h, outlineColor, stroke)
        } else {
            val path = path(left, top, w, h)
            fillPath(path, color)
            if (stroke > 0f) strokePath(path, outlineColor, stroke)
        }

        restore()
    }

    private fun path(left: Float, top: Float, w: Float, h: Float): Path {
        val vertices = if (shape == TRIANGLE) 3 else if (shape == POLYGON) sides else points
        val key = PathKey(shape, left, top, w, h, vertices, if (shape == STAR) innerRadius else 0f)

        cachedPath?.let { if (key == cachedKey) return it }
        cachedPath?.close()

        val centreX = left + w / 2f
        val centreY = top + h / 2f
        val path = if (shape == STAR) {
            starPath(centreX, centreY, w / 2f, h / 2f, vertices, innerRadius / 100f)
        } else {
            polygonPath(centreX, centreY, w / 2f, h / 2f, vertices)
        }

        cachedPath = path
        cachedKey = key
        return path
    }

    private data class PathKey(
        val shape: Int,
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val vertices: Int,
        val innerRadius: Float,
    )

    companion object {
        private const val DEFAULT_SIDE = 48f

        private const val RECTANGLE = 0
        private const val ELLIPSE = 1
        private const val TRIANGLE = 2
        private const val POLYGON = 3
        private const val STAR = 4
        private const val RING = 5
    }
}
