package `in`.artistant.app.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The five day states screen 78 documents, and the precedence between them.
 *
 * The screen exists precisely so Bookings and Gigs cannot drift apart on this,
 * and the fill rule is the half of it that can be pinned without a Compose
 * runtime. TODAY is deliberately absent from [MonthDayFill] — it is a ring over
 * whatever fill the day already has — so what is asserted here is the fill
 * ladder only.
 */
class MonthDayFillTest {

    @Test
    fun `a day with nothing on it is open`() {
        assertEquals(
            MonthDayFill.Open,
            monthDayFill(booked = false, unavailable = false, selected = false),
        )
    }

    @Test
    fun `a gig fills the day with the accent`() {
        assertEquals(
            MonthDayFill.Booked,
            monthDayFill(booked = true, unavailable = false, selected = false),
        )
    }

    @Test
    fun `a blocked-out day fills grey`() {
        assertEquals(
            MonthDayFill.Unavailable,
            monthDayFill(booked = false, unavailable = true, selected = false),
        )
    }

    @Test
    fun `a booking outranks an availability block on the same day`() {
        // An artist who blocked a date and then accepted a gig on it HAS a gig on
        // it. Rendering that day as merely unavailable would hide the one thing
        // the calendar exists to show.
        assertEquals(
            MonthDayFill.Booked,
            monthDayFill(booked = true, unavailable = true, selected = false),
        )
    }

    @Test
    fun `the tapped day always wins`() {
        // Selection has to be visible whatever is under it, or the grid stops
        // answering the user's taps.
        listOf(
            monthDayFill(booked = true, unavailable = false, selected = true),
            monthDayFill(booked = false, unavailable = true, selected = true),
            monthDayFill(booked = true, unavailable = true, selected = true),
            monthDayFill(booked = false, unavailable = false, selected = true),
        ).forEach { assertEquals(MonthDayFill.Selected, it) }
    }

    // --- the schedule row's clock -------------------------------------------

    @Test
    fun `a clock label splits into the time and its meridiem`() {
        assertEquals("8:00" to "pm", splitClockLabel("8:00 PM"))
        assertEquals("6:30" to "am", splitClockLabel(" 6:30 AM "))
    }

    @Test
    fun `an unreadable clock label stays on one line`() {
        // Stacking half of a label we cannot parse is worse than printing all of
        // it, so anything that is not exactly two tokens comes back whole.
        assertEquals("TBD" to null, splitClockLabel("TBD"))
        assertEquals("20:00 hrs IST" to null, splitClockLabel("20:00 hrs IST"))
        assertEquals("" to null, splitClockLabel("   "))
    }

    // --- the grid's own arithmetic (unchanged, re-pinned here) ---------------

    @Test
    fun `a day outside the displayed month has no day-of-month on this grid`() {
        // The guard that keeps a booking in April off May's grid.
        assertNull(dayOfMonthInMonth("Sat, May 16, 2026", 2026, 3))
        assertEquals(16, dayOfMonthInMonth("Sat, May 16, 2026", 2026, 4))
    }
}
