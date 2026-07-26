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
 * SDK-independent and always runs: every reported throwable's message is passed
 * through [PiiScrub] BEFORE it would ever leave the device (the beforeSend contract),
 * and only the OPAQUE user id is attached — never name/email/phone. To go live,
 * link `sentry-android`, init in `Application.onCreate`, set a `beforeSend` that runs
 * `PiiScrub.scrub`, and send the scrubbed throwable from [forward] behind the DSN guard.
 */
@Singleton
open class SentryCrash @Inject constructor() : Crash {

    /** The opaque user id currently attached (set on sign-in, cleared on sign-out). */
    @Volatile
    var attachedUserId: String? = null
        private set

    override fun record(throwable: Throwable) {
        // Scrub PII from the message up front — the same redaction the beforeSend hook
        // would apply — so nothing sensitive is ever staged for send.
        val scrubbed = PiiScrub.scrub(throwable.message ?: "")
        forward(throwable, scrubbed)
    }

    override fun setUser(userId: String?) {
        // Only the opaque Supabase UUID; null clears the association on sign-out (DPDP §11).
        attachedUserId = userId
        forwardUser(userId)
    }

    /** SDK send point — guarded no-op (SDK not linked + dark-until-DSN). Overridable for tests. */
    protected open fun forward(throwable: Throwable, scrubbedMessage: String) {
        if (AppEnvironment.sentryDsn.isBlank()) return
        // SDK not linked — see class header. No crash, no send.
    }

    protected open fun forwardUser(userId: String?) {
        if (AppEnvironment.sentryDsn.isBlank()) return
    }
}
