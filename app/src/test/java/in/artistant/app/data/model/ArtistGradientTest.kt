package `in`.artistant.app.data.model

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Pins the gradient palette clamp + index→palette mapping (iOS `ArtistGradient`). */
class ArtistGradientTest {

    @Test
    fun `each palette is a 3-stop gradient landing on the warm-black ground`() {
        for (i in 0..5) {
            val p = ArtistGradient.palette(i)
            assertEquals("palette $i should have 3 stops", 3, p.size)
            assertEquals("palette $i should end on 0F1014", Color(0xFF0F1014), p.last())
        }
    }

    @Test
    fun `index 0 maps to the pink-violet palette`() {
        assertEquals(
            listOf(Color(0xFFFF6B9D), Color(0xFF7C5CFF), Color(0xFF0F1014)),
            ArtistGradient.palette(0),
        )
    }

    @Test
    fun `out-of-range indices clamp to the nearest valid palette`() {
        assertEquals(ArtistGradient.palette(0), ArtistGradient.palette(-1))
        assertEquals(ArtistGradient.palette(0), ArtistGradient.palette(-999))
        assertEquals(ArtistGradient.palette(5), ArtistGradient.palette(6))
        assertEquals(ArtistGradient.palette(5), ArtistGradient.palette(999))
    }

    @Test
    fun `distinct in-range indices are distinct palettes`() {
        assertNotEquals(ArtistGradient.palette(0), ArtistGradient.palette(1))
        assertNotEquals(ArtistGradient.palette(2), ArtistGradient.palette(3))
    }
}
