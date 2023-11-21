package org.polyfrost.evergreenhud.mixins;

import org.polyfrost.evergreenhud.hud.PlayerPreview;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "club.sk1er.mods.levelhead.render.AboveHeadRender")
public class LevelheadAboveHeadRenderMixin {
    @Dynamic("Levelhead")
    @Inject(method = "render(Lnet/minecraftforge/client/event/RenderLivingEvent$Specials$Post;)V", at = @At("HEAD"), cancellable = true)
    private void onRender(CallbackInfo ci) {
        if (PlayerPreview.Companion.getCancelNametags()) {
            ci.cancel();
        }
    }
}
