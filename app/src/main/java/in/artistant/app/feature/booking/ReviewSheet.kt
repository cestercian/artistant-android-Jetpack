package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.ReviewRepositoryError
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewSheetUiState(
    val rating: Int = 5,
    val body: String = "",
    val isSubmitting: Boolean = false,
    val error: String? = null,
    /** One-shot: the insert landed. Consumed by the sheet, which then closes. */
    val submitted: Boolean = false,
)

/**
 * The review write, off the composition.
 *
 * It exists for one reason. [ReviewSheet] used to launch `insert` on
 * `rememberCoroutineScope()`, whose Job is the sheet's own slot in the
 * composition — and the sheet is a `ModalBottomSheet` its host dismisses on a
 * scrim tap or a downward drag, with no guard while the write is in flight. So a
 * client who tapped "Submit review" and swiped the sheet away during a slow
 * request had the insert cancelled mid-flight, silently: the catch never fired
 * (`catch (e: Exception)` would have swallowed the CancellationException
 * anyway), nothing was persisted, no error appeared, and the booking still
 * offered "Leave a review". They walked away believing they had reviewed the
 * artist.
 *
 * `viewModelScope` here belongs to the host destination's back-stack entry, not
 * to the sheet, so the request outlives a dismissal and only dies when the
 * booking screen itself is popped. It also puts the repository back behind a
 * ViewModel, where house rule 4 wants it — the sheet used to be handed a
 * `ReviewsRepository` through [BookingDetailViewModel] and call it from
 * composition-scoped code.
 */
@HiltViewModel
class ReviewSheetViewModel @Inject constructor(
    private val reviewsRepository: ReviewsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewSheetUiState())
    val state: StateFlow<ReviewSheetUiState> = _state.asStateFlow()

    fun setRating(value: Int) = _state.update { it.copy(rating = value.coerceIn(1, 5)) }

    fun setBody(value: String) = _state.update { it.copy(body = value) }

    fun submit(bookingId: String) {
        if (_state.value.isSubmitting) return
        val rating = _state.value.rating
        val body = _state.value.body.ifBlank { null }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, error = null) }
            try {
                reviewsRepository.insert(
                    bookingId = bookingId,
                    rating = rating,
                    body = body,
                    categories = null,
                )
                // Reset alongside the flag: this VM is scoped to the booking
                // screen, so it outlives the sheet, and re-opening it after a
                // submitted review must not re-offer the same stars and text.
                _state.update { ReviewSheetUiState(submitted = true) }
            } catch (e: ReviewRepositoryError) {
                _state.update { it.copy(isSubmitting = false, error = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSubmitting = false, error = e.message ?: "Couldn't submit review.")
                }
            }
        }
    }

    fun consumeSubmitted() = _state.update { it.copy(submitted = false) }
}

/**
 * Thin review submit sheet — port of iOS `ReviewSheet`.
 *
 * State-driven and dumb: everything it can decide lives in [ReviewSheetViewModel],
 * which is resolved against the host destination rather than against this sheet.
 *
 * It does NOT watch `submitted` itself. The insert outlives a scrim dismiss (that
 * is the whole point of hoisting it into the ViewModel), so an effect living here
 * would simply not be in composition when a write landed after the sheet closed:
 * the host would never hear about it, the booking would keep offering "Leave a
 * review", and reopening the sheet would flash shut on the stale flag. The host
 * owns that effect — see `BookingDetailScreen`.
 */
@Composable
fun ReviewSheet(
    bookingId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewSheetViewModel = hiltViewModel(),
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()

    // Rounded top only — this is a bottom sheet, so its lower edge is the screen
    // edge and has no corner to soften.
    Column(
        modifier
            .dockSurface()
            .padding(space.lg),
    ) {
        Text("Leave a review", style = AppTheme.type.headline, color = colors.ink)
        Spacer(Modifier.height(space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            (1..5).forEach { star ->
                Text(
                    if (star <= ui.rating) "★" else "☆",
                    style = AppTheme.type.title,
                    color = if (star <= ui.rating) colors.warm else colors.ink3,
                    modifier = Modifier
                        .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                        .background(colors.bgSoft)
                        .clickable {
                            // Selection tick on the star row, as on iOS. The
                            // matching success buzz for a landed insert is the
                            // HOST's (BookingDetailScreen watches `submitted`) —
                            // the write outlives this sheet, so an effect here
                            // would not be in composition when it lands.
                            haptics.select()
                            viewModel.setRating(star)
                        }
                        .padding(horizontal = space.sm, vertical = space.xs),
                )
            }
        }
        Spacer(Modifier.height(space.lg))
        BasicTextField(
            value = ui.body,
            onValueChange = viewModel::setBody,
            textStyle = AppTheme.type.body.copy(color = colors.ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                .background(colors.bgSoft)
                .padding(space.md),
            decorationBox = { inner ->
                if (ui.body.isEmpty()) {
                    Text("Share how the show went…", style = AppTheme.type.body, color = colors.ink3)
                }
                inner()
            },
        )
        ui.error?.let {
            Spacer(Modifier.height(space.sm))
            Text(it, style = AppTheme.type.footnote, color = colors.hot)
        }
        Spacer(Modifier.height(space.lg))
        PrimaryButton(
            text = if (ui.isSubmitting) "Submitting…" else "Submit review",
            onClick = { viewModel.submit(bookingId) },
            fullWidth = true,
            enabled = !ui.isSubmitting,
        )
        Spacer(Modifier.height(space.sm))
        PrimaryButton(text = "Cancel", onClick = onDismiss, variant = ButtonVariant.Ghost, fullWidth = true)
    }
}
