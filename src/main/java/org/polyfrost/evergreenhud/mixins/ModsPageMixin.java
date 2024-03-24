package org.polyfrost.evergreenhud.mixins;

import cc.polyfrost.oneconfig.gui.elements.ModCard;
import cc.polyfrost.oneconfig.gui.pages.ModsPage;
import org.polyfrost.evergreenhud.EvergreenHUD;
import org.polyfrost.evergreenhud.config.HudPage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(value = ModsPage.class, remap = false)
public abstract class ModsPageMixin {

    @Shadow @Final private ArrayList<ModCard> modCards;

    @Inject(method = "reloadMods", at = @At(value = "TAIL"))
    private void remove(CallbackInfo ci) {
        ModsPage page = (ModsPage) ((Object) this);
        List<ModCard> cards = new ArrayList<>(modCards);
        cards.removeIf(card -> EvergreenHUD.Companion.getMods().contains(card.getModData()));
        if (page instanceof HudPage) {
            modCards.removeAll(cards);
        } else {
            modCards.clear();
            modCards.addAll(cards);
        }
    }

    @ModifyConstant(method = "draw", constant = @Constant(intValue = 72))
    private int y(int constant) {
        ModsPage page = (ModsPage) ((Object) this);
        return page instanceof HudPage ? 16 : 72;
    }

}
