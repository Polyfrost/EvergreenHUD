package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.hud.v1.Hud

/** Grows [Hud.staticW]/[Hud.staticH] to the old size, which had the HUD scale applied twice */
fun Hud.migrateStaticContentSize() {
    val factor = effectiveScale
    if (factor <= 0f || factor == 1f) return
    staticW *= factor
    staticH *= factor
}
