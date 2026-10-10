package org.polyfrost.evergreenhud.client.hud.potion

import org.polyfrost.compose.render.PolyColor

interface EffectComponentValues {
    val iconEnabled: Boolean
    val iconBlink: Boolean
    val nameEnabled: Boolean
    val nameBlink: Boolean
    val nameColor: PolyColor
    val showAmplifier: Boolean
    val amplifierStyle: Int
    val durationEnabled: Boolean
    val durationBlink: Boolean
    val durationColor: PolyColor

    val showEffects: Boolean
    val ambientFilter: BooleanArray
    val categoryFilter: BooleanArray
    val permanentEffects: BooleanArray
    val durationRange: FloatArray
    val amplifierRange: FloatArray
    val blinkThreshold: Float
}

class LayeredEffectValues(
    private val layers: List<EffectComponentSettings>,
    private val fallback: EffectComponentSettings,
) : EffectComponentValues {
    private fun source(field: String): EffectComponentSettings =
        layers.firstOrNull { field in it.overriddenOptions } ?: fallback

    override val iconEnabled get() = source(EffectComponentSettings::iconEnabled.name).iconEnabled
    override val iconBlink get() = source(EffectComponentSettings::iconBlink.name).iconBlink
    override val nameEnabled get() = source(EffectComponentSettings::nameEnabled.name).nameEnabled
    override val nameBlink get() = source(EffectComponentSettings::nameBlink.name).nameBlink
    override val nameColor get() = source(EffectComponentSettings::nameColor.name).nameColor
    override val showAmplifier get() = source(EffectComponentSettings::showAmplifier.name).showAmplifier
    override val amplifierStyle get() = source(EffectComponentSettings::amplifierStyle.name).amplifierStyle
    override val durationEnabled get() = source(EffectComponentSettings::durationEnabled.name).durationEnabled
    override val durationBlink get() = source(EffectComponentSettings::durationBlink.name).durationBlink
    override val durationColor get() = source(EffectComponentSettings::durationColor.name).durationColor

    override val showEffects get() = source(EffectComponentSettings::showEffects.name).showEffects
    override val ambientFilter get() = source(EffectComponentSettings::ambientFilter.name).ambientFilter
    override val categoryFilter get() = source(EffectComponentSettings::categoryFilter.name).categoryFilter
    override val permanentEffects get() = source(EffectComponentSettings::permanentEffects.name).permanentEffects
    override val durationRange get() = source(EffectComponentSettings::durationRange.name).durationRange
    override val amplifierRange get() = source(EffectComponentSettings::amplifierRange.name).amplifierRange
    override val blinkThreshold get() = source(EffectComponentSettings::blinkThreshold.name).blinkThreshold
}