package `in`.artistant.app.core.config

import `in`.artistant.app.BuildConfig
import `in`.artistant.app.designsystem.theme.AppRole

/**
 * Typed accessors over the flavored BuildConfig fields — the Android analogue
 * of the iOS xcconfig → Info.plist → AppEnvironment chain. Read config here,
 * never `BuildConfig.*` scattered through the app.
 */
object AppEnvironment {
    val supabaseUrl: String get() = BuildConfig.SUPABASE_URL
    val supabaseAnonKey: String get() = BuildConfig.SUPABASE_ANON_KEY

    /** Flavor name: "dev" / "staging" / "prod". */
    val flavor: String get() = BuildConfig.FLAVOR
    val isProd: Boolean get() = flavor == "prod"

    /** Hosted legal URLs — same canonical host as iOS AppEnvironment. */
    const val privacyPolicyUrl: String = "https://www.artistant.in/legal/privacy"
    const val termsOfServiceUrl: String = "https://www.artistant.in/legal/terms"
    const val supportEmail: String = "support@artistant.in"

    /**
     * Chat Realtime subscribe. Default ON (matches iOS). When false, Chat falls
     * back to poll-on-open/send — still correct, just not push-fresh.
     */
    val realtimeEnabled: Boolean get() = true

    /**
     * Master flag for the ₹99/mo subscription system. Default OFF — paywall +
     * gates stay inert until the operator flips Play Billing on.
     */
    val subscriptionsEnabled: Boolean get() = false

    /**
     * Observability keys — blank by default (dark-until-key). PostHogAnalytics /
     * SentryCrash early-return when empty so shipping without SDKs linked is safe.
     * Operator fills secrets.properties; empty string is the enable switch.
     */
    val posthogApiKey: String get() = BuildConfig.POSTHOG_API_KEY
    val sentryDsn: String get() = BuildConfig.SENTRY_DSN

    /**
     * Play product ids — fixed across environments. Verified against the iOS StoreKit
     * ids (`Sources/Artistant/Services/AppEnvironment.swift`), which these mirror
     * character for character. The role split is product truth, not decoration: artist
     * and client each buy their own monthly, and iOS asks
     * `EntitlementStore.isEntitled(_:)` with the id of the role it is gating.
     *
     * SEAM STATUS — reconcile this BEFORE flipping [subscriptionsEnabled]. Nothing in
     * production reads these yet: `PlayBillingService` carries a single hardcoded SKU of
     * its own (`in.artistant.app.subscription.monthly`) and uses it for the price query,
     * the purchase flow AND the entitlement probe, so with the flag on the paywall asks
     * Play for a product that matches neither id here — the price falls back to the
     * hardcoded "₹99" and `launchSubscribe` fails with "Product not found". Wiring Play
     * Billing means making that path take the role and resolve the id through
     * [subscriptionProductId], and creating the Play Console SKUs under these ids.
     */
    const val artistMonthlyProductId: String = "in.artistant.subscription.artist.monthly"
    const val clientMonthlyProductId: String = "in.artistant.subscription.client.monthly"

    fun subscriptionProductId(role: AppRole): String =
        when (role) {
            AppRole.Artist -> artistMonthlyProductId
            AppRole.Client -> clientMonthlyProductId
        }
}
