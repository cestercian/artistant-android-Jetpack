package `in`.artistant.app.core.config

import `in`.artistant.app.BuildConfig

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

    /**
     * Realtime chat kill-switch (the iOS `realtimeEnabled` flag). Default ON;
     * flip off to fall back to poll-on-send if a Realtime transport issue shows
     * up in the field. A compile-time constant for now — no BuildConfig field
     * until it needs to differ per flavor.
     */
    val realtimeEnabled: Boolean get() = true

    /**
     * v1 monetization gate (the iOS `subscriptionsEnabled` flag). Default OFF — v1
     * ships with zero payment code, so the artist "stay listed" subscribe banner
     * stays hidden until the operator flips this and the Play Billing paywall (M7)
     * lands. A compile-time constant until it needs to differ per flavor.
     */
    val subscriptionsEnabled: Boolean get() = false

    /**
     * Observability keys — DARK-UNTIL-KEY. Blank (the default) means the PostHog /
     * Sentry wrappers stay a silent no-op and never init the SDK. iOS parity: the
     * value being nil/empty is the enable switch, not a code change.
     */
    val posthogApiKey: String get() = BuildConfig.POSTHOG_API_KEY
    val sentryDsn: String get() = BuildConfig.SENTRY_DSN

    /** Support inbox for the Profile → Help mailto (iOS `AppEnvironment.supportEmail`). */
    const val SUPPORT_EMAIL: String = "support@artistant.in"
}
