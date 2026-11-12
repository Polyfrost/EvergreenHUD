package org.polyfrost.evergreenhud.mixins.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.LocalPlayer;
import org.polyfrost.evergreenhud.client.hud.mouse.MouseTurnTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if > 1.8.9 {
import net.minecraft.client.MouseHandler;

@Mixin(MouseHandler.class)
public class Mixin_MouseHandler_TrackMouseTurn {
    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void evergreenhud$trackTurn(LocalPlayer player, double yaw, double pitch, Operation<Void> original) {
        MouseTurnTracker.turn(yaw * 0.15, pitch * 0.15);
        original.call(player, yaw, pitch);
    }
}
//?} else {
/*import net.minecraft.client.render.GameRenderer;

@Mixin(GameRenderer.class)
public class Mixin_MouseHandler_TrackMouseTurn {
    @WrapOperation(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;updateLocalPlayerCamera(FF)V"))
    private void evergreenhud$trackTurn(LocalPlayer player, float yaw, float pitch, Operation<Void> original) {
        MouseTurnTracker.turn(yaw * 0.15, -pitch * 0.15);
        original.call(player, yaw, pitch);
    }
}
*///?}
