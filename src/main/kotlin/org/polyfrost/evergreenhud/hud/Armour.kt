package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.gui.animations.*
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.universal.*
import cc.polyfrost.oneconfig.platform.Platform
import cc.polyfrost.oneconfig.renderer.TextRenderer
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import org.polyfrost.evergreenhud.config.HudConfig
import kotlin.math.*

//#if MC>=10900
//$$ import net.minecraft.inventory.EntityEquipmentSlot
//#endif

class Armour: HudConfig(Mod("ArmourHud", ModType.HUD), "evergreenhud/armour.json", false) {
    @HUD(name = "Main")
    var hud = ArmourHud()

    init {
        initialize()
    }

    class ArmourHud : BasicHud(true, 1920f - 5, 1080f - 5) {

        @Transient val diamondHelmet = ItemStack(Items.diamond_helmet)
        @Transient val diamondChestplate = ItemStack(Items.diamond_chestplate)
        @Transient val diamondLeggings = ItemStack(Items.diamond_leggings)
        @Transient val diamondBoots = ItemStack(Items.diamond_boots)
        @Transient val diamondSword = ItemStack(Items.diamond_sword)

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

        @Slider(
            name = "Animation Duration",
            min = 0f,
            max = 1000f
        )
        var duration = 5

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

        @Dropdown(
            name = "Extra Info",
            options = ["None", "Durability (Absolute)", "Durability (Percent)", "Name"]
        )
        var extraInfo = 0

        @Color(
            name = "Text Color"
        )
        var textColor = OneColor(255, 255, 255)

        @Dropdown(name = "Text Type", options = ["No Shadow", "Shadow", "Full Shadow"])
        var textType = 0

        @DualOption(
            name = "Text Alignment",
            left = "Left", right = "Right"
        )
        var alignment = true

        @Transient private var actualWidth = 0f
        @Transient private var actualHeight = 0f
        @Transient private var iconsX: ArrayList<Float> = ArrayList()
        @Transient private var itemsY: ArrayList<Float> = ArrayList()
        @Transient private var textsX: ArrayList<Float> = ArrayList()
        @Transient private var animationIconX: ArrayList<Animation?> = ArrayList()
        @Transient private var animationItemY: ArrayList<Animation?> = ArrayList()
        @Transient private var animationTextX: ArrayList<Animation?> = ArrayList()
        @Transient private var lastItems: ArrayList<ItemStack?> = ArrayList()

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            if (lastItems.isEmpty()) initArrayList()
            getItems(example)
            draw(matrices, x, y, scale, getItems(example))
        }

        private fun getItems(example: Boolean) = if (example) {
            arrayListOf<ItemStack>().run {
                if (showHelmet) add(diamondHelmet)
                if (showChestplate) add(diamondChestplate)
                if (showLeggings) add(diamondLeggings)
                if (showBoots) add(diamondBoots)
                if (showMainHand) add(diamondSword)

                if (displayType) reverse()
                return@run this
            }
        } else {
            arrayListOf<ItemStack?>().run {
                val inventory = UMinecraft.getPlayer()!!.inventory
                if (showHelmet) add(inventory.armorInventory[3]) else add(null)
                if (showChestplate) add(inventory.armorInventory[2]) else add(null)
                if (showLeggings) add(inventory.armorInventory[1]) else add(null)
                if (showBoots) add(inventory.armorInventory[0]) else add(null)
                if (showMainHand) add(inventory.getCurrentItem()) else add(null)

                if (displayType) reverse()
                return@run this
            }
        }

        private fun getSize(example: Boolean) =
            arrayListOf<Boolean?>().run {
                getItems(example).forEachIndexed { i: Int, stack: ItemStack ->
                    if (stack != null) add(true)
                }
                return@run this
            }

        private fun initArrayList() {
            for (i in 0..4) {
                iconsX.add(0f)
                itemsY.add(0f)
                textsX.add(0f)
                animationIconX.add(null)
                animationItemY.add(null)
                animationTextX.add(null)
                lastItems.add(null)
            }
        }

        private fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, items: List<ItemStack>) {
            val iconSize = 16f
            val offset = iconSize + padding
            val texts = items.map {
                when (extraInfo) {
                    1 -> if (it != null && it.isItemStackDamageable) (it.maxDamage - it.itemDamage).toString() else ""
                    2 -> if (it != null && it.isItemStackDamageable) "${ceil((it.maxDamage - it.itemDamage).toFloat() / it.maxDamage.toFloat() * 100f).toInt()}%" else ""
                    3 -> if (it != null) it.displayName ?: "" else ""
                    else -> ""
                }.let { text ->
                    text to mc.fontRendererObj.getStringWidth(text)
                }
            }

            val longestWidth = texts.maxOfOrNull { mc.fontRendererObj.getStringWidth(it.first) } ?: 0
            var index = 0
            var preWidth = 0
            var biggestWidth = 0f
            var biggestHeight = 0f
            UGraphics.GL.pushMatrix()
            UGraphics.GL.scale(scale, scale, 1f)
            UGraphics.GL.translate(x / scale, y / scale, 0f)
            items.forEachIndexed { i: Int, stack: ItemStack ->

                val (text, textWidth) = texts[i]
                val itemY = if (type) index * offset else 0
                val iconX = when (alignment) {
                    false -> if (type) longestWidth + if (longestWidth > 0) iconPadding else 0 else preWidth + textWidth + if (textWidth > 0) iconPadding else 0
                    true -> preWidth.toFloat()
                }
                val textX = when (alignment) {
                    false -> if (type) longestWidth - textWidth else preWidth
                    true -> iconSize + if (type) +iconPadding else preWidth + iconPadding
                }

                if (lastItems[i] != stack) {
                    if (lastItems[i] == null) {
                        animationIconX[i] = EaseInOutQuad(0, iconX.toFloat(), iconX.toFloat(), false)
                        animationItemY[i] = EaseInOutQuad(0, itemY.toFloat(), itemY.toFloat(), false)
                        animationTextX[i] = EaseInOutQuad(0, textX.toFloat(), textX.toFloat(), false)
                        iconsX[i] = iconX.toFloat()
                        itemsY[i] = itemY.toFloat()
                        textsX[i] = textX.toFloat()
                    }
                    lastItems[i] = stack
                }

                if (iconX.toFloat() != iconsX[i]) {
                    animationIconX[i] = EaseInOutQuad(duration, iconsX[i], iconX.toFloat(), false)
                    iconsX[i] = iconX.toFloat()
                }
                if (itemY.toFloat() != itemsY[i]) {
                    animationItemY[i] = EaseInOutQuad(duration, itemsY[i], itemY.toFloat(), false)
                    itemsY[i] = itemY.toFloat()
                }
                if (textX.toFloat() != textsX[i]) {
                    animationTextX[i] = EaseInOutQuad(duration, textsX[i], textX.toFloat(), false)
                    textsX[i] = textX.toFloat()
                }

                if (stack == null) return@forEachIndexed

                val thisWidth = if (textWidth > 0){
                    max((animationIconX[i]?.get() ?: 0).toFloat() + iconSize, (animationTextX[i]?.get() ?: 0).toFloat() + textWidth.toFloat())
                }else{
                    (animationIconX[i]?.get() ?: 0).toFloat() + iconSize
                }
                val thisHeight = (animationItemY[i]?.get() ?: 0).toFloat() + iconSize

                if (thisWidth > biggestWidth) biggestWidth = thisWidth
                if (thisHeight > biggestHeight) biggestHeight = thisHeight

                UGraphics.GL.pushMatrix()
                RenderHelper.enableGUIStandardItemLighting()
                UGraphics.GL.translate(animationIconX[i]?.get() ?: iconX.toFloat(), animationItemY[i]?.get() ?: itemY.toFloat(), 0f)
                mc.renderItem.zLevel = 200f
                try {
                    mc.renderItem.renderItemAndEffectIntoGUI(stack, 0, 0)
                    mc.renderItem.renderItemOverlayIntoGUI(mc.fontRendererObj, stack, 0, 0, "")
                    RenderHelper.disableStandardItemLighting()
                    UGraphics.GL.popMatrix()
                    UGraphics.GL.pushMatrix()
                    UGraphics.GL.translate(animationTextX[i]?.get() ?: textX.toFloat(), (animationItemY[i]?.get() ?: itemY.toFloat()) + mc.fontRendererObj.FONT_HEIGHT / 2f, 0f)
                    TextRenderer.drawScaledString(
                        text,
                        0f,
                        0f,
                        textColor.rgb,
                        TextRenderer.TextType.toType(textType),
                        1f
                    )
                    UGraphics.GL.popMatrix()
                } finally {
                    mc.renderItem.zLevel = 0f
                }
                index++
                if (!type) preWidth += offset.toInt() + textWidth + if (textWidth > 0) iconPadding else 0
            }
            UGraphics.GL.popMatrix()
            actualWidth = biggestWidth
            actualHeight = biggestHeight
        }

        override fun getWidth(scale: Float, example: Boolean): Float = actualWidth * scale

        override fun getHeight(scale: Float, example: Boolean): Float = actualHeight * scale

        override fun shouldShow(): Boolean {
            return super.shouldShow() && getSize(false).isNotEmpty()
        }
    }
}