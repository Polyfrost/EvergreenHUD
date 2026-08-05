package org.polyfrost.evergreenhud.mixins.client;

import net.minecraft.client.Minecraft;
//? if > 1.8.9 {
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.network.Connection;
//?} else {
/*import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.options.ServerListEntry;
import net.minecraft.network.packet.s2c.play.LoginS2CPacket;
*///?}
import org.polyfrost.evergreenhud.client.ServerChangedEvent;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//~ if = 1.8.9 'ClientCommonPacketListenerImpl' -> 'ClientPlayNetworkHandler'
@Mixin(ClientCommonPacketListenerImpl.class)
public class Mixin_ClientCommonPacketListenerImpl_ServerChangedEvent {
    //? if > 1.8.9 {
    @Inject(
            method = "<init>",
            at = @At("HEAD")
    )
    private static void evergreen$readServerData(Minecraft minecraft, Connection connection, CommonListenerCookie commonListenerCookie, CallbackInfo ci) {
        var data = commonListenerCookie.serverData();
        if (data == null) {
            EventManager.INSTANCE.post(new ServerChangedEvent(null, null, null));
        } else {
            EventManager.INSTANCE.post(new ServerChangedEvent(
                    data.ip,
                    data.name,
                    data.motd
            ));
        }
    }
    //?} else {
    /*@Inject(method = "handleLogin", at = @At("TAIL"))
    private void evergreen$readServerData(LoginS2CPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.hasSingleplayerServer()) {
            EventManager.INSTANCE.post(new ServerChangedEvent(null, null, null));
            return;
        }

        ServerListEntry data = minecraft.getCurrentServer();
        if (data == null) {
            EventManager.INSTANCE.post(new ServerChangedEvent(null, null, null));
        } else {
            EventManager.INSTANCE.post(new ServerChangedEvent(
                    data.ip,
                    data.name,
                    data.motd
            ));
        }
    }
    *///?}
}
