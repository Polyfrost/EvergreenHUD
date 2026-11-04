package org.polyfrost.evergreenhud.client.hud.potion

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.MultiSelectDropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Option
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.RangeNumberList
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import java.lang.reflect.Field
import java.util.Objects

const val OVERRIDDEN_OPTIONS = "overriddenOptions"
const val SEPARATE_FORMATTING = "separateFormatting"
const val EFFECT_PLACEHOLDER = "#effect"

const val MAX_DURATION_SECONDS = 16_777_216F
const val MAX_AMPLIFIER = 255F

class EffectComponentSettings : EffectComponentValues {
    @Include
    var overriddenOptions = emptyArray<String>()

    @Switch(
        title = "Separate Name/Duration Formatting",
        description = "Format the name and duration separately instead of following the HUD's Bold, Italic and Underline text options.",
        subcategory = "Formatting",
    )
    var separateFormatting = false

    @Switch(title = "Show Icon", subcategory = "Icon")
    override var iconEnabled = true
    @Switch(title = "Blink Icon", subcategory = "Icon")
    override var iconBlink = true
    @Slider(title = "Icon Spacing", description = "Spacing between the effect icon and the text", subcategory = "Icon", min = 0F, max = 10F, step = 1F)
    override var iconSpacing = 3f

    @Switch(title = "Show Name", subcategory = "Name")
    override var nameEnabled = true
    @Text(title = "Custom Name", description = "Use #effect for the effect's name.", subcategory = "Name")
    override var customName = EFFECT_PLACEHOLDER
    @Switch(title = "Blink Name", subcategory = "Name")
    override var nameBlink = true
    @Color(title = "Name Color", subcategory = "Name")
    override var nameColor = PolyColor.rgba(255, 255, 255, 255)
    @Switch(title = "Amplifier", subcategory = "Name")
    override var showAmplifier = true
    @RadioButton(title = "Amplifier Style", options = ["Roman", "Arabic"], subcategory = "Name")
    override var amplifierStyle = ROMAN
    @RadioButton(title = "Case Type", options = ["Aa", "AA", "aa"], subcategory = "Name")
    override var caseType = 0
    @MultiSelectDropdown(title = "Name Formatting", options = ["Bold", "Italic", "Underline"], subcategory = "Name")
    override var nameFormatting = booleanArrayOf(false, false, false)

    @Switch(title = "Show Duration", subcategory = "Duration")
    override var durationEnabled = true
    @Switch(title = "Blink Duration", subcategory = "Duration")
    override var durationBlink = true
    @Color(title = "Duration Color", subcategory = "Duration")
    override var durationColor = PolyColor.rgba(255, 255, 255, 255)
    @MultiSelectDropdown(title = "Duration Formatting", options = ["Bold", "Italic", "Underline"], subcategory = "Duration")
    override var durationFormatting = booleanArrayOf(false, false, false)

    @Switch(title = "Show Effect(s)", subcategory = "Filtering")
    override var showEffects = true

    // replaced by override group conditions, kept so older configs still load for FilterMigration
    @MultiSelectDropdown(title = "Ambient Effects", subcategory = "Filtering", options = ["Show all ambient", "Show all nonambient"])
    var ambientFilter = booleanArrayOf(true, true)

    @MultiSelectDropdown(title = "Effect Categories", subcategory = "Filtering", options = ["Show all beneficial", "Show all neutral", "Show all harmful"])
    var categoryFilter = booleanArrayOf(true, true, true)

    @MultiSelectDropdown(title = "Permanent Effects", subcategory = "Filtering", options = ["Show all permanent", "Show all finite"])
    var permanentEffects = booleanArrayOf(true, true)

    @RangeNumberList(
        title = "Duration Ranges",
        description = "Only show effects with this many seconds left, in any of these ranges. Leave empty to show every duration.",
        subcategory = "Filtering",
        min = 0F,
        max = MAX_DURATION_SECONDS,
        reorderable = false,
        addText = "Add duration range",
    )
    override var durationRange = floatArrayOf()

    @RangeNumberList(
        title = "Amplifier Ranges",
        description = "Only show effects with an amplifier in any of these ranges, where 0 is level I. Leave empty to show every amplifier.",
        subcategory = "Filtering",
        min = 0F,
        max = MAX_AMPLIFIER,
        reorderable = false,
        addText = "Add amplifier range",
    )
    override var amplifierRange = floatArrayOf()

    @Slider(title = "Blink Threshold (s)", subcategory = "Blinking", min = 0F, max = 60F, step = 1F)
    override var blinkThreshold = 10f

    fun copyFrom(other: EffectComponentSettings) {
        overriddenOptions = other.overriddenOptions.copyOf()
        separateFormatting = other.separateFormatting
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

    fun toJson(): JsonObject = JsonObject().also { json ->
        json.add(OVERRIDDEN_OPTIONS, JsonArray().also { array -> overriddenOptions.forEach { array.add(JsonPrimitive(it)) } })
        val values = JsonObject()
        for (name in overriddenOptions) {
            val field = fieldsByName[name] ?: continue
            values.add(name, when (val value = field.get(this)) {
                is BooleanArray -> JsonArray().also { array -> value.forEach { array.add(JsonPrimitive(it)) } }
                is FloatArray -> JsonArray().also { array -> value.forEach { array.add(JsonPrimitive(it)) } }
                is PolyColor -> JsonObject().also {
                    it.addProperty("argb", value.rawArgb)
                    it.addProperty("chroma", value.chroma)
                    it.addProperty("chromaSpeed", value.chromaSpeed)
                }
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                else -> continue
            })
        }
        json.add("values", values)
    }

    fun readJson(json: JsonObject) {
        overriddenOptions = json.getAsJsonArray(OVERRIDDEN_OPTIONS)
            ?.map { it.asString }
            ?.filter { it in fieldsByName }
            ?.toTypedArray()
            ?: emptyArray()
        val values = json.getAsJsonObject("values") ?: return
        for (name in overriddenOptions) {
            val field = fieldsByName.getValue(name)
            val element = values.get(name) ?: continue
            runCatching { readValue(field.type, element) }.getOrNull()?.let { field.set(this, it) }
        }
    }

    private fun readValue(type: Class<*>, element: JsonElement): Any? = when (type) {
        BooleanArray::class.java -> element.asJsonArray.map { it.asBoolean }.toBooleanArray()
        FloatArray::class.java -> element.asJsonArray.map { it.asFloat }.toFloatArray()
        PolyColor::class.java -> element.asJsonObject.let {
            PolyColor(it.get("argb").asInt, it.get("chroma")?.asBoolean ?: false, it.get("chromaSpeed")?.asFloat ?: 1f)
        }
        Boolean::class.javaPrimitiveType, Boolean::class.javaObjectType -> element.asJsonPrimitive.takeIf { it.isBoolean }?.asBoolean
        Int::class.javaPrimitiveType, Int::class.javaObjectType -> element.asJsonPrimitive.takeIf { it.isNumber }?.asInt
        Float::class.javaPrimitiveType, Float::class.javaObjectType -> element.asJsonPrimitive.takeIf { it.isNumber }?.asFloat
        else -> null
    }

    fun isDefault(): Boolean = overriddenOptions.isEmpty() && separateFormatting == DEFAULTS.separateFormatting && LEAF_FIELDS.all { matches(it, DEFAULTS) }

    companion object {
        val DEFAULTS = EffectComponentSettings()

        private val fieldsByName: Map<String, Field> by lazy {
            EffectComponentSettings::class.java.declaredFields
                .filter { field ->
                    field.name != OVERRIDDEN_OPTIONS && field.name != SEPARATE_FORMATTING && field.declaredAnnotations.any { ann ->
                        ann.annotationClass.java.isAnnotationPresent(Option::class.java)
                    }
                }
                .onEach { it.isAccessible = true }
                .associateBy { it.name }
        }

        val LEAF_FIELDS: List<String> by lazy { fieldsByName.keys.toList() }

        val RETIRED_FIELDS = setOf("ambientFilter", "categoryFilter", "permanentEffects")

        /** What can still be shown and overridden */
        val OPTION_FIELDS: List<String> by lazy { LEAF_FIELDS - RETIRED_FIELDS }

        val FORMATTING_FIELDS = setOf("nameFormatting", "durationFormatting")
    }
}