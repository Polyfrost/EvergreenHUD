package org.polyfrost.evergreenhud.mixins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.objectweb.asm.Opcodes;
import org.polyfrost.evergreenhud.client.SelectedItemChangedEvent;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class Mixin_NetHandlerPlayClient_SelectedItemChangedEvent {
    @Inject(
            //~ if < 1.21.2 'handleSetHeldSlot' -> 'handleSetCarriedItem'
            method = "handleSetHeldSlot",
            at = @At(
                    //? if <= 1.21.4 {
                    /*value = "FIELD",
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/world/entity/player/Inventory;selected:I",
                    *///?} else {
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;setSelectedSlot(I)V",
                    //?}
                    shift = At.Shift.AFTER
            )
    )
    private void selectedItemChangeCallback(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            EventManager.INSTANCE.post(new SelectedItemChangedEvent(player.getMainHandItem()));
        }
    }
}
