package `in`.artistant.app.feature.availability

import `in`.artistant.app.common.util.availabilityKicker
import `in`.artistant.app.feature.wizard.WizardWeekdays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * The "HOW CLIENTS SEE YOU" preview on ManageAvailability.
 *
 * The bug this pins (audit p12): the preview derived its own answer — the first
 * weekday in canonical Mon→Sun order, formatted "Open Mon" — while the badge
 * clients actually see comes from [availabilityKicker], which scans forward from
 * today. On a Saturday with `days = {Mon, Sat}` Discover rendered "AVAILABLE
 * TODAY" and this box rendered "Open Mon": wrong day and wrong copy, on the one
 * screen whose entire purpose is showing the artist that badge before they save.
 *
 * iOS shares the formatter for exactly this reason — `ManageAvailabilityView`'s
 * `previewLabel` is a one-line delegation to `WeekdayFormatting.availabilityKicker`,
 * with the file's own comment saying the preview must never lie.
 *
 * So the contract is delegation, not a second implementation: every case here is
 * asserted against the helper's output for the same inputs.
 */
class ManageAvailabilityPreviewTest {

    // 2024-01-06 is a Saturday — the failure scenario's "today".
    private val saturday = LocalDate.of(2024, 1, 6)

    // 2024-01-01 is a Monday, matching AvailabilityTest's fixture.
    private val monday = LocalDate.of(2024, 1, 1)

    private fun state(vararg days: String) = ManageAvailabilityUiState(days = days.toSet())

    /** The reported divergence, verbatim. */
    @Test
    fun `the soonest open day is today, not the first day in Mon-Sun order`() {
        val label = state("Mon", "Sat").previewLabel(saturday)

        assertEquals("AVAILABLE TODAY", label)
        assertEquals(availabilityKicker(listOf("Mon", "Sat"), saturday), label)
    }

    @Test
    fun `a later day in the week reads the same as the client-facing badge`() {
        assertEquals("AVAILABLE WED", state("Fri", "Wed").previewLabel(monday))
        assertEquals("AVAILABLE SAT", state("Sat").previewLabel(monday))
    }

    /** Canonical order and clock order agree here — the old code was right by luck. */
    @Test
    fun `Monday selected on a Monday still reads AVAILABLE TODAY`() {
        assertEquals("AVAILABLE TODAY", state("Mon").previewLabel(monday))
    }

    /**
     * Null is the no-badge branch: the screen shows "No badge yet — pick a day you
     * play" instead of a pill, matching Discover's no-pill-over-a-false-pill rule.
     */
    @Test
    fun `no days picked yields no badge`() {
        assertNull(ManageAvailabilityUiState().previewLabel(monday))
    }

    /**
     * A non-empty set always matches inside the 7-day window — the scan covers
     * every weekday name — so a picked day can never render as "no badge".
     */
    @Test
    fun `every single-day selection produces a badge whatever today is`() {
        for (offset in 0L until 7L) {
            val today = monday.plusDays(offset)
            for (day in WizardWeekdays) {
                val label = state(day).previewLabel(today)
                assertNotNull("day=$day today=$today", label)
                assertEquals(
                    "day=$day today=$today",
                    availabilityKicker(setOf(day), today),
                    label,
                )
            }
        }
    }
}
