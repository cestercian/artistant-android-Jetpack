package `in`.artistant.app.feature.messages

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

/**
 * The four per-conversation flags the inbox lets a person set.
 *
 * All four are sets of thread ids rather than columns on the thread, because none
 * of them is a fact about the conversation — they are facts about how *this
 * reader* wants to see it.
 */
data class ThreadFlags(
    val starred: Set<String> = emptySet(),
    val archived: Set<String> = emptySet(),
    /** Threads explicitly marked unread again after being opened. */
    val markedUnread: Set<String> = emptySet(),
    /** Threads whose chat safety notice has been dismissed. */
    val safetyDismissed: Set<String> = emptySet(),
)

/**
 * Device-local conversation flags.
 *
 * Deliberately a narrow seam rather than direct [AppPreferences] use: the
 * ViewModels stay constructible in a plain JVM test (no Context, no DataStore),
 * which is the same reason [ViewerIdentity] exists.
 *
 * These are honestly device-local and the UI should not imply otherwise — star a
 * thread on a phone and it is not starred on the web. The upgrade path is server
 * columns on `threads`; until those exist, persisting locally is the difference
 * between a control that survives relaunch and one that silently forgets.
 */
interface ThreadFlagsStore {
    val flags: Flow<ThreadFlags>

    suspend fun toggleStarred(threadId: String)
    suspend fun toggleArchived(threadId: String)

    /** Explicitly re-unread a thread (details sheet). */
    suspend fun markUnread(threadId: String)

    /** Opening a thread clears the explicit mark — the reader has now seen it. */
    suspend fun clearMarkedUnread(threadId: String)

    suspend fun dismissSafetyBanner(threadId: String)
}

/**
 * [ThreadFlagsStore] over the app's DataStore.
 *
 * Sets are stored as newline-joined id lists. Thread ids are server UUIDs, so no
 * id can contain the separator and the encoding needs no escaping — worth stating
 * because that assumption is what keeps this from needing JSON.
 *
 * Read-modify-write is not atomic across the two DataStore calls. That is
 * acceptable here and nowhere else in the app: every write originates from a
 * single user tap on a single screen, so there is no concurrent writer to lose a
 * toggle to.
 */
@Singleton
class PreferencesThreadFlagsStore(
    private val prefs: AppPreferences,
) : ThreadFlagsStore {

    override val flags: Flow<ThreadFlags> = combine(
        prefs.getString(KEY_STARRED).map(::decode),
        prefs.getString(KEY_ARCHIVED).map(::decode),
        prefs.getString(KEY_UNREAD).map(::decode),
        prefs.getString(KEY_SAFETY).map(::decode),
    ) { starred, archived, unread, safety ->
        ThreadFlags(starred, archived, unread, safety)
    }

    override suspend fun toggleStarred(threadId: String) = toggle(KEY_STARRED, threadId)

    override suspend fun toggleArchived(threadId: String) = toggle(KEY_ARCHIVED, threadId)

    override suspend fun markUnread(threadId: String) = add(KEY_UNREAD, threadId)

    override suspend fun clearMarkedUnread(threadId: String) = remove(KEY_UNREAD, threadId)

    override suspend fun dismissSafetyBanner(threadId: String) = add(KEY_SAFETY, threadId)

    private suspend fun toggle(key: String, threadId: String) {
        val current = read(key)
        write(key, if (threadId in current) current - threadId else current + threadId)
    }

    private suspend fun add(key: String, threadId: String) {
        val current = read(key)
        if (threadId in current) return
        write(key, current + threadId)
    }

    private suspend fun remove(key: String, threadId: String) {
        val current = read(key)
        if (threadId !in current) return
        write(key, current - threadId)
    }

    private suspend fun read(key: String): Set<String> = decode(prefs.getString(key).first())

    private suspend fun write(key: String, ids: Set<String>) =
        prefs.setString(key, ids.joinToString(SEPARATOR))

    private companion object {
        const val SEPARATOR = "\n"
        const val KEY_STARRED = "messages.starred"
        const val KEY_ARCHIVED = "messages.archived"
        const val KEY_UNREAD = "messages.markedUnread"
        const val KEY_SAFETY = "messages.safetyDismissed"

        fun decode(raw: String?): Set<String> =
            raw?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet().orEmpty()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ThreadFlagsModule {
    @Provides
    @Singleton
    fun provideThreadFlagsStore(prefs: AppPreferences): ThreadFlagsStore =
        PreferencesThreadFlagsStore(prefs)
}
