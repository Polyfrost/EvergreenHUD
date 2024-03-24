package org.polyfrost.evergreenhud.config

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.data.Mod
import org.polyfrost.evergreenhud.EvergreenHUD

open class HudConfig(mod: Mod, file: String, enabled: Boolean) : Config(mod, file, enabled) {

    override fun initialize() {
        super.initialize()
        EvergreenHUD.mods.add(mod)
    }

}