package org.polyfrost.evergreenhud.config

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.Exclude
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.gui.pages.ModsPage
import org.polyfrost.evergreenhud.EvergreenHUD

object ModConfig : Config(Mod(EvergreenHUD.NAME, ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "${EvergreenHUD.MODID}.json") {
    @Exclude
    var page: ModsPage? = null
}
