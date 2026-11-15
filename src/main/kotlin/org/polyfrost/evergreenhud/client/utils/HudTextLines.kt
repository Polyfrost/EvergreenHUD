package org.polyfrost.evergreenhud.client.utils

import org.polyfrost.compose.render.FontManager
import org.polyfrost.compose.render.PolyFont
import org.polyfrost.oneconfig.api.hud.v1.Hud
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

fun Hud.applyCase(text: String, caseType: Int = this.caseType): String = when (caseType) {
    1 -> text.uppercase()
    2 -> text.lowercase()
    else -> text
}

private const val FALLBACK_CAP_HEIGHT = 0.7f

private val capHeightRatios = ConcurrentHashMap<String, Float>()

val PolyFont.capHeight: Float
    get() = size * capHeightRatios.getOrPut(name) { FontManager.fontData(name)?.let(::readCapHeightRatio) ?: FALLBACK_CAP_HEIGHT }

private fun readCapHeightRatio(data: ByteArray): Float? = runCatching {
    val buf = ByteBuffer.wrap(data)
    var head = -1
    var os2 = -1
    for (i in 0 until (buf.getShort(4).toInt() and 0xFFFF)) {
        val record = 12 + i * 16
        when (String(data, record, 4, Charsets.US_ASCII)) {
            "head" -> head = buf.getInt(record + 8)
            "OS/2" -> os2 = buf.getInt(record + 8)
        }
    }
    if (head < 0 || os2 < 0 || (buf.getShort(os2).toInt() and 0xFFFF) < 2) return null
    val unitsPerEm = buf.getShort(head + 18).toInt() and 0xFFFF
    val capHeight = buf.getShort(os2 + 88).toInt()
    if (unitsPerEm == 0 || capHeight <= 0) null else capHeight.toFloat() / unitsPerEm
}.getOrNull()
