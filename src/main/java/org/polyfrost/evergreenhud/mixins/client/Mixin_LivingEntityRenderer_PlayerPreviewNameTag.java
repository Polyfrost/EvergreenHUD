package org.polyfrost.evergreenhud.mixins.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewOffscreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public class Mixin_LivingEntityRenderer_PlayerPreviewNameTag {

    @ModifyReturnValue(
            //? if >=1.21.4 {
            method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",
            //?} else {
            /*method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;)Z",
            *///?}
            at = @At("RETURN")
    )
    private boolean evergreenhud$hideNameTagInPlayerPreview(boolean original) {
        return PlayerPreviewOffscreen.rendering ? PlayerPreviewOffscreen.nameTag : original;
    }

}
