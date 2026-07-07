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
}
