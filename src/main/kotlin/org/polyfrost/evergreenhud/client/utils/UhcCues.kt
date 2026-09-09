package org.polyfrost.evergreenhud.client.utils

import kotlin.math.abs
import kotlin.math.atan2

/** Horizontal Minecraft yaw: south is 0 degrees and west is 90 degrees. */
internal fun isFacingOrigin(x: Double, z: Double, yaw: Double, tolerance: Float): Boolean {
    if (x == 0.0 && z == 0.0) return false // No direction to face at the origin.
    val targetYaw = Math.toDegrees(atan2(x, -z))
    val difference = ((yaw - targetYaw) % 360.0 + 540.0) % 360.0 - 180.0
    return abs(difference) <= tolerance.coerceIn(0f, 180f)
}

/** Use unrounded effect ticks so the cue starts exactly at 1.5 seconds. */
internal fun isGappleReEatWindow(regeneration: Boolean, ticks: Int, infinite: Boolean): Boolean =
    regeneration && !infinite && ticks in 1..30
