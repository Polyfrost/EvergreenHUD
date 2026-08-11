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