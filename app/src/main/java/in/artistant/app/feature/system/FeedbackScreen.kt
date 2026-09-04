package `in`.artistant.app.feature.system

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.BottomActionBar
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Screen 64 — Send feedback.
 *
 * The design's note is the whole screen: *honest about the reply*. "We read
 * everything but can't reply individually" sits UNDER the box, before the send
 * button, where it is read as a condition of sending rather than discovered
 * afterwards as an apology. A feedback channel that implies a reply and never
 * sends one stops being used, and then stops being worth reading.
 *
 * Two more things the design puts on the page and this keeps:
 *
 *  - the **counter** (`168 / 2000`), which is `app_feedback.body`'s own
 *    constraint (mig 0073) made visible before it can reject anything;
 *  - the **queue note**, which is a claim about behaviour and therefore has real
 *    machinery behind it — see [FeedbackOutbox].
 *
 * A pushed screen rather than a modal sheet, despite the design drawing a sheet:
 * it is a full-height text composer with a pinned action bar, and a sheet whose
 * content fills the display is a screen wearing a grabber. Same call the score
 * ledger made.
 */
@Composable
fun FeedbackScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedbackViewModel = hiltViewModel(),
    onToast: (String) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val haptics = rememberHaptics()

    LaunchedEffect(state.outcome) {
        val outcome = state.outcome ?: return@LaunchedEffect
        haptics.success()
        onToast(
            when (outcome) {
                // States the fact, not "Success!" — and the two facts are
                // genuinely different.
                FeedbackOutcome.Sent -> "Feedback sent"
                FeedbackOutcome.Queued -> "Queued on this device"
            },
        )
        viewModel.consumeOutcome()
        onClose()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page)
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .statusBarsPadding()
                .padding(horizontal = dimens.component.gutter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Cancel",
                    style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink4,
                    modifier = Modifier
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(dimens.space.xs),
                )
                Text(
                    text = "Send feedback",
                    style = AppTheme.type.sectionTitle,
                    color = colors.ink,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconCircle(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    onClick = onClose,
                    size = dimens.component.iconCircleSm,
                )
            }

            Row(
                Modifier.padding(top = dimens.space.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                FeedbackKind.entries.forEach { kind ->
                    KindButton(
                        label = kind.label,
                        selected = kind == state.kind,
                        onClick = { viewModel.setKind(kind) },
                    )
                }
            }

            FeedbackField(
                value = state.body,
                onValueChange = viewModel::setBody,
                enabled = !state.sending,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = dimens.space.lg),
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.space.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "We read everything but can't reply individually.",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.ink4,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${state.body.length} / $FEEDBACK_MAX_CHARS",
                    style = AppTheme.type.monoPill,
                    color = if (state.remaining == 0) colors.danger else colors.ink4,
                )
            }

            AccentNote(
                text = "No connection? It queues on this device and sends on your next " +
                    "live session.",
                modifier = Modifier.padding(top = dimens.space.lg),
            )
        }

        BottomActionBar {
            PrimaryButton(
                text = if (state.sending) "Sending…" else "Send feedback",
                onClick = viewModel::send,
                fullWidth = true,
                enabled = state.canSend,
            )
        }
    }
}

/**
 * One of the two kind buttons.
 *
 * Not [in.artistant.app.designsystem.component.SegmentedControl]: the design
 * draws two full-height 46dp buttons with the selected one filled in the
 * screen's accent, which is a picker, not the inset view-switch that component
 * describes. Screen 64 spends its accent here because there is nothing else on
 * the page competing for it.
 */
@Composable
private fun RowScope.KindButton(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .weight(1f)
            .heightIn(min = dimens.component.control)
            .clip(RoundedCornerShape(dimens.radii.control))
            .background(if (selected) colors.accent else colors.surface2)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = AppTheme.type.rowTitle.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) colors.onAccent else colors.ink2,
        )
    }
}

/**
 * The note itself.
 *
 * Hand-rolled rather than [in.artistant.app.designsystem.component.AppTextField]
 * because this one has to GROW to fill the page — the design gives it the whole
 * middle of the screen — and that component is built around a fixed control
 * height. The focus ring is the same 1.5dp `ink` stroke the design draws, which
 * is also what the shared field uses when focused.
 */
@Composable
private fun FeedbackField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(dimens.radii.buttonLg)

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface3)
            .border(
                width = if (focused) dimens.component.focusStroke else dimens.size.hairline,
                color = if (focused) colors.ink else colors.hairline,
                shape = shape,
            )
            .padding(dimens.space.lg),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            interactionSource = interaction,
            textStyle = LocalTextStyle.current.merge(
                AppTheme.type.rowTitle.copy(
                    color = colors.ink,
                    fontWeight = FontWeight.Normal,
                    lineHeight = AppTheme.type.body.lineHeight,
                ),
            ),
            // The default caret is black on a near-white field, which is a caret
            // you have to hunt for.
            cursorBrush = SolidColor(colors.accentInk),
            modifier = Modifier.fillMaxSize(),
        )
        if (value.isEmpty()) {
            Text(
                text = "What should we know?",
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Normal),
                color = colors.hint,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun FeedbackFieldPreview() {
    ArtistantTheme {
        Column(Modifier.padding(AppTheme.dimens.component.gutter)) {
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm)) {
                KindButton("General", selected = true, onClick = {})
                KindButton("Bug", selected = false, onClick = {})
            }
            Box(Modifier.size(AppTheme.dimens.size.heroShort)) {
                FeedbackField(
                    value = "The availability strip on an artist profile is the most useful " +
                        "thing in the app.",
                    onValueChange = {},
                    enabled = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
