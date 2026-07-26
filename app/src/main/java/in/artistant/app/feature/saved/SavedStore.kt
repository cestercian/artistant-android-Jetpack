package `in`.artistant.app.feature.saved

import `in`.artistant.app.data.repository.SavedArtistsRepository
import `in`.artistant.app.data.repository.SavedArtistsRepositoryError
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic saved-heart store — port of iOS `SavedStore`.
 * Local ids flip instantly; network add/remove is fire-and-forget per artist
 * (serialized so the final desired state wins a rapid toggle).
 */
@Singleton
class SavedStore @Inject constructor(
    private val repository: SavedArtistsRepository,
    private val prefs: AppPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    private val mutexes = mutableMapOf<String, Mutex>()
    private val jobs = mutableMapOf<String, Job>()

    init {
        scope.launch {
            val cached = prefs.getString(PREFS_KEY).first()
                ?.split(',')
                ?.map { it.trim().lowercase() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty()
            if (cached.isNotEmpty()) _ids.value = cached
            refreshFromServer()
        }
    }

    fun contains(artistId: String): Boolean = artistId.lowercase() in _ids.value

    fun toggle(artistId: String) {
        val id = artistId.lowercase()
        val adding = id !in _ids.value
        _ids.value = if (adding) _ids.value + id else _ids.value - id
        persistLocal()
        jobs[id]?.cancel()
        jobs[id] = scope.launch {
            mutexFor(id).withLock {
                // Re-read desired state after any superseded toggles.
                val wantSaved = id in _ids.value
                runCatching {
                    if (wantSaved) repository.add(id) else repository.remove(id)
                }
            }
        }
    }

    /** Replace UUID-shaped local set with server truth. Leave local alone on failure. */
    suspend fun refreshFromServer() {
        try {
            val remote = repository.list().map { it.lowercase() }.toSet()
            _ids.value = remote
            persistLocal()
        } catch (_: SavedArtistsRepositoryError.NotSignedIn) {
            // Keep local hearts until a session lands.
        } catch (_: Throwable) {
            // Network/RLS — leave local untouched (iOS contract).
        }
    }

    fun reset() {
        _ids.value = emptySet()
        persistLocal()
    }

    private fun persistLocal() {
        scope.launch {
            prefs.setString(PREFS_KEY, _ids.value.joinToString(","))
        }
    }

    private fun mutexFor(id: String): Mutex = synchronized(mutexes) {
        mutexes.getOrPut(id) { Mutex() }
    }

    companion object {
        const val PREFS_KEY = "saved.artistIds"
    }
}
