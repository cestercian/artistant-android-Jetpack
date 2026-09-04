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
import `in`.artistant.app.platform.storage.KeyValueStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** One note the user wrote that has not reached `app_feedback` yet. */
@Serializable
data class PendingFeedback(
    val body: String,
    val isBug: Boolean,
    val writtenAtMs: Long,
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
 */
interface FeedbackOutbox {
    suspend fun pending(): List<PendingFeedback>
    suspend fun enqueue(note: PendingFeedback)

    /**
     * Try every queued note, dropping the ones that land.
     *
     * @return true when the queue is empty afterwards.
     */
    suspend fun drain(): Boolean
}

@Singleton
class DataStoreFeedbackOutbox @Inject constructor(
    private val store: KeyValueStore,
    private val bookings: BookingsRepository,
    @ApplicationContext private val context: Context,
) : FeedbackOutbox {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun pending(): List<PendingFeedback> = decode(store.getString(KEY).first())

    override suspend fun enqueue(note: PendingFeedback) {
        val queued = (decode(store.getString(KEY).first()) + note).takeLast(QUEUE_LIMIT)
        store.setString(KEY, json.encodeToString(queued))
        schedule()
    }

    override suspend fun drain(): Boolean {
        val queued = decode(store.getString(KEY).first())
        if (queued.isEmpty()) return true
        // Kept in order and stopped at the first failure. Sending the rest past a
        // failure would reorder the user's own notes, and a failure is almost
        // always the transport rather than the note — so the next one would fail
        // too, at the cost of one wasted round-trip each.
        var remaining = queued
        for (note in queued) {
            val sent = runCatching { bookings.submitFeedback(note.body, note.isBug) }
                .getOrElse { false }
            if (!sent) break
            remaining = remaining.drop(1)
        }
        store.setString(KEY, if (remaining.isEmpty()) "" else json.encodeToString(remaining))
        return remaining.isEmpty()
    }

    /**
     * Ask the OS to drain when there is a network.
     *
     * `KEEP`, not `REPLACE`: a second queued note while a drain is already
     * scheduled should join that drain, not restart its backoff.
     */
    private fun schedule() {
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
 * that keeps the process awake to do nothing.
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
