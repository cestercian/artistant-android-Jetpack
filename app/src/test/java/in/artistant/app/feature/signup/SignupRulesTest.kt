package `in`.artistant.app.feature.signup

import `in`.artistant.app.data.model.HandleRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure rules behind the "Getting started" screens: the phone number the code is texted to,
 * the resend policy the code screen renders, and the alternatives a taken handle offers.
 *
 * Every one of these is a sentence the design puts on screen — "Sent to +91 98450 12345 by
 * SMS", "Resend in 0:24", four suggestion chips — so each is asserted as the string a user
 * would read, not just as the value behind it.
 */
class SignupRulesTest {

    // ── PhoneRules ────────────────────────────────────────────────────────────

    @Test
    fun `a plain ten-digit mobile is valid and becomes E164`() {
        assertTrue(PhoneRules.isValid("9845012345"))
        assertEquals("+919845012345", PhoneRules.toE164("9845012345"))
    }

    @Test
    fun `separators, the country code and a trunk zero are all stripped`() {
        // Everything a person might paste out of a contact card or an SMS.
        listOf(
            "98450 12345",
            "98450-12345",
            "+91 98450 12345",
            "+919845012345",
            "919845012345",
            "09845012345",
        ).forEach { raw ->
            assertEquals("input was $raw", "9845012345", PhoneRules.national(raw))
            assertEquals("input was $raw", "+919845012345", PhoneRules.toE164(raw))
        }
    }

    @Test
    fun `a number that is not an Indian mobile is rejected rather than sent`() {
        // Too short, too long, and a landline-style leading digit: GoTrue would take the send
        // and Twilio would silently drop it, so the guard is on this side.
        assertFalse(PhoneRules.isValid("98450123"))
        assertFalse(PhoneRules.isValid("12345"))
        assertFalse(PhoneRules.isValid("5845012345"))
        assertFalse(PhoneRules.isValid(""))
        // An invalid number produces no E.164 at all — never a half-formed one.
        assertEquals("", PhoneRules.toE164("98450123"))
    }

    @Test
    fun `display groups the number the way the code screen prints it`() {
        assertEquals("+91 98450 12345", PhoneRules.display("+919845012345"))
        assertEquals("+91 98450 12345", PhoneRules.display("9845012345"))
        // A half-typed number still renders as itself rather than vanishing — the field shows
        // it back while the user is still typing.
        assertEquals("+91 98450", PhoneRules.display("98450"))
        assertEquals("", PhoneRules.display(""))
    }

    // ── OtpResend ─────────────────────────────────────────────────────────────

    @Test
    fun `the cooldown label counts down and then becomes the action`() {
        assertEquals("Resend in 0:30", OtpResend.label(OtpResend.COOLDOWN_SECONDS))
        assertEquals("Resend in 0:24", OtpResend.label(24))
        assertEquals("Resend in 0:09", OtpResend.label(9))
        assertEquals("Resend code", OtpResend.label(0))
    }

    @Test
    fun `a negative countdown can never render as a negative clock`() {
        // The only thing between this and a timer nobody cancelled is the floor.
        assertEquals("Resend code", OtpResend.label(-1))
        assertTrue(OtpResend.canResend(-5))
    }

    @Test
    fun `resend is blocked for the whole cooldown and free at zero`() {
        assertFalse(OtpResend.canResend(OtpResend.COOLDOWN_SECONDS))
        assertFalse(OtpResend.canResend(1))
        assertTrue(OtpResend.canResend(0))
    }

    @Test
    fun `the email escape appears on the second send, not the first`() {
        // The code screen promises this in so many words: "after two failed sends we offer
        // email sign-in instead."
        assertFalse(OtpResend.offersEmailEscape(0))
        assertFalse(OtpResend.offersEmailEscape(1))
        assertTrue(OtpResend.offersEmailEscape(2))
        assertTrue(OtpResend.offersEmailEscape(3))
    }

    @Test
    fun `verify unlocks only on six digits`() {
        assertFalse(OtpResend.isComplete(""))
        assertFalse(OtpResend.isComplete("47291"))
        assertTrue(OtpResend.isComplete("472913"))
        // Length alone is not enough — a pasted "4729-1" is six characters and not a code.
        assertFalse(OtpResend.isComplete("4729-1"))
    }

    // ── HandleSuggestions ─────────────────────────────────────────────────────

    @Test
    fun `a taken handle offers four alternatives, none of them the original`() {
        val suggestions = HandleSuggestions.alternatives("tilt", city = "Bengaluru")
        assertEquals(4, suggestions.size)
        assertFalse(suggestions.contains("tilt"))
        assertEquals(suggestions.size, suggestions.distinct().size)
    }

    @Test
    fun `the city tag is used when we know the city and skipped when we do not`() {
        assertTrue(HandleSuggestions.alternatives("tilt", city = "Bengaluru").contains("tilt_blr"))
        assertTrue(HandleSuggestions.alternatives("tilt", city = "Mumbai").contains("tilt_mum"))
        // An unmapped or absent city contributes nothing rather than a placeholder tag.
        assertFalse(HandleSuggestions.alternatives("tilt", city = "Reykjavik").any { it.contains("_") })
        assertFalse(HandleSuggestions.alternatives("tilt", city = null).any { it.contains("_") })
    }

    @Test
    fun `every suggestion is something the handle field would accept`() {
        // A long base name can push "thetiltcollectiveco" past the 24-character ceiling; those
        // candidates are dropped rather than offered and then rejected by the same screen.
        listOf("tilt", "a_very_long_stage_name", "xyz", "rheamenon").forEach { base ->
            HandleSuggestions.alternatives(base, city = "Bengaluru").forEach { suggestion ->
                assertTrue(
                    "$suggestion (from $base) is not a valid handle",
                    HandleRules.isValidFormat(suggestion),
                )
            }
        }
    }

    @Test
    fun `an empty or unusable handle offers nothing rather than junk`() {
        assertTrue(HandleSuggestions.alternatives("").isEmpty())
        assertTrue(HandleSuggestions.alternatives("   ").isEmpty())
    }
}
