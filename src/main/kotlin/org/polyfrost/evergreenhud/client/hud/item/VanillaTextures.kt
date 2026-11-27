package org.polyfrost.evergreenhud.client.hud.item

//? if = 1.8.9 {
/*import net.ornithemc.osl.core.api.util.NamespacedIdentifier as ResourceLocation
import net.ornithemc.osl.core.api.util.NamespacedIdentifiers
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager
*///?} elif < 1.21.11
//import net.minecraft.resources.ResourceLocation
//? if >= 1.21.11
import net.minecraft.resources.Identifier as ResourceLocation
import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.ImageLoader
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.RenderContext
import org.polyfrost.compose.render.snappedRect
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.ResourceFinishedLoading
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

const val HOTBAR_SLOT = 20f
const val HOTBAR_BORDER = 1f
const val HOTBAR_ITEM_INSET = 3f
data class HotbarSeams(
    val top: Int,
    val bottom: Int,
    val left: Int,
    val right: Int,
    val cutTop: Boolean = false,
    val cutBottom: Boolean = false,
    val cutLeft: Boolean = false,
    val cutRight: Boolean = false,
) {
    companion object {
        val NONE = HotbarSeams(0, 0, 0, 0)
    }
}

private const val HOTBAR_LAST_PIXEL = 21
private const val HOTBAR_SOURCE_SEAM = 11
private const val HOTBAR_SOURCE_GAP = 160

private val HOTBAR_COLUMN_SEGMENTS = listOf(0 to 1, 1 to 11, 11 to 21, 21 to 22)

private fun hotbarColumn(pieceColumn: Int): Int =
    if (pieceColumn < HOTBAR_SOURCE_SEAM) pieceColumn else pieceColumn + HOTBAR_SOURCE_GAP

private inline fun forEachRun(mask: Int, from: Int, to: Int, run: (start: Int, end: Int, open: Boolean) -> Unit) {
    var start = from
    while (start < to) {
        val open = mask shr start and 1 == 1
        var end = start + 1
        while (end < to && (mask shr end and 1 == 1) == open) end++
        run(start, end, open)
        start = end
    }
}

const val HOTBAR_THICKNESS = HOTBAR_BORDER * 2f + HOTBAR_SLOT

fun hotbarStripLength(slots: Int): Float = HOTBAR_BORDER * 2f + slots * HOTBAR_SLOT

private const val HOTBAR_TEXTURE_WIDTH = 182f
private const val HOTBAR_TEXTURE_HEIGHT = 22f

const val CONTAINER_TITLED_TOP = 17f

const val CONTAINER_PLAIN_TOP = 7f

const val CONTAINER_BOTTOM = 7f

const val CONTAINER_SLOT = 18f

const val CONTAINER_ITEM_INSET = 1f

const val CONTAINER_WIDTH = 176f

private const val CONTAINER_TEXTURE_SIZE = 256f

private const val GENERIC_54_GUI_HEIGHT = 222f
private const val SHULKER_BOX_GUI_HEIGHT = 166f

class VanillaTexture internal constructor(
    private val image: PolyBitmap,
    private val baseWidth: Float,
    private val baseHeight: Float,
    private val guiHeight: Float,
) {
    fun blit(
        ctx: RenderContext,
        srcX: Float,
        srcY: Float,
        srcW: Float,
        srcH: Float,
        x: Float,
        y: Float,
        scale: Float,
    ) {
        val dst = ctx.snappedRect(x, y, srcW * scale, srcH * scale)
        ctx.image(
            image,
            dst[0], dst[1], dst[2], dst[3],
            srcX / baseWidth, srcY / baseHeight, (srcX + srcW) / baseWidth, (srcY + srcH) / baseHeight,
            PolyColor.WHITE,
            FilterQuality.None,
        )
    }

    fun drawHotbarSlot(ctx: RenderContext, x: Float, y: Float, scale: Float, seams: HotbarSeams) {
        blit(ctx, 1f, 1f, 10f, 20f, x + scale, y + scale, scale)
        blit(ctx, 171f, 1f, 10f, 20f, x + 11f * scale, y + scale, scale)
        val firstColumn = if (seams.cutLeft) 1 else 0
        val endColumn = if (seams.cutRight) HOTBAR_LAST_PIXEL else HOTBAR_LAST_PIXEL + 1
        for ((segmentFrom, segmentTo) in HOTBAR_COLUMN_SEGMENTS) {
            val from = maxOf(segmentFrom, firstColumn)
            val to = minOf(segmentTo, endColumn)
            if (from >= to) continue
            if (!seams.cutTop) {
                forEachRun(seams.top, from, to) { start, end, open ->
                    val srcX = hotbarColumn(start).let { if (open) it.coerceIn(1, 180) else it }
                    blit(ctx, srcX.toFloat(), if (open) 1f else 0f, (end - start).toFloat(), 1f, x + start * scale, y, scale)
                }
            }
            if (!seams.cutBottom) {
                forEachRun(seams.bottom, from, to) { start, end, open ->
                    val srcX = hotbarColumn(start).let { if (open) it.coerceIn(1, 180) else it }
                    val srcY = if (open) 20f else 21f
                    blit(ctx, srcX.toFloat(), srcY, (end - start).toFloat(), 1f, x + start * scale, y + HOTBAR_LAST_PIXEL * scale, scale)
                }
            }
        }
        if (!seams.cutLeft) {
            forEachRun(seams.left, 1, HOTBAR_LAST_PIXEL) { start, end, open ->
                blit(ctx, if (open) 1f else 0f, start.toFloat(), 1f, (end - start).toFloat(), x, y + start * scale, scale)
            }
        }
        if (!seams.cutRight) {
            forEachRun(seams.right, 1, HOTBAR_LAST_PIXEL) { start, end, open ->
                val srcX = if (open) 180f else 181f
                blit(ctx, srcX, start.toFloat(), 1f, (end - start).toFloat(), x + HOTBAR_LAST_PIXEL * scale, y + start * scale, scale)
            }
        }
    }

    fun drawContainer(ctx: RenderContext, x: Float, y: Float, rows: Int, titled: Boolean, scale: Float) {
        if (rows <= 0) return
        val top = containerTop(titled)
        val slots = rows * CONTAINER_SLOT
        blit(ctx, 0f, 0f, CONTAINER_WIDTH, top, x, y, scale)
        blit(ctx, 0f, CONTAINER_TITLED_TOP, CONTAINER_WIDTH, slots, x, y + top * scale, scale)
        blit(
            ctx,
            0f,
            guiHeight - CONTAINER_BOTTOM,
            CONTAINER_WIDTH,
            CONTAINER_BOTTOM,
            x,
            y + (top + slots) * scale,
            scale,
        )
    }
}

fun containerTop(titled: Boolean): Float = if (titled) CONTAINER_TITLED_TOP else CONTAINER_PLAIN_TOP

fun containerHeight(rows: Int, titled: Boolean): Float =
    containerTop(titled) + rows * CONTAINER_SLOT + CONTAINER_BOTTOM

object VanillaTextures {
    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Vanilla Textures")

    //? if > 1.8.9 {
    private val HOTBAR = ResourceLocation.withDefaultNamespace("textures/gui/sprites/hud/hotbar.png")
    private val GENERIC_54 = ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png")
    private val SHULKER_BOX = ResourceLocation.withDefaultNamespace("textures/gui/container/shulker_box.png")
    //?} else {
    /*private val HOTBAR = NamespacedIdentifiers.from("minecraft", "textures/gui/widgets.png")
    private val GENERIC_54 = NamespacedIdentifiers.from("minecraft", "textures/gui/container/generic_54.png")
    *///?}

    private val cache = ConcurrentHashMap<ResourceLocation, VanillaTexture>()
    private val missing = ConcurrentHashMap.newKeySet<ResourceLocation>()

    fun initialize() {
        eventHandler { _: ResourceFinishedLoading ->
            cache.clear()
            missing.clear()
        }
    }

    fun hotbar(): VanillaTexture? =
        //? if > 1.8.9 {
        load(HOTBAR, HOTBAR_TEXTURE_WIDTH, HOTBAR_TEXTURE_HEIGHT, HOTBAR_TEXTURE_HEIGHT)
        //?} else
        //load(HOTBAR, CONTAINER_TEXTURE_SIZE, CONTAINER_TEXTURE_SIZE, HOTBAR_TEXTURE_HEIGHT)

    //? if > 1.8.9 {
    fun container(shulker: Boolean): VanillaTexture? = if (shulker) {
        load(SHULKER_BOX, CONTAINER_TEXTURE_SIZE, CONTAINER_TEXTURE_SIZE, SHULKER_BOX_GUI_HEIGHT)
    } else {
        load(GENERIC_54, CONTAINER_TEXTURE_SIZE, CONTAINER_TEXTURE_SIZE, GENERIC_54_GUI_HEIGHT)
    }
    //?} else {
    /*fun container(shulker: Boolean): VanillaTexture? =
        load(GENERIC_54, CONTAINER_TEXTURE_SIZE, CONTAINER_TEXTURE_SIZE, GENERIC_54_GUI_HEIGHT)
    *///?}

    private fun load(id: ResourceLocation, baseWidth: Float, baseHeight: Float, guiHeight: Float): VanillaTexture? {
        cache[id]?.let { return it }
        if (id in missing) return null
        val image = try {
            //~ if = 1.8.9 'mc.resourceManager' -> 'ResourceManager.client()'
            mc.resourceManager.getResource(id).orElse(null)
                ?.open()?.use { ImageLoader.fromBytes(it.readAllBytes()) }
        } catch (e: Exception) {
            LOGGER.warn("Failed to load the vanilla texture {}", id, e)
            null
        }
        if (image == null) {
            missing.add(id)
            return null
        }
        val texture = VanillaTexture(image, baseWidth, baseHeight, guiHeight)
        cache[id] = texture
        return texture
    }
}
