package `in`.artistant.app.navigation

import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `artistant_event` payload matrix, including the malformed cases.
 *
 * A push arrives as an untyped String map from FCM, so every id is nullable at
 * the boundary. The existing PushPayloadRouterTest covers the happy rows; this
 * one covers the missing-id and role-split rows, which is where a bad payload
 * would otherwise become a crash or a nav to nowhere.
 */
class PushPayloadMatrixTest {

    private fun route(
        event: String?,
        bookingId: String? = null,
        threadId: String? = null,
        requestId: String? = null,
        role: AppRole = AppRole.Client,
    ) = PushPayloadRouter.route(event, bookingId, threadId, requestId, role)

    @Test
    fun nullOrBlankEvent_isIgnored() {
        assertTrue(route(null) is PushDeepLinkAction.Ignore)
        assertTrue(route("") is PushDeepLinkAction.Ignore)
        assertTrue(route("   ") is PushDeepLinkAction.Ignore)
    }

    @Test
    fun bookingConfirmedClient_needsABookingId() {
        assertEquals(
            PushDeepLinkAction.OpenBookingDetail("b-1"),
            route("booking_confirmed_client", bookingId = "b-1"),
        )
        assertTrue(route("booking_confirmed_client", bookingId = null) is PushDeepLinkAction.Ignore)
    }

    @Test
    fun bookingConfirmedArtist_goesToTheGigsTab_withNoIdNeeded() {
        assertEquals(PushDeepLinkAction.ArtistGigs, route("booking_confirmed_artist", role = AppRole.Artist))
    }

    @Test
    fun reminderSplitsByRole() {
        assertEquals(
            PushDeepLinkAction.ArtistGigs,
            route("booking_reminder_24h", bookingId = "b-1", role = AppRole.Artist),
        )
        assertEquals(
            PushDeepLinkAction.OpenBookingDetail("b-1"),
            route("booking_reminder_24h", bookingId = "b-1", role = AppRole.Client),
        )
    }

    @Test
    fun clientReminderWithoutABookingId_isIgnored() {
        assertTrue(route("booking_reminder_24h", bookingId = null, role = AppRole.Client) is PushDeepLinkAction.Ignore)
    }

    @Test
    fun reviewRequestWithoutABookingId_isIgnored() {
        assertTrue(route("booking_review_request", bookingId = null) is PushDeepLinkAction.Ignore)
    }

    @Test
    fun reviewRequest_armsTheReviewSheet_plainConfirmDoesNot() {
        assertEquals(
            PushDeepLinkAction.OpenBookingDetail("b-1", autoReview = true),
            route("booking_review_request", bookingId = "b-1"),
        )
        assertEquals(
            PushDeepLinkAction.OpenBookingDetail("b-1", autoReview = false),
            route("booking_confirmed_client", bookingId = "b-1"),
        )
    }

    @Test
    fun bookingRequest_landsTheArtistOnHomeWhereTheAcceptCardLives() {
        assertEquals(PushDeepLinkAction.ArtistHome, route("booking_request", role = AppRole.Artist))
        // The client never receives this event, but a mis-targeted send must not
        // resolve to a client destination either.
        assertEquals(PushDeepLinkAction.ArtistHome, route("booking_request", role = AppRole.Client))
    }

    @Test
    fun messageAndGigRequest_tolerateAMissingId_withoutThrowing() {
        // Current contract: the tab still switches and the pending id is null,
        // so the screen simply shows its list rather than a specific row.
        assertEquals(
            PushDeepLinkAction.OpenThread(null, artistSide = false),
            route("message", threadId = null),
        )
        assertEquals(
            PushDeepLinkAction.OpenGigRequest(null),
            route("gig_request", requestId = null, role = AppRole.Artist),
        )
    }

    @Test
    fun unknownEventNamesAreIgnored_notGuessedAt() {
        assertTrue(route("booking_confirmed") is PushDeepLinkAction.Ignore)
        assertTrue(route("MESSAGE") is PushDeepLinkAction.Ignore) // case-sensitive by contract
        assertTrue(route("payout_released") is PushDeepLinkAction.Ignore)
    }

    @Test
    fun routedActionsSurviveTheRouterEndToEnd() {
        val router = TabRouter()

        router.apply(route("message", threadId = "t-1", role = AppRole.Artist))

        assertEquals(ArtistDeepTab.Messages, router.artistTab.value)
        assertEquals("t-1", router.consumePendingThread())
    }
}
