package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemContainerContents
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.absoluteAt
import org.polyfrost.compose.composables.background
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hooks.EnderChestTracker
import org.polyfrost.evergreenhud.client.hooks.ShulkerPreview
import org.polyfrost.evergreenhud.client.hooks.heldShulkerBox
import org.polyfrost.evergreenhud.client.hooks.shulkerContents
import org.polyfrost.evergreenhud.client.hud.item.ITEM_SIZE
import org.polyfrost.evergreenhud.client.hud.item.ItemGrid
import org.polyfrost.evergreenhud.client.hud.item.ItemGridSlot
import org.polyfrost.evergreenhud.client.hud.item.whenItemsReady
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind
import org.polyfrost.oneconfig.utils.v1.dsl.mc

private const val PLAYER = 0
private const val ENDER_CHEST = 1
private const val HELD_SHULKER = 2

private const val COLS = 9
private const val ROWS = 3
private const val SLOT = 18f
private const val SLOT_BOUND_GAP = 2f
private const val SLOT_BOUND_SIZE = SLOT - SLOT_BOUND_GAP
private const val EDGE = 8f
private const val TITLE_Y = 6f
private const val FONT_SIZE = 8f

private const val WIDTH = 176f
private const val GRID_HEIGHT = ROWS * SLOT
private const val GRID_TOP = 6f
private const val GRID_TOP_TITLED = 20f

private const val GRID_BOTTOM = GRID_TOP

private const val MIN_SCALE = 0.25f
private const val MIN_WIDTH = WIDTH * MIN_SCALE
private const val MIN_HEIGHT = (GRID_TOP + GRID_HEIGHT + GRID_BOTTOM) * MIN_SCALE

private const val DEFAULT_HEIGHT = GRID_TOP_TITLED + GRID_HEIGHT + GRID_BOTTOM

class InventoryHud : Hud(
    id = "inventory.json",
    title = "Inventory",
    category = Category.PLAYER,
) {
    private companion object {
        private val exampleShulker by lazy {
            ItemStack(Items.SHULKER_BOX).apply {
                set(
                    DataComponents.CONTAINER,
                    ItemContainerContents.fromItems(
                        listOf(
                            ItemStack(Items.DIAMOND, 32),
                            ItemStack(Items.ENCHANTED_BOOK),
                            ItemStack(Items.GOLDEN_APPLE, 8),
                            ItemStack(Items.DIAMOND_PICKAXE),
                        ),
                    ),
                )
            }
        }

        private val exampleContents by lazy {
            List(ROWS * COLS) { i ->
                when (i) {
                    0 -> ItemStack(Items.DIAMOND, 32)
                    1 -> ItemStack(Items.ENCHANTED_BOOK)
                    9 -> ItemStack(Items.GOLDEN_APPLE, 8)
                    11 -> ItemStack(Items.DIAMOND_PICKAXE)
                    else -> ItemStack.EMPTY
                }
            }
        }
    }

    @RadioButton(title = "Inventory", options = ["Player", "Ender Chest", "Held Shulker"])
    var type = PLAYER

    @Switch(title = "Show Title")
    var showTitle = true

    @Switch(title = "Slot Bounds", description = "Draw a background behind every slot in the grid.")
    var slotBounds = false

    @Color(title = "Slot Bounds Color")
    var slotBoundsColor = PolyColor(0x60000000)

    @Slider(title = "Slot Bounds Radius", min = 0F, max = 8F, step = 1F)
    var slotBoundsRadius = 0f

    @Keybind(
        title = "Pin Shulker Preview",
        description = "Held Shulker only. Keeps the shulker's contents on screen after you stop holding it. Press again to unpin.",
    )
    var pinKey: OneConfigKeybind = KeybindHelper.builder()
        .key(InputConstants.KEY_V)
        .inScreens()
        .action { pressed: Boolean -> if (pressed) ShulkerPreview.togglePin(); true }
        .register()

    private class Grid(val title: String?, val items: List<ItemStack>)

    private var grid = mutableStateOf<Grid?>(null)

    private var _staticW = mutableStateOf(WIDTH)
    override var staticW: Float
        get() = _staticW.value
        set(v) { _staticW.value = v.coerceAtLeast(MIN_WIDTH) }

    private var _staticH = mutableStateOf(DEFAULT_HEIGHT)
    override var staticH: Float
        get() = _staticH.value
        set(v) { _staticH.value = v.coerceAtLeast(MIN_HEIGHT) }

    private var sizedForHeight = 0f

    private fun shulker(): ItemStack? =
        if (!isReal) exampleShulker else ShulkerPreview.pinnedStack ?: mc.player?.heldShulkerBox()

    private val visible: Boolean
        get() = type != HELD_SHULKER || HudManager.isEditing || shulker() != null

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun minimumSize(): Pair<Float, Float> = MIN_WIDTH to MIN_HEIGHT

    override val alwaysRedraw: Boolean
        get() = super.alwaysRedraw || (isReal && grid.value?.items?.any { !it.isEmpty } == true)

    override fun setup() {
        super.setup()
        staticWidth = true
        tree?.getProp("staticW")?.addMetadata("default", WIDTH)
        tree?.getProp("staticH")?.addMetadata("default", naturalHeight())
        if (isReal) {
            hideIf("slotBoundsColor") { !slotBounds }
            hideIf("slotBoundsRadius") { !slotBounds }
            updateWhenChanged("type")
            updateWhenChanged("showTitle")
        }
    }

    override fun update(): Boolean {
        val next = whenItemsReady(null) {
            if (visible) Grid(if (showTitle) titleText() else null, contents().orEmpty()) else null
        }
        publishSlots(next)
        keepScaleAcrossNaturalHeight()

        val current = grid.value
        if (next == null && current == null) return false
        if (next != null && current != null && next.sameAs(current)) return false
        grid.value = next
        return true
    }

    private fun Grid.sameAs(other: Grid): Boolean =
        title == other.title && items.size == other.items.size &&
            items.indices.all { ItemStack.matches(items[it], other.items[it]) }

    private fun publishSlots(next: Grid?) {
        if (next == null || type != HELD_SHULKER || !isReal || HudManager.isEditing) return
        val scale = effectiveScale
        val content = contentScale
        val top = gridTop()
        val originX = offsetX(content)
        val originY = offsetY(content)
        val slots = ArrayList<ShulkerPreview.Slot>(next.items.size)
        for (i in next.items.indices) {
            val item = next.items[i]
            if (item.isEmpty) continue
            slots.add(
                ShulkerPreview.Slot(
                    x + (originX + (EDGE + i % COLS * SLOT) * content) * scale,
                    y + (originY + (top + i / COLS * SLOT) * content) * scale,
                    ITEM_SIZE * content * scale,
                    item,
                ),
            )
        }
        ShulkerPreview.publishSlots(slots)
    }

    private fun gridTop(): Float = if (showTitle) GRID_TOP_TITLED else GRID_TOP

    private fun naturalHeight(): Float = gridTop() + GRID_HEIGHT + GRID_BOTTOM

    private val boxW: Float get() = if (staticWidth && staticW > 0f) staticW else WIDTH

    private val boxH: Float get() = if (staticWidth && staticH > 0f) staticH else naturalHeight()

    private val contentScale: Float
        get() {
            val natural = naturalHeight()
            if (natural <= 0f) return 1f
            return minOf(boxW / WIDTH, boxH / natural).coerceAtLeast(MIN_SCALE)
        }

    private fun offsetX(content: Float): Float = ((boxW - WIDTH * content) / 2f).coerceAtLeast(0f)

    private fun offsetY(content: Float): Float = ((boxH - naturalHeight() * content) / 2f).coerceAtLeast(0f)

    private fun keepScaleAcrossNaturalHeight() {
        val natural = naturalHeight()
        if (natural <= 0f) return
        if (sizedForHeight <= 0f || staticH <= 0f) {
            sizedForHeight = natural
            return
        }
        if (natural == sizedForHeight) return
        staticH = staticH / sizedForHeight * natural
        sizedForHeight = natural
    }

    private fun titleText(): String = when (type) {
        PLAYER -> "Inventory"
        ENDER_CHEST -> "Ender Chest"
        else -> shulker()?.hoverName?.string ?: "Shulker Box"
    }

    private fun contents(): List<ItemStack>? = when (type) {
        HELD_SHULKER -> shulker()?.shulkerContents()
        ENDER_CHEST -> if (!isReal) exampleContents else EnderChestTracker.contents()
        else -> if (!isReal) {
            exampleContents
        } else {
            val inv = mc.player?.inventory
            inv?.let { List(ROWS * COLS) { i -> if (COLS + i < it.containerSize) it.getItem(COLS + i).copy() else ItemStack.EMPTY } }
        }
    }

    @Composable
    override fun Content() {
        val current = grid.value ?: return
        val top = gridTop()
        val content = contentScale
        val originX = offsetX(content)
        val originY = offsetY(content)

        PolyBox(modifier = hudBackground().size(boxW, boxH)) {
            current.title?.let { Title(it, content, originX, originY) }

            val boundInset = (SLOT_BOUND_SIZE - ITEM_SIZE) / 2f

            if (slotBounds) {
                for (row in 0 until ROWS) {
                    for (col in 0 until COLS) {
                        PolyBox(
                            modifier = PolyModifier
                                .absoluteAt(
                                    originX + (EDGE + col * SLOT - boundInset) * content,
                                    originY + (top + row * SLOT - boundInset) * content,
                                )
                                .size(SLOT_BOUND_SIZE * content, SLOT_BOUND_SIZE * content)
                                .background(slotBoundsColor, slotBoundsRadius * content),
                        )
                    }
                }
            }

            val slots = ArrayList<ItemGridSlot>(ROWS * COLS)
            for (row in 0 until ROWS) {
                for (col in 0 until COLS) {
                    val item = current.items.getOrNull(row * COLS + col) ?: continue
                    if (item.isEmpty) continue
                    slots.add(
                        ItemGridSlot(
                            originX + (EDGE + col * SLOT) * content,
                            originY + (top + row * SLOT) * content,
                            item,
                        ),
                    )
                }
            }

            ItemGrid(this@InventoryHud, slots, boxW, boxH, size = ITEM_SIZE * content)
        }
    }

    @Composable
    private fun Title(title: String, content: Float, originX: Float, originY: Float) {
        val color = PolyColor(textColor, textChroma, textChromaSpeed)
        val modifier = PolyModifier.absoluteAt(originX + EDGE * content, originY + TITLE_Y * content)
        if (font == Font.Minecraft) {
            PolyMcText(title, color = color, shadow = showShadow, scale = content, modifier = modifier)
        } else {
            PolyText(
                title,
                color = color,
                fontSize = FONT_SIZE * content,
                shadow = showShadow,
                shadowColor = PolyColor(shadowColor, shadowChroma, shadowChromaSpeed),
                shadowOffset = shadowOffsetX * content,
                font = getPoppinsFontName(),
                modifier = modifier,
            )
        }
    }

    override fun clone(): Hud = (super.clone() as InventoryHud).also {
        it.grid = mutableStateOf(null)
        it._staticW = mutableStateOf(staticW)
        it._staticH = mutableStateOf(staticH)
        it.sizedForHeight = sizedForHeight
    }
}
