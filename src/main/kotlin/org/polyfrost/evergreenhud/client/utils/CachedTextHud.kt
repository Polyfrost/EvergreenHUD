package org.polyfrost.evergreenhud.client.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.compose.render.PolyColor
import org.polyfrost.oneconfig.api.hud.v1.Hud

abstract class CachedTextHud(
    title: String,
    category: Category,
    prefix: String = "$title: ",
    suffix: String = "",
    id: String = "${title.replace(' ', '_').lowercase()}.json",
    protected open val defaultText: String = "",
) : SpacedTextHud(id, title, category, prefix, suffix) {
    protected var currentText: String = defaultText

    private var valueColorState: MutableState<PolyColor?> = mutableStateOf(null)

    private var lastValueState: MutableState<String> = mutableStateOf(defaultText)

    fun updateWithText(text: Any?) {
        this.currentText = text?.toString() ?: defaultText
        updateAndRecalculate()
    }

    override fun getText(): String? = currentText

    override fun defaultPosition(): Pair<Float, Float> = 0f to 0f

    protected open fun valueColor(): PolyColor? = null

    override fun concat(prefix: String, value: String?, suffix: String): String {
        lastValueState.value = value.orEmpty()
        return super.concat(prefix, value, suffix)
    }

    override fun update(): Boolean {
        val result = super.update()
        valueColorState.value = valueColor()
        return result
    }

    @Composable
    override fun Content() {
        val color = valueColorState.value ?: return super.Content()
        HudStyledLines(styledLines(color))
    }

    private fun styledLines(color: PolyColor): List<List<StyledRun>> {
        val value = lastValueState.value
        if (value.isEmpty()) {
            val bare = listOfNotNull(prefix.takeIf { it.isNotEmpty() }, suffix.takeIf { it.isNotEmpty() }).joinToString(concatString)
            return listOf(listOf(run(decorate(bare))))
        }
        return value.split('\n').map { line -> styledLine(line, color) }
    }

    private fun styledLine(value: String, color: PolyColor): List<StyledRun> {
        val runs = ArrayList<StyledRun>(5)
        if (brackets) runs.add(run("["))
        if (prefix.isNotEmpty()) runs.add(run("$prefix$concatString"))
        runs.add(run(value, color))
        if (suffix.isNotEmpty()) runs.add(run("$concatString$suffix"))
        if (brackets) runs.add(run("]"))
        return runs
    }

    private fun run(text: String, color: PolyColor? = null) = StyledRun(text, color, false, false)

    override fun clone(): Hud = (super.clone() as CachedTextHud).also {
        it.valueColorState = mutableStateOf(null)
        it.lastValueState = mutableStateOf(defaultText)
    }
}
