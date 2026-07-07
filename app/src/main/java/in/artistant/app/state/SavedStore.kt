package `in`.artistant.app.state

import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.data.repository.SavedArtistsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide saved-artists state (iOS `SavedStore`). A `@Singleton` — NOT a
 * per-screen ViewModel — so a heart toggled on a Discover tile and the same
 * artist's ArtistProfile save button stay in lockstep (both collect [ids]).
 *
 * [toggle] is OPTIMISTIC: the local set flips instantly (UI updates), then a
 * fire-and-forget repository write follows. Writes for the SAME id are chained
 * so rapid off→on→off taps commit in submission order (iOS audit P3 — otherwise
 * the add/remove awaits could race and leave the server out of sync with the
 * user's final intent). Network failures are swallowed: local state is the
 * source of truth for a wishlist gesture, and [refreshFromServer] reconciles.
 */
@Singleton
class SavedStore @Inject constructor(
    private val repository: SavedArtistsRepository,
) {
    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    // Long-lived scope for the optimistic writes; SupervisorJob so one failed
    // write doesn't tear down siblings.
    private val scope = CoroutineScope(SupervisorJob())
    private val inFlight = mutableMapOf<String, Job>()

    fun contains(id: String): Boolean = _ids.value.contains(id.lowercaseUuid())

    /** Optimistic local flip + serialized remote write. */
    fun toggle(id: String) {
        val key = id.lowercaseUuid()
        _ids.update { current -> if (current.contains(key)) current - key else current + key }
        // Chain after any in-flight write for this id, then push the CURRENT
        // desired state (read fresh below) so the last intent wins and commits
        // land in submission order.
        val prior = inFlight[key]
        inFlight[key] = scope.launch {
            prior?.join()
            runCatching {
                if (_ids.value.contains(key)) repository.add(key) else repository.remove(key)
            }
        }
    }

    /** Cross-device sync — replace the set with the server's saved ids. */
    suspend fun refreshFromServer() {
        runCatching { repository.list() }
            .onSuccess { remote -> _ids.value = remote.map { it.lowercaseUuid() }.toSet() }
    }
}
