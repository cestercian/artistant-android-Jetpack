package `in`.artistant.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.MessagesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ThreadListItem(val thread: Thread, val counterpartName: String)
data class MessagesUiState(
    val threads: List<ThreadListItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val messagesRepository: MessagesRepository,
    private val artistsRepository: ArtistsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching { messagesRepository.listThreadsForUser() }
            .onSuccess { threads ->
                val rows = threads.map { thread ->
                    val name = thread.clientName?.takeIf { it.isNotBlank() }
                        ?: artistsRepository.find(thread.artistId)?.name
                        ?: "Artist"
                    ThreadListItem(thread, name)
                }
                _state.update { it.copy(threads = rows, isLoading = false) }
            }
            .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
    }

    /** Opens a real participant-pair thread; navigation receives only server UUIDs. */
    fun findOrCreateThread(artistId: String, onReady: (String) -> Unit) = viewModelScope.launch {
        runCatching { messagesRepository.findOrCreateThread(artistId) }
            .onSuccess(onReady)
            .onFailure { e -> _state.update { it.copy(error = e.message) } }
    }
}
