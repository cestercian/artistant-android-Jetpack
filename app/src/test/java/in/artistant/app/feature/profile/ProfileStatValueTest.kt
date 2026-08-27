package `in`.artistant.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The profile header's stat band, for the case the band could not be read.
 *
 * `ProfileViewModel.refresh()` fetches the bookings list best-effort — no
 * `onFailure` — so an offline / RLS / not-signed-in read used to leave the
 * counters on their `0` defaults and the header stated "0 BOOKINGS · 0 SAVED ·
 * 0 COMPLETED": a confident claim about the account's track record, identical to
 * what a genuinely new user sees, with no error and no retry anywhere near it.
 * Nulling the counters until a read answers is what makes the two states
 * different, and this is the render rule that shows the difference.
 *
 * Pure over an `Int?` for the same reason [LiveBookingsCountTest] is pure over a
 * `List<Booking>` — the ViewModel needs Hilt, a session and a DataStore-backed
 * AppPreferences to construct, none of which this suite can build.
 */
class ProfileStatValueTest {

    @Test
    fun `a count that was read prints as itself`() {
        assertEquals("3", profileStatValue(3))
    }

    @Test
    fun `a genuine zero still prints zero`() {
        // The honest empty account. This is the claim the em dash exists to stop
        // a FAILED read from making, so it must survive intact for a real read.
        assertEquals("0", profileStatValue(0))
    }

    @Test
    fun `a count that could not be read prints an em dash, not zero`() {
        assertEquals("—", profileStatValue(null))
    }
}
