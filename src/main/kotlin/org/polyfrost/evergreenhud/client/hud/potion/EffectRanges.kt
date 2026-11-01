package org.polyfrost.evergreenhud.client.hud.potion

import kotlin.math.ceil
import kotlin.math.min

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

/** Only configs older than [RANGE_LISTS] used `[0, 0]` as "off", since later it is a valid level I / 0 s filter */
internal fun EffectComponentSettings.migrateRanges(fromVersion: Int) {
    if (fromVersion < RANGE_LISTS) normalizeLegacyRanges()
}

/** One duration in ticks from each allowed range, or just [default] if the list doesn't restrict anything */
internal fun fallbackDurations(ranges: FloatArray, default: Int): List<Int> =
    if (ranges.isEmpty()) listOf(default)
    else (0 until ranges.size / 2).map { ((ranges[it * 2] + ranges[it * 2 + 1]) * 10f).toInt() }

/** The lowest two amplifiers from each allowed range, or levels I and II if the list doesn't restrict anything */
internal fun fallbackAmplifiers(ranges: FloatArray): List<Int> =
    if (ranges.isEmpty()) listOf(0, 1)
    else (0 until ranges.size / 2).flatMap {
        val low = ceil(ranges[it * 2]).toInt()
        (low..min(low + 1, ranges[it * 2 + 1].toInt()))
    }.distinct()
