package `in`.artistant.app.platform.account

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DPDP §11 compliance surface (port of iOS `AccountService`). Wraps the
 * `delete-account` + `data-export` Edge Functions so the UI (Profile delete
 * flow, DataExportScreen) calls them without duplicating the auth-header + URL
 * plumbing.
 *
 * Both functions use standard Supabase USER-JWT auth (NOT the shared-secret
 * pattern send-push/compute-score use): they're invoked BY the user about THEIR
 * own data, so the JWT both authenticates the caller AND identifies whose data to
 * operate on. supabase-kt's `functions.invoke` attaches the session token
 * automatically (same as `BookingsRepository.cancel`). Both POST with an empty
 * JSON body — the functions ignore the body and read the JWT.
 *
 * Interface + Supabase impl + Fake — the repository seam (ARCHITECTURE §4). The
 * Fake lets the delete/export FAILURE paths be unit-tested: a failed delete that
 * LOOKS successful would strand an orphaned backend account (and leave the user
 * thinking they're erased), and a failed export must surface, not silently no-op.
 */
interface AccountService {
    /**
     * DPDP §11(1)(b) erasure. POSTs `delete-account` (anonymize + ban). Returns on
     * success; THROWS on auth/network/5xx or a `deleted=false` body. After success
     * the caller's session is dead — the caller must sign out + wipe local state.
     */
    suspend fun deleteAccount()

    /**
     * DPDP §11(1)(a) portability. POSTs `data-export`; the function returns either
     * the inline JSON dump OR a `{mode:"signed_url", url, expires_in_seconds}`
     * envelope for a large payload. The caller materializes the result to a file +
     * shares it.
     */
    suspend fun exportData(): ExportResult
}

/** The two `data-export` response shapes, keyed on payload size (iOS `ExportResult`). */
sealed interface ExportResult {
    /** Small payload — the raw JSON bytes returned inline. */
    data class Inline(val json: String) : ExportResult

    /** Large payload — uploaded to the private `exports` bucket; fetch via [url]. */
    data class SignedUrl(val url: String, val expiresInSeconds: Int) : ExportResult
}

/** Thrown when the server acknowledges the request but reports the delete didn't happen. */
class AccountDeleteException(message: String) : Exception(message)

@Singleton
class SupabaseAccountService @Inject constructor(
    private val client: SupabaseClient,
) : AccountService {

    override suspend fun deleteAccount() {
        // Empty-object body: the function reads the JWT, not the body. Using the
        // body-overload (vs a raw builder) keeps us on the same invoke path
        // BookingsRepository already relies on, across supabase-kt versions.
        val resp = client.functions.invoke(function = "delete-account", body = buildJsonObject {})
        val decoded = LENIENT.decodeFromString<DeleteResponse>(resp.bodyAsText())
        if (!decoded.deleted) throw AccountDeleteException("Server returned deleted=false")
    }

    override suspend fun exportData(): ExportResult {
        val resp = client.functions.invoke(function = "data-export", body = buildJsonObject {})
        val text = resp.bodyAsText()
        // Try the signed-url envelope first. If it decodes AND mode=="signed_url",
        // it's the large-payload path; a malformed url there is a server bug — but a
        // blank url is unusable, so fall through to inline only when it's clearly not
        // an envelope. Otherwise the bytes ARE the inline JSON dump.
        val envelope = runCatching { LENIENT.decodeFromString<SignedUrlEnvelope>(text) }.getOrNull()
        return if (envelope != null && envelope.mode == "signed_url" && envelope.url.isNotBlank()) {
            ExportResult.SignedUrl(url = envelope.url, expiresInSeconds = envelope.expiresInSeconds)
        } else {
            ExportResult.Inline(json = text)
        }
    }

    @Serializable
    private data class DeleteResponse(val deleted: Boolean = false)

    @Serializable
    private data class SignedUrlEnvelope(
        val mode: String = "",
        val url: String = "",
        val expires_in_seconds: Int = 0,
    ) {
        val expiresInSeconds: Int get() = expires_in_seconds
    }

    private companion object {
        val LENIENT = Json { ignoreUnknownKeys = true }
    }
}
