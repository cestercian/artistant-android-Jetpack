package `in`.artistant.app.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.data.repository.ArtistReportReasons
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.ListRow
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The "···" menu on screen 04 — save, share, report.
 *
 * A sheet rather than a dropdown because the design has no dropdown anywhere in
 * it, and because the third item is a report: a destructive-adjacent action
 * deserves a target you cannot hit by accident while reaching for the header.
 *
 * Report is hidden on the self view. Reporting yourself would insert a row
 * naming you as both reporter and reported, which is noise a human moderator
 * then has to read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileActionSheet(
    artistName: String,
    isSaved: Boolean,
    isSelf: Boolean,
    onToggleSaved: () -> Unit,
    onShare: () -> Unit,
    onReport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colors.surface,
    ) {
        SheetScaffold {
            Text(
                artistName,
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = dimens.space.sm),
            )
            if (!isSelf) {
                ListRow(
                    title = if (isSaved) "Saved" else "Save this artist",
                    leading = {
                        Icon(
                            imageVector = if (isSaved) {
                                ProfileActionIcons.Saved
                            } else {
                                ProfileActionIcons.Save
                            },
                            contentDescription = null,
                            tint = if (isSaved) colors.accentInk else colors.ink,
                            modifier = Modifier.size(dimens.size.iconLg),
                        )
                    },
                    onClick = {
                        onToggleSaved()
                        onDismiss()
                    },
                    trailing = {},
                )
            }
            ListRow(
                title = "Share profile",
                leading = {
                    Icon(
                        ProfileActionIcons.Share,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(dimens.size.iconLg),
                    )
                },
                onClick = {
                    onShare()
                    onDismiss()
                },
                trailing = {},
            )
            if (!isSelf) {
                ListRow(
                    title = "Report this artist",
                    leading = {
                        Icon(
                            ProfileActionIcons.Report,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(dimens.size.iconLg),
                        )
                    },
                    destructive = true,
                    onClick = onReport,
                    showHairline = false,
                    trailing = {},
                )
            }
        }
    }
}

/**
 * Screen 56 — report this artist.
 *
 * **Says queued, not received.** The insert is soft-failing by contract: a
 * report that cannot reach the server is appended to a local log on this device
 * instead of throwing into the profile. That is the right behaviour and it makes
 * the copy load-bearing, because "Report received" would be a claim this client
 * cannot make. The caption under the button states the rule up front, and the
 * toast afterwards says which of the two actually happened.
 *
 * The note is optional and explicitly not shared with the artist — a reporter
 * who thinks the subject will read it writes a different, more careful, less
 * useful note.
 *
 * Reasons come from [ArtistReportReasons] and go into `reports.reason`
 * verbatim, so a moderator reads the same string the reporter picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportArtistSheet(
    artistName: String,
    onSubmit: (reason: String, details: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var note by rememberSaveable { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colors.surface,
    ) {
        SheetScaffold {
            Row(
                Modifier.fillMaxWidth().padding(bottom = space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Report this artist",
                    style = AppTheme.type.sectionTitle,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconCircle(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = dimens.component.iconCircleSm,
                )
            }
            Text(
                "Why are you reporting $artistName?",
                style = AppTheme.type.body,
                color = colors.ink2,
            )
            Spacer(Modifier.height(space.md))
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ArtistReportReasons.forEachIndexed { index, option ->
                    ReasonRow(
                        label = option,
                        selected = option == reason,
                        showHairline = index != ArtistReportReasons.lastIndex,
                        onClick = { reason = option },
                    )
                }
                Spacer(Modifier.height(space.lg))
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Add a note — optional",
                    hint = "This won't be shared with them",
                    singleLine = false,
                    minHeight = dimens.component.skeletonTile,
                )
            }
            Spacer(Modifier.height(space.lg))
            PrimaryButton(
                text = "Submit report",
                onClick = { reason?.let { onSubmit(it, note.trim().ifBlank { null }) } },
                // A report with no reason is a row a moderator cannot triage, so
                // the button waits rather than filing "Something else" on the
                // reporter's behalf.
                enabled = reason != null,
                fullWidth = true,
            )
            Spacer(Modifier.height(space.md))
            Text(
                "Queued on this device if you're offline.",
                style = AppTheme.type.caption,
                color = colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * One radio row. The selected mark is an accent disc with a check in it, which
 * is what the design draws — a ring with a dot would be Material's radio, and
 * this app does not use Material's.
 */
@Composable
private fun ReasonRow(
    label: String,
    selected: Boolean,
    showHairline: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .then(if (showHairline) Modifier.hairlineBottom() else Modifier)
            .padding(vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = if (selected) "$label, selected" else label
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.radio)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(colors.accent)
                    } else {
                        Modifier.border(dimens.component.focusStroke, colors.lineStrong, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
            }
        }
        Text(
            label,
            style = AppTheme.type.body,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
    }
}
