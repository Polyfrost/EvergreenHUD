package org.polyfrost.evergreenhud.config

import cc.polyfrost.oneconfig.gui.OneConfigGui
import cc.polyfrost.oneconfig.gui.pages.ModsPage
import cc.polyfrost.oneconfig.renderer.NanoVGHelper
import cc.polyfrost.oneconfig.renderer.font.Fonts
import cc.polyfrost.oneconfig.utils.InputHandler
import cc.polyfrost.oneconfig.utils.SearchUtils
import org.polyfrost.evergreenhud.mixins.ModsPageAccessor
import java.awt.Color
import java.util.*

class HudPage: ModsPage() {

    override fun drawStatic(vg: Long, x: Int, y: Int, inputHandler: InputHandler?): Int {
        return 0
    }

    override fun draw(vg: Long, x: Int, y: Int, inputHandler: InputHandler?) {
        val accessor: ModsPageAccessor = ModConfig.page as ModsPageAccessor
        val filter = if (OneConfigGui.INSTANCE == null) "" else OneConfigGui.INSTANCE.searchValue.lowercase(Locale.getDefault()).trim { it <= ' ' }
        var iX = x + 16
        var iY = y + 16
        val finalModCards = ArrayList(accessor.modCards)
        for (modCard in finalModCards) {
            if (filter == "" || SearchUtils.isSimilar(modCard.modData.name, filter)) {
                if (iY + 135 >= y - scroll && iY <= y + 728 - scroll) modCard.draw(vg, iX.toFloat(), iY.toFloat(), inputHandler)
                iX += 260
                if (iX > x + 796) {
                    iX = x + 16
                    iY += 135
                }
            }
        }
        accessor.setSize(iY - y + 135)
    }

    override fun getTitle(): String {
        return "EvergreenHUD"
    }

    override fun isBase(): Boolean {
        return false
    }

}