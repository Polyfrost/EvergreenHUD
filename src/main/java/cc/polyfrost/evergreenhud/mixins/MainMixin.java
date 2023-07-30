package cc.polyfrost.evergreenhud.mixins;

import cc.polyfrost.evergreenhud.hook.PlaytimeHook;
import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Main.class)
public class MainMixin {
    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;run()V"))
    private static void onMain(String[] strings, CallbackInfo ci) {
        PlaytimeHook.startTime = System.currentTimeMillis();
    }
}
