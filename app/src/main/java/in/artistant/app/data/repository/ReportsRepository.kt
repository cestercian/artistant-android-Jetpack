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
    suspend fun reportConversation(
        threadId: String,
        reason: String,
        details: String? = null,
    ): ReportOutcome

    suspend fun reportArtist(
        artistId: String,
        reason: String,
        details: String? = null,
    ): ReportOutcome
}

/**
 * Whether a report reached the server, or is sitting in this install's local log
 * waiting for one.
 *
 * The distinction is the whole of screen 56's note ("says queued, not
 * received"). The insert soft-fails by design — a report must never throw into
 * the UI — and before this type existed the caller could not tell a landed
 * report from a logged one, so the only copy it could honestly show was the
 * weaker of the two for both. [Queued] is deliberately not called "failed": the
 * row is kept, and it is kept for THIS account on THIS device only.
 */
enum class ReportOutcome { Sent, Queued }

val ReportReasons = listOf(
    "Inaccurate profile",
    "Not a real artist",
    "Scam or spam",
    "Offensive",
    "Something else",
)

/**
 * Screen 56's reasons for reporting an artist from their profile.
 *
 * A different list from [ReportReasons], which is the *conversation* report's:
 * the two surfaces see different evidence. From a thread the reader is reporting
 * what was said to them; from a profile they are reporting the listing and the
 * gig behind it, so "asked me to pay off-platform" — the failure mode a
 * no-payments matchmaker exists to police — leads.
 *
 * The strings go into `reports.reason` verbatim, so they are stable text and not
 * derived from a UI label.
 */
val ArtistReportReasons = listOf(
    "Asked me to pay off-platform",
    "Didn't show up",
    "Misleading profile or samples",
    "Harassment or abuse",
    "Something else",
)

@Singleton
class SupabaseReportsRepository @Inject constructor(
    private val client: SupabaseClient,
    private val prefs: AppPreferences,
) : ReportsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val localLogMutex = Mutex()

    override suspend fun reportConversation(
        threadId: String,
        reason: String,
        details: String?,
    ): ReportOutcome = insert(
        ReportInsert(
            threadId = threadId.lowercase(),
            reason = reason,
            details = details?.ifBlank { null },
            surface = "conversation",
        ),
    )

    override suspend fun reportArtist(
        artistId: String,
        reason: String,
        details: String?,
    ): ReportOutcome {
        val id = artistId.lowercase()
        return insert(
            ReportInsert(
                reportedUserId = id,
                artistId = id,
                reason = reason,
                details = details?.ifBlank { null },
                surface = "artist",
            ),
        )
    }

    private suspend fun insert(row: ReportInsert): ReportOutcome =
        try {
            client.from("reports").insert(row)
            ReportOutcome.Sent
        } catch (t: Throwable) {
            // Soft-fail, whatever the reason — missing table, RLS, offline. A
            // report must never throw into the chat UI (see the class doc), and
            // the local log is what keeps the failure from being silent.
            Timber.w(t, "reports insert failed — logging locally")
            appendLocal(row)
            ReportOutcome.Queued
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

class FakeReportsRepository(
    /**
     * What both methods answer. Flipping it to [ReportOutcome.Queued] is how a
     * test drives the offline copy on screen 56 without a network stack — the
     * Supabase twin reaches that branch through a caught throw, which a fake
     * cannot reproduce by throwing (the interface never throws by contract).
     */
    var outcome: ReportOutcome = ReportOutcome.Sent,
) : ReportsRepository {
    val conversation = mutableListOf<Triple<String, String, String?>>()
    val artists = mutableListOf<Triple<String, String, String?>>()

    override suspend fun reportConversation(
        threadId: String,
        reason: String,
        details: String?,
    ): ReportOutcome {
        conversation += Triple(threadId, reason, details)
        return outcome
    }

    override suspend fun reportArtist(
        artistId: String,
        reason: String,
        details: String?,
    ): ReportOutcome {
        artists += Triple(artistId, reason, details)
        return outcome
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
