@file:OptIn(ExperimentalOneConfigUiApi::class)

package org.polyfrost.evergreenhud.client.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.hud.v1.HudText
import org.polyfrost.oneconfig.api.hud.v1.TextRun
import org.polyfrost.oneconfig.api.ui.v1.ExperimentalOneConfigUiApi

abstract class CachedTextHud(
    title: String,
    category: Category,
    prefix: String = "$title: ",
    suffix: String = "",
    id: String = "${title.replace(' ', '_').lowercase()}.json",
    protected open val defaultText: String = "",
) : SpacedTextHud(id, title, category, prefix, suffix) {
    protected var currentText: String = defaultText

    private var lastValue: String = defaultText

    private var styledLinesState: MutableState<List<List<TextRun>>?> = mutableStateOf(null)

    private var styledKey: StyleKey? = null

    fun updateWithText(text: Any?) {
        this.currentText = text?.toString() ?: defaultText
        updateAndRecalculate()
    }

    override fun getText(): String? = currentText

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    override fun updateFrequency(): Long = if (isReal) Long.MAX_VALUE else -1L

    protected open fun valueColor(): PolyColor? = null

    override fun concat(prefix: String, value: String?, suffix: String): String {
        lastValue = value.orEmpty()
        return super.concat(prefix, value, suffix)
    }

    override fun update(): Boolean {
        val result = super.update()
        val color = valueColor()
        if (color == null) {
            styledKey = null
            styledLinesState.value = null
            return result
        }
        val key = StyleKey(lastValue, color, prefix, suffix, brackets, concatString)
        if (key != styledKey) {
            styledKey = key
            styledLinesState.value = styledLines(color)
        }
        return result
    }

    @Composable
    override fun Content() {
        val lines = styledLinesState.value ?: return super.Content()
        HudText(lines)
    }

    private fun styledLines(color: PolyColor): List<List<TextRun>> {
        val value = lastValue
        if (value.isEmpty()) {
            val bare = listOfNotNull(prefix.takeIf { it.isNotEmpty() }, suffix.takeIf { it.isNotEmpty() }).joinToString(concatString)
            return listOf(listOf(run(decorate(bare))))
        }
        return value.split('\n').map { line -> styledLine(line, color) }
    }

    private fun styledLine(value: String, color: PolyColor): List<TextRun> {
        val runs = ArrayList<TextRun>(5)
        if (brackets) runs.add(run("["))
        if (prefix.isNotEmpty()) runs.add(run("$prefix$concatString"))
        runs.add(run(value, color))
        if (suffix.isNotEmpty()) runs.add(run("$concatString$suffix"))
        if (brackets) runs.add(run("]"))
        return runs
    }

    private fun run(text: String, color: PolyColor? = null) = TextRun(text, color)

    override fun onCloned() {
        super.onCloned()
        lastValue = defaultText
        styledKey = null
        styledLinesState = mutableStateOf(null)
    }

    private data class StyleKey(
        val value: String,
        val color: PolyColor,
        val prefix: String,
        val suffix: String,
        val brackets: Boolean,
        val separator: String,
    )
}
