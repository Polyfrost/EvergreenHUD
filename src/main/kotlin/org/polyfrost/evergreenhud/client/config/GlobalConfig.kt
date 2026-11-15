@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.config

import org.polyfrost.compose.render.PolyColor
import org.polyfrost.evergreenhud.client.hud.clock.ClockHud
import org.polyfrost.oneconfig.api.config.v1.copy
import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.Property
import org.polyfrost.oneconfig.api.config.v1.annotations.Color
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.hud.v1.Font
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.HudStyles
import org.polyfrost.oneconfig.api.hud.v1.Weight
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

object GlobalConfig : Config(
    "evergreenhud.json",
    "assets/evergreenhud/evergreenhud.svg",
    "EvergreenHUD",
    Category.HUD,
) {

    @Dropdown(title = "Font", subcategory = "Text")
    var font = Font.Minecraft

    @Color(title = "Text Color", subcategory = "Text")
    var textColor = PolyColor(0xFFFFFFFF.toInt())

    @Slider(title = "Text Scale", min = 0.5f, max = 4f, step = 0.1f, subcategory = "Text")
    var textScale = 1f

    @Dropdown(title = "Weight", subcategory = "Text")
    var textWeight = Weight.Regular

    @Dropdown(title = "Case", options = ["Normal", "UPPERCASE", "lowercase"], subcategory = "Text")
    var caseType = 0

    @Switch(title = "Bold", subcategory = "Text")
    var textBold = false

    @Switch(title = "Italic", subcategory = "Text")
    var textItalic = false

    @Switch(title = "Underline", subcategory = "Text")
    var textUnderline = false

    @Switch(title = "Show Background", subcategory = "Background")
    var showBackground = true

    @Color(title = "Background Color", subcategory = "Background")
    var bgColor = PolyColor(0x80000000.toInt())

    @Slider(title = "Corner Radius", min = 0f, max = 20f, step = 1f, subcategory = "Background")
    var bgRadius = 4f

    @Switch(title = "Show Shadow", subcategory = "Shadow")
    var showShadow = false

    @Color(title = "Shadow Color", subcategory = "Shadow")
    var shadowColor = PolyColor(0x40000000)

    @Slider(title = "Shadow Offset X", min = -10f, max = 10f, step = 0.5f, subcategory = "Shadow")
    var shadowOffsetX = 2f

    @Slider(title = "Shadow Offset Y", min = -10f, max = 10f, step = 0.5f, subcategory = "Shadow")
    var shadowOffsetY = 2f

    @Slider(title = "Padding Top", min = 0f, max = 32f, step = 1f, subcategory = "Layout & Scale")
    var padTop = 4f

    @Slider(title = "Padding Bottom", min = 0f, max = 32f, step = 1f, subcategory = "Layout & Scale")
    var padBottom = 4f

    @Slider(title = "Padding Left", min = 0f, max = 32f, step = 1f, subcategory = "Layout & Scale")
    var padLeft = 4f

    @Slider(title = "Padding Right", min = 0f, max = 32f, step = 1f, subcategory = "Layout & Scale")
    var padRight = 4f

    @Switch(
        title = "Use GUI Scale",
        description = "Scale HUDs with Minecraft's GUI scale. Turn this off to use a custom scale.",
        subcategory = "Layout & Scale",
    )
    var useGuiScale = true

    @Slider(title = "Custom Scale", min = 0.5f, max = 4f, step = 0.05f, subcategory = "Layout & Scale")
    var customScale = 1f

    private val appliers: Map<String, (Hud) -> Unit> = linkedMapOf(
        "font" to { h -> h.font = font },
        "textColor" to { h ->
            h.textPolyColor = textColor
            if (h is ClockHud) h.applyGlobalColor(textColor)
        },
        "textScale" to { h -> h.textScale = textScale },
        "textWeight" to { h -> h.textWeight = textWeight },
        "caseType" to { h -> h.caseType = caseType },
        "textBold" to { h -> h.textBold = textBold },
        "textItalic" to { h -> h.textItalic = textItalic },
        "textUnderline" to { h -> h.textUnderline = textUnderline },
        "showBackground" to { h ->
            if (h.hasBackground()) {
                h.showBackground = showBackground
            }
        },
        "bgColor" to { h ->
            if (h.hasBackground()) {
                h.bgPolyColor = bgColor
            }
        },
        "bgRadius" to { h -> if (h.hasBackground()) h.bgRadius = bgRadius },
        "showShadow" to { h -> h.showShadow = showShadow },
        "shadowColor" to { h ->
            h.shadowPolyColor = shadowColor
        },
        "shadowOffsetX" to { h -> h.shadowOffsetX = shadowOffsetX },
        "shadowOffsetY" to { h -> h.shadowOffsetY = shadowOffsetY },
        "padTop" to { h -> h.padTop = padTop },
        "padBottom" to { h -> h.padBottom = padBottom },
        "padLeft" to { h -> h.padLeft = padLeft },
        "padRight" to { h -> h.padRight = padRight },
        "useGuiScale" to { h -> h.useGuiScale = useGuiScale },
        "customScale" to { h -> h.customScale = customScale },
    )

    override fun initialize(byConfigManager: Boolean) {
        super.initialize(byConfigManager)
        if (tree == null) return

        addDependency("bgColor", "showBackground")
        addDependency("bgRadius", "showBackground")
        addDependency("shadowColor", "showShadow")
        addDependency("shadowOffsetX", "showShadow")
        addDependency("shadowOffsetY", "showShadow")
        addDependency("customScale", "Use GUI Scale") {
            if (useGuiScale) Property.Display.DISABLED else Property.Display.SHOWN
        }

        for ((option, apply) in appliers) {
            addCallback(option) { _: Any? ->
                broadcast(apply)
                false
            }
        }
    }

    private fun broadcast(apply: (Hud) -> Unit) = HudStyles.applyToAll(id, apply)
}
