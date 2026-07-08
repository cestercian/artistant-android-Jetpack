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
     * v1 monetization gate (the iOS `subscriptionsEnabled` flag). DEFAULT OFF — v1 ships
     * with zero payment code, so the whole M7 Play-Billing seam stays DORMANT: the artist
     * "stay listed" banner is hidden, the paywall is unreachable, and PlayBilling never
     * touches BillingClient. The operator flips `SUBSCRIPTIONS_ENABLED` (secrets.properties)
     * once the Play Console products + RTDN backend are live — a config change, no code.
     */
    val subscriptionsEnabled: Boolean get() = BuildConfig.SUBSCRIPTIONS_ENABLED

    /**
     * The two Play subscription product ids (iOS `AppEnvironment.{artist,client}MonthlyProductID`).
     * Role is derived from the `.artist.monthly` / `.client.monthly` suffix. Constants, not
     * BuildConfig — the same ids back every flavor's Play Console entry.
     */
    const val ARTIST_MONTHLY_PRODUCT_ID: String = "in.artistant.subscription.artist.monthly"
    const val CLIENT_MONTHLY_PRODUCT_ID: String = "in.artistant.subscription.client.monthly"

    /** Both product ids, the set the paywall queries (iOS `subscriptionProductIDs`). */
    val subscriptionProductIds: List<String>
        get() = listOf(ARTIST_MONTHLY_PRODUCT_ID, CLIENT_MONTHLY_PRODUCT_ID)

    /**
     * Observability keys — DARK-UNTIL-KEY. Blank (the default) means the PostHog /
     * Sentry wrappers stay a silent no-op and never init the SDK. iOS parity: the
     * value being nil/empty is the enable switch, not a code change.
     */
    val posthogApiKey: String get() = BuildConfig.POSTHOG_API_KEY
    val sentryDsn: String get() = BuildConfig.SENTRY_DSN

    /** Support inbox for the Profile → Help mailto (iOS `AppEnvironment.supportEmail`). */
    const val SUPPORT_EMAIL: String = "support@artistant.in"

    /** Hosted legal pages (iOS `AppEnvironment.{terms,privacy}PolicyURL`) — shown on the paywall. */
    const val TERMS_URL: String = "https://www.artistant.in/legal/terms"
    const val PRIVACY_URL: String = "https://www.artistant.in/legal/privacy"
}
