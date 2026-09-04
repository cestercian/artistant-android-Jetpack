package `in`.artistant.app.feature.artist

import androidx.compose.ui.graphics.Color
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.ReportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The places screen 04 could quietly state something it does not know.
 *
 * Each of these is a claim the marketplace makes about an artist on the page a
 * client decides from, so the interesting cases are all the ones where the
 * honest answer is "we don't know" rather than a plausible-looking figure.
 */
class ArtistProfileFactsTest {

    private fun artist(
        category: String = "Band",
        genre: String = "Indie folk",
        city: String = "Bengaluru",
        score: Int = 0,
        gigs: Int = 0,
        response: String = "",
    ) = Artist(
        id = "a1",
        name = "The Tilt Collective",
        handle = "tilt",
        category = category,
        genre = genre,
        city = city,
        price = 0,
        duration = "set",
        score = score,
        gradient = listOf(Color.Black, Color.White),
        gigs = gigs,
        response = response,
    )

    private fun review(rating: Int, id: String = "r$rating") =
        Review(id = id, name = "Rhea", org = "", rating = rating, body = "Great set")

    // ── subtitle ────────────────────────────────────────────────────────────

    @Test
    fun `subtitle joins category genre and city`() {
        assertEquals(
            "Band · Indie folk · Bengaluru",
            ArtistProfileFacts.subtitle(artist()),
        )
    }

    @Test
    fun `a blank genre does not become an empty segment`() {
        // "Band ·  · Bengaluru" is what a naive join produces, and it reads as a
        // rendering fault rather than as a missing field.
        assertEquals(
            "Band · Bengaluru",
            ArtistProfileFacts.subtitle(artist(genre = "   ")),
        )
    }

    @Test
    fun `a genre that repeats the category is not printed twice`() {
        assertEquals(
            "Band · Bengaluru",
            ArtistProfileFacts.subtitle(artist(genre = "Band")),
        )
    }

    @Test
    fun `an artist with nothing but a city still gets a line`() {
        assertEquals(
            "Bengaluru",
            ArtistProfileFacts.subtitle(artist(category = "", genre = "")),
        )
    }

    // ── rating pill ─────────────────────────────────────────────────────────

    @Test
    fun `no reviews means no pill, not a zero`() {
        // A failed reviews read arrives as this same empty list, and a pill
        // reading "0.00 (0)" beside an artist's name is a claim we cannot make.
        assertNull(ArtistProfileFacts.ratingLabel(emptyList()))
    }

    @Test
    fun `the pill averages the very list the section renders`() {
        val reviews = listOf(review(5, "a"), review(5, "b"), review(4, "c"))
        assertEquals("4.67 (3)", ArtistProfileFacts.ratingLabel(reviews))
    }

    @Test
    fun `the average is formatted in a fixed locale`() {
        // Mono numerals sit beside "128" and "1h" in the strip; a French device
        // rendering "4,50" would put a comma into an otherwise ASCII column.
        assertEquals("4.50 (2)", ArtistProfileFacts.ratingLabel(listOf(review(5, "a"), review(4, "b"))))
    }

    // ── stat cells ──────────────────────────────────────────────────────────

    @Test
    fun `an unranked artist reads New, never zero`() {
        // Under 5 completed gigs is the New tier whatever the score column says.
        assertEquals("New", ArtistProfileFacts.scoreCell(artist(score = 0, gigs = 0)))
        assertEquals("New", ArtistProfileFacts.scoreCell(artist(score = 71, gigs = 4)))
    }

    @Test
    fun `a ranked artist reads the number`() {
        assertEquals("86", ArtistProfileFacts.scoreCell(artist(score = 86, gigs = 128)))
    }

    @Test
    fun `an unmeasured reply time is a dash, not an invented figure`() {
        assertEquals(ArtistProfileFacts.UNKNOWN, ArtistProfileFacts.replyCell(artist(response = "")))
        assertEquals(ArtistProfileFacts.UNKNOWN, ArtistProfileFacts.replyCell(artist(response = "  ")))
    }

    @Test
    fun `a measured reply time is the server's own words`() {
        assertEquals("1h", ArtistProfileFacts.replyCell(artist(response = "1h")))
    }

    @Test
    fun `zero shows is a fact and prints as zero`() {
        assertEquals("0", ArtistProfileFacts.showsCell(artist(gigs = 0)))
        assertEquals("128", ArtistProfileFacts.showsCell(artist(gigs = 128)))
    }

    // ── self view ───────────────────────────────────────────────────────────

    @Test
    fun `self is decided case-insensitively`() {
        // The route's id can arrive upper-cased from a deep link or a share URL,
        // and comparing raw would show an artist the bookable view of their own
        // profile — a request the server's self-booking guard then rejects.
        assertTrue(
            ArtistProfileFacts.isSelfProfile(
                viewerId = "3F2504E0-4F89-11D3-9A0C-0305E82C3301",
                artistId = "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
            ),
        )
    }

    @Test
    fun `a signed-out reader is never self`() {
        assertFalse(ArtistProfileFacts.isSelfProfile(viewerId = null, artistId = "a1"))
        assertFalse(ArtistProfileFacts.isSelfProfile(viewerId = "  ", artistId = "a1"))
    }

    @Test
    fun `a different account is not self`() {
        assertFalse(ArtistProfileFacts.isSelfProfile(viewerId = "a2", artistId = "a1"))
    }

    // ── report copy ─────────────────────────────────────────────────────────

    @Test
    fun `a queued report never claims to have been received`() {
        val queued = ArtistProfileFacts.reportToast(ReportOutcome.Queued)!!
        assertTrue("must say queued", queued.contains("queued", ignoreCase = true))
        assertFalse(queued.contains("received", ignoreCase = true))
        assertFalse(queued.contains("sent", ignoreCase = true))
    }

    @Test
    fun `a sent report says so`() {
        assertTrue(
            ArtistProfileFacts.reportToast(ReportOutcome.Sent)!!
                .contains("sent", ignoreCase = true),
        )
    }

    @Test
    fun `no report means no toast`() {
        assertNull(ArtistProfileFacts.reportToast(null))
    }
}
