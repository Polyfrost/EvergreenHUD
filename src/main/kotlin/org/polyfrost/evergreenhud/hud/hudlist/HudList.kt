package org.polyfrost.evergreenhud.hud.hudlist

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.core.ConfigUtils
import cc.polyfrost.oneconfig.config.elements.BasicOption
import cc.polyfrost.oneconfig.config.elements.OptionPage
import cc.polyfrost.oneconfig.hud.Hud

abstract class HudList<T : Hud> : ArrayList<T>() {
    abstract fun newHud(): T
    abstract fun getHudName(hud: T): String

    fun addOptionTo(config: Config, page: OptionPage, description: String = "", category: String = "General", subcategory: String = ""): BasicOption {
        val option = HudListOption(this, config, description, category, subcategory)
        ConfigUtils.getSubCategory(page, category, subcategory).options.add(option)
        return option
    }
}