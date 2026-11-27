@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud.armor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
//? if > 1.8.9
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.polyfrost.compose.composables.NodeBox
import org.polyfrost.compose.composables.NodeCanvas
import org.polyfrost.compose.composables.NodeColumn
import org.polyfrost.compose.composables.NodeMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.NodeRow
import org.polyfrost.compose.composables.NodeText
import org.polyfrost.compose.composables.absoluteAt
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.render.PolyFont
import org.polyfrost.evergreenhud.client.hud.item.HOTBAR_BORDER
import org.polyfrost.evergreenhud.client.hud.item.HOTBAR_ITEM_INSET
import org.polyfrost.evergreenhud.client.hud.item.HOTBAR_THICKNESS
import org.polyfrost.evergreenhud.client.hud.item.HotbarSeams
import org.polyfrost.evergreenhud.client.hud.item.ITEM_SIZE
import org.polyfrost.evergreenhud.client.hud.item.ItemIcon
import org.polyfrost.evergreenhud.client.hud.item.VanillaTextures
import org.polyfrost.evergreenhud.client.hud.item.hotbarStripLength
import org.polyfrost.evergreenhud.client.hud.item.whenItemsReady
import org.polyfrost.evergreenhud.client.utils.applyCase
import org.polyfrost.oneconfig.api.config.v1.ConfigManager
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudGroups
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private const val TEXT_GAP = 2f
private const val FONT_SIZE = 8f

private const val CUSTOM = 0
private const val VANILLA = 1

private val HOTBAR_WIDTH = hotbarStripLength(9)

private const val DURABILITY = 1
private const val DURABILITY_PERCENT = 2
private const val NAME = 3
private const val ITEM_COUNT = 4

internal const val TEXT_LEFT = 0
internal const val TEXT_RIGHT = 1
internal const val TEXT_ABOVE = 2
internal const val TEXT_BELOW = 3

private const val VERTICAL_BIT = 1
private const val REVERSED_BIT = 2

abstract class ArmorPieceHud(val piece: ArmorPiece) : Hud(
    id = "armor-${piece.key}.json",
    title = piece.title,
    category = Category.PLAYER,
) {
    @RadioButton(
        title = "Style",
        description = "Vanilla draws the items in a hotbar, taken from your resource pack.",
        options = ["Custom", "Vanilla"],
    )
    var style = CUSTOM

    @Switch(title = "Item Decorations", description = "Vanilla durability bar and stack count.")
    var showDecorations = true

    @Switch(
        title = "Bow Arrow Count",
        description = "Show the number of arrows you carry on a held bow, where the stack count would be. Hand pieces only.",
    )
    var showArrowCount = true

    @Switch(
        title = "Held Item Count",
        description = "Show how many of the held item you carry, where the stack count would be. Hand pieces only.",
    )
    var countHeld = false

    @Dropdown(title = "Extra Info", options = ["None", "Durability", "Durability %", "Item Name", "Item Count"])
    var extraInfo = 0

    @Dropdown(title = "Text Position", options = ["Left", "Right", "Above", "Below"])
    var textPosition = TEXT_RIGHT

    @Switch(title = "Dynamic Durability Color", description = "Fade the durability text between two colors as the item wears down.")
    var dynamicColor = false

    @Color(title = "Full Durability Color")
    var fullDurabilityColor = PolyColor(0xFFFFFFFF.toInt())

    @Color(title = "Empty Durability Color")
    var emptyDurabilityColor = PolyColor(0xFFFF5555.toInt())

    @Include
    var pendingLayout = -1

    @Include
    var pendingGap = ArmorLayout.DEFAULT_GAP

    @Include
    var migrationBatch = ""

    private class Entry(val stack: ItemStack, val text: String, val textColor: Int, val count: String?)

    private var entry = mutableStateOf<Entry?>(null)

    private var rev = mutableStateOf(0)

    private var updated = false

    private var appliedSeams = HotbarSeams.NONE

    //? if > 1.8.9 {
    private var countCache: Map<Item, Int>? = null
    //?} else
    //private var countCache: Map<Pair<Item, Int>, Int>? = null

    private fun armorSiblings(): List<ArmorPieceHud> {
        val current = group ?: return emptyList()
        val preset = HudGroups.presetOf(current) ?: return current.huds.filterIsInstance<ArmorPieceHud>()
        return HudGroups.presetMembers(current, preset).filterIsInstance<ArmorPieceHud>()
    }

    internal fun isVanilla(): Boolean = style == VANILLA

    internal fun nominalSize(): Pair<Float, Float> {
        val scale = textScale.coerceAtLeast(0.01f)
        if (style == VANILLA) {
            return hotbarStripLength(1) * scale * effectiveScale to HOTBAR_THICKNESS * scale * effectiveScale
        }
        val label = infoText(piece.example)
        val labelWidth = if (label.isEmpty()) 0f else textWidth(label, scale)
        val labelHeight = if (labelWidth > 0f) textHeight(scale) else 0f
        val gap = if (labelWidth > 0f) TEXT_GAP * scale else 0f
        val stacked = textPosition == TEXT_ABOVE || textPosition == TEXT_BELOW
        val width = if (stacked) maxOf(ITEM_SIZE * scale, labelWidth) else ITEM_SIZE * scale + gap + labelWidth
        val height = if (stacked) ITEM_SIZE * scale + gap + labelHeight else maxOf(ITEM_SIZE * scale, labelHeight)
        return (width + padLeft + padRight) * effectiveScale to (height + padTop + padBottom) * effectiveScale
    }

    internal fun rowStart(): Pair<Float, Float> {
        val (width, height) = nominalSize()
        val hotbarLeft = (HudManager.guiScreenWidth - HOTBAR_WIDTH) / 2f
        val x = if (mainHandOnRight()) hotbarLeft + HOTBAR_WIDTH else hotbarLeft - width
        return x to HudManager.guiScreenHeight - height
    }

    override fun defaultPosition(): Pair<Float, Float> {
        val (x, y) = rowStart()
        val offset = nominalSize().first * piece.ordinal
        return (if (mainHandOnRight()) x + offset else x - offset) to y
    }

    //? if > 1.8.9 {
    internal fun mainHandOnRight(): Boolean = mc.options?.mainHand()?.get() != HumanoidArm.LEFT
    //?} else
    //internal fun mainHandOnRight(): Boolean = true

    override fun hasBackground(): Boolean = style != VANILLA

    override fun canMergeBackground(): Boolean = true

    override fun shouldShow(): Boolean = entry.value != null

    override fun collapsesWhenNotShown(): Boolean = true

    override fun setup() {
        if (isReal) {
            hideIf("fullDurabilityColor") { !dynamicColor }
            hideIf("emptyDurabilityColor") { !dynamicColor }
            for (option in listOf("textPosition", "showDecorations")) {
                addCallback(option) { rev.value++ }
            }
            addCallback("style") { groupPieces().forEach { it.rev.value++ } }
            for (option in listOf("style", "textPosition", "showBackground", "mergeBackground")) {
                addCallback(option) { ArmorLayout.rechain(armorSiblings()) }
            }
        }
    }

    override fun updateFrequency(): Long = if (isReal) 50.milliseconds.inWholeNanoseconds else -1L

    override fun update(): Boolean {
        if (isReal) applyPendingLayout()
        val changed = refreshEntry()
        updated = true
        return changed
    }

    private fun refreshEntry(): Boolean {
        countCache = null
        val next = buildEntry()
        val current = entry.value
        if (next == null || current == null) {
            if (next === current) return false
        } else if (next.sameAs(current)) {
            return false
        }
        entry.value = next
        return true
    }

    private fun settled(): Boolean =
        hidden || (updated && (!shouldShow() || renderedW > 0f && renderedH > 0f))

    private fun applyPendingLayout() {
        if (pendingLayout < 0) return
        val batch = HudManager.activeInstances.filterIsInstance<ArmorPieceHud>()
            .filter { it.groupPath == groupPath && it.migrationBatch == migrationBatch && it.pendingLayout >= 0 }
            .sortedBy { it.piece.ordinal }
        val visible = batch.filter { !it.hidden }
        if (visible.firstOrNull() !== this || !visible.all { it.settled() } || visible.none { it.shouldShow() }) return

        val root = batch.first()
        val point = if (root.anchorParent != null) root.effectiveSelfAnchorPoint else root.effectiveGrowthAnchor
        val pinX = root.anchorPointX(point)
        val pinY = root.anchorPointY(point)
        ArmorLayout.chain(
            batch,
            vertical = pendingLayout and VERTICAL_BIT != 0,
            reversed = pendingLayout and REVERSED_BIT != 0,
            gap = ArmorLayout.gap(batch, pendingGap),
        )
        val bounds = HudGroups.bounds(batch) ?: return
        ArmorLayout.shift(
            root,
            pinX - (bounds[0] + ArmorLayout.fractionX(point) * bounds[2]),
            pinY - (bounds[1] + ArmorLayout.fractionY(point) * bounds[3]),
        )
        for (hud in batch) {
            hud.pendingLayout = -1
            hud.tree?.id?.let { ConfigManager.active().save(it) }
        }
    }

    private fun Entry.sameAs(other: Entry): Boolean =
        text == other.text && textColor == other.textColor && count == other.count &&
            ItemStack.matches(stack, other.stack)

    private fun buildEntry(): Entry? = whenItemsReady(null) {
        val stack = currentStack() ?: return@whenItemsReady null
        val text = infoText(stack)
        val color = if (dynamicColor && stack.isDamageableItem &&
            (extraInfo == DURABILITY || extraInfo == DURABILITY_PERCENT)
        ) {
            durabilityColor(durabilityPercent(stack))
        } else {
            textColor
        }
        val count = when {
            //~ if = 1.8.9 'Items.BOW || stack.item == Items.CROSSBOW)' -> 'Items.BOW)'
            showArrowCount && (stack.item == Items.BOW || stack.item == Items.CROSSBOW) -> arrowCount().toString()
            //~ if = 1.8.9 'carried(stack.item)' -> 'carried(stack)'
            countHeld && piece.inHand -> carried(stack.item).takeIf { it > 1 }?.toString()
            else -> null
        }
        Entry(stack, text, color, count)
    }

    private fun currentStack(): ItemStack? =
        (if (isReal) piece.equipped() else null) ?: piece.example.takeIf { !isReal || HudManager.isEditing }

    private fun infoText(stack: ItemStack): String = when (extraInfo) {
        DURABILITY -> if (stack.isDamageableItem) (stack.maxDamage - stack.damageValue).toString() else ""
        DURABILITY_PERCENT -> if (stack.isDamageableItem) "${durabilityPercent(stack)}%" else ""
        NAME -> stack.hoverName.string
        //~ if = 1.8.9 'carried(stack.item)' -> 'carried(stack)'
        ITEM_COUNT -> carried(stack.item).toString()
        else -> ""
    }

    private fun durabilityPercent(stack: ItemStack): Int =
        ceil((stack.maxDamage - stack.damageValue).toFloat() / stack.maxDamage.toFloat() * 100f).toInt()

    private fun arrowCount(): Int {
        if (!isReal) return 64
        //? if > 1.8.9 {
        return carried(Items.ARROW) + carried(Items.SPECTRAL_ARROW) + carried(Items.TIPPED_ARROW)
        //?} else
        //return mc.player?.let { carriedCounts()[Items.ARROW to 0] } ?: 0
    }

    //~ if = 1.8.9 'item: Item' -> 'stack: ItemStack'
    private fun carried(item: Item): Int {
        if (!isReal) return 64
        if (mc.player == null) return 0
        //? if > 1.8.9 {
        return carriedCounts()[item] ?: 0
        //?} else
        //return carriedCounts()[stack.item to if (stack.item.hasCustomData()) stack.metadata else 0] ?: 0
    }

    //? if > 1.8.9 {
    private fun carriedCounts(): Map<Item, Int> = countCache ?: HashMap<Item, Int>().also { counts ->
        val inv = mc.player?.inventory
        if (inv != null) for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i)
            if (!s.isEmpty) counts.merge(s.item, s.count, Int::plus)
        }
        countCache = counts
    }
    //?} else {
    /*private fun carriedCounts(): Map<Pair<Item, Int>, Int> = countCache ?: HashMap<Pair<Item, Int>, Int>().also { counts ->
        val inv = mc.player?.inventory
        if (inv != null) for (i in 0 until inv.containerSize) {
            val s = inv.getItem(i) ?: continue
            counts.merge(s.item to if (s.item.hasCustomData()) s.metadata else 0, s.count, Int::plus)
        }
        countCache = counts
    }
    *///?}

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
        val current = entry.value ?: return
        val scale = textScale.coerceAtLeast(0.01f)
        if (style == VANILLA) {
            VanillaContent(current, scale)
            return
        }
        NodeBox(modifier = hudBackground().padding(padLeft, padTop, padRight, padBottom)) {
            Entry(current, scale, PolyAlign.Center)
        }
    }

    private fun drawnStripRect(): FloatArray? {
        if (style != VANILLA || entry.value == null) return null
        val (width, height) = nominalSize()
        val applied = if (isDrawn()) appliedSeams else HotbarSeams.NONE
        return drawnRect(floatArrayOf(x, y, width, height), applied, HOTBAR_BORDER * textScale.coerceAtLeast(0.01f) * effectiveScale)
    }

    private fun isDrawn(): Boolean = HudManager.isEditing || (!hidden && !isCollapsed)

    private fun groupPieces(): List<ArmorPieceHud> = group?.directHuds.orEmpty().filterIsInstance<ArmorPieceHud>() + this

    private fun vanillaNeighbours(): List<ArmorPieceHud> =
        anchoredNeighbours(this, groupPieces(), { it.anchorParent as? ArmorPieceHud }, { it.isDrawn() })
            .filter { it.style == VANILLA }

    private fun labelOffset(boxWidth: Float, boxHeight: Float, labelWidth: Float, labelHeight: Float, gap: Float) =
        when (textPosition) {
            TEXT_LEFT -> -(labelWidth + gap) to (boxHeight - labelHeight) / 2f
            TEXT_ABOVE -> (boxWidth - labelWidth) / 2f to -(labelHeight + gap)
            TEXT_BELOW -> (boxWidth - labelWidth) / 2f to boxHeight + gap
            else -> boxWidth + gap to (boxHeight - labelHeight) / 2f
        }

    @Composable
    private fun VanillaContent(entry: Entry, scale: Float) {
        rev.value
        val strip = drawnStripRect() ?: return
        val edges = seams(strip, vanillaNeighbours().mapNotNull { it.drawnStripRect() }, scale * effectiveScale)
        appliedSeams = edges
        val border = HOTBAR_BORDER * scale
        val stripWidth = hotbarStripLength(1) * scale
        val stripHeight = HOTBAR_THICKNESS * scale
        val stripX = if (edges.cutLeft) -border else 0f
        val stripY = if (edges.cutTop) -border else 0f
        val boxWidth = stripWidth - (if (edges.cutLeft) border else 0f) - (if (edges.cutRight) border else 0f)
        val boxHeight = stripHeight - (if (edges.cutTop) border else 0f) - (if (edges.cutBottom) border else 0f)
        val labelWidth = if (entry.text.isEmpty()) 0f else textWidth(entry.text, scale)

        NodeBox(modifier = PolyModifier.size(boxWidth, boxHeight)) {
            NodeCanvas(PolyModifier.absoluteAt(stripX, stripY).size(stripWidth, stripHeight)) { x, y, _, _ ->
                VanillaTextures.hotbar()?.drawHotbarSlot(this, x, y, scale, edges)
            }
            ItemIcon(
                entry.stack,
                size = ITEM_SIZE * scale,
                decorations = showDecorations,
                countOverride = entry.count,
                modifier = PolyModifier.absoluteAt(
                    stripX + HOTBAR_ITEM_INSET * scale,
                    stripY + HOTBAR_ITEM_INSET * scale,
                ),
            )
            if (labelWidth > 0f) {
                val (labelX, labelY) = labelOffset(boxWidth, boxHeight, labelWidth, textHeight(scale), TEXT_GAP * scale)
                Info(entry, scale, PolyModifier.absoluteAt(labelX, labelY))
            }
        }
    }

    private fun textWidth(text: String, scale: Float): Float = if (font == Font.Minecraft) {
        McFontQueue.measureTextWidth(applyCase(text), scale)
    } else {
        PolyFont(getPoppinsFontName(), FONT_SIZE * scale).measureTextWidth(applyCase(text))
    }

    private fun textHeight(scale: Float): Float = if (font == Font.Minecraft) {
        McFontQueue.measureTextHeight(scale)
    } else {
        PolyFont(getPoppinsFontName(), FONT_SIZE * scale).metrics.let { it.descent - it.ascent }
    }

    @Composable
    private fun Entry(entry: Entry, scale: Float, align: PolyAlign) {
        rev.value
        val gap = if (entry.text.isEmpty()) 0f else TEXT_GAP * scale
        val modifier = PolyModifier.align(align)
        val textFirst = textPosition == TEXT_LEFT || textPosition == TEXT_ABOVE
        val stacked = textPosition == TEXT_ABOVE || textPosition == TEXT_BELOW

        val body: @Composable () -> Unit = {
            if (textFirst && entry.text.isNotEmpty()) Info(entry, scale)
            ItemIcon(
                entry.stack,
                size = ITEM_SIZE * scale,
                decorations = showDecorations,
                countOverride = entry.count,
                modifier = PolyModifier.align(PolyAlign.Center),
            )
            if (!textFirst && entry.text.isNotEmpty()) Info(entry, scale)
        }

        if (stacked) {
            NodeColumn(gap = gap, modifier = modifier, content = body)
        } else {
            NodeRow(gap = gap, modifier = modifier, content = body)
        }
    }

    @Composable
    private fun Info(entry: Entry, scale: Float, modifier: PolyModifier = PolyModifier.align(PolyAlign.Center)) {
        val color = PolyColor(entry.textColor, textChroma, textChromaSpeed)
        val text = applyCase(entry.text)
        if (font == Font.Minecraft) {
            NodeMcText(text, color = color, shadow = showShadow, scale = scale, modifier = modifier)
        } else {
            NodeText(
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


    override fun onCloned() {
        entry = mutableStateOf(null)
        rev = mutableStateOf(0)
    }
}

class ArmorHelmetHud : ArmorPieceHud(ArmorPiece.HELMET)

class ArmorChestplateHud : ArmorPieceHud(ArmorPiece.CHESTPLATE)

class ArmorLeggingsHud : ArmorPieceHud(ArmorPiece.LEGGINGS)

class ArmorBootsHud : ArmorPieceHud(ArmorPiece.BOOTS)

class ArmorMainHandHud : ArmorPieceHud(ArmorPiece.MAIN_HAND)

//? if > 1.8.9
class ArmorOffHandHud : ArmorPieceHud(ArmorPiece.OFF_HAND)
