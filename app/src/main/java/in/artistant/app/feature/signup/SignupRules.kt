package `in`.artistant.app.feature.signup

import `in`.artistant.app.data.model.HandleRules
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The pure rules the "Getting started" screens run on — phone formatting, the OTP resend
 * policy, and handle alternatives.
 *
 * All of it lives outside the ViewModels on purpose: every one of these is a decision the
 * design states in copy ("Resend in 0:24", "Sent to +91 98450 12345 by SMS", the four
 * suggestion chips on screen 90) and a decision stated in copy is a decision worth a test.
 * None of them touches Android or supabase-kt, so the tests are plain JVM.
 */

/**
 * Indian mobile numbers, which is the only country the product ships in (v1 is India-only,
 * INR, and the design's field is hard-labelled "IN +91").
 *
 * The app stores and sends E.164 — `+919845012345` — because that is what GoTrue's phone
 * provider takes and what Twilio delivers to. It DISPLAYS the grouped form, because a code
 * screen that says "Sent to +919845012345" is asking the reader to verify a number they have
 * to count digits in.
 */
object PhoneRules {
    /** India. The one country code v1 serves. */
    const val DIAL_CODE = "+91"

    /** A national mobile number is exactly ten digits and never starts below 6. */
    private const val NATIONAL_LENGTH = 10
    private val NATIONAL = Regex("^[6-9][0-9]{9}$")

    /** Everything that is not a digit, stripped — a pasted "+91 98450-12345" is still valid. */
    fun digits(raw: String): String = raw.filter(Char::isDigit)

    /**
     * The ten national digits of [raw], with a leading country code (91) or trunk zero
     * removed. Returns whatever is left when the input is too short to strip from — the
     * caller is validating, not sanitising, and silently returning "" for a half-typed number
     * would make [isValid] and [national] disagree about the same string.
     */
    fun national(raw: String): String {
        var d = digits(raw)
        if (d.length > NATIONAL_LENGTH) {
            if (d.startsWith(COUNTRY_DIGITS)) d = d.drop(COUNTRY_DIGITS.length)
            else if (d.startsWith(TRUNK_PREFIX)) d = d.drop(TRUNK_PREFIX.length)
        }
        return if (d.length > NATIONAL_LENGTH) d.takeLast(NATIONAL_LENGTH) else d
    }

    private const val COUNTRY_DIGITS = "91"
    private const val TRUNK_PREFIX = "0"

    fun isValid(raw: String): Boolean = NATIONAL.matches(national(raw))

    /** `+919845012345` — what GoTrue is given. Empty when [raw] is not a valid number. */
    fun toE164(raw: String): String =
        if (isValid(raw)) "$DIAL_CODE${national(raw)}" else ""

    /**
     * `+91 98450 12345` — what the code screen says it texted.
     *
     * Grouped 5 + 5, which is how the number is written in India and how the design draws it.
     * Falls back to the ungrouped digits for anything that is not a full number, so a partial
     * value still renders as itself rather than as an empty string.
     */
    fun display(raw: String): String {
        val n = national(raw)
        if (n.length != NATIONAL_LENGTH) return if (n.isEmpty()) "" else "$DIAL_CODE $n"
        return "$DIAL_CODE ${n.take(GROUP)} ${n.drop(GROUP)}"
    }

    private const val GROUP = 5
}

/**
 * The OTP resend policy (screen 119).
 *
 * Three rules, all of them visible in the design's own copy:
 *  - a cooldown, rendered as "Resend in 0:24";
 *  - an escape to email after two sends, which the screen's "NOT ARRIVING?" block promises
 *    in so many words ("After two failed sends we offer email sign-in instead");
 *  - a code length, which is what makes Verify tappable.
 */
object OtpResend {
    /** The cooldown between sends. The design's screenshot is caught at 0:24 of it. */
    const val COOLDOWN_SECONDS = 30

    /** After this many sends the screen offers email sign-in as a way out. */
    const val EMAIL_ESCAPE_AFTER_SENDS = 2

    /** Six boxes. */
    const val CODE_LENGTH = 6

    fun canResend(secondsLeft: Int): Boolean = secondsLeft <= 0

    fun offersEmailEscape(sendCount: Int): Boolean = sendCount >= EMAIL_ESCAPE_AFTER_SENDS

    fun isComplete(code: String): Boolean =
        code.length == CODE_LENGTH && code.all(Char::isDigit)

    /**
     * "Resend in 0:24" while the cooldown runs, "Resend code" once it is spent.
     *
     * Seconds are floored at zero rather than trusted: a negative would render as "0:-1",
     * and the only thing between this and a negative is a timer nobody has cancelled yet.
     */
    fun label(secondsLeft: Int): String =
        if (canResend(secondsLeft)) {
            "Resend code"
        } else {
            val s = secondsLeft.coerceAtLeast(0)
            "Resend in %d:%02d".format(s / SECONDS_PER_MINUTE, s % SECONDS_PER_MINUTE)
        }

    private const val SECONDS_PER_MINUTE = 60

    /**
     * The cooldown as a flow of seconds-remaining, [from] down to and including zero.
     *
     * A flow rather than a loop inside the ViewModel so the tick sequence is testable: under
     * `runTest` the `delay` runs on virtual time, which turns a thirty-second behaviour into a
     * millisecond assertion. The terminal zero is emitted deliberately — it is the value that
     * flips "Resend in 0:01" to "Resend code", and a countdown that stops at one leaves the
     * control disabled forever.
     */
    fun countdown(from: Int = COOLDOWN_SECONDS): Flow<Int> = flow {
        for (second in from downTo 0) {
            emit(second)
            if (second > 0) delay(ONE_SECOND_MS)
        }
    }

    /** One tick. Named so the loop above reads as seconds rather than as a magic number. */
    private const val ONE_SECOND_MS = 1_000L
}

/**
 * Alternatives for a taken handle (screen 90's four chips).
 *
 * These are SUGGESTIONS, not availability: nothing here has been near the server, and the
 * screen presents them as things to try rather than as things that are free. Tapping one puts
 * it in the field, which re-runs the same live check every other handle goes through — so a
 * suggestion that is itself taken comes back "Taken" a moment later, exactly like anything
 * else the user might type. Inventing an "available" badge for them would be the fabricated
 * server data REDESIGN_2026-09 §5.2 forbids.
 *
 * Every candidate is filtered through [HandleRules.isValidFormat], so a long base name cannot
 * produce a suggestion the field would reject.
 */
object HandleSuggestions {

    /** City → the short form Indian handles actually use. Anything else contributes nothing. */
    private val CITY_TAGS = mapOf(
        "bangalore" to "blr",
        "bengaluru" to "blr",
        "chennai" to "maa",
        "delhi" to "del",
        "goa" to "goa",
        "hyderabad" to "hyd",
        "kolkata" to "ccu",
        "mumbai" to "mum",
        "pune" to "pnq",
    )

    private const val MAX = 4

    fun alternatives(handle: String, city: String? = null): List<String> {
        val base = HandleRules.normalize(handle).filter { it.isLetterOrDigit() || it == '_' }
        if (base.isEmpty()) return emptyList()
        val tag = city?.trim()?.lowercase()?.let { CITY_TAGS[it] }
        return listOfNotNull(
            "${base}collective",
            tag?.let { "${base}_$it" },
            "the${base}co",
            "${base}live",
            "${base}official",
        )
            .filter { it != base && HandleRules.isValidFormat(it) }
            .distinct()
            .take(MAX)
    }
}
