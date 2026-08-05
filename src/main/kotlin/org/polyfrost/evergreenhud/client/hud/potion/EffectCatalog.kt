package org.polyfrost.evergreenhud.client.hud.potion

//? if > 1.8.9 {
import net.minecraft.core.registries.BuiltInRegistries
//?} else {
/*import net.minecraft.client.resource.language.I18n
import net.minecraft.entity.living.effect.StatusEffect
*///?}

object EffectCatalog {
    data class Entry(val path: String, val title: String)

    private var cached: List<Entry>? = null

    val ENTRIES: List<Entry>
        get() = cached ?: buildEntries().also { cached = it }

    private fun buildEntries(): List<Entry> {
        val entries = mutableListOf<Entry>()
        //? if > 1.8.9 {
        for (effect in BuiltInRegistries.MOB_EFFECT) {
            val id = BuiltInRegistries.MOB_EFFECT.getKey(effect) ?: continue
            entries += Entry(id.path, effect.displayName.string)
        }
        //?} else {
        /*for (effect in StatusEffect.BY_ID.filterNotNull()) {
            entries += Entry(effect.id.toString(), I18n.translate(effect.translationKey))
        }
        *///?}
        return entries
    }

    val titleToPath: Map<String, String> get() = ENTRIES.associate { it.title to it.path }
}