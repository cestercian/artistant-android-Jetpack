package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.core.result.mapPostgrest
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.model.dto.DBMessage
import `in`.artistant.app.data.model.dto.DBThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Thrown when a threadId isn't a server UUID (a local, pre-persistence inquiry
 * thread). The store SWALLOWS it — the message lives on the device and settles to
 * `.Sent` (no retry chip), the same transitional shape as the iOS
 * `.seedThreadNotPersistable` typed error.
 */
object LocalThreadNotPersistable : Exception("This conversation stays on this device.")

/**
 * Chat data boundary (port of iOS `MessagesRepository`). Reads/writes `threads` +
 * `messages` and opens the one Realtime channel the app uses (INSERTs on
 * `public.messages`, filtered to a thread). RLS gates every path on `auth.uid()`.
 *
 * The `messages` reads are COLUMN-SCOPED via [DBMessage.COLUMNS] — never
 * `select("*")`: 0061 revokes `body_raw`, so a `*` read 403s (§4 of API_MAPPING).
 */
interface MessagesRepository {
    /** Threads the signed-in user participates in (RLS-gated), newest-touched first. */
    suspend fun listThreadsForUser(): List<Thread>

    /** Messages for [threadId], oldest first. Explicit columns (a `*` read 403s post-0061). */
    suspend fun listMessages(threadId: String): List<Message>

    /**
     * Insert a message; returns the inserted [Message]. The redaction trigger
     * rewrites `body` inline, so the RETURNING row is already what participants
     * see — read it back (no echo-and-update dance). Throws
     * [LocalThreadNotPersistable] for a non-UUID (local) thread.
     */
    suspend fun send(threadId: String, body: String): Message

    /**
     * Subscribe to INSERTs on `public.messages` for [threadId]. Collects the
     * Realtime flow on [scope] and invokes [onInsert] per new row (including the
     * viewer's own sends echoed back — callers dedup by id). Gated on
     * [AppEnvironment.realtimeEnabled]. Returns the collecting [Job]; cancel it on
     * screen dispose to tear the channel down. A no-op (already-complete) Job for a
     * disabled flag, a non-UUID thread, or a signed-out viewer.
     */
    fun subscribeMessages(threadId: String, scope: CoroutineScope, onInsert: (Message) -> Unit): Job

    /** Find-or-create the REAL threads row for (signed-in client, [artistId], [bookingId]); returns its UUID. */
    suspend fun findOrCreateThread(artistId: String, bookingId: String?): String

    /** Zero the signed-in viewer's unread counter on [threadId] (mark-as-read). No-op for a non-UUID thread. */
    suspend fun markThreadRead(threadId: String)
}

@Singleton
class SupabaseMessagesRepository @Inject constructor(
    private val client: SupabaseClient,
) : MessagesRepository {

    override suspend fun listThreadsForUser(): List<Thread> {
        // Capture the id ONCE — the mapper picks the viewer's unread column by role.
        val userId = currentUserId()
        return try {
            client.postgrest.from("threads")
                .select(Columns.raw(DBThread.COLUMNS)) {
                    // RLS (threads_select_participants) gates the set; order newest-touched first.
                    order("last_message_at", Order.DESCENDING)
                }
                .decodeList<DBThread>()
                .map { it.toThread(userId) }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    override suspend fun listMessages(threadId: String): List<Message> {
        // Capture the viewer id ONCE (a second read after the await could race a
        // sign-out and mis-attribute every row to .Them — the iOS Greptile P1).
        val userId = currentUserId()
        if (!threadId.isUuid()) throw LocalThreadNotPersistable
        return try {
            client.postgrest.from("messages")
                .select(Columns.raw(DBMessage.COLUMNS)) {
                    filter { eq("thread_id", threadId.lowercaseUuid()) }
                    order("sent_at", Order.ASCENDING)
                }
                .decodeList<DBMessage>()
                .map { it.toMessage(userId) }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    override suspend fun send(threadId: String, body: String): Message {
        val userId = currentUserId()
        if (!threadId.isUuid()) throw LocalThreadNotPersistable
        val row = MessageInsert(
            thread_id = threadId.lowercaseUuid(),
            sender_id = userId,
            body = body,
        )
        return try {
            client.postgrest.from("messages")
                // Explicit RETURNING columns — 0061 forbids body_raw, so no `*`.
                .insert(row) { select(Columns.raw(DBMessage.COLUMNS)) }
                .decodeSingle<DBMessage>()
                .toMessage(userId)
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    override fun subscribeMessages(
        threadId: String,
        scope: CoroutineScope,
        onInsert: (Message) -> Unit,
    ): Job {
        // Gate: flag off / local thread / signed-out → no channel. Return a
        // completed Job so callers can cancel() unconditionally.
        if (!AppEnvironment.realtimeEnabled || !threadId.isUuid()) return doneJob()
        val viewerId = client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid() ?: return doneJob()

        // Liveness gate (the iOS OSAllocatedUnfairLock → Mutex): structured
        // cancellation already stops the collect, but a decode already past the
        // suspension point could still call onInsert AFTER cancel lands. The
        // flag is flipped false in `finally`; the callback checks it under the
        // lock, so no post-cancel store mutation slips through.
        val liveLock = Mutex()
        var live = true

        val tid = threadId.lowercaseUuid()
        // One channel per thread. postgresChangeFlow MUST be built before subscribe
        // (it errors if called after the channel joins), so build the flow, then join.
        val channel = client.realtime.channel("messages:$tid")
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("thread_id", FilterOperator.EQ, tid)
        }

        return scope.launch {
            try {
                channel.subscribe(blockUntilSubscribed = true)
                flow.collect { action ->
                    val message = runCatching { action.decodeRecord<DBMessage>().toMessage(viewerId) }
                        .getOrNull() ?: return@collect
                    liveLock.withLock { if (live) onInsert(message) }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (_: Throwable) {
                // Transport / RLS / join failure — degrade to the poll-on-send path.
            } finally {
                liveLock.withLock { live = false }
                // Tear down off the (already-cancelled) scope so unsubscribe still runs.
                withContext(NonCancellable) { runCatching { client.realtime.removeChannel(channel) } }
            }
        }
    }

    override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String {
        val clientId = currentUserId()
        val artistStr = artistId.lowercaseUuid()
        val bookingStr = bookingId?.lowercaseUuid()

        // A client can have several threads with one artist (one per booking + one
        // bookingless inquiry), unique per the DB's threads_unique_per_pair_booking.
        suspend fun findExisting(): String? =
            client.postgrest.from("threads")
                .select(Columns.list("id")) {
                    filter {
                        eq("client_id", clientId)
                        eq("artist_id", artistStr)
                        if (bookingStr != null) eq("booking_id", bookingStr) else exact("booking_id", null)
                    }
                    limit(1)
                }
                .decodeList<IdOnly>()
                .firstOrNull()?.id

        return try {
            findExisting() ?: run {
                val inserted = client.postgrest.from("threads")
                    .insert(ThreadInsert(clientId, artistStr, bookingStr)) { select(Columns.list("id")) }
                    .decodeList<IdOnly>()
                    .firstOrNull()?.id
                inserted ?: findExisting() ?: throw AppError.NotFoundOrUnauthorized
            }
        } catch (e: AppError) {
            throw e
        } catch (t: Throwable) {
            // A parallel insert may have won the unique-index race — re-select once.
            runCatching { findExisting() }.getOrNull() ?: throw mapPostgrest(t)
        }
    }

    override suspend fun markThreadRead(threadId: String) {
        // Signed-out or a non-UUID (local) thread → no server row to reset.
        val userId = client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid() ?: return
        if (!threadId.isUuid()) return
        val tid = threadId.lowercaseUuid()
        try {
            // Two self-selecting updates: whichever participant the viewer is, that
            // counter resets; the other WHERE matches nothing. RLS gates it.
            client.postgrest.from("threads").update(UnreadZero(client_unread_count = 0)) {
                filter { eq("id", tid); eq("client_id", userId) }
            }
            client.postgrest.from("threads").update(UnreadZero(artist_unread_count = 0)) {
                filter { eq("id", tid); eq("artist_id", userId) }
            }
        } catch (t: Throwable) {
            throw mapPostgrest(t)
        }
    }

    private fun currentUserId(): String =
        client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid()
            ?: throw AppError.NotFoundOrUnauthorized

    @Serializable private data class IdOnly(val id: String)
    @Serializable private data class MessageInsert(val thread_id: String, val sender_id: String, val body: String)
    @Serializable private data class ThreadInsert(val client_id: String, val artist_id: String, val booking_id: String?)
    // Two nullable fields so a single `update()` shape can zero either counter
    // (kotlinx omits the null one — only the intended column is written).
    @Serializable private data class UnreadZero(
        val client_unread_count: Int? = null,
        val artist_unread_count: Int? = null,
    )
}

/** True if [this] parses as a UUID (a server thread) vs a local inquiry id. */
private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess

/** An already-completed Job — the cancel-safe "no subscription" return. */
private fun doneJob(): Job = Job().apply { complete() }
