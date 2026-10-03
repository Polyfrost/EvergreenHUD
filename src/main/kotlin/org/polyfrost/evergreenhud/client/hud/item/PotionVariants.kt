package org.polyfrost.evergreenhud.client.hud.item

//? if > 1.8.9 {
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
//?} else {
/*import net.minecraft.client.resource.language.I18n
import net.minecraft.entity.living.effect.StatusEffect
import net.minecraft.entity.living.effect.StatusEffectInstance
import net.minecraft.world.item.Items
import net.minecraft.world.item.PotionItem
*///?}
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
//? if > 1.8.9 {
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents
//?}

const val ANY_POTION = "any"

const val ANY_POTION_LABEL = "Any"

private val ROMAN = arrayOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X")

class PotionVariant(val id: String, val label: String, val suffix: String)

fun isAnyPotion(id: String): Boolean = id == ANY_POTION || id.isBlank()

val Item.hasPotionContents: Boolean
    //? if > 1.8.9 {
    get() = whenItemsReady(false) { components().has(DataComponents.POTION_CONTENTS) }
    //?} else
    //get() = this is PotionItem

private var cachedVariants: List<PotionVariant>? = null
private var cachedById: Map<String, PotionVariant> = emptyMap()
//? if = 1.8.9
//private var cachedByEffects: Map<List<StatusEffectInstance>, String> = emptyMap()

fun potionVariants(): List<PotionVariant> {
    //? if > 1.8.9 {
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
    //?} else {
    /*cachedVariants?.let { return it }
    val potion = Items.POTION
    val stacks = ArrayList<ItemStack>()
    potion.addToCreativeMenu(potion, potion.creativeModeTab, stacks)
    val seen = HashMap<String, Int>()
    val variants = stacks.map { stack ->
        val metadata = stack.metadata
        val effect = potion.getPotionEffects(metadata)?.firstOrNull()
        val suffix = effect?.let { legacySuffix(it, metadata) }.orEmpty()
        var label = if (effect == null) {
            stack.hoverName.string
        } else {
            (if (PotionItem.isSplashPotion(metadata)) "Splash " else "") + I18n.translate(effect.name) + suffix
        }
        val count = seen.merge(label, 1, Int::plus) ?: 1
        if (count > 1) label += " [$metadata]"
        PotionVariant(metadata.toString(), label, suffix)
    }
    cachedVariants = variants
    cachedById = variants.associateBy { it.id }
    cachedByEffects = variants.mapNotNull { v -> potion.getPotionEffects(v.id.toInt())?.let { it to v.id } }.toMap()
    return variants
    *///?}
}

fun potionVariant(id: String): PotionVariant? {
    if (isAnyPotion(id)) return null
    potionVariants()
    return cachedById[id]
}

fun potionIdOf(stack: ItemStack): String? {
    //? if > 1.8.9 {
    val potion = stack.get(DataComponents.POTION_CONTENTS)?.potion()?.orElse(null) ?: return null
    return BuiltInRegistries.POTION.getKey(potion.value())?.toString()
    //?} else {
    /*val item = stack.item as? PotionItem ?: return null
    potionVariants()
    // the same potion can have several metadata values, so match by its effects
    return item.getPotionEffects(stack.metadata)?.let { cachedByEffects[it] } ?: stack.metadata.toString()
    *///?}
}

//? if > 1.8.9 {
fun potionStack(item: Item, id: String): ItemStack = whenItemsReady(ItemStack(item)) {
    if (potionVariant(id) == null) return@whenItemsReady ItemStack(item)
    val potion = BuiltInRegistries.POTION.firstOrNull { BuiltInRegistries.POTION.getKey(it)?.toString() == id }
        ?: return@whenItemsReady ItemStack(item)
    PotionContents.createItemStack(item, BuiltInRegistries.POTION.wrapAsHolder(potion))
}
//?} else
//fun potionStack(item: Item, id: String): ItemStack = if (potionVariant(id) == null) ItemStack(item) else ItemStack(item, 1, id.toInt())

//? if > 1.8.9 {
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
//?} else {
/*private fun legacySuffix(effect: StatusEffectInstance, metadata: Int): String {
    val amplifier = effect.amplifier
    return when {
        amplifier > 0 -> " ${ROMAN.getOrElse(amplifier) { (amplifier + 1).toString() }}"
        metadata and 0x40 != 0 && !StatusEffect.BY_ID[effect.id].isInstant -> " (Extended)"
        else -> ""
    }
}
*///?}

private fun titleCase(path: String): String = path.split('_').joinToString(" ") { word ->
    word.replaceFirstChar(Char::uppercaseChar)
}
