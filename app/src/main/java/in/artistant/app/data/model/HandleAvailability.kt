package `in`.artistant.app.data.model

/**
 * Result of the `handle_is_available` RPC (port of iOS `HandleAvailabilityResult`).
 *
 * [Failure] is DISTINCT from `Unavailable` on purpose: a network/auth blip must be
 * treated as "unknown — let the upsert decide" rather than blocking the user, so the
 * caller re-enables Continue on failure (the iOS `handleIsAvailable` computed-var
 * behaviour). The `users_handle_key` unique constraint is the real backstop.
 */
sealed interface HandleAvailability {
    data object Available : HandleAvailability
    data object Unavailable : HandleAvailability
    data class Failure(val message: String) : HandleAvailability
}

/** The handle format contract, shared by validation + the availability check.
 *  Mirrors the iOS regex `^[a-z0-9_]{3,24}$` (lowercase alnum + underscore, 3–24). */
object HandleRules {
    private val REGEX = Regex("^[a-z0-9_]{3,24}$")

    /** Lowercase + trim, matching what we send to Postgres. */
    fun normalize(raw: String): String = raw.trim().lowercase()

    /** Format-only check (no availability round-trip). Normalizes first. */
    fun isValidFormat(raw: String): Boolean = REGEX.matches(normalize(raw))
}

/** Minimal client-side email validation for the email-auth sheet. Not RFC-exhaustive —
 *  just enough to catch obvious typos before a round-trip (GoTrue is the real validator). */
object EmailRules {
    // one @, at least one dot in the domain, no spaces — the pragmatic 99% check.
    private val REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun isValid(raw: String): Boolean = REGEX.matches(raw.trim())
}

/** GoTrue's minimum password length. Shared by the sign-UP and sign-IN guards so the two
 *  can't drift — parity: iOS EmailAuthView gates submit on `passwordValid` (>=6) for BOTH modes. */
object PasswordRules {
    const val MIN_LENGTH = 6
    fun isValid(raw: String): Boolean = raw.length >= MIN_LENGTH
}
