package org.polyfrost.evergreenhud.mixins.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewPartialTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InventoryScreen.class)
public class Mixin_InventoryScreen_PlayerPreviewPartialTick {
    @ModifyExpressionValue(
            //? if <1.21.4 {
            /*method = "method_29977",
            *///?} elif <1.21.8 {
            /*method = "method_64045",
            *///?} elif <1.21.11 {
            /*method = "renderEntityInInventory",
            *///?} else {
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            //?}
            at = @At(value = "CONSTANT", args = "floatValue=1.0")
    )
    private static float evergreenhud$playerPreviewPartialTick(float original) {
        float override = PlayerPreviewPartialTick.getPlayerPreviewPartialTick();
        return override < 0.0F ? original : override;
    }
}
