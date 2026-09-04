package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * Why someone reports a CONVERSATION (design 73).
 *
 * A separate list from [ReportReasons] because the two surfaces are reporting
 * different things: a profile can be fake or offensive, a conversation is where
 * pressure and off-platform payment requests happen. Sharing one list would have
 * offered "Inaccurate profile" as a reason to report a chat.
 *
 * The strings go to `reports.reason` verbatim, so they are also the moderation
 * team's taxonomy — change them only with that team.
 */
val ConversationReportReasons = listOf(
    "Asked to move payment off-platform",
    "Pressuring or aggressive messages",
    "Spam or a scam",
    "Impersonating someone",
    "Something else",
)

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
    private val localLogMutex = Mutex()

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
            // Soft-fail, whatever the reason — missing table, RLS, offline. A
            // report must never throw into the chat UI (see the class doc), and
            // the local log is what keeps the failure from being silent.
            Timber.w(t, "reports insert failed — logging locally")
            appendLocal(row)
        }
    }

    /**
     * Read-modify-write of the whole local log, serialised.
     *
     * The read and the write are two separate DataStore operations, and this log
     * is the ONLY record of a report the server refused — two interleaved appends
     * would both encode the list they read and the loser's report would vanish
     * exactly where the fallback exists to catch it.
     */
    private suspend fun appendLocal(row: ReportInsert) = localLogMutex.withLock {
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
