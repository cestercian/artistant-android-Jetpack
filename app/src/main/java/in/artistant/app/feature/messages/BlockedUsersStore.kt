package `in`.artistant.app.feature.messages

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.data.repository.BlockRepository
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The set of people the viewer has blocked, shared across the messages surfaces.
 *
 * A store rather than a plain repository call because two screens need the same
 * answer at the same instant: blocking happens in the chat's details sheet, and
 * the INBOX is where the block visibly takes effect (migration 0087 is enforced
 * client-side by filtering those conversations out). If each screen fetched its
 * own copy, blocking someone would appear to do nothing until the inbox was
 * pulled to refresh. This is the same reason [ThreadFlagsStore] is a store.
 *
 * Offline-first, like the flags: the set is mirrored to disk so a cold start
 * with no network still hides the right conversations. The server copy wins when
 * it arrives, but a FAILED fetch never does — see [refresh].
 */
interface BlockedUsersStore {
    /** Blocked user ids, lowercased. Emits on every local change. */
    val blocked: StateFlow<Set<String>>

    /** Best-effort reconcile from the server. Never widens what is visible on failure. */
    suspend fun refresh()

    /**
     * Block or unblock, optimistically. Returns false when the server write
     * failed and the set was rolled back, so the caller can say so.
     */
    suspend fun toggle(userId: String): Boolean
}

/**
 * The single DataStore slot the offline mirror lives in.
 *
 * Its own two-method seam because the store's failure behaviour is the part of
 * blocking most worth testing — a failed fetch must not clear the set, a failed
 * write must roll back — and [AppPreferences] needs a Context, so without this
 * that logic would only be reachable from an instrumented test. Same motivation
 * as [ViewerIdentity]: take the narrow slice, keep the ViewModels and stores
 * constructible in a plain JVM test.
 */
interface BlockedUsersMirror {
    suspend fun read(): String?
    suspend fun write(value: String)
}

class PreferencesBlockedUsersMirror(private val prefs: AppPreferences) : BlockedUsersMirror {
    override suspend fun read(): String? = prefs.getString(KEY).first()
    override suspend fun write(value: String) = prefs.setString(KEY, value)

    private companion object {
        const val KEY = "messages.blockedUsers"
    }
}

/**
 * [BlockedUsersStore] over [BlockRepository] plus a DataStore mirror.
 *
 * The mirror is stamped with the OWNER's uid and ignored when it doesn't match
 * the current session. Sign-out already wipes this DataStore, so in the normal
 * path the stamp is redundant — it exists for the path where it isn't: a
 * blocked-list mirror inherited by the next person to sign in on a shared device
 * would silently hide their own conversations, with no UI anywhere that could
 * explain why. A trust & safety set is exactly the wrong thing to leak sideways.
 */
@Singleton
class ServerBlockedUsersStore(
    private val repository: BlockRepository,
    private val mirror: BlockedUsersMirror,
    private val viewer: ViewerIdentity,
) : BlockedUsersStore {

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    override val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    /** The disk mirror is read once per process; after that memory is the truth. */
    private var hydrated = false

    override suspend fun refresh() {
        if (!hydrated) {
            hydrated = true
            _blocked.value = readLocal()
        }
        // Deliberately no `onFailure` branch. Signed out, offline, or a table
        // that hasn't been applied to this project yet all land here, and in
        // every one of those cases the honest answer is "I don't know", not
        // "nobody is blocked" — treating a failure as an empty set would un-hide
        // every blocked conversation the moment the network hiccuped.
        runCatching { repository.listBlocked() }
            .onSuccess { server ->
                _blocked.value = server
                persist(server)
            }
    }

    override suspend fun toggle(userId: String): Boolean {
        val id = userId.lowercase()
        val wasBlocked = id in _blocked.value
        update(if (wasBlocked) _blocked.value - id else _blocked.value + id)

        val wrote = runCatching {
            if (wasBlocked) repository.unblock(id) else repository.block(id)
        }
        if (wrote.isFailure) {
            // Recomputed from the CURRENT set rather than the pre-toggle
            // snapshot: another id may have been toggled while this write was in
            // flight, and reverting to the snapshot would undo that too.
            update(if (wasBlocked) _blocked.value + id else _blocked.value - id)
            return false
        }
        return true
    }

    private suspend fun update(ids: Set<String>) {
        _blocked.value = ids
        persist(ids)
    }

    /**
     * Stored as `owner\nid\nid…`. Ids are server UUIDs and the owner is one too,
     * so no value can contain the separator and the encoding needs no escaping —
     * the same reasoning that keeps [PreferencesThreadFlagsStore] off JSON.
     */
    private suspend fun persist(ids: Set<String>) {
        val owner = viewer.currentUserId()?.lowercase() ?: return
        mirror.write((listOf(owner) + ids).joinToString(SEPARATOR))
    }

    private suspend fun readLocal(): Set<String> {
        val owner = viewer.currentUserId()?.lowercase() ?: return emptySet()
        val parts = mirror.read()?.split(SEPARATOR)?.filter { it.isNotBlank() }
            ?: return emptySet()
        // First field is the owner; a mismatch means this mirror belongs to
        // somebody else who used this device, so it is not ours to act on.
        if (parts.firstOrNull() != owner) return emptySet()
        return parts.drop(1).toSet()
    }

    private companion object {
        const val SEPARATOR = "\n"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object BlockedUsersModule {
    @Provides
    @Singleton
    fun provideBlockedUsersStore(
        repository: BlockRepository,
        prefs: AppPreferences,
        viewer: ViewerIdentity,
    ): BlockedUsersStore =
        ServerBlockedUsersStore(repository, PreferencesBlockedUsersMirror(prefs), viewer)
}
