package `in`.artistant.app.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * Month stepping behind the calendar grid's prev/next chevrons.
 *
 * Reported symptom: the Bookings (client) and Gigs (artist) grids rendered the
 * CURRENT month with no way to leave it. A booking in any other month appeared
 * in the list below but its month could never be shown on the grid — and since
 * tapping a grid day filters the list to that day, a December booking was
 * unreachable from the calendar until January.
 *
 * Stepping is pure arithmetic on (year, 0-based month) so it can be tested
 * without a Compose host. The case that breaks naive implementations is the year
 * boundary in BOTH directions: `month + 1` past December and `month - 1` before
 * January, plus multi-month steps that cross more than one boundary.
 */
class CalendarMonthStepTest {

    @Test
    fun `stepping inside a year just moves the month`() {
        assertEquals(CalendarMonth(2026, Calendar.AUGUST), CalendarMonth(2026, Calendar.JULY).stepped(1))
        assertEquals(CalendarMonth(2026, Calendar.JUNE), CalendarMonth(2026, Calendar.JULY).stepped(-1))
    }

    @Test
    fun `stepping forward off December rolls into the next January`() {
        assertEquals(
            CalendarMonth(2027, Calendar.JANUARY),
            CalendarMonth(2026, Calendar.DECEMBER).stepped(1),
        )
    }

    @Test
    fun `stepping back off January rolls into the previous December`() {
        assertEquals(
            CalendarMonth(2025, Calendar.DECEMBER),
            CalendarMonth(2026, Calendar.JANUARY).stepped(-1),
        )
    }

    @Test
    fun `a multi-month step crosses as many year boundaries as it needs`() {
        assertEquals(
            CalendarMonth(2025, Calendar.NOVEMBER),
            CalendarMonth(2026, Calendar.FEBRUARY).stepped(-3),
        )
        assertEquals(
            CalendarMonth(2028, Calendar.JANUARY),
            CalendarMonth(2026, Calendar.NOVEMBER).stepped(14),
        )
        assertEquals(
            CalendarMonth(2024, Calendar.MARCH),
            CalendarMonth(2026, Calendar.MARCH).stepped(-24),
        )
    }

    @Test
    fun `a zero step is the identity`() {
        assertEquals(CalendarMonth(2026, Calendar.MARCH), CalendarMonth(2026, Calendar.MARCH).stepped(0))
    }

    @Test
    fun `forward then back returns to the same month across every boundary`() {
        // Walks two whole years a month at a time so a boundary can't be skipped.
        var m = CalendarMonth(2025, Calendar.JANUARY)
        repeat(24) {
            assertEquals(m, m.stepped(1).stepped(-1))
            assertEquals(m, m.stepped(-1).stepped(1))
            m = m.stepped(1)
        }
        assertEquals(CalendarMonth(2027, Calendar.JANUARY), m)
    }

    @Test
    fun `the month is never outside 0 to 11 after a step`() {
        // The 0-based month feeds Calendar.set() and MonthDayGrid directly; a 12
        // would silently roll the grid a year off instead of failing loudly.
        var m = CalendarMonth(2026, Calendar.JANUARY)
        repeat(30) {
            m = m.stepped(1)
            assertEquals(true, m.month in 0..11)
        }
    }

    @Test
    fun `stepping is not clamped to today`() {
        // iOS MonthCalendarView doesn't clamp either — past gigs and far-future
        // holds are both legitimate destinations.
        assertEquals(CalendarMonth(2019, Calendar.MAY), CalendarMonth(2026, Calendar.MAY).stepped(-84))
        assertEquals(CalendarMonth(2033, Calendar.MAY), CalendarMonth(2026, Calendar.MAY).stepped(84))
    }

    @Test
    fun `the stepped month drives the grid header label`() {
        // firstDayEpochMs is what the header formats, so the chevrons and the
        // title can't disagree about which month the grid is showing.
        val december = CalendarMonth(2026, Calendar.NOVEMBER).stepped(1)
        assertEquals("December 2026", monthLabelFromEpoch(december.firstDayEpochMs))
        assertEquals("January 2027", monthLabelFromEpoch(december.stepped(1).firstDayEpochMs))
    }

    @Test
    fun `the cold-start month is the one containing now`() {
        val nov2026 = Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.NOVEMBER, 17)
        }.timeInMillis
        assertEquals(CalendarMonth(2026, Calendar.NOVEMBER), currentCalendarMonth(nov2026))
    }
}
