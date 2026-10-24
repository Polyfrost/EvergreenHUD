package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.utils.media.DBusConnection
import org.polyfrost.evergreenhud.client.utils.media.LinuxMedia

class LinuxMediaTest {

    private val firefoxReply = (
        "6c02010158020000b70900004200000006017300090000003a312e383234383534000000000000000801670005617b73" +
        "767d000000000000050175000200000007017300090000003a312e383137323636000000000000005002000000000000" +
        "0e000000506c61796261636b53746174757300017300000007000000506c6179696e6700000000000400000052617465" +
        "0001640000000000000000000000f03f080000004d657461646174610005617b73767d00300100000d0000006d707269" +
        "733a747261636b696400016f000000001f0000002f6f72672f6d707269732f4d65646961506c61796572322f66697265" +
        "666f7800000000000b000000786573616d3a7469746c6500017300000b0000005465737420547261636b210000000000" +
        "0b000000786573616d3a616c62756d000173000011000000546865205465737420416c62756d21212100000000000000" +
        "0c000000786573616d3a61727469737400026173000000000f0000000a000000546573742042616e6421000000000000" +
        "09000000786573616d3a75726c000173000000002a00000068747470733a2f2f6f70656e2e73706f746966792e636f6d" +
        "2f636f6c6c656374696f6e2f747261636b730000000000000c0000006d707269733a6c656e6774680001780000000000" +
        "40fbbd0b0000000006000000566f6c756d65000164000000000000000000f03f08000000506f736974696f6e00017800" +
        "80d54302000000000900000043616e476f4e65787400016200000000010000000d00000043616e476f50726576696f75" +
        "730001620000000001000000000000000700000043616e506c6179000162000001000000000000000800000043616e50" +
        "617573650001620001000000000000000700000043616e5365656b000162000001000000000000000a00000043616e43" +
        "6f6e74726f6c00016200000001000000"
        ).chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun firefox() = DBusConnection.decode(firefoxReply).body[0] as Map<*, *>

    @Test
    fun decodesGetAllReply() {
        val message = DBusConnection.decode(firefoxReply)
        assertEquals("a{sv}", message.signature)
        assertEquals(DBusConnection.METHOD_RETURN, message.type)
        val props = firefox()
        assertEquals("Playing", props["PlaybackStatus"])
        assertEquals(true, props["CanGoNext"])
    }

    @Test
    fun parsesFullPlayer() {
        val info = LinuxMedia.parse("org.mpris.MediaPlayer2.firefox", firefox())!!
        assertEquals("Test Track!", info.title)
        assertEquals("Test Band!", info.artist)
        assertTrue(info.playing)
        assertTrue(info.lengthMs > 0)
        assertTrue(info.canNext)
        assertTrue(info.canToggle)
    }

    @Test
    fun pausedPlayerDoesNotAdvance() {
        val props = firefox().toMutableMap().apply { put("PlaybackStatus", "Paused") }
        val info = LinuxMedia.parse("p", props)!!
        assertFalse(info.playing)
        assertEquals(info.positionMs, info.positionAt(info.sampledAtMs + 5_000L))
    }

    @Test
    fun joinsArtistsAndReadsStringLengths() {
        val props = mapOf(
            "PlaybackStatus" to "Playing",
            "Position" to 22_000_000L,
            "Metadata" to mapOf(
                "xesam:title" to "Song",
                "xesam:artist" to listOf("A", "B"),
                "mpris:length" to "164000000",
                "mpris:artUrl" to "file:///tmp/art.png",
            ),
        )
        val info = LinuxMedia.parse("p", props)!!
        assertEquals("A, B", info.artist)
        assertEquals(22_000L, info.positionMs)
        assertEquals(164_000L, info.lengthMs)
        assertEquals("file:///tmp/art.png", info.artUrl)
        assertEquals(27_000L, info.positionAt(info.sampledAtMs + 5_000L))
        assertEquals(164_000L, info.positionAt(info.sampledAtMs + 999_000L))
    }

    @Test
    fun emptyMetadataIsIgnored() {
        assertNull(LinuxMedia.parse("p", mapOf("PlaybackStatus" to "Stopped", "Metadata" to emptyMap<String, Any>())))
    }

    @Test
    fun talksToTheSessionBus() {
        val bus = runCatching { DBusConnection.session() }.getOrNull()
        assumeTrue(bus != null, "no session bus")
        bus!!.use {
            val names = it.call("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus", "ListNames")
            assertNotNull(names.firstOrNull() as? List<*>)
        }
    }
}
