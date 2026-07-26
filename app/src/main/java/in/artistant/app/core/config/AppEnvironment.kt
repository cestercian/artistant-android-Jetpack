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

    /** Play product ids — fixed across environments (mirrors iOS StoreKit ids). */
    const val artistMonthlyProductId: String = "in.artistant.subscription.artist.monthly"
    const val clientMonthlyProductId: String = "in.artistant.subscription.client.monthly"

    fun subscriptionProductId(role: AppRole): String =
        when (role) {
            AppRole.Artist -> artistMonthlyProductId
            AppRole.Client -> clientMonthlyProductId
        }
}
