package `in`.artistant.app.feature.booking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The funnel's date/time strip, pinned against a fixed clock.
 *
 * The regression underneath all of this: the strip seeds at "now", so chip 0 is
 * always today, and the time grid used to be the artist's whole published list
 * regardless of the hour. A client opening the funnel at 23:10 was preselected
 * onto "8:30 PM" and could file a live `pending_confirm` request for a show that
 * had already ended — no server guard catches it either.
 *
 * Every case here passes its own `nowMs` rather than reading the system clock,
 * which is the only way to assert "late at night" from a suite that runs at
 * whatever hour CI happens to start.
 */
class BookingSlotsTest {

    /**
     * An IST wall-clock instant, because that is the clock a gig label means.
     *
     * Deliberately NOT `Calendar.getInstance()`: the filter reads both halves of
     * its comparison in Asia/Kolkata, so a helper on the device zone would make
     * every case here pass only on a machine that happens to sit in India.
     */
    private fun at(hour: Int, minute: Int, day: Int = 15): Long =
        Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
            clear()
            set(2026, Calendar.AUGUST, day, hour, minute, 0)
        }.timeInMillis

    // ── slotMinutesOfDay ────────────────────────────────────────────────────

    @Test
    fun slotMinutesOfDay_readsTheCanonicalGrid() {
        assertEquals(18 * 60, slotMinutesOfDay("6:00 PM"))
        assertEquals(19 * 60 + 30, slotMinutesOfDay("7:30 PM"))
        assertEquals(23 * 60, slotMinutesOfDay("11:00 PM"))
    }

    @Test
    fun slotMinutesOfDay_getsTheTwelveOClockCasesRight() {
        // The two the naive "+12 for PM" version gets wrong.
        assertEquals(12 * 60, slotMinutesOfDay("12:00 PM"))
        assertEquals(0, slotMinutesOfDay("12:00 AM"))
        assertEquals(9 * 60 + 15, slotMinutesOfDay("9:15 AM"))
    }

    @Test
    fun slotMinutesOfDay_refusesALabelThisAppDidNotWrite() {
        assertNull(slotMinutesOfDay("20:30"))
        assertNull(slotMinutesOfDay("8:30"))
        assertNull(slotMinutesOfDay("late"))
        assertNull(slotMinutesOfDay("13:00 PM"))
        assertNull(slotMinutesOfDay(""))
    }

    // ── bookableTimeSlots ───────────────────────────────────────────────────

    @Test
    fun bookableTimeSlots_dropsTodaysSlotsTheClockHasPassed() {
        val now = at(hour = 20, minute = 0)

        // 6:00 and 7:30 are gone; 8:30 is half an hour out and still askable.
        assertEquals(
            listOf("8:30 PM", "9:00 PM", "10:00 PM", "11:00 PM"),
            bookableTimeSlots(DefaultTimeSlots, dayEpochMs = now, nowMs = now),
        )
    }

    @Test
    fun bookableTimeSlots_readsTheGigClockNotTheDevices() {
        // 22:00 in Dubai is 23:30 in India, so an 11:00 PM show is already gone
        // — even though the device's own clock still reads ten in the evening.
        val default = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dubai"))
            val istNow = at(hour = 23, minute = 30)

            assertFalse(
                "11:00 PM" in bookableTimeSlots(DefaultTimeSlots, dayEpochMs = istNow, nowMs = istNow),
            )
        } finally {
            TimeZone.setDefault(default)
        }
    }

    @Test
    fun bookableTimeSlots_treatsASlotStartingExactlyNowAsGone() {
        val now = at(hour = 20, minute = 30)

        // 8:30 PM is not a show you can still ask for at 8:30 PM.
        assertFalse("8:30 PM" in bookableTimeSlots(DefaultTimeSlots, dayEpochMs = now, nowMs = now))
    }

    @Test
    fun bookableTimeSlots_leavesEveryOtherDayWhole() {
        val now = at(hour = 23, minute = 10)
        val tomorrow = at(hour = 0, minute = 5, day = 16)

        assertEquals(
            DefaultTimeSlots,
            bookableTimeSlots(DefaultTimeSlots, dayEpochMs = tomorrow, nowMs = now),
        )
    }

    @Test
    fun bookableTimeSlots_emptiesOnceTodaysLastSlotHasGone() {
        val now = at(hour = 23, minute = 10)

        assertTrue(bookableTimeSlots(DefaultTimeSlots, dayEpochMs = now, nowMs = now).isEmpty())
    }

    @Test
    fun bookableTimeSlots_keepsASlotItCannotParse() {
        val now = at(hour = 23, minute = 10)

        // Hiding a slot we merely failed to read would take a bookable time off
        // the artist's own profile — the worse of the two mistakes.
        assertEquals(
            listOf("after midnight"),
            bookableTimeSlots(listOf("6:00 PM", "after midnight"), dayEpochMs = now, nowMs = now),
        )
    }

    // ── upcomingDateChips ───────────────────────────────────────────────────

    @Test
    fun upcomingDateChips_marksTodayUnavailableOnceItsLastSlotHasPassed() {
        val now = at(hour = 23, minute = 10)

        val chips = upcomingDateChips(count = 3, timeSlots = DefaultTimeSlots, nowMs = now)

        assertFalse("today has nothing left to book", chips[0].available)
        assertTrue(chips[1].available)
        assertTrue(chips[2].available)
    }

    @Test
    fun upcomingDateChips_keepsTodayWhileAnyOfItsSlotsRemain() {
        val now = at(hour = 19, minute = 0)

        val chips = upcomingDateChips(count = 2, timeSlots = DefaultTimeSlots, nowMs = now)

        assertTrue(chips[0].available)
    }

    @Test
    fun upcomingDateChips_withNoSlotsIgnoresTheClockEntirely() {
        val now = at(hour = 23, minute = 55)

        // The quote screen asks for a date, not a start time, so it hands over no
        // grid and today must stay pickable.
        val chips = upcomingDateChips(count = 2, nowMs = now)

        assertTrue(chips[0].available)
    }

    @Test
    fun upcomingDateChips_stillHonoursTheArtistsWeekdays() {
        val now = at(hour = 9, minute = 0)

        val chips = upcomingDateChips(daysAvailable = listOf("Sat"), nowMs = now)

        assertTrue(chips.any { it.available })
        chips.forEach { chip ->
            assertEquals(chip.weekdayAbbrev.equals("Sat", ignoreCase = true), chip.available)
        }
    }

    @Test
    fun upcomingDateChips_needsBothTheWeekdayAndTheClock() {
        val now = at(hour = 23, minute = 10)
        val todayAbbrev = upcomingDateChips(count = 1, nowMs = now).first().weekdayAbbrev

        val chips = upcomingDateChips(
            count = 1,
            daysAvailable = listOf(todayAbbrev),
            timeSlots = DefaultTimeSlots,
            nowMs = now,
        )

        assertFalse("a legal weekday with no slot left is still not bookable", chips[0].available)
    }
}
