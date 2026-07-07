package `in`.artistant.app.feature.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.state.BookingStore
import `in`.artistant.app.state.MessageStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the shared `ChatScreen` (port of iOS `ChatView`). The message list lives
 * in the process-wide [MessageStore]; this VM is the per-thread orchestrator —
 * ensure/refresh/mark-read on open, optimistic send + retry, and the Realtime
 * subscription lifecycle (open on foreground, tear down on background/dispose).
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val store: MessageStore,
    private val artists: ArtistsRepository,
    private val bookings: BookingStore,
) : ViewModel() {

    val threadId: String = savedStateHandle.get<String>("threadId").orEmpty()

    /**
     * This thread, tracked out of the store (re-emits on send/receive/refresh).
     * Eagerly (not WhileSubscribed) because the VM reads [thread]`.value` synchronously
     * from [displayBody] / [artistName] during composition — a lazily-started flow
     * would hand back the stale construction-time snapshot there.
     */
    val thread: StateFlow<Thread?> =
        store.threads
            .map { list -> list.firstOrNull { it.id == threadId } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, store.thread(threadId))

    /**
     * Messages in chronological order. Optimistic inserts (local `Date`) and realtime
     * echoes arrive out of band, so sort by sentAt at render; tie-break on id so
     * same-instant rows keep a STABLE order across recompositions (parity with iOS
     * `orderedMessages`).
     */
    val messages: StateFlow<List<Message>> =
        thread
            .map { t ->
                (t?.messages ?: emptyList()).sortedWith(
                    compareBy({ it.sentAt }, { it.id }),
                )
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- Realtime subscription lifecycle -------------------------------------
    // store.subscribe() launches the channel collect on the store's own scope and
    // returns the Job; cancelling the Job tears the channel + WebSocket down. We
    // hold the latest Job + a generation token: teardown cancels and bumps the
    // generation so any superseded subscribe is authoritatively retired (iOS's
    // onDisappear generation bump). The token is belt-and-suspenders here — subscribe
    // returns synchronously — but keeps the invariant if it ever goes async.
    private var realtimeJob: Job? = null
    private var generation = 0

    /**
     * Open the thread: ensure it's in the store (a chat reached from find-or-create
     * isn't in the list yet — pull the set), hydrate messages + the header artist,
     * and mark read. Silent-on-failure paths live in the store.
     */
    fun load() {
        viewModelScope.launch {
            if (store.thread(threadId) == null) store.refreshFromServer()
            store.refreshMessages(threadId)
            store.thread(threadId)?.artistId?.let { artists.ensureFull(it) }
            store.markRead(threadId)
        }
    }

    /** Foreground return: fill the gap missed while backgrounded, then re-subscribe. */
    fun onForeground() {
        viewModelScope.launch { store.refreshMessages(threadId) }
        subscribeRealtime()
    }

    /** (Re)open the Realtime channel. Gated inside the repo on realtimeEnabled + a UUID thread. */
    fun subscribeRealtime() {
        realtimeJob?.cancel()
        generation++
        realtimeJob = store.subscribe(threadId)
    }

    /** Tear the channel down (background / dispose). Idempotent. */
    fun teardownRealtime() {
        realtimeJob?.cancel()
        realtimeJob = null
        generation++
    }

    // Return the store's write Job so a test can join() it deterministically (the
    // store settles the send on its own dispatcher; the screen ignores the return).
    fun send(body: String): Job = store.send(threadId, body)

    fun retry(messageId: String): Job = store.retryFailedMessage(threadId, messageId)

    /** The client-viewer's header artist (null until hydrated / for the artist viewer). */
    fun artistName(): String? = thread.value?.artistId?.let { artists.find(it)?.name }

    fun artistId(): String? = thread.value?.artistId

    /**
     * Per-bubble redaction gate (the anti-leakage moat). Bodies stay masked until the
     * thread's booking is confirmed/completed; a bookingless inquiry always redacts.
     * The server trigger is authoritative — this mirrors it for optimistic/echoed
     * bodies. Applied per bubble in the composable so each row masks independently.
     */
    fun displayBody(raw: String): String {
        val t = thread.value ?: return raw
        return store.displayBody(raw, t, bookingConfirmed(t))
    }

    private fun bookingConfirmed(thread: Thread): Boolean {
        val status = thread.bookingId?.let { bookings.booking(it)?.status } ?: return false
        return status == BookingStatus.Confirmed || status == BookingStatus.Completed
    }

    override fun onCleared() {
        teardownRealtime()
    }
}
