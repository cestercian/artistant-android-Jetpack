package `in`.artistant.app.platform.media

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.SamplesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
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
 */
@Singleton
class UploadQueue @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val media: ArtistMediaRepository,
    private val samples: SamplesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
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

    init {
        restoreSnapshot()
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
        val failed = _state.value.failed
        if (failed.isEmpty()) return
        _state.update {
            it.copy(
                failed = emptyList(),
                pending = it.pending + failed.map { t -> t.withAttempt(0) },
                batchTotal = it.batchTotal + failed.size,
            )
        }
        persist()
        scheduleWork()
        pump()
    }

    /**
     * Call once auth session is ready (RootViewModel). Restored poison tasks
     * with burned attempt budgets stay in failed; others re-enter the runner.
     */
    fun resumeAfterLaunch() {
        if (_state.value.pending.isNotEmpty()) {
            scheduleWork()
            pump()
        }
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
     */
    fun clearAll() {
        val dropped = _state.value.let { it.pending + it.failed }
        _state.value = State()
        persist()
        if (dropped.isEmpty()) return
        // Deletes are file IO and sign-out runs on the main dispatcher.
        scope.launch {
            dropped.forEach { task ->
                runCatching { task.stagedFile().delete() }
                    .onFailure { Timber.w(it, "Staged upload file delete failed: %s", task.id) }
            }
        }
    }

    private fun pump() {
        scope.launch {
            mutex.withLock {
                if (_state.value.isRunning) return@withLock
                _state.update { it.copy(isRunning = true) }
                persist()
                try {
                    while (true) {
                        val next = _state.value.pending.firstOrNull() ?: break
                        // Crash-loop protection: bump attempts BEFORE execute so a
                        // hard crash doesn't infinite-retry the same poison asset.
                        val marked = next.withAttempt(next.attempts + 1)
                        _state.update {
                            it.copy(pending = listOf(marked) + it.pending.drop(1))
                        }
                        persist()
                        try {
                            execute(marked)
                            _state.update { uploadSucceeded(it, marked.id) }
                            persist()
                        } catch (t: Throwable) {
                            Timber.w(t, "Upload task failed: %s", marked.id)
                            _state.update { uploadFailed(it, marked, MAX_ATTEMPTS) }
                            persist()
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
                    persist()
                }
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

    private fun restoreSnapshot() {
        val snap = runCatching {
            if (!snapshotFile.exists()) return
            json.decodeFromString<PersistedSnapshot>(snapshotFile.readText())
        }.getOrNull() ?: return
        val pending = snap.pending.mapNotNull { it.toTask() }
            .filter { it.attempts < MAX_ATTEMPTS }
        val burned = snap.pending.mapNotNull { it.toTask() }
            .filter { it.attempts >= MAX_ATTEMPTS }
        val failed = snap.failed.mapNotNull { it.toTask() } + burned
        _state.value = State(
            pending = pending,
            failed = failed,
            batchTotal = pending.size,
            batchCompleted = 0,
        )
    }

    private fun persist() {
        val snap = PersistedSnapshot(
            pending = _state.value.pending.map { it.toPersisted() },
            failed = _state.value.failed.map { it.toPersisted() },
        )
        runCatching {
            snapshotFile.writeText(json.encodeToString(snap))
        }.onFailure { Timber.w(it, "UploadQueue persist failed") }
    }

    private fun scheduleWork() {
        val req = OneTimeWorkRequestBuilder<UploadDrainWorker>().build()
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
    override suspend fun doWork(): Result {
        uploadQueue.resumeAfterLaunch()
        // Give the in-process pump a moment; WorkManager's job is the kick, not the drain.
        return Result.success()
    }
}
