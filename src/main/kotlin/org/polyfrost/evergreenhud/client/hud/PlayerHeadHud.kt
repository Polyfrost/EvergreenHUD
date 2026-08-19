package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.resources.DefaultPlayerSkin
//? if < 1.21.10
//import net.minecraft.client.resources.PlayerSkin
//? if >= 1.21.10
import net.minecraft.world.entity.player.PlayerSkin
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorSpace
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.size
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.internal.ui.PlayerHeadTextureAccess
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import java.util.UUID
import kotlin.math.min

private const val DEFAULT_SIDE = 8f
private const val MAX_SIDE = 32f

class PlayerHeadHud : Hud(
    id = "player_head.json",
    title = "Player Head",
    category = Category.PLAYER,
) {
    private var _staticW = mutableStateOf(DEFAULT_SIDE)
    override var staticW: Float
        get() = _staticW.value
        set(v) { _staticW.value = v.coerceIn(DEFAULT_SIDE, MAX_SIDE) }

    private var _staticH = mutableStateOf(DEFAULT_SIDE)
    override var staticH: Float
        get() = _staticH.value
        set(v) { _staticH.value = v.coerceIn(DEFAULT_SIDE, MAX_SIDE) }

    init {
        showBackground = false
    }

    override fun setup() {
        super.setup()
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", DEFAULT_SIDE)
        tree?.getProp("staticH")?.addMetadata("default", DEFAULT_SIDE)
    }

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun minimumSize(): Pair<Float, Float> = DEFAULT_SIDE to DEFAULT_SIDE

    override val alwaysRedraw: Boolean get() = true

    override fun update(): Boolean = false

    override fun clone(): Hud = (super.clone() as PlayerHeadHud).apply {
        _staticW = mutableStateOf(this@PlayerHeadHud.staticW)
        _staticH = mutableStateOf(this@PlayerHeadHud.staticH)
    }

    @Composable
    override fun Content() {
        PolyBox(modifier = hudBackground().size(staticW, staticH)) {
            PolyCanvas(PolyModifier.size(staticW, staticH)) { x, y, w, h ->
                val head = PlayerHeadTexture.current() ?: return@PolyCanvas
                val side = min(w, h)
                val left = x + (w - side) / 2f
                val top = y + (h - side) / 2f
                canvas.drawImageRect(head, Rect.makeXYWH(left, top, side, side), HEAD_PAINT)
            }
        }
    }
}

private val HEAD_PAINT = Paint()

private object PlayerHeadTexture {
    private val FALLBACK_UUID = UUID(0L, 0L)

    private const val FACE = 8
    private const val HEAD_U = 8
    private const val HEAD_V = 8
    private const val HAT_U = 40
    private const val HAT_V = 8

    private var cachedTexture: Any? = null
    private var image: Image? = null

    fun current(): Image? {
        val skin = mc.player?.skin ?: DefaultPlayerSkin.get(FALLBACK_UUID)
        val texture = texturePath(skin)
        if (texture == cachedTexture) return image

        image?.close()
        image = build(skin) ?: build(DefaultPlayerSkin.get(FALLBACK_UUID))
        cachedTexture = texture
        return image
    }

    private fun texturePath(skin: PlayerSkin): Any {
        //? if >= 1.21.10
        return skin.body().texturePath()
        //? if < 1.21.10
        //return skin.texture
    }

    private fun build(skin: PlayerSkin): Image? = runCatching {
        //? if >= 1.21.10
        val texture = skin.body().texturePath()
        //? if < 1.21.10
        //val texture = skin.texture

        val packed = mc.resourceManager.getResource(texture).orElse(null)
        if (packed != null) return@runCatching packed.open().use { NativeImage.read(it) }.use { headImage(it) }

        val pixels = PlayerHeadTextureAccess.readPixels(mc.textureManager.getTexture(texture))
        pixels?.let { headImage(it) }
    }.getOrNull()

    private fun headImage(skin: NativeImage): Image {
        val hasHat = skin.width >= HAT_U + FACE && skin.height >= HAT_V + FACE
        val bytes = ByteArray(FACE * FACE * 4)

        for (y in 0 until FACE) {
            for (x in 0 until FACE) {
                var color = pixel(skin, HEAD_U + x, HEAD_V + y)
                if (hasHat) color = composite(pixel(skin, HAT_U + x, HAT_V + y), color)

                val i = (y * FACE + x) * 4
                bytes[i] = (color shr 16 and 0xFF).toByte()
                bytes[i + 1] = (color shr 8 and 0xFF).toByte()
                bytes[i + 2] = (color and 0xFF).toByte()
                bytes[i + 3] = (color ushr 24).toByte()
            }
        }

        return Image.makeRaster(
            ImageInfo(ColorInfo(ColorType.RGBA_8888, ColorAlphaType.UNPREMUL, ColorSpace.sRGB), FACE, FACE),
            bytes,
            FACE * 4,
        )
    }

    private fun pixel(image: NativeImage, x: Int, y: Int): Int {
        //? if >= 1.21.4 {
        return image.getPixel(x, y)
        //?} else {
        /*val abgr = image.getPixelRGBA(x, y)
        return (abgr and 0xFF00FF00.toInt()) or ((abgr shr 16) and 0xFF) or ((abgr and 0xFF) shl 16)
        *///?}
    }

    private fun composite(src: Int, dst: Int): Int {
        val srcAlpha = src ushr 24
        if (srcAlpha == 0) return dst
        if (srcAlpha == 0xFF) return src

        val dstAlpha = dst ushr 24
        if (dstAlpha == 0) return src

        val outAlpha = srcAlpha + dstAlpha * (0xFF - srcAlpha) / 0xFF
        if (outAlpha == 0) return 0

        fun channel(shift: Int): Int {
            val s = (src shr shift) and 0xFF
            val d = (dst shr shift) and 0xFF
            return (s * srcAlpha + d * dstAlpha * (0xFF - srcAlpha) / 0xFF) / outAlpha
        }

        return (outAlpha shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
