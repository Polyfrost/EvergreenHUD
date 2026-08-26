package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.absoluteAt
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hud.item.HOTBAR_ITEM_INSET
import org.polyfrost.evergreenhud.client.hud.item.HOTBAR_SLOT
import org.polyfrost.evergreenhud.client.hud.item.HOTBAR_THICKNESS
import org.polyfrost.evergreenhud.client.hud.item.ITEM_SIZE
import org.polyfrost.evergreenhud.client.hud.item.ItemIcon
import org.polyfrost.evergreenhud.client.hud.item.VanillaTextures
import org.polyfrost.evergreenhud.client.hud.item.hotbarStripLength
import org.polyfrost.evergreenhud.client.hud.item.whenItemsReady
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TEXT_GAP = 2f
private const val FONT_SIZE = 8f

private const val HORIZONTAL = 0

private const val CUSTOM = 0
private const val VANILLA = 1

private val HOTBAR_WIDTH = hotbarStripLength(9)

private const val DURABILITY = 1
private const val DURABILITY_PERCENT = 2
private const val NAME = 3

private const val LEFT = 0
private const val RIGHT = 1
private const val ABOVE = 2
private const val BELOW = 3

class ArmorHud : Hud(
    id = "armor.json",
    title = "Armor Status",
    category = Category.PLAYER,
) {
    private companion object {
        private val exampleHelmet by lazy { ItemStack(Items.DIAMOND_HELMET) }
        private val exampleChestplate by lazy { ItemStack(Items.DIAMOND_CHESTPLATE) }
        private val exampleLeggings by lazy { ItemStack(Items.DIAMOND_LEGGINGS) }
        private val exampleBoots by lazy { ItemStack(Items.DIAMOND_BOOTS) }
        private val exampleMainHand by lazy { ItemStack(Items.DIAMOND_SWORD) }
        private val exampleOffhand by lazy { ItemStack(Items.SHIELD) }
    }

    @RadioButton(
        title = "Style",
        description = "Vanilla draws the items in a hotbar, taken from your resource pack.",
        options = ["Custom", "Vanilla"],
    )
    var style = CUSTOM

    @Switch(title = "Helmet")
    var showHelmet = true

    @Switch(title = "Chestplate")
    var showChestplate = true

    @Switch(title = "Leggings")
    var showLeggings = true

    @Switch(title = "Boots")
    var showBoots = true

    @Switch(title = "Main Hand")
    var showMainHand = true

    @Switch(title = "Off Hand")
    var showOffhand = true

    @Slider(title = "Padding", min = 0F, max = 20F, step = 1F)
    var padding = 5f

    @RadioButton(title = "Direction", options = ["Horizontal", "Vertical"])
    var direction = HORIZONTAL

    @Switch(title = "Reversed")
    var reversed = false

    @Switch(title = "Item Decorations", description = "Vanilla durability bar and stack count.")
    var showDecorations = true

    @Switch(title = "Bow Arrow Count", description = "Show the number of arrows you carry on a held bow, where the stack count would be.")
    var showArrowCount = true

    @Dropdown(title = "Extra Info", options = ["None", "Durability", "Durability %", "Item Name"])
    var extraInfo = 0

    @Dropdown(title = "Text Position", options = ["Left", "Right", "Above", "Below"])
    var textPosition = RIGHT

    @Switch(title = "Dynamic Durability Color", description = "Fade the durability text between two colors as the item wears down.")
    var dynamicColor = false

    @Color(title = "Full Durability Color")
    var fullDurabilityColor = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Empty Durability Color")
    var emptyDurabilityColor = PolyColor(0xFFFF5555.toInt())

    private class Entry(val stack: ItemStack, val text: String, val textColor: Int, val count: String?)

    private var entries = mutableStateOf<List<Entry>>(emptyList())

    private var rev = mutableStateOf(0)

    override fun defaultPosition(): Pair<Float, Float> {
        val (width, height) = defaultSize()
        val hotbarLeft = (HudManager.guiScreenWidth - HOTBAR_WIDTH) / 2f
        val x = if (mainHandOnRight()) hotbarLeft + HOTBAR_WIDTH else hotbarLeft - width
        return x to HudManager.guiScreenHeight - height
    }

    private fun mainHandOnRight(): Boolean = mc.options?.mainHand()?.get() != HumanoidArm.LEFT

    private fun defaultSize(): Pair<Float, Float> {
        val scale = textScale.coerceAtLeast(0.01f)
        val slots = enabledSlots()
        val (along, across) = if (style == VANILLA) {
            hotbarStripLength(slots) to HOTBAR_THICKNESS
        } else {
            (slots * ITEM_SIZE + (slots - 1).coerceAtLeast(0) * padding) to ITEM_SIZE
        }
        val (width, height) = if (direction == HORIZONTAL) along to across else across to along
        return (width * scale + padLeft + padRight) * effectiveScale to
            (height * scale + padTop + padBottom) * effectiveScale
    }

    private fun enabledSlots(): Int =
        listOf(showHelmet, showChestplate, showLeggings, showBoots, showMainHand, showOffhand).count { it }

    override fun hasBackground(): Boolean = style != VANILLA

    override fun canMergeBackground(): Boolean = true

    override val alwaysRedraw: Boolean
        get() = super.alwaysRedraw || (isReal && entries.value.isNotEmpty())

    override fun setup() {
        if (isReal) {
            hideIf("fullDurabilityColor") { !dynamicColor }
            hideIf("emptyDurabilityColor") { !dynamicColor }
            // the hotbar spaces its own slots
            hideIf("padding") { style == VANILLA }
            for (option in listOf("style", "padding", "direction", "textPosition", "showDecorations")) {
                addCallback(option) { rev.value++ }
            }
        }
    }

    override fun update(): Boolean {
        val next = buildEntries()
        if (next.size == entries.value.size && next.zip(entries.value).all { (a, b) -> a.sameAs(b) }) return false
        entries.value = next
        return true
    }

    private fun Entry.sameAs(other: Entry): Boolean =
        text == other.text && textColor == other.textColor && count == other.count &&
            ItemStack.matches(stack, other.stack)

    private fun buildEntries(): List<Entry> = whenItemsReady(emptyList()) { currentItems() }.map { stack ->
        val text = infoText(stack)
        val color = if (dynamicColor && stack.isDamageableItem &&
            (extraInfo == DURABILITY || extraInfo == DURABILITY_PERCENT)
        ) {
            durabilityColor(durabilityPercent(stack))
        } else {
            textColor
        }
        val count = if (showArrowCount && (stack.item == Items.BOW || stack.item == Items.CROSSBOW)) {
            arrowCount().toString()
        } else {
            null
        }
        Entry(stack, text, color, count)
    }

    private fun currentItems(): List<ItemStack> {
        var list = if (isReal) equippedItems() else exampleItems()
        if (list.isEmpty() && HudManager.isEditing) list = exampleItems()
        if (reversed) list.reverse()
        return list
    }

    private fun exampleItems(): ArrayList<ItemStack> {
        val list = ArrayList<ItemStack>(6)
        if (showHelmet) list.add(exampleHelmet)
        if (showChestplate) list.add(exampleChestplate)
        if (showLeggings) list.add(exampleLeggings)
        if (showBoots) list.add(exampleBoots)
        if (showMainHand) list.add(exampleMainHand)
        if (showOffhand) list.add(exampleOffhand)
        return list
    }

    private fun equippedItems(): ArrayList<ItemStack> {
        val list = ArrayList<ItemStack>(6)
        val player = mc.player ?: return list
        fun add(slot: EquipmentSlot) = player.getItemBySlot(slot).let { if (!it.isEmpty) list.add(it) }
        if (showHelmet) add(EquipmentSlot.HEAD)
        if (showChestplate) add(EquipmentSlot.CHEST)
        if (showLeggings) add(EquipmentSlot.LEGS)
        if (showBoots) add(EquipmentSlot.FEET)
        if (showMainHand) add(EquipmentSlot.MAINHAND)
        if (showOffhand) add(EquipmentSlot.OFFHAND)
        return list
    }

    private fun infoText(stack: ItemStack): String = when (extraInfo) {
        DURABILITY -> if (stack.isDamageableItem) (stack.maxDamage - stack.damageValue).toString() else ""
        DURABILITY_PERCENT -> if (stack.isDamageableItem) "${durabilityPercent(stack)}%" else ""
        NAME -> stack.hoverName.string
        else -> ""
    }

    private fun durabilityPercent(stack: ItemStack): Int =
        ceil((stack.maxDamage - stack.damageValue).toFloat() / stack.maxDamage.toFloat() * 100f).toInt()

    private fun arrowCount(): Int {
        if (!isReal) return 64
        val player = mc.player ?: return 0
        val inv = player.inventory
        var count = 0
        for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (s.item == Items.ARROW || s.item == Items.SPECTRAL_ARROW || s.item == Items.TIPPED_ARROW) count += s.count
        }
        return count
    }

    private fun durabilityColor(percent: Int): Int {
        val p = percent.coerceIn(0, 100) / 100f
        val empty = emptyDurabilityColor.argb
        val full = fullDurabilityColor.argb
        fun lerp(shift: Int): Int {
            val from = (empty shr shift) and 0xFF
            val to = (full shr shift) and 0xFF
            return (from + (to - from) * p).roundToInt().coerceIn(0, 255) shl shift
        }
        return lerp(24) or lerp(16) or lerp(8) or lerp(0)
    }

    @Composable
    override fun Content() {
        rev.value
        val list = entries.value
        if (list.isEmpty()) return
        val scale = textScale.coerceAtLeast(0.01f)
        if (style == VANILLA) {
            VanillaContent(list, scale)
            return
        }
        val modifier = hudBackground().padding(padLeft, padTop, padRight, padBottom)

        PolyBox(modifier = modifier) {
            if (direction == HORIZONTAL) {
                PolyRow(gap = padding * scale) {
                    for (entry in list) Entry(entry, scale, PolyAlign.Center)
                }
            } else {
                val rowAlign = when (textPosition) {
                    RIGHT -> PolyAlign.Left
                    LEFT -> PolyAlign.Right
                    else -> PolyAlign.Center
                }
                PolyColumn(gap = padding * scale) {
                    for (entry in list) Entry(entry, scale, rowAlign)
                }
            }
        }
    }

    /**
     * The items laid out as a vanilla hotbar. Extra info would break the strip in half if it sat
     * between two slots, so it goes outside it instead: above or below a hotbar running across, and
     * beside one running down.
     */
    @Composable
    private fun VanillaContent(list: List<Entry>, scale: Float) {
        val horizontal = direction == HORIZONTAL
        val length = hotbarStripLength(list.size) * scale
        val thickness = HOTBAR_THICKNESS * scale
        val stripWidth = if (horizontal) length else thickness
        val stripHeight = if (horizontal) thickness else length

        val labels = list.map { if (it.text.isEmpty()) 0f else textWidth(it.text, scale) }
        val labelled = labels.any { it > 0f }
        val labelWidth = labels.max()
        val labelHeight = if (labelled) textHeight(scale) else 0f
        val textFirst = textPosition == LEFT || textPosition == ABOVE
        val gap = if (labelled) TEXT_GAP * scale else 0f

        fun slotCenter(index: Int) = (HOTBAR_ITEM_INSET + ITEM_SIZE / 2f + index * HOTBAR_SLOT) * scale

        var before = 0f
        var after = 0f
        if (horizontal) {
            for ((index, label) in labels.withIndex()) {
                if (label <= 0f) continue
                before = maxOf(before, label / 2f - slotCenter(index))
                after = maxOf(after, slotCenter(index) + label / 2f - stripWidth)
            }
        }

        val stripX = when {
            horizontal -> before
            textFirst -> labelWidth + gap
            else -> 0f
        }
        val stripY = if (horizontal && textFirst) labelHeight + gap else 0f
        val width = if (horizontal) before + stripWidth + after else stripWidth + gap + labelWidth
        val height = if (horizontal) stripHeight + gap + labelHeight else stripHeight

        PolyBox(modifier = PolyModifier.size(width, height)) {
            PolyCanvas(PolyModifier.absoluteAt(stripX, stripY).size(stripWidth, stripHeight)) { x, y, _, _ ->
                VanillaTextures.hotbar()?.drawHotbar(this, x, y, list.size, !horizontal, scale)
            }

            for ((index, entry) in list.withIndex()) {
                val along = (HOTBAR_ITEM_INSET + index * HOTBAR_SLOT) * scale
                val across = HOTBAR_ITEM_INSET * scale
                ItemIcon(
                    this@ArmorHud,
                    entry.stack,
                    size = ITEM_SIZE * scale,
                    decorations = showDecorations,
                    countOverride = entry.count,
                    modifier = PolyModifier.absoluteAt(
                        stripX + if (horizontal) along else across,
                        stripY + if (horizontal) across else along,
                    ),
                )

                val label = labels[index]
                if (label <= 0f) continue
                val infoX = when {
                    horizontal -> stripX + slotCenter(index) - label / 2f
                    textFirst -> labelWidth - label
                    else -> stripX + stripWidth + gap
                }
                val infoY = when {
                    !horizontal -> stripY + slotCenter(index) - labelHeight / 2f
                    textFirst -> 0f
                    else -> stripY + stripHeight + gap
                }
                Info(entry, scale, PolyModifier.absoluteAt(infoX, infoY))
            }
        }
    }

    private fun textWidth(text: String, scale: Float): Float = if (font == Font.Minecraft) {
        McFontQueue.measureTextWidth(text, scale)
    } else {
        FontManager.getFont(FONT_SIZE * scale, getPoppinsFontName()).measureTextWidth(text)
    }

    private fun textHeight(scale: Float): Float = if (font == Font.Minecraft) {
        McFontQueue.measureTextHeight(scale)
    } else {
        FontManager.getFont(FONT_SIZE * scale, getPoppinsFontName()).metrics.let { it.descent - it.ascent }
    }

    @Composable
    private fun Entry(entry: Entry, scale: Float, align: PolyAlign) {
        val gap = if (entry.text.isEmpty()) 0f else TEXT_GAP * scale
        val modifier = PolyModifier.align(align)
        val textFirst = textPosition == LEFT || textPosition == ABOVE
        val stacked = textPosition == ABOVE || textPosition == BELOW

        val body: @Composable () -> Unit = {
            if (textFirst && entry.text.isNotEmpty()) Info(entry, scale)
            ItemIcon(
                this@ArmorHud,
                entry.stack,
                size = ITEM_SIZE * scale,
                decorations = showDecorations,
                countOverride = entry.count,
                modifier = PolyModifier.align(PolyAlign.Center),
            )
            if (!textFirst && entry.text.isNotEmpty()) Info(entry, scale)
        }

        if (stacked) {
            PolyColumn(gap = gap, modifier = modifier, content = body)
        } else {
            PolyRow(gap = gap, modifier = modifier, content = body)
        }
    }

    @Composable
    private fun Info(entry: Entry, scale: Float, modifier: PolyModifier = PolyModifier.align(PolyAlign.Center)) {
        val color = PolyColor(entry.textColor, textChroma, textChromaSpeed)
        if (font == Font.Minecraft) {
            PolyMcText(entry.text, color = color, shadow = showShadow, scale = scale, modifier = modifier)
        } else {
            PolyText(
                entry.text,
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

    override fun clone(): Hud = (super.clone() as ArmorHud).also {
        it.entries = mutableStateOf(emptyList())
        it.rev = mutableStateOf(0)
    }
}
