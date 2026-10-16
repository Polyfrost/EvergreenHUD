package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.polyfrost.oneconfig.utils.v1.JsonUtils
import org.slf4j.LoggerFactory

private const val FORMAT_VERSION = 1

val CATEGORY_ORDER = buildList {
    add(CATEGORY_BENEFICIAL)
    //? if > 1.8.9
    add(CATEGORY_NEUTRAL)
    add(CATEGORY_HARMFUL)
}

/** [first] and [second] name the two directions, where list keys pick what comes first or last instead */
enum class SortKey(val key: String, val title: String, val first: String, val second: String) {
    DURATION("duration", "Duration", "Shortest first", "Longest first"),
    NAME("name", "Name", "A to Z", "Z to A"),
    AMPLIFIER("amplifier", "Amplifier", "Lowest first", "Highest first"),
    AMBIENT("ambient", "Ambient", "Ambient first", "Non-ambient first"),
    PERMANENT("permanent", "Permanent", "Permanent first", "Finite first"),
    PERCENT_LEFT("percent_left", "Percent Left", "Lowest first", "Highest first"),
    ELAPSED("elapsed", "Elapsed", "Shortest first", "Longest first"),
    STARTED_AT("started_at", "Started At", "Shortest first", "Longest first"),
    CATEGORY("category", "Category", "First", "Last"),
    EFFECTS("effects", "Effects", "First", "Last"),
    TIME_RANGES("time_ranges", "Time Left Ranges", "First", "Last"),
    AMPLIFIER_RANGES("amplifier_ranges", "Amplifier Ranges", "First", "Last"),
    PERCENT_RANGES("percent_ranges", "Percent Left Ranges", "First", "Last"),
    ELAPSED_RANGES("elapsed_ranges", "Elapsed Ranges", "First", "Last"),
    STARTED_RANGES("started_ranges", "Started At Ranges", "First", "Last");

    val isList: Boolean get() = this == CATEGORY || this == EFFECTS

    val isRanges: Boolean get() = this in setOf(TIME_RANGES, AMPLIFIER_RANGES, PERCENT_RANGES, ELAPSED_RANGES, STARTED_RANGES)

    val tiered: Boolean get() = isList || isRanges

    companion object {
        fun of(key: String?): SortKey? = entries.firstOrNull { it.key == key }
    }
}

class SortSubject(val facts: EffectFacts, val name: String, val duration: Int, val amplifier: Int)

class SortRule(
    val key: SortKey,
    flipped: Boolean = false,
    values: List<String> = if (key == SortKey.CATEGORY) CATEGORY_ORDER else emptyList(),
    only: OverrideRule? = null,
    ranges: FloatArray = floatArrayOf(),
) {
    var flipped by mutableStateOf(flipped)
    var values by mutableStateOf(values)
    var ranges by mutableStateOf(ranges)
    var only by mutableStateOf(only)

    val uid: Int = nextUid++

    fun applies(facts: EffectFacts): Boolean = only?.takeIf { it.isActive }?.matches(facts) ?: true

    fun comparator(): Comparator<SortSubject> {
        fun tier(index: Int, count: Int): Int = index.takeIf { it >= 0 } ?: if (flipped) -1 else count
        fun rank(value: String?): Int = tier(values.indexOf(value), values.size)
        fun rank(value: Float): Int = tier((0 until ranges.size / 2).indexOfFirst { value in ranges[it * 2]..ranges[it * 2 + 1] }, ranges.size / 2)
        fun rankTimed(facts: EffectFacts, value: Float): Int = rank(if (facts.permanent) Float.NaN else value)
        fun timed(value: (EffectFacts) -> Float): Comparator<SortSubject> {
            val byValue = compareBy<SortSubject> { value(it.facts) }
            return compareBy<SortSubject> { it.facts.permanent }.then(if (flipped) byValue.reversed() else byValue)
        }
        val base: Comparator<SortSubject> = when (key) {
            SortKey.DURATION -> compareBy { it.duration }
            SortKey.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            SortKey.AMPLIFIER -> compareBy { it.amplifier }
            SortKey.AMBIENT -> compareBy { !it.facts.ambient }
            SortKey.PERMANENT -> compareBy { !it.facts.permanent }
            SortKey.CATEGORY -> return compareBy { rank(it.facts.category) }
            SortKey.EFFECTS -> return compareBy { rank(it.facts.id) }
            SortKey.PERCENT_LEFT -> return timed { it.percentLeft }
            SortKey.ELAPSED -> return timed { it.elapsed }
            SortKey.STARTED_AT -> return timed { it.startSeconds }
            SortKey.TIME_RANGES -> return compareBy { rankTimed(it.facts, it.facts.seconds) }
            SortKey.AMPLIFIER_RANGES -> return compareBy { rank(it.facts.amplifier.toFloat()) }
            SortKey.PERCENT_RANGES -> return compareBy { rankTimed(it.facts, it.facts.percentLeft) }
            SortKey.ELAPSED_RANGES -> return compareBy { rankTimed(it.facts, it.facts.elapsed) }
            SortKey.STARTED_RANGES -> return compareBy { rankTimed(it.facts, it.facts.startSeconds) }
        }
        return if (flipped) base.reversed() else base
    }

    fun copy(): SortRule = SortRule(key, flipped, values, only?.copy(), ranges.copyOf())
}

/**
 * Applied last to first as stable sorts, so the first rule decides and ties fall through to the next, ending in
 * the game's order. A rule limited by [SortRule.only] reorders just the effects it applies to, within the places
 * they already hold
 */
fun <T> sortBy(
    items: List<T>,
    rules: List<SortRule>,
    reverseTies: Boolean = false,
    reverseAll: Boolean = false,
    subject: (T) -> SortSubject,
): List<T> {
    val start = if (reverseTies) items.asReversed() else items
    if (rules.isEmpty() || items.size < 2) return if (reverseAll) start.asReversed() else start
    val sorted = start.map { it to subject(it) }.toMutableList()
    for (rule in rules.asReversed()) {
        val slots = sorted.indices.filter { rule.applies(sorted[it].second.facts) }
        if (slots.size < 2) continue
        val comparator = rule.comparator()
        val ordered = slots.map { sorted[it] }.sortedWith { a, b -> comparator.compare(a.second, b.second) }
        slots.forEachIndexed { i, slot -> sorted[slot] = ordered[i] }
    }
    val result = sorted.map { it.first }
    return if (reverseAll) result.asReversed() else result
}

class SortRulesModel {
    var rules: MutableList<SortRule> = mutableStateListOf()
        private set

    var reverseTies by mutableStateOf(false)
    var reverseAll by mutableStateOf(false)

    var revision by mutableStateOf(0)
        private set

    var persist: (String) -> Unit = {}

    private var encoded = ""

    fun sync(data: String) {
        if (data === encoded) return
        if (data != encoded) {
            val decoded = decode(data)
            rules = mutableStateListOf<SortRule>().apply { addAll(decoded.rules) }
            reverseTies = decoded.reverseTies
            reverseAll = decoded.reverseAll
            revision++
        }
        encoded = data
    }

    fun replace(next: List<SortRule>) {
        rules = mutableStateListOf<SortRule>().apply { addAll(next) }
        changed()
    }

    fun changed() {
        encoded = encode(Sorting(rules, reverseTies, reverseAll))
        revision++
        persist(encoded)
    }

    fun <T> sort(items: List<T>, subject: (T) -> SortSubject): List<T> = sortBy(items, rules, reverseTies, reverseAll, subject)

    fun deepCopy(): SortRulesModel = SortRulesModel().also { it.sync(encode(Sorting(rules, reverseTies, reverseAll))) }

    class Sorting(val rules: List<SortRule>, val reverseTies: Boolean = false, val reverseAll: Boolean = false)

    companion object {
        private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Potion Effects")

        fun encode(sorting: Sorting): String {
            if (sorting.rules.isEmpty() && !sorting.reverseTies && !sorting.reverseAll) return ""
            val root = JsonObject()
            root.addProperty("version", FORMAT_VERSION)
            root.addProperty("reverseTies", sorting.reverseTies)
            root.addProperty("reverseAll", sorting.reverseAll)
            root.add("rules", JsonArray().also { array ->
                for (rule in sorting.rules) array.add(JsonObject().also { json ->
                    json.addProperty("key", rule.key.key)
                    json.addProperty("flipped", rule.flipped)
                    if (rule.key.isList) json.add("values", JsonArray().also { values -> rule.values.forEach { values.add(JsonPrimitive(it)) } })
                    if (rule.key.isRanges) json.add("ranges", JsonArray().also { ranges -> rule.ranges.forEach { ranges.add(JsonPrimitive(it)) } })
                    rule.only?.let { only -> json.add("only", OverrideGroupsModel.encodeConditions(only.conditions)) }
                })
            })
            return root.toString()
        }

        fun decode(data: String): Sorting {
            if (data.isBlank()) return Sorting(emptyList())
            return try {
                val root = JsonUtils.parse(data).asJsonObject
                val rules = root.getAsJsonArray("rules")?.mapNotNull { element ->
                    val json = element.asJsonObject
                    val key = SortKey.of(json.get("key")?.asString) ?: return@mapNotNull null
                    SortRule(
                        key = key,
                        flipped = json.get("flipped")?.asBoolean ?: false,
                        values = if (key.isList) json.getAsJsonArray("values")?.map { it.asString }.orEmpty() else emptyList(),
                        ranges = json.getAsJsonArray("ranges")?.map { it.asFloat }?.toFloatArray() ?: floatArrayOf(),
                        only = json.getAsJsonArray("only")?.let { OverrideRule(OverrideGroupsModel.decodeConditions(it)) }?.takeIf { it.conditions.isNotEmpty() },
                    )
                }.orEmpty()
                Sorting(rules, root.get("reverseTies")?.asBoolean ?: false, root.get("reverseAll")?.asBoolean ?: false)
            } catch (e: Exception) {
                LOGGER.error("Failed to read the potion effect sort rules, they will be reset", e)
                Sorting(emptyList())
            }
        }

        /** Runs of category or effect entries in the old checklist become one list rule each */
        fun migrate(sorting: Array<String>, idForTitle: (String) -> String?): List<SortRule> {
            val rules = mutableListOf<SortRule>()
            fun appendTo(key: SortKey, value: String) {
                val last = rules.lastOrNull()
                if (last != null && last.key == key) {
                    if (value !in last.values) last.values = last.values + value
                } else {
                    rules += SortRule(key, values = listOf(value))
                }
            }
            for (entry in sorting) {
                when (entry) {
                    "Name" -> rules += SortRule(SortKey.NAME)
                    "Duration" -> rules += SortRule(SortKey.DURATION)
                    "Amplifier" -> rules += SortRule(SortKey.AMPLIFIER, flipped = true)
                    "Ambient Effects" -> rules += SortRule(SortKey.AMBIENT, flipped = true)
                    "Beneficial Effects" -> appendTo(SortKey.CATEGORY, CATEGORY_BENEFICIAL)
                    "Neutral Effects" -> if (CATEGORY_NEUTRAL in CATEGORY_ORDER) appendTo(SortKey.CATEGORY, CATEGORY_NEUTRAL)
                    "Harmful Effects" -> appendTo(SortKey.CATEGORY, CATEGORY_HARMFUL)
                    else -> idForTitle(entry)?.let { appendTo(SortKey.EFFECTS, it) }
                }
            }
            return rules
        }
    }
}
