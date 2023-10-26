package cc.polyfrost.evergreenhud.hud.hudlist

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.elements.BasicOption
import cc.polyfrost.oneconfig.gui.elements.BasicButton
import cc.polyfrost.oneconfig.hud.Hud
import cc.polyfrost.oneconfig.renderer.asset.SVG
import cc.polyfrost.oneconfig.utils.InputHandler
import cc.polyfrost.oneconfig.utils.color.ColorPalette

private val PLUS_ICON = SVG("/assets/evergreenhud/plus.svg")

@Suppress("UnstableAPIUsage")
class HudListOption<T : Hud>(
    val hudList: HudList<T>,
    val config: Config,
    description: String,
    category: String,
    subcategory: String
) : BasicOption(null, null, "", description, category, subcategory, 2) {
    private val addButton = BasicButton(32, 32, PLUS_ICON, BasicButton.ALIGNMENT_CENTER, ColorPalette.PRIMARY)
    private val wrappedList = hudList.mapTo(ArrayList()) { hud ->
        WrappedHud(this, hud)
    }
    private var planToRemove: WrappedHud<T>? = null

    init {
        addButton.setClickAction {
            val hud = hudList.newHud()
            wrappedList.add(WrappedHud(this, hud))
            hudList.add(hud)
        }
    }

    override fun getHeight() = wrappedList.size * 48 + 32

    override fun draw(vg: Long, x: Int, y: Int, inputHandler: InputHandler) {
        var nextY = y

        for (hud in wrappedList) {
            hud.drawInList(vg, x, nextY, inputHandler)
            nextY += 48
        }

        addButton.draw(vg, x.toFloat(), nextY.toFloat(), inputHandler)

        checkToRemove()
    }

    fun planToRemove(hud: WrappedHud<T>) {
        planToRemove = hud
    }

    private fun checkToRemove() {
        val removing = (planToRemove ?: return)
        removing.remove()
        wrappedList.remove(removing)
        hudList.remove(removing.hud)
        planToRemove = null
    }
}