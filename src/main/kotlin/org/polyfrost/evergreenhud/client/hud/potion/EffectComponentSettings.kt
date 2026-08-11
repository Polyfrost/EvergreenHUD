package org.polyfrost.evergreenhud.client.hud.potion

import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.utils.copy
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.MultiSelectDropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.RadioButton
import org.polyfrost.oneconfig.api.config.v1.annotations.RangeSlider
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch

class EffectComponentSettings : EffectComponentValues {
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
        iconEnabled = other.iconEnabled
        iconBlink = other.iconBlink
        nameEnabled = other.nameEnabled
        nameBlink = other.nameBlink
        nameColor = other.nameColor.copy()
        showAmplifier = other.showAmplifier
        amplifierStyle = other.amplifierStyle
        durationEnabled = other.durationEnabled
        durationBlink = other.durationBlink
        durationColor = other.durationColor.copy()

        showEffects = other.showEffects
        ambientFilter = other.ambientFilter.copyOf()
        categoryFilter = other.categoryFilter.copyOf()
        permanentEffects = other.permanentEffects.copyOf()
        durationRange = other.durationRange.copyOf()
        amplifierRange = other.amplifierRange.copyOf()
        blinkThreshold = other.blinkThreshold
    }
}