package `in`.artistant.app.platform.media

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.SamplesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serial upload drain — Android port of iOS UploadQueue.
 *
 * Persists pending + failed tasks to Application Support JSON so a process kill
 * doesn't strand wizard media. [resumeAfterLaunch] restarts the runner once auth
 * is ready (uploads need JWT). WorkManager kicks a one-shot worker after enqueue
 * so the OS can finish drains even if the process is backgrounded.
 *
 * Both halves of that snapshot are off the caller's thread — the read behind
 * [restored], the writes behind [persist] — so nothing here does file IO on the main
 * dispatcher. That makes the queue briefly "empty because not loaded yet", which is a
 * different thing from empty; [awaitRestore] is how a caller tells them apart.
 */
@Singleton
class UploadQueue @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val media: ArtistMediaRepository,
    private val samples: SamplesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    /**
     * Serialises snapshot writes. Deliberately NOT [mutex]: that one is held for a
     * whole drain, so sharing it would defer every write until the drain ended —
     * which is exactly when a crash has to find an up-to-date attempt counter.
     */
    private val diskMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val snapshotFile: File
        get() = File(context.filesDir, "upload-queue.json")

    sealed class Task {
        abstract val id: String
        abstract val artistId: String
        abstract val attempts: Int
        abstract fun withAttempt(n: Int): Task
        abstract fun toPersisted(): PersistedTask

        data class CoverPhoto(
            override val id: String = UUID.randomUUID().toString(),
            override val artistId: String,
            val filePath: String,
            override val attempts: Int = 0,
        ) : Task() {
            override fun withAttempt(n: Int) = copy(attempts = n)
            override fun toPersisted() = PersistedTask(
                id = id,
                kind = "cover_photo",
                artistId = artistId,
                filePath = filePath,
                attempts = attempts,
            )
        }

        data class AudioSample(
            override val id: String = UUID.randomUUID().toString(),
            override val artistId: String,
            val filePath: String,
            val title: String,
            val durationSeconds: Double,
            override val attempts: Int = 0,
        ) : Task() {
            override fun withAttempt(n: Int) = copy(attempts = n)
            override fun toPersisted() = PersistedTask(
                id = id,
                kind = "audio_sample",
                artistId = artistId,
                filePath = filePath,
                title = title,
                durationSeconds = durationSeconds,
                attempts = attempts,
            )
        }
    }

    data class State(
        val pending: List<Task> = emptyList(),
        val failed: List<Task> = emptyList(),
        val isRunning: Boolean = false,
        val batchTotal: Int = 0,
        val batchCompleted: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * Opens once the on-disk snapshot has been read back into [state].
     *
     * The read used to run straight in `init`, which put a file read on whichever
     * thread first injected this `@Singleton` — the main thread, during the first
     * frame. Moving it to [scope] means callers can now observe an empty queue that is
     * merely not loaded yet, and "nothing is queued" is a DESTRUCTIVE answer for the
     * wizard's orphan sweep, which deletes every staged file the queue doesn't claim.
     * So the read is awaitable ([awaitRestore]) and everything whose answer depends on
     * it waits: the drain ([pump]), the resume ([resumeAfterLaunch]), the snapshot
     * writer ([persistNow]) and the sweep. [clearAll] is the one that deliberately does
     * NOT wait — waiting would let the merge land after the wipe.
     */
    private val restored = CompletableDeferred<Unit>()

    init {
        scope.launch {
            try {
                restoreSnapshot()
            } finally {
                // In a `finally`: a restore that threw must not leave every upload
                // path parked on a gate that never opens.
                restored.complete(Unit)
            }
        }
    }

    /** Suspends until the snapshot read has landed. See [restored]. */
    suspend fun awaitRestore() {
        restored.await()
    }

    fun enqueueCoverPhoto(artistId: String, file: File) {
        enqueue(Task.CoverPhoto(artistId = artistId.lowercase(), filePath = file.absolutePath))
    }

    fun enqueueAudioSample(artistId: String, file: File, title: String, durationSeconds: Double) {
        enqueue(
            Task.AudioSample(
                artistId = artistId.lowercase(),
                filePath = file.absolutePath,
                title = title,
                durationSeconds = durationSeconds,
            ),
        )
    }

    private fun enqueue(task: Task) {
        _state.update {
            it.copy(
                pending = it.pending + task,
                batchTotal = if (it.pending.isEmpty() && !it.isRunning) 1 else it.batchTotal + 1,
            )
        }
        persist()
        scheduleWork()
        pump()
    }

    /**
     * Send everything the runner gave up on back round with a fresh attempt budget.
     *
     * Called from the EPK's stalled-upload banner (`EpkViewModel.retryFailedUploads`),
     * which is the only surface that reports `failed` — a burned task is otherwise
     * invisible, and a retry nobody can ask for is the same thing as no retry.
     */
    fun retryFailed() {
        // Read-modify-write on ONE value. The list used to be read before the
        // update and closed over inside it, so a task that burned its last
        // attempt in that gap was cleared by `failed = emptyList()` without ever
        // being added to `pending` from the stale capture — the drain reported a
        // failure and the queue silently ate the task.
        val before = _state.getAndUpdate { current ->
            if (current.failed.isEmpty()) {
                current
            } else {
                current.copy(
                    failed = emptyList(),
                    pending = current.pending + current.failed.map { it.withAttempt(0) },
                    batchTotal = current.batchTotal + current.failed.size,
                )
            }
        }
        if (before.failed.isEmpty()) return
        persist()
        scheduleWork()
        pump()
    }

    /**
     * Send ONE burned task back round — design screen 66's per-item Retry.
     *
     * Bulk retry alone is the wrong granularity for a stalled queue and it is the
     * only one the banner used to offer. Two uploads stall for different reasons
     * far more often than for the same one (a clip over the bucket's 10 MiB cap
     * beside a cover that hit a dead cell), so "Retry all" spends the network on
     * the task that is going to fail again either way. The sheet offers per-item
     * first and the bulk control second for exactly that reason.
     *
     * A no-op when the id names nothing failed, which is what a double-tap on a
     * row the previous tap already requeued looks like.
     */
    fun retryFailed(taskId: String) {
        // `getAndUpdate`, not read-then-assign. The drain writes this same
        // `_state` from an IO coroutine, so a completion landing between the read
        // and the assignment was overwritten by a snapshot taken before it —
        // resurrecting an uploaded task, or flipping `isRunning` back to true
        // over a drain that had just finished. `retryOne` is a no-op for an id
        // that names nothing, so it is safe to apply unconditionally and decide
        // afterwards, off the value the successful CAS actually replaced.
        val before = _state.getAndUpdate { retryOne(it, taskId) }
        if (before.failed.none { it.id == taskId }) return
        persist()
        scheduleWork()
        pump()
    }

    /**
     * Forget one burned task and delete its staged bytes — screen 66's Discard.
     *
     * The delete is the point. A discarded task that left its file behind would
     * leave a multi-megabyte copy in `filesDir` that nothing references and no
     * sweep on this screen's path claims, for a clip the artist has explicitly
     * said they no longer want. Same reasoning, and the same ordering, as
     * [clearAll]: drop it from state first so nothing can drain it, then unlink.
     */
    fun discardFailed(taskId: String) {
        // Two reads of `_state.value` and a bare assignment between them was
        // three chances to act on a value the drain had already replaced. One
        // atomic swap: `before` IS the snapshot that was retired, so the task
        // whose bytes we unlink is exactly the one this call removed.
        val before = _state.getAndUpdate { discardOne(it, taskId) }
        val dropped = before.failed.firstOrNull { it.id == taskId } ?: return
        persist()
        scope.launch {
            runCatching { dropped.stagedFile().delete() }
                .onFailure { Timber.w(it, "Discarded upload file delete failed: %s", dropped.id) }
        }
    }

    /**
     * Call once auth session is ready (RootViewModel). Restored poison tasks
     * with burned attempt budgets stay in failed; others re-enter the runner.
     *
     * Waits for [restored] first: this is the ONE caller whose whole job is to act on
     * what the snapshot held, so reading `pending` before the read lands would see an
     * empty queue and resume nothing at all.
     */
    fun resumeAfterLaunch() {
        scope.launch {
            awaitRestore()
            if (_state.value.pending.isEmpty()) return@launch
            scheduleWork()
            pump()
        }
    }

    /**
     * Drain to completion and report whether the queue emptied — [UploadDrainWorker]'s
     * entry point, and the reason the worker is worth having.
     *
     * The worker used to kick [resumeAfterLaunch] and return `success` on the next
     * line, so WorkManager marked the work done before the first byte moved and
     * withdrew exactly the process-lifetime guarantee the worker exists to buy.
     * Joining the drain's own job (rather than running it in the worker's coroutine)
     * keeps every drain on this singleton's scope: a worker that the OS stops
     * mid-upload stops waiting, it does not cancel the upload.
     *
     * [pump] waits for [restored], so a worker that runs before the snapshot read has
     * landed no longer reports a drain of a queue it never saw.
     */
    internal suspend fun drain(): Boolean {
        pump().join()
        return _state.value.pending.isEmpty()
    }

    /**
     * Forget every task, pending and failed — the sign-out / delete-account wipe,
     * called from `SessionManager.signOut` (iOS `UploadQueue.cancelAll`).
     *
     * Every task carries the artist id it was staged for, so leaving the snapshot
     * behind hands it to whoever signs in next: [resumeAfterLaunch] drains it, the
     * upload burns its three attempts against an RLS policy that correctly refuses
     * to let this account write another artist's media, and it lands in `failed` —
     * where the EPK offers the new artist a Retry for a cover they never picked. The
     * staged bytes go with it, because once the queue has forgotten a task nothing
     * references its file.
     *
     * An upload already in flight is not interrupted — its socket is not ours to
     * kill — but it cannot resurrect what this dropped: [uploadSucceeded] and
     * [uploadFailed] both refuse to write back once the head of the queue is no
     * longer their task.
     *
     * The wipe lands twice on purpose. Once here, on the caller's thread, because
     * `signOut` must not return while the departing account's tasks are still readable —
     * [resumeAfterLaunch] would drain them for whoever signs in next. Then again inside
     * [diskMutex], because [restoreSnapshot] is no longer synchronous with construction
     * and its merge would otherwise put them back: sharing the lock makes the FILE the
     * arbiter, so read-first means this overwrites what it merged and wipe-first means
     * it reads the empty snapshot left here and merges nothing. (A task the read merged
     * in that window loses its `state` row but not its staged bytes; the wizard's orphan
     * sweep is the backstop for staged files nothing claims.) Deletes are file IO and
     * sign-out runs on the main dispatcher, so they go to [scope] too.
     */
    fun clearAll() {
        val dropped = _state.value.let { it.pending + it.failed }
        _state.value = State()
        scope.launch {
            diskMutex.withLock {
                _state.value = State()
                writeSnapshot()
            }
            dropped.forEach { task ->
                runCatching { task.stagedFile().delete() }
                    .onFailure { Timber.w(it, "Staged upload file delete failed: %s", task.id) }
            }
        }
    }

    /** Every drain waits for [restored] — see [resumeAfterLaunch] for why. */
    private fun pump(): Job = scope.launch {
        awaitRestore()
        runDrain()
    }

    private suspend fun runDrain() {
        mutex.withLock {
            if (_state.value.isRunning) return@withLock
            _state.update { it.copy(isRunning = true) }
            persistNow()
            try {
                while (true) {
                    val next = _state.value.pending.firstOrNull() ?: break
                    // Offline, `execute` can only throw, and every throw costs the head
                    // task one of its three attempts. Stop rather than spend them:
                    // [scheduleWork]'s worker carries a CONNECTED constraint, so the OS
                    // restarts this drain when there is a network to upload over. Without
                    // this the in-process pump `enqueue` fires is ungated and burns the
                    // budget before the worker's constraint has said a word.
                    if (!isOnline()) break
                    // Crash-loop protection: bump attempts BEFORE execute so a
                    // hard crash doesn't infinite-retry the same poison asset.
                    val marked = next.withAttempt(next.attempts + 1)
                    _state.update {
                        it.copy(pending = listOf(marked) + it.pending.drop(1))
                    }
                    persistNow()
                    try {
                        execute(marked)
                        _state.update { uploadSucceeded(it, marked.id) }
                        persistNow()
                    } catch (t: Throwable) {
                        Timber.w(t, "Upload task failed: %s", marked.id)
                        _state.update { uploadFailed(it, marked, MAX_ATTEMPTS) }
                        persistNow()
                        // The gap before the next attempt of anything. See
                        // [retryDelayMillis] for why it can't be conditioned on the
                        // requeued task being back at the HEAD.
                        delay(retryDelayMillis(_state.value, marked))
                    }
                }
            } finally {
                val drained = _state.value.pending.isEmpty()
                _state.update {
                    it.copy(
                        isRunning = false,
                        batchTotal = if (drained) 0 else it.batchTotal,
                        batchCompleted = if (drained) 0 else it.batchCompleted,
                    )
                }
                persistNow()
            }
        }
    }

    /** The staged copy a task uploads — the one place the two kinds' paths converge. */
    private fun Task.stagedFile(): File = File(
        when (this) {
            is Task.CoverPhoto -> filePath
            is Task.AudioSample -> filePath
        },
    )

    /**
     * Whether an upload has any chance of landing.
     *
     * Fail-OPEN — no manager, or a read that throws, and we try anyway: the worst case
     * there is the behaviour we already had, whereas a false "offline" would stall the
     * queue for the life of the install. A null `activeNetwork` IS the platform
     * answering, so that one is believed.
     */
    private fun isOnline(): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return@runCatching true
        val network = cm.activeNetwork ?: return@runCatching false
        cm.getNetworkCapabilities(network)
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(true)

    private suspend fun execute(task: Task) {
        val file = task.stagedFile()
        if (!file.exists()) error("fileEvicted")
        when (task) {
            is Task.CoverPhoto -> media.uploadPhoto(file, task.artistId, position = 0)
            is Task.AudioSample -> samples.upload(
                audioFile = file,
                title = task.title,
                durationSeconds = task.durationSeconds,
                artistId = task.artistId,
            )
        }
    }

    /** Read and apply as one [diskMutex] section — see [clearAll] for what that settles. */
    private suspend fun restoreSnapshot() {
        diskMutex.withLock {
            val snap = readSnapshot() ?: return@withLock
            _state.update { restoredInto(it, snap, MAX_ATTEMPTS) }
        }
    }

    /**
     * The bytes back. Caller holds [diskMutex]. Null when nothing is staged or the file
     * won't parse — a corrupt snapshot must leave a usable queue, not a dead app.
     */
    private fun readSnapshot(): PersistedSnapshot? = runCatching {
        if (snapshotFile.exists()) {
            json.decodeFromString<PersistedSnapshot>(snapshotFile.readText())
        } else {
            null
        }
    }.getOrNull()

    /**
     * Queue a snapshot write on the IO scope — never on the caller's thread.
     *
     * [enqueue] and [retryFailed] are both reached from `viewModelScope`, i.e. the main
     * dispatcher, and each write is a full serialize plus a file write: the wizard's
     * publish step enqueues a cover and three samples in one loop, so this used to be
     * four blocking writes back to back during the "Going live" animation.
     */
    private fun persist() {
        scope.launch { persistNow() }
    }

    /**
     * The write itself. Each one re-reads the live state under [diskMutex], so two
     * writes can never interleave and a late one can never restore a state the queue
     * has already left — whichever runs last writes the truth, which is what makes
     * the fire-and-forget [persist] safe.
     */
    private suspend fun persistNow() {
        // Never write before the read. A snapshot written pre-restore holds only what
        // this process has enqueued since launch, so [restoreSnapshot] would read it
        // back and merge those same tasks in a second time — and if the write landed
        // first it would have already dropped the uploads a process kill stranded,
        // which is the one job the snapshot has.
        awaitRestore()
        diskMutex.withLock { writeSnapshot() }
    }

    /** The bytes. Caller holds [diskMutex]. */
    private fun writeSnapshot() {
        val snap = PersistedSnapshot(
            pending = _state.value.pending.map { it.toPersisted() },
            failed = _state.value.failed.map { it.toPersisted() },
        )
        runCatching {
            snapshotFile.writeText(json.encodeToString(snap))
        }.onFailure { Timber.w(it, "UploadQueue persist failed") }
    }

    private fun scheduleWork() {
        val req = OneTimeWorkRequestBuilder<UploadDrainWorker>()
            // Offline the drain can only fail, and every failure costs the head task
            // one of its three attempts — so the worker waits for a network instead
            // of burning the budget. WorkManager's default retry backoff
            // (exponential, 30s) covers the `Result.retry()` the worker returns when
            // tasks are still pending.
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            UNIQUE_WORK,
            ExistingWorkPolicy.KEEP,
            req,
        )
    }

    companion object {
        /** Internal so [uploadFailed]'s tests can pin the real budget, not a guess. */
        internal const val MAX_ATTEMPTS = 3
        private const val UNIQUE_WORK = "artistant.upload.drain"
    }
}

/**
 * The queue state after the head task's upload landed.
 *
 * Head identity is checked rather than assumed. [UploadQueue.clearAll] can empty the
 * queue while a task is mid-flight (sign-out, delete-account), and a blind `drop(1)`
 * would then discard whatever the next owner of the queue had put at the head and
 * count a completion for it. When the head is no longer this task, the outcome belongs
 * to a queue that no longer exists, so it is dropped.
 */
internal fun uploadSucceeded(state: UploadQueue.State, taskId: String): UploadQueue.State {
    if (state.pending.firstOrNull()?.id != taskId) return state
    return state.copy(
        pending = state.pending.drop(1),
        batchCompleted = state.batchCompleted + 1,
    )
}

/**
 * The queue state after the head task's upload threw.
 *
 * A task with attempts left goes to the BACK of the queue, so one asset that keeps
 * failing can't starve the rest of a batch; a task that has burned [maxAttempts] moves
 * to `failed`, where the EPK's stalled-upload banner can hand it to
 * [UploadQueue.retryFailed]. Same head-identity guard as [uploadSucceeded]: a queue
 * that was wiped mid-flight must not be repopulated by the failure of the upload it
 * abandoned, or the next account inherits a stranded task it never created.
 */
internal fun uploadFailed(
    state: UploadQueue.State,
    task: UploadQueue.Task,
    maxAttempts: Int,
): UploadQueue.State {
    if (state.pending.firstOrNull()?.id != task.id) return state
    val rest = state.pending.drop(1)
    return if (task.attempts < maxAttempts) {
        state.copy(pending = rest + task)
    } else {
        state.copy(pending = rest, failed = state.failed + task)
    }
}

/**
 * Move one burned task from `failed` back to the end of `pending`, with a fresh
 * attempt budget — the state half of [UploadQueue.retryFailed].
 *
 * Appended rather than pushed to the head: the artist retrying one item has said
 * nothing about the others, and jumping the queue would delay uploads that are
 * still working in favour of one that has already failed three times.
 *
 * `batchTotal` grows with it, so the "k of n" the banner reads counts the
 * requeued task rather than reporting a batch the queue has already outgrown.
 * Returns the SAME instance when nothing matched, so a caller can skip the
 * persist and the drain kick on a no-op.
 */
internal fun retryOne(state: UploadQueue.State, taskId: String): UploadQueue.State {
    val task = state.failed.firstOrNull { it.id == taskId } ?: return state
    return state.copy(
        failed = state.failed.filterNot { it.id == taskId },
        pending = state.pending + task.withAttempt(0),
        batchTotal = state.batchTotal + 1,
    )
}

/** Drop one burned task — the state half of [UploadQueue.discardFailed]. */
internal fun discardOne(state: UploadQueue.State, taskId: String): UploadQueue.State =
    state.copy(failed = state.failed.filterNot { it.id == taskId })

/**
 * How long the drain waits after [task]'s attempt failed, given the state that failure
 * produced.
 *
 * The gap is owed to the FAILED task, not to the head of the queue. Conditioning it on
 * "the requeued task came back to the head" only ever fires for a queue of one, and the
 * queue the artist actually has is the wizard's Publish: a cover plus every pending
 * sample, enqueued in one loop (`WizardViewModel.publish`). With two or more tasks the
 * head after any failure is always a DIFFERENT task, so a head-keyed gap never fires
 * and the drain rotates the whole batch through all [UploadQueue.MAX_ATTEMPTS] attempts
 * back to back — the original no-backoff defect verbatim, just with more tasks. Any
 * requeue therefore pauses the drain, whichever task it picks up next: the dominant
 * failure is "no network", which is a property of the phone rather than of one asset.
 *
 * Zero when the task was NOT requeued — it burned its budget and moved to `failed`, or
 * a sign-out wiped the queue under it. Nothing is owed a gap that isn't coming back.
 */
internal fun retryDelayMillis(state: UploadQueue.State, task: UploadQueue.Task): Long =
    if (state.pending.any { it.id == task.id }) backoffMillisFor(task.attempts) else 0L

/**
 * The ladder itself: 2s, then 8s.
 *
 * With no gap all [UploadQueue.MAX_ATTEMPTS] attempts are spent within milliseconds of
 * the first failure. The dominant failure here is "no network", which comes back in
 * seconds: an artist who hit Publish during a cell→wifi handover would otherwise watch
 * three instant failures burn the budget and permanently fail the cover, with the EPK's
 * Retry banner the only way back.
 */
internal fun backoffMillisFor(attempts: Int): Long = when (attempts) {
    1 -> 2_000L
    2 -> 8_000L
    else -> 30_000L
}

/**
 * The queue state after the on-disk snapshot is read back into it.
 *
 * MERGES rather than replaces, because the read is no longer synchronous with the
 * singleton's construction: a caller that enqueues in the same breath — the wizard's
 * Publish, one frame after the EPK first injects the queue — can already have put a
 * task in `pending`, and replacing would drop the cover it just staged. Restored tasks
 * were queued first, so they keep the head of the line.
 *
 * A restored task whose attempt counter is already spent goes straight to `failed`
 * rather than back into the runner: the counter is bumped BEFORE `execute`, so a
 * snapshot holding `attempts == maxAttempts` is the fingerprint of an asset that hard-
 * crashed the process three times, and the crash-loop guard is worth nothing if a
 * restart hands it a fresh budget.
 */
internal fun restoredInto(
    live: UploadQueue.State,
    snapshot: PersistedSnapshot,
    maxAttempts: Int,
): UploadQueue.State {
    val queued = snapshot.pending.mapNotNull { it.toTask() }
    val pending = queued.filter { it.attempts < maxAttempts }
    val failed = snapshot.failed.mapNotNull { it.toTask() } +
        queued.filter { it.attempts >= maxAttempts }
    return live.copy(
        pending = pending + live.pending,
        failed = failed + live.failed,
        batchTotal = live.batchTotal + pending.size,
    )
}

@Serializable
data class PersistedSnapshot(
    val pending: List<PersistedTask> = emptyList(),
    val failed: List<PersistedTask> = emptyList(),
)

@Serializable
data class PersistedTask(
    val id: String,
    val kind: String,
    @SerialName("artist_id") val artistId: String,
    @SerialName("file_path") val filePath: String,
    val title: String = "",
    @SerialName("duration_seconds") val durationSeconds: Double = 0.0,
    val attempts: Int = 0,
) {
    fun toTask(): UploadQueue.Task? = when (kind) {
        "cover_photo" -> UploadQueue.Task.CoverPhoto(
            id = id,
            artistId = artistId,
            filePath = filePath,
            attempts = attempts,
        )
        "audio_sample" -> UploadQueue.Task.AudioSample(
            id = id,
            artistId = artistId,
            filePath = filePath,
            title = title,
            durationSeconds = durationSeconds,
            attempts = attempts,
        )
        else -> null
    }
}

@HiltWorker
class UploadDrainWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val uploadQueue: UploadQueue,
) : CoroutineWorker(context, params) {
    /**
     * Awaits the drain, which is the whole point: WorkManager keeps the process alive
     * for as long as `doWork` is still running and not one millisecond longer, so the
     * old body — kick the pump, return `success` on the next line — bought the drain
     * nothing at all. `retry` when tasks are still pending hands the leftovers back to
     * the OS with its network constraint and backoff, instead of reporting a drain
     * that did not happen.
     */
    override suspend fun doWork(): Result =
        if (uploadQueue.drain()) Result.success() else Result.retry()
}
