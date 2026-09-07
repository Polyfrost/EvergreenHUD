package org.polyfrost.evergreenhud.mixins.client;

//? if >= 1.21.10
import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewOffscreen;
//? if >= 26.1 && < 26.2 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
*///? }
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >= 26.2 {
@Mixin(value = net.minecraft.client.Minecraft.class, priority = 1500)
//? } else if >= 26.1 {
/*@Mixin(RenderTarget.class)
*///? } else if >= 1.21.10 {
/*@Mixin(targets = "org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx", remap = false)
*///? } else {
/*@Mixin(net.minecraft.client.Minecraft.class)
*///? }
public class Mixin_PlayerPreviewPresent {

    //? if >= 26.3 {
    @Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V"
            )
    )
    private void evergreenhud$renderPlayerPreview(CallbackInfo ci) {
        PlayerPreviewOffscreen.render();
    }
    //? } else if >= 26.2 {
    /*@Inject(
            method = "renderFrame",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/GpuSurface;blitFromTexture(Lcom/mojang/blaze3d/systems/CommandEncoder;Lcom/mojang/blaze3d/textures/GpuTextureView;)V"
            )
    )
    private void evergreenhud$renderPlayerPreview(CallbackInfo ci) {
        PlayerPreviewOffscreen.render();
    }
    *///? } else if >= 26.1 {
    /*@Inject(method = "blitToScreen", at = @At("HEAD"))
    private void evergreenhud$renderPlayerPreview(CallbackInfo ci) {
        RenderTarget self = (RenderTarget) (Object) this;
        Minecraft client = Minecraft.getInstance();
        if (client == null || self != client.getMainRenderTarget()) return;
        PlayerPreviewOffscreen.render();
    }
    *///? } else if >= 1.21.10 {
    /*@Inject(method = "draw", at = @At("TAIL"), remap = false)
    private void evergreenhud$renderPlayerPreview(CallbackInfo ci) {
        PlayerPreviewOffscreen.render();
    }
    *///? }

}
