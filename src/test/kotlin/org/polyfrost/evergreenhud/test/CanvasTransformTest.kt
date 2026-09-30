package org.polyfrost.evergreenhud.test

import org.jetbrains.skia.Surface
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.polyfrost.evergreenhud.client.utils.transformValues

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
}
