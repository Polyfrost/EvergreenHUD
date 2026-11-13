package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.polyfrost.evergreenhud.client.hud.potion.OverrideCondition.Type
import org.polyfrost.oneconfig.internal.ui.components.effect.normalizeEffectIds
import org.polyfrost.oneconfig.utils.v1.JsonUtils
import org.slf4j.LoggerFactory

const val CATEGORY_BENEFICIAL = "beneficial"
const val CATEGORY_NEUTRAL = "neutral"
const val CATEGORY_HARMFUL = "harmful"
const val AMBIENT = "ambient"
const val NON_AMBIENT = "non_ambient"
const val PERMANENT = "permanent"
const val FINITE = "finite"

private val AMBIENT_VALUES = listOf(AMBIENT, NON_AMBIENT)
private val DURATION_VALUES = listOf(PERMANENT, FINITE)

private const val FORMAT_VERSION = 2

internal var nextUid = 0

/** [id] is the namespaced registry ID such as `minecraft:speed` */
class EffectFacts(
    val id: String?,
    val category: String,
    val ambient: Boolean,
    val permanent: Boolean = false,
    val seconds: Float = 0f,
    val amplifier: Int = 0,
    val startSeconds: Float = seconds,
    val items: ItemSnapshot = ItemSnapshot.EMPTY,
) {
    val elapsed: Float get() = startSeconds - seconds

    val percentLeft: Float get() = if (startSeconds > 0f) seconds / startSeconds * 100f else 100f
}

enum class Match(val key: String, val title: String) {
    ANY("any", "Any rule"),
    NONE("none", "No rule");

    companion object {
        fun of(key: String?): Match = entries.firstOrNull { it.key == key } ?: ANY
    }
}

class OverrideCondition(
    val type: Type,
    values: List<String> = emptyList(),
    negated: Boolean = false,
    ranges: FloatArray = floatArrayOf(),
    val items: ItemQuery = ItemQuery(),
) {
    var values by mutableStateOf(values)
    var negated by mutableStateOf(negated)
    var ranges by mutableStateOf(ranges)

    init {
        type.choices?.let { choices ->
            val fold = this.negated && !type.hasRanges
            val picked = if (fold) choices - this.values.toSet() else this.values
            this.values = listOf(picked.singleOrNull() ?: choices.first())
            if (!type.hasRanges) this.negated = false
        }
    }

    /**
     * [choices] is set for types that are a single pick between two values, which never negate. Duration's
     * negation instead means outside its time ranges, which only apply while it is Finite
     */
    enum class Type(val key: String, val title: String, val choices: List<String>? = null) {
        EFFECT("effect", "Effect"),
        CATEGORY("category", "Category"),
        AMBIENT("ambient", "Ambient", AMBIENT_VALUES),
        DURATION("duration", "Duration", DURATION_VALUES),
        AMPLIFIER("amplifier", "Amplifier"),
        ELAPSED("elapsed", "Elapsed"),
        STARTED_AT("started_at", "Started At"),
        PERCENT_LEFT("percent_left", "Percent Left"),
        ITEMS("items", "Items");

        /** Measured from when the effect was first seen, so permanent effects never match these */
        val isTimed: Boolean get() = this == ELAPSED || this == STARTED_AT || this == PERCENT_LEFT

        val hasRanges: Boolean get() = this == DURATION || this == AMPLIFIER || isTimed

        companion object {
            fun of(key: String?): Type? = entries.firstOrNull { it.key == key }
        }
    }

    val isActive: Boolean get() = when {
        type == Type.ITEMS -> items.entries.isNotEmpty()
        type == Type.AMPLIFIER || type.isTimed -> ranges.isNotEmpty()
        else -> values.isNotEmpty()
    }

    val isFinite: Boolean get() = type == Type.DURATION && FINITE in values

    fun test(facts: EffectFacts): Boolean {
        val hit = when (type) {
            Type.EFFECT -> facts.id in values
            Type.CATEGORY -> facts.category in values
            Type.AMBIENT -> (if (facts.ambient) AMBIENT else NON_AMBIENT) in values
            Type.DURATION -> return if (facts.permanent) PERMANENT in values
            else FINITE in values && (ranges.isEmpty() || ranges.allowsInRanges(facts.seconds) != negated)
            Type.AMPLIFIER -> ranges.allowsInRanges(facts.amplifier.toFloat())
            Type.ELAPSED -> return !facts.permanent && ranges.allowsInRanges(facts.elapsed) != negated
            Type.STARTED_AT -> return !facts.permanent && ranges.allowsInRanges(facts.startSeconds) != negated
            Type.PERCENT_LEFT -> return !facts.permanent && ranges.allowsInRanges(facts.percentLeft) != negated
            Type.ITEMS -> items.matches(facts.items)
        }
        return hit != negated
    }

    fun copy(): OverrideCondition = OverrideCondition(type, values, negated, ranges.copyOf(), items.copy())
}

class OverrideRule(conditions: List<OverrideCondition> = emptyList(), anyOf: List<AnyOf> = emptyList()) {
    val conditions: MutableList<OverrideCondition> = mutableStateListOf<OverrideCondition>().apply { addAll(conditions) }
    val anyOf: MutableList<AnyOf> = mutableStateListOf<AnyOf>().apply { addAll(anyOf) }

    val uid: Int = nextUid++

    val isActive: Boolean get() = conditions.any { it.isActive } || anyOf.any { it.isActive }

    fun matches(facts: EffectFacts): Boolean =
        conditions.filter { it.isActive }.all { it.test(facts) } && anyOf.filter { it.isActive }.all { it.matches(facts) }

    /**
     * One condition of each type, and no Category beside an "Effect is", as within a rule those only repeat or
     * contradict each other. "Effect is not" stays allowed beside a Category, as in "beneficial except Speed".
     * [alongside] holds the conditions that always apply with these, such as the rule around an alternative
     */
    fun canAdd(type: Type, alongside: List<OverrideCondition> = emptyList()): Boolean {
        val all = conditions + anyOf.flatMap { it.conditions } + alongside
        return when {
            all.any { it.type == type } -> false
            type.isTimed -> all.none { it.type == Type.DURATION && PERMANENT in it.values }
            type == Type.CATEGORY -> all.none { it.type == Type.EFFECT && !it.negated }
            else -> true
        }
    }

    fun locksEffectNegation(alongside: List<OverrideCondition> = emptyList()): Boolean =
        (conditions + anyOf.flatMap { it.conditions } + alongside).any { it.type == Type.CATEGORY }

    fun add(type: Type, alongside: List<OverrideCondition> = emptyList()) {
        if (!canAdd(type, alongside)) return
        val finite = type == Type.DURATION && (conditions + anyOf.flatMap { it.conditions } + alongside).any { it.type.isTimed }
        conditions += OverrideCondition(
            type,
            values = if (finite) listOf(FINITE) else emptyList(),
            negated = type == Type.EFFECT && locksEffectNegation(alongside),
        )
    }

    /** What an alternative inside [block] is checked against: this rule's conditions and its other blocks */
    fun alongside(block: AnyOf): List<OverrideCondition> = conditions + anyOf.filter { it !== block }.flatMap { it.conditions }

    fun copy(): OverrideRule = OverrideRule(conditions.map { it.copy() }, anyOf.map { it.copy() })
}

/** Alternatives where any one matching is enough, each needing all of its own conditions */
class AnyOf(alternatives: List<OverrideRule> = emptyList()) {
    val alternatives: MutableList<OverrideRule> = mutableStateListOf<OverrideRule>().apply { addAll(alternatives) }

    val uid: Int = nextUid++

    val isActive: Boolean get() = alternatives.any { it.isActive }

    val conditions: List<OverrideCondition> get() = alternatives.flatMap { it.conditions }

    fun matches(facts: EffectFacts): Boolean = alternatives.filter { it.isActive }.any { it.matches(facts) }

    fun copy(): AnyOf = AnyOf(alternatives.map { it.copy() })
}

class OverrideGroup(
    name: String = "New Group",
    enabled: Boolean = true,
    match: Match = Match.ANY,
    rules: List<OverrideRule> = emptyList(),
    val settings: EffectComponentSettings = EffectComponentSettings(),
) {
    var name by mutableStateOf(name)
    var enabled by mutableStateOf(enabled)
    var match by mutableStateOf(match)
    val rules: MutableList<OverrideRule> = mutableStateListOf<OverrideRule>().apply { addAll(rules) }

    val uid: Int = nextUid++

    fun matches(facts: EffectFacts): Boolean {
        if (!enabled) return false
        val active = rules.filter { it.isActive }
        if (active.isEmpty()) return false
        return when (match) {
            Match.ANY -> active.any { it.matches(facts) }
            Match.NONE -> active.none { it.matches(facts) }
        }
    }

    fun copy(name: String = this.name): OverrideGroup = OverrideGroup(
        name,
        enabled,
        match,
        rules.map { it.copy() },
        EffectComponentSettings().also { it.copyFrom(settings) },
    )
}

class OverrideGroupsModel {
    var groups: MutableList<OverrideGroup> = mutableStateListOf()
        private set

    var revision by mutableStateOf(0)
        private set

    var persist: (String) -> Unit = {}

    private var encoded = ""

    fun sync(data: String) {
        if (data === encoded) return
        if (data != encoded) {
            groups = mutableStateListOf<OverrideGroup>().apply { addAll(decode(data)) }
            revision++
        }
        encoded = data
    }

    fun replace(next: List<OverrideGroup>) {
        groups = mutableStateListOf<OverrideGroup>().apply { addAll(next) }
        changed()
    }

    fun changed() {
        encoded = encode(groups)
        revision++
        persist(encoded)
    }

    fun deepCopy(): OverrideGroupsModel = OverrideGroupsModel().also { it.sync(encode(groups)) }

    companion object {
        private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Potion Effects")

        fun encode(groups: List<OverrideGroup>): String {
            if (groups.isEmpty()) return ""
            val root = JsonObject()
            root.addProperty("version", FORMAT_VERSION)
            root.add("groups", JsonArray().also { array ->
                for (group in groups) array.add(JsonObject().also { json ->
                    json.addProperty("name", group.name)
                    json.addProperty("enabled", group.enabled)
                    json.addProperty("match", group.match.key)
                    json.add("rules", JsonArray().also { rules ->
                        for (rule in group.rules) rules.add(encodeRule(rule))
                    })
                    json.add("settings", group.settings.toJson())
                })
            })
            return root.toString()
        }

        fun decode(data: String): MutableList<OverrideGroup> {
            if (data.isBlank()) return mutableListOf()
            return try {
                val root = JsonUtils.parse(data).asJsonObject
                root.getAsJsonArray("groups")?.mapTo(mutableListOf()) { element ->
                    val json = element.asJsonObject
                    val matchKey = json.get("match")?.asString
                    val decoded = json.getAsJsonArray("rules")
                        ?.map { decodeRule(it.asJsonObject) }
                        ?: legacyRules(matchKey, decodeConditions(json.getAsJsonArray("conditions")))
                    val rules = withShared(decoded, decodeConditions(json.getAsJsonArray("shared")))
                    OverrideGroup(
                        name = json.get("name")?.asString ?: "Group",
                        enabled = json.get("enabled")?.asBoolean ?: true,
                        match = Match.of(matchKey),
                        rules = rules.filter { it.conditions.isNotEmpty() || it.anyOf.isNotEmpty() },
                        settings = EffectComponentSettings().also { s -> json.getAsJsonObject("settings")?.let(s::readJson) },
                    )
                } ?: mutableListOf()
            } catch (e: Exception) {
                LOGGER.error("Failed to read the potion effect override groups, they will be reset", e)
                mutableListOf()
            }
        }

        private fun legacyRules(match: String?, conditions: List<OverrideCondition>): List<OverrideRule> =
            if (match == "all" || match == null) listOf(OverrideRule(conditions))
            else conditions.map { OverrideRule(listOf(it)) }

        /** Conditions a group once shared with every rule are folded into each, which matches the same effects */
        private fun withShared(rules: List<OverrideRule>, shared: List<OverrideCondition>): List<OverrideRule> = when {
            shared.isEmpty() -> rules
            rules.isEmpty() -> listOf(OverrideRule(shared))
            else -> rules.map { OverrideRule(shared.map { it.copy() } + it.conditions, it.anyOf) }
        }

        private fun encodeRule(rule: OverrideRule): JsonObject = JsonObject().also { r ->
            r.add("conditions", encodeConditions(rule.conditions))
            if (rule.anyOf.isNotEmpty()) r.add("anyOf", JsonArray().also { blocks ->
                for (block in rule.anyOf) blocks.add(JsonArray().also { alternatives ->
                    for (alternative in block.alternatives) alternatives.add(encodeConditions(alternative.conditions))
                })
            })
        }

        private fun decodeRule(json: JsonObject): OverrideRule = OverrideRule(
            decodeConditions(json.getAsJsonArray("conditions")),
            json.getAsJsonArray("anyOf")?.map { block ->
                AnyOf(block.asJsonArray.map { OverrideRule(decodeConditions(it.asJsonArray)) }.filter { it.conditions.isNotEmpty() })
            }?.filter { it.alternatives.isNotEmpty() }.orEmpty(),
        )

        internal fun encodeConditions(conditions: List<OverrideCondition>): JsonArray = JsonArray().also { array ->
            for (condition in conditions) array.add(JsonObject().also { c ->
                c.addProperty("type", condition.type.key)
                c.addProperty("negated", condition.negated)
                c.add("values", JsonArray().also { values -> condition.values.forEach { values.add(JsonPrimitive(it)) } })
                if (condition.type == Type.ITEMS) c.add("items", condition.items.toJson())
                if (condition.type.hasRanges) c.add("ranges", JsonArray().also { ranges -> condition.ranges.forEach { ranges.add(JsonPrimitive(it)) } })
            })
        }

        internal fun decodeConditions(array: JsonArray?): List<OverrideCondition> = array?.mapNotNull { c ->
            val condition = c.asJsonObject
            val key = condition.get("type")?.asString
            val type = (if (key == "time_left") Type.DURATION else Type.of(key)) ?: return@mapNotNull null
            val values = if (key == "time_left") listOf(FINITE) else condition.getAsJsonArray("values")?.map { it.asString }.orEmpty()
            OverrideCondition(
                type,
                if (type == Type.EFFECT) normalizeEffectIds(values) else values,
                condition.get("negated")?.asBoolean ?: false,
                condition.getAsJsonArray("ranges")?.map { it.asFloat }?.toFloatArray() ?: floatArrayOf(),
                if (type == Type.ITEMS) ItemQuery.fromJson(condition.getAsJsonObject("items")) else ItemQuery(),
            )
        }.orEmpty()
    }
}
