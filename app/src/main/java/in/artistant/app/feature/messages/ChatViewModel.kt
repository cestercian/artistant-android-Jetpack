package `in`.artistant.app.feature.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.MessagesRepository
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
    val isSending: Boolean = false,
    val error: String? = null,
    val counterpartLastReadAt: Long? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messagesRepository: MessagesRepository,
    private val artistsRepository: ArtistsRepository,
) : ViewModel() {
    private val threadId: String = checkNotNull(savedStateHandle["threadId"])
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching {
            val thread = messagesRepository.listThreadsForUser().firstOrNull { it.id == threadId }
            val messages = messagesRepository.listMessages(threadId)
            thread to messages
        }.onSuccess { (thread, messages) ->
            val title = thread?.clientName?.takeIf { it.isNotBlank() }
                ?: thread?.let { artistsRepository.find(it.artistId)?.name }
                ?: "Chat"
            _state.update { it.copy(thread = thread, title = title, messages = messages, isLoading = false) }
            markReadBestEffort()
        }.onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
    }

    fun send(body: String) {
        val text = body.trim()
        if (text.isEmpty() || _state.value.isSending) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, error = null) }
            runCatching { messagesRepository.send(threadId, text) }
                .onSuccess { sent ->
                    _state.update { state ->
                        state.copy(
                            messages = (state.messages + sent).distinctBy { it.id },
                            isSending = false,
                        )
                    }
                    markReadBestEffort()
                }
                .onFailure { e -> _state.update { it.copy(isSending = false, error = e.message) } }
        }
    }

    private fun markReadBestEffort() = viewModelScope.launch {
        runCatching { messagesRepository.markThreadRead(threadId) }
        runCatching { messagesRepository.markThreadReadReceipt(threadId) }
        val readAt = runCatching { messagesRepository.counterpartLastRead(threadId) }.getOrNull()
        _state.update { it.copy(counterpartLastReadAt = readAt) }
    }
}
