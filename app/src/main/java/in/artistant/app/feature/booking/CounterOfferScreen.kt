package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.RequestsRepositoryError
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CounterOfferUiState(
    val request: StoredRequest? = null,
    val amount: String = "",
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val isSending: Boolean = false,
    val sendError: String? = null,
    val sent: Boolean = false,
) {
    /** What they offered — the number that stays on screen above the field. */
    val theirOffer: Int get() = request?.raw?.amount ?: 0

    /** Who and what for: "Rhea Menon · sangeet, 200 guests", minus what is null. */
    val reference: String get() = counterReferenceLine(request)

    val delta: String? get() = counterDeltaLine(theirOffer, amount.toIntOrNull())

    val canSend: Boolean get() = !isSending && counterAmountError(amount) == null
}

/**
 * Screen 61 — the counter offer.
 *
 * The design's note is the interaction: **their number stays visible.** You never
 * type blind, so the offer being answered sits in a card directly above the field
 * answering it, and the delta between the two is spelled out underneath as you
 * type.
 *
 * **Whose counter this is.** The brief framed 61 as the client's reply to an
 * artist's counter. The schema says otherwise, and so does the design's own copy
 * ("Your full band package is ₹36,000" is the artist speaking): `gig_requests`
 * has exactly one UPDATE policy — `gig_requests_update_artist` (mig 0002) — and
 * the client's only writes are INSERT and a DELETE limited to `status = 'open'`.
 * A client-side counter would be a form whose submit RLS refuses, which is worse
 * than not shipping it. So this is the artist's counter, on the one write the
 * server permits, and the client-side reply is listed as blocked on the backend.
 *
 * The destination is `counter_offer/{requestId}` in the artist graph. The gig
 * detail screen (`feature/gigs`, another section's file) keeps its own inline
 * sheet until that section is rewritten; both call the same
 * `RequestsRepository.counter`, so they cannot disagree about what a counter is.
 */
@HiltViewModel
class CounterOfferViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val requestsRepository: RequestsRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _state = MutableStateFlow(CounterOfferUiState())
    val state: StateFlow<CounterOfferUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                val found = requestsRepository.listForArtist()
                    .firstOrNull { it.id.equals(requestId, ignoreCase = true) }
                _state.update {
                    it.copy(
                        request = found,
                        // Seeded with THEIR number, not blank: a counter starts
                        // from the offer it is answering, and an empty field
                        // makes the artist retype a figure that is on screen.
                        amount = found?.raw?.amount?.toString().orEmpty(),
                        isLoading = false,
                        loadError = if (found == null) "This request isn't available." else null,
                    )
                }
            } catch (e: RequestsRepositoryError) {
                _state.update { it.copy(isLoading = false, loadError = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.message) }
            }
        }
    }

    /** Digits only — the field opens the number pad and the column is an int. */
    fun setAmount(value: String) {
        _state.update { it.copy(amount = value.filter { c -> c.isDigit() }, sendError = null) }
    }

    fun send() {
        val s = _state.value
        val error = counterAmountError(s.amount)
        if (error != null) {
            _state.update { it.copy(sendError = error) }
            return
        }
        if (s.isSending) return
        viewModelScope.launch {
            _state.update { it.copy(isSending = true, sendError = null) }
            try {
                requestsRepository.counter(requestId, s.amount.toInt())
                _state.update { it.copy(isSending = false, sent = true) }
            } catch (e: RequestsRepositoryError) {
                _state.update { it.copy(isSending = false, sendError = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSending = false, sendError = e.message ?: "Couldn't send the counter.")
                }
            }
        }
    }
}

/**
 * Why a counter cannot be sent, or null when it can.
 *
 * A blank field and a zero are different mistakes and get different words. Zero
 * matters specifically: `counter` flips the request to `countered`, which takes
 * it out of the Accept/Decline dock, so a ₹0 counter is an unrecoverable state
 * reached by clearing a number pad.
 */
fun counterAmountError(amount: String): String? {
    val digits = amount.trim()
    if (digits.isEmpty()) return "Enter your number."
    val value = digits.toIntOrNull() ?: return "Enter your number."
    if (value <= 0) return "A counter has to be above ₹0."
    return null
}

/**
 * "₹4,000 above their offer" / "₹2,000 below their offer" / "The same as their
 * offer" — the line under the field.
 *
 * The design writes this as an artist's own sentence ("Your full band package is
 * ₹36,000 — this is +₹6,000 for the lawn set-up"), which needs a package price
 * and a reason neither the request nor this screen has. The arithmetic between
 * the two numbers on screen is the half that IS true, and it is the half that
 * stops the artist typing blind. Null while there is nothing to compare.
 */
fun counterDeltaLine(theirOffer: Int, yours: Int?): String? {
    if (theirOffer <= 0 || yours == null || yours <= 0) return null
    val diff = yours - theirOffer
    return when {
        diff > 0 -> "${formatInr(diff)} above their offer of ${formatInr(theirOffer)}"
        diff < 0 -> "${formatInr(-diff)} below their offer of ${formatInr(theirOffer)}"
        else -> "The same as their offer"
    }
}

/**
 * "Rhea Menon · 200 guests" — who is asking and what for, minus whatever is null.
 *
 * `gig_requests` carries no occasion column, so the design's "sangeet" is not
 * available; the head count is, and the requester's name is (mig 0100's
 * denormalized `client_name`, null on rows written before the backfill). A
 * missing name is left out rather than filled with "Client", which would print
 * the same fact about every requester.
 */
fun counterReferenceLine(request: StoredRequest?): String {
    if (request == null) return ""
    return listOfNotNull(
        request.requesterName,
        request.raw.date.trim().takeIf { it.isNotEmpty() },
    ).joinToString(" · ")
}

@Composable
fun CounterOfferScreen(
    onDismiss: () -> Unit,
    onSent: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CounterOfferViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    androidx.compose.runtime.LaunchedEffect(state.sent) {
        if (state.sent) onSent()
    }

    // A destination that LOOKS like a sheet: the design draws 61 as a panel over
    // a dimmed page, and it is reached as a pushed route rather than from a
    // composable that could host a ModalBottomSheet. The scrim is tappable and
    // dismisses, which is the behaviour a sheet's own scrim would have.
    Box(
        modifier
            .fillMaxSize()
            .background(colors.glassSoftScrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(Modifier.align(Alignment.BottomCenter)) {
            SheetScaffold(
                // Swallows taps so a press inside the panel does not reach the
                // scrim underneath and dismiss the sheet the artist is typing in.
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { },
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = space.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Cancel",
                        style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.ink4,
                        modifier = Modifier
                            .clip(RoundedCornerShape(dimens.radii.sm))
                            .clickable(onClick = onDismiss)
                            .padding(vertical = space.sm)
                            .padding(end = space.sm),
                    )
                    Text(
                        "Counter offer",
                        style = AppTheme.type.sectionTitle,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconCircle(
                        icon = Icons.Filled.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                        size = dimens.size.avatarSm,
                    )
                }

                when {
                    state.isLoading && state.request == null -> {
                        Text(
                            "Reading the request…",
                            style = AppTheme.type.body,
                            color = colors.ink3,
                            modifier = Modifier.padding(bottom = space.xl),
                        )
                    }
                    state.request == null -> {
                        EmptyState(
                            title = "Nothing to counter",
                            body = state.loadError,
                            actionLabel = "Try again",
                            onAction = viewModel::refresh,
                        )
                    }
                    else -> {
                        FunnelCard {
                            Text(
                                "Their offer",
                                style = AppTheme.type.caption.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = colors.ink4,
                            )
                            Row(
                                Modifier.padding(top = space.sm),
                                horizontalArrangement = Arrangement.spacedBy(space.sm),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                Text(
                                    formatInr(state.theirOffer),
                                    style = AppTheme.type.displaySmall,
                                    color = colors.ink,
                                )
                                if (state.reference.isNotEmpty()) {
                                    Text(
                                        state.reference,
                                        style = AppTheme.type.caption,
                                        color = colors.ink4,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(bottom = space.xs),
                                    )
                                }
                            }
                        }

                        Text(
                            "Your number",
                            style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink4,
                            modifier = Modifier.padding(top = space.lg, bottom = space.sm),
                        )
                        AmountField(
                            value = state.amount,
                            onValueChange = viewModel::setAmount,
                        )
                        state.delta?.let { line ->
                            Text(
                                line,
                                style = AppTheme.type.caption,
                                color = colors.ink4,
                                modifier = Modifier.padding(top = space.sm),
                            )
                        }
                        state.sendError?.let { message ->
                            Banner(
                                title = message,
                                tone = BannerTone.Failure,
                                modifier = Modifier.padding(top = space.md),
                            )
                        }
                        AccentNote(
                            "Countering keeps the request open. They can accept, counter back, " +
                                "or decline.",
                            modifier = Modifier.padding(top = space.lg),
                        )
                        PrimaryButton(
                            text = if (state.isSending) "Sending counter…" else "Send counter",
                            onClick = viewModel::send,
                            fullWidth = true,
                            enabled = state.canSend,
                            modifier = Modifier.padding(top = space.xl),
                        )
                        if (state.request?.status == GigRequestStatus.Countered) {
                            Text(
                                "You've already countered this one.",
                                style = AppTheme.type.caption,
                                color = colors.ink4,
                                modifier = Modifier.padding(top = space.sm),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The amount well: a rupee sign, then the number, in the design's own weight.
 *
 * `BasicTextField` rather than [AppTextField] because this field IS the screen —
 * it is set at 22sp with the currency as a separate glyph, and squeezing that
 * into the shared 15sp control would make the one thing being decided the
 * quietest thing on the panel.
 */
@Composable
private fun AmountField(value: String, onValueChange: (String) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.control)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(dimens.component.focusStroke, colors.ink, shape)
            .defaultMinSize(minHeight = dimens.funnel.amountField)
            .padding(horizontal = dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Text("₹", style = AppTheme.type.displaySmall, color = colors.ink4)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = AppTheme.type.displaySmall.copy(color = colors.ink),
            singleLine = true,
            cursorBrush = SolidColor(colors.accentInk),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
