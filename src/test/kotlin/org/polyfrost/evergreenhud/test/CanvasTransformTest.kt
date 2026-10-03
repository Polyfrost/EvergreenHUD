package org.polyfrost.evergreenhud.test

import org.jetbrains.skia.Surface
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.utils.transformValues
import org.polyfrost.evergreenhud.client.utils.snapToPixels

class CanvasTransformTest {
    @Test
    fun `reads canvas transform for pixel snapping`() {
        Surface.makeRasterN32Premul(16, 16).use { surface ->
            val canvas = surface.canvas
            assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f), canvas.transformValues())
            canvas.translate(3f, 4f)
            canvas.scale(2f, 3f)
            assertArrayEquals(floatArrayOf(2f, 0f, 3f, 0f, 3f, 4f, 0f, 0f, 1f), canvas.transformValues())
        }
    }
    @Test
    fun `shared pixel snapping keeps subpixel keys visible`() {
        Surface.makeRasterN32Premul(16, 16).use { surface ->
            val canvas = surface.canvas
            canvas.scale(0.25f, 0.25f)
            val rect = snapToPixels(canvas, 0f, 0f, 1f, 1f)
            assertEquals(4f, rect.width)
            assertEquals(4f, rect.height)
        }
    }
}
