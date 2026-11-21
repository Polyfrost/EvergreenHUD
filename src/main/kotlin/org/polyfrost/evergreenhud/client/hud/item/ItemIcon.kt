package org.polyfrost.evergreenhud.client.hud.item

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import net.minecraft.world.item.ItemStack
import org.polyfrost.compose.composables.NodeBox
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.absoluteAt
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.minecraft.mc.NodeMcItemIcon

const val ITEM_SIZE = 16f

@Composable
fun ItemIcon(
    stack: ItemStack,
    size: Float = ITEM_SIZE,
    decorations: Boolean = true,
    countOverride: String? = null,
    modifier: PolyModifier = PolyModifier,
) = NodeMcItemIcon(stack, size, modifier, decorations, countOverride)

class ItemGridSlot(val index: Int, val x: Float, val y: Float, val stack: ItemStack)

@Composable
fun ItemGrid(
    slots: List<ItemGridSlot>,
    width: Float,
    height: Float,
    size: Float = ITEM_SIZE,
    decorations: Boolean = true,
) {
    if (slots.isEmpty()) return
    NodeBox(PolyModifier.size(width, height)) {
        for (slot in slots) {
            key(slot.index) {
                NodeMcItemIcon(
                    slot.stack,
                    size,
                    modifier = PolyModifier.absoluteAt(slot.x, slot.y),
                    decorations = decorations,
                )
            }
        }
    }
}

inline fun <T> whenItemsReady(fallback: T, block: () -> T): T = try {
    block()
} catch (throwable: Throwable) {
    fallback
}
