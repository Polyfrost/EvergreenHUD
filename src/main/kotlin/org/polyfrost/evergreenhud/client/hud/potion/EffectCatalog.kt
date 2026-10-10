package org.polyfrost.evergreenhud.client.hud.potion

//? if > 1.8.9 {
import net.minecraft.core.registries.BuiltInRegistries
//?} else {
/*import net.minecraft.client.resource.language.I18n
import net.minecraft.entity.living.effect.StatusEffect
*///?}

object EffectCatalog {
    data class Entry(val path: String, val title: String, val category: String)

    val LEGACY_IDS: Map<String, String> = mapOf(
        "1" to "speed",
        "2" to "slowness",
        "3" to "haste",
        "4" to "mining_fatigue",
        "5" to "strength",
        "6" to "instant_health",
        "7" to "instant_damage",
        "8" to "jump_boost",
        "9" to "nausea",
        "10" to "regeneration",
        "11" to "resistance",
        "12" to "fire_resistance",
        "13" to "water_breathing",
        "14" to "invisibility",
        "15" to "blindness",
        "16" to "night_vision",
        "17" to "hunger",
        "18" to "weakness",
        "19" to "poison",
        "20" to "wither",
        "21" to "health_boost",
        "22" to "absorption",
        "23" to "saturation",
    )

    private var cached: List<Entry>? = null

    val ENTRIES: List<Entry>
        get() = cached ?: buildEntries().also { cached = it }

    //? if <= 1.8.9 {
    /*private val pathById: Map<Int, String> by lazy {
        StatusEffect.getKeys().mapNotNull { key -> StatusEffect.get(key.toString())?.let { it.id to key.path } }.toMap()
    }

    fun pathOf(id: Int): String = pathById[id] ?: id.toString()
    *///?}

    private fun buildEntries(): List<Entry> {
        val entries = mutableListOf<Entry>()
        //? if > 1.8.9 {
        for (effect in BuiltInRegistries.MOB_EFFECT) {
            val id = BuiltInRegistries.MOB_EFFECT.getKey(effect) ?: continue
            entries += Entry(id.path, effect.displayName.string, effect.category.name.lowercase())
        }
        //?} else {
        /*for (effect in StatusEffect.BY_ID.filterNotNull()) {
            entries += Entry(pathOf(effect.id), I18n.translate(effect.translationKey), if (effect.isHarmful) "harmful" else "beneficial")
        }
        *///?}
        return entries
    }

    val titleToPath: Map<String, String> get() = ENTRIES.associate { it.title to it.path }
}