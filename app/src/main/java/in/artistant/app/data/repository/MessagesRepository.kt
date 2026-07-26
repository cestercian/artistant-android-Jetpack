package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageKind
import `in`.artistant.app.data.model.Thread
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class MessagesRepositoryError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object NotSignedIn : MessagesRepositoryError("Sign in to send and sync messages.")
    class Underlying(cause: Throwable) : MessagesRepositoryError(cause.message ?: "Messages request failed", cause)
}

/** Cancellation seam for the future Realtime channel. */
fun interface MessagesSubscription { fun cancel() }

interface MessagesRepository {
    suspend fun listThreadsForUser(): List<Thread>
    suspend fun listMessages(threadId: String, limit: Int = 50): List<Message>
    suspend fun send(threadId: String, body: String): Message
    suspend fun findOrCreateThread(artistId: String, bookingId: String? = null): String
    suspend fun markThreadRead(threadId: String)
    suspend fun markThreadReadReceipt(threadId: String)
    suspend fun counterpartLastRead(threadId: String): Long?

    /**
     * Realtime is intentionally deferred: polling on open/send is the safe initial
     * M4 slice. The token keeps the UI lifecycle ready for a later channel wiring.
     */
    suspend fun subscribeMessages(threadId: String, onInsert: (Message) -> Unit): MessagesSubscription =
        MessagesSubscription {}
}

@Singleton
class SupabaseMessagesRepository @Inject constructor(
    private val client: SupabaseClient,
) : MessagesRepository {
    override suspend fun listThreadsForUser(): List<Thread> {
        val userId = currentUserId() ?: throw MessagesRepositoryError.NotSignedIn
        return try {
            client.from("threads")
                .select(THREAD_COLUMNS) { order("last_message_at", Order.DESCENDING) }
                .decodeList<DbThread>()
                .map { it.toDomain(userId) }
        } catch (t: Throwable) {
            throw MessagesRepositoryError.Underlying(t)
        }
    }

    override suspend fun listMessages(threadId: String, limit: Int): List<Message> {
        val userId = currentUserId() ?: throw MessagesRepositoryError.NotSignedIn
        return try {
            // 0072 columns are optional during rollout. Retry the base projection if absent.
            val rows = try {
                client.from("messages")
                    .select(MESSAGE_COLUMNS_WITH_SYSTEM) {
                        filter { eq("thread_id", threadId.lowercase()) }
                        order("sent_at", Order.DESCENDING)
                        limit(limit.toLong())
                    }
                    .decodeList<DbMessage>()
            } catch (_: Throwable) {
                client.from("messages")
                    .select(MESSAGE_COLUMNS) {
                        filter { eq("thread_id", threadId.lowercase()) }
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
            require(userId != artist) { "The conversation opens once the booking is confirmed." }
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

    private fun currentUserId(): String? = client.auth.currentSessionOrNull()?.user?.id?.lowercase()

    companion object {
        /** Never replace these with `*`: `body_raw` is historically forbidden by RLS. */
        val MESSAGE_COLUMNS = Columns.list("id", "thread_id", "sender_id", "body", "sent_at")
        val MESSAGE_COLUMNS_WITH_SYSTEM = Columns.list(
            "id", "thread_id", "sender_id", "body", "sent_at", "kind", "action_route",
        )
        private val THREAD_COLUMNS = Columns.list(
            "id", "client_id", "artist_id", "booking_id", "client_name",
            "client_unread_count", "artist_unread_count", "last_message_preview", "last_message_at",
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
) {
    fun toDomain(userId: String) = Thread(
        id = id,
        artistId = artistId,
        bookingId = bookingId,
        clientName = clientName,
        lastPreview = lastPreview.orEmpty(),
        lastMessageAtEpochMs = parseEpochMs(lastMessageAt),
        unreadCount = if (artistId.equals(userId, true)) artistUnread ?: 0 else clientUnread ?: 0,
    )
}

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
@Serializable private data class ThreadReadRow(
    @SerialName("user_id") val userId: String,
    @SerialName("last_read_at") val lastReadAt: String,
)

private fun parseEpochMs(value: String?): Long? = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

/** In-memory twin for ViewModel tests and deterministic local behaviour. */
class FakeMessagesRepository(
    private val userId: String = "00000000-0000-0000-0000-000000000001",
) : MessagesRepository {
    private val threads = mutableListOf<Thread>()
    private val messages = mutableMapOf<String, MutableList<Message>>()

    override suspend fun listThreadsForUser(): List<Thread> = threads.sortedByDescending { it.lastMessageAtEpochMs }
    override suspend fun listMessages(threadId: String, limit: Int): List<Message> =
        messages[threadId]?.takeLast(limit)?.toList().orEmpty()

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
        val thread = Thread(UUID.randomUUID().toString(), artistId.lowercase(), bookingId)
        threads.add(thread)
        return thread.id
    }

    override suspend fun markThreadRead(threadId: String) {
        val index = threads.indexOfFirst { it.id == threadId }
        if (index >= 0) threads[index] = threads[index].copy(unreadCount = 0)
    }
    override suspend fun markThreadReadReceipt(threadId: String) = Unit
    override suspend fun counterpartLastRead(threadId: String): Long? = null
}
