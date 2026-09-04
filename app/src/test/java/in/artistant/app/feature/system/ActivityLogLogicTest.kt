package `in`.artistant.app.feature.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of the activity log (design 123): append + trim, the chip
 * mapping, the Today/Earlier split and the relative stamp.
 */
class ActivityLogLogicTest {

    private fun entry(
        id: String,
        event: String? = null,
        at: Long = 0L,
        read: Boolean = false,
    ) = ActivityEntry(id = id, event = event, title = "t", body = "b", receivedAtMs = at, read = read)

    // ── append + trim ────────────────────────────────────────────────────────

    @Test
    fun `append puts the newest entry first`() {
        val log = appendActivity(listOf(entry("old")), entry("new"))
        assertEquals(listOf("new", "old"), log.map { it.id })
    }

    @Test
    fun `append trims the oldest past the limit`() {
        val seeded = (1..5).map { entry("e$it") }
        val log = appendActivity(seeded, entry("newest"), limit = 3)
        assertEquals(listOf("newest", "e1", "e2"), log.map { it.id })
    }

    @Test
    fun `append never returns an empty log for a non-positive limit`() {
        // A limit of zero would silently discard the entry it was just handed,
        // which is the one outcome the caller cannot detect.
        val log = appendActivity(emptyList(), entry("only"), limit = 0)
        assertEquals(listOf("only"), log.map { it.id })
    }

    @Test
    fun `append into an empty log keeps the entry`() {
        assertEquals(listOf("first"), appendActivity(emptyList(), entry("first")).map { it.id })
    }

    // ── categories ───────────────────────────────────────────────────────────

    @Test
    fun `booking family maps to the bookings chip`() {
        listOf(
            "booking_confirmed_client",
            "booking_confirmed_artist",
            "booking_reminder_24h",
            "booking_request",
        ).forEach {
            assertEquals(it, ActivityCategory.Booking, activityCategory(it))
        }
    }

    @Test
    fun `gig request is a quote and a review request is a review`() {
        assertEquals(ActivityCategory.Quote, activityCategory("gig_request"))
        assertEquals(ActivityCategory.Review, activityCategory("booking_review_request"))
    }

    @Test
    fun `chat and unknown events stay out of every chip`() {
        assertEquals(ActivityCategory.Other, activityCategory("message"))
        assertEquals(ActivityCategory.Other, activityCategory("a_future_server_event"))
        assertEquals(ActivityCategory.Other, activityCategory(null))
    }

    @Test
    fun `a padded event name still maps`() {
        // The push payload arrives as FCM data strings; the plan and the router
        // both trim, and a third reading of the same field must agree with them.
        assertEquals(ActivityCategory.Quote, activityCategory("  gig_request "))
    }

    @Test
    fun `All admits everything a chip would hide`() {
        val chat = entry("m", event = "message")
        assertTrue(matchesFilter(chat, ActivityFilter.All))
        assertFalse(matchesFilter(chat, ActivityFilter.Bookings))
        assertFalse(matchesFilter(chat, ActivityFilter.Quotes))
        assertFalse(matchesFilter(chat, ActivityFilter.Reviews))
    }

    // ── grouping ─────────────────────────────────────────────────────────────

    @Test
    fun `today is the calendar day, not the last 24 hours`() {
        val midnight = 1_000_000L
        val (today, earlier) = groupActivity(
            listOf(
                entry("this-morning", at = midnight + 1),
                entry("exactly-midnight", at = midnight),
                entry("last-night", at = midnight - 1),
            ),
            startOfTodayMs = midnight,
        )
        assertEquals(listOf("this-morning", "exactly-midnight"), today.map { it.id })
        assertEquals(listOf("last-night"), earlier.map { it.id })
    }

    @Test
    fun `grouping preserves the incoming order within each group`() {
        val midnight = 100L
        val (today, _) = groupActivity(
            listOf(entry("a", at = 300), entry("b", at = 200)),
            startOfTodayMs = midnight,
        )
        assertEquals(listOf("a", "b"), today.map { it.id })
    }

    // ── stamps ───────────────────────────────────────────────────────────────

    @Test
    fun `stamps step through minutes, hours, days and weeks`() {
        val now = 10_000_000_000L
        val minute = 60_000L
        assertEquals("now", relativeStamp(now - 30_000, now))
        assertEquals("2m", relativeStamp(now - 2 * minute, now))
        assertEquals("59m", relativeStamp(now - 59 * minute, now))
        assertEquals("1h", relativeStamp(now - 60 * minute, now))
        assertEquals("23h", relativeStamp(now - 23 * 60 * minute, now))
        assertEquals("1d", relativeStamp(now - 24 * 60 * minute, now))
        assertEquals("6d", relativeStamp(now - 6 * 24 * 60 * minute, now))
        assertEquals("1w", relativeStamp(now - 7 * 24 * 60 * minute, now))
    }

    @Test
    fun `a clock that moved backwards never renders a negative stamp`() {
        // A manual clock change or a caught-up timezone puts a stored timestamp
        // in the future. "-3m" reads as a bug in the app rather than the clock.
        assertEquals("now", relativeStamp(receivedAtMs = 2_000L, nowMs = 1_000L))
    }
}
