package org.polyfrost.evergreenhud.client.hooks

import net.fabricmc.loader.api.FabricLoader
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.slf4j.LoggerFactory

object VanillaHudCompat {
    private const val MOD_ID = "vanillahud"

    private val LOGGER = LoggerFactory.getLogger("EvergreenHUD/VanillaHUD Compat")

    val isPresent: Boolean by lazy { FabricLoader.getInstance().isModLoaded(MOD_ID) }

    private val statusEffects: (() -> Hud?)? by lazy { hud("getStatusEffects") }

    val canHideStatusEffects: Boolean get() = statusEffects != null

    private var previousStatusEffectsHidden: Boolean? = null

    fun hideStatusEffects(hide: Boolean) {
        val hud = statusEffects?.invoke() ?: return
        if (hide) {
            if (previousStatusEffectsHidden == null) previousStatusEffectsHidden = hud.hidden
            if (!hud.hidden) hud.hidden = true
        } else {
            val previous = previousStatusEffectsHidden ?: return
            previousStatusEffectsHidden = null
            if (hud.hidden != previous) hud.hidden = previous
        }
    }

    private fun hud(getter: String): (() -> Hud?)? {
        if (!isPresent) return null
        return try {
            val huds = Class.forName("org.polyfrost.vanillahud.hud.Huds")
            val instance = huds.getField("INSTANCE").get(null)
            val method = huds.getMethod(getter)
            var failed = false
            val read: () -> Hud? = read@{
                if (failed) return@read null
                try {
                    method.invoke(instance) as? Hud
                } catch (e: Throwable) {
                    failed = true
                    LOGGER.warn("Failed to read VanillaHUD's hud through {}, its compat option will do nothing", getter, e)
                    null
                }
            }
            read
        } catch (_: NoSuchMethodException) {
            null
        } catch (e: Throwable) {
            LOGGER.warn("Failed to find VanillaHUD's hud through {}, its compat option will do nothing", getter, e)
            null
        }
    }
}
