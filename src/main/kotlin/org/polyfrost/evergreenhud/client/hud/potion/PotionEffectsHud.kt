package org.polyfrost.evergreenhud.client.hud.potion

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import net.minecraft.core.registries.BuiltInRegistries
//? if < 1.21.11
//import net.minecraft.resources.ResourceLocation
//? if >= 1.21.11
import net.minecraft.resources.Identifier as ResourceLocation
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
import org.polyfrost.compose.layout.PolyAlign
import org.polyfrost.compose.render.ImageLoader
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.config.v1.annotations.DraggableList
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudManager
import org.polyfrost.oneconfig.utils.v1.dsl.mc
import org.slf4j.LoggerFactory
import kotlin.math.PI
import kotlin.math.cos

private const val ICON = 18f
private const val ICON_GAP = 3f
private const val LINE_GAP = 1f
private const val FONT_SIZE = 8f

private const val ROMAN = 0

private const val SORT_NAME = "Name"
private const val SORT_DURATION = "Duration"
private const val SORT_AMPLIFIER = "Amplifier"
private const val SORT_AMBIENT = "Ambient Effects"
private const val SORT_BAD_EFFECTS = "Bad Effects"

private const val INFINITE = "**:**"

private const val BLINK_PERIOD = 1000L
private const val BLINK_MIN_FADE = 0.25f

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

        private val EXAMPLES = listOf(
            Example("speed", "Speed", 1200, 1),
            Example("strength", "Strength", 140, 0),
        )

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

    private data class Row(val icon: Image?, val name: String?, val duration: String?, val fade: Float)

    @Switch(title = "Icon")
    var showIcon = true

    @Switch(title = "Name")
    var showName = true

    @Switch(title = "Duration")
    var showDuration = true

    @Switch(title = "Amplifier", description = "Show the effect level next to its name.")
    var showAmplifier = true

    @RadioButton(title = "Amplifier Style", options = ["Roman", "Arabic"])
    var amplifierStyle = ROMAN

    @Switch(title = "Ambient Effects", description = "Show effects coming from beacons and conduits.")
    var showAmbient = true

    @DraggableList(
        title = "Sorting",
        description = "Which sorting rules to apply, by priority.",
        checkable = true,
        options = [
            SORT_NAME,
            SORT_DURATION,
            SORT_AMPLIFIER,
            SORT_AMBIENT,
            SORT_BAD_EFFECTS
        ]
    )
    var sorting = emptyArray<String>()

    @Switch(title = "Reversed")
    var reversed = false

    @Slider(title = "Spacing", min = 0F, max = 10F, step = 1F)
    var spacing = 2f

    @Slider(
        title = "Blink Threshold (s)",
        description = "Fade an effect in and out once its remaining duration drops below this. 0 disables it.",
        min = 0F,
        max = 30F,
        step = 1F,
    )
    var blinkThreshold = 5f

    private var rows = mutableStateOf<List<Row>>(emptyList())

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun canMergeBackground(): Boolean = true

    override fun update(): Boolean {
        syncIcons(mc.resourcePackRepository.selectedPacks.map { it.id })
        val next = buildRows()
        if (next == rows.value) return false
        rows.value = next
        return true
    }

    private fun buildRows(): List<Row> {
        val effects = currentEffects()
        if (effects.isEmpty()) {
            return if (!isReal || HudManager.isEditing) EXAMPLES.map { row(ResourceLocation.withDefaultNamespace(it.id), it.name, it.ticks, it.amplifier, false) }
            else emptyList()
        }

        val ordered = sortEffects(effects, sorting, reversed)
        return ordered.map { row(it) }
    }

    private fun sortEffects(effects: List<MobEffectInstance>, sortingStack: Array<String>, reversed: Boolean): List<MobEffectInstance> {
        var sorted = effects

        // Sorting is reversed because the last rule in the list is the highest priority.
        for (rule in sortingStack.reversed()) {
            sorted = sortByRule(sorted, rule)
        }

        return if (reversed) sorted.asReversed() else sorted
    }

    private fun sortByRule(effects: List<MobEffectInstance>, rule: String): List<MobEffectInstance> {
        return when (rule) {
            SORT_NAME -> effects.sortedBy { it.effect.value().displayName.string }
            SORT_DURATION -> effects.sortedBy { if (it.isInfiniteDuration) Int.MAX_VALUE else it.duration }
            SORT_AMPLIFIER -> effects.sortedByDescending { it.amplifier }
            SORT_AMBIENT -> effects.sortedBy { it.isAmbient }
            SORT_BAD_EFFECTS -> effects.sortedBy { it.effect.value().isBeneficial }
            else -> effects
        }
    }

    private fun currentEffects(): List<MobEffectInstance> {
        if (!isReal) return emptyList()
        val player = mc.player ?: return emptyList()
        return player.activeEffects.filter { it.showIcon() && (showAmbient || !it.isAmbient) }
    }

    private fun row(effect: MobEffectInstance): Row {
        val mobEffect = effect.effect.value()
        return row(
            BuiltInRegistries.MOB_EFFECT.getKey(mobEffect),
            mobEffect.displayName.string,
            effect.duration,
            effect.amplifier,
            effect.isInfiniteDuration,
        )
    }

    private fun row(id: ResourceLocation?, name: String, ticks: Int, amplifier: Int, infinite: Boolean): Row {
        val level = amplifier + 1
        val title = when {
            !showName -> null
            showAmplifier && level > 1 -> "$name ${if (amplifierStyle == ROMAN) roman(level) else level}"
            else -> name
        }
        val time = when {
            !showDuration -> null
            infinite -> INFINITE
            else -> formatDuration(ticks)
        }
        return Row(if (showIcon && id != null) iconFor(id) else null, title, time, fade(ticks, infinite))
    }

    private fun fade(ticks: Int, infinite: Boolean): Float {
        if (infinite || blinkThreshold <= 0f || ticks > blinkThreshold * 20f) return 1f
        val phase = (System.currentTimeMillis() % BLINK_PERIOD).toFloat() / BLINK_PERIOD
        return BLINK_MIN_FADE + (1f - BLINK_MIN_FADE) * ((cos(phase * 2f * PI.toFloat()) + 1f) / 2f)
    }

    @Composable
    override fun Content() {
        val list = rows.value
        if (list.isEmpty()) return
        val scale = textScale.coerceAtLeast(0.01f)

        val modifier = hudBackground().padding(padLeft, padTop, padRight, padBottom)

        PolyBox(modifier = modifier) {
            PolyColumn(gap = spacing * scale) {
                for (row in list) Effect(row, scale)
            }
        }
    }

    @Composable
    private fun Effect(row: Row, scale: Float) {
        PolyRow(gap = ICON_GAP * scale) {
            if (row.icon != null) Icon(row.icon, ICON * scale, row.fade)
            if (row.name != null || row.duration != null) {
                PolyColumn(gap = LINE_GAP * scale, modifier = PolyModifier.align(PolyAlign.Center)) {
                    if (row.name != null) Line(row.name, scale, row.fade)
                    if (row.duration != null) Line(row.duration, scale, row.fade)
                }
            }
        }
    }

    @Composable
    private fun Icon(icon: Image, size: Float, fade: Float) {
        PolyCanvas(PolyModifier.size(size, size)) { x, y, w, h ->
            iconPaint.alpha = (255f * fade).toInt().coerceIn(0, 255)
            image(icon, x, y, w, h, iconPaint)
        }
    }

    @Composable
    private fun Line(text: String, scale: Float, fade: Float) {
        val color = PolyColor(faded(textColor, fade), textChroma, textChromaSpeed)
        if (font == Font.Minecraft) {
            PolyMcText(text, color = color, shadow = showShadow, scale = scale)
        } else {
            PolyText(
                text,
                color = color,
                fontSize = FONT_SIZE * scale,
                shadow = showShadow,
                shadowColor = PolyColor(faded(shadowColor, fade), shadowChroma, shadowChromaSpeed),
                shadowOffset = shadowOffsetX,
                font = getPoppinsFontName(),
            )
        }
    }

    private fun faded(color: Int, fade: Float): Int {
        if (fade >= 1f) return color
        val alpha = ((color ushr 24 and 0xFF) * fade).toInt().coerceIn(0, 255)
        return (color and 0xFFFFFF) or (alpha shl 24)
    }

    override fun clone(): Hud = (super.clone() as PotionEffectsHud).also {
        it.rows = mutableStateOf(emptyList())
    }
}
