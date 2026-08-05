package `in`.artistant.app.feature.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import `in`.artistant.app.data.repository.ReportReasons
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Thread details + report — port of iOS ThreadDetailsSheet (Airbnb trust).
 * Gig summary + participants + report reason picker. Local star/mute deferred.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailsSheet(
    title: String,
    bookingId: String?,
    bookingSummary: String?,
    counterpartLabel: String,
    viewerIsArtist: Boolean,
    onBookingClick: (String) -> Unit,
    onReport: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reporting by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bg,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.xl)
                .padding(bottom = space.xxl),
        ) {
            Text("Details", style = AppTheme.type.headline, color = colors.ink)
            Spacer(Modifier.height(space.lg))
            HRule()
            Spacer(Modifier.height(space.lg))

            Text("Gig", style = AppTheme.type.caption, color = colors.ink3)
            Spacer(Modifier.height(space.sm))
            if (bookingId != null && bookingSummary != null) {
                Text(
                    bookingSummary,
                    style = AppTheme.type.body,
                    color = colors.ink2,
                    modifier = Modifier.clickable { onBookingClick(bookingId) },
                )
            } else {
                Text("No booking yet — inquiry", style = AppTheme.type.body, color = colors.ink3)
            }

            Spacer(Modifier.height(space.lg))
            HRule()
            Spacer(Modifier.height(space.lg))

            Text("Participants", style = AppTheme.type.caption, color = colors.ink3)
            Spacer(Modifier.height(space.sm))
            // Each row carries its seat, so "who is who" no longer rests on name
            // recognition alone — the roles are always opposites (iOS
            // ThreadDetailsSheet.participants does the same).
            ParticipantRow(
                name = counterpartLabel,
                role = ThreadCounterpart.counterpartRole(viewerIsArtist),
            )
            ParticipantRow(name = "You", role = ThreadCounterpart.viewerRole(viewerIsArtist))

            Spacer(Modifier.height(space.lg))
            HRule()
            Spacer(Modifier.height(space.lg))

            when {
                submitted -> Text(
                    "Thanks — we'll review this conversation.",
                    style = AppTheme.type.body,
                    color = colors.brand,
                )
                reporting -> {
                    Text("Why are you reporting?", style = AppTheme.type.callout, color = colors.ink)
                    Spacer(Modifier.height(space.md))
                    ReportReasons.forEach { reason ->
                        Text(
                            reason,
                            style = AppTheme.type.body,
                            color = colors.ink2,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onReport(reason)
                                    submitted = true
                                }
                                .padding(vertical = space.sm),
                        )
                    }
                }
                else -> {
                    PrimaryButton(
                        text = "Report conversation",
                        onClick = { reporting = true },
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

/**
 * One participant: name over an uppercased role caption. The role is what makes
 * the two rows unambiguous when both names are unfamiliar — iOS renders the same
 * pairing (`participantRow`, subtitle uppercased at render time).
 */
@Composable
private fun ParticipantRow(name: String, role: String) {
    val colors = AppTheme.colors
    Column(Modifier.padding(vertical = AppTheme.dimens.space.xs)) {
        Text(name, style = AppTheme.type.callout, color = colors.ink, maxLines = 1)
        Text(role.uppercase(), style = AppTheme.type.monoSmall, color = colors.ink3, maxLines = 1)
    }
}
