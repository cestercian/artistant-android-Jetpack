package `in`.artistant.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionTest {

    private val hidden = Redaction.PLACEHOLDER

    @Test
    fun `strips email`() {
        assertEquals("reach me at $hidden ok", Redaction.redact("reach me at gig@band.com ok"))
    }

    @Test
    fun `strips indian phone with and without prefix`() {
        assertEquals("call $hidden", Redaction.redact("call +91 98765 43210"))
        assertEquals("call $hidden", Redaction.redact("call 9876543210"))
    }

    @Test
    fun `strips instagram handle`() {
        assertEquals("dm $hidden now", Redaction.redact("dm @cool.band now"))
    }

    @Test
    fun `strips social url`() {
        assertEquals("here $hidden", Redaction.redact("here https://instagram.com/cool.band"))
    }

    @Test
    fun `clean text untouched`() {
        val clean = "Looking forward to the show on Saturday!"
        assertEquals(clean, Redaction.redact(clean))
        assertFalse(Redaction.shouldRedact(clean))
    }

    @Test
    fun `shouldRedact flags contact info`() {
        assertTrue(Redaction.shouldRedact("gig@band.com"))
        assertTrue(Redaction.shouldRedact("9876543210"))
        assertTrue(Redaction.shouldRedact("@handle"))
    }
}
