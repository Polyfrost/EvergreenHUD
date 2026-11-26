@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

/** Moves the old content scale out of [Hud.textScale] into [Hud.customScale], dividing lengths that [Hud.customScale] also scales */
fun Hud.migrateContentScale(scaleOwn: (factor: Float) -> Unit = {}) {
    val factor = textScale
    if (factor <= 0f || factor == 1f) return
    customScale *= factor
    textScale = 1f
    padLeft /= factor
    padTop /= factor
    padRight /= factor
    padBottom /= factor
    bgRadius /= factor
    borderSize /= factor
    shadowOffsetX /= factor
    shadowOffsetY /= factor
    if (staticW > 0f) staticW /= factor
    if (staticH > 0f) staticH /= factor
    scaleOwn(factor)
}
