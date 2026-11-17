@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import net.minecraft.client.Minecraft
//? if > 1.8.9
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.toTextRuns
import org.polyfrost.oneconfig.api.config.v1.annotations.Checkbox
import org.polyfrost.oneconfig.api.config.v1.annotations.Number
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.HudText
import org.polyfrost.oneconfig.api.hud.v1.TextRun
import org.polyfrost.oneconfig.api.hud.v1.plainText
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.evergreenhud.client.utils.SpacedTextHud

private val AQUA = PolyColor(0xFF55FFFF.toInt())
private val GRAY = PolyColor(0xFFAAAAAA.toInt())

private val PLACEHOLDER_LINES = listOf(
    listOf(TextRun("Item Lore HUD", AQUA)),
    listOf(TextRun("Hold an item with lore", GRAY)),
)

class LoreHud : SpacedTextHud(
    id = "lore.json",
    title = "Item Lore",
    category = Category.INFO,
    prefix = "",
) {
    @Switch(title = "Show Item Name")
    var showName = true

    @Checkbox(title = "Remove Empty Lines")
    var removeEmptyLines = true

    @Number(title = "Max Lines", min = 0f, max = 50f)
    var maxLines = 0

    private val ItemStack.isNameShown: Boolean
        //~ if = 1.8.9 'has(DataComponents.CUSTOM_NAME)' -> 'hasCustomHoverName()'
        get() = has(DataComponents.CUSTOM_NAME)

    private var currentLines: List<List<TextRun>> = emptyList()
    private var hasLore = false
    private var linesState: MutableState<List<List<TextRun>>> = mutableStateOf(emptyList())

    private var tickHandler: EventHandler<*>? = null

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun setup() {
        super.setup()
        if (isReal) {
            updateWhenChanged("showName")
            updateWhenChanged("removeEmptyLines")
            updateWhenChanged("maxLines")
            tickHandler = eventHandler { _: TickEvent.End ->
                if (!hidden || HudManager.isEditing) theItem = Minecraft.getInstance().player?.mainHandItem
            }
        }
    }

    override fun updateFrequency(): Long = if (isReal) Long.MAX_VALUE else -1L

    override fun remove() {
        tickHandler?.unregister()
        tickHandler = null
    }

    var theItem: ItemStack? = null
        set(value) {
            val old = field
            if (old == null && value == null) return
            if (old != null && value != null && ItemStack.matches(old, value)) return
            field = value?.copy()
            updateAndRecalculate()
        }

    @Composable
    override fun Content() = HudText(linesState.value)

    override fun update(): Boolean {
        val lore = loreLines()
        hasLore = lore.isNotEmpty()
        currentLines = lore.ifEmpty { PLACEHOLDER_LINES }

        val result = super.update()
        linesState.value = currentLines
        return result
    }

    override fun getText(): String = currentLines.joinToString("\n") { it.plainText() }

    override fun shouldShow(): Boolean = hasLore

    private fun loreLines(): List<List<TextRun>> {
        val item = theItem ?: return emptyList()
        val out = ArrayList<List<TextRun>>()

        if (showName && item.isNameShown) {
            val name = item.hoverName.toTextRuns()
            if (name.plainText().isNotEmpty()) out.add(name)
        }

        var i = 0
        item.forEachLore { line ->
            if (maxLines in 1..i) return@forEachLore
            if (removeEmptyLines && line.plainText().isBlank()) return@forEachLore
            out.add(line)
            i++
        }

        return out
    }

    private inline fun ItemStack.forEachLore(consumer: (List<TextRun>) -> Unit) {
        //? if > 1.8.9 {
        val lore = this.get(DataComponents.LORE) ?: return
        for (line: Component in lore.lines) {
            consumer(line.toTextRuns())
        }
        //?} else {
        /*val display = nbt?.getCompound("display") ?: return
        val lore = display.getList("Lore", 8)
        for (i in 0 until lore.size()) {
            consumer(Component.fromLegacy(lore.getString(i)).toTextRuns())
        }
        *///?}
    }

    override fun onCloned() {
        linesState = mutableStateOf(emptyList())
        tickHandler = null
    }
}
