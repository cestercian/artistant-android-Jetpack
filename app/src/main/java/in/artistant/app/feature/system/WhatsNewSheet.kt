package `in`.artistant.app.feature.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Screen 137 — the once-per-version release notes, presented from the root.
 *
 * The whole trigger lives in [WhatsNewViewModel]; this only draws whatever it is
 * handed and tells it when the sheet closed. Dismissal and presentation are the
 * same event here — the design gives the sheet a close cross AND a "Got it" CTA,
 * and neither is more of an acknowledgement than the other, so both mark the
 * version seen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewHost(viewModel: WhatsNewViewModel = hiltViewModel()) {
    val note by viewModel.visibleNote.collectAsStateWithLifecycle()
    val current = note ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = viewModel::acknowledge,
        sheetState = sheetState,
        // The sheet brings its own container (SheetScaffold): Material's would
        // draw an M3 surface and an M3 drag handle over it.
        dragHandle = null,
        containerColor = AppTheme.colors.surface,
    ) {
        WhatsNewSheet(note = current, onDismiss = viewModel::acknowledge)
    }
}

/** The sheet's inside — hoisted so a preview can render it without Hilt. */
@Composable
fun WhatsNewSheet(
    note: ReleaseNote,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    SheetScaffold(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The reserve keeps the title centred against the SHEET, not against
            // the space left over beside the close control.
            Spacer(Modifier.size(dimens.component.iconCircleSm))
            Text(
                text = "What's new",
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconCircle(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onDismiss,
                size = dimens.component.iconCircleSm,
            )
        }

        Column(
            Modifier
                .heightIn(max = dimens.size.heroTall)
                .verticalScroll(rememberScrollState())
                .padding(top = dimens.space.lg),
        ) {
            EyebrowLabel(
                text = "Version ${note.version} · ${note.released}",
                color = colors.ink3,
            )

            Column(
                Modifier.padding(top = dimens.space.lg),
                verticalArrangement = Arrangement.spacedBy(dimens.space.md),
            ) {
                note.highlights.forEach { HighlightRow(it) }
            }

            // Omitted entirely when a release shipped no fixes — a first release
            // has none, and an "ALSO FIXED" heading over nothing is a bug report.
            if (note.fixes.isNotEmpty()) {
                HRule(Modifier.padding(vertical = dimens.space.lg))
                EyebrowLabel(text = "Also fixed", color = colors.ink3)
                Column(
                    Modifier.padding(top = dimens.space.sm),
                    verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
                ) {
                    note.fixes.forEach { FixRow(it) }
                }
            }
        }

        PrimaryButton(
            text = "Got it",
            onClick = onDismiss,
            fullWidth = true,
            modifier = Modifier.padding(top = dimens.space.lg),
        )
    }
}

@Composable
private fun HighlightRow(highlight: ReleaseHighlight) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.md)) {
        Box(
            Modifier
                .size(dimens.component.iconCircleSm)
                // A rounded SQUARE, not the app's usual icon circle: the design
                // draws these as tiles because they are labels for features, not
                // controls, and a row of accent discs reads as three buttons.
                .clip(RoundedCornerShape(dimens.radii.md))
                .background(colors.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = highlight.icon.vector(),
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
        Column {
            Text(
                text = highlight.title,
                style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
            Text(
                text = highlight.body,
                style = AppTheme.type.subtitle.copy(lineHeight = AppTheme.type.body.lineHeight),
                color = colors.ink2,
                modifier = Modifier.padding(top = dimens.space.xs),
            )
        }
    }
}

@Composable
private fun FixRow(text: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
        Box(
            Modifier
                .padding(top = dimens.space.sm)
                .size(dimens.size.gridDot)
                .clip(CircleShape)
                .background(colors.ink3),
        )
        Text(text = text, style = AppTheme.type.chip, color = colors.ink2)
    }
}

@Composable
private fun ReleaseIcon.vector(): ImageVector = when (this) {
    ReleaseIcon.Booking -> Icons.Filled.EventAvailable
    ReleaseIcon.Score -> Icons.Filled.Insights
    ReleaseIcon.Chat -> Icons.AutoMirrored.Filled.Chat
    ReleaseIcon.Shield -> Icons.Outlined.Shield
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun WhatsNewPreview() {
    ArtistantTheme {
        Column {
            Spacer(Modifier.height(AppTheme.dimens.space.xxl))
            WhatsNewSheet(
                note = ReleaseNotes.forVersion("0.1.0")!!,
                onDismiss = {},
            )
        }
    }
}
