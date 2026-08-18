package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageKind
import `in`.artistant.app.data.model.Thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed class MessagesRepositoryError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object NotSignedIn : MessagesRepositoryError("Sign in to send and sync messages.")
    class Underlying(cause: Throwable) : MessagesRepositoryError(cause.message ?: "Messages request failed", cause)
}

/** Cancellation seam for the Realtime channel — caller MUST cancel on dispose. */
fun interface MessagesSubscription { fun cancel() }

interface MessagesRepository {
    suspend fun listThreadsForUser(): List<Thread>

    /**
     * One PAGE of a thread's messages, oldest-first (the display order).
     *
     * [before] is the scroll-back cursor, in the same epoch milliseconds
     * [Message.sentAtEpochMs] carries: null asks for the newest [limit] rows —
     * the default open — and a non-null value asks for the newest [limit] rows
     * at or older than that instant, i.e. the next page UP. Without it the
     * client could only ever show the newest 50 messages of a conversation and
     * everything before them was unreachable (iOS ships the same cursor as
     * `listMessages(threadID:limit:before:)`).
     *
     * The result is always ONE page, never the whole history, so callers merge
     * it rather than replace with it (`ChatRealtimeLogic.mergePreservingOptimistic`),
     * which also collapses the boundary row the cursor deliberately re-fetches.
     */
    suspend fun listMessages(threadId: String, limit: Int = 50, before: Long? = null): List<Message>

    suspend fun send(threadId: String, body: String): Message
    suspend fun findOrCreateThread(artistId: String, bookingId: String? = null): String
    suspend fun markThreadRead(threadId: String)
    suspend fun markThreadReadReceipt(threadId: String)
    suspend fun counterpartLastRead(threadId: String): Long?

    /**
     * Mute or unmute THE VIEWER'S OWN side of a thread — migration 0091's
     * `threads.client_muted` / `threads.artist_muted`.
     *
     * Per-side by design: mute is a fact about a reader, not about the
     * conversation, so a client silencing a thread must not silence the artist.
     * The server enforces this too (0091's `tg_threads_guard_mute` rejects a
     * cross-side write with `insufficient_privilege`), which is exactly why this
     * takes no column or seat argument — the implementation resolves the
     * viewer's own column and writes only that one.
     *
     * The mute is real, not cosmetic: `send-push` reads the RECIPIENT's column
     * and skips the chat-message push when it is set, so this suppresses the
     * lock-screen notification rather than only the in-app presentation.
     *
     * Throws so the caller can revert an optimistic toggle — a mute that says it
     * worked and didn't is worse than one that reports failure.
     */
    suspend fun setMuted(threadId: String, muted: Boolean)

    /**
     * Realtime INSERT subscribe on `public.messages` for one thread. Echoes include
     * the caller's own sends — ChatViewModel dedups via [ChatRealtimeLogic].
     * Returns a no-op subscription when realtime is off, unsigned-in, or join fails
     * (poll-on-open remains the fallback).
     */
    suspend fun subscribeMessages(threadId: String, onInsert: (Message) -> Unit): MessagesSubscription
}

@Singleton
class SupabaseMessagesRepository @Inject constructor(
    private val client: SupabaseClient,
) : MessagesRepository {
    override suspend fun listThreadsForUser(): List<Thread> {
        val userId = currentUserId() ?: throw MessagesRepositoryError.NotSignedIn
        return try {
            // 0091's mute pair is requested by name. It is live on both projects,
            // so the retry is insurance rather than a rollout path — but this is
            // the inbox's only load, and a missing column would empty it
            // entirely, which is not a failure worth risking for two booleans.
            // Same shape as the 0072 fallback in listMessages below.
            val rows = try {
                client.from("threads")
                    .select(THREAD_COLUMNS_WITH_MUTE) { order("last_message_at", Order.DESCENDING) }
                    .decodeList<DbThread>()
            } catch (_: Throwable) {
                client.from("threads")
                    .select(THREAD_COLUMNS) { order("last_message_at", Order.DESCENDING) }
                    .decodeList<DbThread>()
            }
            rows.map { it.toDomain(userId) }
        } catch (t: Throwable) {
            throw MessagesRepositoryError.Underlying(t)
        }
    }

    override suspend fun listMessages(threadId: String, limit: Int, before: Long?): List<Message> {
        val userId = currentUserId() ?: throw MessagesRepositoryError.NotSignedIn
        val tid = threadId.lowercase()
        // The scroll-back bound is the END of the cursor's millisecond, not the
        // cursor instant itself. `sent_at` is a microsecond `timestamptz` while
        // the cursor is a domain `sentAtEpochMs` that lost those microseconds on
        // the way in, so an exclusive `< cursor` would permanently skip any
        // OLDER sibling sharing the cursor's millisecond. Overshooting by one
        // millisecond re-fetches the boundary row instead, and the caller merges
        // by id — one duplicate on the wire, no hole in the history. Same
        // reasoning as iOS's inclusive `.lte` cursor.
        val cursor = before?.let { Instant.ofEpochMilli(it + 1).toString() }
        return try {
            // 0072 columns are optional during rollout. Retry the base projection if absent.
            val rows = try {
                client.from("messages")
                    .select(MESSAGE_COLUMNS_WITH_SYSTEM) {
                        filter {
                            eq("thread_id", tid)
                            if (cursor != null) lt("sent_at", cursor)
                        }
                        order("sent_at", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<DbMessage>()
            } catch (_: Throwable) {
                client.from("messages")
                    .select(MESSAGE_COLUMNS) {
                        filter {
                            eq("thread_id", tid)
                            if (cursor != null) lt("sent_at", cursor)
                        }
                        order("sent_at", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<DbMessage>()
            }
            rows.asReversed().map { it.toDomain(userId) }
        } catch (t: Throwable) {
            throw MessagesRepositoryError.Underlying(t)
        }
    }

    override suspend fun send(threadId: String, body: String): Message {
        val userId = currentUserId() ?: throw MessagesRepositoryError.NotSignedIn
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "A message can't be empty." }
        return try {
            client.from("messages")
                .insert(MessageInsert(threadId.lowercase(), userId, trimmed)) {
                    select(MESSAGE_COLUMNS)
                }
                .decodeSingle<DbMessage>()
                .toDomain(userId)
        } catch (t: Throwable) {
            throw MessagesRepositoryError.Underlying(t)
        }
    }

    override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String {
        val userId = currentUserId() ?: throw MessagesRepositoryError.NotSignedIn
        val artist = artistId.lowercase()
        val booking = bookingId?.lowercase()
        suspend fun existing(): String? = client.from("threads")
            .select(Columns.list("id")) {
                filter {
                    if (booking != null) eq("booking_id", booking)
                    else {
                        eq("client_id", userId)
                        eq("artist_id", artist)
                        exact("booking_id", null)
                    }
                }
                limit(1)
            }
            .decodeList<ThreadIdRow>()
            .firstOrNull()?.id

        try {
            existing()?.let { return it }
            // No thread yet. Only the CLIENT may create — artist here means the
            // booking isn't confirmed (0015 hasn't minted the row). Creating
            // would mint client_id == artist_id (self-thread / 0081 CHECK).
            if (userId == artist) {
                throw IllegalStateException("The conversation opens once the booking is confirmed.")
            }
            return client.from("threads")
                .insert(ThreadInsert(userId, artist, booking)) { select(Columns.list("id")) }
                .decodeSingle<ThreadIdRow>()
                .id
        } catch (t: Throwable) {
            // A concurrent creator can win the unique-pair race; read once more.
            existing()?.let { return it }
            throw MessagesRepositoryError.Underlying(t)
        }
    }

    override suspend fun markThreadRead(threadId: String) {
        val userId = currentUserId() ?: return
        try {
            client.from("threads").update(UnreadPatch(0)) {
                filter { eq("id", threadId.lowercase()); eq("client_id", userId) }
            }
            client.from("threads").update(ArtistUnreadPatch(0)) {
                filter { eq("id", threadId.lowercase()); eq("artist_id", userId) }
            }
        } catch (t: Throwable) {
            throw MessagesRepositoryError.Underlying(t)
        }
    }

    override suspend fun markThreadReadReceipt(threadId: String) {
        if (currentUserId() == null) return
        try {
            client.postgrest.rpc("mark_thread_read", buildJsonObject { put("p_thread", threadId.lowercase()) })
        } catch (t: Throwable) {
            throw MessagesRepositoryError.Underlying(t)
        }
    }

    /**
     * Write the viewer's own mute column, and only that one.
     *
     * The seat is READ from the row rather than inferred from anything the
     * caller passed: `client_muted` and `artist_muted` are separately owned
     * (0091), and the guard trigger raises `insufficient_privilege` on a
     * cross-side write — so guessing wrong is a hard failure, not a silent one.
     * Reading first costs one small round trip on a rare, user-initiated action.
     *
     * The resolved seat is then repeated in the UPDATE's own filter. That is not
     * redundant: if the session changes between the read and the write, the
     * write matches zero rows instead of landing on the counterparty's column.
     */
    override suspend fun setMuted(threadId: String, muted: Boolean) {
        val userId = currentUserId() ?: throw MessagesRepositoryError.NotSignedIn
        val tid = threadId.lowercase()
        try {
            val seats = client.from("threads")
                .select(Columns.list("client_id", "artist_id")) {
                    filter { eq("id", tid) }
                    limit(1)
                }
                .decodeList<ThreadSeatsRow>()
                .firstOrNull()
                ?: throw IllegalStateException("That conversation is no longer available.")

            // `artist_id` is the artist's USER id (artists.id references users.id),
            // so both seats are directly comparable to the session uid.
            val viewerIsArtist = seats.artistId.equals(userId, ignoreCase = true)
            val viewerIsClient = seats.clientId.equals(userId, ignoreCase = true)
            if (!viewerIsArtist && !viewerIsClient) {
                throw IllegalStateException("You're not part of this conversation.")
            }

            if (viewerIsArtist) {
                client.from("threads").update(ArtistMutedPatch(muted)) {
                    filter { eq("id", tid); eq("artist_id", userId) }
                }
            } else {
                client.from("threads").update(ClientMutedPatch(muted)) {
                    filter { eq("id", tid); eq("client_id", userId) }
                }
            }
        } catch (t: Throwable) {
            throw MessagesRepositoryError.Underlying(t)
        }
    }

    override suspend fun counterpartLastRead(threadId: String): Long? {
        val userId = currentUserId() ?: return null
        return try {
            client.from("thread_reads")
                .select(Columns.list("user_id", "last_read_at")) {
                    filter { eq("thread_id", threadId.lowercase()) }
                }
                .decodeList<ThreadReadRow>()
                .filterNot { it.userId.equals(userId, ignoreCase = true) }
                .mapNotNull { parseEpochMs(it.lastReadAt) }
                .maxOrNull()
        } catch (_: Throwable) {
            // Pre-0072 servers have no table; receipts are strictly best effort.
            null
        }
    }

    override suspend fun subscribeMessages(
        threadId: String,
        onInsert: (Message) -> Unit,
    ): MessagesSubscription {
        if (!AppEnvironment.realtimeEnabled) return MessagesSubscription {}
        val userId = currentUserId() ?: return MessagesSubscription {}
        val tid = threadId.lowercase()
        // Seed / local-only thread ids aren't on the server — Realtime can't join them.
        if (!UUID_REGEX.matches(tid)) return MessagesSubscription {}

        // Liveness gate: cancel() flips this synchronously so a callback mid-decode
        // after teardown never mutates UI (iOS OSAllocatedUnfairLock → AtomicBoolean).
        val live = AtomicBoolean(true)
        val channel = client.channel("messages:$tid")
        val changeFlow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("thread_id", FilterOperator.EQ, tid)
        }
        // Own scope so collection survives the subscribe await and dies on cancel().
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val collectJob: Job = scope.launch {
            changeFlow.collect { action ->
                if (!live.get()) return@collect
                val row = action.decodeRecordOrNull<DbMessage>() ?: run {
                    Timber.w("Realtime message decode failed for thread %s", tid)
                    return@collect
                }
                if (!live.get()) return@collect
                onInsert(row.toDomain(userId))
            }
        }

        return try {
            channel.subscribe(blockUntilSubscribed = true)
            MessagesSubscription {
                live.set(false)
                collectJob.cancel()
                scope.launch {
                    runCatching { channel.unsubscribe() }
                    scope.coroutineContext[Job]?.cancel()
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "Realtime subscribe failed for thread %s — polling fallback", tid)
            live.set(false)
            collectJob.cancel()
            scope.coroutineContext[Job]?.cancel()
            MessagesSubscription {}
        }
    }

    private fun currentUserId(): String? = client.auth.currentSessionOrNull()?.user?.id?.lowercase()

    companion object {
        private val UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        /** Never replace these with `*`: `body_raw` is historically forbidden by RLS. */
        val MESSAGE_COLUMNS = Columns.list("id", "thread_id", "sender_id", "body", "sent_at")
        val MESSAGE_COLUMNS_WITH_SYSTEM = Columns.list(
            "id", "thread_id", "sender_id", "body", "sent_at", "kind", "action_route",
        )
        private val THREAD_COLUMNS = Columns.list(
            "id", "client_id", "artist_id", "booking_id", "client_name",
            "client_unread_count", "artist_unread_count", "last_message_preview", "last_message_at",
        )

        /** [THREAD_COLUMNS] plus migration 0091's per-side mute pair. */
        private val THREAD_COLUMNS_WITH_MUTE = Columns.list(
            "id", "client_id", "artist_id", "booking_id", "client_name",
            "client_unread_count", "artist_unread_count", "last_message_preview", "last_message_at",
            "client_muted", "artist_muted",
        )
    }
}

@Serializable private data class DbThread(
    val id: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("artist_id") val artistId: String,
    @SerialName("booking_id") val bookingId: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_unread_count") val clientUnread: Int? = null,
    @SerialName("artist_unread_count") val artistUnread: Int? = null,
    @SerialName("last_message_preview") val lastPreview: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null,
    // 0091. Defaulted so the pre-0091 fallback projection still decodes; an
    // absent column reads as unmuted, which is the server's own default.
    @SerialName("client_muted") val clientMuted: Boolean? = null,
    @SerialName("artist_muted") val artistMuted: Boolean? = null,
) {
    fun toDomain(userId: String): Thread {
        // One seat test drives every viewer-relative field, so the unread badge
        // and the mute state can never disagree about which side is looking.
        val viewerIsArtist = artistId.equals(userId, true)
        return Thread(
            id = id,
            artistId = artistId,
            clientId = clientId,
            bookingId = bookingId,
            clientName = clientName,
            lastPreview = lastPreview.orEmpty(),
            lastMessageAtEpochMs = parseEpochMs(lastMessageAt),
            unreadCount = if (viewerIsArtist) artistUnread ?: 0 else clientUnread ?: 0,
            muted = (if (viewerIsArtist) artistMuted else clientMuted) == true,
        )
    }
}

/** Just the two participant ids — the seat lookup behind [SupabaseMessagesRepository.setMuted]. */
@Serializable private data class ThreadSeatsRow(
    @SerialName("client_id") val clientId: String,
    @SerialName("artist_id") val artistId: String,
)

@Serializable private data class DbMessage(
    val id: String,
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_id") val senderId: String? = null,
    val body: String,
    @SerialName("sent_at") val sentAt: String,
    val kind: String? = null,
    @SerialName("action_route") val actionRoute: String? = null,
) {
    fun toDomain(userId: String) = Message(
        id, threadId, senderId, body, parseEpochMs(sentAt) ?: System.currentTimeMillis(),
        kind = if (kind == "system") MessageKind.System else MessageKind.User,
        actionRoute = actionRoute,
        isMine = senderId.equals(userId, ignoreCase = true),
    )
}

@Serializable private data class MessageInsert(
    @SerialName("thread_id") val threadId: String,
    @SerialName("sender_id") val senderId: String,
    val body: String,
)
@Serializable private data class ThreadInsert(
    @SerialName("client_id") val clientId: String,
    @SerialName("artist_id") val artistId: String,
    @SerialName("booking_id") val bookingId: String?,
)
@Serializable private data class ThreadIdRow(val id: String)
@Serializable private data class UnreadPatch(@SerialName("client_unread_count") val count: Int)
@Serializable private data class ArtistUnreadPatch(@SerialName("artist_unread_count") val count: Int)

// One patch type per side, each holding exactly ONE column. Kept apart rather
// than a single nullable-pair payload so there is no shape in which a write can
// carry the counterparty's mute column at all (0091).
@Serializable private data class ClientMutedPatch(@SerialName("client_muted") val muted: Boolean)
@Serializable private data class ArtistMutedPatch(@SerialName("artist_muted") val muted: Boolean)
@Serializable private data class ThreadReadRow(
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_at") val lastReadAt: String,
)

private fun parseEpochMs(value: String?): Long? = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

/**
 * In-memory twin for ViewModel tests and deterministic local behaviour.
 *
 * Mute is modelled as the server models it — TWO independently-owned columns
 * ([clientMuted] / [artistMuted]) rather than the one viewer-relative flag the
 * domain [Thread] exposes. That is deliberate: collapsing them here would make
 * "a client muting must not touch the artist's column" untestable, since both
 * sides would read back through the same field.
 */
class FakeMessagesRepository(
    private val userId: String = "00000000-0000-0000-0000-000000000001",
    seedThreads: List<Thread> = emptyList(),
) : MessagesRepository {
    private val threads = seedThreads.toMutableList()
    private val messages = mutableMapOf<String, MutableList<Message>>()

    /** `threads.client_muted`, by thread id. Exposed so tests can assert on the raw column. */
    val clientMuted = mutableMapOf<String, Boolean>()

    /** `threads.artist_muted`, by thread id. */
    val artistMuted = mutableMapOf<String, Boolean>()

    override suspend fun listThreadsForUser(): List<Thread> =
        threads.sortedByDescending { it.lastMessageAtEpochMs }.map(::withViewerMute)

    /** Project the viewer's OWN column onto the row, exactly as the decoder does. */
    private fun withViewerMute(thread: Thread): Thread {
        val muted = if (thread.artistId.equals(userId, ignoreCase = true)) {
            artistMuted[thread.id]
        } else {
            clientMuted[thread.id]
        }
        return thread.copy(muted = muted == true)
    }

    override suspend fun setMuted(threadId: String, muted: Boolean) {
        val thread = threads.firstOrNull { it.id == threadId }
            ?: throw MessagesRepositoryError.Underlying(NoSuchElementException(threadId))
        when {
            thread.artistId.equals(userId, ignoreCase = true) -> artistMuted[threadId] = muted
            thread.clientId.equals(userId, ignoreCase = true) -> clientMuted[threadId] = muted
            // Mirrors the real seam: a non-participant has no column of their own,
            // and must not fall through to writing somebody else's.
            else -> throw MessagesRepositoryError.Underlying(
                IllegalStateException("You're not part of this conversation."),
            )
        }
    }

    /** One page, same shape as the real seam: the newest [limit] rows no newer than [before]. */
    override suspend fun listMessages(threadId: String, limit: Int, before: Long?): List<Message> =
        messages[threadId].orEmpty()
            .filter { before == null || it.sentAtEpochMs <= before }
            .takeLast(limit)

    override suspend fun send(threadId: String, body: String): Message {
        val text = body.trim()
        require(text.isNotEmpty()) { "A message can't be empty." }
        val message = Message(UUID.randomUUID().toString(), threadId, userId, text, System.currentTimeMillis(), isMine = true)
        messages.getOrPut(threadId) { mutableListOf() }.add(message)
        val index = threads.indexOfFirst { it.id == threadId }
        if (index >= 0) threads[index] = threads[index].copy(lastPreview = text, lastMessageAtEpochMs = message.sentAtEpochMs)
        return message
    }

    override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String {
        threads.firstOrNull { it.artistId.equals(artistId, true) && it.bookingId == bookingId }?.let { return it.id }
        // Only the client seat can mint a thread (see the real impl), so the
        // viewer IS the client here — carry that so the new row has both seats.
        val thread = Thread(
            id = UUID.randomUUID().toString(),
            artistId = artistId.lowercase(),
            clientId = userId,
            bookingId = bookingId,
        )
        threads.add(thread)
        return thread.id
    }

    override suspend fun markThreadRead(threadId: String) {
        val index = threads.indexOfFirst { it.id == threadId }
        if (index >= 0) threads[index] = threads[index].copy(unreadCount = 0)
    }
    override suspend fun markThreadReadReceipt(threadId: String) = Unit
    override suspend fun counterpartLastRead(threadId: String): Long? = null

    /** Test hook: Fake has no WebSocket; callers get a no-op cancel token. */
    override suspend fun subscribeMessages(
        threadId: String,
        onInsert: (Message) -> Unit,
    ): MessagesSubscription = MessagesSubscription {}
}
