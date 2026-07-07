package `in`.artistant.app.domain.chat

/**
 * Anti-leakage chat redaction (the moat). The server trigger
 * `tg_messages_redact` does the authoritative redaction on insert; this is the
 * client-side mirror used for previews / optimistic bubbles before a booking is
 * confirmed. Ported to the brief's spec.
 *
 * Order matters: URLs first (so a social URL isn't half-eaten by the handle or
 * email rule), then emails, then phones, then bare @handles. Each match becomes
 * the single placeholder token.
 */
object Redaction {
    const val PLACEHOLDER = "[hidden until booking confirmed]"

    // Social / any http(s) URL — greedy enough to swallow the whole link.
    private val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
    // Emails.
    private val emailRegex = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    // Indian phone numbers: optional +91 / 0 prefix, then a 10-digit number,
    // tolerating spaces or hyphens between groups.
    private val phoneRegex = Regex("""(?:\+91[\-\s]?|0)?[6-9]\d(?:[\-\s]?\d){8}""")
    // Bare @handles (Instagram/etc.) — must start at a word boundary so it
    // doesn't fire inside an already-handled email local-part.
    private val handleRegex = Regex("""(?<![\w@])@[A-Za-z0-9_.]{2,}""")

    fun redact(text: String): String =
        text
            .replace(urlRegex, PLACEHOLDER)
            .replace(emailRegex, PLACEHOLDER)
            .replace(phoneRegex, PLACEHOLDER)
            .replace(handleRegex, PLACEHOLDER)

    /** True if [text] contains any contact info the redactor would strip. */
    fun shouldRedact(text: String): Boolean =
        urlRegex.containsMatchIn(text) ||
            emailRegex.containsMatchIn(text) ||
            phoneRegex.containsMatchIn(text) ||
            handleRegex.containsMatchIn(text)
}
