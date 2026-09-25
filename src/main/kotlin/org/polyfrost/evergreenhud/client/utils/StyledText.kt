package org.polyfrost.evergreenhud.client.utils

import net.minecraft.network.chat.Component
//? if > 1.8.9 {
import net.minecraft.network.chat.Style
//?} else
//import org.polyfrost.oneconfig.internal.legacy.chat.Style
import org.polyfrost.compose.render.PolyColor
//? if > 1.8.9
import java.util.Optional

data class StyledRun(
    val text: String,
    val color: PolyColor?,
    val bold: Boolean,
    val italic: Boolean,
)

@JvmInline
value class StyledCell(val runs: List<StyledRun>)

fun StyledRun.asCell(): StyledCell = StyledCell(listOf(this))

fun Component.toStyledRuns(): List<StyledRun> {
    val runs = ArrayList<StyledRun>()
    //? if > 1.8.9 {
    visit({ style: Style, text: String ->
        if (text.isNotEmpty()) {
            runs.add(
                StyledRun(
                    text = text,
                    color = style.color?.let { PolyColor(0xFF000000.toInt() or it.value) },
                    bold = style.isBold,
                    italic = style.isItalic,
                )
            )
        }
        Optional.empty<Unit>()
    }, Style.EMPTY)
    //?} else {
    /*for (part in this) {
        val text = part.content
        val style = part.style
        if (text.isNotEmpty()) {
            runs.add(
                StyledRun(
                    text = text,
                    color = Style.fromLegacy(style).rgbColor?.let { PolyColor(0xFF000000.toInt() or it) },
                    bold = style.isBold,
                    italic = style.isItalic,
                )
            )
        }
    }
    *///?}
    return runs
}

fun List<StyledRun>.plainText(): String = joinToString("") { it.text }
