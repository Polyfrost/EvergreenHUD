package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.DualOption
import cc.polyfrost.oneconfig.config.annotations.HUD
import cc.polyfrost.oneconfig.config.annotations.Slider
import cc.polyfrost.oneconfig.config.annotations.Switch
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.ReceivePacketEvent
import cc.polyfrost.oneconfig.events.event.ScreenOpenEvent
import cc.polyfrost.oneconfig.gui.animations.Animation
import cc.polyfrost.oneconfig.gui.animations.EaseInOutQuad
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe
import cc.polyfrost.oneconfig.libs.universal.UGraphics
import cc.polyfrost.oneconfig.libs.universal.UGraphics.GL
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.inventory.ContainerChest
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

private const val VANILLA = false
private val vanillaBackgroundTexture: ResourceLocation = ResourceLocation("textures/gui/container/inventory.png")

class Inventory : Config(Mod("Inventory", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/inventory.json", false) {

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
        y: Int
    ) : BasicHud(enabled, x.toFloat(), y.toFloat()){

        @Switch(name = "Dynamic Rows")
        protected var dynamic = false

        @DualOption(name = "Background Type", left = "Vanilla", right = "OneConfig")
        protected var backgroundType = false

        @Slider(name = "Items Spacing", min = 0f, max = 10f)
        protected var spacing = 4f

        @Slider(name = "Animation Duration", min = 0f, max = 1000f)
        protected var duration = 200f

        @Transient
        protected var size = 0

        @Transient
        protected var height = 0F

        @Transient
        protected var itemsX: ArrayList<Float> = ArrayList()

        @Transient
        protected var itemsY: ArrayList<Float> = ArrayList()

        @Transient
        protected var lastHasItem: ArrayList<Boolean> = ArrayList()

        @Transient
        protected var animationsY: ArrayList<Animation?> = ArrayList()

        override fun draw(matrices: UMatrixStack, x: Float, y: Float, scale: Float, example: Boolean) {
            if (itemsY.isEmpty()) initArray()
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

            val padding = if (backgroundType == VANILLA) 16f + spacing else 18f
            var i = 0
            var biggestHeight = 0F
            for (row in 0..2) {
                val itemY = i * padding

                if (getRows()[row] != lastHasItem[row]) {
                    if (!lastHasItem[row]) {
                        animationsY[row] = EaseInOutQuad(0, itemY, itemY, false)
                        itemsY[row] = itemY
                    }
                    lastHasItem[row] = getRows()[row] ?: true
                }

                if (itemsY[row] != itemY) {
                    animationsY[row] = EaseInOutQuad(duration.toInt(), itemsY[row], itemY, false)
                    itemsY[row] = itemY
                }

                if (dynamic && getRows()[row] == false && backgroundType) continue

                val thisHeight = (animationsY[row]?.get() ?: 0).toFloat() + 16f
                val translation = if (backgroundType && dynamic) animationsY[row]?.get() ?: itemY else itemY

                if (thisHeight > biggestHeight) biggestHeight = thisHeight

                GL.pushMatrix()
                GL.translate(0f, translation, 0f)
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

        private fun initArray() {
            for (i in 0..2) {
                itemsY.add(0f)
                animationsY.add(null)
                lastHasItem.add(false)
            }
            for (i in 0..35) {
                itemsX.add(0f)
            }
        }

        private fun drawItem(item: ItemStack?) {
            if (item == null) return
            val itemRenderer = mc.renderItem
            itemRenderer.renderItemAndEffectIntoGUI(item, 0, 0)
            itemRenderer.renderItemOverlayIntoGUI(mc.fontRendererObj, item, 0, 0, null)
        }

        protected abstract fun getItem(index: Int): ItemStack?

        private fun getRows() =
            arrayListOf<Boolean?>().run {
                this@InventoryHUD.size = 0
                for (row in 0..2) {
                    for (column in 0..8) {
                        val index = row * 9 + column
                        if (getItem(index) != null && getItem(index)?.item != null) {
                            add(true)
                            this@InventoryHUD.size++
                            break
                        }
                    }
                    if (this@InventoryHUD.size == row) add(false)
                }
                return@run this
            }

        override fun getWidth(scale: Float, example: Boolean): Float {
            return if (backgroundType) spacing * 8 + 144f else 176f
        }

        override fun getHeight(scale: Float, example: Boolean): Float {
            return if (dynamic && !example && backgroundType) height else if (backgroundType) spacing * 2 + 48f else 68f
        }

        override fun shouldShow(): Boolean {
            getRows()
            return super.shouldShow() && (!dynamic || size > 0)
        }

        override fun shouldDrawBackground() = super.shouldDrawBackground() && backgroundType
    }

    class PlayerInventoryHUD : InventoryHUD(true, 104, 180){

        override fun getItem(index: Int): ItemStack? {
            if (mc.thePlayer == null) return null
            return mc.thePlayer.inventory.mainInventory[index + 9]
        }

    class PlayerInventoryHUD : InventoryHUD(400, 700) {
        override fun getItem(index: Int): ItemStack? =
            mc.thePlayer?.inventory?.mainInventory?.get(index + 9)
    }

    class EnderChestHUD : InventoryHUD(false, 280, 180){
        @Transient
        private var enderChest: IInventory? = null

        init {
            EventManager.INSTANCE.register(this)
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
            if (e.packet !is
                    //#if MC>=11202
                    //$$ net.minecraft.network.play.server.SPacketJoinGame
                    //#else
                    net.minecraft.network.play.server.S01PacketJoinGame
            //#endif
            ) return
            enderChest = null
        }

        override fun getItem(index: Int): ItemStack? = enderChest?.getStackInSlot(index)
    }
}