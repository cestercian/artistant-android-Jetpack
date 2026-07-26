package `in`.artistant.app.feature.booking

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Booking detail — role-aware names + Accept/Decline (artist) or Cancel (client).
 * Port of iOS `BookingDetailView` (simplified M3 slice).
 */
@Composable
fun BookingDetailScreen(
    isArtistViewer: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookingDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val booking = state.booking

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
                    state.actionError?.let {
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
                    booking.venueNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                        HRule()
                        DetailRow("Notes", notes)
                    }
                }
                if (viewModel.showAcceptDecline(isArtistViewer) || viewModel.showClientCancel(isArtistViewer)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(colors.bgElev)
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
                    }
                }
            }
        }
    }
}

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
