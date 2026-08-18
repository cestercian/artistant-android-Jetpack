package `in`.artistant.app.platform.observability

import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.core.logging.PiiScrub
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sentry-shaped [Crash] wrapper (port of iOS `SentryConfig`).
 *
 * DARK-UNTIL-KEY + GUARDED NO-OP
 * ==============================
 * The Sentry Android SDK is NOT linked (same rationale as [PostHogAnalytics]: with
 * no DSN, linking it changes nothing observable). [forward] is a guarded no-op that
 * early-returns when `SENTRY_DSN` is blank (the default). The load-bearing part is
 * SDK-independent and always runs: [forward] never receives the reported exception,
 * only a SCRUBBED TWIN of it — every message in the cause chain through [PiiScrub],
 * which is the beforeSend contract applied one step earlier — and only the OPAQUE
 * user id is attached, never name/email/phone. To go live, link `sentry-android`,
 * init in `Application.onCreate`, and call `Sentry.captureException(scrubbed)` from
 * [forward] behind the DSN guard: the twin is what makes that call safe, since
 * `captureException` serialises the whole chain, not just the top message.
 */
@Singleton
open class SentryCrash @Inject constructor() : Crash {

    /**
     * The opaque user id currently attached (set on sign-in, cleared on sign-out). Kept as
     * state, not just forwarded, so the DPDP §11 rule that sign-out clears the association
     * is assertable with no SDK linked — `ObservabilityTest` is the reader.
     */
    @Volatile
    var attachedUserId: String? = null
        private set

    override fun record(throwable: Throwable) {
        // Scrubbing `throwable.message` alone was not enough. The wiring this class
        // prescribes is `captureException`, which serialises every cause's message too, and
        // the PII is usually a link DOWN the chain: a repository wraps a PostgREST failure
        // whose body echoed the user's email into `AppError.Unknown(cause)`, so the display
        // string was clean while the upload would have carried the address verbatim. The
        // send point therefore never sees the original at all.
        forward(scrubbedTwin(throwable))
    }

    override fun setUser(userId: String?) {
        // Only the opaque Supabase UUID; null clears the association on sign-out (DPDP §11).
        attachedUserId = userId
        forwardUser(userId)
    }

    /** SDK send point — guarded no-op (SDK not linked + dark-until-DSN). Overridable for tests. */
    protected open fun forward(scrubbed: Throwable) {
        if (AppEnvironment.sentryDsn.isBlank()) return
        // SDK not linked — see class header. No crash, no send.
    }

    protected open fun forwardUser(userId: String?) {
        if (AppEnvironment.sentryDsn.isBlank()) return
    }

    /**
     * A send-safe twin of [throwable]: one link per exception in the cause chain, each
     * carrying the original type name plus its message run through [PiiScrub], and the
     * original stack trace (frames are class/method/line — no user data). Rebuilding rather
     * than mutating means everything NOT copied is dropped instead of leaked: suppressed
     * exceptions, and whatever else a future SDK would have serialised off the original.
     */
    private fun scrubbedTwin(throwable: Throwable): Throwable {
        val chain = causeChain(throwable)
        var twin = scrubbedLink(chain.last(), cause = null)
        for (i in chain.size - 2 downTo 0) twin = scrubbedLink(chain[i], cause = twin)
        return twin
    }

    private fun scrubbedLink(original: Throwable, cause: Throwable?): Throwable =
        ScrubbedThrowable(
            "${original.javaClass.name}: ${PiiScrub.scrub(original.message ?: "")}",
            cause,
        ).also { it.stackTrace = original.stackTrace }

    /**
     * [throwable] first, then its causes. Bounded and identity-checked: a cause chain can be
     * wired into a cycle (`initCause` in both directions), and a walk that trusted it would
     * hang here rather than in the SDK.
     */
    private fun causeChain(throwable: Throwable): List<Throwable> {
        val chain = ArrayList<Throwable>(MAX_CHAIN)
        var next: Throwable? = throwable
        while (next != null && chain.size < MAX_CHAIN) {
            val link: Throwable = next
            if (chain.any { it === link }) break
            chain += link
            next = link.cause
        }
        return chain
    }

    private companion object {
        /** Deep enough for any real wrap chain; the cap is insurance against a cycle. */
        const val MAX_CHAIN = 8
    }
}

/**
 * A reported exception with every message redacted. The original type name rides in the
 * message (`java.io.IOException: …`) because the type is the useful grouping key in a crash
 * dashboard and the real class can't be reconstructed generically — and seeing THIS class in
 * a payload is the signal that the redaction ran.
 */
private class ScrubbedThrowable(message: String, cause: Throwable?) : Throwable(message, cause)
