package `in`.artistant.app.platform.observability

import `in`.artistant.app.core.config.AppEnvironment
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PostHog-shaped [Analytics] wrapper (port of iOS `Analytics`).
 *
 * DARK-UNTIL-KEY + GUARDED NO-OP
 * ==============================
 * The PostHog Android SDK is NOT linked (avoids adding a network-fetched dep whose
 * only observable effect — with no key configured — is identical to doing nothing).
 * So [forward] is a guarded no-op: it early-returns when `POSTHOG_API_KEY` is blank
 * (the default), which is ALWAYS true until the operator sets the key. The
 * load-bearing, testable parts live here either way: the [ALLOWED_EVENTS] allowlist (a
 * stray `capture("foo")` can never leak a new event) and identify/reset. To go live later,
 * link `posthog-android`, and send from [forward] behind the same key guard — no
 * other change (see [forward]).
 *
 * EVENT TAXONOMY — the minimal funnel surface (owner directive): the core four of iOS
 * `Analytics.allowedEvents` (iOS also carries five browse/review ids Android has no
 * emitter for). Properties are NON-PII only (UUIDs + ints, never names/emails/message
 * bodies). See [ALLOWED_EVENTS] for which ids are wired.
 */
@Singleton
open class PostHogAnalytics @Inject constructor() : Analytics {

    override fun capture(event: String, props: Map<String, Any?>) {
        // Allowlist FIRST so a non-allowlisted event is dropped regardless of whether
        // a key is set — the surface can't silently grow.
        if (!isAllowed(event)) {
            Timber.d("Analytics: dropping non-allowlisted event '%s'", event)
            return
        }
        forward(event, props)
    }

    override fun identify(userId: String) = forwardIdentify(userId)

    override fun reset() = forwardReset()

    /**
     * The SDK send point. Overridable so tests can observe which events reach the
     * sink; in production it's a guarded no-op (SDK not linked + dark-until-key).
     * When PostHog is linked, gate the real `PostHogSDK.capture(...)` on the same
     * blank-key check.
     */
    protected open fun forward(event: String, props: Map<String, Any?>) {
        if (AppEnvironment.posthogApiKey.isBlank()) return
        // SDK not linked — see class header. No crash, no send.
    }

    protected open fun forwardIdentify(userId: String) {
        if (AppEnvironment.posthogApiKey.isBlank()) return
    }

    protected open fun forwardReset() {
        if (AppEnvironment.posthogApiKey.isBlank()) return
    }

    companion object {
        /**
         * The CEILING on the emitted surface — `capture` drops anything not in this set, so
         * the taxonomy can't grow without a deliberate edit here. It is not a promise that
         * all four are wired: `app_open` (ArtistantApplication.onCreate) and
         * `signup_complete` (SignupViewModel) have emitters today; `booking_created` and
         * `message_sent` are iOS-parity ids sitting ahead of their Android call sites, so
         * adding one is a one-line change at the call site rather than an allowlist edit.
         *
         * `booking_paid` is deliberately absent. v1 is the no-payments matchmaker
         * (request → accept), so it could never fire; iOS deleted it for the same reason
         * (`CheckoutView`: "Removed the payment-era booking_paid event").
         */
        val ALLOWED_EVENTS: Set<String> = setOf(
            "app_open",
            "signup_complete",
            "booking_created",
            "message_sent",
        )

        fun isAllowed(event: String): Boolean = event in ALLOWED_EVENTS
    }
}
