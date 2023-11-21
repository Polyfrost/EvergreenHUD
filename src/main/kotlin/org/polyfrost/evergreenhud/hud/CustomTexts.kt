package org.polyfrost.evergreenhud.hud

import org.polyfrost.evergreenhud.hud.hudlist.HudList
import cc.polyfrost.oneconfig.config.Config
import cc.polyfrost.oneconfig.config.annotations.CustomOption
import cc.polyfrost.oneconfig.config.annotations.Text
import cc.polyfrost.oneconfig.config.data.Mod
import cc.polyfrost.oneconfig.config.data.ModType
import cc.polyfrost.oneconfig.config.elements.OptionPage
import cc.polyfrost.oneconfig.hud.TextHud
import java.lang.reflect.Field

class CustomTexts : Config(Mod("Custom Texts", ModType.HUD, "/assets/evergreenhud/evergreenhud.svg"), "evergreenhud/customtexts.json", false) {
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