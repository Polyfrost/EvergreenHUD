package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
//? if > 1.8.9 {
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
//?} else {
/*import net.minecraft.client.resource.language.I18n
import net.minecraft.entity.living.effect.StatusEffect
import net.minecraft.entity.living.effect.StatusEffectInstance as MobEffectInstance
*///?}
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import org.polyfrost.compose.composables.PolyBox
import org.polyfrost.compose.composables.PolyCanvas
import org.polyfrost.compose.composables.PolyColumn
import org.polyfrost.compose.composables.PolyMcText
import org.polyfrost.compose.composables.PolyModifier
import org.polyfrost.compose.composables.PolyRow
import org.polyfrost.compose.composables.PolyText
import org.polyfrost.compose.composables.align
import org.polyfrost.compose.composables.padding
import org.polyfrost.compose.composables.size
import org.polyfrost.compose.composables.width
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.mc.McFontQueue.measureWidth
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyFont

import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hooks.VanillaHudCompat
import org.polyfrost.evergreenhud.client.hud.item.potionIdOf
import org.polyfrost.evergreenhud.client.utils.applyCase
import org.polyfrost.oneconfig.api.config.v1.Node
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.collect.impl.OneConfigCollector
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.event.v1.invoke.EventHandler
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.Section
import org.polyfrost.oneconfig.api.hud.v1.Weight

import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.internal.ui.components.effect.EffectCatalog as OneConfigEffects
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.polyfrost.oneconfig.utils.v1.MHUtils.setAccessible
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.cos

private const val ICON = 18f
private const val ICON_GAP = 3f
private const val LINE_GAP = 1f
private const val FONT_SIZE = 8f

const val ROMAN = 0

private const val AMBIENT_EFFECTS = "Ambient Effects"
private const val BENEFICIAL_EFFECTS = "Beneficial Effects"
private const val NEUTRAL_EFFECTS = "Neutral Effects"
private const val HARMFUL_EFFECTS = "Harmful Effects"

private const val INFINITE = "**:**"

private const val BOLD = 1
private const val ITALIC = 2
private const val UNDERLINE = 4

private const val MC_ITALIC_SHEAR = 0.25f

private val COLOR_CODE = Regex("§[0-9a-fA-FrR]")

private const val BLINK_PERIOD = 1000L
private const val BLINK_MIN_FADE = 0.25f

private const val EDITOR_DIM_ALPHA = 0.5f

private const val MODE_AUTO = 0
private const val MODE_FULL = 1
private const val MODE_SINGLE_LINE = 2
private const val MODE_STACKED = 3

private const val DIRECTION_AUTO = 0
private const val DIRECTION_VERTICAL = 1
private const val DIRECTION_HORIZONTAL = 2

private const val SECTION_SWITCH_DISTANCE = 4f

private val SECTION_GRID = arrayOf(
    arrayOf(Section.TopLeft, Section.TopCenter, Section.TopRight),
    arrayOf(Section.CenterLeft, Section.Center, Section.CenterRight),
    arrayOf(Section.BottomLeft, Section.BottomCenter, Section.BottomRight),
)

internal const val PER_OPTION_OVERRIDES = 1
internal const val RANGE_LISTS = 2
private const val OVERRIDE_GROUPS = 3
private const val FILTER_GROUPS = 4

private const val HOTBAR_SLOTS = 9
private const val MAIN_INVENTORY_SLOTS = 36

class PotionEffectsHud : Hud(
    id = "potion_effects.json",
    title = "Potion Effects",
    category = Category.PLAYER,
) {
    companion object {
        private val ROMAN_VALUES = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        private val ROMAN_NUMERALS =
            arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")

        private fun charWidth(scale: Float): Float =
            measureWidth?.invoke(" ", scale) ?: (FONT_SIZE * scale * 0.5f)

        private val EXAMPLES = listOf(
            Example("speed", 1200, 1),
            Example("strength", 140, 0),
            Example("slowness", 840, 0),
            Example("glowing", 500, 0),
            Example("regeneration", 60, 1, ambient = true),
        )

        private val EXAMPLE_EFFECTS: List<MobEffectInstance> by lazy {
            EXAMPLES.mapNotNull { example ->
                //? if > 1.8.9 {
                val registry = BuiltInRegistries.MOB_EFFECT
                val effect = registry.firstOrNull { registry.getKey(it)?.path == example.id } ?: return@mapNotNull null
                MobEffectInstance(registry.wrapAsHolder(effect), example.ticks, example.amplifier, example.ambient, true)
                //?} else {
                /*val effect = StatusEffect.get(example.id) ?: return@mapNotNull null
                MobEffectInstance(effect.id, example.ticks, example.amplifier, example.ambient, true)
                *///?}
            }
        }

        private val EFFECT_STRIPPED_FIELDS = setOf("categoryFilter")

        private val HIDDEN = Supplier { Property.Display.HIDDEN }

        private val PROPERTY_CONDITIONS = Property::class.java.getDeclaredField("conditions").apply { setAccessible() }
        private val PROPERTY_CALLBACKS = Property::class.java.getDeclaredField("callbacks").apply { setAccessible() }

        fun roman(value: Int): String {
            if (value < 1 || value > 3999) return value.toString()
            val builder = StringBuilder()
            var left = value
            for (i in ROMAN_VALUES.indices) {
                while (left >= ROMAN_VALUES[i]) {
                    builder.append(ROMAN_NUMERALS[i])
                    left -= ROMAN_VALUES[i]
                }
            }
            return builder.toString()
        }

        fun formatDuration(ticks: Int): String {
            val total = ticks / 20
            val seconds = total % 60
            val minutes = total / 60 % 60
            val hours = total / 3600
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
            else "%d:%02d".format(minutes, seconds)
        }
    }

    private class Example(val id: String, val ticks: Int, val amplifier: Int, val ambient: Boolean = false)

    private data class Row(
        val icon: PolyBitmap?,
        val name: String?,
        val duration: String?,
        val blinking: Boolean,
        val iconBlink: Boolean,
        val nameBlink: Boolean,
        val nameColor: PolyColor,
        val nameStyle: Int,
        val durationBlink: Boolean,
        val durationColor: PolyColor,
        val durationStyle: Int,
        val dimmed: Boolean,
    )

    private class RowStyle(
        val iconAlpha: Float,
        val nameFade: Float,
        val durationFade: Float,
        val nameColor: PolyColor,
        val durationColor: PolyColor,
    )

    private var categoryScopes = PerCategoryEffectSettings()
    private var effectScopes = PerEffectSettings()

    @Include
    var sorting = emptyArray<String>()

    @SortRulesOption(
        title = "Sorting",
        description = "The first rule decides the order, ties fall through to the next.",
        category = "Sorting",
        subcategory = "Sorting",
    )
    var sortRulesData = ""

    private var sortRules = SortRulesModel()

    @Include
    var overrides = emptyArray<String>()

    @OverrideGroupsOption(
        title = "Override Groups",
        description = "Change how matching effects look.",
        category = "Effects",
        subcategory = "Effects",
    )
    var overrideGroupsData = ""

    private var overrideGroups = OverrideGroupsModel()

    private var effectStarts = EffectStarts()

    private var itemSnapshot = ItemSnapshot.EMPTY

    @Include
    var reversed = false

    @Include
    var sortingVersion = 0

    @Slider(title = "Spacing", min = 0F, max = 10F, step = 1F, subcategory = "Dimensions")
    var spacing = 2f

    @RadioButton(title = "Text Alignment", options = ["Auto", "Left", "Center", "Right"], subcategory = "Dimensions")
    var textAlignment = 0

    @RadioButton(title = "Layout Mode", options = ["Auto", "Full", "Single Line", "Stacked"], subcategory = "Dimensions")
    var layoutMode = MODE_AUTO

    @RadioButton(title = "List Direction", options = ["Auto", "Vertical", "Horizontal"], subcategory = "Dimensions")
    var listDirection = DIRECTION_AUTO

    @Switch(
        title = "Hide Vanilla Status Effects",
        description = "Turns off VanillaHUD's own status effects element, so it does not draw on top of this one.",
    )
    var hideVanillaEffects = false

    @Include
    var overrideOptionsVersion = 0

    @Include
    var formattingInitialized = false

    private var rows = mutableStateOf<List<Row>>(emptyList())

    private data class Layout(val spacing: Float, val horizontal: Boolean, val mode: Int, val align: PolyAlign)

    private var layoutState = mutableStateOf(Layout(spacing, false, MODE_FULL, PolyAlign.Left))

    private var layoutSection = mutableStateOf<Section?>(null)
    private val activeSection: Section get() = layoutSection.value ?: section

    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var settledX = 0f
    private var settledY = 0f
    private var holdingSection = false
    private var heldAtX = 0f
    private var heldAtY = 0f

    private var blinkFade = mutableFloatStateOf(1f)

    private var fontRevision = mutableIntStateOf(FontManager.revision)

    private var tickHandler: EventHandler<*>? = null

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun setup() {
        super.setup()
        overrideGroups.sync(overrideGroupsData)
        overrideGroups.persist = { overrideGroupsData = it }
        sortRules.sync(sortRulesData)
        sortRules.persist = { sortRulesData = it }
        if (isReal) {
            hideIf("hideVanillaEffects") { !VanillaHudCompat.isPresent }
            tickHandler = eventHandler { _: TickEvent.End ->
                if (VanillaHudCompat.isPresent) VanillaHudCompat.hideStatusEffects(hideVanillaEffects && !hidden)
                trackStarts()
                updateAndRecalculate()
            }

            if (overrideOptionsVersion < RANGE_LISTS) migrateRangeLists()
            if (overrideOptionsVersion < OVERRIDE_GROUPS) {
                val scopes = legacyScopes()
                migrateLegacyEffectKeys()
                if (overrideOptionsVersion < PER_OPTION_OVERRIDES) migrateLegacyOverrides(scopes)
                migrateToOverrideGroups(scopes)
                overrideOptionsVersion = OVERRIDE_GROUPS
            }
            if (overrideOptionsVersion < FILTER_GROUPS) {
                overrideGroups.replace(FilterMigration.run(overrideGroups.groups, categoryScopes.global, EffectCatalog.ENTRIES))
                overrideOptionsVersion = FILTER_GROUPS
            }
            getProperty("overrideOptionsVersion").removeMetadata("default")
            stripLegacyScopes()

            setupGlobalFormatting()

            val groupsProp = Property.recast<String>(getProperty("overrideGroupsData"))
            groupsProp.addMetadata(OVERRIDE_GROUPS_MODEL, overrideGroups)
            groupsProp.addMetadata(OVERRIDE_GROUPS_INHERIT, InheritOption { settings, field ->
                settings.copyField(field, resolvedGlobal())
                if (field == EffectComponentSettings::caseType.name) settings.caseType = caseType
            })
            overrideGroups.persist = { groupsProp.set(it) }

            if (sortingVersion < 1) {
                val titles = EffectCatalog.ENTRIES.associate { it.title to it.id }
                sortRules.reverseAll = reversed
                sortRules.replace(SortRulesModel.migrate(sorting, titles::get))
                sorting = emptyArray()
                reversed = false
                sortingVersion = 1
            }
            val sortProp = Property.recast<String>(getProperty("sortRulesData"))
            sortProp.addMetadata(SORT_RULES_MODEL, sortRules)
            sortRules.persist = { sortProp.set(it) }
        }
    }

    override fun updateFrequency(): Long = if (isReal) Long.MAX_VALUE else -1L

    override fun remove() {
        tickHandler?.unregister()
        tickHandler = null
        if (VanillaHudCompat.isPresent) VanillaHudCompat.hideStatusEffects(false)
    }

    private fun claim(prop: Property<*>): Property<Any?> {
        @Suppress("UNCHECKED_CAST")
        (PROPERTY_CONDITIONS.get(prop) as MutableList<Any>?)?.removeIf { it is OwnedCondition && it.owner !== this }
        @Suppress("UNCHECKED_CAST")
        (PROPERTY_CALLBACKS.get(prop) as MutableList<Any>?)?.removeIf { it is OwnedCallback<*> && it.owner !== this }
        return Property.recast(prop)
    }

    /** The fixed override slots used before override groups, kept only to migrate them */
    private class OverrideScope(
        val key: String,
        val title: String,
        val stripped: Set<String>,
        val category: String?,
        val isEffect: Boolean,
        val effectId: String? = null,
        val settings: () -> EffectComponentSettings,
    ) {
        fun overlaps(other: OverrideScope): Boolean = when {
            category == null || other.category == null -> true
            isEffect && other.isEffect -> false
            else -> category == other.category
        }

        fun condition(): OverrideCondition = when {
            isEffect -> OverrideCondition(OverrideCondition.Type.EFFECT, listOf(effectId ?: key))
            key == "ambient" -> OverrideCondition(OverrideCondition.Type.AMBIENT, listOf(AMBIENT))
            else -> OverrideCondition(OverrideCondition.Type.CATEGORY, listOf(key))
        }
    }

    private fun setupGlobalFormatting() {
        val global = { categoryScopes.global }
        val fieldProps = EffectComponentSettings.FORMATTING_FIELDS.map { field ->
            claim(getProperty("global.$field")).also { prop ->
                prop.addDisplayCondition(OwnedCondition(this) { !global().separateFormatting })
            }
        }
        claim(getProperty("global.$SEPARATE_FORMATTING")).addCallback(OwnedCallback(this) { separate ->
            if (separate == true && !formattingInitialized) {
                formattingInitialized = true
                for (prop in fieldProps) prop.set(hudFormatting())
            }
            for (prop in fieldProps) prop.revaluateDisplay()
        })
    }

    private fun hudFormatting(): BooleanArray = booleanArrayOf(textBold, textItalic, textUnderline)

    private fun resolvedGlobal(): EffectComponentSettings {
        val global = categoryScopes.global
        if (global.separateFormatting) return global
        return EffectComponentSettings().also {
            it.copyFrom(global)
            it.nameFormatting = hudFormatting()
            it.durationFormatting = hudFormatting()
        }
    }

    private class OwnedCondition(val owner: PotionEffectsHud, private val hidden: () -> Boolean) : Supplier<Property.Display> {
        override fun get(): Property.Display = if (hidden()) Property.Display.HIDDEN else Property.Display.SHOWN
    }

    private class OwnedCallback<T>(val owner: PotionEffectsHud, private val action: (T) -> Unit) : Predicate<T> {
        override fun test(t: T): Boolean {
            action(t)
            return false
        }
    }

    private fun legacyScopes(): List<OverrideScope> = buildList {
        for (scope in categoryScopes.scopeDefs) {
            if (!scope.isOverride) continue
            val key = scope.key
            val category = key.takeIf { scope.title != AMBIENT_EFFECTS }
            add(OverrideScope(key, scope.title, scope.strippedFields, category, isEffect = false) { categoryScopes[key] })
        }
        for (entry in EffectCatalog.ENTRIES) {
            val path = entry.path
            add(OverrideScope(path, entry.title, EFFECT_STRIPPED_FIELDS, entry.category, isEffect = true, effectId = entry.id) { effectScopes.byPath.getValue(path) })
        }
    }

    /**
     * Turns every enabled legacy override into a group in the same order, then keeps any disabled one that
     * still holds changes as a disabled group so nothing is lost
     */
    private fun migrateToOverrideGroups(scopes: List<OverrideScope>) {
        val byTitle = scopes.associateBy { it.title }
        val enabled = overrides.mapNotNull { byTitle[it] }.distinct()
        val disabled = scopes.filter { it !in enabled && it.settings().overriddenOptions.isNotEmpty() }

        fun group(scope: OverrideScope, on: Boolean) = OverrideGroup(
            name = scope.title,
            enabled = on,
            rules = listOf(OverrideRule(listOf(scope.condition()))),
            settings = EffectComponentSettings().also { it.copyFrom(scope.settings()) },
        )

        val migrated = enabled.map { group(it, on = true) } + disabled.map { group(it, on = false) }
        if (migrated.isNotEmpty()) overrideGroups.replace(overrideGroups.groups + migrated)
        overrides = emptyArray()
    }

    /** The legacy slots only exist in the tree so old configs bind to them, and must not be saved or shown */
    private fun stripLegacyScopes() {
        val tree = tree ?: return
        for (scope in categoryScopes.scopeDefs) if (scope.isOverride) stripProperty(tree, scope.key)
        for (entry in EffectCatalog.ENTRIES) stripProperty(tree, entry.path)
        for (scope in categoryScopes.scopeDefs) if (scope.isOverride) categoryScopes[scope.key].copyFrom(EffectComponentSettings.DEFAULTS)
        for (settings in effectScopes.byPath.values) settings.copyFrom(EffectComponentSettings.DEFAULTS)
    }

    private fun migrateLegacyEffectKeys() {
        val tree = tree ?: return
        for ((legacy, path) in EffectCatalog.LEGACY_IDS) {
            val old = tree.get(legacy) as? Tree ?: continue
            val target = tree.get(path) as? Tree
            val settings = effectScopes.byPath[path]
            if (target != null && settings != null && settings.isDefault()) {
                target.overwrite(old, false, false, null)
                settings.normalizeLegacyRanges()
            }
            stripProperty(tree, legacy)
        }
    }

    private fun migrateRangeLists() {
        val all = categoryScopes.scopeDefs.map { it.settings } + effectScopes.byPath.values
        for (settings in all) settings.migrateRanges(overrideOptionsVersion)

        val known = EffectCatalog.ENTRIES.mapTo(HashSet()) { it.path } + categoryScopes.scopeDefs.map { it.key }
        tree?.onAll { id, node ->
            if (node !is Tree || id in known) return@onAll
            for (field in listOf("durationRange", "amplifierRange")) {
                val prop = node.getProp(field) ?: continue
                val value = prop.get()
                val numbers = when (value) {
                    is FloatArray -> value.toList()
                    is DoubleArray -> value.map { it.toFloat() }
                    is Array<*> -> value.mapNotNull { (it as? Number)?.toFloat() }
                    is List<*> -> value.mapNotNull { (it as? Number)?.toFloat() }
                    else -> continue
                }
                if (numbers.toFloatArray().isLegacyOff()) Property.recast<Any?>(prop).set(floatArrayOf())
            }
        }
    }

    private fun migrateLegacyOverrides(scopes: List<OverrideScope>) {
        val global = categoryScopes.global
        val byTitle = scopes.associateBy { it.title }
        val enabled = overrides.mapNotNull { byTitle[it] }
        for (scope in scopes) {
            val settings = scope.settings()
            if (settings.overriddenOptions.isNotEmpty()) continue
            val priority = enabled.indexOf(scope)
            val fallbacks = if (priority < 0) emptyList() else {
                listOf(global) + enabled.drop(priority + 1).filter(scope::overlaps).map { it.settings() }
            }
            settings.overriddenOptions = EffectComponentSettings.LEAF_FIELDS
                .filter { field ->
                    field !in scope.stripped &&
                        (!settings.matches(field, EffectComponentSettings.DEFAULTS) || fallbacks.any { !settings.matches(field, it) })
                }
                .toTypedArray()
        }
    }

    override fun canMergeBackground(): Boolean = true

    override fun update(): Boolean {
        layoutState.value = Layout(spacing, direction(), layoutMode(), autoTextAlign())
        val fontsChanged = fontRevision.intValue != FontManager.revision
        if (fontsChanged) fontRevision.intValue = FontManager.revision
        val next = buildRows()
        val rowsChanged = next != rows.value
        if (rowsChanged) rows.value = next
        val sectionChanged = settleLayoutSection()
        return rowsChanged || sectionChanged || fontsChanged
    }

    override fun onEditorDragStart() {
        super.onEditorDragStart()
        dragging = true
        dragX = x
        dragY = y
    }

    override fun onEditorDragEnd() {
        super.onEditorDragEnd()
        dragging = false
    }

    override fun updateRelativeX(absX: Float) {
        super.updateRelativeX(absX)
        dragX = absX
    }

    override fun updateRelativeY(absY: Float) {
        super.updateRelativeY(absY)
        dragY = absY
    }

    private fun settleLayoutSection(): Boolean {
        val target = section
        val held = layoutSection.value

        if (held == target || held == null || !HudManager.isEditing || selfAnchorPoint != HudAnchor.Auto) {
            if (!dragging) {
                dragX = x
                dragY = y
            }
            if (holdingSection && dragX == heldAtX && dragY == heldAtY && held == target) return false
            holdingSection = false
            settledX = dragX
            settledY = dragY
            if (held == target) return false
            layoutSection.value = target
            return true
        }

        val row = towards(rowOf(held), rowOf(target), dragY - settledY)
        val column = towards(columnOf(held), columnOf(target), dragX - settledX)
        val next = SECTION_GRID[row][column]

        if (next != target) {
            section = next
            x = dragX
            y = dragY
            holdingSection = true
            heldAtX = dragX
            heldAtY = dragY
        }

        if (next == held) return false
        if (next == target) holdingSection = false
        layoutSection.value = next
        settledX = dragX
        settledY = dragY
        return true
    }

    private fun towards(from: Int, to: Int, moved: Float): Int = when {
        to > from && moved >= SECTION_SWITCH_DISTANCE -> to
        to < from && moved <= -SECTION_SWITCH_DISTANCE -> to
        else -> from
    }

    private fun rowOf(sec: Section): Int = SECTION_GRID.indexOfFirst { sec in it }

    private fun columnOf(sec: Section): Int = SECTION_GRID[rowOf(sec)].indexOf(sec)

    private fun buildRows(): List<Row> {
        val editing = HudManager.isEditing || !isReal
        itemSnapshot = if (isReal) snapshotItems() else ItemSnapshot.EMPTY

        //? if > 1.8.9 {
        val active = if (!isReal) emptyList() else mc.player?.activeEffects?.toList().orEmpty()
        //?} else
        //val active = if (!isReal) emptyList() else mc.player?.activeEffects?.filter { StatusEffect.BY_ID.getOrNull(it.id)?.hasIcon() == true }.orEmpty()
        val effects = active.ifEmpty { if (editing) EXAMPLE_EFFECTS else return emptyList() }

        val visible = if (editing) effects else effects.filter(::shouldDisplayEffect)
        if (visible.isEmpty()) return emptyList()

        sortRules.sync(sortRulesData)
        val ordered = sortRules.sort(visible, ::sortSubject)
        return ordered.map { effect -> row(effect, dimmed = editing && !shouldDisplayEffect(effect)) }
    }

    private fun sortSubject(effect: MobEffectInstance): SortSubject {
        val duration = if (effect.isInfiniteDuration) Int.MAX_VALUE else effect.duration
        return SortSubject(factsFor(effect), displayNameOf(effect), duration, amplifierOf(effect))
    }

    private fun amplifierOf(effect: MobEffectInstance): Int {
        //? if > 1.8.9 {
        return effect.amplifier
        //?} else
        //return effect.amplifier and 0xFF
    }

    private fun localizedNameOf(effect: MobEffectInstance): String {
        //? if > 1.8.9 {
        return effect.effect.value().displayName.string
        //?} else
        //return I18n.translate(StatusEffect.BY_ID[effect.id].translationKey)
    }

    private fun displayNameOf(effect: MobEffectInstance): String =
        resolveName(valuesFor(effect), localizedNameOf(effect))

    private fun resolveName(values: EffectComponentValues, name: String): String =
        values.customName.ifBlank { EFFECT_PLACEHOLDER }.replace(EFFECT_PLACEHOLDER, name)

    private fun shouldDisplayEffect(effect: MobEffectInstance): Boolean {
        val values = valuesFor(effect)

        if (!values.showEffects) return false

        if (!effect.isInfiniteDuration && !values.durationRange.allowsInRanges(effect.duration / 20f)) return false

        if (!values.amplifierRange.allowsInRanges(amplifierOf(effect).toFloat())) return false

        return true
    }

    private fun row(effect: MobEffectInstance, dimmed: Boolean = false): Row {
        //? if > 1.8.9 {
        val mobEffect = effect.effect.value()
        return row(
            BuiltInRegistries.MOB_EFFECT.getKey(mobEffect)?.toString(),
            localizedNameOf(effect),
            effect.duration,
            amplifierOf(effect),
            effect.isInfiniteDuration,
            valuesFor(effect),
            dimmed,
        )
        //?} else {
        /*val statusEffect = StatusEffect.BY_ID[effect.id]
        return row(
            EffectCatalog.idOf(effect.id),
            localizedNameOf(effect),
            effect.duration,
            amplifierOf(effect),
            effect.isInfiniteDuration,
            valuesFor(effect),
            dimmed,
        )
        *///?}
    }

    private fun row(id: String?, name: String, ticks: Int, amplifier: Int, infinite: Boolean, values: EffectComponentValues, dimmed: Boolean = false): Row {
        val level = amplifier + 1
        val amplifierText = if (values.showAmplifier && level > 1) {
            if (values.amplifierStyle == ROMAN) roman(level) else level.toString()
        } else null

        val displayName = resolveName(values, name)
        val title = when {
            values.nameEnabled && amplifierText != null -> "$displayName $amplifierText"
            values.nameEnabled -> displayName
            amplifierText != null -> amplifierText
            else -> null
        }?.let { applyCase(it, values.caseType) }

        val time = when {
            !values.durationEnabled -> null
            infinite -> INFINITE
            else -> formatDuration(ticks)
        }
        return Row(
            icon = if (values.iconEnabled && id != null) OneConfigEffects.icon(id) else null,
            name = title,
            duration = time,
            blinking = isBlinking(ticks, infinite, values.blinkThreshold),
            iconBlink = values.iconBlink,
            nameBlink = values.nameBlink,
            nameColor = values.nameColor,
            nameStyle = styleFlags(values.nameFormatting),
            durationBlink = values.durationBlink,
            durationColor = values.durationColor,
            durationStyle = styleFlags(values.durationFormatting),
            dimmed = dimmed,
        )
    }

    private fun styleFlags(formatting: BooleanArray): Int {
        var flags = 0
        if (formatting.getOrElse(0) { false }) flags = flags or BOLD
        if (formatting.getOrElse(1) { false }) flags = flags or ITALIC
        if (formatting.getOrElse(2) { false }) flags = flags or UNDERLINE
        return flags
    }

    private fun isBlinking(ticks: Int, infinite: Boolean, blinkThreshold: Float): Boolean =
        !infinite && blinkThreshold > 0f && ticks <= blinkThreshold * 20f

    private fun currentBlinkFade(): Float {
        val phase = (System.currentTimeMillis() % BLINK_PERIOD).toFloat() / BLINK_PERIOD
        return BLINK_MIN_FADE + (1f - BLINK_MIN_FADE) * ((cos(phase * 2f * PI.toFloat()) + 1f) / 2f)
    }

    private fun textAlign(mode: Int): PolyAlign = when (mode) {
        2 -> PolyAlign.Center
        3 -> PolyAlign.Right
        else -> PolyAlign.Left
    }

    private fun autoTextAlign(): PolyAlign {
        if (textAlignment != 0) return textAlign(textAlignment)

        if (selfAnchorPoint != HudAnchor.Auto) {
            return horizontalFrom(selfAnchorPoint)
        }

        return when (activeSection) {
            Section.TopLeft, Section.CenterLeft, Section.BottomLeft -> PolyAlign.Left
            Section.TopCenter, Section.Center, Section.BottomCenter -> PolyAlign.Center
            Section.TopRight, Section.CenterRight, Section.BottomRight -> PolyAlign.Right
        }
    }

    private fun horizontalFrom(anchor: HudAnchor): PolyAlign = when (anchor) {
        HudAnchor.TopLeft, HudAnchor.Left, HudAnchor.BottomLeft -> PolyAlign.Left
        HudAnchor.Top, HudAnchor.Center, HudAnchor.Bottom -> PolyAlign.Center
        HudAnchor.TopRight, HudAnchor.Right, HudAnchor.BottomRight -> PolyAlign.Right
        HudAnchor.Auto -> PolyAlign.Left
    }

    private fun direction(): Boolean {
        if (listDirection != DIRECTION_AUTO) return listDirection == DIRECTION_HORIZONTAL

        if (selfAnchorPoint != HudAnchor.Auto) {
            return when (selfAnchorPoint) {
                HudAnchor.Top, HudAnchor.Center, HudAnchor.Bottom -> true
                else -> false
            }
        }

        return when (activeSection) {
            Section.TopCenter, Section.Center, Section.BottomCenter -> true
            else -> false
        }
    }

    private fun layoutMode(): Int {
        if (layoutMode != MODE_AUTO) return layoutMode

        if (selfAnchorPoint != HudAnchor.Auto) {
            return when (selfAnchorPoint) {
                HudAnchor.Top, HudAnchor.Center, HudAnchor.Bottom -> MODE_STACKED
                HudAnchor.Left, HudAnchor.Right -> MODE_FULL
                HudAnchor.TopLeft, HudAnchor.TopRight, HudAnchor.BottomLeft, HudAnchor.BottomRight -> MODE_FULL
                HudAnchor.Auto -> MODE_FULL
            }
        }

        return when (activeSection) {
            Section.TopCenter, Section.Center, Section.BottomCenter -> MODE_STACKED
            Section.CenterLeft, Section.CenterRight -> MODE_FULL
            Section.TopLeft, Section.TopRight, Section.BottomLeft, Section.BottomRight -> MODE_FULL
        }
    }

    private fun isBottomAligned(): Boolean {
        if (selfAnchorPoint != HudAnchor.Auto) {
            return when (selfAnchorPoint) {
                HudAnchor.BottomLeft, HudAnchor.Bottom, HudAnchor.BottomRight -> true
                else -> false
            }
        }
        return when (activeSection) {
            Section.BottomLeft, Section.BottomCenter, Section.BottomRight -> true
            else -> false
        }
    }

    private fun styleFor(row: Row): RowStyle {
        val dimAlpha = if (row.dimmed) EDITOR_DIM_ALPHA else 1f
        val fade = if (row.blinking) blinkFade.floatValue else 1f
        val iconAlpha = (if (row.iconBlink) fade else 1f) * dimAlpha
        val nameFade = (if (row.nameBlink) fade else 1f) * dimAlpha
        val durationFade = (if (row.durationBlink) fade else 1f) * dimAlpha
        return RowStyle(
            iconAlpha = iconAlpha,
            nameFade = nameFade,
            durationFade = durationFade,
            nameColor = PolyColor(faded(row.nameColor.argb, nameFade), row.nameColor.chroma, row.nameColor.chromaSpeed),
            durationColor = PolyColor(faded(row.durationColor.argb, durationFade), row.durationColor.chroma, row.durationColor.chromaSpeed),
        )
    }

    @Composable
    override fun Content() {
        val layout = layoutState.value
        val list = rows.value
        if (list.isEmpty()) return
        val scale = textScale.coerceAtLeast(0.01f)

        if (list.any { it.blinking }) {
            LaunchedEffect(Unit) {
                while (true) withFrameMillis { blinkFade.floatValue = currentBlinkFade() }
            }
        }

        val fonts = fontRevision.intValue
        val maxRowWidth = remember(list, scale, font, textWeight, layout.mode, fonts) {
            val mode = layout.mode
            val hasAnyIcon = list.any { it.icon != null }
            val iconWidth = ICON * scale
            val iconGap = ICON_GAP * scale

            list.maxOf { row ->
                val nameWidth = row.name?.let { textWidth(it, row.nameStyle, scale) } ?: 0f
                val durationWidth = row.duration?.let { textWidth(it, row.durationStyle, scale) } ?: 0f
                when (mode) {
                    MODE_STACKED -> maxOf(if (row.icon != null) iconWidth else 0f, nameWidth, durationWidth)
                    MODE_SINGLE_LINE -> {
                        val gapWidth = if (row.name != null && row.duration != null) charWidth(scale) else 0f
                        val textWidth = nameWidth + gapWidth + durationWidth
                        (if (hasAnyIcon) iconWidth + iconGap else 0f) + textWidth
                    }
                    else -> (if (hasAnyIcon) iconWidth + iconGap else 0f) + maxOf(nameWidth, durationWidth)
                }
            }
        }

        val horizontal = layout.horizontal
        val modifier = hudBackground().padding(padLeft, padTop, padRight, padBottom)

        PolyBox(modifier = modifier) {
            if (horizontal) {
                PolyRow(gap = layout.spacing * scale) {
                    for (row in list) Effect(row, scale, maxRowWidth, layout)
                }
            } else {
                PolyColumn(gap = layout.spacing * scale) {
                    for (row in list) Effect(row, scale, maxRowWidth, layout)
                }
            }
        }
    }

    @Composable
    private fun Effect(row: Row, scale: Float, maxRowWidth: Float, layout: Layout) {
        val mode = layout.mode
        if (mode == MODE_STACKED) {
            StackedEffect(row, scale, maxRowWidth)
            return
        }

        val style = styleFor(row)
        val align = layout.align

        val iconBlock: @Composable () -> Unit = {
            if (row.icon != null) Icon(row.icon, ICON * scale, style.iconAlpha)
        }

        PolyBox(modifier = PolyModifier.width(maxRowWidth)) {
            PolyRow(gap = ICON_GAP * scale, modifier = PolyModifier.align(align)) {
                if (align != PolyAlign.Right) iconBlock()

                if (row.name != null || row.duration != null) {
                    if (mode == MODE_SINGLE_LINE) CompactText(row, scale, style, align)
                    else FullText(row, scale, style, align)
                }

                if (align == PolyAlign.Right) iconBlock()
            }
        }
    }

    @Composable
    private fun CompactText(row: Row, scale: Float, style: RowStyle, align: PolyAlign) {
        PolyRow(gap = charWidth(scale), modifier = PolyModifier.align(PolyAlign.Center)) {
            if (align == PolyAlign.Right) {
                if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor, row.durationStyle)
                if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor, row.nameStyle)
            } else {
                if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor, row.nameStyle)
                if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor, row.durationStyle)
            }
        }
    }

    @Composable
    private fun FullText(row: Row, scale: Float, style: RowStyle, align: PolyAlign) {
        PolyColumn(gap = LINE_GAP * scale, modifier = PolyModifier.align(PolyAlign.Center)) {
            if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor, row.nameStyle, modifier = PolyModifier.align(align))
            if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor, row.durationStyle, modifier = PolyModifier.align(align))
        }
    }

    @Composable
    private fun StackedEffect(row: Row, scale: Float, maxWidth: Float) {
        val style = styleFor(row)
        val reversed = isBottomAligned()

        val iconBlock: @Composable () -> Unit = {
            if (row.icon != null) Icon(row.icon, ICON * scale, style.iconAlpha, modifier = PolyModifier.align(PolyAlign.Center))
        }
        val nameBlock: @Composable () -> Unit = {
            if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor, row.nameStyle, modifier = PolyModifier.align(PolyAlign.Center))
        }
        val durationBlock: @Composable () -> Unit = {
            if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor, row.durationStyle, modifier = PolyModifier.align(PolyAlign.Center))
        }

        PolyBox(modifier = PolyModifier.width(maxWidth)) {
            PolyColumn(gap = LINE_GAP * scale, modifier = PolyModifier.align(PolyAlign.Center)) {
                if (reversed) {
                    durationBlock(); nameBlock(); iconBlock()
                } else {
                    iconBlock(); nameBlock(); durationBlock()
                }
            }
        }
    }

    @Composable
    private fun Icon(icon: PolyBitmap, size: Float, alpha: Float, modifier: PolyModifier = PolyModifier) {
        PolyCanvas(modifier.size(size, size)) { x, y, w, h ->
            val tint = PolyColor.WHITE.withAlpha((255f * alpha).toInt().coerceIn(0, 255))
            val pixelsPerUnit = Platform.screen().let { if (it.guiWidth() > 0) it.viewportWidth().toFloat() / it.guiWidth() else 1f }
            val downscaled = w * pixelsPerUnit * (if (isReal) effectiveScale else 1f) < icon.width
            val sampling = if (downscaled) FilterQuality.Low else FilterQuality.None
            image(icon, x, y, w, h, 0f, 0f, 1f, 1f, tint, sampling)
        }
    }

    private fun mcText(text: String, style: Int): String {
        if ((style and (BOLD or ITALIC or UNDERLINE)) == 0) return text
        val codes = buildString(6) {
            if ((style and BOLD) != 0) append("§l")
            if ((style and ITALIC) != 0) append("§o")
            if ((style and UNDERLINE) != 0) append("§n")
        }
        return text.split('\n').joinToString("\n") { codes + COLOR_CODE.replace(it) { match -> match.value + codes } }
    }

    private fun poppinsFont(style: Int): String {
        val base = when {
            (style and BOLD) != 0 || textWeight == Weight.Bold -> "poppins-bold"
            textWeight == Weight.Thin -> "poppins-thin"
            textWeight == Weight.Medium -> "poppins-medium"
            textWeight == Weight.Black -> "poppins-black"
            else -> "poppins"
        }
        return if ((style and ITALIC) != 0) "$base-italic" else base
    }

    private fun mcItalicOffset(style: Int, scale: Float): Float =
        if ((style and ITALIC) != 0) MC_ITALIC_SHEAR * McFontQueue.measureTextHeight(scale) / 2f else 0f

    private fun textWidth(text: String, style: Int, scale: Float): Float =
        if (font == Font.Minecraft) text.split('\n').maxOf { McFontQueue.measureTextWidth(mcText(it, style), scale) } + mcItalicOffset(style, scale)
        else PolyFont(poppinsFont(style), FONT_SIZE * scale).measureTextWidth(text)

    @Composable
    private fun Line(text: String, scale: Float, fade: Float, color: PolyColor, style: Int, modifier: PolyModifier = PolyModifier) {
        val underline = (style and UNDERLINE) != 0
        if (font == Font.Minecraft) {
            if ((style and ITALIC) == 0) {
                PolyMcText(mcText(text, style), color, shadow = showShadow, scale = scale, modifier = modifier)
                return
            }

            fontRevision.intValue
            val lines = text.split('\n').map { mcText(it, style) }
            val lineHeight = McFontQueue.measureTextHeight(scale)
            val width = lines.maxOf { McFontQueue.measureTextWidth(it, scale) }
            val totalHeight = McFontQueue.measureBlockHeight(lines.size, scale)
            PolyCanvas(modifier.size(width, totalHeight), live = color.chroma) { x, y, _, _ ->
                val argb = color.argb
                for ((i, formatted) in lines.withIndex()) {
                    val top = y + i * lineHeight
                    save()
                    try {
                        translate(x, top)
                        mcText(formatted, 0f, 0f, argb, showShadow, scale)
                    } finally {
                        restore()
                    }
                }
            }
        } else {
            val fontName = poppinsFont(style)
            val shadow = PolyColor(faded(shadowColor, fade), shadowChroma, shadowChromaSpeed)
            if (!underline) {
                PolyText(
                    text,
                    color,
                    fontSize = FONT_SIZE * scale,
                    shadow = showShadow,
                    shadowColor = shadow,
                    shadowOffset = shadowOffsetX,
                    font = fontName,
                    modifier = modifier,
                )
                return
            }

            val polyFont = PolyFont(fontName, FONT_SIZE * scale)
            val metrics = polyFont.metrics
            val width = polyFont.measureTextWidth(text)
            val height = metrics.descent - metrics.ascent
            val position = metrics.underlinePosition
            val thickness = metrics.underlineThickness
            PolyCanvas(modifier.size(width, height)) { x, y, w, _ ->
                val baseline = y - metrics.ascent
                if (showShadow) {
                    val sx = x + shadowOffsetX
                    val sy = baseline + shadowOffsetX
                    text(text, sx, sy, shadow, polyFont)
                    line(sx, sy + position, sx + w, sy + position, shadow, thickness)
                }
                text(text, x, baseline, color, polyFont)
                line(x, baseline + position, x + w, baseline + position, color, thickness)
            }
        }
    }

    private fun faded(color: Int, fade: Float): Int {
        if (fade >= 1f) return color
        val alpha = ((color ushr 24 and 0xFF) * fade).toInt().coerceIn(0, 255)
        return (color and 0xFFFFFF) or (alpha shl 24)
    }

    override fun clone(): Hud = (super.clone() as PotionEffectsHud).also {
        it.sorting = sorting.copyOf()
        it.sortRules = sortRules.deepCopy()
        it.overrides = overrides.copyOf()
        it.overrideGroups = overrideGroups.deepCopy()
        it.layoutState = mutableStateOf(layoutState.value)
        it.blinkFade = mutableFloatStateOf(1f)
        it.fontRevision = mutableIntStateOf(FontManager.revision)
        it.tickHandler = null
        it.effectStarts = EffectStarts()

        it.categoryScopes = categoryScopes.deepCopy()
        it.effectScopes = effectScopes.deepCopy()

        it.rows = mutableStateOf(emptyList())
        it.layoutSection = mutableStateOf(null)
    }

    private class PerCategoryEffectSettings {
        val global = EffectComponentSettings()
        val beneficial = EffectComponentSettings()
        val neutral = EffectComponentSettings()
        val harmful = EffectComponentSettings()
        val ambient = EffectComponentSettings()

        val scopeDefs: List<ScopeDef> get() = listOf(
            ScopeDef("global", "Global", global, strippedFields = setOf(OVERRIDDEN_OPTIONS, "caseType"), isOverride = false),
            ScopeDef("beneficial", BENEFICIAL_EFFECTS, beneficial, strippedFields = setOf("categoryFilter")),
            ScopeDef("neutral", NEUTRAL_EFFECTS, neutral, strippedFields = setOf("categoryFilter")),
            ScopeDef("harmful", HARMFUL_EFFECTS, harmful, strippedFields = setOf("categoryFilter")),
            ScopeDef("ambient", AMBIENT_EFFECTS, ambient, strippedFields = setOf("ambientFilter", "categoryFilter", "permanentEffects", "emittingParticleEffects")),
        )

        operator fun get(key: String): EffectComponentSettings = when (key) {
            "beneficial" -> beneficial
            "neutral" -> neutral
            "harmful" -> harmful
            "ambient" -> ambient
            else -> global
        }

        fun copyFrom(other: PerCategoryEffectSettings) {
            global.copyFrom(other.global)
            beneficial.copyFrom(other.beneficial)
            neutral.copyFrom(other.neutral)
            harmful.copyFrom(other.harmful)
            ambient.copyFrom(other.ambient)
        }

        fun deepCopy(): PerCategoryEffectSettings = PerCategoryEffectSettings().also { it.copyFrom(this) }
    }

    override fun addToSerialized(tree: Tree) {
        super.addToSerialized(tree)
        val collector = OneConfigCollector()

        for (scope in categoryScopes.scopeDefs) {
            val t = Tree.tree(scope.key)
            t.addMetadata(mapOf(
                "title" to scope.title, "description" to scope.description,
                "category" to "Effects", "subcategory" to "Effects",
                "collapsed" to (scope.key != "global")
            ))
            collector.handle(t, scope.settings, 0)
            for (field in scope.strippedFields) stripProperty(t, field)
            for (field in EffectComponentSettings.RETIRED_FIELDS) t.getProp(field)?.addDisplayCondition(HIDDEN)
            if (scope.isOverride) stripProperty(t, SEPARATE_FORMATTING)
            tree.put(t)
        }

        for (entryDef in EffectCatalog.ENTRIES) {
            val settings = effectScopes.byPath.getValue(entryDef.path)
            val t = Tree.tree(entryDef.path)
            t.addMetadata(mapOf(
                "title" to entryDef.title,
                "description" to "Overrides for the ${entryDef.title} effect.",
                "category" to "Effects", "subcategory" to "Effects",
                "collapsed" to true
            ))
            collector.handle(t, settings, 0)
            for (field in EFFECT_STRIPPED_FIELDS) stripProperty(t, field)
            stripProperty(t, SEPARATE_FORMATTING)
            tree.put(t)
        }
    }

    private fun idOf(effect: MobEffectInstance): String? {
        //? if > 1.8.9 {
        return BuiltInRegistries.MOB_EFFECT.getKey(effect.effect.value())?.toString()
        //?} else
        //return EffectCatalog.idOf(effect.id)
    }

    private fun factsFor(effect: MobEffectInstance): EffectFacts {
        //? if > 1.8.9 {
        val category = when (effect.effect.value().category) {
            MobEffectCategory.BENEFICIAL -> CATEGORY_BENEFICIAL
            MobEffectCategory.NEUTRAL -> CATEGORY_NEUTRAL
            MobEffectCategory.HARMFUL -> CATEGORY_HARMFUL
        }
        //?} else
        //val category = if (StatusEffect.BY_ID[effect.id].isHarmful) CATEGORY_HARMFUL else CATEGORY_BENEFICIAL

        val id = idOf(effect)
        val seconds = effect.duration / 20f
        val start = id?.let(effectStarts::startTicks)?.div(20f) ?: seconds
        return EffectFacts(id, category, effect.isAmbient, effect.isInfiniteDuration, seconds, amplifierOf(effect), start, itemSnapshot)
    }

    private fun snapshotItems(): ItemSnapshot {
        val player = mc.player ?: return ItemSnapshot.EMPTY
        val inventory = player.inventory
        val builder = ItemSnapshot.Builder()
        for (slot in 0 until MAIN_INVENTORY_SLOTS) {
            val stack = inventory.getItem(slot)
            val keys = itemKeys(stack) ?: continue
            if (slot < HOTBAR_SLOTS) builder.add(keys, stack.count, ItemLocation.HOTBAR, ItemLocation.INVENTORY)
            else builder.add(keys, stack.count, ItemLocation.INVENTORY)
        }
        val mainHand = player.mainHandItem
        itemKeys(mainHand)?.let { builder.add(it, mainHand.count, ItemLocation.MAIN_HAND, ItemLocation.EITHER_HAND) }
        //? if > 1.8.9 {
        val offhand = player.offhandItem
        itemKeys(offhand)?.let { builder.add(it, offhand.count, ItemLocation.OFFHAND, ItemLocation.EITHER_HAND, ItemLocation.INVENTORY) }
        //?}
        return builder.build()
    }

    private fun itemKeys(stack: ItemStack?): List<String>? {
        if (stack == null || stack.isEmpty) return null
        //~ if = 1.8.9 'BuiltInRegistries.ITEM' -> 'Item.REGISTRY'
        val id = BuiltInRegistries.ITEM.getKey(stack.item).toString()
        return listOfNotNull(id, potionIdOf(stack)?.let { itemKey(id, it) })
    }

    private fun trackStarts() {
        val effects = mc.player?.activeEffects ?: return effectStarts.clear()
        effectStarts.update(effects.mapNotNull { effect ->
            if (effect.isInfiniteDuration) null else idOf(effect)?.let { EffectStarts.Seen(it, effect.duration, amplifierOf(effect)) }
        })
    }

    private fun valuesFor(effect: MobEffectInstance): EffectComponentValues {
        overrideGroups.sync(overrideGroupsData)
        val facts = factsFor(effect)
        val layers = overrideGroups.groups.mapNotNull { group -> group.settings.takeIf { group.matches(facts) } }
        val global = GlobalEffectValues(categoryScopes.global, { caseType }, ::hudFormatting)
        return if (layers.isEmpty()) global else LayeredEffectValues(layers, global)
    }

    private class PerEffectSettings {
        val byPath: Map<String, EffectComponentSettings> =
            EffectCatalog.ENTRIES.associate { it.path to EffectComponentSettings() }

        fun deepCopy(): PerEffectSettings = PerEffectSettings().also { copy ->
            for ((path, settings) in byPath) {
                copy.byPath[path]?.copyFrom(settings)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun stripProperty(t: Tree, key: String) {
        val theMapField = Tree::class.java.getDeclaredField("theMap")
        theMapField.setAccessible()
        val map = theMapField.get(t) as LinkedHashMap<String, Node>
        map.remove(key)
    }
}
