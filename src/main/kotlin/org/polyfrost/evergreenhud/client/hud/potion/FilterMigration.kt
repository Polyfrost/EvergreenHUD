package org.polyfrost.evergreenhud.client.hud.potion

import org.polyfrost.evergreenhud.client.hud.potion.OverrideCondition.Type
import org.slf4j.LoggerFactory

private const val SHOW_EFFECTS = "showEffects"

/**
 * Turns the retired Ambient Effects, Effect Categories and Permanent Effects dropdowns into override groups
 * that switch Show Effect(s) off
 *
 * Every filter used to resolve on its own: the first matching group overriding it, else Global. So an earlier
 * group overriding a filter shields its effects from later ones, which a hide group cannot express. A readable
 * group per unticked box is tried first and kept only if it hides exactly what the filters did across every
 * effect, ambient state and duration kind; otherwise one exact group is built from that same comparison
 */
object FilterMigration {
    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Potion Effects")

    private enum class Filter(val field: String, val type: Type, val values: List<String>, val labels: List<String>) {
        AMBIENT_EFFECTS("ambientFilter", Type.AMBIENT, listOf(AMBIENT, NON_AMBIENT), listOf("ambient", "non-ambient")),
        EFFECT_CATEGORIES(
            "categoryFilter", Type.CATEGORY,
            listOf(CATEGORY_BENEFICIAL, CATEGORY_NEUTRAL, CATEGORY_HARMFUL), listOf("beneficial", "neutral", "harmful"),
        ),
        PERMANENT_EFFECTS("permanentEffects", Type.DURATION, listOf(PERMANENT, FINITE), listOf("permanent", "finite"));

        fun boxes(settings: EffectComponentSettings): BooleanArray = when (this) {
            AMBIENT_EFFECTS -> settings.ambientFilter
            EFFECT_CATEGORIES -> settings.categoryFilter
            PERMANENT_EFFECTS -> settings.permanentEffects
        }

        fun valueOf(facts: EffectFacts): String = when (this) {
            AMBIENT_EFFECTS -> if (facts.ambient) AMBIENT else NON_AMBIENT
            EFFECT_CATEGORIES -> facts.category
            PERMANENT_EFFECTS -> if (facts.permanent) PERMANENT else FINITE
        }

        fun shows(settings: EffectComponentSettings, facts: EffectFacts): Boolean =
            boxes(settings).getOrElse(values.indexOf(valueOf(facts))) { true }
    }

    /**
     * The whole group list once migrated: the new hide groups, then [groups] minus any left overriding nothing,
     * which were only ever there for the filters
     */
    fun run(groups: List<OverrideGroup>, global: EffectComponentSettings, catalog: List<EffectCatalog.Entry>): List<OverrideGroup> {
        val added = migrate(groups, global, catalog)
        clear(groups, global)
        return added + groups.filter { it.settings.overriddenOptions.isNotEmpty() }
    }

    /** The groups to put in front of [groups], empty when no box was unticked anywhere */
    fun migrate(groups: List<OverrideGroup>, global: EffectComponentSettings, catalog: List<EffectCatalog.Entry>): List<OverrideGroup> {
        val space = factSpace(catalog)
        val categories = catalog.map { it.category }.toSet()

        val readable = mutableListOf<OverrideGroup>()
        for (filter in Filter.entries) {
            val present = filter.values.filter { filter != Filter.EFFECT_CATEGORIES || it in categories }
            for (group in groups) {
                if (filter.field !in group.settings.overriddenOptions) continue
                hideGroupFor(filter, present, group.settings)?.let { hidden -> fromGroup(group, filter, present, hidden)?.let(readable::add) }
            }
            hideGroupFor(filter, present, global)?.let { readable += fromGlobal(filter, present, it) }
        }
        if (readable.isEmpty()) return emptyList()

        if (space.all { visible(it, readable + groups, global) == legacyVisible(it, groups, global) }) return readable

        val exact = exactGroup(space, groups, global, catalog)
        val result = listOfNotNull(exact) + readable.filterNot { it.enabled }
        if (!space.all { visible(it, result + groups, global) == legacyVisible(it, groups, global) }) {
            LOGGER.error("Migrated potion effect filters do not hide exactly the same effects as before")
        }
        return result
    }

    /** Drops the retired filters from every group and puts their values back to showing everything */
    fun clear(groups: List<OverrideGroup>, global: EffectComponentSettings) {
        for (settings in groups.map { it.settings } + global) {
            settings.overriddenOptions = settings.overriddenOptions.filter { it !in EffectComponentSettings.RETIRED_FIELDS }.toTypedArray()
            for (field in EffectComponentSettings.RETIRED_FIELDS) settings.copyField(field, EffectComponentSettings.DEFAULTS)
        }
    }

    fun legacyVisible(facts: EffectFacts, groups: List<OverrideGroup>, global: EffectComponentSettings): Boolean {
        fun layer(field: String) = groups.firstOrNull { field in it.settings.overriddenOptions && it.matches(facts) }?.settings ?: global
        if (!layer(SHOW_EFFECTS).showEffects) return false
        return Filter.entries.all { it.shows(layer(it.field), facts) }
    }

    fun visible(facts: EffectFacts, groups: List<OverrideGroup>, global: EffectComponentSettings): Boolean =
        (groups.firstOrNull { SHOW_EFFECTS in it.settings.overriddenOptions && it.matches(facts) }?.settings ?: global).showEffects

    fun factSpace(catalog: List<EffectCatalog.Entry>): List<EffectFacts> = catalog.flatMap { entry ->
        listOf(true, false).flatMap { ambient ->
            listOf(true, false).map { permanent -> EffectFacts(entry.id, entry.category, ambient, permanent) }
        }
    }

    private fun hideGroupFor(filter: Filter, present: List<String>, settings: EffectComponentSettings): List<String>? {
        val boxes = filter.boxes(settings)
        return present.filter { !boxes.getOrElse(filter.values.indexOf(it)) { true } }.ifEmpty { null }
    }

    private fun label(filter: Filter, hidden: List<String>): String =
        hidden.joinToString("/") { filter.labels[filter.values.indexOf(it)] }

    /** Null when every present value is hidden, as then the filter needs no condition of its own */
    private fun condition(filter: Filter, present: List<String>, hidden: List<String>): OverrideCondition? =
        if (hidden.size == present.size) null else OverrideCondition(filter.type, hidden)

    private fun inverse(filter: Filter, present: List<String>, hidden: List<String>): OverrideCondition =
        if (filter.type.choices != null) OverrideCondition(filter.type, present - hidden.toSet())
        else OverrideCondition(filter.type, hidden, negated = true)

    private fun hide(name: String, enabled: Boolean, match: Match, rules: List<OverrideRule>) = OverrideGroup(
        name = name,
        enabled = enabled,
        match = match,
        rules = rules,
        settings = EffectComponentSettings().also {
            it.showEffects = false
            it.overriddenOptions = arrayOf(SHOW_EFFECTS)
        },
    )

    private fun fromGlobal(filter: Filter, present: List<String>, hidden: List<String>): OverrideGroup {
        val condition = condition(filter, present, hidden)
        val rules = if (condition != null) listOf(OverrideRule(listOf(condition))) else everything()
        return hide("Hide ${label(filter, hidden)} effects", enabled = true, Match.ANY, rules)
    }

    private fun fromGroup(group: OverrideGroup, filter: Filter, present: List<String>, hidden: List<String>): OverrideGroup? {
        val active = group.rules.filter { it.isActive }
        if (active.isEmpty()) return null
        val condition = condition(filter, present, hidden)
        val name = "${group.name}: hide ${label(filter, hidden)} effects"
        return when (group.match) {
            Match.ANY -> hide(name, group.enabled, Match.ANY, active.map { rule ->
                OverrideRule(rule.conditions.filter { it.isActive }.map { it.copy() } + listOfNotNull(condition), rule.anyOf.map { it.copy() })
            })
            // NOR of the group's rules and "not hidden" is the group matching while the value is hidden
            Match.NONE -> hide(
                name, group.enabled, Match.NONE,
                active.map { it.copy() } + listOfNotNull(condition?.let { OverrideRule(listOf(inverse(filter, present, hidden))) }),
            )
        }
    }

    private fun everything(): List<OverrideRule> =
        listOf(AMBIENT, NON_AMBIENT).map { OverrideRule(listOf(OverrideCondition(Type.AMBIENT, listOf(it)))) }

    private data class Combo(val ambient: Boolean, val permanent: Boolean)

    /** Hides exactly what the filters hid on top of what Show Effect(s) already hides, merged where it can be */
    private fun exactGroup(
        space: List<EffectFacts>,
        groups: List<OverrideGroup>,
        global: EffectComponentSettings,
        catalog: List<EffectCatalog.Entry>,
    ): OverrideGroup? {
        val combosById = space
            .filter { !legacyVisible(it, groups, global) && visible(it, groups, global) }
            .groupBy({ it.id!! }, { Combo(it.ambient, it.permanent) })
            .mapValues { it.value.toSet() }
        if (combosById.isEmpty()) return null

        val rules = combosById.entries.groupBy({ it.value }, { it.key }).flatMap { (combos, ids) ->
            val terms = cover(combos)
            describeIds(ids.toSet(), catalog).flatMap { part ->
                terms.flatMap { term ->
                    val conditions = part.map { it.copy() } + term
                    if (conditions.isEmpty()) everything() else listOf(OverrideRule(conditions))
                }
            }
        }
        return hide("Hidden by old filters", enabled = true, Match.ANY, rules)
    }

    /**
     * Each entry is one rule's worth of conditions that together pick out [ids]: whole categories where every
     * effect in them is hidden, a category minus its few exceptions where most are, and any leftovers by name
     */
    private fun describeIds(ids: Set<String>, catalog: List<EffectCatalog.Entry>): List<List<OverrideCondition>> {
        val byCategory = Filter.EFFECT_CATEGORIES.values
            .mapNotNull { category -> catalog.filter { it.category == category }.map { it.id }.takeIf { it.isNotEmpty() }?.let { category to it } }
            .toMap()
        val full = byCategory.filterValues { ids.containsAll(it) }.keys
        if (full.size == byCategory.size) return listOf(emptyList())

        val parts = mutableListOf<List<OverrideCondition>>()
        if (full.isNotEmpty()) parts += listOf(OverrideCondition(Type.CATEGORY, full.toList()))
        var leftover = ids - full.flatMap { byCategory.getValue(it) }.toSet()
        for ((category, members) in byCategory) {
            if (category in full) continue
            val hit = members.filter { it in leftover }
            if (hit.size * 2 <= members.size) continue
            parts += listOf(
                OverrideCondition(Type.CATEGORY, listOf(category)),
                OverrideCondition(Type.EFFECT, (members - hit.toSet()).sorted(), negated = true),
            )
            leftover = leftover - hit.toSet()
        }
        if (leftover.isNotEmpty()) parts += listOf(OverrideCondition(Type.EFFECT, leftover.sorted()))
        return parts
    }

    /** The fewest Ambient and Duration conditions, possibly overlapping, whose rules cover exactly [combos] */
    private fun cover(combos: Set<Combo>): List<List<OverrideCondition>> {
        fun ambient(value: Boolean) = OverrideCondition(Type.AMBIENT, listOf(if (value) AMBIENT else NON_AMBIENT))
        fun duration(value: Boolean) = OverrideCondition(Type.DURATION, listOf(if (value) PERMANENT else FINITE))
        val cells = listOf(true, false).flatMap { a -> listOf(true, false).map { p -> Combo(a, p) } }
        val terms = buildList<Pair<() -> List<OverrideCondition>, Set<Combo>>> {
            add({ emptyList<OverrideCondition>() } to cells.toSet())
            for (value in listOf(true, false)) {
                add({ listOf(ambient(value)) } to cells.filter { it.ambient == value }.toSet())
                add({ listOf(duration(value)) } to cells.filter { it.permanent == value }.toSet())
            }
            for (cell in cells) add({ listOf(ambient(cell.ambient), duration(cell.permanent)) } to setOf(cell))
        }.filter { combos.containsAll(it.second) }

        val best = (1 until (1 shl terms.size))
            .map { mask -> terms.filterIndexed { i, _ -> mask and (1 shl i) != 0 } }
            .filter { picked -> picked.flatMap { it.second }.toSet() == combos }
            .minBy { it.size }
        return best.map { it.first() }
    }
}
