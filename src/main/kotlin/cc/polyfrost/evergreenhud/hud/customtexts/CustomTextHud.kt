package cc.polyfrost.evergreenhud.hud.customtexts

import cc.polyfrost.oneconfig.config.annotations.Text
import cc.polyfrost.oneconfig.hud.TextHud

class CustomTextHud : TextHud(true, 180, 30) {
    @Text(name = "Text")
    var text = "Custom Text"

    override fun getLines(lines: MutableList<String>, example: Boolean) {
        lines.add(text)
    }
}
