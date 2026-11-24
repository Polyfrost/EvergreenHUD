package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.media.visualOrder
import org.polyfrost.evergreenhud.client.hud.media.visualWords

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

    @Test
    fun lyricWordsLaidOutRightToLeft() {
        assertNull(visualWords(listOf("word ", "by ", "word")))
        assertEquals(listOf(2 to true, 1 to true, 0 to true), visualWords(listOf("אני ", "אוהב ", "אותך")))
        assertEquals(listOf(2 to true, 1 to false, 0 to true), visualWords(listOf("שיר ", "Song ", "שני")))
    }
}
