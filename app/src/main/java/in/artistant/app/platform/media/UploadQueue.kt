package `in`.artistant.app.platform.media

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
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serial upload drain — pragmatic Android port of iOS UploadQueue.
 * WorkManager can replace persistence later; for now a process-scoped queue with
 * retry is enough to finish wizard publish without blocking go-live.
 *
 * Go-live (artists.published) is ALWAYS sync in the wizard — this queue only
 * backfills cover photos + audio samples after the artist is already live.
 */
@Singleton
class UploadQueue @Inject constructor(
    private val media: ArtistMediaRepository,
    private val samples: SamplesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    sealed class Task {
        abstract val id: String
        abstract val artistId: String
        abstract val attempts: Int
        abstract fun withAttempt(n: Int): Task

        data class CoverPhoto(
            override val id: String = UUID.randomUUID().toString(),
            override val artistId: String,
            val file: File,
            override val attempts: Int = 0,
        ) : Task() {
            override fun withAttempt(n: Int) = copy(attempts = n)
        }

        data class AudioSample(
            override val id: String = UUID.randomUUID().toString(),
            override val artistId: String,
            val file: File,
            val title: String,
            val durationSeconds: Double,
            override val attempts: Int = 0,
        ) : Task() {
            override fun withAttempt(n: Int) = copy(attempts = n)
        }
    }

    data class State(
        val pending: List<Task> = emptyList(),
        val failed: List<Task> = emptyList(),
        val isRunning: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun enqueueCoverPhoto(artistId: String, file: File) {
        enqueue(Task.CoverPhoto(artistId = artistId.lowercase(), file = file))
    }

    fun enqueueAudioSample(artistId: String, file: File, title: String, durationSeconds: Double) {
        enqueue(
            Task.AudioSample(
                artistId = artistId.lowercase(),
                file = file,
                title = title,
                durationSeconds = durationSeconds,
            ),
        )
    }

    private fun enqueue(task: Task) {
        _state.update { it.copy(pending = it.pending + task) }
        pump()
    }

    fun retryFailed() {
        val failed = _state.value.failed
        if (failed.isEmpty()) return
        _state.update { it.copy(failed = emptyList(), pending = it.pending + failed.map { t -> t.withAttempt(0) }) }
        pump()
    }

    private fun pump() {
        scope.launch {
            mutex.withLock {
                if (_state.value.isRunning) return@withLock
                _state.update { it.copy(isRunning = true) }
                try {
                    while (true) {
                        val next = _state.value.pending.firstOrNull() ?: break
                        _state.update { it.copy(pending = it.pending.drop(1)) }
                        try {
                            execute(next)
                        } catch (t: Throwable) {
                            Timber.w(t, "Upload task failed: %s", next.id)
                            val attempt = next.attempts + 1
                            if (attempt < MAX_ATTEMPTS) {
                                _state.update { it.copy(pending = it.pending + next.withAttempt(attempt)) }
                            } else {
                                _state.update { it.copy(failed = it.failed + next.withAttempt(attempt)) }
                            }
                        }
                    }
                } finally {
                    _state.update { it.copy(isRunning = false) }
                }
            }
        }
    }

    private suspend fun execute(task: Task) {
        when (task) {
            is Task.CoverPhoto -> {
                media.uploadPhoto(task.file, task.artistId, position = 0)
            }
            is Task.AudioSample -> {
                samples.upload(
                    audioFile = task.file,
                    title = task.title,
                    durationSeconds = task.durationSeconds,
                    artistId = task.artistId,
                )
            }
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
    }
}
