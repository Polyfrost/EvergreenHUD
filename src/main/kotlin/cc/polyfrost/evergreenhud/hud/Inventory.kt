package cc.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.events.EventManager
import cc.polyfrost.oneconfig.events.event.*
import cc.polyfrost.oneconfig.gui.animations.*
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.eventbus.Subscribe
import cc.polyfrost.oneconfig.libs.universal.*
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.inventory.GuiChest
import net.minecraft.client.renderer.*
import net.minecraft.client.renderer.entity.RenderItem
import net.minecraft.inventory.*
import net.minecraft.item.ItemStack
import net.minecraft.network.play.server.S01PacketJoinGame
import net.minecraft.util.ResourceLocation

class Inventory : Config(Mod("Inventory", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/inventory.json", false) {

    @HUD(name = "Player Inventory", category = "Player Inventory")
    var playerInventoryHUD = PlayerInventoryHUD()

    @HUD(name = "Player Inventory", category = "Ender Chest")
    var enderChestHUD = EnderChestHUD()

    init {
        initialize()
    }

    abstract class InventoryHUD(
        x: Int,
        y: Int
    ) : BasicHud(false, x.toFloat(), y.toFloat()){

        @Switch(name = "Dynamic Rows")
        protected var dynamic = false

        @DualOption(name = "Background Type", left = "Vanilla", right = "OneConfig")
        protected var backgroundType = false

        @Slider(name = "Items Spacing", min = 0f, max = 10f)
        protected var spacing = 4f

        @Slider(name = "Animation Duration", min = 0f, max = 1000f)
        protected var duration = 200f

        @Transient protected var size = 0
        @Transient protected var height = 0F
        @Transient protected var itemsX: ArrayList<Float> = ArrayList()
        @Transient protected var itemsY: ArrayList<Float> = ArrayList()
        @Transient protected var lastHasItem: ArrayList<Boolean> = ArrayList()
        @Transient protected var animationsY: ArrayList<Animation?> = ArrayList()
        @Transient protected val Tex: ResourceLocation = ResourceLocation("textures/gui/container/inventory.png")
        @Transient protected var enderChest: IInventory? = null

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            if (itemsY.isEmpty()) initArray()
            UGraphics.GL.pushMatrix()
            UGraphics.GL.translate(x, y, 100f)
            UGraphics.GL.scale(scale, scale, 1.0f)
            GlStateManager.enableRescaleNormal()
            UGraphics.enableBlend()
            UGraphics.tryBlendFuncSeparate(770, 771, 1, 0)
            mc.textureManager.bindTexture(Tex)
            if (!backgroundType){
                Gui.drawScaledCustomSizeModalRect(0, 0, 0f, 0f, 176, 7, 176, 7, 256f, 256f)
                Gui.drawScaledCustomSizeModalRect(0, 7, 0f, 83f, 176, 54, 176, 54, 256f, 256f)
                Gui.drawScaledCustomSizeModalRect(0, 61, 0f, 159f, 176, 7, 176, 7, 256f, 256f)
            }
            RenderHelper.enableGUIStandardItemLighting()
            if (!backgroundType) UGraphics.GL.translate(8f, 8f, 8f)

            val padding = if (backgroundType) 16f + spacing else 18f
            var i = 0
            var biggestHeight = 0F
            for (row in 0..2) {
                val itemY = i * padding

                if (getRows()[row] != lastHasItem[row]){
                    if (!lastHasItem[row]){
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

                UGraphics.GL.pushMatrix()
                UGraphics.GL.translate(0f, translation, 0f)
                for (column in 0..8) {
                    val index = row * 9 + column
                    drawItem(getItem(index))
                    GlStateManager.translate(padding, 0f, 0f)
                }
                GlStateManager.translate(-padding * 9, padding, 0f)
                UGraphics.GL.popMatrix()

                i++
            }

            height = biggestHeight

            RenderHelper.disableStandardItemLighting()
            UGraphics.disableBlend()
            GlStateManager.disableRescaleNormal()
            UGraphics.enableAlpha()
            UGraphics.GL.popMatrix()
        }

        private fun initArray(){
            for(i in 0..2){
                itemsY.add(0f)
                animationsY.add(null)
                lastHasItem.add(false)
            }
            for (i in 0..35){
                itemsX.add(0f)
            }
        }

         private fun drawItem(item: ItemStack?) {
             if (item == null) return
             val itemRenderer: RenderItem = mc.renderItem
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
                            this@InventoryHUD.size ++
                            break
                        }
                    }
                    if (this.size == row) add(false)
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

        override fun shouldDrawBackground(): Boolean {
            return super.shouldDrawBackground() && backgroundType
        }

    }

    class PlayerInventoryHUD : InventoryHUD(400, 700){
        override fun getItem(index: Int): ItemStack? {
            if (mc.thePlayer == null) return null
            return mc.thePlayer.inventory.mainInventory[index + 9]
        }

    }

    class EnderChestHUD : InventoryHUD(600, 700){
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
            if (e.packet !is S01PacketJoinGame) return
            enderChest = null
        }

        override fun getItem(index: Int): ItemStack? {
            return if (enderChest == null) null else enderChest!!.getStackInSlot(index)
        }
    }
}