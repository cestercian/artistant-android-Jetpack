package `in`.artistant.app.platform.push

import `in`.artistant.app.platform.preferences.NotificationSettings
import `in`.artistant.app.platform.preferences.NotificationToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Screen 124's switches, applied to an arriving push.
 *
 * The bug this suite pins is the one the review found: the eight toggles persisted perfectly
 * and nothing ever read them, so "Booking confirmed or declined · off" and "Quiet hours · on"
 * were controls over nothing. The decision is a table — category × preference × wall clock —
 * so it is tested as one.
 */
class PushDeliveryTest {

    private val allOn = NotificationSettings(newActs = true, tipsAndOffers = true)
    private val midnight = 0
    private val noon = 12

    // --- the mapping ---------------------------------------------------------------------

    @Test
    fun `every event the router knows has a switch that owns it`() {
        // The router's `when` is the vocabulary; a category map that drifts from it means a
        // switch silently governing nothing, which is the whole defect.
        val events = mapOf(
            "message" to NotificationToggle.QuotesAndReplies,
            "gig_request" to NotificationToggle.QuotesAndReplies,
            "booking_request" to NotificationToggle.QuotesAndReplies,
            "booking_confirmed_client" to NotificationToggle.BookingUpdates,
            "booking_confirmed_artist" to NotificationToggle.BookingUpdates,
            "booking_reminder_24h" to NotificationToggle.ShowDayReminder,
            "booking_review_request" to NotificationToggle.ReviewReminders,
        )
        for ((event, toggle) in events) {
            assertEquals("category for $event", toggle, pushCategoryFor(event))
        }
    }

    @Test
    fun `an event no switch names has no category, and is never dropped`() {
        assertNull(pushCategoryFor("something_the_server_grew_later"))
        assertNull(pushCategoryFor(null))
        assertEquals(
            PushDelivery.Post,
            pushDeliveryFor("something_the_server_grew_later", NotificationSettings(), noon),
        )
    }

    @Test
    fun `the category read trims, the way the plan and the router do`() {
        assertEquals(NotificationToggle.QuotesAndReplies, pushCategoryFor(" message "))
    }

    // --- rule 1: off means dropped, not quietened ----------------------------------------

    @Test
    fun `a switched-off category is dropped`() {
        val off = allOn.copy(bookingUpdates = false)
        assertEquals(PushDelivery.Drop, pushDeliveryFor("booking_confirmed_client", off, noon))
    }

    @Test
    fun `switching one category off leaves the others alone`() {
        val noReviews = allOn.copy(reviewReminders = false)
        assertEquals(PushDelivery.Drop, pushDeliveryFor("booking_review_request", noReviews, noon))
        assertEquals(PushDelivery.Post, pushDeliveryFor("message", noReviews, noon))
    }

    @Test
    fun `off beats quiet hours — a dropped push is not a silent one`() {
        val off = allOn.copy(quotesAndReplies = false, quietHours = true)
        assertEquals(PushDelivery.Drop, pushDeliveryFor("message", off, midnight))
    }

    @Test
    fun `every mapped category can be switched off`() {
        // No row may be decorative: turning each one off has to change the answer for at least
        // one event that maps to it.
        val events = listOf(
            "message" to NotificationToggle.QuotesAndReplies,
            "booking_confirmed_client" to NotificationToggle.BookingUpdates,
            "booking_reminder_24h" to NotificationToggle.ShowDayReminder,
            "booking_review_request" to NotificationToggle.ReviewReminders,
        )
        for ((event, toggle) in events) {
            assertEquals(
                "$event with $toggle off",
                PushDelivery.Drop,
                pushDeliveryFor(event, allOn.with(toggle, false), noon),
            )
        }
    }

    // --- rule 2: quiet hours quieten, they never drop ------------------------------------

    @Test
    fun `the quiet window is 10pm to 8am`() {
        assertEquals(false, isQuietHour(21))
        assertEquals(true, isQuietHour(22))
        assertEquals(true, isQuietHour(23))
        assertEquals(true, isQuietHour(0))
        assertEquals(true, isQuietHour(7))
        assertEquals(false, isQuietHour(8))
        assertEquals(false, isQuietHour(12))
    }

    @Test
    fun `inside quiet hours an ordinary push posts silently`() {
        assertEquals(PushDelivery.Silent, pushDeliveryFor("message", allOn, 23))
        assertEquals(PushDelivery.Silent, pushDeliveryFor("gig_request", allOn, 3))
        assertEquals(PushDelivery.Silent, pushDeliveryFor("booking_review_request", allOn, 7))
    }

    @Test
    fun `outside quiet hours the same push posts normally`() {
        assertEquals(PushDelivery.Post, pushDeliveryFor("message", allOn, 9))
        assertEquals(PushDelivery.Post, pushDeliveryFor("message", allOn, 21))
    }

    @Test
    fun `quiet hours off means nothing is quietened at 3am`() {
        val noQuietHours = allOn.copy(quietHours = false)
        assertEquals(PushDelivery.Post, pushDeliveryFor("message", noQuietHours, 3))
    }

    // --- rule 3: the stated exception ----------------------------------------------------

    @Test
    fun `a booking confirmation still makes a sound at 3am, which is what the screen promises`() {
        assertEquals(PushDelivery.Post, pushDeliveryFor("booking_confirmed_client", allOn, 3))
        assertEquals(PushDelivery.Post, pushDeliveryFor("booking_confirmed_artist", allOn, 3))
    }

    @Test
    fun `an unknown event does NOT inherit the urgent exception`() {
        // "We don't know what this is" is not "this is urgent".
        assertEquals(PushDelivery.Silent, pushDeliveryFor("mystery_event", allOn, 2))
    }

    @Test
    fun `the show-day reminder is not urgent — the evening before can wait until morning`() {
        assertEquals(PushDelivery.Silent, pushDeliveryFor("booking_reminder_24h", allOn, 23))
    }

    // --- the channel the silent answer posts to ------------------------------------------

    @Test
    fun `every loud channel has a quiet twin, and it is a different channel`() {
        val loud = listOf(
            NotificationChannels.MESSAGES,
            NotificationChannels.BOOKINGS,
            NotificationChannels.GIGS,
        )
        val twins = loud.map { NotificationChannels.quietTwinOf(it) }
        assertEquals("twins must be distinct", twins.size, twins.toSet().size)
        for ((id, twin) in loud.zip(twins)) {
            assertEquals(false, id == twin)
        }
        assertEquals(NotificationChannels.MESSAGES_QUIET, NotificationChannels.quietTwinOf(NotificationChannels.MESSAGES))
        assertEquals(NotificationChannels.GIGS_QUIET, NotificationChannels.quietTwinOf(NotificationChannels.GIGS))
    }

    @Test
    fun `an unknown channel falls back to a registered quiet one, never to nothing`() {
        assertEquals(NotificationChannels.BOOKINGS_QUIET, NotificationChannels.quietTwinOf("invented"))
    }

    @Test
    fun `the defaults post everything transactional and drop neither marketing row it never gets`() {
        val defaults = NotificationSettings()
        assertEquals(PushDelivery.Post, pushDeliveryFor("message", defaults, noon))
        assertEquals(PushDelivery.Post, pushDeliveryFor("booking_confirmed_client", defaults, noon))
        assertEquals(PushDelivery.Post, pushDeliveryFor("booking_review_request", defaults, noon))
        // Marketing defaults to OFF, but no `send-push` event maps to it — so the default
        // cannot silently swallow anything the server actually sends.
        assertNull(pushCategoryFor("new_acts"))
    }
}
