package `in`.artistant.app.data.model

import `in`.artistant.app.feature.artisthome.openGigRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decode contract for [GigRequestStatus] — the same rule [BookingStatus] got in
 * PR #47, applied to the quote loop.
 *
 * The server owns the status vocabulary and can move ahead of a shipped client
 * (a future `withdrawn` / `converted`, or a row stamped by the web client). The
 * fallback for a status this build doesn't know must therefore be NEUTRAL.
 * `Open` is the opposite of neutral: it labels the row "Awaiting response", it
 * is the state the artist's Accept/Decline/Counter dock hangs off
 * (`GigRequestDetailViewModel.showActions`), and it is what puts the request in
 * Home's "New requests" rail — so decoding an unrecognised status to it handed
 * the artist live CTAs over a row this build cannot reason about, and Accept
 * fired a status PATCH the server may already have terminalized.
 *
 * Acceptance tests: they assert the property (never actionable), not the enum
 * name, so they keep holding if the case is renamed.
 */
class GigRequestStatusTest {

    // --- decode --------------------------------------------------------------

    @Test
    fun `fromDb maps a status this build does not know to the neutral fallback`() {
        for (raw in listOf("withdrawn", "converted", "some_future_status", "")) {
            val decoded = GigRequestStatus.fromDb(raw)
            assertNotEquals(
                "unknown status '$raw' must not decode to the actionable Open",
                GigRequestStatus.Open,
                decoded,
            )
            assertEquals(GigRequestStatus.Unknown, decoded)
        }
    }

    @Test
    fun `fromDb maps null to the neutral fallback`() {
        assertNotEquals(GigRequestStatus.Open, GigRequestStatus.fromDb(null))
        assertEquals(GigRequestStatus.Unknown, GigRequestStatus.fromDb(null))
    }

    @Test
    fun `fromDb still maps every known db value to its own case`() {
        assertEquals(GigRequestStatus.Open, GigRequestStatus.fromDb("open"))
        assertEquals(GigRequestStatus.Countered, GigRequestStatus.fromDb("countered"))
        assertEquals(GigRequestStatus.Accepted, GigRequestStatus.fromDb("accepted"))
        assertEquals(GigRequestStatus.Declined, GigRequestStatus.fromDb("declined"))
        assertEquals(GigRequestStatus.Expired, GigRequestStatus.fromDb("expired"))
    }

    @Test
    fun `the unknown case is decode-only and never a db value`() {
        // Its sentinel is not in the `gig_requests.status` check constraint, and
        // the only statuses ever written are the three literals the repository
        // sends from accept / decline / counter.
        assertFalse(
            GigRequestStatus.Unknown.dbValue in
                listOf("open", "countered", "accepted", "declined", "expired"),
        )
        // Copy parity with [BookingStatus.Unknown] (and iOS): one state, one word.
        assertEquals("Unavailable", GigRequestStatus.Unknown.label)
        assertEquals(BookingStatus.Unknown.label, GigRequestStatus.Unknown.label)
    }

    // --- consumers: the fallback must be inert -------------------------------

    @Test
    fun `an unrecognised status is never an open quote request`() {
        val rows = GigRequestStatus.entries.map { request(it) }

        // The negotiation inbox — the rail that offers the artist a live answer.
        assertEquals(
            listOf(GigRequestStatus.Open, GigRequestStatus.Countered),
            openGigRequests(rows).map { it.status },
        )
        assertTrue(openGigRequests(listOf(request(GigRequestStatus.fromDb("withdrawn")))).isEmpty())
    }

    private fun request(status: GigRequestStatus) = StoredRequest(
        raw = GigRequest(
            id = "q-${status.name}",
            client = "Meera",
            message = "Sangeet, rooftop set",
            date = "Sat, May 16, 2026",
            amount = 20000,
        ),
        status = status,
    )
}
