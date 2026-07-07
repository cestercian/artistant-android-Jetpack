package `in`.artistant.app.feature.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.ReviewError
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The review-write lifecycle (port of iOS `ReviewSheet`'s submit path). Reads the
 * booking id from the hosting `BookingDetail` nav entry, then inserts via
 * [ReviewsRepository], mapping the typed [ReviewError]s to user copy — including
 * the already-reviewed case (a re-tap after a successful submit self-corrects).
 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val reviews: ReviewsRepository,
) : ViewModel() {

    private val bookingId: String = savedStateHandle.get<String>("bookingId").orEmpty()

    data class UiState(val submitting: Boolean = false, val error: String? = null, val done: Boolean = false)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun submit(rating: Int, body: String?) {
        if (rating !in 1..5 || _state.value.submitting) return
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                reviews.insert(bookingId, rating, body?.trim()?.takeIf { it.isNotEmpty() })
                _state.update { it.copy(submitting = false, done = true) }
            } catch (e: ReviewError) {
                _state.update { it.copy(submitting = false, error = message(e)) }
            } catch (t: Throwable) {
                _state.update { it.copy(submitting = false, error = "Couldn't submit review. Try again in a moment.") }
            }
        }
    }

    private fun message(e: ReviewError): String = when (e) {
        ReviewError.AlreadyReviewed -> "You've already left a review for this booking."
        ReviewError.InvalidRating -> "Pick a rating between 1 and 5 stars."
        ReviewError.NotSignedIn -> "Sign in to leave a review."
        ReviewError.BookingNotFound -> "Booking not found — pull-to-refresh and try again."
        ReviewError.BookingNotCompleted -> "You can review this booking once the show is over."
    }
}

/**
 * Modal review sheet — 1-5 stars + optional note, submit → [ReviewsRepository].
 * Closes on a successful write (or Cancel). Kept pinned while the write is in flight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(
    artistName: String?,
    onDismiss: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var rating by remember { mutableIntStateOf(0) }
    var body by remember { mutableStateOf("") }
    val maxBody = 200

    // Dismiss once the write lands.
    LaunchedEffect(state.done) { if (state.done) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.bgElev) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = space.xl).padding(bottom = space.xl),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            Text(
                artistName?.let { "How was $it?" } ?: "Leave a review",
                style = AppTheme.type.displayMedium,
                color = colors.ink,
            )

            // Star row.
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                (1..5).forEach { idx ->
                    Icon(
                        imageVector = if (idx <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "$idx star",
                        tint = if (idx <= rating) colors.brand else colors.ink4,
                        modifier = Modifier.size(44.dp).clickable { rating = idx },
                    )
                }
            }

            OutlinedTextField(
                value = body,
                onValueChange = { if (it.length <= maxBody) body = it },
                label = { Text("Note (optional)", color = colors.ink3) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${body.length} / $maxBody", style = AppTheme.type.monoSmall, color = colors.ink4)

            state.error?.let { Text(it, style = AppTheme.type.footnote, color = colors.hot) }

            Spacer(Modifier.height(space.xs))
            SubmitButton(
                enabled = rating in 1..5 && !state.submitting,
                submitting = state.submitting,
                onClick = { viewModel.submit(rating, body) },
            )
        }
    }
}

@Composable
private fun SubmitButton(enabled: Boolean, submitting: Boolean, onClick: () -> Unit) {
    `in`.artistant.app.designsystem.component.PrimaryButton(
        text = if (submitting) "Submitting…" else "Submit",
        onClick = onClick,
        fullWidth = true,
        enabled = enabled,
    )
}
