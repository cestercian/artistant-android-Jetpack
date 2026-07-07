package `in`.artistant.app.data.repository

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageSender
import `in`.artistant.app.data.model.Thread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import java.time.Instant
import java.util.UUID

/**
 * In-memory [MessagesRepository] for tests / previews. Records sent bodies and
 * mints deterministic server messages. Two hooks drive the store's reconcile tests:
 * [failSend] forces the write to throw (exercises `.Sending` → `.Failed` + retry),
 * and [beforeSendReturns] fires with the about-to-be-returned server message BEFORE
 * `send` returns — a test wires it to `store.receiveRealtimeMessage` to simulate the
 * realtime echo BEATING the send-return (the duplicate-risk race).
 */
class FakeMessagesRepository(
    threads: List<Thread> = emptyList(),
    var failSend: Boolean = false,
) : MessagesRepository {

    val storedThreads: MutableList<Thread> = threads.toMutableList()
    private val messagesByThread: MutableMap<String, MutableList<Message>> =
        threads.associate { it.id to it.messages.toMutableList() }.toMutableMap()

    private var serverSeq = 0
    /** Fired with the server message just before [send] returns it. */
    var beforeSendReturns: ((Message) -> Unit)? = null

    override suspend fun listThreadsForUser(): List<Thread> = storedThreads.toList()

    override suspend fun listMessages(threadId: String): List<Message> =
        messagesByThread[threadId]?.toList() ?: emptyList()

    override suspend fun send(threadId: String, body: String): Message {
        if (failSend) throw AppError.Unknown(RuntimeException("send failed"))
        val message = Message(
            id = "server-${++serverSeq}",
            sender = MessageSender.Me,
            body = body,      // Fake does NOT redact — content matches the optimistic body.
            sentAt = Instant.now(),
        )
        messagesByThread.getOrPut(threadId) { mutableListOf() }.add(message)
        beforeSendReturns?.invoke(message)
        return message
    }

    // Realtime is driven directly via store.receiveRealtimeMessage in tests — the
    // fake channel is a no-op completed Job.
    override fun subscribeMessages(
        threadId: String,
        scope: CoroutineScope,
        onInsert: (Message) -> Unit,
    ): Job = Job().apply { complete() }

    override suspend fun findOrCreateThread(artistId: String, bookingId: String?): String =
        storedThreads.firstOrNull { it.artistId == artistId && it.bookingId == bookingId }?.id
            ?: UUID.randomUUID().toString().also {
                storedThreads.add(Thread(id = it, artistId = artistId, bookingId = bookingId))
                messagesByThread[it] = mutableListOf()
            }

    override suspend fun markThreadRead(threadId: String) {
        val idx = storedThreads.indexOfFirst { it.id == threadId }
        if (idx >= 0) storedThreads[idx] = storedThreads[idx].copy(unread = 0)
    }
}
