@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
//? if > 1.8.9
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.polyfrost.evergreenhud.client.hud.item.hasPotionContents
import org.polyfrost.evergreenhud.client.hud.item.potionStack
import org.polyfrost.evergreenhud.client.hud.item.potionVariants
import org.polyfrost.evergreenhud.client.hud.item.whenItemsReady
import org.polyfrost.oneconfig.api.ui.v1.item.ItemStackIcon
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import org.polyfrost.oneconfig.internal.ui.components.dropdown.SimpleDropdownMenu
import org.polyfrost.oneconfig.internal.ui.components.item.ItemIcon
import org.polyfrost.oneconfig.internal.ui.components.item.ItemIconHandle
import org.polyfrost.oneconfig.internal.ui.components.item.rememberItemCatalog
import org.polyfrost.oneconfig.internal.ui.components.picker.PickerEntry
import org.polyfrost.oneconfig.internal.ui.components.picker.TilePicker
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

private const val MAX_ITEM_COUNT = 41F * 64F

internal val ITEM_LOCATIONS = buildList {
    add(ItemLocation.MAIN_HAND)
    //? if > 1.8.9 {
    add(ItemLocation.OFFHAND)
    add(ItemLocation.EITHER_HAND)
    //?}
    add(ItemLocation.HOTBAR)
    add(ItemLocation.INVENTORY)
}

class ItemOption(override val id: String, override val displayName: String) : PickerEntry

private val itemsById: Map<String, Item> by lazy {
    //~ if = 1.8.9 'BuiltInRegistries.ITEM' -> 'Item.REGISTRY'
    BuiltInRegistries.ITEM.associateBy { BuiltInRegistries.ITEM.getKey(it).toString() }
}

internal fun stackOf(key: String): ItemStack? {
    val item = itemsById[itemOfKey(key)] ?: return null
    val potion = potionOfKey(key) ?: return ItemStack(item)
    return potionStack(item, potion)
}

private fun potionLabel(itemName: String, variant: String): String =
    //? if > 1.8.9 {
    "$itemName: $variant"
    //?} else
    //variant

/** Every item, then each potion variant of the items that hold one as its own entry */
@Composable
private fun rememberItemOptions(): List<ItemOption> {
    val items = rememberItemCatalog()
    return remember(items) {
        items.flatMap { item ->
            val option = ItemOption(item.id, item.displayName)
            val hasPotions = whenItemsReady(false) { itemsById[item.id]?.hasPotionContents == true }
            if (!hasPotions) listOf(option)
            else listOf(option) + potionVariants().map { ItemOption(itemKey(item.id, it.id), potionLabel(item.displayName, it.label)) }
        }
    }
}

@Composable
internal fun ItemKeyIcon(key: String, modifier: Modifier = Modifier, alpha: Float = 1f) {
    val stack = remember(key) { whenItemsReady(null) { stackOf(key) } }
    key(key) {
        if (stack != null) ItemStackIcon(stack, modifier, framed = false, alpha = alpha)
        else ItemIcon(null as ItemIconHandle?, modifier, framed = false, alpha = alpha)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ItemValues(condition: OverrideCondition, shape: Shape, onChange: () -> Unit) {
    val query = condition.items
    val options = rememberItemOptions()
    val names = remember(options) { options.associate { it.id to it.displayName } }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (location in ITEM_LOCATIONS) {
            Pill(location.title, selected = query.location == location) {
                query.location = location
                onChange()
            }
        }
    }

    if (query.maxRequired > 1) RequiredCount(query, onChange)

    for (entry in query.entries) {
        key(entry.key) {
            ItemEntryCard(entry, names[entry.key] ?: entry.key, shape, onChange) {
                query.entries.remove(entry)
                onChange()
            }
        }
    }

    ItemPickerButton(query, options, shape, onChange)
}

@Composable
private fun RequiredCount(query: ItemQuery, onChange: () -> Unit) {
    val theme = OneConfigTheme.current
    val required = query.effectiveRequired
    fun set(value: Int) {
        query.required = value
        onChange()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("At least", color = theme.textColor, fontSize = 12.sp)
        Pill("−", selected = false, enabled = required > 1) { set(required - 1) }
        Text(required.toString(), color = theme.textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Pill("+", selected = false, enabled = required < query.maxRequired) { set(required + 1) }
        Text("of ${query.entries.size}", color = theme.textColorSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun ItemEntryCard(entry: ItemEntry, name: String, shape: Shape, onChange: () -> Unit, onRemove: () -> Unit) {
    val theme = OneConfigTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(theme.modCardBackground, shape)
            .border(1.dp, theme.borderColor, shape)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ItemKeyIcon(entry.key, Modifier.size(24.dp))
            Text(name, modifier = Modifier.weight(1f), color = theme.textColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            HoverIcon("trash", onClick = onRemove)
        }
        RangeList(entry, entry.counts, MAX_ITEM_COUNT, "Add count range", reorderable = false) {
            entry.counts = it
            onChange()
        }
    }
}

@Composable
private fun ItemPickerButton(query: ItemQuery, options: List<ItemOption>, shape: Shape, onChange: () -> Unit) {
    val theme = OneConfigTheme.current
    var picking by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        ActionButton("plus", "Add item", shape) { picking = true }

        SimpleDropdownMenu(
            expanded = picking,
            onDismissRequest = { picking = false },
            offset = DpOffset(0.dp, 8.dp),
            modifier = Modifier.width(PickerWidth),
            pinAnchor = true,
        ) {
            TilePicker(
                entries = options,
                selected = query.entries.map { it.key },
                modifier = Modifier
                    .clip(PickerShape)
                    .background(theme.popupBackground, PickerShape)
                    .border(1.dp, theme.borderColor, PickerShape)
                    .padding(vertical = 12.dp),
                onToggle = { key ->
                    val existing = query.entries.firstOrNull { it.key == key }
                    if (existing != null) query.entries.remove(existing) else query.entries += ItemEntry(key)
                    onChange()
                },
                icon = { option, modifier, alpha -> ItemKeyIcon(option.id, modifier, alpha) },
                onClose = { picking = false },
                titleKey = "Items",
                searchPlaceholderKey = "Search items",
                emptyKey = "No items found",
            )
        }
    }
}
