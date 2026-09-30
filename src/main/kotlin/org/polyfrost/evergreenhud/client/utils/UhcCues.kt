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

/**
 * A normal golden apple takes 32 ticks to eat. Finite Regeneration heals when
 * its remaining duration is a multiple of (50 shr amplifier), so the last heal
 * is one interval before expiry. Finish at least one tick after that heal.
 * For Regen II and III, show the cue four ticks early to allow 200 ms to react.
 */
internal fun isGappleReEatWindow(regeneration: Boolean, ticks: Int, infinite: Boolean, amplifier: Int): Boolean {
    if (!regeneration || infinite || ticks <= 0) return false
    // High levels heal every tick; clamp before shifting to avoid JVM shift wrapping.
    val healInterval = (50 shr amplifier.coerceIn(0, 6)).coerceAtLeast(1)
    val eatTicks = 32
    val reactionTicks = if (amplifier in 1..2) 4 else 0
    return ticks <= reactionTicks + eatTicks + healInterval - 1
}
