package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide whether a thread draws a quote card, and whose buttons
 * are on it.
 *
 * The failure this guards is not cosmetic. Drawing Accept on the wrong seat
 * offers someone a decision RLS will refuse — they tap, the write is rejected,
 * and they have been told the deal was theirs to close when it never was.
 */
class ThreadQuoteTest {

    private val artist = "0f7a2b1c-0000-4000-8000-000000000001"
    private val other = "0f7a2b1c-0000-4000-8000-0000000000ff"
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun request(
        id: String = "q1",
        artistId: String = artist,
        amount: Int = 48_000,
        counter: Int? = null,
        status: GigRequestStatus = GigRequestStatus.Open,
        expiresAt: Long? = now + hour,
        date: String = "Sat 12 Oct",
        pkg: String = "Full band",
    ) = StoredRequest(
        raw = GigRequest(
            id = id,
            client = "Rhea",
            message = "",
            date = date,
            amount = amount,
            packageLabel = pkg,
            artistId = artistId,
            expiresAtEpochMs = expiresAt,
        ),
        status = status,
        counterAmount = counter,
    )

    // --- which row a thread picks -------------------------------------------

    @Test
    fun `a thread with no artist has no quote`() {
        assertNull(ThreadQuote.pick(listOf(request()), null, viewerIsArtist = false, nowMs = now))
        assertNull(ThreadQuote.pick(listOf(request()), "  ", viewerIsArtist = false, nowMs = now))
    }

    @Test
    fun `only the quote with THIS thread's artist is picked`() {
        val quote = ThreadQuote.pick(
            requests = listOf(request(id = "other", artistId = other), request(id = "mine")),
            artistId = artist,
            viewerIsArtist = false,
            nowMs = now,
        )
        assertEquals("mine", quote?.requestId)
    }

    @Test
    fun `artist id matching is case insensitive, because uuids arrive both ways`() {
        val quote = ThreadQuote.pick(
            requests = listOf(request(artistId = artist.uppercase())),
            artistId = artist,
            viewerIsArtist = false,
            nowMs = now,
        )
        assertEquals("q1", quote?.requestId)
    }

    @Test
    fun `the newest match wins, and the repositories order newest first`() {
        val quote = ThreadQuote.pick(
            requests = listOf(request(id = "new"), request(id = "old")),
            artistId = artist,
            viewerIsArtist = false,
            nowMs = now,
        )
        assertEquals("new", quote?.requestId)
    }

    @Test
    fun `declined, expired and unknown rows draw no card at all`() {
        listOf(
            GigRequestStatus.Declined,
            GigRequestStatus.Expired,
            GigRequestStatus.Unknown,
        ).forEach { status ->
            assertNull(
                "status $status must not render a card",
                ThreadQuote.pick(
                    requests = listOf(request(status = status)),
                    artistId = artist,
                    viewerIsArtist = false,
                    nowMs = now,
                ),
            )
        }
    }

    @Test
    fun `an accepted row still draws a card, because it is the record of the deal`() {
        val quote = ThreadQuote.pick(
            requests = listOf(request(status = GigRequestStatus.Accepted)),
            artistId = artist,
            viewerIsArtist = false,
            nowMs = now,
        )
        assertTrue(quote!!.frozen)
        assertFalse(quote.actionable)
    }

    // --- whose move it is ---------------------------------------------------

    @Test
    fun `an open request is the artist's to answer`() {
        assertTrue(ThreadQuote.decidesNext(GigRequestStatus.Open, viewerIsArtist = true))
        assertFalse(ThreadQuote.decidesNext(GigRequestStatus.Open, viewerIsArtist = false))
    }

    @Test
    fun `a countered request is the client's to answer`() {
        assertTrue(ThreadQuote.decidesNext(GigRequestStatus.Countered, viewerIsArtist = false))
        assertFalse(ThreadQuote.decidesNext(GigRequestStatus.Countered, viewerIsArtist = true))
    }

    @Test
    fun `nobody answers a terminal or unreadable row`() {
        listOf(
            GigRequestStatus.Accepted,
            GigRequestStatus.Declined,
            GigRequestStatus.Expired,
            GigRequestStatus.Unknown,
        ).forEach { status ->
            assertFalse(ThreadQuote.decidesNext(status, viewerIsArtist = true))
            assertFalse(ThreadQuote.decidesNext(status, viewerIsArtist = false))
        }
    }

    // --- the amount on the table --------------------------------------------

    @Test
    fun `a counter replaces the proposal as the standing number`() {
        val quote = ThreadQuote.from(
            stored = request(amount = 48_000, counter = 40_000, status = GigRequestStatus.Countered),
            viewerIsArtist = false,
            nowMs = now,
        )
        assertEquals(40_000, quote.amountInr)
        assertTrue(quote.countered)
    }

    @Test
    fun `with no counter the proposal stands`() {
        val quote = ThreadQuote.from(request(), viewerIsArtist = true, nowMs = now)
        assertEquals(48_000, quote.amountInr)
        assertFalse(quote.countered)
    }

    // --- expiry -------------------------------------------------------------

    @Test
    fun `a lapsed offer is rendered but never actionable`() {
        val quote = ThreadQuote.from(request(expiresAt = now - 1), viewerIsArtist = true, nowMs = now)
        assertTrue(quote.expired)
        assertTrue(quote.viewerDecides)
        assertFalse("the buttons must be gone once the deadline passed", quote.actionable)
    }

    @Test
    fun `the deadline itself counts as lapsed`() {
        val quote = ThreadQuote.from(request(expiresAt = now), viewerIsArtist = true, nowMs = now)
        assertTrue(quote.expired)
    }

    @Test
    fun `a row with no parseable deadline never reads as expired`() {
        val quote = ThreadQuote.from(request(expiresAt = null), viewerIsArtist = true, nowMs = now)
        assertFalse(quote.expired)
        assertTrue(quote.actionable)
        assertNull(quote.expiresAtEpochMs)
    }

    // --- the terms line -----------------------------------------------------

    @Test
    fun `terms join the package and the date`() {
        assertEquals("Full band · Sat 12 Oct", ThreadQuote.from(request(), false, now).terms)
    }

    @Test
    fun `terms drop a missing half rather than printing a dangling separator`() {
        assertEquals("Full band", ThreadQuote.from(request(date = ""), false, now).terms)
        assertEquals("Sat 12 Oct", ThreadQuote.from(request(pkg = ""), false, now).terms)
    }
}
