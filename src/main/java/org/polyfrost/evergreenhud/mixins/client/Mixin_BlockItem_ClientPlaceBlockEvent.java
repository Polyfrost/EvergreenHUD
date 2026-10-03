package org.polyfrost.evergreenhud.mixins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
//? if > 1.8.9 {
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
//?} else {
/*import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.world.level.Level;
*///?}
import org.polyfrost.evergreenhud.client.ClientPlaceBlockEvent;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class Mixin_BlockItem_ClientPlaceBlockEvent {

    //? if > 1.8.9 {
    @Inject(method = "placeBlock", at = @At("RETURN"))
    private void placeBlock(BlockPlaceContext blockPlaceContext, BlockState blockState, CallbackInfoReturnable<Boolean> cir) {
        var player = blockPlaceContext.getPlayer();
        if (player != null && player == Minecraft.getInstance().player) {
            EventManager.INSTANCE.post(new ClientPlaceBlockEvent(player, blockPlaceContext.getLevel()));
        }
    }
    //?} else {
    /*@Inject(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound(DDDLjava/lang/String;FF)V",
                    shift = At.Shift.AFTER
            )
    )
    private void placeBlock(ItemStack stack, Player player, Level world, BlockPos pos, Direction direction, float hitX, float hitY, float hitZ, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient && player == Minecraft.getInstance().player) {
            EventManager.INSTANCE.post(new ClientPlaceBlockEvent(player, world));
        }
    }
    *///?}

}
