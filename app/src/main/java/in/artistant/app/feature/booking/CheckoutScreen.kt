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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.CardView
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.KVRow
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import androidx.compose.runtime.LaunchedEffect

/**
 * Confirm-match screen (port of iOS `CheckoutView`). Shows a summary card built
 * from the draft + resolved artist, then a Confirm button that runs the payment
 * seam → booking write and, on success, navigates to Confirmed ([onConfirmed]).
 */
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onConfirmed: (String) -> Unit,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val artist = viewModel.artist()

    // One-shot confirmed → navigate forward.
    LaunchedEffect(Unit) {
        viewModel.confirmed.collect(onConfirmed)
    }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        FunnelHeader("Confirm match", onBack)

        state.error?.let { msg ->
            Row(
                Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = space.lg, vertical = space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Text(msg, style = AppTheme.type.footnote, color = colors.hot, modifier = Modifier.weight(1f))
                Text(
                    "Retry",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.brand,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !state.confirming) { viewModel.confirm() }
                        .padding(space.xs),
                )
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(space.lg),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            val d = draft
            if (d != null && artist != null) {
                val pkg = artist.packages.getOrNull(d.packageIndex)
                CardView {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
                        InitialAvatar(artist.name)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(artist.name, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
                            Text(pkg?.name ?: "Custom", style = AppTheme.type.caption, color = colors.ink3)
                        }
                        Pill("${artist.score}", tone = PillTone.Brand)
                    }
                    Spacer(Modifier.height(space.md))
                    HRule()
                    KVRow("Date", d.date)
                    KVRow("Time", d.time)
                    KVRow("Venue", d.venue.ifEmpty { "TBD" })
                    KVRow("Guests", "${d.guests}")
                    HRule()
                    KVRow("Artist fee", formatInr(pkg?.price ?: 0), bold = true, lime = true, big = true)
                }
            }
        }

        CtaBar {
            PrimaryButton(
                text = if (state.confirming) "Confirming match…" else "Confirm match",
                onClick = viewModel::confirm,
                fullWidth = true,
                enabled = !state.confirming && draft != null,
            )
        }
    }
}

/** Tiny initials chip standing in for the not-yet-built shared Avatar. */
@Composable
internal fun InitialAvatar(name: String, size: Int = 44) {
    val colors = AppTheme.colors
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(colors.bgSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = AppTheme.type.headline,
            color = colors.ink2,
        )
    }
}
