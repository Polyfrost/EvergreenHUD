package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.annotations.Number
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.hud.TextHud
import cc.polyfrost.oneconfig.libs.universal.*
import cc.polyfrost.oneconfig.renderer.*
import cc.polyfrost.oneconfig.utils.color.ColorUtils
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.item.ItemStack
import org.polyfrost.evergreenhud.config.HudConfig
import org.polyfrost.evergreenhud.mixins.GuiIngameAccessor
import org.polyfrost.evergreenhud.utils.ItemStackUtils.getLore
import kotlin.math.min

class HeldItemLore : HudConfig("Held Item Lore", "evergreenhud/helditemlore.json", false) {
    @HUD(name = "Main")
    var hud = HeldItemLoreHud()

    class HeldItemLoreHud : TextHud(true, 50, 50) {

        @Switch(name = "Fade Out", description = "If disabled, the held item lore HUD will persist indefinitely.")
        var fadeOut: Boolean = false

        @Switch(name = "Remove Empty Lines")
        var removeEmptyLines: Boolean = false

        @Switch(name = "Skip Item Name")
        var skipItemName: Boolean = false

        @Number(name = "Stop After Line", min = 0F, max = 100F, description = "The HUD will stop rendering lore lines after this amount. Leave at 0 to render all lines in an item lore.\nThis setting won't count the item's display name as a line.")
         var stopAfterLine: Int = 0

         // @Number(name = "Extra Seconds", min = 0F, max = 60F, description = "The number of extra seconds the tooltip will remain on screen before fading out.")
         // var extraSeconds: Int = 0
        // above line is my attempt at extending the time a tooltip had before fading out -ery

        @Exclude private var opacity = 0

        @Exclude private val TOOLTIP: MutableList<String> = mutableListOf(
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

        override fun drawLine(line: String?, x: Float, y: Float, scale: Float) {
            val color = OneColor(
                ColorUtils.setAlpha(
                    this.color.rgb,
                    min(this.color.alpha.toDouble(), opacity.toDouble()).toInt()
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

        override fun drawAll(matrices: UMatrixStack?, example: Boolean) {
            if (example) {
                opacity = 255
                super.drawAll(matrices, true)
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
            opacity = o
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
                addTextToHUD(TOOLTIP, lines)
                return
            }

            if (!shouldShow()) return

            val theHeldItem: ItemStack =
                //#if MC>=11202
                //$$ mc.player.heldItemMainhand
                //#else
                mc.thePlayer.heldItem
                //#endif
            ?: return
            val itemName = theHeldItem.displayName
            val itemLore = theHeldItem.getLore()
            if (itemName.isNotEmpty() && !skipItemName) lines.add("§r$itemName§r") // §r to avoid coloring either the inventory or enderchest huds
            if (itemLore.isNotEmpty()) {
                addTextToHUD(itemLore, lines)
            }
        }

        private fun addTextToHUD(theListToAdd: List<String>, lines: MutableList<String>) {
            var index = 0
            for (line in theListToAdd) {
                if (line.isEmpty() && removeEmptyLines) continue
                lines.add(line)
                index++
                if (stopAfterLine in 1..index) break
            }
        }
    }

}