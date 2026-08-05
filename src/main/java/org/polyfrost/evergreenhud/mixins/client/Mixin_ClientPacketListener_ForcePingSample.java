package org.polyfrost.evergreenhud.mixins.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.polyfrost.evergreenhud.client.hud.PingHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
public class Mixin_ClientPacketListener_ForcePingSample {
    @Unique
    private int evergreen$ticksSinceSample = Integer.MAX_VALUE;

    @ModifyExpressionValue(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/DebugScreenOverlay;showNetworkCharts()Z"
            )
    )
    private boolean evergreen$forcePingSample(boolean original) {
        if (original) return true;
        int interval = PingHud.sampleIntervalTicks();
        if (interval < 0) return false;
        if (evergreen$ticksSinceSample >= interval) {
            evergreen$ticksSinceSample = 0;
            return true;
        }
        evergreen$ticksSinceSample++;
        return false;
    }
}
//?} else {
/*import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SharedConstants.class)
public class Mixin_ClientPacketListener_ForcePingSample {}
*///?}
