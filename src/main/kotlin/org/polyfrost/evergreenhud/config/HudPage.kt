package org.polyfrost.evergreenhud.config

import cc.polyfrost.oneconfig.gui.pages.ModsPage
import cc.polyfrost.oneconfig.utils.InputHandler

class HudPage: ModsPage() {

    override fun drawStatic(vg: Long, x: Int, y: Int, inputHandler: InputHandler?): Int {
        return 0
    }

    override fun getTitle(): String {
        return "EvergreenHUD"
    }

    override fun isBase(): Boolean {
        return false
    }

}