package `in`.artistant.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Handle-regex + email validation parity with the iOS `^[a-z0-9_]{3,24}$` + email checks. */
class ValidationTest {

    @Test
    fun `handle accepts lowercase alnum and underscore, 3 to 24`() {
        assertTrue(HandleRules.isValidFormat("yash"))
        assertTrue(HandleRules.isValidFormat("a_1"))          // min length 3
        assertTrue(HandleRules.isValidFormat("dj_snake_2024"))
        assertTrue(HandleRules.isValidFormat("a".repeat(24))) // max length 24
    }

    @Test
    fun `handle normalizes case and whitespace before checking`() {
        assertTrue(HandleRules.isValidFormat("  Yash  "))     // trims + lowercases
        assertTrue(HandleRules.isValidFormat("YASH_01"))
    }

    @Test
    fun `handle rejects too short, too long, and bad chars`() {
        assertFalse(HandleRules.isValidFormat("ab"))          // < 3
        assertFalse(HandleRules.isValidFormat("a".repeat(25)))// > 24
        assertFalse(HandleRules.isValidFormat("has space"))
        assertFalse(HandleRules.isValidFormat("dash-not-ok"))
        assertFalse(HandleRules.isValidFormat("emoji😀"))
        assertFalse(HandleRules.isValidFormat(""))
    }

    @Test
    fun `email accepts normal addresses`() {
        assertTrue(EmailRules.isValid("a@b.co"))
        assertTrue(EmailRules.isValid("yash.faid+tag@gmail.com"))
        assertTrue(EmailRules.isValid("  trimmed@example.com  "))
    }

    @Test
    fun `email rejects obvious garbage`() {
        assertFalse(EmailRules.isValid("no-at-sign.com"))
        assertFalse(EmailRules.isValid("no@dot"))
        assertFalse(EmailRules.isValid("two@@at.com"))
        assertFalse(EmailRules.isValid("has space@x.com"))
        assertFalse(EmailRules.isValid(""))
    }

    // The shared >=6 password rule that BOTH the email sign-UP and sign-IN guards enforce
    // (parity: iOS EmailAuthView passwordValid). A 5-char password must be rejected so sign-in
    // no longer fires a doomed GoTrue request.
    @Test
    fun `password rejects fewer than 6 chars`() {
        assertFalse(PasswordRules.isValid("12345"))   // 5 chars — the sign-in guard case
        assertFalse(PasswordRules.isValid(""))
    }

    @Test
    fun `password accepts 6 or more chars`() {
        assertTrue(PasswordRules.isValid("123456"))
        assertTrue(PasswordRules.isValid("a longer passphrase"))
    }
}
