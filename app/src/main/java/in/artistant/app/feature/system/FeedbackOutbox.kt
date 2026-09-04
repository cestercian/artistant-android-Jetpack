package `in`.artistant.app.feature.system

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.feature.messages.ViewerIdentity
import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** One note the user wrote that has not reached `app_feedback` yet. */
@Serializable
data class PendingFeedback(
    val body: String,
    val isBug: Boolean,
    val writtenAtMs: Long,
    /**
     * The account that wrote it, lowercase — stamped by [FeedbackOutbox.enqueue].
     *
     * The queue outlives the session that filled it (that is its whole job), and
     * `submitFeedback` inserts as *whoever is signed in when it runs*. Without an
     * owner recorded here, a note typed by one account and drained after another
     * signs in is filed against the wrong `app_feedback.user_id` — an
     * unauthenticated mis-attribution the row can never be corrected out of,
     * because mig 0073 gives `app_feedback` no SELECT policy at all.
     *
     * Nullable only so a blob written before this field existed still decodes.
     * Such a row is dropped by the drain rather than sent.
     */
    val userId: String? = null,
    /**
     * Identity within the queue; [FeedbackOutbox.enqueue] assigns it.
     *
     * The drain marks notes sent BY ID rather than by position, so an enqueue
     * that lands while a drain is in flight cannot be dropped by the drain's
     * closing write.
     */
    val id: String = "",
)

/**
 * Notes typed while the insert could not land — the machinery behind screen 64's
 * promise: *"No connection? It queues on this device and sends on your next live
 * session."*
 *
 * That line is the reason this exists. It is on the design, it is a claim about
 * behaviour, and without a queue behind it the screen would be lying: the
 * repository's insert returns false on a transport failure and the note would be
 * gone. So a failed send is persisted here and drained by [FeedbackWorker], a
 * WorkManager job with a `CONNECTED` constraint — the OS wakes it on the next
 * live session, which is precisely what the copy says.
 *
 * `app_feedback` (mig 0073) is insert-only for `authenticated` with no SELECT
 * policy, so a queued note cannot be read back from the server to check. The
 * local copy IS the record until the insert succeeds.
 *
 * **Ownership rule.** A queued note belongs to the account that wrote it. The
 * drain submits only notes owned by the account signed in when it runs, drops
 * notes owned by anybody else, and does nothing at all when nobody is signed in.
 */
interface FeedbackOutbox {
    suspend fun pending(): List<PendingFeedback>

    /**
     * Queue [note] against the signed-in account.
     *
     * Dropped when there is no session: `app_feedback` is insert-only for
     * `authenticated`, so an unattributable note could never land anywhere, and
     * the screen that writes them lives behind the auth gate.
     */
    suspend fun enqueue(note: PendingFeedback)

    /**
     * Try every queued note THIS account owns, dropping the ones that land.
     *
     * @return true when the queue is empty afterwards.
     */
    suspend fun drain(): Boolean
}

/**
 * Ask the OS to drain when there is a network.
 *
 * A seam rather than a `WorkManager.getInstance(context)` call inside the outbox
 * so [DataStoreFeedbackOutbox] carries no Android `Context` and its ordering
 * guarantees can be pinned by a JVM test — the same trade
 * [`in`.artistant.app.feature.messages.ViewerIdentity] makes for the session.
 */
fun interface FeedbackDrainScheduler {
    fun schedule()
}

/**
 * The real scheduler.
 *
 * `KEEP`, not `REPLACE`: a second queued note while a drain is already scheduled
 * should join that drain, not restart its backoff.
 */
@Singleton
class WorkManagerFeedbackDrainScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : FeedbackDrainScheduler {
    override fun schedule() {
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                FeedbackWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FeedbackWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build(),
                    )
                    .build(),
            )
        }.onFailure { Timber.w(it, "Couldn't schedule the feedback drain") }
    }
}

@Singleton
class DataStoreFeedbackOutbox @Inject constructor(
    private val store: KeyValueStore,
    private val bookings: BookingsRepository,
    private val viewer: ViewerIdentity,
    private val scheduler: FeedbackDrainScheduler,
) : FeedbackOutbox {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * One transaction boundary for every mutation of the queue.
     *
     * The queue is a single DataStore string read and written whole, and it has
     * two independent writers: the composer's enqueue (a user pressing Send) and
     * the drain (the ViewModel on open, plus [FeedbackWorker] whenever the OS
     * decides the device is online). Unserialized, an enqueue and a drain that
     * overlap either resend a note the drain had already delivered or discard
     * one the enqueue had just added.
     *
     * The lock is held across the drain's network calls, deliberately. That is
     * what makes "no note is sent twice" true rather than nearly true — two
     * drains cannot both pick up the same note — and the cost is bounded: at
     * most [QUEUE_LIMIT] inserts, and a Send pressed during a drain waits for
     * it. The alternative (a second lock for single-flighting, plus removal by
     * id against a queue that moved underneath) buys latency nobody can perceive
     * at the price of an invariant nobody can check.
     */
    private val mutex = Mutex()

    override suspend fun pending(): List<PendingFeedback> = decode(store.getString(KEY).first())

    override suspend fun enqueue(note: PendingFeedback) {
        val owner = note.userId?.lowercase() ?: viewer.currentUserId() ?: run {
            Timber.i("Dropped a feedback note: no signed-in account to attribute it to")
            return
        }
        mutex.withLock {
            val stamped = note.copy(
                userId = owner,
                id = note.id.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            )
            val queued = (decode(store.getString(KEY).first()) + stamped).takeLast(QUEUE_LIMIT)
            store.setString(KEY, json.encodeToString(queued))
        }
        scheduler.schedule()
    }

    override suspend fun drain(): Boolean = mutex.withLock {
        val queued = decode(store.getString(KEY).first())
        if (queued.isEmpty()) return@withLock true

        // Nobody signed in: the insert would be rejected by RLS anyway, and
        // submitting somebody's note as an account that has not signed in yet is
        // the failure this ownership rule exists to prevent. Leave the queue
        // exactly as it is and report it undrained, so the worker's own backoff
        // brings it back.
        val owner = viewer.currentUserId() ?: return@withLock false

        // Somebody else's notes go, they do not travel. Sign-out wipes this
        // store (`AppPreferences.wipeAll`), so a foreign row can only be the
        // residue of a wipe that did not complete — and the safe direction for
        // an unattributable note is the floor, not another account's feedback.
        val mine = queued.filter { it.userId == owner }
        if (mine.size != queued.size) {
            Timber.i("Dropped ${queued.size - mine.size} queued note(s) owned by another account")
        }

        // Kept in order and stopped at the first failure. Sending the rest past a
        // failure would reorder the user's own notes, and a failure is almost
        // always the transport rather than the note — so the next one would fail
        // too, at the cost of one wasted round-trip each.
        var remaining = mine
        for (note in mine) {
            val sent = runCatching { bookings.submitFeedback(note.body, note.isBug) }
                .getOrElse { false }
            if (!sent) break
            // By id, never by position: the id is what survives a queue that
            // changed shape between the read and this write.
            remaining = remaining.filterNot { it.id == note.id }
        }
        store.setString(KEY, if (remaining.isEmpty()) "" else json.encodeToString(remaining))
        remaining.isEmpty()
    }

    private fun decode(raw: String?): List<PendingFeedback> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<PendingFeedback>>(raw) }
            .getOrElse {
                Timber.w(it, "Discarding an unreadable feedback queue")
                emptyList()
            }
    }

    private companion object {
        const val KEY = "system.feedbackOutbox"

        /**
         * A cap, because this is one DataStore string read and written whole. A
         * user with more than this many undelivered notes has a broken session,
         * not a backlog worth preserving.
         */
        const val QUEUE_LIMIT = 20
    }
}

/**
 * The drain, run by the OS when the device is next online.
 *
 * `Result.retry()` rather than `failure()` on a partial drain, so WorkManager's
 * own backoff owns the retry schedule — the alternative is a hand-rolled timer
 * that keeps the process awake to do nothing. A queue that cannot drain because
 * nobody is signed in retries the same way: the note is undelivered, which is
 * what `retry` means, and the next sign-in is exactly when it becomes sendable.
 */
@HiltWorker
class FeedbackWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outbox: FeedbackOutbox,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result =
        if (runCatching { outbox.drain() }.getOrElse { false }) Result.success() else Result.retry()

    companion object {
        const val WORK_NAME = "artistant.feedback.drain"
    }
}
