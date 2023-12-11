package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.Exclude
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Number
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.TextHud
import cc.polyfrost.oneconfig.libs.universal.UMinecraft
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.polyfrost.evergreenhud.mixins.GuiIngameAccessor
import org.polyfrost.evergreenhud.utils.ItemStackUtils.getLore

class HeldItemLore : Config(Mod("Held Item Lore", ModType.HUD), "evergreenhud/helditemlore.json", false) {
    @HUD(name = "Main")
    var hud = HeldItemLoreHud()

    init {
        initialize()
    }

    class HeldItemLoreHud : TextHud(false, 50, 50) {

        @Switch(name = "Fade Out (Instantly)", description = "If disabled, the Held Item Lore HUD will remain on the screen indefinitely.")
        var fadeOut: Boolean = true

        @Switch(name = "Remove Empty Lines")
        var removeEmptyLines: Boolean = false

        // @Number(name = "Extra Seconds of Held Item Lore", min = 0F, max = 60F, description = "The number of extra seconds the tooltip will remain on screen before fading out.")
        // var extraSeconds = 0
        // above line is my attempt at extending the time a tooltip had before fading out -ery

        @Exclude private var opacity = 0 // a place to start in case anyone gets around to implementing gradual fadeout -ery

        @Exclude private val TOOLTIP = mutableListOf(
            "§bExample Item Lore §7(Left click and hold to drag this!)",
            "",
            "§7If you can read this, you're spending",
            "§cWAY §7too much time in §2Minecraft§7.",
            "",
            "§7You need to get out of your house",
            "§7and earn some §7§osocial points.",
            "",
            "§c§lGet out there §r§7and live your life.",
            "§7Make friends. Go on a hike. §7§nDo both at once!",
        )

        override fun shouldShow(): Boolean {
            val inGameGUI: GuiIngameAccessor = UMinecraft.getMinecraft().ingameGUI as GuiIngameAccessor

            // val ticksPerSecond = 20
            // val extraTicks = (extraSeconds * ticksPerSecond)
            val remainingTicks = inGameGUI.getRemainingHighlightTicks() // + extraTicks
            var o: Int =
                if (fadeOut) (remainingTicks * 256 / 10)
                else 255
            if (o > 255) o = 255
            return o > 0 && super.shouldShow()
        }

        override fun getLines(lines: MutableList<String>, example: Boolean) {
            if (
            //#if MC>=11202
            //$$ mc.player
            //#else
            mc.thePlayer
        //#endif
                == null) {
                lines.add("Unknown")
                return
            }

            if (example) {
                lines.clear()
                if (!removeEmptyLines) lines.addAll(TOOLTIP)
                else {
                    for (line in TOOLTIP) {
                        if (line.isNotEmpty()) {
                            lines.add(line)
                        }
                    }
                }
                return
            }

            if (!shouldShow()) return

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