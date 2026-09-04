package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.model.Thread
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
    private val client = "0f7a2b1c-0000-4000-8000-0000000000c1"
    private val otherClient = "0f7a2b1c-0000-4000-8000-0000000000c2"
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    /** The bookingless inquiry thread — the one shape that can carry a quote. */
    private fun thread(
        artistId: String = artist,
        clientId: String = client,
        bookingId: String? = null,
    ) = Thread(id = "t1", artistId = artistId, clientId = clientId, bookingId = bookingId)

    private fun request(
        id: String = "q1",
        artistId: String = artist,
        clientId: String = client,
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
            clientId = clientId,
            expiresAtEpochMs = expiresAt,
        ),
        status = status,
        counterAmount = counter,
    )

    // --- which row a thread picks -------------------------------------------

    @Test
    fun `a thread that names nobody has no quote`() {
        assertNull(ThreadQuote.pick(listOf(request()), null, viewerIsArtist = false, nowMs = now))
        assertNull(
            "a blank artist half is not half a key",
            ThreadQuote.pick(listOf(request()), thread(artistId = "  "), false, now),
        )
        assertNull(
            "a blank client half is not half a key either",
            ThreadQuote.pick(listOf(request()), thread(clientId = ""), false, now),
        )
    }

    /**
     * The bug this whole key exists for.
     *
     * `threads` has no `request_id`, so a booking thread and an open gig request
     * between the same two people used to collide: the quote card, with a live
     * Accept button, drew itself on the conversation about the CONFIRMED
     * booking. A thread with a booking behind it is about that booking; the
     * negotiation lives in the bookingless thread migration 0047/0076 opens.
     */
    @Test
    fun `a thread with a booking behind it never draws a quote card`() {
        assertNull(
            ThreadQuote.pick(
                requests = listOf(request()),
                thread = thread(bookingId = "b-1"),
                viewerIsArtist = false,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `only the quote with THIS thread's artist is picked`() {
        val quote = ThreadQuote.pick(
            requests = listOf(request(id = "other", artistId = other), request(id = "mine")),
            thread = thread(),
            viewerIsArtist = false,
            nowMs = now,
        )
        assertEquals("mine", quote?.requestId)
    }

    /**
     * The artist half alone is not a filter on the artist's OWN seat.
     *
     * `listForArtist()` returns every client's requests and `artist_id` is the
     * viewer's own id on all of them, so matching on it alone put one client's
     * number on another client's thread — and, through the same call in the
     * inbox, the same number on every row at once.
     */
    @Test
    fun `only the quote with THIS thread's client is picked`() {
        val quote = ThreadQuote.pick(
            requests = listOf(
                request(id = "someone else's", clientId = otherClient),
                request(id = "mine"),
            ),
            thread = thread(),
            viewerIsArtist = true,
            nowMs = now,
        )
        assertEquals("mine", quote?.requestId)
    }

    @Test
    fun `id matching is case insensitive on both halves, because uuids arrive both ways`() {
        val quote = ThreadQuote.pick(
            requests = listOf(request(artistId = artist.uppercase(), clientId = client.uppercase())),
            thread = thread(),
            viewerIsArtist = false,
            nowMs = now,
        )
        assertEquals("q1", quote?.requestId)
    }

    /**
     * Ambiguity is answered with silence, not with the newest row.
     *
     * Two live requests between the same pair are possible, and nothing on
     * either row says which of them this conversation is. Picking the newer one
     * put Accept and Counter on a card whose buttons would have changed the
     * OTHER request — the user agrees to one number and a different one moves.
     */
    @Test
    fun `two candidates between the same pair draw no card at all`() {
        assertNull(
            ThreadQuote.pick(
                requests = listOf(request(id = "new"), request(id = "old")),
                thread = thread(),
                viewerIsArtist = false,
                nowMs = now,
            ),
        )
    }

    /**
     * A past deal must not bury the offer on the table.
     *
     * `accepted` is a record, not an offer — no buttons, no decision — so it
     * does not compete for the card. Counting it as a candidate meant one old
     * accepted request permanently suppressed every later quote between the same
     * two people: two matches, no card, and unlike a live-vs-live tie it could
     * never resolve, because an accepted row stays accepted forever. The new
     * offer, which is the thing somebody has to answer, was invisible.
     */
    @Test
    fun `an accepted record does not hide a later live offer`() {
        val quote = ThreadQuote.pick(
            requests = listOf(
                request(id = "new ask"),
                request(id = "last year's deal", status = GigRequestStatus.Accepted),
            ),
            thread = thread(),
            viewerIsArtist = true,
            nowMs = now,
        )
        assertEquals("new ask", quote?.requestId)
        assertTrue("and it is answerable, which is the whole point", quote!!.actionable)
    }

    /** Same rule from the other live status: a counter outranks an old record too. */
    @Test
    fun `an accepted record does not hide a live counter offer`() {
        val quote = ThreadQuote.pick(
            requests = listOf(
                request(id = "old", status = GigRequestStatus.Accepted),
                request(id = "countered", counter = 40_000, status = GigRequestStatus.Countered),
            ),
            thread = thread(),
            viewerIsArtist = false,
            nowMs = now,
        )
        assertEquals("countered", quote?.requestId)
        assertEquals(40_000, quote?.amountInr)
    }

    /**
     * Records do not compete with offers, but they still compete with each
     * other: two past deals and nothing says which one this conversation's card
     * describes, and a record stating the wrong number is still a wrong number.
     */
    @Test
    fun `two accepted records with no live offer draw no card`() {
        assertNull(
            ThreadQuote.pick(
                requests = listOf(
                    request(id = "deal 1", status = GigRequestStatus.Accepted),
                    request(id = "deal 2", status = GigRequestStatus.Accepted),
                ),
                thread = thread(),
                viewerIsArtist = false,
                nowMs = now,
            ),
        )
    }

    /**
     * …and it comes back on its own. A second candidate that has left the
     * rendering set — declined here, expired by `sweep_expired_gig_requests`
     * (mig 0090) in practice — is not a candidate, so the remaining one is
     * unambiguous again.
     */
    @Test
    fun `a second row that draws no card does not make the first ambiguous`() {
        val quote = ThreadQuote.pick(
            requests = listOf(
                request(id = "dead", status = GigRequestStatus.Declined),
                request(id = "live"),
            ),
            thread = thread(),
            viewerIsArtist = false,
            nowMs = now,
        )
        assertEquals("live", quote?.requestId)
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
                    thread = thread(),
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
            thread = thread(),
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
