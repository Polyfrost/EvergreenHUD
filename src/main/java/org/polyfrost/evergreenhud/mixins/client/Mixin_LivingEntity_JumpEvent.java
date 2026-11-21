package org.polyfrost.evergreenhud.mixins.client;

import net.minecraft.world.entity.LivingEntity;
import org.polyfrost.evergreenhud.client.hud.keystrokes.LocalJump;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class Mixin_LivingEntity_JumpEvent {
    //? if = 1.8.9 {
    /*@Inject(method = "jump", at = @At("HEAD"))
    *///?} else
    @Inject(method = "jumpFromGround", at = @At("TAIL"))
    private void evergreenhud$onJump(CallbackInfo ci) {
        LocalJump.onJump(this);
    }
}
