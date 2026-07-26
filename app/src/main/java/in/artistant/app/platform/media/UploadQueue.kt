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

    fun clearAll() {
        _state.value = State()
        persist()
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
                            _state.update {
                                it.copy(
                                    pending = it.pending.drop(1),
                                    batchCompleted = it.batchCompleted + 1,
                                )
                            }
                            persist()
                        } catch (t: Throwable) {
                            Timber.w(t, "Upload task failed: %s", marked.id)
                            _state.update { st ->
                                val rest = st.pending.drop(1)
                                if (marked.attempts < MAX_ATTEMPTS) {
                                    st.copy(pending = rest + marked)
                                } else {
                                    st.copy(pending = rest, failed = st.failed + marked)
                                }
                            }
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

    private suspend fun execute(task: Task) {
        val file = File(
            when (task) {
                is Task.CoverPhoto -> task.filePath
                is Task.AudioSample -> task.filePath
            },
        )
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
        private const val MAX_ATTEMPTS = 3
        private const val UNIQUE_WORK = "artistant.upload.drain"
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
