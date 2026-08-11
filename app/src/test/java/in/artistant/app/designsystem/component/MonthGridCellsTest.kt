package `in`.artistant.app.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The month grid's adjacent-month fill.
 *
 * The grid draws complete Monday-first weeks, which means the first and last
 * rows borrow days from the neighbouring months. That arithmetic is where the
 * bugs live — a month starting on Monday needs no leading fill at all, a month
 * starting on Sunday needs six, and both boundaries cross a year end — so it is
 * a pure function over (year, month) rather than something only a screenshot
 * could catch.
 *
 * Months are `Calendar.MONTH`, i.e. 0-based: 0 = January, 11 = December.
 */
class MonthGridCellsTest {

    @Test
    fun `every row has seven cells`() {
        for (m in 0..11) {
            monthGridCells(2026, m).forEach { week ->
                assertEquals("month $m", 7, week.size)
            }
        }
    }

    @Test
    fun `a month starting on a Monday needs no leading fill`() {
        // 1 June 2026 is a Monday.
        assertEquals(1, monthGridCells(2026, 5).first().first())
    }

    @Test
    fun `a month starting on a Sunday borrows six leading days`() {
        // 1 February 2026 is a Sunday, so the row runs 26..31 January, then 1 Feb.
        val first = monthGridCells(2026, 1).first()
        assertEquals(listOf(26, 27, 28, 29, 30, 31, 1), first)
    }

    @Test
    fun `January borrows from the previous December`() {
        // 1 January 2026 is a Thursday: three leading cells, and December has 31
        // days, so they must be 29, 30, 31 — not 28, 29, 30 off a 30-day month.
        val first = monthGridCells(2026, 0).first()
        assertEquals(listOf(29, 30, 31, 1, 2, 3), first.take(6))
    }

    @Test
    fun `February borrows from a 31-day January`() {
        // The leading fill must count back from JANUARY's length, not from the
        // month being drawn — the classic version of this bug reads 28 days.
        assertEquals(26, monthGridCells(2026, 1).first().first())
    }

    @Test
    fun `December's trailing fill counts up from the first of January`() {
        val last = monthGridCells(2026, 11).last()
        // 31 December 2026 is a Thursday, so the final row trails into January.
        assertEquals(31, last[3])
        assertEquals(listOf(1, 2, 3), last.drop(4))
    }

    @Test
    fun `a leap February is fully covered`() {
        val days = monthGridCells(2028, 1).flatten()
        assertTrue("29 Feb 2028 must appear", days.contains(29))
    }

    @Test
    fun `the grid never runs past six weeks`() {
        for (y in 2026..2030) {
            for (m in 0..11) {
                val weeks = monthGridCells(y, m).size
                assertTrue("$y/$m had $weeks weeks", weeks in 4..6)
            }
        }
    }
}
