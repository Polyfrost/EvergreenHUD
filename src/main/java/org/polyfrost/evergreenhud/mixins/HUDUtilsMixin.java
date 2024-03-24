package org.polyfrost.evergreenhud.mixins;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.HUD;
import cc.polyfrost.oneconfig.config.core.ConfigUtils;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.config.elements.OptionPage;
import cc.polyfrost.oneconfig.gui.elements.config.ConfigSlider;
import cc.polyfrost.oneconfig.hud.HUDUtils;
import cc.polyfrost.oneconfig.hud.Hud;
import cc.polyfrost.oneconfig.internal.hud.HudCore;
import org.polyfrost.evergreenhud.hud.Clock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;

@Mixin(value = HUDUtils.class, remap = false)
public class HUDUtilsMixin {

    @Inject(method = "addHudOptions", at = @At("TAIL"))
    private static void hudUtils$modifyOptions(OptionPage page, Field field, Object instance, Config config, CallbackInfo ci) {
        Hud hud = (Hud) ConfigUtils.getField(field, instance);
        if (!(hud instanceof Clock.ClockHud)) return;
        HUD hudAnnotation = field.getAnnotation(HUD.class);
        String category = hudAnnotation.category();
        String subcategory = hudAnnotation.subcategory();
        HudCore.hudOptions.removeIf(HUDUtilsMixin::hudUtils$shouldRemove);
        ConfigUtils.getSubCategory(page, hudAnnotation.category(), hudAnnotation.subcategory()).options.removeIf(HUDUtilsMixin::hudUtils$shouldRemove);
        try {
            ArrayList<Field> fieldArrayList = ConfigUtils.getClassFields(hud.getClass());
            HashMap<String, Field> fields = new HashMap<>();
            for (Field f : fieldArrayList) fields.put(f.getName(), f);
            BasicOption option = new ConfigSlider(fields.get("padding"), hud, "Padding", "The padding of the HUD.", category, subcategory, 0, 10, 0, false);
            option.addDependency(hudAnnotation.name(), hud::isEnabled);
            HudCore.hudOptions.add(option);
            ConfigUtils.getSubCategory(page, hudAnnotation.category(), hudAnnotation.subcategory()).options.add(option);
        }  catch (Exception ignored) {
        }
    }

    @Unique
    private static boolean hudUtils$shouldRemove(BasicOption option) {
        String fieldName = option.getField().getName();
        Object hud = option.getParent();
        if (!(hud instanceof Clock.ClockHud)) return false;
        switch (fieldName) {
            case "cornerRadius":
            case "rounded":
            case "paddingX":
            case "paddingY":
                return true;
        }
        return false;
    }

}
