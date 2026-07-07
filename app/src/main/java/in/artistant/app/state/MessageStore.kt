package `in`.artistant.app.state

import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.MessageSender
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.LocalThreadNotPersistable
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.domain.chat.Redaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Process-wide chat state (port of iOS `State/MessageStore.swift`). Owns the
 * optimistic-send reconcile: a composed message lands instantly as `.Sending`,
 * then a background write settles it. The tricky part is the **3-way reconcile**
 * across {optimistic placeholder, send-return echo, realtime echo} — the same
 * server row can arrive via BOTH the send() return AND the Realtime subscription,
 * so we dedup by server id and, for the realtime-beats-send race, by a tight
 * content+sender+time match. send/retry return the write [Job] so tests can
 * `join()` deterministically.
 */
@Singleton
class MessageStore @Inject constructor(
    private val repository: MessagesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _threads = MutableStateFlow<List<Thread>>(emptyList())
    val threads: StateFlow<List<Thread>> = _threads.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    /** Sign-out wipe so a new account never sees the prior user's conversations. */
    fun reset() {
        _threads.value = emptyList()
    }

    fun thread(id: String): Thread? = _threads.value.firstOrNull { it.id == id }

    /** Total unread across threads — drives the Messages tab badge. */
    val totalUnread: Int get() = _threads.value.sumOf { maxOf(0, it.unread) }

    /**
     * Seed / replace the thread set, NORMALIZING any lingering `.Sending` → `.Failed`.
     * A message persisted while still sending means the app was killed mid-send; it
     * never confirmed and nothing re-drives it, so it must reload with a retry
     * affordance (the retry chip shows only for `.Failed`). The server is the source
     * of truth for real threads, so its rows always decode `.Sent` — this only bites
     * a future persisted-snapshot seed, kept for parity with iOS cold-load.
     */
    fun setThreads(threads: List<Thread>) {
        _threads.value = threads.map { t -> t.copy(messages = t.messages.map(::normalizePending)) }
    }

    private fun normalizePending(m: Message): Message =
        if (m.delivery == MessageDelivery.Sending) m.copy(delivery = MessageDelivery.Failed) else m

    // ---- Optimistic send + reconcile ----

    /**
     * Optimistic-local insert + background write. The composer sees the bubble land
     * instantly (`.Sending`); the round-trip settles it. Returns the write [Job]
     * (an already-complete Job when the input is empty / the thread is missing).
     */
    fun send(threadId: String, body: String): Job {
        // Defensive cap — messages.body is unbounded text server-side; a pathological
        // paste shouldn't mint a giant row. 4000 chars is far beyond any real turn.
        val capped = body.take(4000)
        if (capped.isBlank() || thread(threadId) == null) return completedJob()

        // Optimistic id is replaced by the server id once the write reconciles.
        val optimisticId = "optimistic-${System.currentTimeMillis()}"
        val optimistic = Message(
            id = optimisticId,
            sender = MessageSender.Me,
            body = capped,
            sentAt = Instant.now(),
            delivery = MessageDelivery.Sending,
        )
        updateThread(threadId) { it.copy(messages = it.messages + optimistic) }
        return deliver(threadId, optimisticId, capped)
    }

    /**
     * Fire the network write for an already-inserted optimistic message and
     * reconcile. Extracted so [retryFailedMessage] re-runs the same path under the
     * same optimistic id.
     */
    private fun deliver(threadId: String, optimisticId: String, body: String): Job = scope.launch {
        try {
            val serverMessage = repository.send(threadId, body)
            // 3-way reconcile: DROP the optimistic placeholder, then append the
            // server message ONLY if it isn't already present. The realtime
            // subscription may have ALREADY appended it (same server id) before this
            // write returned — a naive replace-in-place would duplicate it. The
            // not-present check also covers a refresh that wiped the optimistic.
            updateThread(threadId) { t ->
                val withoutOptimistic = t.messages.filterNot { it.id == optimisticId }
                if (withoutOptimistic.any { it.id == serverMessage.id }) {
                    t.copy(messages = withoutOptimistic)
                } else {
                    t.copy(messages = withoutOptimistic + serverMessage)
                }
            }
        } catch (_: LocalThreadNotPersistable) {
            // Local inquiry thread — never persists server-side by design. Not a
            // user-facing failure: settle to `.Sent` (no retry chip).
            markDelivery(threadId, optimisticId, MessageDelivery.Sent)
        } catch (_: Throwable) {
            // Real network / auth / RLS failure — flip to `.Failed` so the screen
            // shows a Tap-to-retry affordance instead of a silent drop.
            markDelivery(threadId, optimisticId, MessageDelivery.Failed)
        }
    }

    /** Retry a `.Failed` send under the SAME optimistic id. Guarded so a double-tap can't fire two writes. */
    fun retryFailedMessage(threadId: String, messageId: String): Job {
        val t = thread(threadId) ?: return completedJob()
        val m = t.messages.firstOrNull { it.id == messageId } ?: return completedJob()
        if (m.delivery != MessageDelivery.Failed) return completedJob()
        markDelivery(threadId, messageId, MessageDelivery.Sending)
        return deliver(threadId, messageId, m.body)
    }

    private fun markDelivery(threadId: String, messageId: String, state: MessageDelivery) {
        updateThread(threadId) { t ->
            t.copy(messages = t.messages.map { if (it.id == messageId) it.copy(delivery = state) else it })
        }
    }

    /**
     * Append a Realtime-delivered message, with dedup. Fires for EVERY new row —
     * including the viewer's own sends echoed back — so dedup by server id first.
     * No-op if the thread isn't local yet (a refresh will pick it up).
     */
    fun receiveRealtimeMessage(threadId: String, message: Message) {
        val t = thread(threadId) ?: return
        // Already landed via the send() echo OR a prior realtime callback.
        if (t.messages.any { it.id == message.id }) return

        // Realtime-beats-send race: this can be the echo of our OWN optimistic send
        // whose id hasn't been swapped to the server id yet. Collapse it INTO the
        // in-flight placeholder instead of appending a duplicate the send() reconcile
        // would only clear a beat later. SCOPE TIGHTLY (iOS realtime-chat-audit P1):
        //   - `.Sending` ONLY, never `.Failed` — a `.Failed` bubble has no server row,
        //     so overwriting it would destroy the user's message + its retry chip.
        //   - recency-bounded so an unrelated older in-flight bubble can't be swallowed.
        // (Server redaction may make the echoed body differ from the raw optimistic
        // body — then this falls through to append and deliver()'s id check dedups.)
        if (message.sender == MessageSender.Me) {
            val match = t.messages.firstOrNull {
                it.sender == MessageSender.Me &&
                    it.delivery == MessageDelivery.Sending &&
                    it.body == message.body &&
                    abs(it.sentAt.toEpochMilli() - message.sentAt.toEpochMilli()) < CONTENT_MATCH_WINDOW_MS
            }
            if (match != null) {
                updateThread(threadId) { th ->
                    th.copy(messages = th.messages.map { if (it.id == match.id) message else it })
                }
                return
            }
        }
        updateThread(threadId) { it.copy(messages = it.messages + message) }
    }

    // ---- Read / refresh ----

    /**
     * Mark a thread read for the viewer: clears the local unread badge immediately
     * AND resets the server counter so the next refresh doesn't re-show it. Retries
     * once on a transient blip (markThreadRead is idempotent).
     */
    fun markRead(threadId: String) {
        updateThread(threadId) { if (it.unread != 0) it.copy(unread = 0) else it }
        scope.launch {
            runCatching { repository.markThreadRead(threadId) }.onFailure {
                delay(500)
                runCatching { repository.markThreadRead(threadId) }
            }
        }
    }

    /**
     * Cross-device sync — pulls the server thread set, hydrates each thread's
     * messages, and replaces local. Non-UUID local inquiry threads survive (the
     * server doesn't know them). On failure, local state is UNTOUCHED (the threads
     * the user last saw stay on screen) and an error is emitted.
     */
    suspend fun refreshFromServer() {
        try {
            val remote = repository.listThreadsForUser()
            val hydrated = remote.map { thread ->
                // No `try?` — a failed per-thread hydrate must NOT silently merge an
                // empty list (visually wiping history while the refresh "succeeds").
                val serverMessages = repository.listMessages(thread.id)
                val existingLocal = thread(thread.id)?.messages ?: emptyList()
                thread.copy(messages = mergePreservingOptimistic(serverMessages, existingLocal))
            }
            val remoteIds = hydrated.map { it.id }.toSet()
            // Keep local-only (non-UUID) threads the server can't report.
            val localOnly = _threads.value.filter { !it.id.isUuid() && it.id !in remoteIds }
            _threads.value = hydrated + localOnly
        } catch (t: Throwable) {
            _errors.tryEmit(t.message ?: "Couldn't load messages.")
        }
    }

    /** Per-thread message refresh (pull-to-refresh / re-open). Silent-on-failure, preserves optimistic. */
    suspend fun refreshMessages(threadId: String) {
        if (thread(threadId) == null) return
        try {
            val serverMessages = repository.listMessages(threadId)
            updateThread(threadId) { t ->
                t.copy(messages = mergePreservingOptimistic(serverMessages, t.messages))
            }
        } catch (_: LocalThreadNotPersistable) {
            // Local thread — nothing to hydrate; keep the on-device messages.
        } catch (t: Throwable) {
            _errors.tryEmit(t.message ?: "Couldn't load messages.")
        }
    }

    /**
     * Merge server messages with any local optimistic messages (`.Sending` /
     * `.Failed`) not yet server-side, so a refresh never drops an in-flight send or
     * a `.Failed` message awaiting retry (server rows always decode `.Sent`).
     */
    private fun mergePreservingOptimistic(server: List<Message>, existing: List<Message>): List<Message> {
        val serverIds = server.map { it.id }.toSet()
        val pending = existing.filter { it.delivery != MessageDelivery.Sent && it.id !in serverIds }
        return if (pending.isEmpty()) server else (server + pending).sortedBy { it.sentAt }
    }

    // ---- Realtime subscription seam ----

    /**
     * Open the Realtime channel for [threadId], routing each insert into
     * [receiveRealtimeMessage]. Returns the [Job] the screen cancels on dispose.
     * A no-op Job when realtime is off / the thread is local (handled in the repo).
     */
    fun subscribe(threadId: String): Job =
        repository.subscribeMessages(threadId, scope) { receiveRealtimeMessage(threadId, it) }

    // ---- Redaction (anti-leakage moat) ----

    /**
     * Whether to redact contact info for display. Redaction LIFTS once the thread's
     * booking is confirmed/completed; a bookingless inquiry always redacts. The
     * server trigger is authoritative — this is the display-layer belt-and-suspenders
     * for optimistic echoes + previews (applying it to an already-redacted body is a
     * no-op). Caller supplies [bookingConfirmed] (resolved via BookingStore).
     */
    fun shouldRedact(thread: Thread, bookingConfirmed: Boolean): Boolean =
        !(thread.bookingId != null && bookingConfirmed)

    /** Body as it should DISPLAY: redacted while the booking isn't confirmed, else raw. */
    fun displayBody(body: String, thread: Thread, bookingConfirmed: Boolean): String =
        if (shouldRedact(thread, bookingConfirmed)) Redaction.redact(body) else body

    // ---- helpers ----

    private fun updateThread(threadId: String, transform: (Thread) -> Thread) {
        _threads.update { list -> list.map { if (it.id == threadId) transform(it) else it } }
    }

    private companion object {
        // A same-body realtime echo must land near its optimistic send in time so a
        // genuine repeat / an unrelated older in-flight bubble can't be swallowed.
        const val CONTENT_MATCH_WINDOW_MS = 15_000L
    }
}

private fun completedJob(): Job = Job().apply { complete() }
private fun String.isUuid(): Boolean = runCatching { UUID.fromString(this) }.isSuccess
