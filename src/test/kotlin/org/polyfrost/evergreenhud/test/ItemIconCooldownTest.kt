package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.item.cooldownOverlayHeight

class ItemIconCooldownTest {

    @Test
    fun `cooldown overlay uses vanilla pixel rounding`() {
        assertEquals(0, cooldownOverlayHeight(0f))
        assertEquals(1, cooldownOverlayHeight(0.01f))
        assertEquals(1, cooldownOverlayHeight(1f / 16f))
        assertEquals(2, cooldownOverlayHeight(Math.nextUp(1f / 16f)))
        assertEquals(8, cooldownOverlayHeight(0.5f))
        assertEquals(16, cooldownOverlayHeight(1f))
    }

    @Test
    fun `cooldown overlay clamps progress to the item bounds`() {
        assertEquals(0, cooldownOverlayHeight(-1f))
        assertEquals(16, cooldownOverlayHeight(2f))
    }
}
