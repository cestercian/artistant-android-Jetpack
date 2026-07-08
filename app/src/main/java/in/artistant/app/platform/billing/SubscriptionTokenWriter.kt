package `in`.artistant.app.platform.billing

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the `{app_account_token → user_id}` binding the server uses to attribute a Play
 * subscription to a user (iOS `EntitlementStore.registerAccountToken`). Extracted as a seam
 * so [EntitlementStore] stays plain-JVM constructible and the "binding written BEFORE
 * purchase" behaviour is unit-testable with a fake writer (no SupabaseClient in the test).
 */
interface SubscriptionTokenWriter {
    /** Upsert the binding row for [appAccountToken] → [userId]. Best-effort; never throws. */
    suspend fun bind(appAccountToken: String, userId: String)
}

@Singleton
class SupabaseSubscriptionTokenWriter @Inject constructor(
    private val client: SupabaseClient,
) : SubscriptionTokenWriter {

    @Serializable
    private data class Binding(val app_account_token: String, val user_id: String)

    override suspend fun bind(appAccountToken: String, userId: String) {
        // Authenticated insert — RLS (`sat_insert_self`, migration 0067) enforces
        // auth.uid() = user_id, so a user can only bind a token to themselves; the RTDN
        // handler trusts the binding's existence as proof of who purchased. ON CONFLICT DO
        // NOTHING (no UPDATE path, no update RLS). Best-effort — a failed write just parks
        // the RTDN until reconcile, so it must never block/throw into the purchase flow.
        try {
            client.postgrest.from("subscription_account_tokens")
                .upsert(Binding(appAccountToken.lowercase(), userId.lowercase())) {
                    onConflict = "app_account_token"
                    ignoreDuplicates = true
                }
        } catch (t: Throwable) {
            Timber.w(t, "subscription_account_tokens binding write failed (best-effort)")
        }
    }
}
