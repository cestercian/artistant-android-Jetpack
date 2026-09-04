package `in`.artistant.app.feature.system

import `in`.artistant.app.designsystem.theme.AppRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a row on screen 123 goes.
 *
 * The rule under test is that a row lands wherever the NOTIFICATION for the same
 * payload would have landed — including the two fallbacks `TabRouter.apply` makes
 * when an id is missing. Rows used to render as tappable and then do nothing for
 * every tab-only action and for both of those fallbacks, which is a control that
 * lies about being one.
 */
class ActivityDestinationTest {

    private fun entry(
        event: String?,
        bookingId: String? = null,
        threadId: String? = null,
        requestId: String? = null,
    ) = ActivityEntry(
        id = "e",
        event = event,
        title = "t",
        body = "b",
        receivedAtMs = 0L,
        bookingId = bookingId,
        threadId = threadId,
        requestId = requestId,
    )

    @Test
    fun `a confirmation opens its booking`() {
        val destination = activityDestination(
            entry("booking_confirmed_client", bookingId = "b-1"),
            AppRole.Client,
        )
        assertEquals(ActivityDestination.Booking("b-1"), destination)
    }

    @Test
    fun `a chat push opens its thread`() {
        assertEquals(
            ActivityDestination.Thread("t-1"),
            activityDestination(entry("message", threadId = "t-1"), AppRole.Client),
        )
    }

    @Test
    fun `a chat push with no thread id opens the inbox`() {
        // Not "nothing": the user was shown a message notification, and tapping
        // its record has to reach their messages. Same call the tap path makes.
        assertEquals(
            ActivityDestination.Messages,
            activityDestination(entry("message"), AppRole.Client),
        )
    }

    @Test
    fun `a gig request opens the request`() {
        assertEquals(
            ActivityDestination.GigRequest("r-1"),
            activityDestination(entry("gig_request", requestId = "r-1"), AppRole.Artist),
        )
    }

    @Test
    fun `a gig request with no id opens the studio it is listed in`() {
        assertEquals(
            ActivityDestination.Home,
            activityDestination(entry("gig_request"), AppRole.Artist),
        )
    }

    @Test
    fun `an artist-side confirmation opens Gigs`() {
        assertEquals(
            ActivityDestination.Gigs,
            activityDestination(entry("booking_confirmed_artist"), AppRole.Artist),
        )
    }

    @Test
    fun `a booking request opens the studio`() {
        assertEquals(
            ActivityDestination.Home,
            activityDestination(entry("booking_request"), AppRole.Artist),
        )
    }

    @Test
    fun `a reminder follows the role, exactly as the tap does`() {
        assertEquals(
            ActivityDestination.Gigs,
            activityDestination(entry("booking_reminder_24h", bookingId = "b-1"), AppRole.Artist),
        )
        assertEquals(
            ActivityDestination.Booking("b-1"),
            activityDestination(entry("booking_reminder_24h", bookingId = "b-1"), AppRole.Client),
        )
    }

    @Test
    fun `a review request opens the booking it is about`() {
        assertEquals(
            ActivityDestination.Booking("b-1"),
            activityDestination(entry("booking_review_request", bookingId = "b-1"), AppRole.Client),
        )
    }

    @Test
    fun `an event this build has never heard of has no destination`() {
        // The row still renders — it IS a record of something that arrived — but
        // it draws without an affordance rather than looking tappable.
        assertNull(activityDestination(entry("some_event_from_the_future"), AppRole.Client))
    }

    @Test
    fun `a payload with no event at all has no destination`() {
        assertNull(activityDestination(entry(null), AppRole.Client))
        assertNull(activityDestination(entry("   "), AppRole.Client))
    }

    @Test
    fun `a confirmation whose booking id arrived blank has no destination`() {
        // `booking_detail/` matches nothing in either graph, so the honest
        // rendering is a row that cannot be tapped.
        assertNull(
            activityDestination(entry("booking_confirmed_client", bookingId = "  "), AppRole.Client),
        )
    }
}
