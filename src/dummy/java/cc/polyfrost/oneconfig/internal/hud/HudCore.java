package cc.polyfrost.oneconfig.internal.hud;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.hud.Hud;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HudCore {
    public static boolean editing;
    public static final ConcurrentHashMap<Map.Entry<Field, Object>, Hud> huds = new ConcurrentHashMap<>();
    public static final ArrayList<BasicOption> hudOptions = new ArrayList<>();
}
