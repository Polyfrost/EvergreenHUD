package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.potion.formatPotionEffectMinecraftText

class PotionEffectsTextStyleTest {

    @Test
    fun `minecraft text stays unchanged without styles`() {
        assertEquals("Speed", formatPotionEffectMinecraftText("Speed", bold = false, italic = false))
    }

    @Test
    fun `minecraft text applies bold and italic styles`() {
        assertEquals("§lSpeed§r", formatPotionEffectMinecraftText("Speed", bold = true, italic = false))
        assertEquals("§oSpeed§r", formatPotionEffectMinecraftText("Speed", bold = false, italic = true))
        assertEquals("§l§oSpeed§r", formatPotionEffectMinecraftText("Speed", bold = true, italic = true))
    }
}
