package `in`.artistant.app.core.result

/**
 * Sealed error hierarchy the repositories map PostgREST failures into, so the
 * UI branches on a typed cause instead of string-matching. Mirrors the iOS
 * per-repo error enums (`.notFoundOrUnauthorized`, `.handleTaken`, …).
 */
sealed class AppError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** PGRST116 — row not found OR blocked by RLS (indistinguishable by design). */
    data object NotFoundOrUnauthorized : AppError("Not found or not permitted.")

    /** 23505 — unique constraint (e.g. handle taken, duplicate position). */
    data object UniqueViolation : AppError("That value is already taken.")

    /** insufficient_privilege — a guarded-column write was rejected. */
    data object GuardedWrite : AppError("That change isn't allowed.")

    /** Anything we didn't classify — keep the real cause for logging. */
    class Unknown(cause: Throwable) : AppError(cause.message ?: "Something went wrong.", cause)
}

/**
 * Map a raw PostgREST/network error into an [AppError] by sniffing the code in
 * its message. supabase-kt surfaces the PGRST/SQLSTATE code in the exception
 * text, so a substring check is enough and avoids coupling to its exception
 * types. ponytail: string-sniff is sufficient; switch to typed
 * RestException.code if supabase-kt exposes it cleanly later.
 */
fun mapPostgrest(t: Throwable): AppError {
    val msg = t.message.orEmpty()
    return when {
        "PGRST116" in msg -> AppError.NotFoundOrUnauthorized
        "23505" in msg -> AppError.UniqueViolation
        "insufficient_privilege" in msg -> AppError.GuardedWrite
        else -> AppError.Unknown(t)
    }
}
