package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import net.minecraft.core.registries.BuiltInRegistries
//? if < 1.21.11
//import net.minecraft.resources.ResourceLocation
//? if >= 1.21.11
import net.minecraft.resources.Identifier as ResourceLocation
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
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
import org.polyfrost.compose.mc.McFontQueue.measureWidth
import org.polyfrost.compose.render.ImageLoader
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hooks.VanillaHudCompat
import org.polyfrost.oneconfig.api.config.v1.Node
import org.polyfrost.oneconfig.api.config.v1.Tree
import org.polyfrost.oneconfig.api.config.v1.annotations.DraggableList
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Option
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.collect.impl.OneConfigCollector
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.TickEvent
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.api.hud.v1.Section
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.polyfrost.oneconfig.utils.v1.MHUtils.setAccessible
import org.slf4j.LoggerFactory
import kotlin.math.PI
import kotlin.math.cos

private const val ICON = 18f
private const val ICON_GAP = 3f
private const val LINE_GAP = 1f
private const val FONT_SIZE = 8f

const val ROMAN = 0

private const val NAME = "Name"
private const val DURATION = "Duration"
private const val AMPLIFIER = "Amplifier"
private const val AMBIENT_EFFECTS = "Ambient Effects"
private const val BENEFICIAL_EFFECTS = "Beneficial Effects"
private const val NEUTRAL_EFFECTS = "Neutral Effects"
private const val HARMFUL_EFFECTS = "Harmful Effects"

private const val INFINITE = "**:**"

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
            Example("speed", "Speed", 1200, 1),
            Example("strength", "Strength", 140, 0),
        )

        val ALL_LEAF_FIELDS: List<String> by lazy {
            EffectComponentSettings::class.java.declaredFields
                .filter { field ->
                    field.declaredAnnotations.any { ann ->
                        (ann as java.lang.annotation.Annotation).annotationType().isAnnotationPresent(Option::class.java)
                    }
                }
                .map { it.name }
        }

        private val iconPaint = Paint()
        private val iconCache = HashMap<ResourceLocation, Image?>()
        private var cachedPackIds: List<String>? = null

        fun syncIcons(ids: List<String>) {
            if (ids == cachedPackIds) return
            cachedPackIds = ids
            iconCache.clear()
        }

        fun iconFor(id: ResourceLocation): Image? {
            if (iconCache.containsKey(id)) return iconCache[id]
            val path = ResourceLocation.fromNamespaceAndPath(id.namespace, "textures/mob_effect/${id.path}.png")
            val icon = try {
                mc.resourceManager.getResource(path).orElse(null)
                    ?.open()?.use { ImageLoader.fromBytes(it.readBytes()) }
            } catch (e: Exception) {
                LOGGER.warn("Failed to load the icon for effect {}", id, e)
                null
            }
            iconCache[id] = icon
            return icon
        }

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

    private class Example(val id: String, val name: String, val ticks: Int, val amplifier: Int)

    private data class Row(
        val icon: Image?,
        val name: String?,
        val duration: String?,
        val fade: Float,
        val iconBlink: Boolean,
        val nameBlink: Boolean,
        val nameColor: PolyColor,
        val durationBlink: Boolean,
        val durationColor: PolyColor,
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

    private var rows = mutableStateOf<List<Row>>(emptyList())

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun setup() {
        super.setup()
        if (isReal) {
            hideIf("hideVanillaEffects") { !VanillaHudCompat.isPresent }
            if (VanillaHudCompat.isPresent) {
                eventHandler { _: TickEvent ->
                    VanillaHudCompat.hideStatusEffects(hideVanillaEffects && !hidden)
                }
            }

            for (scope in categoryScopes.scopeDefs) {
                if (!scope.isOverride) continue
                val check = { scope.title !in overrides }
                for (field in ALL_LEAF_FIELDS) {
                    if (field in scope.strippedFields) continue
                    hideIf("${scope.key}.$field", check)
                }
            }

            for (entry in EffectCatalog.ENTRIES) {
                val check = { entry.title !in overrides }
                for (field in ALL_LEAF_FIELDS) {
                    if (field == "categoryFilter") continue
                    hideIf("${entry.path}.$field", check)
                }
            }
        }
    }

    override fun canMergeBackground(): Boolean = true

    override fun update(): Boolean {
        syncIcons(mc.resourcePackRepository.selectedPacks.map { it.id })
        val next = buildRows()
        if (next == rows.value) return false
        rows.value = next
        return true
    }

    private fun buildRows(): List<Row> {
        if (!isReal) {
            return exampleRows()
        }

        val active = mc.player?.activeEffects?.toList().orEmpty()
        if (active.isEmpty()) {
            return if (HudManager.isEditing) exampleRows()
            else emptyList()
        }

        val editing = HudManager.isEditing
        val visible = if (editing) active else active.filter(::shouldDisplayEffect)
        if (visible.isEmpty()) return emptyList()

        val ordered = sortEffects(visible, sorting, reversed)
        return ordered.map { effect -> row(effect, dimmed = editing && !shouldDisplayEffect(effect)) }
    }

    private fun exampleRows(): List<Row> =
        EXAMPLES.map {
            row(
                ResourceLocation.withDefaultNamespace(it.id),
                it.name,
                it.ticks,
                it.amplifier,
                false,
                categoryScopes.global
            )
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
            NAME -> effects.sortedBy { it.effect.value().displayName.string }
            DURATION -> effects.sortedBy { if (it.isInfiniteDuration) Int.MAX_VALUE else it.duration }
            AMPLIFIER -> effects.sortedByDescending { it.amplifier }
            AMBIENT_EFFECTS -> effects.sortedBy { it.isAmbient }
            BENEFICIAL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.BENEFICIAL }
            NEUTRAL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.NEUTRAL }
            HARMFUL_EFFECTS -> effects.sortedByDescending { it.effect.value().category == MobEffectCategory.HARMFUL }
            else -> {
                val path = EffectCatalog.titleToPath[rule] ?: return effects
                effects.sortedByDescending { BuiltInRegistries.MOB_EFFECT.getKey(it.effect.value())?.path == path }
            }
        }
    }

    private fun shouldDisplayEffect(effect: MobEffectInstance): Boolean {
        if (!effect.showIcon()) return false
        val values = valuesFor(effect)

        if (!values.showEffects) return false
        if (!values.ambientFilter[0] && effect.isAmbient) return false
        if (!values.ambientFilter[1] && !effect.isAmbient) return false

        if (!values.permanentEffects[0] && effect.isInfiniteDuration) return false
        if (!values.permanentEffects[1] && !effect.isInfiniteDuration) return false

        val min = values.durationRange[0]
        val max = values.durationRange[1]
        if (min != 0f || max != 0f) {
            if (!effect.isInfiniteDuration && effect.duration / 20f !in min..max) return false
        }

        val category = effect.effect.value().category
        if (!values.categoryFilter[0] && category == MobEffectCategory.BENEFICIAL) return false
        if (!values.categoryFilter[1] && category == MobEffectCategory.NEUTRAL) return false
        if (!values.categoryFilter[2] && category == MobEffectCategory.HARMFUL) return false

        return true
    }

    private fun row(effect: MobEffectInstance, dimmed: Boolean = false): Row {
        val mobEffect = effect.effect.value()
        return row(
            BuiltInRegistries.MOB_EFFECT.getKey(mobEffect),
            mobEffect.displayName.string,
            effect.duration,
            effect.amplifier,
            effect.isInfiniteDuration,
            valuesFor(effect),
            dimmed,
        )
    }

    private fun row(id: ResourceLocation?, name: String, ticks: Int, amplifier: Int, infinite: Boolean, values: EffectComponentValues, dimmed: Boolean = false): Row {
        val level = amplifier + 1
        val amplifierText = if (values.showAmplifier && level > 1) {
            if (values.amplifierStyle == ROMAN) roman(level) else level.toString()
        } else null

        val title = when {
            values.nameEnabled && amplifierText != null -> "$name $amplifierText"
            values.nameEnabled -> name
            amplifierText != null -> amplifierText
            else -> null
        }

        val time = when {
            !values.durationEnabled -> null
            infinite -> INFINITE
            else -> formatDuration(ticks)
        }
        return Row(
            icon = if (values.iconEnabled && id != null) iconFor(id) else null,
            name = title,
            duration = time,
            fade = fade(ticks, infinite, values.blinkThreshold),
            iconBlink = values.iconBlink,
            nameBlink = values.nameBlink,
            nameColor = values.nameColor,
            durationBlink = values.durationBlink,
            durationColor = values.durationColor,
            dimmed = dimmed,
        )
    }

    private fun fade(ticks: Int, infinite: Boolean, blinkThreshold: Float): Float {
        if (infinite || blinkThreshold <= 0f || ticks > blinkThreshold * 20f) return 1f
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

        return when (section) {
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

        return when (section) {
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

        return when (section) {
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
        return when (section) {
            Section.BottomLeft, Section.BottomCenter, Section.BottomRight -> true
            else -> false
        }
    }

    private fun styleFor(row: Row): RowStyle {
        val dimAlpha = if (row.dimmed) EDITOR_DIM_ALPHA else 1f
        val iconAlpha = (if (row.iconBlink) row.fade else 1f) * dimAlpha
        val nameFade = (if (row.nameBlink) row.fade else 1f) * dimAlpha
        val durationFade = (if (row.durationBlink) row.fade else 1f) * dimAlpha
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
        if (list.isEmpty()) return
        val scale = textScale.coerceAtLeast(0.01f)

        val maxRowWidth = remember(list, scale, font, layoutMode()) {
            val mode = layoutMode()
            val hasAnyIcon = list.any { it.icon != null }
            val iconWidth = ICON * scale
            val iconGap = ICON_GAP * scale

            list.maxOf { row ->
                val nameWidth = row.name?.let { measureWidth?.invoke(it, scale) ?: 0f } ?: 0f
                val durationWidth = row.duration?.let { measureWidth?.invoke(it, scale) ?: 0f } ?: 0f
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

        val horizontal = direction()
        val modifier = hudBackground().padding(padLeft, padTop, padRight, padBottom)

        PolyBox(modifier = modifier) {
            if (horizontal) {
                PolyRow(gap = spacing * scale) {
                    for (row in list) Effect(row, scale, maxRowWidth)
                }
            } else {
                PolyColumn(gap = spacing * scale) {
                    for (row in list) Effect(row, scale, maxRowWidth)
                }
            }
        }
    }

    @Composable
    private fun Effect(row: Row, scale: Float, maxRowWidth: Float) {
        val mode = layoutMode()
        if (mode == MODE_STACKED) {
            StackedEffect(row, scale, maxRowWidth)
            return
        }

        val style = styleFor(row)
        val align = autoTextAlign()

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
                if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor)
                if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor)
            } else {
                if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor)
                if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor)
            }
        }
    }

    @Composable
    private fun FullText(row: Row, scale: Float, style: RowStyle, align: PolyAlign) {
        PolyColumn(gap = LINE_GAP * scale, modifier = PolyModifier.align(PolyAlign.Center)) {
            if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor, modifier = PolyModifier.align(align))
            if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor, modifier = PolyModifier.align(align))
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
            if (row.name != null) Line(row.name, scale, style.nameFade, style.nameColor, modifier = PolyModifier.align(PolyAlign.Center))
        }
        val durationBlock: @Composable () -> Unit = {
            if (row.duration != null) Line(row.duration, scale, style.durationFade, style.durationColor, modifier = PolyModifier.align(PolyAlign.Center))
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
    private fun Icon(icon: Image, size: Float, alpha: Float, modifier: PolyModifier = PolyModifier) {
        PolyCanvas(modifier.size(size, size)) { x, y, w, h ->
            iconPaint.alpha = (255f * alpha).toInt().coerceIn(0, 255)
            image(icon, x, y, w, h, iconPaint)
        }
    }

    @Composable
    private fun Line(text: String, scale: Float, fade: Float, color: PolyColor, modifier: PolyModifier = PolyModifier) {
        if (font == Font.Minecraft) {
            PolyMcText(text, color, shadow = showShadow, scale = scale, modifier = modifier)
        } else {
            PolyText(
                text,
                color,
                fontSize = FONT_SIZE * scale,
                shadow = showShadow,
                shadowColor = PolyColor(faded(shadowColor, fade), shadowChroma, shadowChromaSpeed),
                shadowOffset = shadowOffsetX,
                font = getPoppinsFontName(),
                modifier = modifier,
            )
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

        it.categoryScopes = categoryScopes.deepCopy()
        it.effectScopes = effectScopes.deepCopy()

        it.rows = mutableStateOf(emptyList())
    }

    private class PerCategoryEffectSettings {
        val global = EffectComponentSettings()
        val beneficial = EffectComponentSettings()
        val neutral = EffectComponentSettings()
        val harmful = EffectComponentSettings()
        val ambient = EffectComponentSettings()

        val scopeDefs: List<ScopeDef> get() = listOf(
            ScopeDef("global", "Global", global, isOverride = false),
            ScopeDef("beneficial", BENEFICIAL_EFFECTS, beneficial, strippedFields = setOf("categoryFilter")),
            ScopeDef("neutral", NEUTRAL_EFFECTS, neutral, strippedFields = setOf("categoryFilter")),
            ScopeDef("harmful", HARMFUL_EFFECTS, harmful, strippedFields = setOf("categoryFilter")),
            ScopeDef("ambient", AMBIENT_EFFECTS, ambient, strippedFields = setOf("ambientFilter", "categoryFilter", "permanentEffects", "emittingParticleEffects")),
        )

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
            stripProperty(t, "categoryFilter")
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
        val mobEffect = effect.effect.value()
        val id = BuiltInRegistries.MOB_EFFECT.getKey(mobEffect)
        val categoryScope = when (mobEffect.category) {
            MobEffectCategory.BENEFICIAL -> categoryScopes.beneficial
            MobEffectCategory.NEUTRAL -> categoryScopes.neutral
            MobEffectCategory.HARMFUL -> categoryScopes.harmful
        }

        for (entry in overrides) {
            val resolved = when (entry) {
                BENEFICIAL_EFFECTS -> categoryScope.takeIf { mobEffect.category == MobEffectCategory.BENEFICIAL }
                NEUTRAL_EFFECTS -> categoryScope.takeIf { mobEffect.category == MobEffectCategory.NEUTRAL }
                HARMFUL_EFFECTS -> categoryScope.takeIf { mobEffect.category == MobEffectCategory.HARMFUL }
                AMBIENT_EFFECTS -> categoryScopes.ambient.takeIf { effect.isAmbient }
                else -> {
                    val path = EffectCatalog.titleToPath[entry] ?: continue
                    effectScopes.byPath[path]?.takeIf { id != null && id.path == path }
                }
            }
            if (resolved != null) return resolved
        }
        return categoryScopes.global
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
