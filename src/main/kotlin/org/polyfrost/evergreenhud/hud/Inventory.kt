package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.*
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe
import cc.polyfrost.oneconfig.libs.universal.*
import cc.polyfrost.oneconfig.libs.universal.UGraphics.GL
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.renderer.*
import net.minecraft.inventory.*
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.polyfrost.evergreenhud.config.HudConfig

private const val VANILLA = false
private val vanillaBackgroundTexture: ResourceLocation = ResourceLocation("textures/gui/container/inventory.png")

class Inventory : HudConfig("Inventory", "evergreenhud/inventory.json", false) {

    @HUD(name = "Player Inventory", category = "Player Inventory")
    var playerInventoryHUD = PlayerInventoryHUD()

    @HUD(name = "Ender Chest", category = "Ender Chest")
    var enderChestHUD = EnderChestHUD()

    init {
        initialize()
    }

    abstract class InventoryHUD(
        enabled: Boolean = false,
        x: Int,
        y: Int,
    ) : BasicHud(enabled, x.toFloat(), y.toFloat()) {

        @DualOption(name = "Background Type", left = "Vanilla", right = "OneConfig")
        protected var backgroundType = false

        @Switch(name = "Dynamic Rows")
        protected var dynamic = false

        @Slider(name = "Items Spacing", min = 0f, max = 10f)
        protected var spacing = 4f

        @Transient
        protected var height = 0F

        override fun draw(matrices: UMatrixStack, x: Float, y: Float, scale: Float, example: Boolean) {
            GL.pushMatrix()
            GL.translate(x, y, 100f)
            GL.scale(scale, scale, 1.0f)
            GlStateManager.enableRescaleNormal()
            UGraphics.enableBlend()
            UGraphics.tryBlendFuncSeparate(770, 771, 1, 0)
            if (backgroundType == VANILLA) {
                mc.textureManager.bindTexture(vanillaBackgroundTexture)
                Gui.drawScaledCustomSizeModalRect(0, 0, 0f, 0f, 176, 7, 176, 7, 256f, 256f)
                Gui.drawScaledCustomSizeModalRect(0, 7, 0f, 83f, 176, 54, 176, 54, 256f, 256f)
                Gui.drawScaledCustomSizeModalRect(0, 61, 0f, 159f, 176, 7, 176, 7, 256f, 256f)
                GL.translate(8f, 8f, 8f)
            }
            RenderHelper.enableGUIStandardItemLighting()

            val padding = if (backgroundType == VANILLA) 18f else 16f + spacing
            var i = 0
            var biggestHeight = 0F
            for (row in 0..2) {
                val itemY = i * padding

                if (dynamic && !getRowAreShownList()[row] && backgroundType) continue

                val thisHeight = itemY + 16f

                if (thisHeight > biggestHeight) biggestHeight = thisHeight

                GL.pushMatrix()
                GL.translate(0f, itemY, 0f)
                for (column in 0..8) {
                    val index = row * 9 + column
                    drawItem(getItem(index))
                    GL.translate(padding, 0f, 0f)
                }
                GL.translate(-padding * 9, padding, 0f)
                GL.popMatrix()

                i++
            }

            height = biggestHeight

            RenderHelper.disableStandardItemLighting()
            UGraphics.disableBlend()
            GlStateManager.disableRescaleNormal()
            UGraphics.enableAlpha()
            GL.popMatrix()
        }

        private fun drawItem(item: ItemStack?) {
            item ?: return
            with(mc.renderItem) {
                renderItemAndEffectIntoGUI(item, 0, 0)
                renderItemOverlayIntoGUI(mc.fontRendererObj, item, 0, 0, null)
            }
        }

        abstract fun getItem(index: Int): ItemStack?

        private fun getRowAreShownList() =
            // what the functional ahh fuck am i reading
            // this entire method is silly
            (0..2).map { row ->
                (0..8).any { column ->
                    getItem(row * 9 + column) != null
                }
            }

        override fun getWidth(scale: Float, example: Boolean): Float =
            if (backgroundType) {
                spacing * 8 + 144f
            } else {
                176f
            }

        override fun getHeight(scale: Float, example: Boolean): Float =
            if (dynamic && !example && backgroundType) {
                height
            } else if (backgroundType) {
                spacing * 2 + 48f
            } else {
                68f
            }

        override fun shouldShow(): Boolean =
            super.shouldShow() && (!dynamic || true in getRowAreShownList())

        override fun shouldDrawBackground() = super.shouldDrawBackground() && backgroundType
    }

    class PlayerInventoryHUD : InventoryHUD(true, 104, 180) {
        override fun getItem(index: Int): ItemStack? =
            mc.thePlayer?.inventory?.mainInventory?.get(index + 9)
    }

    class EnderChestHUD : InventoryHUD(false, 280, 180) {
        @Transient
        private var enderChest: IInventory? = null

        init {
            EventManager.INSTANCE.register(this)
        }

        @Subscribe
        fun onOpenContainer(e: ScreenOpenEvent) {
            // i read this code somewhere else, remove the duplicate 
            if (e.screen !is GuiChest) return
            val chestGUI = e.screen as GuiChest?
            val chestContainer = chestGUI!!.inventorySlots as ContainerChest
            val title = chestContainer.lowerChestInventory.displayName.unformattedText
            if ("Ender Chest" != title) return
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
            // this again probably fixes a bug 
            // use worldload event if needed
            enderChest = null
        }

        override fun getItem(index: Int): ItemStack? = enderChest?.getStackInSlot(index)
    }
}