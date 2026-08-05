package `in`.artistant.app.feature.booking

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.messages.ChatOpenViewModel
import java.time.Instant

/**
 * Booking detail — role-aware names + Accept/Decline (artist) or Cancel (client),
 * plus Message / Getting there / Add to calendar / Leave a review (iOS BookingDetailView).
 */
@Composable
fun BookingDetailScreen(
    isArtistViewer: Boolean,
    onBack: () -> Unit,
    onOpenChat: (threadId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BookingDetailViewModel = hiltViewModel(),
    chatOpen: ChatOpenViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openingChat by chatOpen.opening.collectAsStateWithLifecycle()
    val chatError by chatOpen.error.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val context = LocalContext.current
    val booking = state.booking
    var showReview by remember { mutableStateOf(false) }

    when {
        state.isLoading && booking == null -> {
            Box(modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
        }
        booking == null -> {
            Column(modifier.fillMaxSize().background(colors.bg)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                }
                EmptyState(
                    title = "Booking not found",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }
        }
        else -> {
            Column(modifier.fillMaxSize().background(colors.bg)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                    }
                    Text("Booking", style = AppTheme.type.headline, color = colors.ink)
                }
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(space.lg),
                ) {
                    Text(
                        viewModel.counterpartyName(isArtistViewer),
                        style = AppTheme.type.displaySmall,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(space.sm))
                    Pill(booking.status.label, tone = PillTone.Neutral)
                    (state.actionError ?: chatError)?.let {
                        Spacer(Modifier.height(space.sm))
                        Text(it, style = AppTheme.type.footnote, color = colors.hot)
                    }
                    Spacer(Modifier.height(space.xl))
                    DetailRow("Date", booking.date)
                    HRule()
                    DetailRow("Time", booking.time)
                    HRule()
                    DetailRow("Venue", booking.venue)
                    HRule()
                    DetailRow("Guests", "${booking.guests}")
                    HRule()
                    DetailRow("Fee", formatInr(booking.fee))

                    // BOOK-07 — Getting there (venue_notes). Separate from Venue so load-in
                    // instructions stay scannable; hidden when blank.
                    booking.venueNotes?.trim()?.takeIf { it.isNotEmpty() }?.let { notes ->
                        Spacer(Modifier.height(space.xl))
                        Text("Getting there", style = AppTheme.type.caption, color = colors.ink3)
                        Spacer(Modifier.height(space.sm))
                        Text(notes, style = AppTheme.type.body, color = colors.ink)
                    }

                    if (showReview) {
                        Spacer(Modifier.height(space.xl))
                        ReviewSheet(
                            bookingId = booking.id,
                            reviewsRepository = viewModel.reviewsRepository,
                            onDismiss = { showReview = false },
                            onSubmitted = { showReview = false },
                        )
                    }
                }

                // Dock: Accept/Decline replaces Message while artist+pending (iOS parity —
                // no thread yet until accept). Otherwise Message is primary.
                Column(
                    Modifier
                        .dockSurface()
                        .padding(space.lg),
                    verticalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    if (viewModel.showAcceptDecline(isArtistViewer)) {
                        PrimaryButton(
                            text = if (state.isActing) "Accepting…" else "Accept request",
                            onClick = viewModel::acceptRequest,
                            fullWidth = true,
                            enabled = !state.isActing,
                        )
                        PrimaryButton(
                            text = if (state.isActing) "Declining…" else "Decline",
                            onClick = viewModel::declineRequest,
                            variant = ButtonVariant.Ghost,
                            fullWidth = true,
                            enabled = !state.isActing,
                        )
                    } else {
                        PrimaryButton(
                            text = when {
                                openingChat -> "Opening…"
                                isArtistViewer -> "Message client"
                                else -> "Message artist"
                            },
                            onClick = {
                                chatOpen.open(booking.artistId, booking.id, onOpenChat)
                            },
                            fullWidth = true,
                            enabled = !openingChat,
                        )
                    }

                    if (viewModel.showClientCancel(isArtistViewer)) {
                        PrimaryButton(
                            text = if (state.isActing) "Cancelling…" else "Cancel request",
                            onClick = viewModel::cancelBooking,
                            variant = ButtonVariant.Ghost,
                            fullWidth = true,
                            enabled = !state.isActing,
                        )
                    }

                    // Confirmed → system calendar compose (zero-permission ACTION_INSERT).
                    if (booking.status == BookingStatus.Confirmed) {
                        PrimaryButton(
                            text = "Add to calendar",
                            onClick = {
                                launchAddToCalendar(context, booking)?.let { err ->
                                    viewModel.reportActionError(err)
                                }
                            },
                            variant = ButtonVariant.Ghost,
                            fullWidth = true,
                        )
                    }

                    // Completed → leave a review (client only; artists don't review themselves).
                    if (!isArtistViewer && booking.status == BookingStatus.Completed && !showReview) {
                        PrimaryButton(
                            text = "Leave a review",
                            onClick = { showReview = true },
                            variant = ButtonVariant.Ghost,
                            fullWidth = true,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Zero-permission calendar handoff — mirrors iOS BOOK-06. Prefills title/location/
 * window; the system Calendar app owns the compose UI (we never read the store).
 */
private fun launchAddToCalendar(context: android.content.Context, booking: Booking): String? {
    val startMs = parseIsoEpochMs(booking.startDatetimeIso)
        ?: return "Couldn't add to calendar — missing show time."
    val endMs = parseIsoEpochMs(booking.endDatetimeIso) ?: (startMs + 2L * 60 * 60 * 1000)
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "Artistant · ${booking.venue}")
        .putExtra(CalendarContract.Events.EVENT_LOCATION, booking.venue)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
    booking.venueNotes?.takeIf { it.isNotBlank() }?.let {
        intent.putExtra(CalendarContract.Events.DESCRIPTION, it)
    }
    return runCatching { context.startActivity(intent); null }
        .getOrElse { "Couldn't open a calendar app on this device." }
}

private fun parseIsoEpochMs(iso: String?): Long? =
    iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

@Composable
private fun DetailRow(label: String, value: String) {
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.ink3)
        Text(value, style = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}
