package `in`.artistant.app.data.model

import `in`.artistant.app.data.model.dto.DBArtist
import `in`.artistant.app.data.model.dto.DBPackage
import `in`.artistant.app.data.model.dto.SearchArtistRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the DTO → domain mapping for the two hot Browse read shapes. */
class DtoMappingTest {

    @Test
    fun `DBArtist headline price prefers the popular package`() {
        val row = DBArtist(
            id = "a1", handle = "dj", stage_name = "DJ Neon", category = "DJ",
            genre = null, base_city = "Mumbai", cover_gradient_index = 2, score = 71,
        )
        val cheap = DBPackage("a1", 0, "Basic", "1 hr", 10000, emptyList(), popular = false)
        val popular = DBPackage("a1", 1, "Headline", "2 hr", 25000, emptyList(), popular = true)

        val artist = row.toArtist(
            packages = listOf(cheap.toPackage(), popular.toPackage()),
            tech = listOf("PA system"),
            samples = emptyList(),
            coverUrl = "https://cdn/x.jpg",
        )

        assertEquals("DJ Neon", artist.name)
        assertEquals(25000, artist.price)          // popular wins over first
        assertEquals("2 hr", artist.duration)
        assertEquals("", artist.genre)             // null genre → blank
        assertEquals(ArtistGradient.palette(2), artist.gradient)
        assertEquals("https://cdn/x.jpg", artist.coverUrl)
        assertTrue(artist.reviews.isEmpty())       // reviews load separately
    }

    @Test
    fun `DBArtist with no packages falls back to zero price and set duration`() {
        val row = DBArtist(
            id = "a2", handle = "band", stage_name = "The Band", category = "Band",
            base_city = "Delhi",
        )
        val artist = row.toArtist(emptyList(), emptyList(), emptyList(), coverUrl = null)
        assertEquals(0, artist.price)
        assertEquals("set", artist.duration)
        assertEquals(null, artist.coverUrl)
    }

    @Test
    fun `SearchArtistRow maps to a tile-level partial`() {
        val row = SearchArtistRow(
            id = "a3", stage_name = "Sitar Co", handle = "sitar", category = "Classical",
            genre = "Hindustani", base_city = "Jaipur", min_price = null, score = 64,
            total_gigs = 9, cover_gradient_index = 4,
        )
        val partial = row.toPartialArtist(coverUrl = null)

        assertEquals("Sitar Co", partial.name)
        assertEquals(0, partial.price)             // null min_price → 0
        assertEquals("", partial.duration)         // tile carries no duration
        assertEquals(64, partial.score)
        assertEquals(9, partial.gigs)
        assertEquals(ArtistGradient.palette(4), partial.gradient)
        assertTrue(partial.packages.isEmpty())     // placeholders until full fetch
    }
}
