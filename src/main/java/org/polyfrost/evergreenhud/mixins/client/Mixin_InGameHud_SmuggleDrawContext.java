package org.polyfrost.evergreenhud.mixins.client;

import net.minecraft.client.DeltaTracker;
//? if < 26.2
//import net.minecraft.client.gui.Gui;
//? if >= 26.2
import net.minecraft.client.gui.Hud;
//? if < 26
//import net.minecraft.client.gui.GuiGraphics;
//? if >= 26
import net.minecraft.client.gui.GuiGraphicsExtractor;
//? if < 1.21.10
//import org.polyfrost.evergreenhud.client.hooks.PlayerPreviewOffscreen;
import org.polyfrost.evergreenhud.client.hooks.SmuggledHudDrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if < 26.2
//@Mixin(Gui.class)
//? if >= 26.2
@Mixin(Hud.class)
public class Mixin_InGameHud_SmuggleDrawContext {

    @Inject(
        //? if < 26
        //method = "render",
        //? if >= 26
        method = "extractRenderState",
        at = @At("HEAD")
    )
    private void evergreenhud$smuggleDrawContext(
            //? if < 26
            //GuiGraphics graphics,
            //? if >= 26
            GuiGraphicsExtractor graphics,
            DeltaTracker deltaTracker,
            CallbackInfo ci
    ) {
        SmuggledHudDrawContext.setSmuggledHudPartialTick(deltaTracker.getGameTimeDeltaPartialTick(false));
        //? if < 1.21.10
        //PlayerPreviewOffscreen.render();
    }

}
