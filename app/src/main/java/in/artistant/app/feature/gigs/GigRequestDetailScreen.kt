package `in`.artistant.app.feature.gigs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Gig quote detail — Accept / Decline / Counter via [RequestsRepository].
 * Port of iOS `GigRequestDetailView` — Accept / Decline / Counter + clash card.
 */
@Composable
fun GigRequestDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GigRequestDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val request = state.request
    var confirmingDecline by remember { mutableStateOf(false) }

    when {
        state.isLoading && request == null -> {
            Box(modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
        }
        request == null -> {
            Column(modifier.fillMaxSize().background(colors.bg)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                }
                EmptyState(
                    title = "Request not found",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }
        }
        else -> {
            RevealOnAppear {
                Column(modifier.fillMaxSize().background(colors.bg)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = space.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                        }
                        Text("Gig request", style = AppTheme.type.headline, color = colors.ink)
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(space.lg),
                        verticalArrangement = Arrangement.spacedBy(space.lg),
                    ) {
                        // The headline is the requester when we can name them.
                        // We usually can't: `users` is self-only under RLS, so
                        // the embed comes back null on the artist's side, and
                        // `gig_requests` carries no denormalized `client_name`
                        // the way `bookings` does (0080). The gig's date takes
                        // the slot instead of a placeholder that would read the
                        // same on every request the artist opens.
                        val requester = request.requesterName
                        Text(
                            requester ?: request.raw.date.ifBlank { "Gig request" },
                            style = AppTheme.type.displaySmall,
                            color = colors.ink,
                        )
                        if (requester != null) {
                            Text(request.raw.date, style = AppTheme.type.footnote, color = colors.ink3)
                        }
                        Pill(request.raw.packageLabel, tone = PillTone.Brand)
                        if (state.clashes.isNotEmpty()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
                                    .background(colors.bgSoft)
                                    .padding(space.md),
                                verticalArrangement = Arrangement.spacedBy(space.xs),
                            ) {
                                Text("Calendar clash", style = AppTheme.type.caption, color = colors.warm)
                                state.clashes.forEach { clash ->
                                    Text(clash.title, style = AppTheme.type.callout, color = colors.ink)
                                }
                            }
                        }
                        state.actionError?.let {
                            Text(it, style = AppTheme.type.footnote, color = colors.hot)
                        }
                        OfferBlock(request)
                        if (request.raw.message.isNotBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                                Text("MESSAGE", style = AppTheme.type.caption, color = colors.ink3)
                                Text(request.raw.message, style = AppTheme.type.body, color = colors.ink2)
                            }
                        }
                    }
                    if (viewModel.showActions()) {
                        Column(
                            Modifier
                                .dockSurface()
                                .padding(space.lg),
                            verticalArrangement = Arrangement.spacedBy(space.sm),
                        ) {
                            PrimaryButton(
                                text = if (state.isActing) "Accepting…" else "Accept",
                                onClick = viewModel::accept,
                                fullWidth = true,
                                enabled = !state.isActing,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                                PrimaryButton(
                                    text = if (state.isActing) "Declining…" else "Decline",
                                    onClick = { confirmingDecline = true },
                                    variant = ButtonVariant.Ghost,
                                    fullWidth = true,
                                    enabled = !state.isActing,
                                    modifier = Modifier.weight(1f),
                                )
                                PrimaryButton(
                                    text = "Counter",
                                    onClick = viewModel::showCounterSheet,
                                    variant = ButtonVariant.Subtle,
                                    fullWidth = true,
                                    enabled = !state.isActing,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmingDecline) {
        AlertDialog(
            shape = RoundedCornerShape(AppTheme.dimens.radii.xxl),
            onDismissRequest = { confirmingDecline = false },
            title = { Text("Decline this request?") },
            text = { Text("The client is notified and this request closes.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDecline = false
                    viewModel.decline()
                }) {
                    Text("Decline request", color = colors.hot)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDecline = false }) {
                    Text("Keep open")
                }
            },
        )
    }

    if (state.showCounterSheet) {
        CounterDialog(
            theirOffer = request?.raw?.amount ?: 0,
            amount = state.counterAmount,
            isActing = state.isActing,
            onAmountChange = viewModel::setCounterAmount,
            onDismiss = viewModel::dismissCounterSheet,
            onSend = viewModel::sendCounter,
        )
    }
}

@Composable
private fun OfferBlock(request: `in`.artistant.app.data.model.StoredRequest) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Text("OFFER", style = AppTheme.type.caption, color = colors.ink3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Their offer", style = AppTheme.type.footnote, color = colors.ink2)
            Text(formatInr(request.raw.amount), style = AppTheme.type.monoMedium, color = colors.ink)
        }
        request.counterAmount?.let { counter ->
            HRule()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your counter", style = AppTheme.type.footnote, color = colors.ink2)
                Text(formatInr(counter), style = AppTheme.type.monoMedium, color = colors.brand)
            }
        }
        if (request.status != GigRequestStatus.Open) {
            HRule()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Status", style = AppTheme.type.footnote, color = colors.ink2)
                // The label, not a capitalised db value: it is what the artist
                // Home rail prints for this same row (and what iOS's statusPill
                // uses), so a countered request stopped reading "Countered" here
                // and "Counter offer" there — and the decode fallback now has a
                // word of its own ("Unavailable") that the column value lacks.
                Pill(request.status.label, tone = PillTone.Neutral)
            }
        }
    }
}

@Composable
private fun CounterDialog(
    theirOffer: Int,
    amount: String,
    isActing: Boolean,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val radii = AppTheme.dimens.radii
    val parsed = amount.toIntOrNull() ?: 0

    AlertDialog(
        shape = RoundedCornerShape(AppTheme.dimens.radii.xxl),
        onDismissRequest = onDismiss,
        title = { Text("Counter offer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                Text(
                    "Their offer: ${formatInr(theirOffer)}",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radii.md))
                        .border(AppTheme.dimens.size.hairline, colors.line, RoundedCornerShape(radii.md))
                        .padding(space.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("₹", style = AppTheme.type.monoLarge, color = colors.ink3)
                    BasicTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        textStyle = AppTheme.type.monoLarge.copy(color = colors.ink),
                        modifier = Modifier.padding(start = space.xs),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !isActing && parsed > 0) {
                Text(if (isActing) "Sending…" else "Send counter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
