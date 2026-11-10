package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.utils.media.cleanTitle
import org.polyfrost.evergreenhud.client.utils.media.parseLrc
import org.polyfrost.evergreenhud.client.utils.media.decryptKrc
import org.polyfrost.evergreenhud.client.utils.media.parseKrc
import org.polyfrost.evergreenhud.client.utils.media.parseLyricsfile
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterOutputStream
import org.polyfrost.evergreenhud.client.utils.media.syllables
import org.polyfrost.evergreenhud.client.utils.media.pick
import org.polyfrost.evergreenhud.client.utils.media.wantsClean

class LyricsParseTest {

    @Test
    fun lyricsfileWords() {
        val yaml = """
            version: '1.0'
            metadata:
              title: Test
              duration_ms: 203000
            lines:
            - text: Make 'em dance
              start_ms: 140
              end_ms: 3050
              words:
              - text: 'Make '
                start_ms: 140
              - text: '''em '
                start_ms: 680
              - text: dance
                start_ms: 1070
            - text: Second line
              start_ms: 9060
              end_ms: 10156
            plain: |-
              Make 'em dance
              - text: not a line
        """.trimIndent()
        val lyrics = parseLyricsfile(yaml)!!
        assertTrue(lyrics.wordSynced)
        assertEquals(listOf("Make 'em dance", "Second line"), lyrics.lines.map { it.text })
        assertEquals(listOf(140L, 680L, 1070L), lyrics.lines[0].words.map { it.startMs })
        assertEquals(listOf(680L, 1070L, 3050L), lyrics.lines[0].words.map { it.endMs })
        assertEquals(3050L, lyrics.lines[0].endMs)
        assertEquals(1, lyrics.lineAt(9060L))
        assertEquals(-1, lyrics.lineAt(100L))
    }

    @Test
    fun lrcLinesGetEstimatedWords() {
        val lyrics = parseLrc("[ar:Someone]\n[00:37.68] Waiting in a car\n[00:56.78] \n[01:14.23][00:10.00] Again")!!
        assertFalse(lyrics.wordSynced)
        assertEquals(listOf(10_000L, 37_680L, 74_230L), lyrics.lines.map { it.startMs })
        assertEquals(56_780L, lyrics.lines[1].endMs)
        val words = lyrics.lines[1].words
        assertEquals(listOf("Waiting ", "in ", "a ", "car"), words.map { it.text })
        assertEquals(37_680L, words.first().startMs)
        for (i in 1 until words.size) assertEquals(words[i - 1].endMs, words[i].startMs)
        assertTrue(words.last().endMs <= 56_780L)
    }

    @Test
    fun enhancedLrcWords() {
        val lyrics = parseLrc("[00:01.00]<00:01.00>One <00:01.50>two<00:02.00>")!!
        assertTrue(lyrics.wordSynced)
        assertEquals(listOf(1000L, 1500L), lyrics.lines[0].words.map { it.startMs })
        assertEquals(2000L, lyrics.lines[0].endMs)
    }

    @Test
    fun cleansTitles() {
        assertEquals("Notorious Thugs", cleanTitle("Notorious Thugs - 2014 Remaster"))
        assertEquals("Midnight City", cleanTitle("Midnight City (Official Video)"))
        assertEquals("Song", cleanTitle("Song [Remastered 2011] (feat. Someone)"))
        assertEquals("M83 - Midnight City", cleanTitle("M83 - Midnight City"))
        assertEquals("1999", cleanTitle("1999"))
        assertEquals("Oasis - Live Forever", cleanTitle("Oasis - Live Forever"))
        assertEquals("Lana Del Rey - Video Games", cleanTitle("Lana Del Rey - Video Games"))
        assertEquals("Gym Class Heroes - Stereo Hearts", cleanTitle("Gym Class Heroes - Stereo Hearts"))
        assertEquals("Song (Love Me Clean)", cleanTitle("Song (Love Me Clean)"))
        assertEquals("Song", cleanTitle("Song (Official Music Video) - Radio Edit"))
        assertEquals("Song", cleanTitle("Song - feat. Someone"))
    }

    @Test
    fun countsSyllables() {
        assertEquals(listOf(1, 1, 3, 3, 3, 1), listOf("the", "time", "oxygen", "Benjamins", "steadily,", "smoke").map(::syllables))
    }

    @Test
    fun estimatePausesAtPunctuationAndHoldsLastWord() {
        val lrc = (0 until 8).joinToString("\n") { "[00:${"%02d".format(it * 3)}.00]So much smoke, need oxygen" } + "\n[00:30.00]end"
        val words = parseLrc(lrc)!!.lines[0].words
        assertEquals(listOf("So ", "much ", "smoke, ", "need ", "oxygen"), words.map { it.text })
        assertTrue(words[3].startMs > words[2].endMs, "pause after the comma")
        assertEquals(words[0].endMs, words[1].startMs)
        val perSyllable = { i: Int, n: Int -> (words[i].endMs - words[i].startMs) / n }
        assertTrue(perSyllable(4, 3) > perSyllable(1, 1), "last word is held longer")
        assertTrue(words.last().endMs < 3000L)
    }

    @Test
    fun indentedLyricsfileWithWordEnds() {
        val yaml = """
            version: '1.0'

            lines:
              - text: "I been tryna call"
                start_ms: 27395
                end_ms: 28960
                words:
                  - text: "I "
                    start_ms: 27395
                    end_ms: 27549
                  - text: "call"
                    start_ms: 28077
                    end_ms: 28500
        """.trimIndent()
        val lyrics = parseLyricsfile(yaml)!!
        assertTrue(lyrics.wordSynced)
        assertEquals(listOf(27549L, 28500L), lyrics.lines[0].words.map { it.endMs })
        assertEquals("I call", lyrics.lines[0].text)
    }

    @Test
    fun krcDecryptsAndDropsHeaderAndCredits() {
        val krc = listOf(
            "[ti:Love Is Only a Feeling]",
            "[0,23970]<0,1141,0>Love Is Only a Feeling<1141,1141,0> - <2282,1141,0>Joey Bada$$",
            "[23971,1049]<0,398,0>作词：Someone",
            "[25156,1394]<0,175,0>But <175,293,0>what's <468,271,0>more <739,655,0>preferred",
        ).joinToString("\n")
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val zipped = ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(krc.toByteArray()) } }.toByteArray()
        val encrypted = "krc1".toByteArray() + ByteArray(zipped.size) { (zipped[it].toInt() xor key[it % 16]).toByte() }
        val lyrics = parseKrc(decryptKrc(encrypted), "Love Is Only a Feeling", "Joey Bada$$")!!
        assertTrue(lyrics.wordSynced)
        assertEquals(listOf("But what's more preferred"), lyrics.lines.map { it.text })
        assertEquals(listOf(25156L, 25331L, 25624L, 25895L), lyrics.lines[0].words.map { it.startMs })
        assertEquals(25331L, lyrics.lines[0].words[0].endMs)
    }

    @Test
    fun prefersMatchingVersion() {
        val clean = parseLrc("[00:01.00] Sit down, be humble")!!
        val explicit = parseLrc("[00:01.00] Bitch, sit down, be humble")!!
        assertTrue(explicit.explicit)
        assertFalse(clean.explicit)
        assertTrue(wantsClean("HUMBLE. (Clean)"))
        assertFalse(wantsClean("HUMBLE."))
        assertEquals(explicit, sequenceOf(null, clean, explicit).pick({ it.explicit }))
        assertEquals(clean, sequenceOf(clean, explicit).pick({ !it.explicit }))
        assertEquals(clean, sequenceOf(clean, explicit).pick({ it.explicit }, limit = 1))
    }
}
