package org.polyfrost.evergreenhud.client.hud.item

import androidx.compose.runtime.Composable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import org.jetbrains.skia.Paint
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.absoluteAt
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hooks.HudOffscreen
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.internal.ui.components.item.ItemCatalog
import org.polyfrost.oneconfig.internal.ui.components.item.itemImage
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

const val ITEM_SIZE = 16f

private const val OVERSIZE_FACTOR = 0.5f

private const val BAR_X = 2f
private const val BAR_Y = 13f
private const val BAR_WIDTH = 13f

private val BAR_BACKGROUND = PolyColor(0xFF000000.toInt())
private val COOLDOWN_OVERLAY = PolyColor(0x7FFFFFFF)

private val itemPaint = Paint()

private val requestedIcons: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

@Composable
fun ItemIcon(
    hud: Hud,
    stack: ItemStack,
    size: Float = ITEM_SIZE,
    decorations: Boolean = true,
    countOverride: String? = null,
    modifier: PolyModifier = PolyModifier,
) {
    val scale = size / ITEM_SIZE
    PolyBox(modifier = modifier.size(size, size)) {
        ItemImage(hud, stack, size, decorations, countOverride)
        if (!decorations || offscreen(hud)) return@PolyBox

        if (stack.isDamageableItem && stack.damageValue > 0) DurabilityBar(stack, scale)
        //? if >= 1.21.8
        if (hud.isReal) CooldownOverlay(stack, scale)

        val count = countOverride ?: stack.count.takeIf { it > 1 }?.toString()
        if (count != null) {
            PolyMcText(
                count,
                scale = scale,
                modifier = PolyModifier.align(PolyAlign.BottomRight),
            )
        }
        //? if < 1.21.8
        //if (hud.isReal) CooldownOverlay(stack, scale)
    }
}

private fun offscreen(hud: Hud): Boolean = hud.isReal && HudOffscreen.isUsable

class ItemGridSlot(val x: Float, val y: Float, val stack: ItemStack)

@Composable
fun ItemGrid(
    hud: Hud,
    slots: List<ItemGridSlot>,
    width: Float,
    height: Float,
    size: Float = ITEM_SIZE,
    decorations: Boolean = true,
) {
    if (slots.isEmpty()) return
    val scale = size / ITEM_SIZE
    val offscreen = offscreen(hud)

    PolyCanvas(PolyModifier.absoluteAt(0f, 0f).size(width, height)) { x, y, _, _ ->
        if (offscreen) {
            val hudScale = hud.effectiveScale
            for (slot in slots) {
                HudOffscreen.submitItem(
                    slot.stack,
                    hud.x + (x + slot.x) * hudScale,
                    hud.y + (y + slot.y) * hudScale,
                    scale * hudScale,
                    decorations,
                )
            }
            if (HudOffscreen.hasContent) {
                val margin = size * OVERSIZE_FACTOR
                HudOffscreen.drawInto(
                    canvas,
                    hud.x,
                    hud.y,
                    hudScale,
                    width + margin * 2f,
                    height + margin * 2f,
                    x - margin,
                    y - margin,
                )
                return@PolyCanvas
            }
        }
        for (slot in slots) {
            val id = itemId(slot.stack)
            val icon = itemImage(id)
            if (icon != null) image(icon, x + slot.x, y + slot.y, size, size, itemPaint) else requestIcon(id)
        }
    }

    if (!decorations || offscreen) return
    for (slot in slots) {
        PolyBox(modifier = PolyModifier.absoluteAt(slot.x, slot.y).size(size, size)) {
            val stack = slot.stack
            if (stack.isDamageableItem && stack.damageValue > 0) DurabilityBar(stack, scale)
            val count = stack.count.takeIf { it > 1 }?.toString()
            if (count != null) {
                PolyMcText(
                    count,
                    scale = scale,
                    modifier = PolyModifier.align(PolyAlign.BottomRight),
                )
            }
        }
    }
}

@Composable
private fun ItemImage(hud: Hud, stack: ItemStack, size: Float, decorations: Boolean, countOverride: String?) {
    PolyCanvas(PolyModifier.size(size, size)) { x, y, w, h ->
        if (offscreen(hud)) {
            val hudScale = hud.effectiveScale
            HudOffscreen.submitItem(
                stack,
                hud.x + x * hudScale,
                hud.y + y * hudScale,
                size / ITEM_SIZE * hudScale,
                decorations,
                countOverride,
            )
            if (HudOffscreen.hasContent) {
                val margin = size * OVERSIZE_FACTOR
                HudOffscreen.drawInto(
                    canvas,
                    hud.x,
                    hud.y,
                    hudScale,
                    w + margin * 2f,
                    h + margin * 2f,
                    x - margin,
                    y - margin,
                )
                return@PolyCanvas
            }
        }
        val id = itemId(stack)
        val icon = itemImage(id)
        if (icon != null) image(icon, x, y, w, h, itemPaint) else requestIcon(id)
    }
}

private fun requestIcon(id: String) {
    if (!requestedIcons.add(id)) return
    ItemCatalog.loadIcon(id) { requestedIcons.remove(id) }
}

inline fun <T> whenItemsReady(fallback: T, block: () -> T): T = try {
    block()
} catch (throwable: Throwable) {
    fallback
}

fun itemId(stack: ItemStack): String = BuiltInRegistries.ITEM.getKey(stack.item).toString()

@Composable
private fun CooldownOverlay(stack: ItemStack, scale: Float) {
    PolyCanvas(PolyModifier.size(ITEM_SIZE * scale, ITEM_SIZE * scale)) { x, y, _, _ ->
        val height = cooldownOverlayHeight(cooldownPercent(stack))
        if (height <= 0) return@PolyCanvas

        rect(
            x,
            y + (ITEM_SIZE - height) * scale,
            ITEM_SIZE * scale,
            height * scale,
            COOLDOWN_OVERLAY,
        )
    }
}

private fun cooldownPercent(stack: ItemStack): Float {
    val player = mc.player ?: return 0f
    //? if < 1.21.4 {
    /*return player.cooldowns.getCooldownPercent(
        stack.item,
        mc.timer.getGameTimeDeltaPartialTick(true),
    )
    *///? } else {
    return player.cooldowns.getCooldownPercent(
        stack,
        mc.deltaTracker.getGameTimeDeltaPartialTick(true),
    )
    //? }
}

internal fun cooldownOverlayHeight(cooldown: Float): Int =
    ceil(ITEM_SIZE * cooldown.coerceIn(0f, 1f)).toInt()

@Composable
private fun DurabilityBar(stack: ItemStack, scale: Float) {
    val remaining = ((stack.maxDamage - stack.damageValue).toFloat() / stack.maxDamage).coerceIn(0f, 1f)
    val filled = (remaining * BAR_WIDTH).roundToInt().toFloat()
    val color = PolyColor(barColor(remaining))

    PolyCanvas(PolyModifier.size(ITEM_SIZE * scale, ITEM_SIZE * scale)) { x, y, _, _ ->
        rect(x + BAR_X * scale, y + BAR_Y * scale, BAR_WIDTH * scale, scale, BAR_BACKGROUND)
        if (filled > 0f) rect(x + BAR_X * scale, y + BAR_Y * scale, filled * scale, scale, color)
    }
}

private fun barColor(remaining: Float): Int {
    val hue = remaining / 3f
    val sector = (hue * 6f).toInt() % 6
    val f = hue * 6f - (hue * 6f).toInt()
    val q = (255 * (1f - f)).roundToInt().coerceIn(0, 255)
    val t = (255 * f).roundToInt().coerceIn(0, 255)
    val (r, g, b) = when (sector) {
        0 -> Triple(255, t, 0)
        1 -> Triple(q, 255, 0)
        2 -> Triple(0, 255, t)
        3 -> Triple(0, q, 255)
        4 -> Triple(t, 0, 255)
        else -> Triple(255, 0, q)
    }
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
}
