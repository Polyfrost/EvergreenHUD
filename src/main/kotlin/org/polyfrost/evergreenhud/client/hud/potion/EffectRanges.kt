package org.polyfrost.evergreenhud.client.hud.potion

/** Flat `[min0, max0, min1, max1, ...]` range list; an empty list doesn't restrict anything */
internal fun FloatArray.allowsInRanges(value: Float): Boolean =
    isEmpty() || (0 until size / 2).any { value in this[it * 2]..this[it * 2 + 1] }

/** The old single range slider stored "off" as `[0, 0]` */
internal fun FloatArray.isLegacyOff(): Boolean = size == 2 && this[0] == 0f && this[1] == 0f

/** Replaces old `[0, 0]` "off" ranges with the new empty "off" list */
internal fun EffectComponentSettings.normalizeLegacyRanges() {
    if (durationRange.isLegacyOff()) durationRange = floatArrayOf()
    if (amplifierRange.isLegacyOff()) amplifierRange = floatArrayOf()
}
