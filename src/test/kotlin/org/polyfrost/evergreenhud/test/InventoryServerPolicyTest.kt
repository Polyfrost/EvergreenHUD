package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.inventoryHudAllowedOnServer

class InventoryServerPolicyTest {
    @Test
    fun `blocks every address containing hoplite regardless of case or port`() {
        for (address in listOf("hoplite.gg", "play.hoplite.gg", "HOPLITE.GG:25565", "proxy-hoplite.example.net", "example.net/hoplite")) {
            assertFalse(inventoryHudAllowedOnServer(address), address)
        }
    }

    @Test
    fun `allows other servers singleplayer and disconnected state`() {
        for (address in listOf(null, "", "localhost", "127.0.0.1:25565", "mc.hypixel.net", "example.net")) {
            assertTrue(inventoryHudAllowedOnServer(address))
        }
        // Switching servers must not permanently change the user's HUD preference.
        assertFalse(inventoryHudAllowedOnServer("hoplite.gg"))
        assertTrue(inventoryHudAllowedOnServer("example.net"))
    }
}
