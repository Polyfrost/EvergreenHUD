package org.polyfrost.evergreenhud.mixins.client;

//? if > 1.8.9 {
import net.minecraft.client.Minecraft;
//? if < 26
//import net.minecraft.client.gui.GuiGraphics;
//? if >= 26
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import org.polyfrost.evergreenhud.client.hooks.ShulkerPreview;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public class Mixin_AbstractContainerScreen_ShulkerPreviewTooltip {

    @Inject(
        //? if < 26
        //method = "render",
        //? if >= 26
        method = "extractRenderState",
        at = @At("TAIL")
    )
    private void evergreenhud$shulkerPreviewTooltip(
            //? if < 26
            //GuiGraphics graphics,
            //? if >= 26
            GuiGraphicsExtractor graphics,
            int mouseX, int mouseY, float partialTick, CallbackInfo ci
    ) {
        ItemStack hovered = ShulkerPreview.consumeHovered(mouseX, mouseY);
        if (hovered == null) return;

        //? if < 1.21.8
        //graphics.renderTooltip(Minecraft.getInstance().font, hovered, mouseX, mouseY);
        //? if >= 1.21.8
        graphics.setTooltipForNextFrame(Minecraft.getInstance().font, hovered, mouseX, mouseY);
    }

}
//?} else {
/*import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SharedConstants.class)
public class Mixin_AbstractContainerScreen_ShulkerPreviewTooltip {}
*///?}
