package `in`.artistant.app.platform.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known-answer + property checks for the nonce dance. A regression here silently breaks every
 * OAuth-ID-token sign-in (token-claim mismatch, no crash), so pin the hash and the charset.
 */
class AuthNonceTest {

    @Test
    fun `sha256 matches the canonical empty-string and abc vectors`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            AuthNonce.sha256Hex(""),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AuthNonce.sha256Hex("abc"),
        )
    }

    @Test
    fun `random nonce has the requested length and allowed charset only`() {
        val allowed = ("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
            "abcdefghijklmnopqrstuvwxyz-._").toSet()
        repeat(50) {
            val n = AuthNonce.random(32)
            assertEquals(32, n.length)
            assertTrue(n.all { it in allowed })
        }
    }

    @Test
    fun `two random nonces differ`() {
        assertNotEquals(AuthNonce.random(), AuthNonce.random())
    }
}
