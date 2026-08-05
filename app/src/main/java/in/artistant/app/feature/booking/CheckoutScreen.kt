package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/** Checkout — summary + "Send request" (mock payment → create → pending_confirm). */
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onConfirmed: (bookingId: String) -> Unit,
    onPaywall: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    LaunchedEffect(state.confirmedBookingId) {
        state.confirmedBookingId?.let { id ->
            onConfirmed(id)
            viewModel.clearNavigation()
        }
    }
    LaunchedEffect(state.needsPaywall) {
        if (state.needsPaywall) {
            onPaywall()
            viewModel.consumePaywall()
        }
    }

    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
            }
            Text("Confirm request", style = AppTheme.type.headline, color = colors.ink)
        }
        state.lastCreateErrorMessage?.let { err ->
            Text(
                err,
                style = AppTheme.type.footnote,
                color = colors.hot,
                modifier = Modifier.padding(horizontal = space.lg, vertical = space.sm),
            )
        }
        val draft = state.draft
        if (draft == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No draft found.", style = AppTheme.type.body, color = colors.ink3)
            }
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(space.lg),
            ) {
                Text(state.artistName.ifBlank { "Artist" }, style = AppTheme.type.displaySmall, color = colors.ink)
                Spacer(Modifier.height(space.lg))
                SummaryRow("Package", draft.packageName)
                HRule()
                SummaryRow("Date", draft.date)
                HRule()
                SummaryRow("Time", draft.time)
                HRule()
                SummaryRow("Venue", draft.venue.ifBlank { "Not set" })
                HRule()
                SummaryRow("Guests", "${draft.guests}")
                if (draft.venueNotes.isNotBlank()) {
                    HRule()
                    SummaryRow("Notes", draft.venueNotes)
                }
                Spacer(Modifier.height(space.xl))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Artist fee", style = AppTheme.type.headline, color = colors.ink)
                    Text(formatInr(draft.feeInr), style = AppTheme.type.monoMedium, color = colors.ink)
                }
            }
            Column(
                Modifier
                    .dockSurface()
                    .padding(space.lg),
            ) {
                if (state.isSubmitting) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.brand)
                    }
                } else {
                    PrimaryButton(
                        text = "Send request",
                        onClick = viewModel::sendRequest,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = false) {}
            .padding(vertical = space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.ink3)
        Text(value, style = AppTheme.type.body, color = AppTheme.colors.ink)
    }
}
