@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
//? if > 1.8.9 {
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
//? if < 1.21.11
//import net.minecraft.resources.ResourceLocation
//? if >= 1.21.11
import net.minecraft.resources.Identifier as ResourceLocation
//?} else
//import net.minecraft.resource.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.margin
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.PolyFont
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hooks.EnderChestTracker
//? if > 1.8.9 {
import org.polyfrost.evergreenhud.client.hooks.isShulkerBox
//?}
import org.polyfrost.evergreenhud.client.hud.item.ANY_POTION
import org.polyfrost.evergreenhud.client.hud.item.ANY_POTION_LABEL
import org.polyfrost.evergreenhud.client.hud.item.ITEM_SIZE
import org.polyfrost.evergreenhud.client.hud.item.ItemIcon
import org.polyfrost.evergreenhud.client.hud.item.hasPotionContents
import org.polyfrost.evergreenhud.client.hud.item.potionIdOf
import org.polyfrost.evergreenhud.client.hud.item.potionStack
import org.polyfrost.evergreenhud.client.hud.item.potionVariant
import org.polyfrost.evergreenhud.client.hud.item.potionVariants
import org.polyfrost.evergreenhud.client.hud.item.whenItemsReady
import org.polyfrost.evergreenhud.client.utils.applyCase
import org.polyfrost.evergreenhud.client.utils.capHeight
import org.polyfrost.oneconfig.api.config.v1.annotations.Button
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.ItemList
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.utils.v1.dsl.mc

private const val TEXT_GAP = 2f
private const val FONT_SIZE = 8f

private const val MC_INK_HEIGHT = 7f

private const val BASE_PADDING = 1f

class ItemCounterHud : Hud(
    id = "item_counter.json",
    title = "Item Counter",
    category = Category.PLAYER,
) {
    private companion object {
        private const val TOTAL = 0

        private const val EXAMPLE_COUNT = 137
    }

    @ItemList(
        title = "Item",
        description = "The item to count.",
        maxEntries = 1,
        reorderable = false,
        addText = "Select Item",
    )
    var item = arrayOf("minecraft:diamond")

    @Dropdown(title = "Potion", description = "Which potion variant to count.", options = [ANY_POTION_LABEL])
    var potion = ANY_POTION

    @Switch(title = "Show Icon")
    var showIcon = true

    @Switch(title = "Show Item Name")
    var showName = false

    @Dropdown(title = "Count Format", options = ["Total", "Stacks + Remainder"])
    var format = TOTAL

    @Switch(title = "Count Shulker Contents", description = "Also count matching items inside shulker boxes.")
    var countShulkers = false

    @Switch(
        title = "Count Ender Chest",
        description = "Also count matching items in your ender chest, as of the last time you opened it.",
    )
    var countEnderChest = false

    @Switch(title = "Hide When Empty", description = "Hide the HUD while you carry none of the item.")
    var hideWhenEmpty = false

    private var resolvedQuery: String? = null
    private var resolved: Item? = null

    private class Display(val stack: ItemStack, val text: String, val showIcon: Boolean)

    private var cachedItem: Item? = null
    private var cachedPotion: String? = null
    private var cachedStack: ItemStack? = null

    private var display = mutableStateOf<Display?>(null)

    private var tickHandler: EventHandler<*>? = null

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    @Button(title = "Use Held Item", description = "Count whatever you are currently holding.")
    fun useHeldItem() {
        val held = mc.player?.mainHandItem?.takeIf { !it.isEmpty } ?: return
        //~ if = 1.8.9 'BuiltInRegistries.ITEM' -> 'Item.REGISTRY'
        item = arrayOf(BuiltInRegistries.ITEM.getKey(held.item).toString())
        potion = potionIdOf(held)?.takeIf { potionVariant(it) != null } ?: ANY_POTION
    }

    override fun setup() {
        super.setup()
        if (!isReal) return

        applyPotionOptions()
        updateWhenChanged("item")
        updateWhenChanged("potion")
        updateWhenChanged("showIcon")
        hideIf("potion") { currentItem()?.hasPotionContents != true }
        tickHandler = eventHandler { _: TickEvent.End -> if (!hidden || HudManager.isEditing) updateAndRecalculate() }
    }

    override fun updateFrequency(): Long = if (isReal) Long.MAX_VALUE else -1L

    override fun remove() {
        tickHandler?.unregister()
        tickHandler = null
    }

    private fun applyPotionOptions() {
        val variants = potionVariants()
        if (variants.isEmpty()) return
        val property = tree?.getProp("potion") ?: return
        property.addMetadata("options", listOf(ANY_POTION_LABEL) + variants.map { it.label })
        property.addMetadata("optionValues", listOf(ANY_POTION) + variants.map { it.id })
        property.removeMetadata("optionsKey")
    }

    override fun update(): Boolean {
        val next = whenItemsReady(null) { recompute() }
        val current = display.value
        if (next == null && current == null) return false
        if (next != null && current != null &&
            next.text == current.text && next.showIcon == current.showIcon &&
            ItemStack.matches(next.stack, current.stack)
        ) return false
        display.value = next
        return true
    }

    private fun currentItem(): Item? {
        val query = item.firstOrNull()
        if (query != resolvedQuery) {
            resolvedQuery = query
            resolved = query?.let(::resolve)
        }
        return resolved
    }

    private fun resolve(query: String): Item? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null

        //? if > 1.8.9 {
        val id = ResourceLocation.tryParse(
            if (trimmed.contains(':')) trimmed.lowercase() else "minecraft:${trimmed.lowercase().replace(' ', '_')}"
        ) ?: return null
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null)?.takeIf { it != Items.AIR }
        //?} else {
        /*val id = Identifier(
            if (trimmed.contains(':')) trimmed.lowercase() else "minecraft:${trimmed.lowercase().replace(' ', '_')}"
        )
        return Item.REGISTRY[id]
        *///?}
    }

    private fun count(target: Item): Int {
        if (!isReal) return EXAMPLE_COUNT
        val inventory = mc.player?.inventory ?: return 0
        var count = 0
        for (i in 0 until inventory.containerSize) count += countIn(inventory.getItem(i), target)
        if (countEnderChest) EnderChestTracker.contents()?.forEach { count += countIn(it, target) }
        return count
    }

    //~ if = 1.8.9 'slot: ItemStack' -> 'slot: ItemStack?'
    private fun countIn(slot: ItemStack, target: Item): Int {
        //~ if = 1.8.9 'slot.isEmpty' -> 'slot == null || slot.isEmpty'
        if (slot.isEmpty) return 0
        var count = if (matches(slot, target)) slot.count else 0
        //? if > 1.8.9 {
        if (countShulkers && slot.isShulkerBox) {
            val inner = slot.get(DataComponents.CONTAINER)?.nonEmptyItems() ?: return count
            //? if >= 26 {
            val anyPotion = potionFilter(target) == null
            for (template in inner) {
                if (template.item().value() == target && (anyPotion || matches(template.create(), target))) count += template.count()
            }
            //?} else {
            /*for (stack in inner) {
                if (matches(stack, target)) count += stack.count
            }
            *///?}
        }
        //?}
        return count
    }

    private fun matches(stack: ItemStack, target: Item): Boolean {
        if (stack.item != target) return false
        val wanted = potionFilter(target) ?: return true
        return potionIdOf(stack) == wanted
    }

    private fun potionFilter(target: Item): String? =
        potion.takeIf { target.hasPotionContents && potionVariant(it) != null }

    private fun countText(count: Int, stack: ItemStack): String {
        if (format == TOTAL) return count.toString()
        val stackSize = stack.maxStackSize
        if (stackSize <= 1 || count < stackSize) return count.toString()
        val stacks = count / stackSize
        val remainder = count % stackSize
        return if (remainder == 0) "${stacks}x$stackSize" else "${stacks}x$stackSize + $remainder"
    }

    private fun recompute(): Display? {
        val editing = !isReal || HudManager.isEditing
        val target = currentItem() ?: Items.DIAMOND.takeIf { editing } ?: return null

        val count = count(target)
        if (!editing && hideWhenEmpty && count <= 0) return null

        val variant = potionFilter(target)?.let(::potionVariant)
        val stack = displayStack(target, variant?.id)
        val text = buildString {
            if (showName) append(stack.hoverName.string).append(variant?.suffix.orEmpty()).append(": ")
            append(countText(count, stack))
        }
        return Display(stack, text, showIcon)
    }

    private fun displayStack(target: Item, potionId: String?): ItemStack {
        cachedStack?.let { if (target == cachedItem && potionId == cachedPotion) return it }
        val next = if (potionId == null) ItemStack(target) else potionStack(target, potionId)
        cachedItem = target
        cachedPotion = potionId
        cachedStack = next
        return next
    }

    @Composable
    override fun Content() {
        val current = display.value ?: return
        val scale = textScale.coerceAtLeast(0.01f)
        val basePaddingRight = if (current.showIcon) TEXT_GAP else BASE_PADDING
        val modifier = hudBackground().padding(
            padLeft + BASE_PADDING * scale,
            padTop + BASE_PADDING * scale,
            padRight + basePaddingRight * scale,
            padBottom + BASE_PADDING * scale,
        )

        val iconSize = if (current.showIcon) ITEM_SIZE * scale else 0f

        PolyBox(modifier = modifier) {
            PolyRow(gap = if (current.showIcon) TEXT_GAP * scale else 0f) {
                if (current.showIcon) {
                    ItemIcon(
                        current.stack,
                        size = iconSize,
                        modifier = PolyModifier.align(PolyAlign.Center),
                    )
                }
                Label(current.text, scale, iconSize)
            }
        }
    }

    @Composable
    private fun Label(raw: String, scale: Float, iconSize: Float) {
        val text = applyCase(raw)
        val color = textPolyColor
        if (font == Font.Minecraft) {
            val modifier = topAlign(inkOffset(iconSize, 0f, MC_INK_HEIGHT * scale))
            PolyMcText(text, color = color, shadow = showShadow, scale = scale, modifier = modifier)
        } else {
            val polyFont = PolyFont(getPoppinsFontName(), FONT_SIZE * scale)
            val inkTop = -polyFont.metrics.ascent - polyFont.capHeight
            val modifier = topAlign(inkOffset(iconSize, inkTop, polyFont.capHeight))
            PolyText(
                text,
                color = color,
                fontSize = FONT_SIZE * scale,
                shadow = showShadow,
                shadowColor = shadowPolyColor,
                shadowOffset = shadowOffsetX,
                font = getPoppinsFontName(),
                modifier = modifier,
            )
        }
    }

    private fun topAlign(offset: Float): PolyModifier =
        PolyModifier.align(PolyAlign.Top).margin(top = offset)

    private fun inkOffset(rowHeight: Float, inkTop: Float, inkHeight: Float): Float =
        ((rowHeight - inkHeight) / 2f - inkTop).coerceAtLeast(0f)

    override fun onCloned() {
        display = mutableStateOf(null)
        tickHandler = null
        cachedStack = null
    }
}
