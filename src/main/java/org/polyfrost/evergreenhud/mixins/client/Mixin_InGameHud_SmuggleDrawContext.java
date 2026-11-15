package org.polyfrost.evergreenhud.mixins.client;

//? if < 26.2 && > 1.8.9
//import net.minecraft.client.gui.Gui;
//? if >= 26.2
import net.minecraft.client.gui.Hud;
//? if = 1.8.9
//import net.minecraft.client.gui.GameGui;
//? if > 1.8.9 && < 1.21.10 {
/*import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
*///?}
//? if < 1.21.10 {
/*import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewOffscreen;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
import org.spongepowered.asm.mixin.Mixin;

//? if = 1.8.9
//@Mixin(GameGui.class)
//? if < 26.2 && > 1.8.9
//@Mixin(Gui.class)
//? if >= 26.2
@Mixin(Hud.class)
public class Mixin_InGameHud_SmuggleDrawContext {

    //? if > 1.8.9 && < 1.21.10 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void evergreenhud$renderPlayerPreview(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        PlayerPreviewOffscreen.render();
    }
    *///?} else if = 1.8.9 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void evergreenhud$renderPlayerPreview(float partialTick, CallbackInfo ci) {
        PlayerPreviewOffscreen.render();
    }
    *///?}

}
