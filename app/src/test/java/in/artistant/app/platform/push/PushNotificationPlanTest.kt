package `in`.artistant.app.platform.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What an arriving push shows.
 *
 * The service that posts it needs a Context, but the decisions don't. These pin the
 * contract the receipt path now depends on: a push that arrives is SHOWN (it used to be
 * routed, yanking the user off the screen they were on and, because nothing was posted,
 * showing them nothing at all), one row per thing to look at, and no notification at
 * all for a payload that has nowhere to send anyone.
 */
class PushNotificationPlanTest {

    private fun payload(vararg pairs: Pair<String, String>) = mapOf(*pairs)

    @Test
    fun aChatPushCarriesTheServerCopyOnTheMessagesChannel() {
        val plan = pushNotificationPlan(
            payload(
                "artistant_event" to "message",
                "artistant_thread_id" to "thread-1",
                "title" to "Aditi",
                "body" to "Are you free on the 14th?",
            ),
        )

        assertEquals(NotificationChannels.MESSAGES, plan?.channelId)
        assertEquals("Aditi", plan?.title)
        assertEquals("Are you free on the 14th?", plan?.body)
    }

    @Test
    fun aGigRequestUsesTheGigsChannelAndBookingsTakeTheRest() {
        assertEquals(NotificationChannels.GIGS, pushChannelFor("gig_request"))
        assertEquals(NotificationChannels.BOOKINGS, pushChannelFor("booking_confirmed_client"))
        assertEquals(NotificationChannels.BOOKINGS, pushChannelFor("booking_reminder_24h"))
        // A server event this build has never heard of still has to be deliverable.
        assertEquals(NotificationChannels.BOOKINGS, pushChannelFor("something_new"))
    }

    /**
     * Two messages in one conversation are one conversation to look at, so the second
     * replaces the first rather than stacking a near-identical row beside it.
     */
    @Test
    fun twoPushesAboutTheSameThreadShareANotificationId() {
        val first = pushNotificationPlan(
            payload(
                "artistant_event" to "message",
                "artistant_thread_id" to "thread-1",
                "title" to "Aditi",
                "body" to "First",
            ),
        )
        val second = pushNotificationPlan(
            payload(
                "artistant_event" to "message",
                "artistant_thread_id" to "thread-1",
                "title" to "Aditi",
                "body" to "Second",
            ),
        )

        // Asserted non-null first, or two dropped notifications would agree on nothing
        // and pass.
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first?.notificationId, second?.notificationId)
    }

    @Test
    fun differentTargetsAndFamiliesKeepTheirOwnRows() {
        val thread = pushNotificationPlan(
            payload("artistant_event" to "message", "artistant_thread_id" to "thread-1"),
        )
        val otherThread = pushNotificationPlan(
            payload("artistant_event" to "message", "artistant_thread_id" to "thread-2"),
        )
        val booking = pushNotificationPlan(
            payload(
                "artistant_event" to "booking_confirmed_client",
                "artistant_booking_id" to "thread-1",
            ),
        )

        assertNotEquals(thread?.notificationId, otherThread?.notificationId)
        // Same id string, different family — the channel is part of the key, so a
        // booking never overwrites a chat.
        assertNotEquals(thread?.notificationId, booking?.notificationId)
    }

    /**
     * `PushPayloadRouter` maps an eventless payload to `Ignore`, so a notification for
     * it would promise somewhere to go and then open the app where it already was.
     */
    @Test
    fun aPayloadWithNoEventShowsNothing() {
        assertNull(pushNotificationPlan(emptyMap()))
        assertNull(pushNotificationPlan(payload("title" to "Aditi", "body" to "Hi")))
        assertNull(pushNotificationPlan(payload("artistant_event" to "   ")))
    }

    /** A titleless push still has to be tappable, so the caller titles it. */
    @Test
    fun aMissingTitleIsLeftToTheCaller() {
        val plan = pushNotificationPlan(
            payload("artistant_event" to "booking_confirmed_artist", "body" to ""),
        )

        assertNull(plan?.title)
        assertEquals("", plan?.body)
        assertEquals(NotificationChannels.BOOKINGS, plan?.channelId)
    }

    /** A blank id is as good as no id: fall through to the next one, then the event. */
    @Test
    fun blankIdsDoNotBecomeTheNotificationKey() {
        val blank = pushNotificationPlan(
            payload(
                "artistant_event" to "booking_review_request",
                "artistant_thread_id" to "",
                "artistant_booking_id" to "booking-9",
            ),
        )
        val direct = pushNotificationPlan(
            payload(
                "artistant_event" to "booking_review_request",
                "artistant_booking_id" to "booking-9",
            ),
        )

        assertNotNull(blank)
        assertEquals(direct?.notificationId, blank?.notificationId)
    }
}
