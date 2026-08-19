package org.polyfrost.evergreenhud.mixins.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewPartialTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class Mixin_LivingEntityRenderer_PlayerPreviewNameTag {

    @Inject(
            //? if >=1.21.4 {
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            //?} else {
            /*method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
            *///?}
            at = @At("HEAD"),
            cancellable = true
    )
    private void evergreenhud$hideNameTagInPlayerPreview(
            LivingEntity entity,
            //? if >=1.21.4
            double distanceToCameraSq,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (PlayerPreviewPartialTick.getPlayerPreviewPartialTick() >= 0.0F) {
            cir.setReturnValue(PlayerPreviewPartialTick.getPlayerPreviewNameTag());
        }
    }

}
