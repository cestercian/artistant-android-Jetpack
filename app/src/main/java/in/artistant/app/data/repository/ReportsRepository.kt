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
 * What actually became of a report.
 *
 * The distinction is the whole of screen 56's note ("says queued, not
 * received"). The insert soft-fails by design — a report must never throw into
 * the UI — and before this type existed the caller could not tell a landed
 * report from a logged one, so the only copy it could honestly show was the
 * weaker of the two for both.
 *
 * Three values, not two, because the fallback can fail as well: the local log is
 * a DataStore write, and a write that throws leaves NOTHING holding the report.
 * [Queued] is claimed only once that write has returned; anything else is
 * [Failed]. Telling someone their safety report is "queued on this device" when
 * the device had just dropped it is the worst thing this type can say, and it is
 * what the code said before [Failed] existed.
 */
enum class ReportOutcome {
    /** The row reached `public.reports`. */
    Sent,

    /**
     * The insert did not land and the report IS in this install's local log —
     * for this account, on this device, waiting for a path to the server.
     */
    Queued,

    /** Nothing holds the report. The caller owes the reader a retry. */
    Failed,
}

/**
 * A report the reader wrote that nothing is currently holding.
 *
 * Carried so a retry re-files exactly what they typed. Both report surfaces own
 * a form whose own state is `rememberSaveable` and dies with the sheet, so
 * without this the only recovery from [ReportOutcome.Failed] would be "write it
 * again" — about something that already upset them enough to report.
 *
 * It lives beside [ReportOutcome] rather than in either feature because it is
 * the other half of the same answer: the outcome says nothing holds the report,
 * and this is the report nothing holds.
 */
data class PendingReport(val reason: String, val details: String?)

/**
 * Filing a report, as a state machine — **one** of them, for both surfaces.
 *
 * The artist profile (screen 56) and the chat details sheet (73) file the same row through
 * the same repository and owe the reader the same three answers, and they each grew their own
 * copy of this: two in-flight guards, two generation counters, two settle branches. They had
 * already drifted — one kept the failure banner up across a retry, the other blinked it out —
 * and the tests only held one of them. So the rules live here, once, with the outcome type
 * they are about.
 *
 * It lives in `data/repository` rather than in either feature for the same reason
 * [PendingReport] does: it is the shape of an answer this layer gives, not a screen's idea
 * about it. Nothing in it is Compose- or Android-flavoured, so it is a JVM test away from
 * every rule below — which is the point, since neither ViewModel can be built in a unit test.
 *
 * @property inFlight an attempt is out. The guard: a second tap is swallowed rather than
 *   filing a second row in `public.reports` against one person for one incident, which the
 *   moderation queue reads as a pattern.
 * @property failed a report nothing is holding — the insert failed AND so did the local log.
 *   Durable state with the reader's own words kept for the retry, never a toast: "your safety
 *   report was lost" must not fade after three seconds.
 * @property outcome the momentary receipt, for the surface that renders one in place (chat).
 *   The artist profile raises its receipt as a toast on the app's single host and leaves this
 *   null; [ReportOutcome.Failed] never lands here, it lands in [failed].
 * @property generation stamps the attempt. A completion that is not the current generation
 *   claims nothing — see [settling].
 */
data class ReportSubmission(
    val inFlight: Boolean = false,
    val failed: PendingReport? = null,
    val outcome: ReportOutcome? = null,
    val generation: Int = 0,
) {

    /**
     * The state an attempt STARTS from, or null when the tap must be swallowed.
     *
     * Null is the in-flight guard, and returning it rather than mutating is what lets a test
     * state "a double tap files once" as a two-call sequence.
     *
     * **[failed] is carried through unchanged** — the rule the two surfaces disagreed about.
     * The profile kept it, the chat cleared it, and keeping it is right: on a first submit it
     * is already null, and on a retry it is the banner the reader is looking at. Dropping it
     * for the length of the round trip made that banner blink out and — when the retry failed
     * too — come straight back. What the reader sees instead is the same banner with its
     * action locked and saying "Sending report…" (`Banner`'s `actionEnabled`).
     *
     * A standing [outcome] IS cleared: a receipt for the previous attempt sitting over a new
     * one is a claim about a report that has not landed yet.
     */
    fun starting(): ReportSubmission? =
        if (inFlight) null else copy(inFlight = true, outcome = null, generation = generation + 1)

    /**
     * The state a finished attempt lands on — **or the state untouched**, if the attempt
     * finishing is no longer the current one.
     *
     * A stale completion changes NOTHING, and that includes [inFlight]. Releasing the flag
     * "because it belongs to the attempt that is finishing" was wrong on both counts: the
     * flag belongs to whatever is in flight NOW, and after a discard-then-new-report the
     * answer to that is the new report. The old attempt's late completion unlocked the new
     * one's duplicate guard mid-flight, and a second tap on Submit filed a second row in
     * `public.reports`.
     *
     * The fear that motivated the release — a stale completion leaving the form wedged shut
     * — is answered by the two things that can make a completion stale in the first place.
     * A later attempt owns the flag and will release it when IT settles. A discard releases
     * it in [dismissing]. And [retired] happens at `onCleared`, where there is no form left
     * to wedge.
     *
     * Claiming an OUTCOME is refused for the same reason: a report the reader has moved on
     * from must not re-raise the banner they dismissed.
     *
     * @param pending what was filed, kept for the retry if it turns out nothing holds it.
     */
    fun settling(outcome: ReportOutcome, pending: PendingReport, generation: Int): ReportSubmission {
        if (generation != this.generation) return this
        val settled = copy(inFlight = false)
        return if (outcome == ReportOutcome.Failed) {
            settled.copy(failed = pending, outcome = null)
        } else {
            // A retry that lands is the end of the loss it retried, so it takes the banner
            // down as well as raising its receipt.
            settled.copy(failed = null, outcome = outcome)
        }
    }

    /**
     * Give up on a lost report — the banner's "Discard".
     *
     * Retires whatever is in flight (the bumped generation makes its completion inert) AND
     * releases [inFlight]. Leaving the flag set was a real bug: the abandoned attempt's
     * completion is ignored BY that same stamp, so nothing else would ever clear it, and the
     * next report the reader wrote — from the sheet, about someone else — was swallowed by an
     * in-flight guard held by an attempt no one was waiting for.
     */
    fun dismissing(): ReportSubmission =
        copy(inFlight = false, failed = null, generation = generation + 1)

    /**
     * The screen is gone: retire the attempt without touching what is on screen.
     *
     * `viewModelScope` is cancelled at `onCleared`, but a pass already past its last
     * suspension point runs on to its writes regardless.
     */
    fun retired(): ReportSubmission = copy(generation = generation + 1)
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
            // The fallback gets its own guard. `appendLocal` is a DataStore
            // read-modify-write and it can throw — a corrupt file, a full disk,
            // an IO error — and when it does, nothing anywhere holds the report.
            // This branch used to let that throw escape into the caller's
            // `runCatching`, which then told the reader their report was queued
            // on a device that had just dropped it.
            runCatching { appendLocal(row) }.fold(
                onSuccess = { ReportOutcome.Queued },
                onFailure = { local ->
                    Timber.e(local, "reports local log failed — the report is lost")
                    ReportOutcome.Failed
                },
            )
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
     * What both methods answer. Flipping it is how a test drives screen 56's
     * offline ([ReportOutcome.Queued]) and lost ([ReportOutcome.Failed]) copy
     * without a network stack or a DataStore — the Supabase twin reaches both
     * branches through caught throws, which a fake cannot reproduce by throwing
     * (the interface never throws by contract).
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
