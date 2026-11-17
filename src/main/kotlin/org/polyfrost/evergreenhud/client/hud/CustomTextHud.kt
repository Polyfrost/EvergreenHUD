@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.hud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.oneconfig.api.config.v1.annotations.Text
import org.polyfrost.oneconfig.api.hud.v1.HudText
import org.polyfrost.oneconfig.api.hud.v1.TextHud
import org.polyfrost.oneconfig.api.hud.v1.TextRun
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

private const val DEFAULT_TEXT = "Custom Text"

class CustomTextHud : TextHud(
    id = "custom_text.json",
    title = "Custom Text",
    category = Category.INFO,
    prefix = "",
) {
    @Text(title = "Text", description = "Shown as-is. Use new lines for multiple lines.", multiline = true)
    var customText = DEFAULT_TEXT

    private var linesText: String? = null

    private var linesState: MutableState<List<List<TextRun>>> = mutableStateOf(lines())

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun setup() {
        super.setup()
        if (isReal) updateWhenChanged("customText")
    }

    override fun updateFrequency(): Long = if (isReal) Long.MAX_VALUE else -1L

    override fun getText(): String = customText.ifEmpty { DEFAULT_TEXT }

    override fun update(): Boolean {
        val result = super.update()
        val text = getText()
        if (text != linesText) linesState.value = lines()
        return result
    }

    @Composable
    override fun Content() = HudText(linesState.value)

    private fun lines(): List<List<TextRun>> {
        val text = getText()
        linesText = text
        return text.split('\n').map { listOf(TextRun(it)) }
    }

    override fun onCloned() {
        linesState = mutableStateOf(lines())
    }
}
