package cc.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.*
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe
import cc.polyfrost.oneconfig.libs.universal.*
import cc.polyfrost.oneconfig.renderer.TextRenderer
import cc.polyfrost.oneconfig.utils.dsl.mc
import cc.polyfrost.oneconfig.utils.hypixel.*
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.init.Items
import net.minecraft.inventory.*
import net.minecraft.item.ItemStack
import net.minecraft.network.play.server.S01PacketJoinGame

class BedwarsResource : Config(Mod("Bedwars Resource", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/bedwarsresource.json", false) {

    @HUD(name = "Main")
    var hud = BedwarsResourceHud()

    init {
        initialize()
    }

    class BedwarsResourceHud : BasicHud(true, 1920f - 400, 1080f - 21) {

        init {
            EventManager.INSTANCE.register(this)
        }

        @Transient
        val iron = ItemStack(Items.iron_ingot)

        @Transient
        val gold = ItemStack(Items.gold_ingot)

        @Transient
        val diamond = ItemStack(Items.diamond)

        @Transient
        val emerald = ItemStack(Items.emerald)

        @Switch(
            name = "Show Irons"
        )
        var showIron = true

        @Switch(
            name = "Show Golds"
        )
        var showGold = true

        @Switch(
            name = "Show Diamonds"
        )
        var showDiamond = true

        @Switch(
            name = "Show Emeralds"
        )
        var showEmerald = true

        @Switch(
            name = "Show Ender Chest"
        )
        var showEnderChest = true

        @Switch(
            name = "Hide When Zero"
        )
        var hideZero = true

        @Slider(
            name = "Item Padding",
            min = 0F,
            max = 10F
        )
        var padding = 5

        @Slider(
            name = "Icon Padding",
            min = 0F,
            max = 10F
        )
        var iconPadding = 5

        @DualOption(
            name = "Type",
            left = "Horizontal",
            right = "Vertical"
        )
        var type = false

        @DualOption(
            name = "Display Type",
            left = "Down",
            right = "Up"
        )
        var displayType = false

        @DualOption(
            name = "Text Alignment",
            left = "Left", right = "Right"
        )
        var alignment = true

        @Dropdown(name = "Text Type", options = ["No Shadow", "Shadow", "Full Shadow"])
        var textType = 0

        @Color(
            name = "Text Color"
        )
        var textColor = OneColor(255, 255, 255)

        @Transient
        private var actualWidth = 0F

        @Transient
        private var actualHeight = 0F

        @Transient
        private var size = 0

        @Transient
        private var enderChest: IInventory? = null

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            draw(matrices, x, y, scale, getItems(example), example)
        }

        @Subscribe
        fun onOpenContainer(e: ScreenOpenEvent) {
            if (e.screen !is GuiChest) return
            val chestGUI = e.screen as GuiChest?
            val chestContainer = chestGUI!!.inventorySlots as ContainerChest
            val title = chestContainer.lowerChestInventory.displayName.unformattedText
            if ("Ender Chest" != title) return
            enderChest = chestContainer.lowerChestInventory
        }

        @Subscribe
        fun onWorldLoad(e: ReceivePacketEvent) {
            if (e.packet !is S01PacketJoinGame) return
            enderChest = null
        }

        private fun getItems(example: Boolean) =
            arrayListOf<ItemStack>().run {

                if (showIron) add(iron)
                if (showGold) add(gold)
                if (showDiamond) add(diamond)
                if (showEmerald) add(emerald)

                if (displayType) reverse()
                return@run this
            }

        private fun getAmount() =
            arrayListOf<Int>().run {
                getItems(false).forEachIndexed { i: Int, stack: ItemStack ->
                    var amount = 0
                    for (item in mc.thePlayer.inventory.mainInventory) {
                        if (item != null && item.item == stack.item) {
                            amount += item.stackSize
                        }
                    }
                    if (showEnderChest) for (slot in 0..26) {
                        if (enderChest?.getStackInSlot(slot)?.item != stack.item) continue
                            amount += enderChest?.getStackInSlot(slot)?.stackSize ?: 0
                    }
                    add(amount)
                }
                return@run this
            }

        private fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, items: List<ItemStack>, example: Boolean) {
            val iconSize = 16f

            val offset = iconSize + padding

            val longestWidth = getAmount().maxOfOrNull { mc.fontRendererObj.getStringWidth(it.toString()) } ?: 0

            var lastWidth = 0

            size = 0

            UGraphics.GL.pushMatrix()
            UGraphics.GL.scale(scale, scale, 1f)
            UGraphics.GL.translate(x / scale, y / scale, 0f)
            items.forEachIndexed { i: Int, stack: ItemStack ->

                if (hideZero && getAmount()[i] == 0 && !example) return@forEachIndexed

                val text = getAmount()[i].toString()

                val textWidth = mc.fontRendererObj.getStringWidth(text)

                val itemY = if (type) size * offset else 0

                val iconX = when (alignment) {
                    false -> iconPadding + if (type) longestWidth else lastWidth + textWidth
                    true -> lastWidth
                }

                val textX = when (alignment) {
                    false -> if (type) longestWidth - textWidth else lastWidth
                    true -> iconSize + if (type) iconPadding else lastWidth + iconPadding
                }

                RenderHelper.enableGUIStandardItemLighting()
                mc.renderItem.zLevel = 200f
                mc.renderItem.renderItemAndEffectIntoGUI(stack, iconX, itemY.toInt())
                mc.renderItem.renderItemOverlayIntoGUI(mc.fontRendererObj, stack, 0, 0, "")
                RenderHelper.disableStandardItemLighting()
                TextRenderer.drawScaledString(text,
                    textX.toFloat(),
                    itemY.toFloat() + mc.fontRendererObj.FONT_HEIGHT / 2f,
                    textColor.rgb,
                    TextRenderer.TextType.toType(textType),
                    1f
                )
                size ++
                if (!type) lastWidth += offset.toInt() + textWidth + iconPadding
            }
            UGraphics.GL.popMatrix()
            actualWidth = if (type) longestWidth + iconPadding + iconSize else lastWidth.toFloat() - padding
            actualHeight = if (type) size * offset - padding else 16f
        }

        override fun getWidth(scale: Float, example: Boolean): Float = actualWidth * scale

        override fun getHeight(scale: Float, example: Boolean): Float = actualHeight * scale

        override fun shouldShow(): Boolean {
            return super.shouldShow() && (!hideZero || (getAmount().maxOfOrNull { it } ?: 0) > 0) && (HypixelUtils.INSTANCE.isHypixel && LocrawUtil.INSTANCE.isInGame && LocrawUtil.INSTANCE.locrawInfo?.mapName?.isNotBlank() == true && LocrawUtil.INSTANCE.locrawInfo?.gameType == LocrawInfo.GameType.BEDWARS)
        }
    }
}