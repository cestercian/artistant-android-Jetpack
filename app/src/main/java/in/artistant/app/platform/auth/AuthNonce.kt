package `in`.artistant.app.platform.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The nonce dance both OAuth-ID-token flows need (port of iOS `AppleSignInController`
 * randomNonce/sha256, and reused for Google). A fresh random nonce is generated per
 * sign-in; its SHA-256 is what the provider signs into the ID token, and the RAW nonce
 * is sent to Supabase, which verifies the hash against the token's claim.
 *
 * A regression here silently breaks sign-in (token-claim mismatch, no crash), so both
 * helpers are pinned by a known-answer test.
 */
object AuthNonce {
    // Apple's recommended charset (unreserved URL chars). ≥32 chars recommended.
    private const val CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-._"
    private val random = SecureRandom()

    /** Cryptographically-random nonce of [length] chars from [CHARSET]. */
    fun random(length: Int = 32): String {
        require(length > 0)
        val sb = StringBuilder(length)
        while (sb.length < length) {
            val b = ByteArray(1).also { random.nextBytes(it) }[0].toInt() and 0xFF
            if (b < CHARSET.length) sb.append(CHARSET[b]) // rejection-sample to avoid modulo bias
        }
        return sb.toString()
    }

    /** Lowercase-hex SHA-256 of [input] (what the provider hashes into the ID token). */
    fun sha256Hex(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
