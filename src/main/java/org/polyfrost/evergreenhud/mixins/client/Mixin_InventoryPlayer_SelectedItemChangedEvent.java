package org.polyfrost.evergreenhud.mixins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.objectweb.asm.Opcodes;
import org.polyfrost.evergreenhud.client.SelectedItemChangedEvent;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class Mixin_InventoryPlayer_SelectedItemChangedEvent {
    @Inject(
            method = {
                    //? if >= 1.21.5 {
                    "setSelectedSlot",
                    //?} elif >= 1.21.4 {
                    /*"setSelectedHotbarSlot",
                    *///?} else
                    //"swapPaint",
                    "pickSlot", "replaceWith"
            },
            at = @At(
                    value = "FIELD",
                    opcode = Opcodes.PUTFIELD,
                    target = "Lnet/minecraft/world/entity/player/Inventory;selected:I",
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
