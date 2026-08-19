package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import net.minecraft.core.registries.BuiltInRegistries
//? if < 1.21.11
//import net.minecraft.resources.ResourceLocation
//? if >= 1.21.11
import net.minecraft.resources.Identifier as ResourceLocation
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
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hooks.EnderChestTracker
import org.polyfrost.evergreenhud.client.hooks.isShulkerBox
import org.polyfrost.evergreenhud.client.hooks.shulkerContents
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
import org.polyfrost.oneconfig.api.config.v1.annotations.Button
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.ItemList
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
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

    private class Display(val stack: ItemStack, val text: String)

    private var display = mutableStateOf<Display?>(null)

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    // the icon comes from the offscreen target, which is only filled while the HUD keeps asking for it
    override val alwaysRedraw: Boolean
        get() = super.alwaysRedraw || (isReal && showIcon && display.value != null)

    @Button(title = "Use Held Item", description = "Count whatever you are currently holding.")
    fun useHeldItem() {
        val held = mc.player?.mainHandItem?.takeIf { !it.isEmpty } ?: return
        item = arrayOf(BuiltInRegistries.ITEM.getKey(held.item).toString())
        potion = potionIdOf(held)?.takeIf { potionVariant(it) != null } ?: ANY_POTION
    }

    override fun setup() {
        super.setup()
        if (!isReal) return

        applyPotionOptions()
        updateWhenChanged("item")
        updateWhenChanged("potion")
        hideIf("potion") { currentItem()?.hasPotionContents != true }
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
            next.text == current.text && ItemStack.matches(next.stack, current.stack)
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

        val id = ResourceLocation.tryParse(
            if (trimmed.contains(':')) trimmed.lowercase() else "minecraft:${trimmed.lowercase().replace(' ', '_')}"
        ) ?: return null
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null)?.takeIf { it != Items.AIR }
    }

    private fun count(target: Item): Int {
        if (!isReal) return EXAMPLE_COUNT
        val inventory = mc.player?.inventory ?: return 0
        var count = countIn((0 until inventory.containerSize).map(inventory::getItem), target)
        if (countEnderChest) count += countIn(EnderChestTracker.contents().orEmpty(), target)
        return count
    }

    private fun countIn(stacks: Iterable<ItemStack>, target: Item): Int {
        var count = 0
        for (slot in stacks) {
            if (slot.isEmpty) continue
            if (matches(slot, target)) count += slot.count
            if (countShulkers && slot.isShulkerBox) {
                for (inner in slot.shulkerContents()) {
                    if (matches(inner, target)) count += inner.count
                }
            }
        }
        return count
    }

    private fun matches(stack: ItemStack, target: Item): Boolean {
        if (stack.item != target) return false
        val wanted = potionFilter(target) ?: return true
        return potionIdOf(stack) == wanted
    }

    private fun potionFilter(target: Item): String? =
        potion.takeIf { target.hasPotionContents && potionVariant(it) != null }

    private fun countText(count: Int, target: Item): String {
        if (format == TOTAL) return count.toString()
        val stackSize = ItemStack(target).maxStackSize
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
        val stack = if (variant == null) ItemStack(target) else potionStack(target, variant.id)
        val text = buildString {
            if (showName) append(stack.hoverName.string).append(variant?.suffix.orEmpty()).append(": ")
            append(countText(count, target))
        }
        return Display(stack, text)
    }

    @Composable
    override fun Content() {
        val current = display.value ?: return
        val scale = textScale.coerceAtLeast(0.01f)
        val basePaddingRight = if (showIcon) TEXT_GAP else BASE_PADDING
        val modifier = hudBackground().padding(
            padLeft + BASE_PADDING * scale,
            padTop + BASE_PADDING * scale,
            padRight + basePaddingRight * scale,
            padBottom + BASE_PADDING * scale,
        )

        val iconSize = if (showIcon) ITEM_SIZE * scale else 0f

        PolyBox(modifier = modifier) {
            PolyRow(gap = if (showIcon) TEXT_GAP * scale else 0f) {
                if (showIcon) {
                    ItemIcon(
                        this@ItemCounterHud,
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
    private fun Label(text: String, scale: Float, iconSize: Float) {
        val color = PolyColor(textColor, textChroma, textChromaSpeed)
        if (font == Font.Minecraft) {
            val modifier = topAlign(inkOffset(iconSize, 0f, MC_INK_HEIGHT * scale))
            PolyMcText(text, color = color, shadow = showShadow, scale = scale, modifier = modifier)
        } else {
            val metrics = FontManager.getFont(FONT_SIZE * scale, getPoppinsFontName()).metrics
            val inkTop = -metrics.ascent - metrics.capHeight
            val modifier = topAlign(inkOffset(iconSize, inkTop, metrics.capHeight))
            PolyText(
                text,
                color = color,
                fontSize = FONT_SIZE * scale,
                shadow = showShadow,
                shadowColor = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed),
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

    override fun clone(): Hud = (super.clone() as ItemCounterHud).also {
        it.item = item.copyOf()
        it.display = mutableStateOf(null)
    }
}
