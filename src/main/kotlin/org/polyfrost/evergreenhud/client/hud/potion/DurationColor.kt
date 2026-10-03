package org.polyfrost.evergreenhud.client.hud.potion

internal fun isDurationColorWindow(ticks: Int, infinite: Boolean, seconds: Float): Boolean =
    !infinite && ticks > 0 && seconds.isFinite() && seconds > 0f && ticks / 20f <= seconds
