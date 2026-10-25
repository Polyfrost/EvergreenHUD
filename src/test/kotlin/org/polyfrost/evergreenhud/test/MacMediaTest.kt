package org.polyfrost.evergreenhud.test

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.utils.media.MacMedia
import java.util.Base64

class MacMediaTest {

    private val art = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))

    private fun line(diff: Boolean, payload: String) = """{"type":"data","diff":$diff,"payload":$payload}"""

    @Test
    fun fullPayloadReplacesState() {
        val first = MacMedia.merge(null, line(false, """{"title":"Song","artist":"Band","playing":true}"""))!!
        val second = MacMedia.merge(first, line(false, """{"title":"Other"}"""))!!
        assertEquals("Other", second["title"].asString)
        assertFalse(second.has("artist"))
    }

    @Test
    fun diffPayloadUpdatesAndRemovesKeys() {
        val first = MacMedia.merge(null, line(false, """{"title":"Song","artist":"Band","playing":true}"""))!!
        val second = MacMedia.merge(first, line(true, """{"playing":false,"artist":null}"""))!!
        assertEquals("Song", second["title"].asString)
        assertFalse(second["playing"].asBoolean)
        assertFalse(second.has("artist"))
    }

    @Test
    fun garbageLeavesStateAlone() {
        val state = MacMedia.merge(null, line(false, """{"title":"Song"}"""))
        assertEquals(state, MacMedia.merge(state, "nope"))
    }

    @Test
    fun parsesNowPlaying() {
        val json = JsonParser.parseString(
            """{"bundleIdentifier":"com.spotify.client","title":"Song","artist":"Band","playing":true,"playbackRate":1.0,
            "durationMicros":200000000,"elapsedTimeMicros":10000000,"timestampEpochMicros":1000000000,"artworkData":"$art"}""",
        ).asJsonObject
        val info = MacMedia.parse(json, nowMs = 1_005_000L)!!
        assertEquals("com.spotify.client", info.player)
        assertEquals(15_000L, info.positionMs)
        assertEquals(200_000L, info.lengthMs)
        assertEquals(listOf<Byte>(1, 2, 3), info.artBytes!!.toList())
        assertTrue(info.playing)
    }

    @Test
    fun pausedPositionIsFrozen() {
        val json = JsonParser.parseString(
            """{"title":"Song","playing":false,"elapsedTimeMicros":10000000,"timestampEpochMicros":1000000000}""",
        ).asJsonObject
        assertEquals(10_000L, MacMedia.parse(json, nowMs = 1_500_000L)!!.positionMs)
    }

    @Test
    fun emptyStateHasNoMedia() {
        assertNull(MacMedia.parse(JsonParser.parseString("{}").asJsonObject, nowMs = 0L))
    }
}
