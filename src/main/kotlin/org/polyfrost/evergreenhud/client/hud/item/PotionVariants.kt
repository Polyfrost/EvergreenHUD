package org.polyfrost.evergreenhud.client.hud.item

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents

const val ANY_POTION = "any"

const val ANY_POTION_LABEL = "Any"

private val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

class PotionVariant(val id: String, val label: String, val suffix: String)

fun isAnyPotion(id: String): Boolean = id == ANY_POTION || id.isBlank()

val Item.hasPotionContents: Boolean
    get() = whenItemsReady(false) { components().has(DataComponents.POTION_CONTENTS) }

private var cachedVariants: List<PotionVariant>? = null
private var cachedById: Map<String, PotionVariant> = emptyMap()

fun potionVariants(): List<PotionVariant> {
    cachedVariants?.let { return it }
    val variants = whenItemsReady(emptyList<PotionVariant>()) {
        val seen = HashMap<String, Int>()
        BuiltInRegistries.POTION.mapNotNull { potion ->
            val id = BuiltInRegistries.POTION.getKey(potion) ?: return@mapNotNull null
            val suffix = suffixOf(potion, id.path)
            var label = baseLabel(potion, id.path) + suffix
            if (id.namespace != "minecraft") label += " (${id.namespace})"
            val count = seen.merge(label, 1, Int::plus) ?: 1
            if (count > 1) label += " [${id.path}]"
            PotionVariant(id.toString(), label, suffix)
        }
    }
    if (variants.isEmpty()) return variants
    cachedVariants = variants
    cachedById = variants.associateBy { it.id }
    return variants
}

fun potionVariant(id: String): PotionVariant? {
    if (isAnyPotion(id)) return null
    potionVariants()
    return cachedById[id]
}

fun potionIdOf(stack: ItemStack): String? {
    val potion = stack.get(DataComponents.POTION_CONTENTS)?.potion()?.orElse(null) ?: return null
    return BuiltInRegistries.POTION.getKey(potion.value())?.toString()
}

fun potionStack(item: Item, id: String): ItemStack = whenItemsReady(ItemStack(item)) {
    if (potionVariant(id) == null) return@whenItemsReady ItemStack(item)
    val potion = BuiltInRegistries.POTION.firstOrNull { BuiltInRegistries.POTION.getKey(it)?.toString() == id }
        ?: return@whenItemsReady ItemStack(item)
    PotionContents.createItemStack(item, BuiltInRegistries.POTION.wrapAsHolder(potion))
}

private fun baseLabel(potion: Potion, path: String): String {
    val effect = potion.effects.firstOrNull() ?: return titleCase(path)
    return effect.effect.value().displayName.string
}

private fun suffixOf(potion: Potion, path: String): String {
    val effect = potion.effects.firstOrNull() ?: return ""
    val amplifier = effect.amplifier
    return when {
        amplifier > 0 -> " ${ROMAN.getOrElse(amplifier) { (amplifier + 1).toString() }}"
        path.startsWith("long_") -> " (Extended)"
        else -> ""
    }
}

private fun titleCase(path: String): String = path.split('_').joinToString(" ") { word ->
    word.replaceFirstChar(Char::uppercaseChar)
}
