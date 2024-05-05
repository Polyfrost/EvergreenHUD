package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
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
import org.polyfrost.evergreenhud.config.HudConfig
import kotlin.collections.Map


private val IRON = ItemStack(Items.iron_ingot)
private val GOLD = ItemStack(Items.gold_ingot)
private val DIAMOND = ItemStack(Items.diamond)
private val EMERALD = ItemStack(Items.emerald)
private var enderChest: IInventory? = null

class BedwarsResource : HudConfig("Bedwars Resource", "evergreenhud/bedwarsresource.json", false) {

    @HUD(name = "Main")
    var hud = BedwarsResourceHud()

    init {
        EventManager.INSTANCE.register(this)
    }

    @Subscribe
    fun onOpenContainer(e: ScreenOpenEvent) {
        val chestGui = e.screen as? GuiChest ?: return
        val chestContainer = chestGui.inventorySlots as? ContainerChest ?: return
        val title = chestContainer.lowerChestInventory.displayName.unformattedText
        if (title != "Ender Chest") return
        enderChest = chestContainer.lowerChestInventory
    }

    @Subscribe
    fun onWorldLoad(e: ReceivePacketEvent) {
        if (e.packet !is
                //#if MC>=11202
                //$$ net.minecraft.network.play.server.SPacketJoinGame
                //#else
                net.minecraft.network.play.server.S01PacketJoinGame
        //#endif
        ) return
        enderChest = null
    }

    class BedwarsResourceHud : BasicHud(true, 1920f - 400, 1080f - 21) {
        @Checkbox(
            name = "Show Irons"
        )
        var showIron = true

        @Checkbox(
            name = "Show Golds"
        )
        var showGold = true

        @Checkbox(
            name = "Show Diamonds"
        )
        var showDiamond = true

        @Checkbox(
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

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            draw(x, y, scale, example)
        }

        private val shownItems: List<ItemStack>
            get() = arrayListOf<ItemStack>().apply {
                if (showIron) add(IRON)
                if (showGold) add(GOLD)
                if (showDiamond) add(DIAMOND)
                if (showEmerald) add(EMERALD)
                if (displayType) reverse()
            }

        private fun getItemAmount(item: ItemStack): Int {
            val itemList = mc.thePlayer.inventory.mainInventory.toMutableList()
            enderChest?.itemStackList?.let {
                if (showEnderChest) itemList.addAll(it)
            }

            return itemList.filter {
                it?.item == item.item
            }.sumOf {
                //#if MC>=11202
                //$$ item.getCount()
                //#else
                it.stackSize
                //#endif
            }
        }

        private val IInventory.itemStackList: List<ItemStack>
            get() = (0..26).map { index -> getStackInSlot(index) }

        private fun draw(x: Float, y: Float, scale: Float, example: Boolean) {
            val itemAmountMap: Map<ItemStack, Int> = shownItems.associateWith { getItemAmount(it) }
            val iconSize = 16f
            val offset = iconSize + padding
            val longestWidth = itemAmountMap.maxOfOrNull { (_, amount) ->
                mc.fontRendererObj.getStringWidth(amount.toString())
            } ?: 0
            var lastWidth = 0

            size = 0

            UGraphics.GL.pushMatrix()
            UGraphics.GL.scale(scale, scale, 1f)
            UGraphics.GL.translate(x / scale, y / scale, 0f)
            for ((item, amount) in itemAmountMap) {
                if (hideZero && amount == 0 && !example) continue
                val text = amount.toString()
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
                try {
                    mc.renderItem.renderItemAndEffectIntoGUI(item, iconX, itemY.toInt())
                    mc.renderItem.renderItemOverlayIntoGUI(mc.fontRendererObj, item, 0, 0, "")
                    RenderHelper.disableStandardItemLighting()
                    TextRenderer.drawScaledString(
                        text,
                        textX.toFloat(),
                        itemY.toFloat() + mc.fontRendererObj.FONT_HEIGHT / 2f,
                        textColor.rgb,
                        TextRenderer.TextType.toType(textType),
                        1f
                    )
                } finally {
                    mc.renderItem.zLevel = 0f
                }
                size++
                if (!type) lastWidth += offset.toInt() + textWidth + iconPadding
            }
            UGraphics.GL.popMatrix()
            actualWidth = if (type) longestWidth + iconPadding + iconSize else lastWidth.toFloat() - padding
            actualHeight = if (type) size * offset - padding else 16f
        }

        override fun getWidth(scale: Float, example: Boolean): Float = actualWidth * scale

        override fun getHeight(scale: Float, example: Boolean): Float = actualHeight * scale

        override fun shouldShow(): Boolean = super.shouldShow()
            && (!hideZero || (shownItems.maxOfOrNull { getItemAmount(it) } ?: 0) > 0)
            && HypixelUtils.INSTANCE.isHypixel
            && LocrawUtil.INSTANCE.isInGame
            && LocrawUtil.INSTANCE.locrawInfo?.mapName?.isNotBlank() == true
            && LocrawUtil.INSTANCE.locrawInfo?.gameType == LocrawInfo.GameType.BEDWARS
    }
}