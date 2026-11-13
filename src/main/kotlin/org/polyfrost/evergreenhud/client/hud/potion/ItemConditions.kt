package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.EnumMap

private const val POTION_SEPARATOR = "|"

//? if > 1.8.9 {
const val INVENTORY_SLOTS = 37
//?} else
//const val INVENTORY_SLOTS = 36

/** An item ID such as `minecraft:golden_apple`, or a potion variant as `minecraft:splash_potion|minecraft:healing` */
fun itemKey(itemId: String, potionId: String? = null): String = if (potionId == null) itemId else "$itemId$POTION_SEPARATOR$potionId"

fun itemOfKey(key: String): String = key.substringBefore(POTION_SEPARATOR)

fun potionOfKey(key: String): String? = key.substringAfter(POTION_SEPARATOR, "").ifEmpty { null }

/** [slots] is how many different stacks the location can hold at once */
enum class ItemLocation(val key: String, val title: String, val slots: Int) {
    MAIN_HAND("main_hand", "Main hand", 1),
    OFFHAND("offhand", "Offhand", 1),
    EITHER_HAND("either_hand", "Either hand", 2),
    HOTBAR("hotbar", "Hotbar", 9),
    INVENTORY("inventory", "Inventory", INVENTORY_SLOTS);

    companion object {
        fun of(key: String?): ItemLocation = entries.firstOrNull { it.key == key } ?: MAIN_HAND
    }
}

/** No [counts] means at least one */
class ItemEntry(val key: String, counts: FloatArray = floatArrayOf()) {
    var counts by mutableStateOf(counts)

    fun satisfiedBy(count: Int): Boolean = if (counts.isEmpty()) count > 0 else counts.allowsInRanges(count.toFloat())

    fun copy(): ItemEntry = ItemEntry(key, counts.copyOf())
}

/** At least [required] of the [entries] must be present, which can never be more than the location holds */
class ItemQuery(location: ItemLocation = ItemLocation.MAIN_HAND, required: Int = 1, entries: List<ItemEntry> = emptyList()) {
    var location by mutableStateOf(location)
    var required by mutableStateOf(required)
    val entries: MutableList<ItemEntry> = mutableStateListOf<ItemEntry>().apply { addAll(entries) }

    val maxRequired: Int get() = minOf(entries.size, location.slots).coerceAtLeast(1)

    val effectiveRequired: Int get() = required.coerceIn(1, maxRequired)

    fun matches(items: ItemSnapshot): Boolean = entries.count { it.satisfiedBy(items.count(location, it.key)) } >= effectiveRequired

    fun copy(): ItemQuery = ItemQuery(location, required, entries.map { it.copy() })

    fun toJson(): JsonObject = JsonObject().also { json ->
        json.addProperty("location", location.key)
        json.addProperty("required", required)
        json.add("entries", JsonArray().also { array ->
            for (entry in entries) array.add(JsonObject().also { e ->
                e.addProperty("key", entry.key)
                e.add("counts", JsonArray().also { counts -> entry.counts.forEach { counts.add(JsonPrimitive(it)) } })
            })
        })
    }

    companion object {
        fun fromJson(json: JsonObject?): ItemQuery {
            if (json == null) return ItemQuery()
            val entries = json.getAsJsonArray("entries")?.mapNotNull { element ->
                val entry = element.asJsonObject
                val key = entry.get("key")?.asString ?: return@mapNotNull null
                ItemEntry(key, entry.getAsJsonArray("counts")?.map { it.asFloat }?.toFloatArray() ?: floatArrayOf())
            }.orEmpty()
            val all = json.get("all")?.asBoolean ?: false
            return ItemQuery(
                ItemLocation.of(json.get("location")?.asString),
                json.get("required")?.asInt ?: if (all) entries.size else 1,
                entries,
            )
        }
    }
}

/** How many of each item key sit in each location, gathered once per tick for every effect to share */
class ItemSnapshot(private val counts: Map<ItemLocation, Map<String, Int>>) {
    fun count(location: ItemLocation, key: String): Int = counts[location]?.get(key) ?: 0

    class Builder {
        private val counts = EnumMap<ItemLocation, HashMap<String, Int>>(ItemLocation::class.java)

        fun add(keys: List<String>, count: Int, vararg locations: ItemLocation) {
            for (location in locations) {
                val map = counts.getOrPut(location) { HashMap() }
                for (key in keys) map.merge(key, count, Int::plus)
            }
        }

        fun build(): ItemSnapshot = ItemSnapshot(counts)
    }

    companion object {
        val EMPTY = ItemSnapshot(emptyMap())
    }
}
