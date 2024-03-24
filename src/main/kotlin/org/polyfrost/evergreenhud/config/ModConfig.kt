package org.polyfrost.evergreenhud.config

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.data.*
import org.polyfrost.evergreenhud.EvergreenHUD

object ModConfig : Config(Mod(EvergreenHUD.NAME, ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "${EvergreenHUD.MODID}.json") {
}
