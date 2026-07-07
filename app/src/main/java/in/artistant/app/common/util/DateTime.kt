package `in`.artistant.app.common.util

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Supabase timestamps are ISO-8601 (`2026-07-07T12:34:56.789Z` or without the
 * fractional seconds). `java.time.Instant` parses both RFC-3339 forms, so we
 * lean on the stdlib rather than hand-rolling a formatter.
 * ponytail: stdlib Instant covers parse+format; add a custom formatter only if
 * the backend ever emits an offset form Instant can't take.
 */
object SupabaseISO8601 {
    /** Null on unparseable input rather than throwing — callers branch on it. */
    fun parse(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    /** Instant → ISO-8601 UTC string (what the backend expects on write). */
    fun format(instant: Instant): String = instant.toString()
}
