package cc.polyfrost.evergreenhud.hud

import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.core.OneColor
import cc.polyfrost.oneconfig.config.data.InfoType
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
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

//#if MC>=10900
//$$ import net.minecraft.inventory.EntityEquipmentSlot
//#endif

class Armour: Config(Mod("ArmourHud", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/armour.json", false) {
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

        @Info(text = "i know this is terrible please beg xander to help me", type = InfoType.INFO)
        var balls = null

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

        @DualOption(
            name = "Display Type",
            left = "Down", //lol
            right = "Up"
        )
        var displayType = false //todo why the hell is this a boolean diamond???

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

        @Dropdown(
            name = "Text Alignment",
            options = ["Left", "Right"]
        )
        var alignment = 0

        @Transient private var actualWidth = 5F
        @Transient private var actualHeight = 5F

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
                //$$ if (showHelmet) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.HEAD).let { if (!it.isEmpty) add(it) }
                //$$ if (showChestplate) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.CHEST).let { if (!it.isEmpty) add(it) }
                //$$ if (showLeggings) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.LEGS).let { if (!it.isEmpty) add(it) }
                //$$ if (showBoots) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.FEET).let { if (!it.isEmpty) add(it) }
                //$$ if (showMainHand) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND).let { if (!it.isEmpty) add(it) }
                //$$ if (showOffhand) UMinecraft.getPlayer()!!.getItemStackFromSlot(EntityEquipmentSlot.OFFHAND).let { if (!it.isEmpty) add(it) }
                //#endif

                if (displayType) reverse()
                return@run this
            }
        }

        private fun draw(matrices: UMatrixStack?, x: Float, y: Float, scale: Float, items: List<ItemStack>) {

            val offset = 16f + padding

            val texts = items.map {
                when (extraInfo) {
                    1 -> if (it.isItemStackDamageable) (it.maxDamage - it.itemDamage).toString() else ""
                    2 -> if (it.isItemStackDamageable) "${(it.maxDamage - it.itemDamage) / it.maxDamage * 100}%" else ""
                    3 -> it.displayName ?: ""
                    else -> ""
                }.let { text ->
                    text to mc.fontRendererObj.getStringWidth(text)
                }
            }
            actualWidth = (texts.maxOfOrNull { mc.fontRendererObj.getStringWidth(it.first) } ?: 0) + 2F + 16
            actualHeight = items.size * offset - padding * 2

            val itemX = when (alignment) {
                0 -> x + 2f
                1 -> x + actualWidth - 16f + 6f
                else -> error("Unknown alignment: $alignment")
            }
            val textAnchor = when (alignment) {
                0 -> 2f + 16f * scale
                1 -> actualWidth - 16f - 2f
                else -> error("Unknown alignment: $alignment")
            }
            items.forEachIndexed { i: Int, stack: ItemStack ->
                val itemY = y + offset * i * scale

                UGraphics.GL.pushMatrix()
                UGraphics.GL.translate((itemX - 4f).toDouble(), (itemY - 4f).toDouble(), 0.0)
                UGraphics.GL.scale(scale, scale, 1f)
                RenderHelper.enableGUIStandardItemLighting()
                mc.renderItem.zLevel = 200f
                mc.renderItem.renderItemAndEffectIntoGUI(stack, 0, 0)
                mc.renderItem.renderItemOverlayIntoGUI(mc.fontRendererObj, stack, 0, 0, "")
                RenderHelper.disableStandardItemLighting()
                UGraphics.GL.popMatrix()

                val (text, textWidth) = texts[i]

                val textX = when (alignment) {
                    0 -> x + textAnchor
                    1 -> x + textAnchor - textWidth
                    else -> error("Unknown alignment: $alignment")
                }

                UGraphics.GL.pushMatrix()
                UGraphics.GL.translate(textX.toDouble(), itemY.toDouble(), 0.0)
                TextRenderer.drawScaledString(text, 0f, 0f, textColor.rgb, TextRenderer.TextType.toType(textType), scale)
                UGraphics.GL.popMatrix()
            }
        }

        override fun getWidth(scale: Float, example: Boolean): Float = actualWidth

        override fun getHeight(scale: Float, example: Boolean): Float = actualHeight

    }
}