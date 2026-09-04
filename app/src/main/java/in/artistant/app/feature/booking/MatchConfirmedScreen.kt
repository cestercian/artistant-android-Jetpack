package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MatchConfirmedUiState(
    val booking: Booking? = null,
    val artistName: String = "",
    val isLoading: Boolean = true,
    val loadError: String? = null,
)

@HiltViewModel
class MatchConfirmedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingsRepository: BookingsRepository,
    private val artistsRepository: ArtistsRepository,
) : ViewModel() {
    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"])

    private val _state = MutableStateFlow(MatchConfirmedUiState())
    val state: StateFlow<MatchConfirmedUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            // Unlike the funnel's own confirmation, this screen has no defensible
            // default: it arrives from a chat where terms were negotiated, and
            // the whole page is those terms. With no row there is nothing true to
            // say, so a failed read is a stated failure with a retry.
            val read = runCatching { bookingsRepository.fetchOne(bookingId) }
            val booking = read.getOrNull()
            val artist = booking?.let { artistsRepository.find(it.artistId) }
            _state.update {
                it.copy(
                    booking = booking,
                    artistName = artist?.name.orEmpty(),
                    isLoading = false,
                    loadError = when {
                        booking != null -> null
                        read.isFailure -> "Couldn't load the match."
                        else -> "This booking isn't available any more."
                    },
                )
            }
        }
    }
}

/**
 * Screen 94 — "It's a match. You're both in."
 *
 * The design's note: *a match reached by negotiation needs its own landing — it
 * never passed through checkout.* Screen 07 is the funnel's page and says
 * "request sent" over terms the artist has yet to answer; this one is reached
 * from a chat where an in-thread quote was **accepted**, so both sides have
 * already agreed and the page says so.
 *
 * The terms-are-frozen note is not decoration either: migration 0096 freezes a
 * confirmed booking's date, venue, guest count and package on the server, so the
 * sentence describes a constraint that is actually enforced rather than a
 * promise this screen is making.
 *
 * The messaging section navigates here — `match_confirmed/{bookingId}` — from an
 * accepted quote in the thread.
 */
@Composable
fun MatchConfirmedScreen(
    bookingId: String,
    onViewBooking: (bookingId: String) -> Unit,
    onBackToDiscover: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MatchConfirmedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val booking = state.booking
    val who = state.artistName.ifBlank { "The artist" }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        if (booking == null) {
            if (state.isLoading) {
                Text(
                    "Reading the match…",
                    style = AppTheme.type.body,
                    color = colors.ink3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimens.component.gutter),
                )
            } else {
                EmptyState(
                    title = "Nothing to show",
                    body = state.loadError,
                    actionLabel = "Try again",
                    onAction = viewModel::refresh,
                )
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter)
                .padding(top = space.xxl, bottom = space.xl),
        ) {
            OutcomeMark()
            Text(
                "It's a match.\nYou're both in.",
                style = AppTheme.type.displayHero,
                color = colors.ink,
                modifier = Modifier.padding(top = space.xl),
            )
            Text(
                "$who confirmed the terms you agreed in chat. The date is held for you both.",
                style = AppTheme.type.body,
                color = colors.ink4,
                modifier = Modifier.padding(top = space.md),
            )

            FunnelCard(Modifier.padding(top = space.xl)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Monogram(who)
                    Column(Modifier.weight(1f)) {
                        Text(
                            who,
                            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val whenLine = listOf(booking.date, booking.time)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString(" · ")
                        if (whenLine.isNotEmpty()) {
                            Text(
                                whenLine,
                                style = AppTheme.type.caption,
                                color = colors.ink4,
                                modifier = Modifier.padding(top = space.xs / 2),
                            )
                        }
                    }
                    // Solid lime, and only ever the confirmed word: this screen is
                    // only reached from an accepted quote, so unlike the invoice
                    // there is no status ladder to map. A booking that somehow
                    // arrives here in another state says what it actually is.
                    AccentBadge(
                        if (booking.status == BookingStatus.Confirmed) {
                            "Confirmed"
                        } else {
                            booking.status.label
                        },
                    )
                }
                HRule(Modifier.padding(vertical = space.md))
                Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    booking.packageName?.trim()?.takeIf { it.isNotEmpty() }?.let {
                        TermRow(label = "Package", value = it)
                    }
                    TermRow(
                        label = "Artist fee",
                        value = formatInr(booking.fee),
                        emphasis = true,
                    )
                }
            }

            AccentNote(
                "Terms are frozen now — date, venue, guests and package can't be edited by " +
                    "either side.",
                modifier = Modifier.padding(top = space.lg),
            )
        }

        CtaBar {
            PrimaryButton(
                text = "View the booking",
                onClick = { onViewBooking(bookingId) },
                fullWidth = true,
            )
            Text(
                "Back to discover",
                style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accentInk,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBackToDiscover)
                    .padding(top = space.md),
            )
        }
    }
}

/**
 * A flat initials disc — `hairline` fill, `ink2` letters.
 *
 * Not the shared `Avatar`, which stamps a saturated DJB2 hue gradient. That
 * belongs to lists, where a wall of discs needs telling apart at a glance; here
 * there is exactly one person on the page and the design draws them quietly, so
 * a bright hash circle would be the loudest thing on a screen whose one accent
 * is already spent on the tick and the badge.
 */
@Composable
private fun Monogram(name: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val initials = remember(name) {
        name.trim().split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }
    Box(
        modifier
            .size(dimens.size.avatarMd)
            .clip(CircleShape)
            .background(colors.hairline),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials,
            style = AppTheme.type.sectionTitle,
            color = colors.ink2,
        )
    }
}
