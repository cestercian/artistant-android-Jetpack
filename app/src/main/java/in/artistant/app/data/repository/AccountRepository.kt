package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DPDP §11 account actions — port of iOS `AccountService`.
 *
 * Both Edge Functions use the user's JWT (not shared-secret auth): the token
 * identifies whose data to export or erase.
 */

sealed class ExportResult {
    /** Small payload — JSON returned inline from the Edge Function. */
    data class Inline(val json: String) : ExportResult()

    /** Large payload — 1-hour signed URL in the private `exports` bucket. */
    data class SignedUrl(val url: String, val expiresInSeconds: Int) : ExportResult()
}

sealed class AccountRepositoryError(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    class DeleteFailed(detail: String) : AccountRepositoryError("Account deletion failed: $detail")
    class Underlying(cause: Throwable) :
        AccountRepositoryError(cause.message ?: "Account action failed", cause)

    /**
     * There is no live session to revoke the OTHER ones with — see [requireLiveSession].
     *
     * The message is the one design screen 128 shows, and it is written to say what did NOT
     * happen: "couldn't sign out your other devices" over a banner claiming success is the
     * failure this class exists to make impossible.
     */
    class NoSession : AccountRepositoryError(
        "Couldn't reach the server — nothing was signed out yet.",
    )
}

interface AccountRepository {
    suspend fun deleteAccount()
    suspend fun requestDataExport(): ExportResult

    /**
     * Revoke every OTHER session on this account, keeping this device signed in.
     *
     * The one control design screen 128 can honestly offer. Supabase exposes no session LIST
     * to a client — there is no endpoint and no table behind `auth.sessions` that RLS lets an
     * app read — so the screen cannot draw the iPad and the other phone the design does. What
     * it CAN do is end them, which is the action anyone actually came to that screen for.
     *
     * [SignOutScope.OTHERS] rather than `GLOBAL`, deliberately: global revokes this device's
     * refresh token too, which would sign the user out of the screen they are standing on as a
     * side effect of securing the account, and would skip `SessionManager.signOut()`'s ordered
     * teardown (push-token handback first, then prefs) on the way. "Sign out everywhere else"
     * is also the design's own label.
     *
     * Throws — see [requireLiveSession] — rather than reporting a revoke it could not perform.
     */
    suspend fun signOutOtherDevices()
}

@Singleton
class SupabaseAccountRepository @Inject constructor(
    private val client: SupabaseClient,
) : AccountRepository {

    override suspend fun deleteAccount() {
        try {
            val response = client.functions.invoke(
                function = "delete-account",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
            requireDeleted(response.bodyAsText())
        } catch (e: AccountRepositoryError) {
            throw e
        } catch (t: Throwable) {
            throw AccountRepositoryError.Underlying(t)
        }
    }

    override suspend fun requestDataExport(): ExportResult {
        try {
            val raw = client.functions.invoke(
                function = "data-export",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            ).bodyAsText()
            return parseExportResponse(raw)
        } catch (e: AccountRepositoryError) {
            throw e
        } catch (t: Throwable) {
            throw AccountRepositoryError.Underlying(t)
        }
    }

    override suspend fun signOutOtherDevices() {
        try {
            requireLiveSession(
                hasSession = { client.auth.currentSessionOrNull() != null },
                refresh = { client.auth.refreshCurrentSession() },
            )
            client.auth.signOut(SignOutScope.OTHERS)
        } catch (e: AccountRepositoryError) {
            throw e
        } catch (t: Throwable) {
            throw AccountRepositoryError.Underlying(t)
        }
    }
}

@Serializable
internal data class DeleteResponse(val deleted: Boolean)

@Serializable
internal data class SignedUrlEnvelope(
    val mode: String,
    val url: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int = 3600,
)

private val accountJson = Json { ignoreUnknownKeys = true }

/**
 * Accept a delete only if the server actually said it deleted the account.
 *
 * `delete-account` answers 200 with a body either way, so the HTTP status is not
 * the answer — `{"deleted": false}` is a REFUSAL, and treating it as success
 * would tell someone exercising their DPDP §11 erasure right that their data was
 * gone when it was not. Extracted from the invoke call so the decision is
 * reachable without a live SupabaseClient: it is the one place in this file that
 * decides whether an erasure happened.
 */
internal fun requireDeleted(body: String) {
    val parsed = runCatching { accountJson.decodeFromString<DeleteResponse>(body) }.getOrNull()
        ?: throw AccountRepositoryError.DeleteFailed("Unreadable response: ${body.take(120)}")
    if (!parsed.deleted) {
        throw AccountRepositoryError.DeleteFailed("Server returned deleted=false")
    }
}

/**
 * Refuse "sign out everywhere else" unless there is a session to do it WITH.
 *
 * `Auth.signOut(SignOutScope.OTHERS)` is not a success signal on its own. supabase-kt swallows
 * 401 / 403 / 404 on the logout POST — a revoked or expired token reads exactly like a server
 * that agreed — and with no session at all it never issues the request in the first place and
 * returns normally. Screen 128 then raised "Every other session is signed out" over an account
 * whose other sessions were untouched, to somebody who came to that screen because they think
 * they have been compromised. That is the one banner in the app that must never be a guess.
 *
 * A refresh first, because an app in the background long enough for the access token to lapse is
 * the ordinary case and re-issuing it is exactly what supabase-kt is holding a refresh token
 * for. What is left after a refresh that could not restore one is a device with no credentials,
 * and the honest answer there is a failure the screen shows — not silence dressed as success.
 *
 * The session is passed in as two lambdas so the decision is reachable without a live
 * `SupabaseClient`: it is the one rule in this file that decides whether a revoke happened.
 */
internal suspend fun requireLiveSession(
    hasSession: () -> Boolean,
    refresh: suspend () -> Unit,
) {
    if (hasSession()) return
    runCatching { refresh() }
    if (!hasSession()) throw AccountRepositoryError.NoSession()
}

/**
 * Read the export envelope: a signed URL for a large payload, else the body
 * itself as inline JSON.
 *
 * The mode is decided by the envelope, not by shape-guessing — anything that is
 * not a well-formed `signed_url` envelope IS the export, which is what makes the
 * inline path work for a body that happens to contain other keys. A `signed_url`
 * envelope carrying a blank URL is a server bug rather than an export, and is
 * rejected instead of handing the caller a link that opens nothing.
 */
internal fun parseExportResponse(raw: String): ExportResult {
    val envelope = runCatching { accountJson.decodeFromString<SignedUrlEnvelope>(raw) }.getOrNull()
    if (envelope?.mode == "signed_url") {
        if (envelope.url.isBlank()) {
            throw AccountRepositoryError.Underlying(
                IllegalStateException("data-export returned an empty signed URL"),
            )
        }
        return ExportResult.SignedUrl(url = envelope.url, expiresInSeconds = envelope.expiresInSeconds)
    }
    return ExportResult.Inline(json = raw)
}

/** Test / preview twin — mirrors iOS `FakeAccountService`. */
class FakeAccountRepository(
    var failDelete: Boolean = false,
    var failExport: Boolean = false,
    var failSignOutOthers: Boolean = false,
    var exportResult: ExportResult = ExportResult.Inline("""{"user":"fixture"}"""),
) : AccountRepository {
    var deleteCallCount: Int = 0
        private set
    var exportCallCount: Int = 0
        private set
    var signOutOthersCallCount: Int = 0
        private set

    override suspend fun deleteAccount() {
        deleteCallCount++
        if (failDelete) throw AccountRepositoryError.DeleteFailed("uitest forced failure")
    }

    override suspend fun requestDataExport(): ExportResult {
        exportCallCount++
        if (failExport) throw AccountRepositoryError.Underlying(IllegalStateException("uitest forced failure"))
        return exportResult
    }

    override suspend fun signOutOtherDevices() {
        signOutOthersCallCount++
        if (failSignOutOthers) {
            throw AccountRepositoryError.Underlying(IllegalStateException("uitest forced failure"))
        }
    }
}
