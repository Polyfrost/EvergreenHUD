package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.Exclude
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.hud.TextHud
import cc.polyfrost.oneconfig.libs.universal.UGraphics
import cc.polyfrost.oneconfig.libs.universal.UMinecraft
import cc.polyfrost.oneconfig.renderer.NanoVGHelper
import cc.polyfrost.oneconfig.renderer.TextRenderer
import cc.polyfrost.oneconfig.utils.color.ColorUtils
import cc.polyfrost.oneconfig.utils.dsl.mc
import org.polyfrost.evergreenhud.mixins.GuiIngameAccessor
import org.polyfrost.evergreenhud.utils.ItemStackUtils.getLore
import kotlin.math.min


class HeldItemLore : Config(Mod("Held Item Lore", ModType.HUD), "evergreenhud/helditemlore.json", false) {
    @HUD(name = "Main")
    var hud = HeldItemLoreHud()

    init {
        initialize()
    }

    class HeldItemLoreHud : TextHud(false, 50, 50) {

        @Switch(name = "Fade Out", description = "If disabled, the held item lore HUD will persist indefinitely.")
        var fadeOut: Boolean = true

        @Switch(name = "Instant Fade", description = "Requires \"Fade Out\" to be enabled, otherwise this toggle is ignored.")
        var instantFade: Boolean = false

        @Switch(name = "Remove Empty Lines")
        var removeEmptyLines: Boolean = false

        @Switch(name = "Skip Item Name")
        var skipItemName: Boolean = false

        // @Number(name = "Extra Seconds of Held Item Lore", min = 0F, max = 60F, description = "The number of extra seconds the tooltip will remain on screen before fading out.")
        // var extraSeconds = 0
        // above line is my attempt at extending the time a tooltip had before fading out -ery

        @Exclude private var opacity = 0 // a place to start in case anyone gets around to implementing gradual fadeout -ery

        @Exclude private final val TOOLTIP: MutableList<String> = mutableListOf(
            "§bExample Item Lore §7(Left click and hold to drag this!)",
            "",
            "§7If you can read this, you're spending",
            "§cWAY §7too much time in §2Minecraft§7.",
            "",
            "§7You need to §bget out of your house",
            "§7and earn some §7§osocial points.",
            "",
            "§c§lGet out there §r§7and live your life.",
            "§7Make friends. Go on a hike. §7§nDo both at once!",
        )

        fun drawLine(line: String?, x: Float, y: Float, c: OneColor, scale: Float) {
            val color = OneColor(
                ColorUtils.setAlpha(
                    c.rgb,
                    min(c.alpha.toDouble(), opacity.toDouble()).toInt()
                ) or (this.opacity shl 24)
            )
            UGraphics.enableBlend()
            TextRenderer.drawScaledString(line, x, y, color.rgb, TextRenderer.TextType.toType(textType), scale)
            UGraphics.disableBlend()
        }

        override fun drawBackground(x: Float, y: Float, width: Float, height: Float, scale: Float) {
            val nanoVGHelper = NanoVGHelper.INSTANCE
            nanoVGHelper.setupAndDraw(true) { vg: Long ->
                val bgColor =
                    ColorUtils.setAlpha(
                        bgColor.rgb,
                        min(bgColor.alpha.toDouble(), opacity.toDouble())
                            .toInt()
                    )
                val borderColor =
                    ColorUtils.setAlpha(
                        borderColor.rgb,
                        min(
                            borderColor.alpha.toDouble(),
                            opacity.toDouble()
                        ).toInt()
                    )
                if (rounded) {
                    nanoVGHelper.drawRoundedRect(vg, x, y, width, height, bgColor, cornerRadius * scale)
                    if (border) nanoVGHelper.drawHollowRoundRect(
                        vg,
                        x - borderSize * scale,
                        y - borderSize * scale,
                        width + borderSize * scale,
                        height + borderSize * scale,
                        borderColor,
                        cornerRadius * scale,
                        borderSize * scale
                    )
                } else {
                    nanoVGHelper.drawRect(vg, x, y, width, height, bgColor)
                    if (border) nanoVGHelper.drawHollowRoundRect(
                        vg,
                        x - borderSize * scale,
                        y - borderSize * scale,
                        width + borderSize * scale,
                        height + borderSize * scale,
                        borderColor,
                        0f,
                        borderSize * scale
                    )
                }
            }
        }

        override fun shouldShow(): Boolean {
            val inGameGUI: GuiIngameAccessor = UMinecraft.getMinecraft().ingameGUI as GuiIngameAccessor

            // val ticksPerSecond = 20
            // val extraTicks = (extraSeconds * ticksPerSecond)
            val remainingTicks = inGameGUI.getRemainingHighlightTicks() // + extraTicks
            var o: Int =
                if (fadeOut) (remainingTicks * 256 / 10)
                else 255
            if (o > 255) o = 255
            opacity = if (instantFade) 255 else o
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
                addXToY(TOOLTIP, lines)
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
            if (itemName.isNotEmpty() && !skipItemName) lines.add("§r$itemName")
            if (itemLore.isNotEmpty()) {
                addXToY(itemLore, lines)
            }
        }

        private fun addXToY(theListToAdd: List<String>, lines: MutableList<String>) {
            if (!removeEmptyLines) lines.addAll(theListToAdd)
            else {
                for (line in theListToAdd) {
                    if (line.isNotEmpty()) {
                        lines.add(line)
                    }
                }
            }
        }
    }

}