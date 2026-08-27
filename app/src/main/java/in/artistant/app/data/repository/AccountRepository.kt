package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
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
}

interface AccountRepository {
    suspend fun deleteAccount()
    suspend fun requestDataExport(): ExportResult
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
    var exportResult: ExportResult = ExportResult.Inline("""{"user":"fixture"}"""),
) : AccountRepository {
    var deleteCallCount: Int = 0
        private set
    var exportCallCount: Int = 0
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
}
