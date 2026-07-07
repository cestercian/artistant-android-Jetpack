package `in`.artistant.app.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class InrTest {

    @Test
    fun `full grouping uses en_IN lakh commas`() {
        assertEquals("₹1,00,000", formatInr(100_000))
        assertEquals("₹5,000", formatInr(5_000))
        assertEquals("₹500", formatInr(500))
    }

    @Test
    fun `short form uses K and L`() {
        assertEquals("₹500", formatInrShort(500))
        assertEquals("₹5K", formatInrShort(5_000))
        assertEquals("₹1.5L", formatInrShort(150_000))
        assertEquals("₹2L", formatInrShort(200_000))
        assertEquals("₹12K", formatInrShort(12_000))
    }
}
