package `in`.artistant.app.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TabRouter is the only process-wide place a *previous* session's push can
 * survive: the pending* channels outlive the screens that consume them.
 * `PushService.onSignedOut()` calls [TabRouter.clearTransients] for exactly
 * that reason, so these tests pin the leakage contract:
 *
 *  - sign-out wipes every pending channel,
 *  - a new push wipes stale channels BEFORE writing its own,
 *  - consuming a channel is one-shot.
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
    }

    @Test
    fun aNewPushDropsAnUnconsumedOlderOne() {
        val router = TabRouter()
        router.apply(PushDeepLinkAction.OpenBookingDetail("b-1", autoReview = true))
        assertEquals("b-1", router.pendingBookingDetail.value)
        assertEquals("b-1", router.pendingReviewSheet.value)

        router.apply(PushDeepLinkAction.OpenThread("t-2", artistSide = false))

        assertNull(router.pendingBookingDetail.value)
        assertNull(router.pendingReviewSheet.value)
        assertEquals("t-2", router.pendingThreadId.value)
    }

    @Test
    fun ignoredPush_stillClearsStaleTransients_andLeavesTabsAlone() {
        val router = TabRouter()
        router.apply(PushDeepLinkAction.OpenGigRequest("r-1"))
        val tabBefore = router.artistTab.value

        router.apply(PushDeepLinkAction.Ignore)

        assertNull(router.pendingGigRequestId.value)
        assertEquals(tabBefore, router.artistTab.value)
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
    fun messagePush_selectsTheMessagesTabOnTheViewersOwnSide() {
        val artistRouter = TabRouter()
        artistRouter.apply(PushDeepLinkAction.OpenThread("t-1", artistSide = true))
        assertEquals(ArtistDeepTab.Messages, artistRouter.artistTab.value)
        assertEquals(ClientDeepTab.Discover, artistRouter.clientTab.value) // untouched

        val clientRouter = TabRouter()
        clientRouter.apply(PushDeepLinkAction.OpenThread("t-1", artistSide = false))
        assertEquals(ClientDeepTab.Messages, clientRouter.clientTab.value)
        assertEquals(ArtistDeepTab.Home, clientRouter.artistTab.value) // untouched
    }

    @Test
    fun bookingReviewPush_landsOnBookingsWithTheReviewSheetArmed() {
        val router = TabRouter()

        router.apply(PushDeepLinkAction.OpenBookingDetail("b-7", autoReview = true))

        assertEquals(ClientDeepTab.Bookings, router.clientTab.value)
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
        assertEquals(ArtistDeepTab.Gigs, router.artistTab.value)

        router.apply(PushDeepLinkAction.ArtistHome)
        assertEquals(ArtistDeepTab.Home, router.artistTab.value)
    }
}
