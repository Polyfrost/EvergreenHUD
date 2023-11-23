package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.SingleTextHud
import cc.polyfrost.oneconfig.utils.dsl.mc

class Biome: Config(Mod("Biome", ModType.HUD), "evergreenhud/biome.json", false) {
    @HUD(name = "Main")
    var hud = BiomeHud()

    init {
        initialize()
    }

    class BiomeHud: SingleTextHud("Biome", true, 400, 50) {
        override fun getText(example: Boolean): String {
            val player = mc.thePlayer ?: return "Unknown"
            
            return mc.theWorld.getBiomeGenForCoords(player.position).biomeName
        }

    }
}
