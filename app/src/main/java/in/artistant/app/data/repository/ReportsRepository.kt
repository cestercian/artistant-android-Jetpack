package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `public.reports` insert — port of iOS ReportsRepository (mig 0086).
 * Fire-and-forget; on missing table / offline, append to a local DataStore log.
 * Insert-only RLS; no client SELECT.
 */
interface ReportsRepository {
    suspend fun reportConversation(threadId: String, reason: String, details: String? = null)
    suspend fun reportArtist(artistId: String, reason: String, details: String? = null)
}

val ReportReasons = listOf(
    "Inaccurate profile",
    "Not a real artist",
    "Scam or spam",
    "Offensive",
    "Something else",
)

@Singleton
class SupabaseReportsRepository @Inject constructor(
    private val client: SupabaseClient,
    private val prefs: AppPreferences,
) : ReportsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun reportConversation(threadId: String, reason: String, details: String?) {
        insert(
            ReportInsert(
                threadId = threadId.lowercase(),
                reason = reason,
                details = details?.ifBlank { null },
                surface = "conversation",
            ),
        )
    }

    override suspend fun reportArtist(artistId: String, reason: String, details: String?) {
        val id = artistId.lowercase()
        insert(
            ReportInsert(
                reportedUserId = id,
                artistId = id,
                reason = reason,
                details = details?.ifBlank { null },
                surface = "artist",
            ),
        )
    }

    private suspend fun insert(row: ReportInsert) {
        try {
            client.from("reports").insert(row)
        } catch (t: Throwable) {
            Timber.w(t, "reports insert failed — logging locally")
            appendLocal(row)
            // Soft-fail for missing table / offline — never block chat UX.
            val mapped = mapPostgrest(t)
            if ("reports" !in (t.message.orEmpty()) && "PGRST" !in (t.message.orEmpty()) &&
                "relation" !in (t.message.orEmpty()).lowercase()
            ) {
                // Still soft — conversation report must not throw into UI.
                Timber.d("report soft-fail: %s", mapped.message)
            }
        }
    }

    private suspend fun appendLocal(row: ReportInsert) {
        val prev = prefs.getString(LOCAL_KEY).first().orEmpty()
        val next = if (prev.isBlank()) json.encodeToString(listOf(row))
        else {
            val list = runCatching { json.decodeFromString<List<ReportInsert>>(prev) }.getOrDefault(emptyList())
            json.encodeToString(list + row)
        }
        prefs.setString(LOCAL_KEY, next)
    }

    companion object {
        const val LOCAL_KEY = "reports.localLog"
    }
}

class FakeReportsRepository : ReportsRepository {
    val conversation = mutableListOf<Triple<String, String, String?>>()
    val artists = mutableListOf<Triple<String, String, String?>>()

    override suspend fun reportConversation(threadId: String, reason: String, details: String?) {
        conversation += Triple(threadId, reason, details)
    }

    override suspend fun reportArtist(artistId: String, reason: String, details: String?) {
        artists += Triple(artistId, reason, details)
    }
}

@Serializable
private data class ReportInsert(
    @SerialName("reported_user_id") val reportedUserId: String? = null,
    @SerialName("artist_id") val artistId: String? = null,
    @SerialName("thread_id") val threadId: String? = null,
    val reason: String,
    val details: String? = null,
    val surface: String,
)
