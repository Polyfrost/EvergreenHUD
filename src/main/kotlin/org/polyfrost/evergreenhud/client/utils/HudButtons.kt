package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.oneconfig.api.config.v1.annotations.Button
import org.polyfrost.oneconfig.api.hud.v1.Hud

fun Hud.rebindButtons() {
    val tree = tree ?: return
    var cls: Class<*>? = javaClass
    while (cls != null && cls != Hud::class.java) {
        for (method in cls.declaredMethods) {
            if (method.parameterCount != 0 || !method.isAnnotationPresent(Button::class.java)) continue
            method.isAccessible = true
            tree.getProp(method.name)?.addMetadata("runnable", Runnable { method.invoke(this) })
        }
        cls = cls.superclass
    }
}
