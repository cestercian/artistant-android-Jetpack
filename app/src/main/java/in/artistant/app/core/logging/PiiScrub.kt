package `in`.artistant.app.core.logging

/**
 * Pure PII redaction for crash/telemetry payloads (port of iOS
 * `SentryConfig.scrub`). Distinct from `domain.chat.Redaction` — that one
 * collapses every contact leak into a single "[hidden until booking confirmed]"
 * token for the anti-leakage moat; THIS one uses LABELLED tokens
 * (`[REDACTED:EMAIL]` / `[REDACTED:URL]` / `[REDACTED:PHONE]`) so the operator can
 * SEE in the Sentry dashboard that a redaction happened without exposing the raw
 * value. It's the SDK-independent, unit-tested core the `SentryCrash` beforeSend
 * hook runs over every outgoing message/exception string.
 *
 * Order matters (same rationale as iOS): emails first (a local-part can hold digit
 * runs we don't want the phone pass to eat), then contact-leak URLs (a URL can
 * embed a phone, e.g. `wa.me/919876543210` — flag the whole URL, don't split it),
 * then phones most-specific-first. Short numbers (prices, times) have < 10 digits
 * and no phone/email/URL shape, so benign text is left untouched.
 */
object PiiScrub {

    fun scrub(input: String): String {
        var out = input
        // (1) Emails.
        out = EMAIL.replace(out, "[REDACTED:EMAIL]")
        // (2) Contact-leak URLs (with or without scheme / www.).
        out = URL.replace(out, "[REDACTED:URL]")
        // (3) Phones — +91 contiguous, then 5-5 grouped, then bare 10+ digit run.
        out = PHONE_IN.replace(out, "[REDACTED:PHONE]")
        out = PHONE_GROUPED.replace(out, "[REDACTED:PHONE]")
        out = PHONE_BARE.replace(out, "[REDACTED:PHONE]")
        return out
    }

    private val EMAIL = Regex("""[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}""")
    private val URL = Regex(
        """(https?://)?(www\.)?(wa\.me|t\.me|instagram\.com|whatsapp\.com|facebook\.com|fb\.com|youtube\.com|youtu\.be|calendly\.com|paytm\.me|phonepe\.com|linkedin\.com|x\.com|twitter\.com|discord\.gg|signal\.me|telegram\.me)[\w./?=&%\-]*""",
        RegexOption.IGNORE_CASE,
    )
    private val PHONE_IN = Regex("""\+?91[\s\-]?\d{10}""")
    private val PHONE_GROUPED = Regex("""\d{5}[\s\-]?\d{5}""")
    private val PHONE_BARE = Regex("""\+?\d{10,}""")
}
