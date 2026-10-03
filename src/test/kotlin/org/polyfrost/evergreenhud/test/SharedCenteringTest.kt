package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.CustomTextHud
import org.polyfrost.evergreenhud.client.utils.EvergreenTextHud
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor
import org.polyfrost.oneconfig.api.hud.v1.HudManager

class SharedCenteringTest {
    private class OptInTextHud : EvergreenTextHud("test.json", "Test", Category.INFO) {
        override val supportsCenteredGrowth: Boolean get() = true
        override fun getText(): String = "Test"
        override fun defaultPosition(): Pair<Float, Float> = 0f to 0f
    }

    @Test
    fun `text HUD preserves midpoint as both content dimensions change`() {
        HudManager.guiScreenWidth = 1920f
        HudManager.guiScreenHeight = 1080f
        val hud = OptInTextHud()
        hud.renderedW = 100f
        hud.renderedH = 40f
        hud.setAbsolutePosition(200f, 300f)
        hud.applyCenteredGrowth(true)
        hud.centeredGrowth = true
        val centerX = hud.anchorPointX(HudAnchor.Center)
        val centerY = hud.anchorPointY(HudAnchor.Center)
        hud.renderedW = 200f
        hud.renderedH = 80f
        assertEquals(centerX, hud.x + hud.scaledWidth / 2f, 0.01f)
        assertEquals(centerY, hud.y + hud.scaledHeight / 2f, 0.01f)
        // A separate instance keeps its own setting and saved layout.
        assertFalse(CustomTextHud().centeredGrowth)
    }

    @Test
    fun `shared text HUD restores custom fixed frame`() {
        val hud = OptInTextHud()
        hud.staticWidth = true
        hud.staticW = 200f
        hud.staticH = 120f
        hud.renderedW = 200f
        hud.renderedH = 120f
        hud.applyCenteredGrowth(true)
        hud.centeredGrowth = true
        hud.renderedW = 100f
        hud.renderedH = 40f
        hud.centeredGrowth = false
        hud.applyCenteredGrowth(false)
        assertTrue(hud.staticWidth)
        assertEquals(200f, hud.staticW)
        assertEquals(120f, hud.staticH)
    }
}
