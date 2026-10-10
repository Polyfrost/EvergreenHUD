package org.polyfrost.evergreenhud.client.hud.potion

import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.MultiSelectDropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Option
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.RangeSlider
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import java.lang.reflect.Field
import java.util.Objects

const val OVERRIDDEN_OPTIONS = "overriddenOptions"

class EffectComponentSettings : EffectComponentValues {
    @OverrideOptions(
        title = "Overridden Options",
        description = "Options this override changes. Anything not listed falls through to the next matching override, then Global.",
        subcategory = "Overrides",
    )
    var overriddenOptions = emptyArray<String>()

    @Switch(title = "Show Icon", subcategory = "Icon")
    override var iconEnabled = true
    @Switch(title = "Blink Icon", subcategory = "Icon")
    override var iconBlink = true

    @Switch(title = "Show Name", subcategory = "Name")
    override var nameEnabled = true
    @Switch(title = "Blink Name", subcategory = "Name")
    override var nameBlink = true
    @Color(title = "Name Color", subcategory = "Name")
    override var nameColor = PolyColor.rgba(255, 255, 255, 255)
    @Switch(title = "Amplifier", subcategory = "Name")
    override var showAmplifier = true
    @RadioButton(title = "Amplifier Style", options = ["Roman", "Arabic"], subcategory = "Name")
    override var amplifierStyle = ROMAN

    @Switch(title = "Show Duration", subcategory = "Duration")
    override var durationEnabled = true
    @Switch(title = "Blink Duration", subcategory = "Duration")
    override var durationBlink = true
    @Color(title = "Duration Color", subcategory = "Duration")
    override var durationColor = PolyColor.rgba(255, 255, 255, 255)

    @Switch(title = "Show Effect(s)", subcategory = "Filtering")
    override var showEffects = true

    @MultiSelectDropdown(title = "Ambient Effects", subcategory = "Filtering", options = ["Show all ambient", "Show all nonambient"])
    override var ambientFilter = booleanArrayOf(true, true)

    @MultiSelectDropdown(title = "Effect Categories", subcategory = "Filtering", options = ["Show all beneficial", "Show all neutral", "Show all harmful"])
    override var categoryFilter = booleanArrayOf(true, true, true)

    @MultiSelectDropdown(title = "Permanent Effects", subcategory = "Filtering", options = ["Show all permanent", "Show all finite"])
    override var permanentEffects = booleanArrayOf(true, true)

    @RangeSlider(title = "Duration Range", subcategory = "Filtering", min = 0F, max = 500F, step = 1F)
    override var durationRange = floatArrayOf(0f, 0f)

    @RangeSlider(title = "Amplifier Range", subcategory = "Filtering", min = 0F, max = 10F, step = 1F)
    override var amplifierRange = floatArrayOf(0f, 0f)

    @Slider(title = "Blink Threshold (s)", subcategory = "Blinking", min = 0F, max = 60F, step = 1F)
    override var blinkThreshold = 10f

    fun copyFrom(other: EffectComponentSettings) {
        overriddenOptions = other.overriddenOptions.copyOf()
        for (field in LEAF_FIELDS) copyField(field, other)
    }

    fun copyField(name: String, from: EffectComponentSettings) {
        val field = fieldsByName[name] ?: return
        field.set(this, when (val value = field.get(from)) {
            is BooleanArray -> value.copyOf()
            is FloatArray -> value.copyOf()
            is PolyColor -> value.copy()
            else -> value
        })
    }

    fun matches(name: String, other: EffectComponentSettings): Boolean {
        val field = fieldsByName[name] ?: return true
        return Objects.deepEquals(field.get(this), field.get(other))
    }

    fun isDefault(): Boolean = overriddenOptions.isEmpty() && LEAF_FIELDS.all { matches(it, DEFAULTS) }

    companion object {
        val DEFAULTS = EffectComponentSettings()

        private val fieldsByName: Map<String, Field> by lazy {
            EffectComponentSettings::class.java.declaredFields
                .filter { field ->
                    field.name != OVERRIDDEN_OPTIONS && field.declaredAnnotations.any { ann ->
                        ann.annotationClass.java.isAnnotationPresent(Option::class.java)
                    }
                }
                .onEach { it.isAccessible = true }
                .associateBy { it.name }
        }

        val LEAF_FIELDS: List<String> by lazy { fieldsByName.keys.toList() }
    }
}