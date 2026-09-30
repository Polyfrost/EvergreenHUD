package org.polyfrost.evergreenhud.client.hud

import org.polyfrost.evergreenhud.client.utils.CachedTextHud
import org.polyfrost.evergreenhud.client.utils.shaders.ShaderMod
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import kotlin.time.Duration.Companion.seconds

private const val ZIP = ".zip"

class ShaderHud : CachedTextHud(
    id = "shaders.json",
    title = "Shaders",
    category = Category.INFO,
    prefix = "Shaders: ",
) {
    @Text(title = "No Shaders Text", description = "Shown while shaders are turned off.")
    var noShadersText = "None"

    @Switch(title = "Hide When Disabled", description = "Hide the HUD entirely while shaders are turned off.")
    var hideWhenDisabled = false

    @Switch(title = "Hide File Extension", description = "Trim the .zip from packs which are still zipped up.")
    var hideExtension = true

    @Switch(title = "Show Shader Mod", description = "Show which mod the pack is loaded by, such as Iris.")
    var showShaderMod = false

    override val defaultText: String by ::noShadersText

    private var hasPack = false

    override fun setup() {
        super.setup()

        if (isReal) {
            updateWhenChanged("noShadersText")
            updateWhenChanged("hideWhenDisabled")
            updateWhenChanged("hideExtension")
            updateWhenChanged("showShaderMod")
        }
    }

    override fun getText(): String {
        val mod = ShaderMod.active
        val pack = mod?.packName()

        hasPack = pack != null
        if (mod == null || pack == null) return defaultText

        val name = if (hideExtension && pack.endsWith(ZIP, ignoreCase = true)) pack.dropLast(ZIP.length) else pack
        return if (showShaderMod) "${mod.displayName}: $name" else name
    }

    override fun updateFrequency(): Long = 1.seconds.inWholeNanoseconds

    override fun shouldShow(): Boolean = !hideWhenDisabled || hasPack
}
