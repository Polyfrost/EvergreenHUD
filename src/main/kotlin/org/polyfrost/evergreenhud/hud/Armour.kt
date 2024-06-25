package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.universal.UGraphics
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.platform.Platform
import cc.polyfrost.oneconfig.renderer.TextRenderer
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.WorldRenderer
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.init.Items
import net.minecraft.item.Item
import net.minecraft.item.ItemBow
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumChatFormatting
import org.polyfrost.evergreenhud.config.HudConfig
import kotlin.math.ceil

//#if MC>=10900
//$$ import net.minecraft.inventory.EntityEquipmentSlot
//#endif

class Armour: HudConfig("ArmourHud", "evergreenhud/armour.json", false) {
    @HUD(name = "Main")
    var hud = ArmourHud()

    init {
        initialize()
        addDependency("showOffhand", "Minecraft Version 1.9 or later") { Platform.getInstance().minecraftVersion >= 10900 }
    }

    class ArmourHud : BasicHud(true, 1920f - 5, 1080f - 5) {

        @Transient val diamondHelmet = ItemStack(Items.diamond_helmet)
        @Transient val diamondChestplate = ItemStack(Items.diamond_chestplate)
        @Transient val diamondLeggings = ItemStack(Items.diamond_leggings)
        @Transient val diamondBoots = ItemStack(Items.diamond_boots)
        @Transient val diamondSword = ItemStack(Items.diamond_sword)
        //#if MC>=10900
        //$$ @Transient val shield = ItemStack(Items.SHIELD)
        //#endif

        @Switch(
            name = "Show Helmet"
        )
        var showHelmet = true

        @Switch(
            name = "Show Chestplate"
        )
        var showChestplate = true

        @Switch(
            name = "Show Leggings"
        )
        var showLeggings = true

        @Switch(
            name = "Show Boots"
        )
        var showBoots = true

        @Switch(
            name = "Show Main Hand Item"
        )
        var showMainHand = true

        @Switch(
            name = "Show Offhand Item"
        )
        var showOffhand = true

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

        @Switch(
            name = "Reversed"
        )
        var reversed = false

        @Switch(name = "Show durability bar")
        var durabilityBar = true

        @Switch(name = "Show Item Amount")
        var itemAmount = true

        @Dropdown(
            name = "Extra Info",
            options = ["None", "Durability (Absolute)", "Durability (Percent)", "Name"]
        )
        var extraInfo = 0

        @Switch(
            name = "Dynamic Durability Color"
        )
        var dynamicTextColor = false

        @Color(
            name = "Text Color"
        )
        var textColor = OneColor(255, 255, 255)

        @Dropdown(name = "Text Type", options = ["No Shadow", "Shadow", "Full Shadow"])
        var textType = 0

        @DualOption(
            name = "Text Position",
            left = "Left",
            right = "Right",
            size = 2
        )
        var alignment = true

        @Transient private var actualWidth = 5F
        @Transient private var actualHeight = 5F
        @Transient private var translation = 0F
        @Exclude private val COLORS = linkedMapOf(
            10 to "4",
            25 to "c",
            40 to "6",
            60 to "e",
            80 to "7",
            100 to "f"
        )

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            draw(matrices, x, y, scale, getItems(example))
        }

        private fun getItems(example: Boolean) = if (example) {
            arrayListOf<ItemStack>().run {
                if (showHelmet) add(diamondHelmet)
                if (showChestplate) add(diamondChestplate)
                if (showLeggings) add(diamondLeggings)
                if (showBoots) add(diamondBoots)
                if (showMainHand) add(diamondSword)
                //#if MC>=10900
                //$$ if (showOffhand) add(shield)
                //#endif

                if (reversed) reverse()
                return@run this
            }
        } else {
            arrayListOf<ItemStack>().run {
                //#if MC<10900
                val inventory = mc.thePlayer!!.inventory
                if (showHelmet) inventory.armorInventory[3]?.let { add(it) }
                if (showChestplate) inventory.armorInventory[2]?.let { add(it) }
                if (showLeggings) inventory.armorInventory[1]?.let { add(it) }
                if (showBoots) inventory.armorInventory[0]?.let { add(it) }
                @Suppress("UNNECESSARY_SAFE_CALL") // on 1.8 ItemStacks can be null
                if (showMainHand) mc.thePlayer!!.heldItem?.let { add(it) }
                //#else
                //$$ if (showHelmet) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.HEAD).let { if (!it.isEmpty) add(it) }
                //$$ if (showChestplate) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.CHEST).let { if (!it.isEmpty) add(it) }
                //$$ if (showLeggings) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.LEGS).let { if (!it.isEmpty) add(it) }
                //$$ if (showBoots) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.FEET).let { if (!it.isEmpty) add(it) }
                //$$ if (showMainHand) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).let { if (!it.isEmpty) add(it) }
                //$$ if (showOffhand) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND).let { if (!it.isEmpty) add(it) }
                //#endif

                if (reversed) reverse()
                return@run this
            }
        }

        private fun getItemAmount(item: Item): Int {
            return mc.thePlayer.inventory.mainInventory.toMutableList().filter {
                it?.item == item
            }.sumOf {
                //#if MC>=11202
                //$$ item.getCount()
                //#else
                it.stackSize
                //#endif
            }
        }

        private fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, items: List<ItemStack>) {
            val iconSize = 16f
            val offset = iconSize + padding

            val texts = items.map {
                when (extraInfo) {
                    1 -> if (it.isItemStackDamageable) (it.maxDamage - it.itemDamage).toString() else ""
                    2 -> if (it.isItemStackDamageable) "${ceil((it.maxDamage - it.itemDamage).toFloat() / it.maxDamage.toFloat() * 100f).toInt()}%" else ""
                    3 -> it.displayName ?: ""
                    else -> ""
                }.let { text ->
                    text to mc.fontRendererObj.getStringWidth(text)
                }
            }

            val longestWidth = texts.maxOfOrNull { mc.fontRendererObj.getStringWidth(it.first) } ?: 0

            actualWidth = if (type) longestWidth + iconSize else (padding * (items.size - 1)).toFloat()

            actualHeight = if (type) items.size * offset - padding else offset - padding

            translation = 0F

            if (longestWidth > 0 && type) actualWidth += iconPadding

            UGraphics.GL.pushMatrix()
            UGraphics.GL.scale(scale, scale, 1f)
            UGraphics.GL.translate(x / scale, y / scale, 0f)
            items.forEachIndexed { i: Int, stack: ItemStack ->

                var (text, textWidth) = texts[i]

                if (!type) actualWidth += texts[i].second + iconSize + if (textWidth > 0) iconPadding else 0

                val width = if (type) actualWidth else textWidth + iconSize + iconPadding

                val itemY = if (type) i * offset else 0

                val itemX = when (alignment) {
                    false -> width - iconSize - if (textWidth == 0 && !type) iconPadding else 0
                    true -> 0
                }

                val textX = when (alignment) {
                    false -> width - iconSize - textWidth - iconPadding
                    true -> iconSize + iconPadding
                }

                if (!type && i > 0) translation += offset + texts[i - 1].second + if (texts[i - 1].second > 0) iconPadding else 0

                val amount = getItemAmount(Items.arrow).let {
                    if (stack.item is ItemBow && it != 0) it.toString() else null
                }
                RenderHelper.enableGUIStandardItemLighting()
                mc.renderItem.zLevel = 200f
                mc.renderItem.renderItemAndEffectIntoGUI(stack, itemX.toInt() + translation.toInt(), itemY.toInt())
                renderItemOverlayIntoGUI(mc.fontRendererObj, stack, itemX.toInt() + translation.toInt(), itemY.toInt(), amount)
                RenderHelper.disableStandardItemLighting()
                val renderColor = if (dynamicTextColor) java.awt.Color(255, 255, 255).rgb else textColor.rgb

                if (dynamicTextColor && stack.isItemStackDamageable) {
                    val percentage = ceil((stack.maxDamage - stack.itemDamage).toFloat() / stack.maxDamage.toFloat() * 100f).toInt()
                    for (color in COLORS) {
                        if (percentage <= color.key) {
                            text = "§" + color.value + text
                            break
                        }
                    }
                }


                UGraphics.GL.pushMatrix()
                TextRenderer.drawScaledString(
                    text,
                    textX + translation,
                    itemY.toFloat() + mc.fontRendererObj.FONT_HEIGHT / 2f,
                    renderColor,
                    TextRenderer.TextType.toType(textType),
                    1f
                )
                UGraphics.GL.popMatrix()
            }
            UGraphics.GL.popMatrix()
        }

        fun renderItemOverlayIntoGUI(fr: FontRenderer, stack: ItemStack?, xPosition: Int, yPosition: Int, text: String?) {
            if (stack != null) {
                if (durabilityBar && stack.item.showDurabilityBar(stack)) {
                    val health = stack.item.getDurabilityForDisplay(stack)
                    val j = Math.round(13.0 - health * 13.0).toInt()
                    val i = Math.round(255.0 - health * 255.0).toInt()
                    GlStateManager.disableLighting()
                    GlStateManager.disableDepth()
                    GlStateManager.disableTexture2D()
                    GlStateManager.disableAlpha()
                    GlStateManager.disableBlend()
                    val tessellator = Tessellator.getInstance()
                    val worldrenderer = tessellator.worldRenderer
                    this.draw(worldrenderer, xPosition + 2, yPosition + 13, 13, 2, 0, 0, 0, 255)
                    this.draw(worldrenderer, xPosition + 2, yPosition + 13, 12, 1, (255 - i) / 4, 64, 0, 255)
                    this.draw(worldrenderer, xPosition + 2, yPosition + 13, j, 1, 255 - i, i, 0, 255)
                    GlStateManager.enableAlpha()
                    GlStateManager.enableTexture2D()
                    GlStateManager.enableLighting()
                    GlStateManager.enableDepth()
                }

                if (itemAmount && (stack.stackSize != 1 || text != null)) {
                    var s = text ?: stack.stackSize.toString()
                    if (text == null && stack.stackSize < 1) {
                        s = EnumChatFormatting.RED.toString() + stack.stackSize.toString()
                    }

                    GlStateManager.disableLighting()
                    GlStateManager.disableDepth()
                    GlStateManager.disableBlend()
                    fr.drawStringWithShadow(s, (xPosition + 19 - 2 - fr.getStringWidth(s)).toFloat(), (yPosition + 6 + 3).toFloat(), 16777215)
                    GlStateManager.enableLighting()
                    GlStateManager.enableDepth()
                }
            }
        }

        private fun draw(renderer: WorldRenderer, x: Int, y: Int, width: Int, height: Int, red: Int, green: Int, blue: Int, alpha: Int) {
            renderer.begin(7, DefaultVertexFormats.POSITION_COLOR)
            renderer.pos((x + 0).toDouble(), (y + 0).toDouble(), 0.0).color(red, green, blue, alpha).endVertex()
            renderer.pos((x + 0).toDouble(), (y + height).toDouble(), 0.0).color(red, green, blue, alpha).endVertex()
            renderer.pos((x + width).toDouble(), (y + height).toDouble(), 0.0).color(red, green, blue, alpha).endVertex()
            renderer.pos((x + width).toDouble(), (y + 0).toDouble(), 0.0).color(red, green, blue, alpha).endVertex()
            Tessellator.getInstance().draw()
        }

        override fun getWidth(scale: Float, example: Boolean): Float = actualWidth * scale

        override fun getHeight(scale: Float, example: Boolean): Float = actualHeight * scale

        override fun shouldShow(): Boolean {
            return super.shouldShow() && getItems(false).isNotEmpty()
        }
    }
}