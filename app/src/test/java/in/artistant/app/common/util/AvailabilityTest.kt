package `in`.artistant.app.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class AvailabilityTest {

    // 2024-01-01 is a Monday — a fixed "today" so the pure fn never reads the clock.
    private val monday = LocalDate.of(2024, 1, 1)

    @Test
    fun `today's weekday in the set reads AVAILABLE TODAY`() {
        assertEquals("AVAILABLE TODAY", availabilityKicker(listOf("Mon"), monday))
    }

    @Test
    fun `a later weekday reads AVAILABLE with the uppercased abbreviation`() {
        assertEquals("AVAILABLE SAT", availabilityKicker(listOf("Sat"), monday))
        assertEquals("AVAILABLE SUN", availabilityKicker(listOf("Sun"), monday))
    }

    @Test
    fun `the soonest matching day wins`() {
        // Wed (offset 2) beats Fri (offset 4).
        assertEquals("AVAILABLE WED", availabilityKicker(listOf("Fri", "Wed"), monday))
    }

    @Test
    fun `an empty set yields null so callers show no pill`() {
        assertNull(availabilityKicker(emptyList(), monday))
    }
}
