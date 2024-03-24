package org.polyfrost.evergreenhud.mixins;

import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.gui.OneConfigGui;
import cc.polyfrost.oneconfig.gui.elements.ModCard;
import kotlin.Suppress;
import org.polyfrost.evergreenhud.config.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Suppress(names = "UnstableAPIUsage")
@Mixin(value = ModCard.class, remap = false)
public class ModCardMixin {

    @Shadow @Final private Mod modData;

    @Inject(method = "onClick", at = @At(value = "INVOKE", target = "Lcc/polyfrost/oneconfig/gui/OneConfigGui;openPage(Lcc/polyfrost/oneconfig/gui/pages/Page;)V"), cancellable = true)
    private void page(CallbackInfo ci) {
        if (modData == ModConfig.INSTANCE.mod) {
            OneConfigGui.INSTANCE.openPage(new HudPage());
            ci.cancel();
        }
    }
}
