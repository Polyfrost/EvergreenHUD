package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.utils.dsl.mc
import cc.polyfrost.oneconfig.hud.TextHud
import org.polyfrost.evergreenhud.utils.ItemStackUtils.getLore

class HeldItemLore : Config(Mod("Held Item Lore", ModType.HUD), "evergreenhud/helditemlore.json", false) {
    @HUD(name = "Main")
    var hud = HeldItemLoreHud()

    init {
        initialize()
    }

    class HeldItemLoreHud : TextHud(false, 50, 50) {

        @Switch(
            name = "Fade Out"
        )
        var fadeOut = true

        @Switch(
            name = "Instant Fade"
        )
        var instantFade = false

        @Switch(
            name = "Remove Empty Lines"
        )
        var removeEmptyLines = false

        override fun getLines(lines: MutableList<String>, example: Boolean) {
            if (mc.thePlayer == null) {
                lines.add("Unknown")
                return
            }

            val theHeldItem =
                //#if MC>=11202
                //$$ mc.player.heldItemMainhand
                //#else
                mc.thePlayer.heldItem
            //#endif
            val itemName = theHeldItem.displayName
            val itemLore = theHeldItem.getLore()
            if (itemName.isNotEmpty()) lines.add(itemName)
            if (itemLore.isNotEmpty()) {
                if (!removeEmptyLines) lines.addAll(itemLore)
                else {
                    for (line in itemLore) {
                        if (line.isNotEmpty()) {
                            lines.add(line)
                        }
                    }
                }
            }
        }

    }

}