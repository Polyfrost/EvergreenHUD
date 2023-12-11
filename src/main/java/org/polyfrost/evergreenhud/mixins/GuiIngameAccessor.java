package org.polyfrost.evergreenhud.mixins;

import net.minecraft.client.gui.GuiIngame;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiIngame.class)
public interface GuiIngameAccessor {

    @Accessor("recordPlaying")
    String getRecordPlaying();

    @Accessor("recordIsPlaying")
    boolean getRecordIsPlaying();

    @Accessor("recordPlayingUpFor")
    int getRecordPlayingUpFor();

    @Accessor("titlesTimer")
    int getTitlesTimer();

    @Accessor("titleFadeIn")
    int getTitleFadeIn();

    @Accessor("titleFadeOut")
    int getTitleFadeOut();

    @Accessor("titleDisplayTime")
    int getTitleDisplayTime();

    @Accessor("displayedTitle")
    String getDisplayedTitle();

    @Accessor("displayedSubTitle")
    String getDisplayedSubTitle();

    @Accessor()
    int getRemainingHighlightTicks();

    @Accessor()
    ItemStack getHighlightingItemStack();
}
