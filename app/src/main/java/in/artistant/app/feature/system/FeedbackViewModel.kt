package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.BookingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `app_feedback.is_bug` as the two labels the design draws (screen 64). */
enum class FeedbackKind(val label: String) {
    General("General"),
    Bug("Bug"),
    ;

    val isBug: Boolean get() = this == Bug
}

/**
 * What happened to the note the user pressed Send on.
 *
 * Three outcomes and not two, because "sent" and "queued" are different facts
 * and the screen promises the second one out loud. Collapsing them into a
 * cheerful "Thanks!" is the failure the design's note is about.
 */
enum class FeedbackOutcome { Sent, Queued }

data class FeedbackUiState(
    val body: String = "",
    val kind: FeedbackKind = FeedbackKind.General,
    val sending: Boolean = false,
    val outcome: FeedbackOutcome? = null,
) {
    val remaining: Int get() = FEEDBACK_MAX_CHARS - body.length
    val canSend: Boolean get() = body.isNotBlank() && !sending
}

/**
 * `app_feedback.body` is `check (length(body) between 1 and 2000)` (mig 0073).
 *
 * Enforced in the composer rather than discovered at insert time: a 2,100-word
 * note rejected by a constraint after the user pressed Send is a note the user
 * has to retype, and the design draws the counter for exactly this reason.
 */
const val FEEDBACK_MAX_CHARS = 2_000

/**
 * Screen 64 — Send feedback.
 *
 * The write is [BookingsRepository.submitFeedback], which already lived on that
 * repository (ACCT-12) and returns false rather than throwing on a dropped
 * insert. That false is what makes the queue possible: a failed send is not an
 * error the user has to act on, it is a note that has not gone yet.
 */
@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val bookings: BookingsRepository,
    private val outbox: FeedbackOutbox,
) : ViewModel() {

    private val _state = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    init {
        // Anything left over from a previous session goes now, while there is
        // probably a network — the same moment the copy calls "your next live
        // session". WorkManager covers the case where there isn't.
        viewModelScope.launch { runCatching { outbox.drain() } }
    }

    fun setBody(value: String) = _state.update { it.copy(body = value.take(FEEDBACK_MAX_CHARS)) }

    fun setKind(kind: FeedbackKind) = _state.update { it.copy(kind = kind) }

    fun send() {
        val current = _state.value
        if (!current.canSend) return
        val body = current.body.trim()
        if (body.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(sending = true, outcome = null) }
            val sent = runCatching { bookings.submitFeedback(body, current.kind.isBug) }
                .getOrElse { false }
            if (!sent) {
                outbox.enqueue(
                    PendingFeedback(
                        body = body,
                        isBug = current.kind.isBug,
                        writtenAtMs = System.currentTimeMillis(),
                    ),
                )
            }
            // The box empties either way. The note is safe in both outcomes —
            // on the server or on the device — and leaving the text behind
            // invites a second copy of a note that is already queued.
            _state.update {
                FeedbackUiState(
                    kind = it.kind,
                    outcome = if (sent) FeedbackOutcome.Sent else FeedbackOutcome.Queued,
                )
            }
        }
    }

    fun consumeOutcome() = _state.update { it.copy(outcome = null) }
}
