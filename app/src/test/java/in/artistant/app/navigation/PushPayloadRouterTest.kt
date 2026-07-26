package `in`.artistant.app.navigation

import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins send-push → TabRouter event mapping (iOS PushService parity). */
class PushPayloadRouterTest {
    @Test
    fun message_routesClientToThread() {
        val action = PushPayloadRouter.route(
            event = "message",
            bookingId = null,
            threadId = "thread-1",
            requestId = null,
            role = AppRole.Client,
        )
        assertEquals(PushDeepLinkAction.OpenThread("thread-1", artistSide = false), action)
    }

    @Test
    fun message_routesArtistToThread() {
        val action = PushPayloadRouter.route(
            event = "message",
            bookingId = null,
            threadId = "thread-1",
            requestId = null,
            role = AppRole.Artist,
        )
        assertEquals(PushDeepLinkAction.OpenThread("thread-1", artistSide = true), action)
    }

    @Test
    fun gigRequest_landsOnArtistHome() {
        val action = PushPayloadRouter.route(
            event = "gig_request",
            bookingId = null,
            threadId = null,
            requestId = "req-9",
            role = AppRole.Artist,
        )
        assertEquals(PushDeepLinkAction.OpenGigRequest("req-9"), action)
    }

    @Test
    fun bookingReview_opensDetailWithAutoReview() {
        val action = PushPayloadRouter.route(
            event = "booking_review_request",
            bookingId = "b-1",
            threadId = null,
            requestId = null,
            role = AppRole.Client,
        )
        assertEquals(PushDeepLinkAction.OpenBookingDetail("b-1", autoReview = true), action)
    }

    @Test
    fun unknownEvent_isIgnored() {
        assertTrue(
            PushPayloadRouter.route("nope", null, null, null, AppRole.Client) is PushDeepLinkAction.Ignore,
        )
    }
}
