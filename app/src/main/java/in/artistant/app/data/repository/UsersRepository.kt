package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.model.appRoleFromDb
import `in`.artistant.app.data.model.dbValue
import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The `public.users` row that backs every signed-in account (port of iOS
 * `UsersRepository`). The auth trigger `handle_new_auth_user` creates the row with
 * only `(id, phone)`; the iOS/Android signup flow fills in handle/name/city/role.
 *
 * Interface + Supabase impl + Fake — the repository seam (ARCHITECTURE §4). M1a needs
 * three methods; the rest of the CRUD lands with the screens that use it.
 */
interface UsersRepository {
    /** Calls the `handle_is_available(target_handle text)` RPC. Never throws — a network
     *  blip returns [HandleAvailability.Failure] so the UI degrades to "unknown". */
    suspend fun handleIsAvailable(handle: String): HandleAvailability

    /** Reads the signed-in user's role/name/city/handle. null when not signed in or the
     *  row is absent. THROWS on a network/RLS error so the caller can degrade (keep the
     *  local role) rather than mis-route — the [returning-login] distinction. */
    suspend fun fetchSelfProfile(): SelfProfile?

    /** Upserts the profile row (idempotent, onConflict=id). Throws
     *  [AppError.UniqueViolation] when the handle was just taken. */
    suspend fun upsertSelfProfile(
        handle: String,
        fullName: String,
        city: String,
        role: AppRole,
        termsAccepted: Boolean,
    )
}

@Singleton
class SupabaseUsersRepository @Inject constructor(
    private val client: SupabaseClient,
) : UsersRepository {

    // --- Row DTOs. Explicit @Serializable so we never `select("*")`. ---

    @Serializable
    private data class UserRow(
        val role: String? = null,
        @SerialName("full_name") val fullName: String? = null,
        val city: String? = null,
        val handle: String? = null,
    )

    @Serializable
    private data class ArtistRow(@SerialName("setup_complete") val setupComplete: Boolean? = null)

    @Serializable
    private data class UpsertRow(
        val id: String,
        val handle: String,
        @SerialName("full_name") val fullName: String,
        val city: String,
        val role: String,
        @SerialName("terms_accepted_at") val termsAcceptedAt: String?,
    )

    override suspend fun handleIsAvailable(handle: String): HandleAvailability =
        try {
            // The RPC is SECURITY DEFINER + granted to anon/authenticated, so it works even
            // before sign-in finishes. Pass the param as a JsonObject (the non-reified rpc
            // overload) and decode the bare boolean result.
            val params = buildJsonObject { put("target_handle", handle) }
            val free: Boolean = client.postgrest.rpc("handle_is_available", params).decodeAs()
            if (free) HandleAvailability.Available else HandleAvailability.Unavailable
        } catch (t: Throwable) {
            // Non-fatal: treat as "unknown" so a transient failure doesn't wedge Continue.
            HandleAvailability.Failure(t.message ?: "Couldn't check that handle.")
        }

    override suspend fun fetchSelfProfile(): SelfProfile? {
        // Lowercase the UUID before every query (a load-bearing iOS behaviour).
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase() ?: return null

        val rows = client.postgrest.from("users")
            .select(Columns.list("role", "full_name", "city", "handle")) {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<UserRow>()
        val row = rows.firstOrNull() ?: return null
        val role = appRoleFromDb(row.role)

        // Artists need the wizard-completion flag too so a returning artist on a fresh
        // device boots straight into the artist tabs (the server is the source of truth —
        // the local role/setup flags default false on any new device until hydrated here).
        var artistSetup: Boolean? = null
        if (role == AppRole.Artist) {
            artistSetup = client.postgrest.from("artists")
                .select(Columns.list("setup_complete")) {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<ArtistRow>()
                .firstOrNull()?.setupComplete
        }

        return SelfProfile(
            role = role,
            fullName = row.fullName,
            city = row.city,
            handle = row.handle,
            artistSetupComplete = artistSetup,
        )
    }

    override suspend fun upsertSelfProfile(
        handle: String,
        fullName: String,
        city: String,
        role: AppRole,
        termsAccepted: Boolean,
    ) {
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercase()
            ?: throw AppError.NotFoundOrUnauthorized

        val row = UpsertRow(
            id = userId,
            handle = handle.trim().lowercase(),
            fullName = fullName,
            city = city,
            role = role.dbValue,
            // ISO8601 with a fractional-seconds-tolerant parser server-side.
            termsAcceptedAt = if (termsAccepted) Clock.System.now().toString() else null,
        )

        try {
            client.postgrest.from("users").upsert(row) { onConflict = "id" }
        } catch (t: Throwable) {
            // A 23505 on users_handle_key means the handle was raced onto another device.
            // mapPostgrest classifies it to UniqueViolation; everything else stays typed.
            throw mapPostgrest(t)
        }
    }
}
