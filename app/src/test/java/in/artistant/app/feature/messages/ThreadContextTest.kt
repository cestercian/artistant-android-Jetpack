package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.testsupport.ARTIST_ID
import `in`.artistant.app.testsupport.booking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * What a conversation is about.
 *
 * The interesting cases are all about *not* asserting things: a thread with no
 * booking, a booking the viewer can't read, a venue that is really a placeholder,
 * and a gig that has already happened. Each of those has an honest empty answer,
 * and each of them used to be the kind of thing a UI quietly invents.
 */
class ThreadContextTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    private fun at(year: Int, month: Int, day: Int): Long =
        ZonedDateTime.of(year, month, day, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun aThreadWithNoBookingIsAnInquiry() {
        val context = ThreadContext.resolve(
            Thread(id = "t-1", artistId = ARTIST_ID),
            bookings = mapOf("b-1" to booking(id = "b-1")),
            viewerIsArtist = false,
        )

        assertEquals(ThreadContext.INQUIRY, context)
        assertEquals("Inquiry", context.statusLabel)
        assertNull(context.detailLine)
    }

    /**
     * A miss keeps the id so the row can still route to the booking, but refuses
     * to invent a status. Falling back to a real status here would paint a state
     * onto a gig nobody has read.
     */
    @Test
    fun anUnreadableBookingKeepsTheIdAndDropsTheStatus() {
        val context = ThreadContext.resolve(
            Thread(id = "t-1", artistId = ARTIST_ID, bookingId = "b-gone"),
            bookings = emptyMap(),
            viewerIsArtist = false,
        )

        assertEquals("b-gone", context.bookingId)
        assertNull(context.status)
        assertEquals("Inquiry", context.statusLabel)
    }

    /** Ids round-trip through the server in mixed case; matching must not care. */
    @Test
    fun bookingLookupIsCaseInsensitive() {
        val context = ThreadContext.resolve(
            Thread(id = "t-1", artistId = ARTIST_ID, bookingId = "B-1"),
            bookings = mapOf("b-1" to booking(id = "b-1", status = BookingStatus.Confirmed)),
            viewerIsArtist = false,
        )

        assertEquals(BookingStatus.Confirmed, context.status)
    }

    /**
     * `create()` stamps "TBD" when the venue is left blank, so the string exists
     * but says nothing. Rendering it puts a placeholder in the line that is
     * supposed to say where the gig is.
     */
    @Test
    fun theTbdVenuePlaceholderIsTreatedAsNoVenue() {
        val context = ThreadContext.from(booking(venue = "TBD"), viewerIsArtist = false)

        assertNull(context.venue)
        assertEquals("Sat, May 16, 2026", context.detailLine)
    }

    @Test
    fun detailLineJoinsWhicheverHalvesExist() {
        val both = ThreadContext.from(booking(venue = "Rooftop"), viewerIsArtist = false)
        assertEquals("Sat, May 16, 2026 · Rooftop", both.detailLine)

        val dateOnly = ThreadContext.from(booking(venue = ""), viewerIsArtist = false)
        assertEquals("Sat, May 16, 2026", dateOnly.detailLine)
    }

    /** A zero fee is absent, not free — the matchmaker model never quotes ₹0. */
    @Test
    fun aZeroFeeIsTreatedAsMissing() {
        assertNull(ThreadContext.from(booking(fee = 0), viewerIsArtist = false).fee)
        assertEquals(20_000, ThreadContext.from(booking(fee = 20_000), viewerIsArtist = false).fee)
    }

    // --- whose move is it ----------------------------------------------------

    @Test
    fun pendingConfirmIsTheArtistsMoveNotTheClients() {
        val pending = booking(status = BookingStatus.PendingConfirm)

        assertTrue(ThreadContext.from(pending, viewerIsArtist = true).awaitingViewer)
        assertFalse(ThreadContext.from(pending, viewerIsArtist = false).awaitingViewer)
    }

    /** Everything past the request stage is nobody's pending decision. */
    @Test
    fun noOtherStatusEverAwaitsTheViewer() {
        val settled = listOf(
            BookingStatus.Confirmed,
            BookingStatus.Completed,
            BookingStatus.Cancelled,
            BookingStatus.Disputed,
            BookingStatus.Unknown,
        )

        settled.forEach { status ->
            assertFalse(
                "$status must not ask the artist for a decision",
                ThreadContext.from(booking(status = status), viewerIsArtist = true).awaitingViewer,
            )
        }
    }

    // --- how far off is it ---------------------------------------------------

    @Test
    fun timeUntilNamesTheNextTwoDaysAndThenCounts() {
        val now = at(2026, 5, 1)

        assertEquals("today", ThreadContext.timeUntil(at(2026, 5, 1), now, zone))
        assertEquals("tomorrow", ThreadContext.timeUntil(at(2026, 5, 2), now, zone))
        assertEquals("9 days out", ThreadContext.timeUntil(at(2026, 5, 10), now, zone))
        assertEquals("3 weeks out", ThreadContext.timeUntil(at(2026, 5, 22), now, zone))
    }

    /** "3 weeks out" under a gig that already happened is worse than silence. */
    @Test
    fun timeUntilSaysNothingAboutAPastGig() {
        assertNull(ThreadContext.timeUntil(at(2026, 4, 30), at(2026, 5, 1), zone))
    }

    @Test
    fun timeUntilSaysNothingWithoutAStart() {
        assertNull(ThreadContext.timeUntil(null, Instant.now().toEpochMilli(), zone))
    }

    /** Day granularity, so "tomorrow" doesn't become "today" late in the evening. */
    @Test
    fun timeUntilCountsCalendarDaysNotElapsedHours() {
        val lateTonight = ZonedDateTime.of(2026, 5, 1, 23, 30, 0, 0, zone).toInstant().toEpochMilli()
        val earlyTomorrow = ZonedDateTime.of(2026, 5, 2, 0, 30, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals("tomorrow", ThreadContext.timeUntil(earlyTomorrow, lateTonight, zone))
    }
}
