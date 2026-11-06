package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
//? if > 1.8.9 {
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
//? if < 1.21.11
//import net.minecraft.resources.ResourceLocation
//? if >= 1.21.11
import net.minecraft.resources.Identifier as ResourceLocation
import net.minecraft.server.packs.resources.ReloadableResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import org.polyfrost.oneconfig.api.event.v1.events.InitializationEvent
//?} else {
/*import net.minecraft.client.resource.language.I18n
import net.minecraft.entity.living.effect.StatusEffect
import net.minecraft.entity.living.effect.StatusEffectInstance as MobEffectInstance
import net.ornithemc.osl.core.api.util.NamespacedIdentifiers
import net.ornithemc.osl.resource.loader.api.client.ClientResourceLoaderEvents
import net.ornithemc.osl.resource.loader.api.resource.manager.ResourceManager
import java.util.function.BiConsumer
import kotlin.math.roundToInt
*///?}
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
import org.polyfrost.compose.layout.PolyInsets
import org.polyfrost.compose.mc.McFontQueue
import org.polyfrost.compose.mc.McFontQueue.measureWidth
import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyFont

import org.polyfrost.compose.render.FilterQuality
import org.polyfrost.compose.render.ImageLoader
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.compose.runtime.PolyComposeClock
import org.polyfrost.compose.runtime.PolyComposeRuntime
import org.polyfrost.evergreenhud.client.utils.applyCase
import org.polyfrost.evergreenhud.client.hooks.VanillaHudCompat
import org.polyfrost.evergreenhud.client.utils.applyCase
import org.polyfrost.oneconfig.api.config.v1.Node
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.DraggableList
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
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
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.polyfrost.oneconfig.utils.v1.MHUtils.setAccessible
import org.slf4j.LoggerFactory
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.math.PI
import kotlin.math.cos

private const val ICON = 18f
private const val LINE_GAP = 1f
private const val FONT_SIZE = 8f
private const val OUTLINE_INSET = 1f

const val ROMAN = 0

private const val NAME = "Name"
private const val DURATION = "Duration"
private const val AMPLIFIER = "Amplifier"
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

private const val NO_APPLICABLE_EFFECTS = "No applicable effects"
private const val FALLBACK_TICKS = 600

private const val BLINK_PERIOD = 1000L
private const val BLINK_MIN_FADE = 0.25f

private const val EDITOR_DIM_ALPHA = 0.5f

private const val FILTERED_NEVER = 0
private const val FILTERED_WHEN_NOT_OVERFLOWING = 1
private const val FILTERED_ALWAYS = 2

private const val MEASURE_BOUNDS = 100_000f

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

class PotionEffectsHud : Hud(
    id = "potion_effects.json",
    title = "Potion Effects",
    category = Category.PLAYER,
) {
    private companion object {
        private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/Potion Effects")

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

        private val PROPERTY_CONDITIONS = Property::class.java.getDeclaredField("conditions").apply { setAccessible() }
        private val PROPERTY_CALLBACKS = Property::class.java.getDeclaredField("callbacks").apply { setAccessible() }

        private val measureClock = PolyComposeClock()
        //~ if = 1.8.9 'ResourceLocation' -> 'Int'
        private val iconCache = HashMap<ResourceLocation, PolyBitmap?>()

        init {
            //? if > 1.8.9 {
            eventHandler { _: InitializationEvent ->
                val manager = mc.resourceManager as? ReloadableResourceManager
                if (manager != null) manager.registerReloadListener(ResourceManagerReloadListener { clearIcons() })
                else LOGGER.warn("Resource manager is not reloadable, potion effect icons will not follow resource pack changes")
            }
            //?} else
            //ClientResourceLoaderEvents.END_RESOURCE_RELOAD.register(BiConsumer { _, _ -> clearIcons() })
        }

        private fun clearIcons() {
            iconCache.clear()
            //? if = 1.8.9
            //iconsLoaded = false
        }

        //? if > 1.8.9 {
        fun iconFor(id: ResourceLocation): PolyBitmap? {
            if (iconCache.containsKey(id)) return iconCache[id]
            val path = ResourceLocation.fromNamespaceAndPath(id.namespace, "textures/mob_effect/${id.path}.png")
            val icon = try {
                mc.resourceManager.getResource(path).orElse(null)
                    ?.open()?.use { ImageLoader.fromBytes(it.readAllBytes()) }
            } catch (e: Exception) {
                LOGGER.warn("Failed to load the icon for effect {}", id, e)
                null
            }
            iconCache[id] = icon
            return icon
        }
        //?} else {
        /*private var iconsLoaded = false

        private fun loadIcons() {
            val path = NamespacedIdentifiers.from("minecraft", "textures/gui/container/inventory.png")
            val atlas = ResourceManager.client().getResource(path).orElse(null)?.open()?.use { ImageLoader.fromBytes(it.readBytes()) }
            if (atlas == null) {
                LOGGER.warn("Failed to read the potion effect icon atlas {}", path)
                return
            }
            val scale = atlas.width / 256f
            val size = (ICON * scale).roundToInt()
            for (effect in StatusEffect.BY_ID) {
                if (effect == null || !effect.hasIcon()) continue
                val x = ((effect.iconIndex % 8 * ICON) * scale).roundToInt()
                val y = ((198 + effect.iconIndex / 8 * ICON) * scale).roundToInt()
                val w = minOf(size, atlas.width - x)
                val h = minOf(size, atlas.height - y)
                if (size <= 0 || w <= 0 || h <= 0) continue
                val pixels = ByteArray(size * size * 4)
                for (row in 0 until h) System.arraycopy(atlas.pixels, ((y + row) * atlas.width + x) * 4, pixels, row * size * 4, w * 4)
                iconCache[effect.id] = PolyBitmap(size, size, pixels)
            }
        }

        fun iconFor(effect: StatusEffect): PolyBitmap? {
            if (!iconsLoaded) {
                iconsLoaded = true
                try {
                    loadIcons()
                } catch (e: Exception) {
                    LOGGER.warn("Failed to load potion effect icons", e)
                }
            }
            return iconCache[effect.id]
        }
        *///?}

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
        val iconGap: Float,
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

    @DraggableList(
        title = "Sorting",
        description = "Which sorting rules to apply, by priority.",
        subcategory = "Sorting",
        checkable = true,
    )
    var sorting = emptyArray<String>()

    @DraggableList(
        title = "Overrides",
        description = "Override one or more effects' appearance.",
        subcategory = "Overrides",
        checkable = true
    )
    var overrides = emptyArray<String>()

    @Switch(title = "Reversed", subcategory = "Sorting")
    var reversed = false

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

    @Dropdown(
        title = "Show Filtered Effects in HUD Editor",
        description = "Show effects hidden by filters, dimmed, in the HUD editor.",
        options = ["Never", "When Not Overflowing", "Always"],
        category = "HUD Editor",
        subcategory = "Filtered Effects",
    )
    var showFilteredEffects = FILTERED_WHEN_NOT_OVERFLOWING

    @Include
    var overrideOptionsVersion = 0

    @Include
    var formattingInitialized = false

    private var rows = mutableStateOf<List<Row>>(emptyList())

    private var measureRows = mutableStateOf<List<Row>>(emptyList())
    private var measurer: PolyComposeRuntime? = null

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
        if (isReal) {
            hideIf("hideVanillaEffects") { !VanillaHudCompat.isPresent }
            tickHandler = eventHandler { _: TickEvent.End ->
                if (VanillaHudCompat.isPresent) VanillaHudCompat.hideStatusEffects(hideVanillaEffects && !hidden)
                updateAndRecalculate()
            }

            val scopes = buildList {
                for (scope in categoryScopes.scopeDefs) {
                    if (!scope.isOverride) continue
                    val key = scope.key
                    val category = key.takeIf { scope.title != AMBIENT_EFFECTS }
                    add(OverrideScope(key, scope.title, scope.strippedFields, category, isEffect = false) { categoryScopes[key] })
                }
                for (entry in EffectCatalog.ENTRIES) {
                    val path = entry.path
                    add(OverrideScope(path, entry.title, EFFECT_STRIPPED_FIELDS, entry.category, isEffect = true) { effectScopes.byPath.getValue(path) })
                }
            }

            if (overrideOptionsVersion < RANGE_LISTS) migrateRangeLists()
            migrateLegacyEffectKeys()

            if (overrideOptionsVersion < PER_OPTION_OVERRIDES) {
                migrateLegacyOverrides(scopes)
            }
            overrideOptionsVersion = RANGE_LISTS
            getProperty("overrideOptionsVersion").removeMetadata("default")

            val scoped = scopes.flatMap(::setupOverrideScope)
            setupGlobalFormatting()

            val overridesProp = claim(getProperty("overrides"))
            overridesProp.addCallback(OwnedCallback(this) { for (prop in scoped) prop.revaluateDisplay() })
        }
    }

    override fun updateFrequency(): Long = if (isReal) Long.MAX_VALUE else -1L

    override fun remove() {
        tickHandler?.unregister()
        tickHandler = null
        measurer?.dispose()
        measurer = null
        if (VanillaHudCompat.isPresent) VanillaHudCompat.hideStatusEffects(false)
    }

    private fun claim(prop: Property<*>): Property<Any?> {
        @Suppress("UNCHECKED_CAST")
        (PROPERTY_CONDITIONS.get(prop) as MutableList<Any>?)?.removeIf { it is OwnedCondition && it.owner !== this }
        @Suppress("UNCHECKED_CAST")
        (PROPERTY_CALLBACKS.get(prop) as MutableList<Any>?)?.removeIf { it is OwnedCallback<*> && it.owner !== this }
        return Property.recast(prop)
    }

    private class OverrideScope(
        val key: String,
        val title: String,
        val stripped: Set<String>,
        val category: String?,
        val isEffect: Boolean,
        val settings: () -> EffectComponentSettings,
    ) {
        fun overlaps(other: OverrideScope): Boolean = when {
            category == null || other.category == null -> true
            isEffect && other.isEffect -> false
            else -> category == other.category
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

    private fun setupOverrideScope(scope: OverrideScope): List<Property<*>> {
        val key = scope.key
        val title = scope.title
        val settings = scope.settings
        val listProp = claim(getProperty("$key.$OVERRIDDEN_OPTIONS"))
        listProp.addDisplayCondition(OwnedCondition(this) { title !in overrides })

        val fieldProps = EffectComponentSettings.LEAF_FIELDS.filter { it !in scope.stripped }.map { field ->
            claim(getProperty("$key.$field")).also { prop ->
                prop.addDisplayCondition(OwnedCondition(this) { title !in overrides || field !in settings().overriddenOptions })
            }
        }

        var previous = settings().overriddenOptions.toSet()
        listProp.addCallback(OwnedCallback(this) { next ->
            val target = settings()
            val current = (next as? Array<*>)?.filterIsInstance<String>().orEmpty().toSet()
            val added = current - previous
            if (added.isNotEmpty()) {
                val global = resolvedGlobal()
                for (field in added) target.copyField(field, global)
            }
            if (EffectComponentSettings::caseType.name in added) target.caseType = caseType
            for (field in previous - current) target.copyField(field, EffectComponentSettings.DEFAULTS)
            previous = current
            for (prop in fieldProps) prop.revaluateDisplay()
        })

        return fieldProps + listProp
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

        //? if > 1.8.9 {
        val active = if (!isReal) emptyList() else mc.player?.activeEffects?.toList().orEmpty()
        //?} else
        //val active = if (!isReal) emptyList() else mc.player?.activeEffects?.filter { StatusEffect.BY_ID.getOrNull(it.id)?.hasIcon() == true }.orEmpty()
        if (editing) return editorRows(active)

        return sortedRows(active.filter(::shouldDisplayEffect))
    }

    private fun editorRows(active: List<MobEffectInstance>): List<Row> {
        val sources = if (active.isEmpty()) listOf(EXAMPLE_EFFECTS) else listOf(active, EXAMPLE_EFFECTS)
        for (effects in sources) {
            if (showFilteredEffects != FILTERED_NEVER) {
                val all = sortedRows(effects, dimFiltered = true)
                if (showFilteredEffects == FILTERED_ALWAYS || !overflowsScreen(all)) return all
            }
            val visible = sortedRows(effects.filter(::shouldDisplayEffect))
            if (visible.any(::hasContent)) return visible
        }
        val fallback = sortedRows(fallbackExamples()).filter(::hasContent)
        if (fallback.isNotEmpty()) return fallback
        return listOf(placeholderRow())
    }

    private fun hasContent(row: Row): Boolean = row.icon != null || row.name != null || row.duration != null

    /** If all example effects can't show up, go through every registered effect for variants that could get past the filters */
    private fun fallbackExamples(): List<MobEffectInstance> {
        val found = mutableListOf<MobEffectInstance>()
        //? if > 1.8.9 {
        val effects = BuiltInRegistries.MOB_EFFECT.map { BuiltInRegistries.MOB_EFFECT.wrapAsHolder(it) }
        //?} else
        //val effects = StatusEffect.BY_ID.filterNotNull().filter { it.hasIcon() }
        for (effect in effects) {
            variants@ for (ambient in booleanArrayOf(false, true)) {
                val range = valuesFor(example(effect, FALLBACK_TICKS, 0, ambient, infinite = false)).durationRange
                val ticks = if (range[0] != 0f || range[1] != 0f) ((range[0] + range[1]) / 2f * 20f).toInt().coerceAtLeast(1) else FALLBACK_TICKS
                for (infinite in booleanArrayOf(false, true)) {
                    for (amplifier in 0..1) {
                        val candidate = example(effect, ticks, amplifier, ambient, infinite)
                        if (!shouldDisplayEffect(candidate) || !hasContent(row(candidate))) continue
                        found += candidate
                        if (found.size >= EXAMPLES.size) return found
                        break@variants
                    }
                }
            }
        }
        return found
    }

    //? if > 1.8.9 {
    private fun example(effect: Holder<MobEffect>, ticks: Int, amplifier: Int, ambient: Boolean, infinite: Boolean): MobEffectInstance =
        MobEffectInstance(effect, if (infinite) MobEffectInstance.INFINITE_DURATION else ticks, amplifier, ambient, true)
    //?} else {
    /*private fun example(effect: StatusEffect, ticks: Int, amplifier: Int, ambient: Boolean, infinite: Boolean): MobEffectInstance =
        MobEffectInstance(effect.id, ticks, amplifier, ambient, true).also { it.setPermanent(infinite) }
    *///?}

    /** Shown in the editor when no effect at all can get past the filters, so it is never subject to them */
    private fun placeholderRow(): Row {
        return Row(
            icon = null,
            name = NO_APPLICABLE_EFFECTS,
            duration = null,
            blinking = false,
            iconBlink = false,
            nameBlink = false,
            nameColor = PolyColor.WHITE,
            nameStyle = 0,
            durationBlink = false,
            durationColor = PolyColor.WHITE,
            durationStyle = 0,
            dimmed = true,
        )
    }

    private fun sortedRows(effects: List<MobEffectInstance>, dimFiltered: Boolean = false): List<Row> =
        sortEffects(effects, sorting, reversed).map { effect -> row(effect, dimmed = dimFiltered && !shouldDisplayEffect(effect)) }

    private fun overflowsScreen(list: List<Row>): Boolean {
        if (!isReal || list.isEmpty()) return false
        val runtime = measurer ?: PolyComposeRuntime(measureClock).also {
            it.setContent { Effects(measureRows.value) }
            measurer = it
        }
        measureRows.value = list
        runtime.frame(MEASURE_BOUNDS, MEASURE_BOUNDS)
        val scale = effectiveScale
        val width = runtime.root.width * scale
        val height = runtime.root.height * scale

        val fractionX = pivotFractionX
        val fractionY = pivotFractionY
        val boxWidth = if (staticWidth) scaledWidth else width
        val boxHeight = if (staticWidth) scaledHeight else height
        val left = x + fractionX * (scaledWidth - boxWidth)
        val top = y + fractionY * (scaledHeight - boxHeight)
        return left < 0f || top < 0f || left + width > HudManager.guiScreenWidth || top + height > HudManager.guiScreenHeight
    }

    private fun sortEffects(effects: List<MobEffectInstance>, sortingStack: Array<String>, reversed: Boolean): List<MobEffectInstance> {
        var sorted = effects

        for (rule in sortingStack.reversed()) {
            sorted = sortByRule(sorted, rule)
        }

        return if (reversed) sorted.asReversed() else sorted
    }

    private fun sortByRule(effects: List<MobEffectInstance>, rule: String): List<MobEffectInstance> {
        return when (rule) {
            NAME -> effects.map { it to displayNameOf(it) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.second })
                .map { it.first }
            DURATION -> effects.sortedBy { if (it.isInfiniteDuration) Int.MAX_VALUE else it.duration }
            AMPLIFIER -> effects.sortedByDescending { amplifierOf(it) }
            AMBIENT_EFFECTS -> effects.sortedBy { it.isAmbient }
            //? if > 1.8.9 {
            BENEFICIAL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.BENEFICIAL }
            NEUTRAL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.NEUTRAL }
            HARMFUL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.HARMFUL }
            //?} else {
            //BENEFICIAL_EFFECTS -> effects.sortedByDescending { !StatusEffect.BY_ID[it.id].isHarmful }
            //NEUTRAL_EFFECTS -> effects
            //HARMFUL_EFFECTS -> effects.sortedByDescending { StatusEffect.BY_ID[it.id].isHarmful }
            //?}
            else -> {
                val path = EffectCatalog.titleToPath[rule] ?: return effects
                //? if > 1.8.9 {
                effects.sortedByDescending { BuiltInRegistries.MOB_EFFECT.getKey(it.effect.value())?.path == path }
                //?} else
                //effects.sortedByDescending { EffectCatalog.pathOf(it.id) == path }
            }
        }
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
        if (!values.ambientFilter[0] && effect.isAmbient) return false
        if (!values.ambientFilter[1] && !effect.isAmbient) return false

        if (!values.permanentEffects[0] && effect.isInfiniteDuration) return false
        if (!values.permanentEffects[1] && !effect.isInfiniteDuration) return false

        if (!effect.isInfiniteDuration && !values.durationRange.allowsInRanges(effect.duration / 20f)) return false

        if (!values.amplifierRange.allowsInRanges(amplifierOf(effect).toFloat())) return false

        //? if > 1.8.9 {
        val category = effect.effect.value().category
        if (!values.categoryFilter[0] && category == MobEffectCategory.BENEFICIAL) return false
        if (!values.categoryFilter[1] && category == MobEffectCategory.NEUTRAL) return false
        if (!values.categoryFilter[2] && category == MobEffectCategory.HARMFUL) return false
        //?} else {
        /*val harmful = StatusEffect.BY_ID[effect.id].isHarmful
        if (!values.categoryFilter[0] && !harmful) return false
        if (!values.categoryFilter[2] && harmful) return false
        *///?}

        return true
    }

    private fun row(effect: MobEffectInstance, dimmed: Boolean = false): Row {
        //? if > 1.8.9 {
        val mobEffect = effect.effect.value()
        return row(
            BuiltInRegistries.MOB_EFFECT.getKey(mobEffect),
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
            statusEffect,
            localizedNameOf(effect),
            effect.duration,
            amplifierOf(effect),
            effect.isInfiniteDuration,
            valuesFor(effect),
            dimmed,
        )
        *///?}
    }

    //~ if = 1.8.9 'ResourceLocation?' -> 'StatusEffect?'
    private fun row(id: ResourceLocation?, name: String, ticks: Int, amplifier: Int, infinite: Boolean, values: EffectComponentValues, dimmed: Boolean = false): Row {
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
            icon = if (values.iconEnabled && id != null) iconFor(id) else null,
            iconGap = values.iconSpacing,
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
        val list = rows.value

        if (list.any { it.blinking }) {
            LaunchedEffect(Unit) {
                while (true) withFrameMillis { blinkFade.floatValue = currentBlinkFade() }
            }
        }

        Effects(list)
    }

    @Composable
    private fun Effects(list: List<Row>) {
        val layout = layoutState.value
        if (list.isEmpty()) return
        val scale = textScale.coerceAtLeast(0.01f)

        val fonts = fontRevision.intValue
        val maxRowWidth = remember(list, scale, font, textWeight, layout.mode, fonts) {
            val mode = layout.mode
            val hasAnyIcon = list.any { it.icon != null }
            val iconWidth = ICON * scale

            list.maxOf { row ->
                val nameWidth = row.name?.let { textWidth(it, row.nameStyle, scale) } ?: 0f
                val durationWidth = row.duration?.let { textWidth(it, row.durationStyle, scale) } ?: 0f
                val hasText = row.name != null || row.duration != null
                val iconGap = if (row.icon != null && hasText) row.iconGap * scale else 0f
                when (mode) {
                    MODE_STACKED -> maxOf(if (row.icon != null) iconWidth else 0f, nameWidth, durationWidth)
                    MODE_SINGLE_LINE -> {
                        val gapWidth = if (row.name != null && row.duration != null) charWidth(scale) else 0f
                        val textWidth = nameWidth + gapWidth + durationWidth
                        (if (hasAnyIcon) iconWidth else 0f) + iconGap + textWidth
                    }
                    else -> (if (hasAnyIcon) iconWidth else 0f) + iconGap + maxOf(nameWidth, durationWidth)
                }
            }
        }

        val horizontal = layout.horizontal
        val outline = outlineInsets(list, scale, maxRowWidth, layout)
        val modifier = hudBackground().padding(padLeft + outline.left, padTop + outline.top, padRight + outline.right, padBottom + outline.bottom)

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

    private class RowBounds(val hasText: Boolean, val left: Float, val top: Float, val right: Float, val bottom: Float)

    /** mirrors the layout of Effect and StackedEffect, placing the text block within its row **/
    private fun rowBounds(row: Row, scale: Float, rowWidth: Float, layout: Layout, poppins: PolyFont?): RowBounds {
        val iconSize = if (row.icon != null) ICON * scale else 0f
        if (row.name == null && row.duration == null) return RowBounds(false, 0f, 0f, 0f, 0f)

        val nameWidth = row.name?.let { textWidth(it, row.nameStyle, scale) } ?: 0f
        val durationWidth = row.duration?.let { textWidth(it, row.durationStyle, scale) } ?: 0f
        val lineHeight = poppins?.metrics?.let { it.descent - it.ascent } ?: McFontQueue.measureBlockHeight(1, scale)
        val lineGap = LINE_GAP * scale
        val lines = listOfNotNull(row.name, row.duration).size
        val columnHeight = lines * lineHeight + (lines - 1) * lineGap

        if (layout.mode == MODE_STACKED) {
            val textWidth = maxOf(nameWidth, durationWidth)
            val iconHeight = if (row.icon != null) iconSize + row.iconGap * scale else 0f
            val left = (rowWidth - textWidth) / 2f
            val bottomAligned = isBottomAligned()
            return RowBounds(true, left, if (bottomAligned) 0f else iconHeight, left, if (bottomAligned) iconHeight else 0f)
        }

        val singleLine = layout.mode == MODE_SINGLE_LINE
        val textWidth = if (singleLine) nameWidth + durationWidth + (if (lines > 1) charWidth(scale) else 0f) else maxOf(nameWidth, durationWidth)
        val textHeight = if (singleLine) lineHeight else columnHeight
        val iconWidth = if (row.icon != null) iconSize + row.iconGap * scale else 0f
        val height = maxOf(iconSize, textHeight)
        val contentWidth = iconWidth + textWidth
        val rowLeft = when (layout.align) {
            PolyAlign.Center -> (rowWidth - contentWidth) / 2f
            PolyAlign.Right -> rowWidth - contentWidth
            else -> 0f
        }
        val left = rowLeft + if (layout.align == PolyAlign.Right) 0f else iconWidth
        val top = (height - textHeight) / 2f
        val right = rowWidth - left - textWidth
        return RowBounds(true, left, top, right, top)
    }

    /**
    * a full shadow outlines the text past its bounds on every side, so pad only the edges where the text sits closer to the HUD's edge than the outline reaches
    * the configured padding already counts towards that distance, so only the remaining deficit is added
    * only the rows on the outer edge of the list matter for the edge they lie along
    **/
    private fun outlineInsets(list: List<Row>, scale: Float, rowWidth: Float, layout: Layout): PolyInsets {
        if (!showShadow || !fullShadow) return PolyInsets()
        val poppins = if (font == Font.Minecraft) null else PolyFont(getPoppinsFontName(), FONT_SIZE * scale)
        val outline = OUTLINE_INSET * scale
        // minecraft text is measured with a trailing advance and a blank bottom row, which already hold the outline there
        val slack = if (poppins == null) outline else 0f
        val bounds = list.map { rowBounds(it, scale, rowWidth, layout, poppins) }
        var left = 0f
        var top = 0f
        var right = 0f
        var bottom = 0f
        val horizontal = layout.horizontal
        bounds.forEachIndexed { i, b ->
            if (!b.hasText) return@forEachIndexed
            val first = i == 0
            val last = i == bounds.lastIndex
            if (!horizontal || first) left = maxOf(left, outline - padLeft - b.left)
            if (horizontal || first) top = maxOf(top, outline - padTop - b.top)
            if (!horizontal || last) right = maxOf(right, outline - padRight - b.right - slack)
            if (horizontal || last) bottom = maxOf(bottom, outline - padBottom - b.bottom - slack)
        }
        return PolyInsets(left = left, top = top, right = right, bottom = bottom)
    }

    @Composable
    private fun Effect(row: Row, scale: Float, maxRowWidth: Float, layout: Layout) {
        val mode = layout.mode
        if (mode == MODE_STACKED) {
            StackedEffect(row, scale, maxRowWidth, layout.horizontal)
            return
        }

        val style = styleFor(row)
        val align = layout.align

        val iconBlock: @Composable () -> Unit = {
            if (row.icon != null) Icon(row.icon, ICON * scale, style.iconAlpha)
        }

        PolyBox(modifier = PolyModifier.width(maxRowWidth)) {
            PolyRow(gap = row.iconGap * scale, modifier = PolyModifier.align(align)) {
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
    private fun StackedEffect(row: Row, scale: Float, maxWidth: Float, horizontal: Boolean) {
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

        val textBlock: @Composable () -> Unit = {
            PolyColumn(gap = LINE_GAP * scale, modifier = PolyModifier.align(PolyAlign.Center)) {
                if (reversed) {
                    durationBlock(); nameBlock()
                } else {
                    nameBlock(); durationBlock()
                }
            }
        }
        val hasText = row.name != null || row.duration != null

        val boxModifier = PolyModifier.width(maxWidth).let {
            if (reversed && horizontal) it.align(PolyAlign.Bottom) else it
        }

        PolyBox(modifier = boxModifier) {
            PolyColumn(gap = row.iconGap * scale, modifier = PolyModifier.align(PolyAlign.Center)) {
                if (reversed) {
                    if (hasText) textBlock()
                    iconBlock()
                } else {
                    iconBlock()
                    if (hasText) textBlock()
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
        val shadowPaint = PolyColor(faded(shadowColor, fade), shadowChroma, shadowChromaSpeed)
        val outline = showShadow && fullShadow
        if (font == Font.Minecraft) {
            if ((style and ITALIC) == 0) {
                PolyMcText(
                    mcText(text, style),
                    outlineColor = if (outline) shadowPaint else null,
                    color = color,
                    shadow = showShadow && !fullShadow,
                    scale = scale,
                    modifier = modifier,
                )
                return
            }

            fontRevision.intValue
            val lines = text.split('\n').map { mcText(it, style) }
            val lineHeight = McFontQueue.measureTextHeight(scale)
            val width = lines.maxOf { McFontQueue.measureTextWidth(it, scale) }
            val totalHeight = McFontQueue.measureBlockHeight(lines.size, scale)
            PolyCanvas(modifier.size(width, totalHeight), live = color.chroma || (outline && shadowPaint.chroma)) { x, y, _, _ ->
                val argb = color.argb
                for ((i, formatted) in lines.withIndex()) {
                    val top = y + i * lineHeight
                    save()
                    try {
                        translate(x, top)
                        if (outline) mcTextOutline(formatted, 0f, 0f, argb, shadowPaint.argb, scale)
                        else mcText(formatted, 0f, 0f, argb, showShadow, scale)
                    } finally {
                        restore()
                    }
                }
            }
        } else {
            val fontName = poppinsFont(style)
            val underline = (style and UNDERLINE) != 0
            if (!underline && !outline) {
                PolyText(
                    text,
                    color,
                    fontSize = FONT_SIZE * scale,
                    shadow = showShadow,
                    shadowColor = shadowPaint,
                    shadowOffset = shadowOffsetX,
                    font = fontName,
                    modifier = modifier,
                )
                return
            }

            val polyFont = PolyFont(fontName, FONT_SIZE * scale)
            val metrics = polyFont.metrics
            val width = polyFont.measureTextWidth(text).coerceAtLeast(1f)
            val height = metrics.descent - metrics.ascent
            val position = metrics.underlinePosition
            val thickness = metrics.underlineThickness
            PolyCanvas(PolyModifier.size(width, height).then(modifier), live = color.chroma || (showShadow && shadowPaint.chroma)) { x, y, w, _ ->
                val baseline = y - metrics.ascent
                if (outline) {
                    textOutline(text, x, baseline, shadowPaint, polyFont, scale)
                    if (underline) line(x, baseline + position, x + w, baseline + position, shadowPaint, thickness + OUTLINE_INSET * 2f * scale)
                } else if (showShadow) {
                    val sx = x + shadowOffsetX
                    val sy = baseline + shadowOffsetX
                    text(text, sx, sy, shadowPaint, polyFont)
                    line(sx, sy + position, sx + w, sy + position, shadowPaint, thickness)
                }
                text(text, x, baseline, color, polyFont)
                if (underline) line(x, baseline + position, x + w, baseline + position, color, thickness)
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
        it.overrides = overrides.copyOf()
        it.layoutState = mutableStateOf(layoutState.value)
        it.blinkFade = mutableFloatStateOf(1f)
        it.fontRevision = mutableIntStateOf(FontManager.revision)
        it.tickHandler = null

        it.categoryScopes = categoryScopes.deepCopy()
        it.effectScopes = effectScopes.deepCopy()

        it.rows = mutableStateOf(emptyList())
        it.measureRows = mutableStateOf(emptyList())
        it.measurer = null
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

        val liveSortOptions = buildList {
            addAll(listOf(NAME, DURATION, AMPLIFIER, AMBIENT_EFFECTS, BENEFICIAL_EFFECTS, NEUTRAL_EFFECTS, HARMFUL_EFFECTS))
            addAll(EffectCatalog.ENTRIES.map { it.title })
        }.toTypedArray()

        val sortingProp = tree.getProp("sorting") ?: throw IllegalStateException("sorting property not found on tree")
        sortingProp.addMetadata("options", liveSortOptions)

        for (scope in categoryScopes.scopeDefs) {
            val t = Tree.tree(scope.key)
            t.addMetadata(mapOf(
                "title" to scope.title, "description" to scope.description,
                "category" to "General", "subcategory" to "Effects",
                "collapsed" to (scope.key != "global")
            ))
            collector.handle(t, scope.settings, 0)
            for (field in scope.strippedFields) stripProperty(t, field)
            if (scope.isOverride) {
                stripProperty(t, SEPARATE_FORMATTING)
                describeOverrideOptions(t, scope.strippedFields)
            }
            tree.put(t)
        }

        for (entryDef in EffectCatalog.ENTRIES) {
            val settings = effectScopes.byPath.getValue(entryDef.path)
            val t = Tree.tree(entryDef.path)
            t.addMetadata(mapOf(
                "title" to entryDef.title,
                "description" to "Overrides for the ${entryDef.title} effect.",
                "category" to "General", "subcategory" to "Effects",
                "collapsed" to true
            ))
            collector.handle(t, settings, 0)
            for (field in EFFECT_STRIPPED_FIELDS) stripProperty(t, field)
            stripProperty(t, SEPARATE_FORMATTING)
            describeOverrideOptions(t, EFFECT_STRIPPED_FIELDS)
            tree.put(t)
        }

        val liveOptions = buildList {
            addAll(categoryScopes.scopeDefs.filter { it.isOverride }.map { it.title })
            addAll(EffectCatalog.ENTRIES.map { it.title })
        }.toTypedArray()

        val prop = tree.getProp("overrides") ?: throw IllegalStateException("overrides property not found on tree")
        prop.addMetadata("options", liveOptions)
    }

    private fun valuesFor(effect: MobEffectInstance): EffectComponentValues {
        //? if > 1.8.9 {
        val mobEffect = effect.effect.value()
        val id = BuiltInRegistries.MOB_EFFECT.getKey(mobEffect)
        val categoryScope = when (mobEffect.category) {
            MobEffectCategory.BENEFICIAL -> categoryScopes.beneficial
            MobEffectCategory.NEUTRAL -> categoryScopes.neutral
            MobEffectCategory.HARMFUL -> categoryScopes.harmful
        }
        //?} else {
        /*val statusEffect = StatusEffect.BY_ID[effect.id]
        val harmful = statusEffect.isHarmful
        val id = EffectCatalog.pathOf(effect.id)
        val categoryScope = if (harmful) categoryScopes.harmful else categoryScopes.beneficial
        *///?}

        val layers = overrides.mapNotNull { entry ->
            when (entry) {
                //? if > 1.8.9 {
                BENEFICIAL_EFFECTS -> categoryScope.takeIf { mobEffect.category == MobEffectCategory.BENEFICIAL }
                NEUTRAL_EFFECTS -> categoryScope.takeIf { mobEffect.category == MobEffectCategory.NEUTRAL }
                HARMFUL_EFFECTS -> categoryScope.takeIf { mobEffect.category == MobEffectCategory.HARMFUL }
                //?} else {
                //BENEFICIAL_EFFECTS -> categoryScope.takeIf { !harmful }
                //NEUTRAL_EFFECTS -> null
                //HARMFUL_EFFECTS -> categoryScope.takeIf { harmful }
                //?}
                AMBIENT_EFFECTS -> categoryScopes.ambient.takeIf { effect.isAmbient }
                else -> {
                    val path = EffectCatalog.titleToPath[entry]
                    //~ if = 1.8.9 'id != null && id.path == path' -> 'id == path'
                    if (path == null) null else effectScopes.byPath[path]?.takeIf { id != null && id.path == path }
                }
            }
        }
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

    private fun describeOverrideOptions(t: Tree, stripped: Set<String>) {
        val fields = EffectComponentSettings.LEAF_FIELDS.filter { it !in stripped }
        val prop = t.getProp(OVERRIDDEN_OPTIONS) ?: throw IllegalStateException("$OVERRIDDEN_OPTIONS property not found on ${t.id}")
        prop.addMetadata("options", fields.toTypedArray())
        prop.addMetadata("labels", fields.associateWith { t.getProp(it)?.title ?: it })
    }

    @Suppress("UNCHECKED_CAST")
    private fun stripProperty(t: Tree, key: String) {
        val theMapField = Tree::class.java.getDeclaredField("theMap")
        theMapField.setAccessible()
        val map = theMapField.get(t) as LinkedHashMap<String, Node>
        map.remove(key)
    }
}
