package org.polyfrost.evergreenhud.client.hud.armor

//? if > 1.8.9
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.polyfrost.oneconfig.utils.v1.dsl.mc

enum class ArmorPiece(
    val key: String,
    val title: String,
    val legacyToggle: String,
    val inHand: Boolean,
    example: () -> ItemStack,
) {
    HELMET("helmet", "Helmet", "showHelmet", false, { ItemStack(Items.DIAMOND_HELMET) }),
    CHESTPLATE("chestplate", "Chestplate", "showChestplate", false, { ItemStack(Items.DIAMOND_CHESTPLATE) }),
    LEGGINGS("leggings", "Leggings", "showLeggings", false, { ItemStack(Items.DIAMOND_LEGGINGS) }),
    BOOTS("boots", "Boots", "showBoots", false, { ItemStack(Items.DIAMOND_BOOTS) }),
    MAIN_HAND("mainhand", "Main Hand", "showMainHand", true, { ItemStack(Items.DIAMOND_SWORD) }),

    //? if > 1.8.9
    OFF_HAND("offhand", "Off Hand", "showOffhand", true, { ItemStack(Items.SHIELD) }),
    ;

    val example: ItemStack by lazy(example)

    fun equipped(): ItemStack? {
        val player = mc.player ?: return null
        //? if > 1.8.9 {
        val slot = when (this) {
            HELMET -> EquipmentSlot.HEAD
            CHESTPLATE -> EquipmentSlot.CHEST
            LEGGINGS -> EquipmentSlot.LEGS
            BOOTS -> EquipmentSlot.FEET
            MAIN_HAND -> EquipmentSlot.MAINHAND
            OFF_HAND -> EquipmentSlot.OFFHAND
        }
        return player.getItemBySlot(slot).takeIf { !it.isEmpty }
        //?} else {
        /*return when (this) {
            HELMET -> player.getArmor(3)
            CHESTPLATE -> player.getArmor(2)
            LEGGINGS -> player.getArmor(1)
            BOOTS -> player.getArmor(0)
            MAIN_HAND -> player.mainHandItem
        }
        *///?}
    }
}
