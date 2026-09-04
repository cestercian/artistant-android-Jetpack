package `in`.artistant.app.feature.profile

import `in`.artistant.app.data.repository.AvailabilityDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lines the account surfaces print (design 26 / 47 / 69 / 128).
 *
 * All of it is one rule wearing different clothes: a fact we READ is stated, and a fact we
 * could not read produces a shorter sentence rather than a confident wrong one. The em dash in
 * the stat band is the loud version; these are the quiet ones — a row that drops its subtitle,
 * a summary that stays null.
 */
class AccountCopyTest {

    // ── The calendar row (47 / 69) ────────────────────────────────────────────────────

    @Test
    fun `the calendar row says where it is writing once it is on`() {
        assertEquals(
            "Writing to Work",
            calendarSubtitle(enabled = true, hasPermission = true, calendarTitle = "Work"),
        )
    }

    @Test
    fun `a missing permission is a different sentence from a switch that is simply off`() {
        // Three states, and only one of them is fixable by the switch alone. Collapsing the
        // first two is how a user taps a toggle that appears to do nothing.
        val noPermission = calendarSubtitle(enabled = false, hasPermission = false, calendarTitle = "Artistant")
        val justOff = calendarSubtitle(enabled = false, hasPermission = true, calendarTitle = "Artistant")
        assertFalse(noPermission == justOff)
        assertTrue(noPermission.contains("tap to enable"))
    }

    // ── The artist availability row (47 / 69) ─────────────────────────────────────────

    @Test
    fun `a consecutive run collapses to a range, the way people say it`() {
        assertEquals(
            "Thu–Sun evenings",
            availabilitySummary(AvailabilityDraft(listOf("Thu", "Fri", "Sat", "Sun"), listOf("7 pm"))),
        )
    }

    @Test
    fun `two days stay a list rather than becoming a two-day range`() {
        assertEquals(
            "Fri, Sat evenings",
            availabilitySummary(AvailabilityDraft(listOf("Fri", "Sat"), listOf("8 pm"))),
        )
    }

    @Test
    fun `the range is found against the week, not against the list's own order`() {
        // The server does not promise an order, so "Sun, Thu, Sat, Fri" is the same schedule.
        assertEquals(
            "Thu–Sun evenings",
            availabilitySummary(AvailabilityDraft(listOf("Sun", "Thu", "Sat", "Fri"), listOf("7 pm"))),
        )
    }

    @Test
    fun `a gap in the week stays a list`() {
        assertEquals(
            "Mon, Wed, Fri evenings",
            availabilitySummary(AvailabilityDraft(listOf("Mon", "Wed", "Fri"), listOf("6 pm"))),
        )
    }

    @Test
    fun `the time of day is keyed on the EARLIEST start`() {
        // An act that can start at 6pm and at 9pm is an evening act; one that can start at
        // 11am is not, whatever else it can also do.
        assertEquals(
            "Mon–Fri mornings",
            availabilitySummary(
                AvailabilityDraft(listOf("Mon", "Tue", "Wed", "Thu", "Fri"), listOf("9 am", "7 pm")),
            ),
        )
        assertEquals("afternoons", timeOfDayLabel(listOf("2 pm")))
        assertEquals("evenings", timeOfDayLabel(listOf("5 pm")))
    }

    @Test
    fun `midnight and noon do not flip into the wrong half of the day`() {
        assertEquals("mornings", timeOfDayLabel(listOf("12 am")))
        assertEquals("afternoons", timeOfDayLabel(listOf("12 pm")))
    }

    @Test
    fun `unparseable times drop the phrase rather than guessing one`() {
        assertNull(timeOfDayLabel(listOf("whenever")))
        assertEquals(
            "Fri, Sat",
            availabilitySummary(AvailabilityDraft(listOf("Fri", "Sat"), listOf("whenever"))),
        )
    }

    @Test
    fun `an artist with no days set says so, and a failed read says nothing at all`() {
        // Two different facts. "Not set yet" is a real answer; null means the read failed and
        // the row simply shows one line instead of claiming an empty schedule.
        assertEquals("Not set yet", availabilitySummary(AvailabilityDraft(emptyList(), emptyList())))
        assertNull(availabilitySummary(null))
    }

    // ── Profile's rows and identity (26) ──────────────────────────────────────────────

    @Test
    fun `initials take at most two words and never come out empty`() {
        assertEquals("RM", initials("Rhea Menon"))
        assertEquals("Y", initials("You"))
        assertEquals("AB", initials("aarav  bhatt  iyer"))
        assertEquals("A", initials("   "))
        assertEquals("A", initials(""))
    }

    @Test
    fun `the bookings row states a count it has and drops the line when it does not`() {
        assertEquals("2 upcoming", bookingsRowSubtitle(2))
        assertEquals("1 upcoming", bookingsRowSubtitle(1))
        assertEquals("Nothing on right now", bookingsRowSubtitle(0))
        assertNull("an unread count must not become 'nothing on'", bookingsRowSubtitle(null))
    }

    @Test
    fun `the saved row always has an answer, because the set is local`() {
        assertEquals("12 acts", savedRowSubtitle(12))
        assertEquals("1 act", savedRowSubtitle(1))
        assertEquals("None saved yet", savedRowSubtitle(0))
    }

    @Test
    fun `the subscription row never asserts a plan while billing is dormant`() {
        // `subscriptionsEnabled` is a compile-time false, so nothing has checked an
        // entitlement. Saying "Free plan" there would be a claim about a state nobody read.
        assertEquals("Not available yet", ProfileUiState().subscriptionSubtitle)
    }

    // ── Devices (128) ─────────────────────────────────────────────────────────────────

    @Test
    fun `a device names itself without repeating its own brand`() {
        assertEquals("Google Pixel 8", deviceLabel("Google", "Pixel 8"))
        assertEquals("Samsung SM-G991B", deviceLabel("samsung", "SM-G991B"))
        assertEquals("Oneplus CPH2451", deviceLabel("oneplus", "CPH2451"))
        // The one case the join has to skip, or it reads "Xiaomi Xiaomi 14".
        assertEquals("Xiaomi 14", deviceLabel("Xiaomi", "Xiaomi 14"))
        assertEquals("xiaomi 14", deviceLabel("Xiaomi", "xiaomi 14"))
    }

    @Test
    fun `a device with nothing to say still gets a name`() {
        assertEquals("Google", deviceLabel("google", ""))
        assertEquals("SM-G991B", deviceLabel("", "SM-G991B"))
        assertEquals("This phone", deviceLabel("", ""))
    }

    // ── The stat band shares one rule with the profile header ────────────────────────

    @Test
    fun `the account band prints the same em dash the profile header does`() {
        assertEquals(profileStatValue(null), accountStatValue(null))
        assertEquals(profileStatValue(86), accountStatValue(86))
        assertEquals("—", accountStatValue(null))
    }
}
