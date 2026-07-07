package `in`.artistant.app.feature.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.designsystem.component.AddToCalendarButton
import `in`.artistant.app.designsystem.component.BookingStatusTimeline
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.KVRow
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.feature.booking.FunnelHeader
import `in`.artistant.app.feature.booking.InitialAvatar
import `in`.artistant.app.feature.booking.SectionLabel
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Shared booking detail (port of iOS `BookingDetailView`). Artist header, status
 * timeline, KV detail rows, fee, and the role-agnostic action row: Message (→ Chat
 * stub, M4), Add to calendar (confirmed), Cancel (dialog → store.cancel), Leave
 * review (completed → [ReviewSheet]).
 */
@Composable
fun BookingDetailScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    viewModel: BookingDetailViewModel = hiltViewModel(),
) {
    // Observe so a cancel re-renders.
    viewModel.bookings.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val booking = viewModel.booking()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    var confirmingCancel by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        FunnelHeader("Booking", onBack)

        // A failed cancel (or any store error) surfaces here rather than silently
        // snapping the optimistic flip back on the next refresh.
        error?.let { msg ->
            Row(
                Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = space.xl, vertical = space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Text(
                    msg,
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.hot,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Dismiss",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                    modifier = Modifier.clickable { viewModel.dismissError() }.padding(space.xs),
                )
            }
        }

        if (booking == null) {
            Column(
                Modifier.fillMaxSize().padding(space.xl),
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Text("Booking not found", style = AppTheme.type.displaySmall, color = colors.ink)
                Text(
                    "This booking may have been cancelled, or isn't synced yet. Pull back to Bookings to refresh.",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                )
            }
        } else {
        val artist = viewModel.artist(booking.artistId)
        // Subtitle = "handle · packageName" (iOS BookingDetailView), resolving the
        // package from artist.packages[booking.packageIndex]; falls back to just the
        // handle when the index can't be resolved, and "Loading…" until the artist
        // hydrates.
        val subtitle = artist?.let { a ->
            a.packages.getOrNull(booking.packageIndex)?.name?.let { "${a.handle} · $it" } ?: a.handle
        } ?: "Loading…"
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = space.xl),
        ) {
            // Artist header.
            Row(
                Modifier.fillMaxWidth().padding(vertical = space.lg),
                horizontalArrangement = Arrangement.spacedBy(space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialAvatar(artist?.name ?: "Artist", size = 48)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        artist?.name ?: "Artist",
                        style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                    )
                    Text(
                        subtitle,
                        style = AppTheme.type.caption,
                        color = colors.ink3,
                    )
                }
            }
            HRule()

            // Status.
            Section("Status") { BookingStatusTimeline(booking.status, Modifier.padding(vertical = space.md)) }

            // Details.
            Section("Details") {
                KVRow("Date", booking.dateLabel)
                HRule()
                KVRow("Time", booking.timeLabel)
                HRule()
                KVRow("Venue", booking.venue)
                HRule()
                KVRow("Guests", "${booking.guests}")
                HRule()
                KVRow("Booking ID", booking.id)
            }

            // Price — v1 shows only the artist fee.
            Section("Price") {
                KVRow("Artist fee", formatInr(booking.fee), bold = true, lime = true, big = true)
            }

            Spacer(Modifier.height(space.xl))
            ActionRow(
                booking = booking,
                onMessage = { onMessage(booking.artistId) },
                onCancel = { confirmingCancel = true },
                onReview = { showReview = true },
            )
            Spacer(Modifier.height(space.xxl))
        }
        }
    }

    if (confirmingCancel) {
        AlertDialog(
            onDismissRequest = { confirmingCancel = false },
            title = { Text("Cancel this booking?") },
            text = { Text("This will cancel your booking with the artist. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmingCancel = false; viewModel.cancel() }) {
                    Text("Cancel booking", color = colors.hot)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingCancel = false }) { Text("Keep booking") } },
            containerColor = colors.bgElev,
        )
    }

    if (showReview && booking != null) {
        ReviewSheet(artistName = viewModel.artist(booking.artistId)?.name, onDismiss = { showReview = false })
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(top = AppTheme.dimens.space.xl), verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md)) {
        SectionLabel(title)
        content()
    }
}

@Composable
private fun ActionRow(
    booking: Booking,
    onMessage: () -> Unit,
    onCancel: () -> Unit,
    onReview: () -> Unit,
) {
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        PrimaryButton(text = "Message artist", onClick = onMessage, fullWidth = true)

        if (booking.status == BookingStatus.Confirmed) {
            AddToCalendarButton(booking)
        }
        if (booking.status == BookingStatus.PendingConfirm || booking.status == BookingStatus.Confirmed) {
            PrimaryButton(
                text = "Cancel booking",
                onClick = onCancel,
                variant = `in`.artistant.app.designsystem.component.ButtonVariant.Ghost,
                fullWidth = true,
            )
        }
        if (booking.status == BookingStatus.Completed) {
            PrimaryButton(
                text = "Leave a review",
                onClick = onReview,
                variant = `in`.artistant.app.designsystem.component.ButtonVariant.Ghost,
                fullWidth = true,
            )
        }
    }
}
