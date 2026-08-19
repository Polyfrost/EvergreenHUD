package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.evergreenhud.client.utils.HudTextLines
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.hud.v1.Hud
import org.polyfrost.oneconfig.api.hud.v1.TextHud

private const val DEFAULT_TEXT = "Custom Text"

class CustomTextHud : TextHud(
    id = "custom_text.json",
    title = "Custom Text",
    category = Category.INFO,
    prefix = "",
) {
    @Text(title = "Text", description = "Shown as-is. Use new lines for multiple lines.", multiline = true)
    var customText = DEFAULT_TEXT

    private var linesState: MutableState<List<String>> = mutableStateOf(lines())

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun setup() {
        super.setup()
        if (isReal) updateWhenChanged("customText")
    }

    override fun getText(): String = customText.ifEmpty { DEFAULT_TEXT }

    override fun update(): Boolean {
        val result = super.update()
        linesState.value = lines()
        return result
    }

    @Composable
    override fun Content() = HudTextLines(linesState.value)

    private fun lines(): List<String> = getText().split('\n')

    override fun clone(): Hud = (super.clone() as CustomTextHud).also {
        it.linesState = mutableStateOf(it.lines())
    }
}
