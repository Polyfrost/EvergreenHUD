package cc.polyfrost.evergreenhud.hud.customtexts

import cc.polyfrost.oneconfig.config.elements.BasicOption
import cc.polyfrost.oneconfig.gui.elements.BasicButton
import cc.polyfrost.oneconfig.renderer.asset.SVG
import cc.polyfrost.oneconfig.utils.InputHandler
import cc.polyfrost.oneconfig.utils.color.ColorPalette

val PLUS_ICON = SVG("/assets/evergreenhud/plus.svg")

@Suppress("UnstableAPIUsage")
class TextHudsOptionList(private val config: CustomTexts) : BasicOption(null, null, "", "", "General", "", 2) {
    private val addButton = BasicButton(32, 32, PLUS_ICON, BasicButton.ALIGNMENT_CENTER, ColorPalette.PRIMARY)

    init {
        addButton.setClickAction {
            config.hudHolders.add(CustomTextHudHolder(config, CustomTextHud()))
        }
    }

    override fun getHeight() = config.hudHolders.size * 48 + 32

    override fun draw(vg: Long, x: Int, y: Int, inputHandler: InputHandler) {
        var y2 = y

        for (hud in config.hudHolders) {
            hud.drawInList(vg, x, y2, inputHandler)
            y2 += 48
        }

        addButton.draw(vg, x.toFloat(), y2.toFloat(), inputHandler)

        config.planToRemove?.remove()
    }
}