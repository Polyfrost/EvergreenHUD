package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.hud.hudlist.HudList
import cc.polyfrost.oneconfig.config.annotations.*
import cc.polyfrost.oneconfig.config.data.*
import cc.polyfrost.oneconfig.config.elements.OptionPage
import cc.polyfrost.oneconfig.hud.TextHud
import org.polyfrost.evergreenhud.config.HudConfig
import java.lang.reflect.Field

class CustomTexts : HudConfig(Mod("Custom Texts", ModType.HUD), "evergreenhud/customtexts.json", false) {
    @CustomOption
    var huds = TextHudList()

    init {
        initialize()
    }

    override fun getCustomOption(
        field: Field, annotation: CustomOption, page: OptionPage, mod: Mod, migrate: Boolean
    ) = huds.addOptionTo(this, page)

    class TextHudList : HudList<CustomTextHud>() {
        override fun newHud() = CustomTextHud()
        override fun getHudName(hud: CustomTextHud) = hud.text
    }

    class CustomTextHud : TextHud(true, 180, 30) {
        @Text(name = "Text")
        var text = "Custom Text"

        override fun getLines(lines: MutableList<String>, example: Boolean) {
            lines.add(text)
        }
    }

}