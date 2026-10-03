package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.hud.potion.PotionEffectsHud
import org.polyfrost.oneconfig.api.hud.v1.HudAnchor
import org.polyfrost.oneconfig.api.hud.v1.HudManager

class PotionCenteringTest {
    @Test
    fun `vertical list keeps its left edge and vertical center as names change width`() {
        HudManager.guiScreenWidth = 1920f
        HudManager.guiScreenHeight = 1080f
        val hud = PotionEffectsHud()
        hud.listDirection = 1
        hud.textAlignment = 1
        hud.staticWidth = true
        hud.staticW = 65f
        hud.staticH = 38f
        hud.renderedW = 100f
        hud.renderedH = 40f
        hud.setAbsolutePosition(200f, 300f)
        val oldX = hud.x
        val oldY = hud.y
        hud.applyCenteredGrowth(true)
        assertFalse(hud.staticWidth)
        assertEquals(oldX, hud.x, 1f)
        assertEquals(oldY, hud.y, 1f)
        val left = hud.x
        val centerY = hud.anchorPointY(HudAnchor.Center)
        for ((width, height) in listOf(100f to 80f, 160f to 120f, 0f to 0f, 100f to 40f)) {
            hud.renderedW = width
            hud.renderedH = height
            assertEquals(left, hud.x, 0.01f)
            assertEquals(centerY, hud.anchorPointY(HudAnchor.Center), 0.01f)
            if (width > 0f && height > 0f) {
                // Check the visible list's midpoint, not just the frame anchor.
                assertEquals(centerY, hud.y + height / 2f, 0.01f)
            }
        }
    }

    @Test
    fun `disabling centering restores previous anchors without moving the hud`() {
        HudManager.guiScreenWidth = 1920f
        HudManager.guiScreenHeight = 1080f
        val hud = PotionEffectsHud()
        hud.staticWidth = true
        hud.growthAnchor = HudAnchor.BottomRight
        hud.selfAnchorPoint = HudAnchor.Right
        hud.renderedW = 100f
        hud.renderedH = 40f
        hud.setAbsolutePosition(200f, 300f)
        hud.applyCenteredGrowth(true)
        hud.renderedH = 100f
        val x = hud.x
        val y = hud.y
        hud.applyCenteredGrowth(false)
        assertEquals(HudAnchor.BottomRight, hud.growthAnchor)
        assertEquals(HudAnchor.Right, hud.selfAnchorPoint)
        assertTrue(hud.staticWidth)
        assertEquals(x, hud.x, 1f)
        assertEquals(y, hud.y, 1f)
    }
    @Test
    fun `disabling centering restores custom fixed dimensions after layout`() {
        HudManager.guiScreenWidth = 1920f
        HudManager.guiScreenHeight = 1080f
        val hud = PotionEffectsHud()
        hud.staticWidth = true
        hud.staticW = 200f
        hud.staticH = 120f
        hud.renderedW = 200f
        hud.renderedH = 120f
        hud.setAbsolutePosition(200f, 300f)
        hud.applyCenteredGrowth(true)
        hud.renderedW = 100f
        hud.renderedH = 40f
        val left = hud.x
        val top = hud.y
        hud.applyCenteredGrowth(false)
        assertTrue(hud.staticWidth)
        assertEquals(200f, hud.staticW)
        assertEquals(120f, hud.staticH)
        assertEquals(left, hud.x, 1f)
        assertEquals(top, hud.y, 1f)
        // A second toggle must preserve the same frame, too.
        hud.applyCenteredGrowth(true)
        hud.applyCenteredGrowth(false)
        assertEquals(200f, hud.staticW)
        assertEquals(120f, hud.staticH)
    }


}
