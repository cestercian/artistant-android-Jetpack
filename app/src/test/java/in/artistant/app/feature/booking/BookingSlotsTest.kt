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

    /**
     * The case Greptile caught: the funnel must not offer a slot whose stored
     * instant is already in the past.
     *
     * New York at 22:00 on the 27th is 07:30 on the 28th in Kolkata. When the
     * chip walk ran on the device calendar it produced a chip for "the 27th",
     * while the filter and `startEndIso` both resolve the day in India — so an
     * 8:30 PM slot survived and persisted as the 27th at 20:30 IST, eleven hours
     * gone. With the whole chain on one clock, chip 0 IS the 28th and the
     * evening is genuinely still ahead.
     */
    @Test
    fun upcomingDateChips_startTodayInIndia_notOnTheDevicesCalendar() {
        val default = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            // 22:00 on the 27th in New York == 07:30 on the 28th in Kolkata.
            val now = at(hour = 7, minute = 30, day = 28)

            val chips = upcomingDateChips(count = 1, timeSlots = DefaultTimeSlots, nowMs = now)

            assertTrue("the gig day is India's, so the evening is still ahead", chips[0].available)
            assertEquals("Fri, Aug 28, 2026", chips[0].label)
            assertEquals(
                DefaultTimeSlots,
                bookableTimeSlots(DefaultTimeSlots, dayEpochMs = now, nowMs = now),
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

    // ── the month grid (screen 05) ──────────────────────────────────────────

    @Test
    fun funnelMonthDays_padTheFirstAndLastWeeksWithTheNeighbouringMonths() {
        // October 2026 starts on a Thursday, so a Monday-first grid needs three
        // leading cells (28, 29, 30 September). The off-by-one that puts the 1st
        // under the wrong weekday is invisible in a screenshot.
        val days = funnelMonthDays(2026, Calendar.OCTOBER)

        assertEquals(0, days.size % 7)
        assertEquals(listOf(28, 29, 30), days.take(3).map { it.number })
        assertTrue(days.take(3).none { it.inMonth })
        assertEquals(1, days[3].number)
        assertTrue(days[3].inMonth)
        assertEquals(31, days.count { it.inMonth })
        assertTrue("trailing pad belongs to November", days.last().inMonth.not())
    }

    @Test
    fun funnelMonthDays_handleAFebruaryThatStartsOnASunday() {
        // Feb 2026 starts on a Sunday: six leading cells, and the last week is
        // whole, so the grid is exactly five rows with no trailing pad at all.
        val days = funnelMonthDays(2026, Calendar.FEBRUARY)

        assertEquals(6, days.takeWhile { !it.inMonth }.size)
        assertEquals(28, days.count { it.inMonth })
        assertEquals(0, days.size % 7)
    }

    @Test
    fun monthSelectableDays_dropEverythingBeforeToday() {
        val now = at(hour = 9, minute = 0, day = 15)

        val open = monthSelectableDays(2026, Calendar.AUGUST, nowMs = now)

        assertFalse("yesterday is not bookable", 14 in open)
        assertTrue("today is", 15 in open)
        assertTrue(31 in open)
    }

    @Test
    fun monthSelectableDays_areEmptyForAMonthThatHasPassed() {
        val now = at(hour = 9, minute = 0, day = 15)

        assertTrue(monthSelectableDays(2026, Calendar.JULY, nowMs = now).isEmpty())
        assertTrue(monthSelectableDays(2025, Calendar.DECEMBER, nowMs = now).isEmpty())
    }

    @Test
    fun monthSelectableDays_honourTheArtistsWeekdays() {
        val now = at(hour = 9, minute = 0, day = 1)

        val open = monthSelectableDays(
            year = 2026,
            month = Calendar.AUGUST,
            daysAvailable = listOf("Sat"),
            nowMs = now,
        )

        // August 2026: the Saturdays are 1, 8, 15, 22, 29.
        assertEquals(setOf(1, 8, 15, 22, 29), open)
    }

    @Test
    fun monthSelectableDays_dropTodayWhenTheClockHasPassedTheLastSlot() {
        // The same rule the strip applies, asserted on the grid: 23:10 is past
        // every published slot, so today is gone but tomorrow is not.
        val now = at(hour = 23, minute = 10, day = 15)

        val open = monthSelectableDays(
            year = 2026,
            month = Calendar.AUGUST,
            timeSlots = DefaultTimeSlots,
            nowMs = now,
        )

        assertFalse(15 in open)
        assertTrue(16 in open)
    }

    @Test
    fun firstOpenMonth_stepsForwardPastAMonthWithNothingInIt() {
        // An artist with nothing left in August (23:10 on the 31st) must open the
        // picker on September rather than on an empty grid.
        val now = at(hour = 23, minute = 10, day = 31)

        val opening = firstOpenMonth(timeSlots = DefaultTimeSlots, nowMs = now)

        assertEquals(2026, opening.year)
        assertEquals(Calendar.SEPTEMBER, opening.month)
        assertTrue(opening.selectableDays.isNotEmpty())
    }

    @Test
    fun firstOpenMonth_comesBackOnTheCurrentMonthWhenNothingIsEverOpen() {
        // A weekday the calendar does not have. The horizon runs out and the
        // current month comes back empty — true, and still steppable — rather
        // than the search running away.
        val now = at(hour = 9, minute = 0, day = 15)

        val opening = firstOpenMonth(daysAvailable = listOf("Blursday"), nowMs = now)

        assertEquals(Calendar.AUGUST, opening.month)
        assertTrue(opening.selectableDays.isEmpty())
    }

    @Test
    fun steppedMonth_carriesTheYearBoundaryInBothDirections() {
        assertEquals(2027 to Calendar.JANUARY, steppedMonth(2026, Calendar.DECEMBER, 1))
        assertEquals(2025 to Calendar.DECEMBER, steppedMonth(2026, Calendar.JANUARY, -1))
    }

    @Test
    fun dayOfMonthIfIn_onlyAnswersForTheMonthItWasAskedAbout() {
        val epoch = funnelDayEpochMs(2026, Calendar.OCTOBER, 12)

        assertEquals(12, dayOfMonthIfIn(epoch, 2026, Calendar.OCTOBER))
        assertNull(dayOfMonthIfIn(epoch, 2026, Calendar.NOVEMBER))
        assertNull(dayOfMonthIfIn(epoch, 2027, Calendar.OCTOBER))
        assertNull("nothing picked yet", dayOfMonthIfIn(0L, 2026, Calendar.OCTOBER))
    }

    @Test
    fun isAfterCurrentMonth_isTheCalendarsBackwardsFloor() {
        val now = at(hour = 9, minute = 0, day = 15)

        assertFalse(isAfterCurrentMonth(2026, Calendar.AUGUST, now))
        assertFalse(isAfterCurrentMonth(2026, Calendar.JULY, now))
        assertTrue(isAfterCurrentMonth(2026, Calendar.SEPTEMBER, now))
        assertTrue(isAfterCurrentMonth(2027, Calendar.JANUARY, now))
    }

    // ── the dock's summary line ─────────────────────────────────────────────

    @Test
    fun bookingSummaryLine_joinsWhatIsDecidedAndDropsWhatIsNot() {
        assertEquals(
            "Sat 12 Oct · 8:00 PM · 90 min",
            bookingSummaryLine("Sat 12 Oct", "8:00 PM", "90 min"),
        )
        assertEquals("Sat 12 Oct · 8:00 PM", bookingSummaryLine("Sat 12 Oct", "8:00 PM", ""))
    }

    @Test
    fun bookingSummaryLine_instructsWhenNothingIsDecidedYet() {
        // A dock with a live price and no words above it invites the tap the
        // disabled CTA is about to refuse.
        assertEquals("Pick a date to continue", bookingSummaryLine("", "", "  "))
    }
}
