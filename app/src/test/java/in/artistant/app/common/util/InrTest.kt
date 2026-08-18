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

    /**
     * Regression: the grouping reversed each 2-digit chunk AND the joined string,
     * so any leading pair that isn't a palindrome came out swapped — a ₹60,000
     * pricing tier rendered as "₹06,000" on the EPK (spotted on-device).
     *
     * Every case here has a NON-palindromic leading group, which is exactly what
     * the original tests lacked: 5_000 has a single-digit head and 100_000's head
     * ("100") survives the double reversal by coincidence, so both passed while
     * the formatter was broken for most real prices.
     */
    @Test
    fun `grouping does not swap the digits of the leading group`() {
        assertEquals("₹60,000", formatInr(60_000))
        assertEquals("₹51,000", formatInr(51_000))
        assertEquals("₹83,000", formatInr(83_000))
        assertEquals("₹12,34,567", formatInr(1_234_567))
        assertEquals("₹1,23,45,678", formatInr(12_345_678))
    }

    @Test
    fun `grouping handles the boundaries and negatives`() {
        assertEquals("₹999", formatInr(999))
        assertEquals("₹1,000", formatInr(1_000))
        assertEquals("₹10,000", formatInr(10_000))
        assertEquals("₹0", formatInr(0))
        assertEquals("₹-60,000", formatInr(-60_000))
    }

    @Test
    fun `short form uses K and L`() {
        assertEquals("₹500", formatInrShort(500))
        assertEquals("₹5K", formatInrShort(5_000))
        assertEquals("₹1.5L", formatInrShort(150_000))
        assertEquals("₹2L", formatInrShort(200_000))
        assertEquals("₹12K", formatInrShort(12_000))
    }

    /**
     * Regression (audit p14): the band was picked from the RAW amount, so ₹99,999
     * took the K branch and its 99.999 rounded up to "₹100K" — one lakh printed in
     * the band the lakh rung exists to express. The band now keys off the rounded
     * figure, so anything that rounds to 100 thousands promotes.
     */
    @Test
    fun `short form promotes a rounded 100K to the lakh band`() {
        assertEquals("₹1L", formatInrShort(99_999))
        assertEquals("₹1L", formatInrShort(99_950))
        // One rupee below the rounding boundary — still a K, and still one decimal.
        assertEquals("₹99.9K", formatInrShort(99_949))
        // Nothing else moves: the promotion is the rounding, not a lower threshold.
        assertEquals("₹95K", formatInrShort(95_000))
        assertEquals("₹1L", formatInrShort(100_000))
    }

    /**
     * The `else` branch used to print the raw Int, so a negative rendered ungrouped
     * ("₹-150000") while every other path grouped. Negatives delegate to [formatInr].
     * Sub-₹1,000 amounts stay exact rupees — they are never rounded into a "₹1K".
     */
    @Test
    fun `short form groups negatives and keeps small amounts exact`() {
        assertEquals("₹-1,50,000", formatInrShort(-150_000))
        assertEquals("₹-500", formatInrShort(-500))
        assertEquals("₹999", formatInrShort(999))
        assertEquals("₹0", formatInrShort(0))
    }
}
