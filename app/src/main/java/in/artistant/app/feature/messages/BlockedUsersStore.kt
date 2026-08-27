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

    /**
     * Best-effort reconcile from the server. Never widens what is visible on failure.
     *
     * Returns true when the server copy was actually read, i.e. when [blocked] is
     * now authoritative. False means the set is whatever we last knew — signed
     * out, offline, or a project without 0087 applied — and a caller that is
     * about to RENDER the set has to say so, because an empty set that failed to
     * load and an empty set that loaded look identical and mean opposite things.
     * The inbox ignores this (it filters with whatever it has and a stale block
     * is a safe stale); the blocked-accounts screen cannot.
     */
    suspend fun refresh(): Boolean

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
 * Both copies of the set — the one on disk and the one in memory — are stamped
 * with the OWNER's uid and dropped when it doesn't match the current session.
 * Sign-out already wipes this DataStore, so for the disk copy the stamp is
 * belt-and-braces; for the memory copy it is the only guard there is, because
 * this is a @Singleton and signing out does not restart the process. A
 * blocked-list inherited by the next person to sign in on a shared device would
 * silently hide their own conversations, with no UI anywhere that could explain
 * why. A trust & safety set is exactly the wrong thing to leak sideways.
 */
@Singleton
class ServerBlockedUsersStore(
    private val repository: BlockRepository,
    private val mirror: BlockedUsersMirror,
    private val viewer: ViewerIdentity,
) : BlockedUsersStore {

    private val _blocked = MutableStateFlow<Set<String>>(emptySet())
    override val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()

    /**
     * Whose set is in memory. The disk mirror is read once per ACCOUNT; after
     * that memory is the truth, until the session changes underneath it.
     *
     * Keyed on the uid rather than a plain "have I read disk yet" flag for two
     * reasons. Sign-out wipes the prefs but cannot wipe this object, so without
     * the stamp the next account signed in on the device filters ITS inbox with
     * the previous account's ids — and the first block it performs would then
     * persist that inherited set back to disk under its own name. And on a cold
     * start the first hydrate can land while the session is still being
     * restored, which used to latch an empty set for the whole process; a uid
     * change now re-reads instead.
     */
    private var owner: String? = null

    override suspend fun refresh(): Boolean {
        hydrate()
        // Deliberately no `onFailure` branch. Signed out, offline, or a table
        // that hasn't been applied to this project yet all land here, and in
        // every one of those cases the honest answer is "I don't know", not
        // "nobody is blocked" — treating a failure as an empty set would un-hide
        // every blocked conversation the moment the network hiccuped.
        //
        // The failure is REPORTED rather than acted on: the set stays as it was,
        // and the caller decides whether its screen can honestly claim to be
        // showing the whole list.
        return runCatching { repository.listBlocked() }
            .onSuccess { server ->
                _blocked.value = server
                persist(server)
            }
            .isSuccess
    }

    override suspend fun toggle(userId: String): Boolean {
        // Before touching the set, make sure it is this account's set: a toggle
        // is the one path that WRITES memory back to disk, so acting on an
        // inherited set here is what would make the leak permanent.
        hydrate()
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
     * Adopt the current session's set, re-reading the mirror whenever the
     * session that owns the in-memory copy is not the one asking. A no-op on
     * every call after the first for a given account, so this stays the
     * "read disk once" path it always was.
     *
     * Signed out resolves to the empty set rather than to whatever was last
     * held: there is no inbox to filter while signed out, and holding somebody
     * else's ids across the gap is the whole failure this guards.
     */
    private suspend fun hydrate() {
        val viewerId = viewer.currentUserId()?.lowercase()
        if (viewerId == owner) return
        owner = viewerId
        // The old set goes BEFORE the read, not after it. This is a @Singleton, so
        // a hydrate that never finishes must not leave one account's ids in memory
        // stamped with the next account's name.
        _blocked.value = emptySet()
        // And a mirror that will not read is an unknown, not a crash. DataStore
        // throws IOException on a damaged preferences file, and this call sits on
        // the path of every [refresh] — including the one ChatViewModel makes from
        // a bare launch, where an escaping throw is an unhandled coroutine
        // exception rather than a message that failed to load.
        //
        // The unreadable case is treated as "nothing known" and NOT retried later:
        // what corrects it is the server's copy, which [refresh] is about to ask
        // for, and re-reading disk on a later call would either clobber that answer
        // or need a merge nothing else in this class does.
        _blocked.value = runCatching { readLocal(viewerId) }.getOrDefault(emptySet())
    }

    /**
     * Stored as `owner\nid\nid…`. Ids are server UUIDs and the owner is one too,
     * so no value can contain the separator and the encoding needs no escaping —
     * the same reasoning that keeps [PreferencesThreadFlagsStore] off JSON.
     */
    private suspend fun persist(ids: Set<String>) {
        // The LIVE session stamps the write, never the cached [owner]: a mirror
        // is only ever ours to claim for whoever is signed in right now.
        val ownerId = viewer.currentUserId()?.lowercase() ?: return
        // Guarded for the same reason the read is: losing the offline copy costs a
        // cold start its head start, while letting the failure out of here would
        // take down a block that the SERVER already accepted — memory and the
        // server would agree, and only the caller would be told it failed.
        runCatching { mirror.write((listOf(ownerId) + ids).joinToString(SEPARATOR)) }
    }

    private suspend fun readLocal(viewerId: String?): Set<String> {
        if (viewerId == null) return emptySet()
        val parts = mirror.read()?.split(SEPARATOR)?.filter { it.isNotBlank() }
            ?: return emptySet()
        // First field is the owner; a mismatch means this mirror belongs to
        // somebody else who used this device, so it is not ours to act on.
        if (parts.firstOrNull() != viewerId) return emptySet()
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
