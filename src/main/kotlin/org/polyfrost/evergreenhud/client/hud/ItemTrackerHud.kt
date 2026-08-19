package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hud.item.ITEM_SIZE
import org.polyfrost.evergreenhud.client.hud.item.ItemIcon
import org.polyfrost.evergreenhud.client.hud.item.whenItemsReady
import org.polyfrost.evergreenhud.client.utils.fastRemoveIfReversed
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import kotlin.math.abs

private const val TEXT_GAP = 2f
private const val FONT_SIZE = 8f

private const val BASE_PADDING = 1f

private const val RIGHT = 1

class ItemTrackerHud : Hud(
    id = "item_tracker.json",
    title = "Item Tracker",
    category = Category.PLAYER,
) {
    private companion object {
        private val EXAMPLE = listOf(Items.DIAMOND to 3, Items.COBBLESTONE to 64, Items.ARROW to -1)
    }

    @Slider(title = "Display Time", description = "Seconds an entry stays on screen.", min = 1F, max = 30F, step = 1F)
    var displayTime = 5f

    @Slider(title = "Max Entries", min = 1F, max = 10F, step = 1F)
    var maxEntries = 5

    @Switch(title = "Show Icon")
    var showIcon = true

    @Switch(title = "Show Item Name")
    var showName = true

    @Switch(title = "Newest First", description = "Put the newest entry at the top instead of the bottom.")
    var newestFirst = true

    @Switch(
        title = "Combine Entries",
        description = "Merge repeat changes to the same item into a single entry instead of listing each one.",
    )
    var combine = true

    @Switch(
        title = "Track Containers",
        description = "Also count items moved to and from chests and other containers.",
    )
    var trackContainers = false

    @Slider(title = "Line Spacing", min = 0F, max = 10F, step = 1F)
    var spacing = 1f

    @RadioButton(title = "Text Position", options = ["Left", "Right"])
    var textPosition = RIGHT

    @Color(title = "Gained Color")
    var gainedColor = PolyColor(0xFF55FF55.toInt())

    @Color(title = "Lost Color")
    var lostColor = PolyColor(0xFFFF5555.toInt())

    private class Change(val item: Item, val stack: ItemStack, var amount: Int, var time: Long)

    private class Entry(val stack: ItemStack, val amount: String, val name: String, val color: Int)

    private var changes = ArrayList<Change>()
    private var counts: HashMap<Item, Int>? = null
    private var trackedPlayer: LocalPlayer? = null

    private var entries = mutableStateOf<List<Entry>>(emptyList())

    private var rev = mutableStateOf(0)

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override val alwaysRedraw: Boolean
        get() = super.alwaysRedraw || (isReal && showIcon && entries.value.isNotEmpty())

    override fun setup() {
        if (!isReal) return
        eventHandler { _: TickEvent.End -> track() }
        for (option in listOf("spacing", "textPosition", "showIcon")) {
            addCallback(option) { rev.value++ }
        }
    }

    private fun track() {
        val player = mc.player
        if (player == null) {
            counts = null
            trackedPlayer = null
            if (changes.isNotEmpty()) changes.clear()
            return
        }

        prune()

        if (player !== trackedPlayer) {
            trackedPlayer = player
            counts = snapshot(player)
            return
        }

        val next = snapshot(player)
        val previous = counts
        counts = next
        if (previous == null) return
        if (!trackContainers && player.containerMenu !== player.inventoryMenu) return

        for ((item, count) in next) {
            val delta = count - (previous[item] ?: 0)
            if (delta != 0) record(item, delta)
        }
        for ((item, count) in previous) {
            if (item !in next) record(item, -count)
        }
    }

    private fun snapshot(player: LocalPlayer): HashMap<Item, Int> {
        val counts = HashMap<Item, Int>()
        val inventory = player.inventory
        for (slot in 0 until inventory.containerSize) {
            val stack = inventory.getItem(slot)
            if (stack.isEmpty) continue
            counts[stack.item] = (counts[stack.item] ?: 0) + stack.count
        }
        return counts
    }

    private fun record(item: Item, delta: Int) {
        val now = System.nanoTime()
        if (combine) {
            val existing = changes.lastOrNull { it.item == item }
            if (existing != null) {
                existing.amount += delta
                existing.time = now
                if (existing.amount == 0) changes.remove(existing)
                return
            }
        }
        changes.add(Change(item, ItemStack(item), delta, now))
        val limit = maxEntries.coerceAtLeast(1)
        while (changes.size > limit) changes.removeAt(0)
    }

    private fun prune() {
        if (changes.isEmpty()) return
        val now = System.nanoTime()
        val lifetime = (displayTime.coerceAtLeast(0f) * 1_000_000_000L).toLong()
        changes.fastRemoveIfReversed { now - it.time > lifetime }
    }

    override fun update(): Boolean {
        prune()
        val next = whenItemsReady(emptyList()) { buildEntries() }
        val current = entries.value
        if (next.size == current.size && next.zip(current).all { (a, b) -> a.sameAs(b) }) return false
        entries.value = next
        return true
    }

    private fun Entry.sameAs(other: Entry): Boolean =
        amount == other.amount && name == other.name && color == other.color &&
            ItemStack.matches(stack, other.stack)

    private fun buildEntries(): List<Entry> {
        val list = if (!isReal || (HudManager.isEditing && changes.isEmpty())) {
            EXAMPLE.take(maxEntries.coerceAtLeast(1)).map { (item, amount) -> entry(ItemStack(item), amount) }
        } else {
            changes.map { entry(it.stack, it.amount) }
        }
        return if (newestFirst) list.asReversed() else list
    }

    private fun entry(stack: ItemStack, amount: Int): Entry {
        val text = (if (amount > 0) "+" else "-") + abs(amount)
        val name = if (showName) stack.hoverName.string else ""
        return Entry(stack, text, name, if (amount > 0) gainedColor.argb else lostColor.argb)
    }

    @Composable
    override fun Content() {
        rev.value
        val list = entries.value
        if (list.isEmpty()) return
        val scale = textScale.coerceAtLeast(0.01f)
        val textSidePadding = if (showIcon) TEXT_GAP else BASE_PADDING
        val leftPadding = if (showIcon && textPosition == RIGHT) BASE_PADDING else textSidePadding
        val rightPadding = if (showIcon && textPosition != RIGHT) BASE_PADDING else textSidePadding
        val modifier = hudBackground().padding(
            padLeft + leftPadding * scale,
            padTop + BASE_PADDING * scale,
            padRight + rightPadding * scale,
            padBottom + BASE_PADDING * scale,
        )
        val rowAlign = if (textPosition == RIGHT) PolyAlign.Left else PolyAlign.Right

        PolyBox(modifier = modifier) {
            PolyColumn(gap = spacing * scale) {
                for (entry in list) Entry(entry, scale, rowAlign)
            }
        }
    }

    @Composable
    private fun Entry(entry: Entry, scale: Float, align: PolyAlign) {
        PolyRow(gap = if (showIcon) TEXT_GAP * scale else 0f, modifier = PolyModifier.align(align)) {
            if (textPosition != RIGHT) Text(entry, scale)
            if (showIcon) {
                ItemIcon(
                    this@ItemTrackerHud,
                    entry.stack,
                    size = ITEM_SIZE * scale,
                    decorations = false,
                    modifier = PolyModifier.align(PolyAlign.Center),
                )
            }
            if (textPosition == RIGHT) Text(entry, scale)
        }
    }

    @Composable
    private fun Text(entry: Entry, scale: Float) {
        PolyRow(
            gap = if (entry.name.isEmpty()) 0f else TEXT_GAP * scale,
            modifier = PolyModifier.align(PolyAlign.Center),
        ) {
            Label(entry.amount, entry.color, scale)
            if (entry.name.isNotEmpty()) Label(entry.name, textColor, scale)
        }
    }

    @Composable
    private fun Label(text: String, argb: Int, scale: Float) {
        val color = PolyColor(argb, textChroma, textChromaSpeed)
        val modifier = PolyModifier.align(PolyAlign.Center)
        if (font == Font.Minecraft) {
            PolyMcText(text, color = color, shadow = showShadow, scale = scale, modifier = modifier)
        } else {
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

    override fun clone(): Hud = (super.clone() as ItemTrackerHud).also {
        it.changes = ArrayList()
        it.counts = null
        it.trackedPlayer = null
        it.entries = mutableStateOf(emptyList())
        it.rev = mutableStateOf(0)
    }
}
