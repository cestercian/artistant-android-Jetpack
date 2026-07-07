package `in`.artistant.app.domain.booking

import org.junit.Assert.assertEquals
import org.junit.Test

class BookingMathTest {

    @Test
    fun `10000 fee — 5 percent platform, 18 percent GST on fee plus platform`() {
        val c = BookingMath.compute(10_000)
        assertEquals(500, c.platform)          // 10000 * 0.05
        assertEquals(1_890, c.gst)             // (10000 + 500) * 0.18 = 1890
        assertEquals(12_390, c.total)          // 10000 + 500 + 1890
    }

    @Test
    fun `zero fee is all zeros`() {
        val c = BookingMath.compute(0)
        assertEquals(0, c.platform)
        assertEquals(0, c.gst)
        assertEquals(0, c.total)
    }

    @Test
    fun `rounding to nearest rupee`() {
        // fee=999 → platform=round(49.95)=50; gst=round((1049)*0.18)=round(188.82)=189
        val c = BookingMath.compute(999)
        assertEquals(50, c.platform)
        assertEquals(189, c.gst)
        assertEquals(1_238, c.total)
    }
}
