package org.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.hud.BasicHud
import cc.polyfrost.oneconfig.libs.universal.UGraphics
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack
import cc.polyfrost.oneconfig.libs.universal.UMinecraft
import cc.polyfrost.oneconfig.platform.Platform
import cc.polyfrost.oneconfig.renderer.TextRenderer
import cc.polyfrost.oneconfig.utils.dsl.mc
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
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
            left = "Left",
            right = "Right",
        )
        var alignment = true

        @Transient private var actualWidth = 5F
        @Transient private var actualHeight = 5F
        @Transient private var translation = 0F

        override fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, example: Boolean) {
            draw(matrices, x, y, scale, getItems(example))
        }

        // alloc an arraylist every FRAME?
        // use a field array with nullable foreach, that is 5 elements long
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

                if (displayType) reverse()
                return@run this
            }
        } else {
            arrayListOf<ItemStack>().run {
                //#if MC<10900
                val inventory = UMinecraft.getPlayer()!!.inventory
                if (showHelmet) inventory.armorInventory[3]?.let { add(it) }
                if (showChestplate) inventory.armorInventory[2]?.let { add(it) }
                if (showLeggings) inventory.armorInventory[1]?.let { add(it) }
                if (showBoots) inventory.armorInventory[0]?.let { add(it) }
                @Suppress("UNNECESSARY_SAFE_CALL") // on 1.8 ItemStacks can be null
                if (showMainHand) UMinecraft.getPlayer()!!.heldItem?.let { add(it) }
                //#else
                //$$ val player = UMinecraft.getPlayer() ?: return
                //$$ if (showHelmet) player.getItemStackFromSlot(EntityEquipmentSlot.HEAD).let { if (!it.isEmpty) add(it) }
                //$$ if (showChestplate) player.getItemStackFromSlot(EntityEquipmentSlot.CHEST).let { if (!it.isEmpty) add(it) }
                //$$ if (showLeggings) player.getItemStackFromSlot(EntityEquipmentSlot.LEGS).let { if (!it.isEmpty) add(it) }
                //$$ if (showBoots) player.getItemStackFromSlot(EntityEquipmentSlot.FEET).let { if (!it.isEmpty) add(it) }
                //$$ if (showMainHand) player.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).let { if (!it.isEmpty) add(it) }
                //$$ if (showOffhand) player.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND).let { if (!it.isEmpty) add(it) }
                //#endif

                if (displayType) reverse()
                return@run this
            }
        }

        private fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, items: List<ItemStack>) {
            val iconSize = 16f
            val offset = iconSize + padding

            // allocate another array, this can be avoided
            // just get the textwidth in the foreach or whatever
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

            // nullable primitives are bad
            // also swear you just calculated text with above here...
            val longestWidth = texts.maxOfOrNull { mc.fontRendererObj.getStringWidth(it.first) } ?: 0

            actualWidth = if (type) longestWidth + iconSize else (padding * (items.size - 1)).toFloat()

            actualHeight = if (type) items.size * offset - padding else offset - padding

            translation = 0F

            if (longestWidth > 0 && type) actualWidth += iconPadding

            UGraphics.GL.pushMatrix()
            UGraphics.GL.scale(scale, scale, 1f)
            UGraphics.GL.translate(x / scale, y / scale, 0f)
            items.forEachIndexed { i: Int, stack: ItemStack ->

                // what why not just iterate over texts instead i am so cunfused
                val (text, textWidth) = texts[i]
                // not gonna discuss this
                // after getting textWidth out, we still go and grab it directly??
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

                // yeah.
                if (!type && i > 0) translation += offset + texts[i - 1].second + if (texts[i - 1].second > 0) iconPadding else 0

                RenderHelper.enableGUIStandardItemLighting()
                mc.renderItem.zLevel = 200f
                mc.renderItem.renderItemAndEffectIntoGUI(stack, itemX.toInt() + translation.toInt(), itemY.toInt())
                mc.renderItem.renderItemOverlayIntoGUI(mc.fontRendererObj, stack, itemX.toInt() + translation.toInt(), itemY.toInt(), null)
                RenderHelper.disableStandardItemLighting()

                UGraphics.GL.pushMatrix()
                TextRenderer.drawScaledString(
                    text,
                    textX + translation,
                    itemY.toFloat() + mc.fontRendererObj.FONT_HEIGHT / 2f,
                    textColor.rgb,
                    // this allocs an array every call so like 4 times a frame
                    TextRenderer.TextType.toType(textType),
                    1f
                )
                UGraphics.GL.popMatrix()
            }
            UGraphics.GL.popMatrix()
        }

        override fun getWidth(scale: Float, example: Boolean): Float = actualWidth * scale

        override fun getHeight(scale: Float, example: Boolean): Float = actualHeight * scale

        override fun shouldShow(): Boolean {
            return super.shouldShow() && getItems(false).isNotEmpty()
        }
    }
}