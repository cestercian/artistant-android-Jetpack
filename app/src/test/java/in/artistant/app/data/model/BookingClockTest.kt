package `in`.artistant.app.data.model

import `in`.artistant.app.testsupport.booking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

/**
 * What "when is this gig?" resolves to — the clock every manage action reads.
 *
 * Three wire shapes reach the client and all three have to work:
 *  - `start_datetime` with a trailing `Z`;
 *  - `start_datetime` with a numeric offset (`…+00:00`) — `start_datetime` is a
 *    `timestamptz`, so PostgREST may hand back either. `Instant.parse`
 *    (ISO_INSTANT) rejects the offset form on Android's java.time, hence the
 *    explicit patterns behind it. Note this half cannot fail on the JVM these
 *    tests run on: JDK 12+ widened ISO_INSTANT to take offsets, which is exactly
 *    why the bug survived desktop review;
 *  - no timestamp at all, only the `date_label` / `time_label` pair. Every
 *    booking made through `FakeBookingsRepository` (harness, debug builds) is in
 *    that shape, and a screen that gives up here refuses a gig whose show time
 *    it is printing two sections higher.
 *
 * BookingDetailScreen's "Add to calendar" used to hand-roll `Instant.parse` and
 * failed the last two; it now goes through these.
 */
class BookingClockTest {

    private val defaultZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreZone() {
        TimeZone.setDefault(defaultZone)
    }

    @Test
    fun `start resolves the plain Z form`() {
        val b = booking(startIso = "2026-05-16T20:30:00Z")
        assertEquals(Instant.parse("2026-05-16T20:30:00Z").toEpochMilli(), b.resolvedStartEpochMs())
    }

    @Test
    fun `start resolves the numeric-offset timestamptz form`() {
        val b = booking(startIso = "2026-05-16T20:30:00+00:00")
        assertEquals(Instant.parse("2026-05-16T20:30:00Z").toEpochMilli(), b.resolvedStartEpochMs())
    }

    /** Zone-less form — only the explicit pattern list can take this one. */
    @Test
    fun `start resolves a timestamp with no zone marker as UTC`() {
        assertNull("precondition: ISO_INSTANT rejects this shape", isoInstantOrNull("2026-05-16T20:30:00"))
        val b = booking(startIso = "2026-05-16T20:30:00")
        assertEquals(Instant.parse("2026-05-16T20:30:00Z").toEpochMilli(), b.resolvedStartEpochMs())
    }

    /** No timestamp column — the labels on screen are the answer, read as IST. */
    @Test
    fun `start falls back to the date and time labels in IST`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        val b = booking(date = "Sat, May 16, 2026", time = "8:30 PM", startIso = null)
        // 8:30 PM IST = 15:00Z — not the device's zone, wherever the phone is.
        assertEquals(Instant.parse("2026-05-16T15:00:00Z").toEpochMilli(), b.resolvedStartEpochMs())
    }

    @Test
    fun `end reads the same forms as start`() {
        val b = booking(startIso = "2026-05-16T20:30:00+00:00")
            .copy(endDatetimeIso = "2026-05-16T22:30:00+00:00")
        assertEquals(Instant.parse("2026-05-16T22:30:00Z").toEpochMilli(), b.resolvedEndEpochMs())
    }

    /** Null, not a guess: the caller owns the placeholder gig length. */
    @Test
    fun `end is null when the row carries no end datetime`() {
        assertNull(booking(startIso = "2026-05-16T20:30:00Z").resolvedEndEpochMs())
    }

    private fun isoInstantOrNull(raw: String): Long? =
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
}
