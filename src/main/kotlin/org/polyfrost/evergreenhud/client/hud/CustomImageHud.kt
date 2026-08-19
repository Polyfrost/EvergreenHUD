package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.oneconfig.api.config.v1.annotations.File
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import kotlin.math.min

private const val DEFAULT_SIDE = 48f

private const val MAX_FILE_BYTES = 16L * 1024L * 1024L

private val NEAREST = FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)

private val PLACEHOLDER_COLOR = PolyColor(0x40FFFFFF)
private val PLACEHOLDER_OUTLINE = PolyColor(0x80FFFFFF.toInt())

class CustomImageHud : Hud(
    id = "custom_image.json",
    title = "Custom Image",
    category = Category.INFO,
) {
    @File(
        title = "Image",
        description = "Image file to display.",
        types = ["png", "jpg", "jpeg", "bmp", "webp"],
        filterName = "Images",
    )
    var imagePath = ""

    @Switch(title = "Keep Aspect Ratio", description = "Fit the image inside the HUD without stretching it.")
    var keepAspect = true

    @Switch(title = "Smooth Scaling", description = "Turn off for sharp pixels on pixel art.")
    var smooth = true

    @Slider(title = "Opacity", min = 0F, max = 100F, step = 1F)
    var opacity = 100f

    @Slider(title = "Corner Radius", min = 0F, max = 20F, step = 1F)
    var cornerRadius = 0f

    private var _staticW = mutableStateOf(DEFAULT_SIDE)
    override var staticW: Float get() = _staticW.value; set(v) { _staticW.value = v }

    private var _staticH = mutableStateOf(DEFAULT_SIDE)
    override var staticH: Float get() = _staticH.value; set(v) { _staticH.value = v }

    @Transient
    private var cachedImage: Image? = null

    @Transient
    private var cachedPath: String? = null

    @Transient
    private var cachedStamp = 0L

    @Transient
    private val paint = Paint()

    init {
        showBackground = false
    }

    override fun setup() {
        super.setup()
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", DEFAULT_SIDE)
        tree?.getProp("staticH")?.addMetadata("default", DEFAULT_SIDE)
    }

    override fun minimumSize(): Pair<Float, Float> = 2f to 2f

    override fun hasBackground(): Boolean = false

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun update(): Boolean = false

    override fun remove() {
        releaseImage()
    }

    override fun clone(): Hud = (super.clone() as CustomImageHud).apply {
        _staticW = mutableStateOf(this@CustomImageHud.staticW)
        _staticH = mutableStateOf(this@CustomImageHud.staticH)
        cachedImage = null
        cachedPath = null
        cachedStamp = 0L
    }

    @Composable
    override fun Content() {
        val sizeModifier = PolyModifier.size(scaledWidth, scaledHeight)
        PolyCanvas(sizeModifier) { x, y, w, h -> draw(x, y, w, h) }
    }

    private fun RenderContext.draw(x: Float, y: Float, width: Float, height: Float) {
        val image = image()
        if (image == null) {
            drawPlaceholder(x, y, width, height)
            return
        }

        val srcW = image.width.toFloat()
        val srcH = image.height.toFloat()
        if (srcW <= 0f || srcH <= 0f) return

        val dst = if (keepAspect) {
            val scale = min(width / srcW, height / srcH)
            val w = srcW * scale
            val h = srcH * scale
            Rect.makeXYWH(x + (width - w) / 2f, y + (height - h) / 2f, w, h)
        } else {
            Rect.makeXYWH(x, y, width, height)
        }

        paint.alpha = (opacity / 100f * 255f).toInt().coerceIn(0, 255)
        val sampling: SamplingMode = if (smooth) SamplingMode.LINEAR else NEAREST

        val radius = cornerRadius
        val rounded = radius > 0f
        if (rounded) {
            save()
            clipRRect(dst.left, dst.top, dst.width, dst.height, radius)
        }
        canvas.drawImageRect(image, Rect.makeWH(srcW, srcH), dst, sampling, paint, true)
        if (rounded) restore()
    }

    private fun RenderContext.drawPlaceholder(x: Float, y: Float, width: Float, height: Float) {
        if (isReal && !HudManager.isEditing) return
        rect(x, y, width, height, PLACEHOLDER_COLOR, cornerRadius)
        rectStroke(x, y, width, height, PLACEHOLDER_OUTLINE, 1f, cornerRadius)
    }

    private fun image(): Image? {
        val path = imagePath.trim()
        if (path.isEmpty()) {
            releaseImage()
            cachedPath = null
            return null
        }

        val file = java.io.File(path)
        val stamp = if (file.isFile) file.lastModified() else -1L
        if (path == cachedPath && stamp == cachedStamp) return cachedImage

        releaseImage()
        cachedPath = path
        cachedStamp = stamp
        if (stamp < 0L || file.length() > MAX_FILE_BYTES) return null

        cachedImage = runCatching { Image.makeFromEncoded(file.readBytes()) }.getOrNull()
        return cachedImage
    }

    private fun releaseImage() {
        cachedImage?.close()
        cachedImage = null
    }
}
