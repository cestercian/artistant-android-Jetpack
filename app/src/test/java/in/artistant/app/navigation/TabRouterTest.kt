package `in`.artistant.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TabRouter is the only process-wide place a *previous* session's push can
 * survive: the pending* channels outlive the screens that consume them.
 * `PushService.onSigningOut()` calls [TabRouter.clearTransients] for exactly
 * that reason, so these tests pin the leakage contract:
 *
 *  - sign-out wipes every pending channel,
 *  - a new push wipes stale channels BEFORE writing its own,
 *  - consuming a channel is one-shot.
 *
 * The tab channels are held to the same contract as the ids, because they are
 * events and not selections — see [TabRouter.pendingArtistTab] for the two bugs
 * that came of treating them as retained state.
 */
class TabRouterTest {

    @Test
    fun clearTransients_wipesEveryPendingChannel() {
        val router = TabRouter()
        router.apply(PushDeepLinkAction.OpenBookingDetail("b-1", autoReview = true))
        router.apply(PushDeepLinkAction.OpenThread("t-1", artistSide = false))
        // OpenThread already cleared the booking channels; re-arm one to be sure
        // clearTransients isn't only exercising the empty case.
        router.apply(PushDeepLinkAction.OpenGigRequest("r-1"))

        router.clearTransients()

        assertNull(router.pendingBookingDetail.value)
        assertNull(router.pendingReviewSheet.value)
        assertNull(router.pendingThreadId.value)
        assertNull(router.pendingGigRequestId.value)
        assertNull(router.pendingClientTab.value)
        assertNull(router.pendingArtistTab.value)
    }

    @Test
    fun aNewPushDropsAnUnconsumedOlderOne() {
        val router = TabRouter()
        router.apply(PushDeepLinkAction.OpenBookingDetail("b-1", autoReview = true))
        assertEquals("b-1", router.pendingBookingDetail.value)
        assertEquals("b-1", router.pendingReviewSheet.value)
        assertEquals(ClientDeepTab.Bookings, router.pendingClientTab.value)

        router.apply(PushDeepLinkAction.OpenThread("t-2", artistSide = false))

        assertNull(router.pendingBookingDetail.value)
        assertNull(router.pendingReviewSheet.value)
        assertEquals("t-2", router.pendingThreadId.value)
        assertEquals(ClientDeepTab.Messages, router.pendingClientTab.value)
    }

    @Test
    fun ignoredPush_stillClearsStaleTransients_andArmsNoTab() {
        val router = TabRouter()
        router.apply(PushDeepLinkAction.OpenGigRequest("r-1"))

        router.apply(PushDeepLinkAction.Ignore)

        assertNull(router.pendingGigRequestId.value)
        // An unroutable payload must not leave the gig-request push's tab armed to
        // fire later — that tab belonged to the event we just decided to ignore.
        assertNull(router.pendingArtistTab.value)
    }

    @Test
    fun consumePendingThread_isOneShot() {
        val router = TabRouter()
        router.apply(PushDeepLinkAction.OpenThread("t-1", artistSide = true))

        assertEquals("t-1", router.consumePendingThread())
        assertNull(router.consumePendingThread())
        assertNull(router.pendingThreadId.value)
    }

    @Test
    fun consumePendingGigRequestAndBookingDetail_areOneShot() {
        val router = TabRouter()
        router.apply(PushDeepLinkAction.OpenGigRequest("r-9"))
        assertEquals("r-9", router.consumePendingGigRequest())
        assertNull(router.consumePendingGigRequest())

        router.apply(PushDeepLinkAction.OpenBookingDetail("b-9"))
        assertEquals("b-9", router.consumePendingBookingDetail())
        assertNull(router.consumePendingBookingDetail())
    }

    @Test
    fun consumePendingTabs_areOneShot() {
        val router = TabRouter()
        router.apply(PushDeepLinkAction.ArtistGigs)
        assertEquals(ArtistDeepTab.Gigs, router.consumePendingArtistTab())
        assertNull(router.consumePendingArtistTab())

        router.apply(PushDeepLinkAction.OpenBookingDetail("b-3"))
        assertEquals(ClientDeepTab.Bookings, router.consumePendingClientTab())
        assertNull(router.consumePendingClientTab())
    }

    @Test
    fun messagePush_selectsTheMessagesTabOnTheViewersOwnSide() {
        val artistRouter = TabRouter()
        artistRouter.apply(PushDeepLinkAction.OpenThread("t-1", artistSide = true))
        assertEquals(ArtistDeepTab.Messages, artistRouter.pendingArtistTab.value)
        assertNull(artistRouter.pendingClientTab.value) // untouched

        val clientRouter = TabRouter()
        clientRouter.apply(PushDeepLinkAction.OpenThread("t-1", artistSide = false))
        assertEquals(ClientDeepTab.Messages, clientRouter.pendingClientTab.value)
        assertNull(clientRouter.pendingArtistTab.value) // untouched
    }

    @Test
    fun bookingReviewPush_landsOnBookingsWithTheReviewSheetArmed() {
        val router = TabRouter()

        router.apply(PushDeepLinkAction.OpenBookingDetail("b-7", autoReview = true))

        assertEquals(ClientDeepTab.Bookings, router.pendingClientTab.value)
        assertEquals("b-7", router.pendingBookingDetail.value)
        assertEquals("b-7", router.pendingReviewSheet.value)
    }

    @Test
    fun plainBookingPush_doesNotArmTheReviewSheet() {
        val router = TabRouter()

        router.apply(PushDeepLinkAction.OpenBookingDetail("b-7"))

        assertEquals("b-7", router.pendingBookingDetail.value)
        assertNull(router.pendingReviewSheet.value)
    }

    @Test
    fun artistTabPushes_selectGigsAndHome() {
        val router = TabRouter()

        router.apply(PushDeepLinkAction.ArtistGigs)
        assertEquals(ArtistDeepTab.Gigs, router.pendingArtistTab.value)

        router.apply(PushDeepLinkAction.ArtistHome)
        assertEquals(ArtistDeepTab.Home, router.pendingArtistTab.value)
    }

    /**
     * The regression: the tab was a non-null `MutableStateFlow` seeded with the
     * value `ArtistHome` re-writes, and a StateFlow drops a write equal to its
     * current value. An artist who launched (Home), tapped Messages by hand, then
     * tapped a `booking_request` push got NO navigation — `apply` wrote Home over
     * Home, nothing emitted, and the scaffold's effect never re-ran. The id-less
     * actions have no pending-id channel to navigate for them, so that push tap
     * was simply a no-op.
     */
    @Test
    fun reArmingTheSameTab_emitsAgain_soASecondPushTapIsNeverANoOp() {
        val router = TabRouter()

        router.apply(PushDeepLinkAction.ArtistHome)
        assertEquals(ArtistDeepTab.Home, router.consumePendingArtistTab())
        // …the artist now taps Messages themselves. The router holds nothing: it
        // never tracked the real tab, which is why re-applying it was never safe.
        assertNull(router.pendingArtistTab.value)

        router.apply(PushDeepLinkAction.ArtistHome)

        assertEquals(ArtistDeepTab.Home, router.consumePendingArtistTab())
    }

    /** Same for Gigs — `booking_confirmed_artist` fires repeatedly in a season. */
    @Test
    fun reArmingGigs_emitsAgain() {
        val router = TabRouter()

        router.apply(PushDeepLinkAction.ArtistGigs)
        assertEquals(ArtistDeepTab.Gigs, router.consumePendingArtistTab())

        router.apply(PushDeepLinkAction.ArtistGigs)

        assertEquals(ArtistDeepTab.Gigs, router.consumePendingArtistTab())
    }

    /**
     * A router nobody has pushed to arms NOTHING. This is what makes an activity
     * recreation harmless: the scaffolds' `LaunchedEffect` runs on first
     * composition, so a retained default (`Discover`/`Home`) was re-applied on
     * every font-scale change, day/night flip and process-death restore —
     * `navigateToTab`'s `popUpTo(start)` popping the back stack that
     * `rememberNavController` had just restored.
     */
    @Test
    fun aFreshRouterArmsNoTab_soARecreationNavigatesNowhere() {
        val router = TabRouter()

        assertNull(router.pendingClientTab.value)
        assertNull(router.pendingArtistTab.value)
        assertNull(router.consumePendingClientTab())
        assertNull(router.consumePendingArtistTab())
    }
}
