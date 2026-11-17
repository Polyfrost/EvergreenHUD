package org.polyfrost.evergreenhud.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.compose.render.PolyBitmap
import org.polyfrost.evergreenhud.client.utils.media.blurSource

class BlurSourceTest {

    @Test
    fun thinArtStaysInBounds() {
        for ((w, h) in listOf(1 to 256, 256 to 1, 3 to 1000, 129 to 129, 1 to 1_000_000)) {
            val source = blurSource(PolyBitmap(w, h, ByteArray(w * h * 4) { -1 }))
            assertEquals(source.width * source.height * 4, source.pixels.size)
            for (v in source.pixels) assertEquals(255f, v, 0.01f)
        }
    }
}
