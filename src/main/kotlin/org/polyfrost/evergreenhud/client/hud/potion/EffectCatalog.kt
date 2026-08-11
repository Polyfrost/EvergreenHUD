package org.polyfrost.evergreenhud.client.hud.potion

import net.minecraft.core.registries.BuiltInRegistries

object EffectCatalog {
    data class Entry(val path: String, val title: String)

    private var cached: List<Entry>? = null

    val ENTRIES: List<Entry>
        get() = cached ?: buildEntries().also { cached = it }

    private fun buildEntries(): List<Entry> {
        val entries = mutableListOf<Entry>()
        for (effect in BuiltInRegistries.MOB_EFFECT) {
            val id = BuiltInRegistries.MOB_EFFECT.getKey(effect) ?: continue
            entries += Entry(id.path, effect.displayName.string)
        }
        return entries
    }

    val titleToPath: Map<String, String> get() = ENTRIES.associate { it.title to it.path }
}