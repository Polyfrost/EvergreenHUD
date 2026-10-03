package org.polyfrost.evergreenhud.client.hooks

//? if > 1.8.9 {
import net.minecraft.core.NonNullList
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.ShulkerBoxBlock
import org.polyfrost.oneconfig.utils.v1.dsl.mc

const val SHULKER_SLOTS = 27

val ItemStack.isShulkerBox: Boolean
    get() = (item as? BlockItem)?.block is ShulkerBoxBlock

fun Player.heldShulkerBox(): ItemStack? =
    mainHandItem.takeIf { it.isShulkerBox } ?: offhandItem.takeIf { it.isShulkerBox }

fun ItemStack.shulkerContents(): NonNullList<ItemStack> =
    NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY).also { out ->
        get(DataComponents.CONTAINER)?.copyInto(out)
    }

object ShulkerPreview {
    class Slot(val x: Float, val y: Float, val size: Float, val stack: ItemStack)

    @JvmStatic
    var pinnedStack: ItemStack? = null
        private set

    private var slots: List<Slot> = emptyList()

    @JvmStatic
    fun togglePin() {
        pinnedStack = if (pinnedStack != null) null else mc.player?.heldShulkerBox()?.copy()
    }

    fun publishSlots(slots: List<Slot>) {
        this.slots = slots
    }

    @JvmStatic
    fun consumeHovered(mouseX: Int, mouseY: Int): ItemStack? {
        val current = slots
        slots = emptyList()
        return current.firstOrNull {
            mouseX >= it.x && mouseX < it.x + it.size && mouseY >= it.y && mouseY < it.y + it.size
        }?.stack
    }
}
//?}
