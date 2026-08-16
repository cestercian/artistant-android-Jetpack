package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.BookingStatusTimeline
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.signup.EditorialHeadline
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything on this screen has a defensible default.
 *
 * That is deliberate, and it is what lets the read below fail quietly: with no
 * booking loaded the screen still reads "Request sent." over a pending_confirm
 * timeline, which is exactly what just happened. There is no `isLoading` here
 * because there is nothing to withhold while the read is in flight.
 */
data class ConfirmedUiState(
    val artistName: String = "",
    val date: String = "",
    val status: BookingStatus = BookingStatus.PendingConfirm,
)

@HiltViewModel
class ConfirmedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingsRepository: BookingsRepository,
    private val artistsRepository: ArtistsRepository,
) : ViewModel() {
    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"])
    private val _state = MutableStateFlow(ConfirmedUiState())
    val state: StateFlow<ConfirmedUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Guarded the same way BookingDetailViewModel.refresh guards it, and
            // for a sharper reason. `fetchOne` wraps EVERY transport failure in
            // BookingRepositoryError.Underlying and rethrows — it returns null
            // only for a genuine zero-row read — and viewModelScope carries no
            // CoroutineExceptionHandler, so an unguarded throw here walks out to
            // the thread's uncaught handler and kills the process. Losing
            // connectivity a second after the request lands would crash the one
            // screen whose entire job is to tell the client their request went
            // through, over a booking the server already has.
            //
            // The read is decoration: it names the artist and the date. We only
            // ever arrive here from a create that came back with an id, so a
            // failure degrades to "The artist will respond soon" rather than
            // showing an error over a request that did, in fact, send.
            val booking = runCatching { bookingsRepository.fetchOne(bookingId) }.getOrNull()
            val artist = booking?.let { artistsRepository.find(it.artistId) }
            _state.update {
                it.copy(
                    artistName = artist?.name.orEmpty(),
                    date = booking?.date.orEmpty(),
                    status = booking?.status ?: BookingStatus.PendingConfirm,
                )
            }
        }
    }
}

@Composable
fun ConfirmedScreen(
    onViewBooking: (bookingId: String) -> Unit,
    onBackToDiscover: () -> Unit,
    bookingId: String,
    modifier: Modifier = Modifier,
    viewModel: ConfirmedViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var ringScale by remember { mutableStateOf(0.6f) }

    LaunchedEffect(Unit) { ringScale = 1f }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(AppTheme.dimens.size.ringXl)
                    .clip(CircleShape)
                    .background(colors.brand.copy(alpha = 0.07f)),
            )
            Box(
                Modifier
                    .size(AppTheme.dimens.size.ringLg)
                    .clip(CircleShape)
                    .background(colors.brand.copy(alpha = 0.15f)),
            )
            Box(
                Modifier
                    .size(AppTheme.dimens.size.avatarMd)
                    .scale(ringScale)
                    .clip(CircleShape)
                    .background(colors.brand),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = colors.brandInk)
            }
        }
        Spacer(Modifier.height(space.xl))
        if (ui.status == BookingStatus.PendingConfirm) {
            EditorialHeadline("Request ", "sent", ".", style = AppTheme.type.displayHero)
            Spacer(Modifier.height(space.md))
            Text(
                "${ui.artistName.ifBlank { "The artist" }} will respond soon — we'll notify you.",
                style = AppTheme.type.callout,
                color = colors.ink3,
            )
        } else {
            EditorialHeadline("Match ", "confirmed", ".", style = AppTheme.type.displayHero)
            Spacer(Modifier.height(space.md))
            Text(
                "${ui.artistName} · ${ui.date}",
                style = AppTheme.type.callout,
                color = colors.ink3,
            )
        }
        Spacer(Modifier.height(space.xxl))
        // Where the booking actually IS, not just what it's called. The reference
        // build puts this same four-step timeline on this screen, between the
        // summary and the actions, on a card surface — the Android port shipped
        // the screen without it, which is why BookingStatusTimeline had no caller
        // despite being a finished port that was still being bug-fixed.
        //
        // It earns its place here specifically: this is the screen a client lands
        // on right after sending a request, and the one question it has to answer
        // is "what happens next". A status word alone doesn't.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
                .background(colors.bgCard)
                .padding(space.lg),
        ) {
            BookingStatusTimeline(status = ui.status)
        }
        Spacer(Modifier.height(space.xxl))
        HRule()
        Spacer(Modifier.height(space.xl))
        PrimaryButton(
            text = "View booking",
            onClick = { onViewBooking(bookingId) },
            fullWidth = true,
        )
        Spacer(Modifier.height(space.sm))
        PrimaryButton(
            text = "Back to discover",
            onClick = onBackToDiscover,
            variant = ButtonVariant.Ghost,
            fullWidth = true,
        )
    }
}
