@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.utils

import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.hud.v1.TextRun
import org.polyfrost.oneconfig.api.hud.v1.toTextRuns
import org.polyfrost.oneconfig.api.platform.v1.Platform
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi
import net.kyori.adventure.text.Component as AdventureComponent

fun Component.toTextRuns(): List<TextRun> {
    val wrapped = Platform.compatibility().wrapPlatformComponent(this) as? AdventureComponent
    if (wrapped != null) return wrapped.toTextRuns()
    val text = string
    return if (text.isEmpty()) emptyList() else listOf(TextRun(text))
}
