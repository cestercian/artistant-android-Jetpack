package `in`.artistant.app.state

import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The PURE push routing table ([DeepLinkRouter.routeFor]) — the P2a seam's tested core. Asserts
 * each event lands on the right tab + sets the right id channel per role, and that unknown events
 * (and id-less booking events) no-op. Plus a consume/clear check on the router's channels.
 */
class DeepLinkRouterTest {

    private val thread = "11111111-1111-1111-1111-111111111111"
    private val request = "22222222-2222-2222-2222-222222222222"
    private val booking = "33333333-3333-3333-3333-333333333333"

    private fun route(event: String, role: AppRole) =
        DeepLinkRouter.routeFor(event, role, threadId = thread, requestId = request, bookingId = booking)

    // --- message: shared screen, routes per role, carries the thread id ---------------------

    @Test fun `message routes the client to ClientMessages with the thread id`() {
        val t = route("message", AppRole.Client)!!
        assertEquals(DeepLinkTab.ClientMessages, t.tab)
        assertEquals(thread, t.threadId)
        assertNull(t.requestId); assertNull(t.bookingId)
    }

    @Test fun `message routes the artist to ArtistMessages with the thread id`() {
        val t = route("message", AppRole.Artist)!!
        assertEquals(DeepLinkTab.ArtistMessages, t.tab)
        assertEquals(thread, t.threadId)
    }

    // --- gig_request: artist-only, surfaces on Home with the request id ---------------------

    @Test fun `gig_request routes to ArtistHome with the request id regardless of role`() {
        for (role in AppRole.entries) {
            val t = route("gig_request", role)!!
            assertEquals(DeepLinkTab.ArtistHome, t.tab)
            assertEquals(request, t.requestId)
            assertNull(t.threadId); assertNull(t.bookingId)
        }
    }

    // --- booking_confirmed_client: client Bookings + booking id -----------------------------

    @Test fun `booking_confirmed_client routes to ClientBookings with the booking id`() {
        val t = route("booking_confirmed_client", AppRole.Client)!!
        assertEquals(DeepLinkTab.ClientBookings, t.tab)
        assertEquals(booking, t.bookingId)
        assertNull(t.threadId); assertNull(t.requestId)
    }

    // --- booking_confirmed_artist: artist Gigs, no id ---------------------------------------

    @Test fun `booking_confirmed_artist routes to ArtistGigs with no id`() {
        val t = route("booking_confirmed_artist", AppRole.Artist)!!
        assertEquals(DeepLinkTab.ArtistGigs, t.tab)
        assertNull(t.threadId); assertNull(t.requestId); assertNull(t.bookingId)
    }

    // --- booking_reminder_24h: fan-out, side chosen by the tapping device's role ------------

    @Test fun `booking_reminder_24h routes the artist to ArtistGigs with no id`() {
        val t = route("booking_reminder_24h", AppRole.Artist)!!
        assertEquals(DeepLinkTab.ArtistGigs, t.tab)
        assertNull(t.bookingId)
    }

    @Test fun `booking_reminder_24h routes the client to ClientBookings with the booking id`() {
        val t = route("booking_reminder_24h", AppRole.Client)!!
        assertEquals(DeepLinkTab.ClientBookings, t.tab)
        assertEquals(booking, t.bookingId)
    }

    // --- booking_review_request: client Bookings + booking id (review sheet is P2b/P4) ------

    @Test fun `booking_review_request routes to ClientBookings with the booking id`() {
        val t = route("booking_review_request", AppRole.Client)!!
        assertEquals(DeepLinkTab.ClientBookings, t.tab)
        assertEquals(booking, t.bookingId)
    }

    // --- null cases -------------------------------------------------------------------------

    @Test fun `unknown event does not route`() {
        assertNull(route("something_new", AppRole.Client))
        assertNull(route("something_new", AppRole.Artist))
    }

    @Test fun `booking events with a missing id do not route`() {
        // iOS no-ops these rather than landing on a detail-less booking screen.
        val noId = { e: String, r: AppRole ->
            DeepLinkRouter.routeFor(e, r, threadId = null, requestId = null, bookingId = null)
        }
        assertNull(noId("booking_confirmed_client", AppRole.Client))
        assertNull(noId("booking_review_request", AppRole.Client))
        assertNull(noId("booking_reminder_24h", AppRole.Client))
        // But the artist reminder + artist-confirmed carry no id, so they still route.
        assertEquals(DeepLinkTab.ArtistGigs, noId("booking_reminder_24h", AppRole.Artist)!!.tab)
        assertEquals(DeepLinkTab.ArtistGigs, noId("booking_confirmed_artist", AppRole.Artist)!!.tab)
    }

    // --- router channels: consume clears --------------------------------------------------

    @Test fun `consume clears each parked channel`() {
        val router = DeepLinkRouter()
        router.deepLinkTo(thread)
        assertEquals(thread, router.pendingThreadId.value)
        router.consumePendingThread()
        assertNull(router.pendingThreadId.value)

        // The request/booking/tab channels default null and clear back to null.
        router.consumePendingRequest(); assertNull(router.pendingRequestId.value)
        router.consumePendingBooking(); assertNull(router.pendingBookingId.value)
        router.consumePendingTab(); assertNull(router.pendingTab.value)
    }
}
