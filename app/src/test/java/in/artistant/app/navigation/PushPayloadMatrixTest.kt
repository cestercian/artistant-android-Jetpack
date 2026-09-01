package `in`.artistant.app.navigation

import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /**
     * …and what "the tab still switches" actually buys, through the router that carries it:
     * an id-less `message` push lands the tap on the INBOX, not on a null thread. The
     * action's null `threadId` is not a destination, it is the absence of one — the
     * Messages tab is the destination, and the scaffold's id effect returns early.
     * Mirrors iOS, which lands the same payload on the thread list.
     */
    @Test
    fun anIdLessMessagePush_landsOnTheInbox_notOnANullThread() {
        val clientRouter = TabRouter()
        clientRouter.apply(route("message", threadId = null, role = AppRole.Client))
        assertEquals(ClientDeepTab.Messages, clientRouter.consumePendingClientTab())
        assertNull(clientRouter.consumePendingThread())

        val artistRouter = TabRouter()
        artistRouter.apply(route("message", threadId = null, role = AppRole.Artist))
        assertEquals(ArtistDeepTab.Messages, artistRouter.consumePendingArtistTab())
        assertNull(artistRouter.consumePendingThread())
    }

    /**
     * A blank id is as absent as a missing one, and used to not be.
     *
     * FCM hands every field over as a String, so a server that sends `""` is saying exactly
     * what one that sends nothing says. The `?.let` guards only saw null, so
     * `OpenBookingDetail("")` reached the scaffold and `nav.navigate("booking_detail/")`
     * matched no destination in the graph — the malformed push crashed on tap rather than
     * being ignored. `pushNotificationPlan` had always normalized the same payload; now the
     * showing half and the routing half agree on what "no id" means.
     */
    @Test
    fun blankIdsCountAsMissing_notAsIds() {
        assertTrue(route("booking_confirmed_client", bookingId = "") is PushDeepLinkAction.Ignore)
        assertTrue(route("booking_review_request", bookingId = "   ") is PushDeepLinkAction.Ignore)
        assertTrue(
            route("booking_reminder_24h", bookingId = "", role = AppRole.Client) is PushDeepLinkAction.Ignore,
        )
        // The id-less rows keep their tab-only behaviour rather than becoming Ignore.
        assertEquals(PushDeepLinkAction.OpenThread(null, artistSide = false), route("message", threadId = ""))
        assertEquals(
            PushDeepLinkAction.OpenGigRequest(null),
            route("gig_request", requestId = "  ", role = AppRole.Artist),
        )
    }

    /** Padding is trimmed off an id, never carried into the route string. */
    @Test
    fun idsAreTrimmed_soNoRouteIsBuiltFromPaddedText() {
        assertEquals(
            PushDeepLinkAction.OpenBookingDetail("b-1"),
            route("booking_confirmed_client", bookingId = " b-1 "),
        )
        assertEquals(PushDeepLinkAction.OpenThread("t-1", artistSide = false), route("message", threadId = "\tt-1 "))
    }

    /**
     * The event name is normalized by that same rule, because `pushNotificationPlan` trims
     * it before choosing a channel: " message " was shown to the user on the messages
     * channel and then routed to Ignore, so the notification promised a thread and the tap
     * delivered nothing. Trimming is all that changes — the contract stays case-sensitive.
     */
    @Test
    fun eventNameIsTrimmed_soTheChannelShownAndTheRouteTakenAgree() {
        assertEquals(PushDeepLinkAction.OpenThread("t-1", artistSide = false), route(" message ", threadId = "t-1"))
        assertEquals(PushDeepLinkAction.ArtistGigs, route("booking_confirmed_artist\n", role = AppRole.Artist))
        assertTrue(route(" MESSAGE ") is PushDeepLinkAction.Ignore)
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

        assertEquals(ArtistDeepTab.Messages, router.consumePendingArtistTab())
        assertEquals("t-1", router.consumePendingThread())
    }
}
