package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.media.VisualWord
import org.polyfrost.evergreenhud.client.hud.media.mixedDirection
import org.polyfrost.evergreenhud.client.hud.media.visualOrder
import org.polyfrost.evergreenhud.client.hud.media.visualWords
import org.polyfrost.evergreenhud.client.hud.media.wordLevels

class BidiTest {

    @Test
    fun latinUntouched() {
        val text = "Midnight City"
        assertSame(text, visualOrder(text))
    }

    @Test
    fun hebrewReorderedWithLatinAndDigitsKept() {
        assertEquals("םולש", visualOrder("שלום"))
        assertEquals("Astronengg - ריש", visualOrder("שיר - Astronengg"))
        assertEquals("Song (ריש)", visualOrder("Song (שיר)"))
        assertEquals("(סקימר) ריש", visualOrder("שיר (רמיקס)"))
        assertEquals("2024 ריש", visualOrder("שיר 2024"))
        assertEquals("Track 12 - ריש", visualOrder("Track 12 - שיר"))
        assertEquals("…רישה", visualOrder("השיר…"))
    }

    private fun layout(words: List<String>, ink: FloatArray, gap: FloatArray) =
        wordLevels(words)?.let { visualWords(it, ink, gap) }

    private fun order(vararg words: String) =
        layout(words.toList(), FloatArray(words.size) { 1f }, FloatArray(words.size) { 1f })?.map { it.index to it.rtl }

    @Test
    fun lyricWordsLaidOutRightToLeft() {
        assertNull(order("word ", "by ", "word"))
        assertEquals(listOf(2 to true, 1 to true, 0 to true), order("אני ", "אוהב ", "אותך"))
        assertEquals(listOf(2 to true, 1 to false, 0 to true), order("שיר ", "Song ", "שני"))
    }

    @Test
    fun bracketedLatinKeepsItsOrderInRtlLine() {
        assertEquals(listOf(3 to true, 1 to false, 2 to false, 0 to true), order("שיר ", "(Song ", "title) ", "חדש"))
    }

    @Test
    fun reversedWordsKeepSpacesBetweenThem() {
        val words = layout(listOf("אני ", "אוהב ", "אותך"), floatArrayOf(3f, 4f, 4f), floatArrayOf(1f, 1f, 0f))
        assertEquals(listOf(VisualWord(2, true, 0f), VisualWord(1, true, 5f), VisualWord(0, true, 10f)), words)
    }

    @Test
    fun gapsFollowRunBoundaries() {
        val words = layout(listOf("Go ", "שי", "ר ", "now"), floatArrayOf(2f, 2f, 1f, 3f), floatArrayOf(1f, 0f, 1f, 0f))
        assertEquals(listOf(VisualWord(0, false, 0f), VisualWord(2, true, 3f), VisualWord(1, true, 4f), VisualWord(3, false, 7f)), words)
    }

    @Test
    fun wrappedRowKeepsLineDirection() {
        val levels = wordLevels(listOf("שיר ", "חדש ", "one ", "two ", "שלום"))!!
        val row = visualWords(levels.copyOfRange(2, 5), floatArrayOf(3f, 3f, 4f), floatArrayOf(1f, 1f, 0f))
        assertEquals(listOf(VisualWord(2, true, 0f), VisualWord(0, false, 5f), VisualWord(1, false, 9f)), row)
    }

    @Test
    fun mixedDirectionWordsDetected() {
        assertFalse(mixedDirection("שלום", true))
        assertFalse(mixedDirection("Song", false))
        assertTrue(mixedDirection("שיר2024", true))
        assertTrue(mixedDirection("(רמיקס)", true))
    }
}
