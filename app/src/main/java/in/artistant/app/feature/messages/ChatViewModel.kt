package `in`.artistant.app.feature.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import `in`.artistant.app.data.repository.MessagesSubscription
import `in`.artistant.app.data.repository.ReportsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val thread: Thread? = null,
    val title: String = "Chat",
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val counterpartLastReadAt: Long? = null,
    val showDetails: Boolean = false,
    /**
     * Which side of the thread the viewer sits on. Drives the participant role
     * labels in the details sheet and the "Name · Role · time" caption on
     * incoming runs — both of which are the opposite of the viewer's own role.
     */
    val viewerIsArtist: Boolean = false,
)

/**
 * Chat VM: poll-on-open + Realtime INSERT + optimistic send with 3-way reconcile
 * (local optimistic ↔ send RETURNING ↔ Realtime echo), matching iOS MessageStore.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messagesRepository: MessagesRepository,
    private val artistsRepository: ArtistsRepository,
    private val reports: ReportsRepository,
    private val viewer: ViewerIdentity,
) : ViewModel() {
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var subscription: MessagesSubscription? = null
    /** Bumped on each subscribe attempt so a superseded join is discarded. */
    private var subscribeGeneration = 0

    init {
        refresh()
        subscribeRealtime()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching {
            val thread = messagesRepository.listThreadsForUser().firstOrNull { it.id == threadId }
            val messages = messagesRepository.listMessages(threadId)
            thread to messages
        }.onSuccess { (thread, serverMessages) ->
            // Same seat-aware rule as the inbox row — see ThreadCounterpart. The
            // title also feeds the details sheet's `counterpartLabel`, so fixing
            // it here fixes the Participants list that used to print the viewer's
            // own name directly above "You".
            val viewerId = viewer.currentUserId()
            val viewerIsArtist = thread != null && ThreadCounterpart.viewerIsArtist(thread, viewerId)
            // No thread row (load failed / not a participant) means no seat to
            // resolve from, so stay on the neutral header rather than guessing.
            val title = thread?.let {
                ThreadCounterpart.name(
                    thread = it,
                    viewerId = viewerId,
                    artistName = artistsRepository.find(it.artistId)?.name,
                )
            } ?: "Chat"
            _state.update { state ->
                state.copy(
                    thread = thread,
                    title = title,
                    viewerIsArtist = viewerIsArtist,
                    messages = ChatRealtimeLogic.mergePreservingOptimistic(
                        server = serverMessages,
                        existing = state.messages,
                    ),
                    isLoading = false,
                )
            }
            markReadBestEffort()
        }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
    }

    /**
     * Optimistic insert (`.sending`) then background write. Cap at 4000 chars —
     * same choke point as iOS for both composer and retry.
     */
    fun send(body: String) {
        val capped = body.take(4000)
        val text = capped.trim()
        if (text.isEmpty()) return
        val optimisticId = "optimistic-${System.currentTimeMillis()}"
        val optimistic = Message(
            id = optimisticId,
            threadId = threadId,
            body = capped,
            sentAtEpochMs = System.currentTimeMillis(),
            isMine = true,
            delivery = MessageDelivery.Sending,
        )
        _state.update { it.copy(messages = it.messages + optimistic, error = null) }
        deliver(optimisticId, capped)
    }

    fun retryFailedMessage(messageId: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        if (message.delivery != MessageDelivery.Failed) return
        _state.update {
            it.copy(messages = ChatRealtimeLogic.markDelivery(it.messages, messageId, MessageDelivery.Sending))
        }
        deliver(messageId, message.body)
    }

    private fun deliver(optimisticId: String, body: String) = viewModelScope.launch {
        runCatching { messagesRepository.send(threadId, body) }
            .onSuccess { server ->
                _state.update {
                    it.copy(
                        messages = ChatRealtimeLogic.reconcileSendSuccess(
                            existing = it.messages,
                            optimisticId = optimisticId,
                            server = server,
                        ),
                    )
                }
                markReadBestEffort()
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        messages = ChatRealtimeLogic.markDelivery(
                            it.messages,
                            optimisticId,
                            MessageDelivery.Failed,
                        ),
                        error = e.message,
                    )
                }
            }
    }

    private fun subscribeRealtime() = viewModelScope.launch {
        subscribeGeneration += 1
        val myGeneration = subscribeGeneration
        // Tear down any prior channel before joining (foreground re-subscribe).
        subscription?.cancel()
        subscription = null
        val sub = messagesRepository.subscribeMessages(threadId) { incoming ->
            // Realtime callback may arrive off Main; ViewModel updates must be serialised.
            viewModelScope.launch {
                if (myGeneration != subscribeGeneration) return@launch
                _state.update {
                    it.copy(messages = ChatRealtimeLogic.receiveRealtimeMessage(it.messages, incoming))
                }
            }
        }
        if (myGeneration != subscribeGeneration) {
            sub.cancel()
            return@launch
        }
        subscription = sub
    }

    private fun markReadBestEffort() = viewModelScope.launch {
        runCatching { messagesRepository.markThreadRead(threadId) }
        runCatching { messagesRepository.markThreadReadReceipt(threadId) }
        val readAt = runCatching { messagesRepository.counterpartLastRead(threadId) }.getOrNull()
        _state.update { it.copy(counterpartLastReadAt = readAt) }
    }

    fun openDetails() = _state.update { it.copy(showDetails = true) }
    fun dismissDetails() = _state.update { it.copy(showDetails = false) }

    fun reportConversation(reason: String) = viewModelScope.launch {
        runCatching { reports.reportConversation(threadId, reason) }
    }

    override fun onCleared() {
        subscribeGeneration += 1
        subscription?.cancel()
        subscription = null
        super.onCleared()
    }
}
