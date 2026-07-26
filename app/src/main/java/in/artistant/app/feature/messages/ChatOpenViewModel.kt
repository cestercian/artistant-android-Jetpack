package `in`.artistant.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.MessagesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Turns a "Message this artist" tap into a real chat destination. The Message
 * buttons on ArtistProfile + BookingDetail hand us an artistId (and, for a
 * booking, its id); we [MessagesRepository.findOrCreateThread] the server thread
 * row and hand back its UUID so the caller can push `Chat(threadId)`.
 *
 * Ported from iOS's route-to-`t-<artistId>`-then-`ensureLocalThread` dance, but
 * Android resolves the REAL thread up front (cleaner than a local placeholder id) —
 * so ChatScreen always opens on a persisted thread and send() never no-ops.
 */
@HiltViewModel
class ChatOpenViewModel @Inject constructor(
    private val messages: MessagesRepository,
) : ViewModel() {

    // Drives a blocking spinner while the find-or-create round-trips. Guards against
    // a double-tap firing two inserts (the unique-index would collapse them, but the
    // extra request + double navigate is still wrong).
    private val _opening = MutableStateFlow(false)
    val opening: StateFlow<Boolean> = _opening.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Resolve (or create) the thread for [artistId]/[bookingId], then invoke [onThread]. */
    fun open(artistId: String, bookingId: String?, onThread: (String) -> Unit) {
        if (_opening.value) return
        _error.value = null
        viewModelScope.launch {
            _opening.value = true
            try {
                onThread(messages.findOrCreateThread(artistId, bookingId))
            } catch (t: Throwable) {
                _error.value = t.message ?: "Couldn't open the conversation."
            } finally {
                _opening.value = false
            }
        }
    }

    fun dismissError() { _error.value = null }
}
