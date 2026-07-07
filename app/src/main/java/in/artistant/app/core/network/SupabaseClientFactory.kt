package `in`.artistant.app.core.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import `in`.artistant.app.core.config.AppEnvironment

/**
 * Builds the single SupabaseClient with the five modules the app uses. supabase-kt
 * bundles a Ktor client, so there's no Retrofit/OkHttp wiring here beyond the
 * engine dependency.
 */
object SupabaseClientFactory {

    // The production Supabase host. A prod build MUST point here; a non-prod
    // build MUST NOT (the iOS `assertBackendMatchesTier` guard, ported).
    private const val PROD_HOST = "ouikzcxtetxjuxrygkur.supabase.co"

    fun create(): SupabaseClient {
        val url = AppEnvironment.supabaseUrl
        assertTierMatches(url)
        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = AppEnvironment.supabaseAnonKey,
        ) {
            install(Auth) {
                // OAuth callback deep link — matches the manifest scheme/host.
                scheme = "in.artistant.app"
                host = "login-callback"
            }
            install(Postgrest)
            install(Realtime)
            install(Storage)
            install(Functions)
        }
    }

    /**
     * Tier guard. In M0 the flavors ship PLACEHOLDER URLs ("REPLACE.supabase.co"),
     * so we only assert once a real prod host is configured — otherwise the
     * placeholder would crash the app on launch. Once real creds land, a prod
     * build pointing anywhere but [PROD_HOST] (or a non-prod build pointing AT
     * it) is a hard crash, exactly like iOS.
     */
    private fun assertTierMatches(url: String) {
        val isPlaceholder = "REPLACE" in url
        if (isPlaceholder) return // M0: nothing to assert against a placeholder.

        val pointsAtProd = PROD_HOST in url
        if (AppEnvironment.isProd) {
            require(pointsAtProd) { "prod build must target $PROD_HOST, got $url" }
        } else {
            require(!pointsAtProd) { "non-prod build must NOT target the prod host $url" }
        }
    }
}
