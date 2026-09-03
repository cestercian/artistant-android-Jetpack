package `in`.artistant.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Help + feedback sheet — port of iOS Profile HelpCenter / FeedbackSheet.
 * FAQ is static; submit is owned by [ProfileViewModel] so dismissal can't cancel it.
 */
@Composable
fun HelpFeedbackSheet(
    sending: Boolean,
    status: String?,
    statusOk: Boolean,
    onSubmit: (body: String, isBug: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var body by remember { mutableStateOf("") }
    var isBug by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    LaunchedEffect(statusOk, status) {
        if (statusOk && status != null) {
            // iOS buzzes on the tap; we buzz on the answer, because we have one.
            // `status` is cleared to null before each submit, so a second send
            // with identical copy still changes the key and still buzzes.
            haptics.success()
            body = ""
        }
    }

    // Hosted in a hand-rolled scrim rather than a Material `ModalBottomSheet`,
    // so it does not inherit Material's rounded sheet top — it has to bring its
    // own. The inert `clickable` swallows taps so they don't reach the scrim
    // behind and dismiss the sheet.
    Column(
        modifier
            .dockSurface()
            .clickable(enabled = false) {}
            .padding(space.lg),
    ) {
        Text("Help", style = AppTheme.type.headline, color = colors.ink)
        Spacer(Modifier.height(space.md))
        FaqRow("How do bookings work?", "Send a request → the artist Accepts or Declines. Chat stays in Artistant.")
        FaqRow("Payments?", "v1 is matchmaker — negotiate in chat. Escrow lands later.")
        FaqRow("Safety", "Always communicate through Artistant. Report anything off-platform.")
        Spacer(Modifier.height(space.xl))
        Text("Send feedback", style = AppTheme.type.callout, color = colors.ink)
        Spacer(Modifier.height(space.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            Text(
                "Product",
                style = AppTheme.type.caption,
                color = if (!isBug) colors.brandInk else colors.ink2,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .background(if (!isBug) colors.brand else colors.bgSoft)
                    .clickable { isBug = false }
                    .padding(horizontal = space.md, vertical = space.sm),
            )
            Text(
                "Bug",
                style = AppTheme.type.caption,
                color = if (isBug) colors.brandInk else colors.ink2,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .background(if (isBug) colors.brand else colors.bgSoft)
                    .clickable { isBug = true }
                    .padding(horizontal = space.md, vertical = space.sm),
            )
        }
        Spacer(Modifier.height(space.md))
        BasicTextField(
            value = body,
            onValueChange = { body = it },
            textStyle = AppTheme.type.body.copy(color = colors.ink),
            // The default caret is black, which on `bgSoft` is no caret at all.
            cursorBrush = SolidColor(colors.brand),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                .background(colors.bgSoft)
                .padding(space.md),
            decorationBox = { inner ->
                if (body.isEmpty()) {
                    Text("What should we know?", style = AppTheme.type.body, color = colors.ink3)
                }
                inner()
            },
        )
        status?.let {
            Spacer(Modifier.height(space.sm))
            Text(
                it,
                style = AppTheme.type.footnote,
                color = if (statusOk) colors.ink2 else colors.hot,
            )
        }
        Spacer(Modifier.height(space.lg))
        PrimaryButton(
            text = if (sending) "Sending…" else "Send",
            onClick = {
                if (sending || body.isBlank()) return@PrimaryButton
                onSubmit(body.trim(), isBug)
            },
            fullWidth = true,
            enabled = !sending && body.isNotBlank(),
        )
        Spacer(Modifier.height(space.sm))
        PrimaryButton(text = "Close", onClick = onDismiss, variant = ButtonVariant.Ghost, fullWidth = true)
    }
}

@Composable
private fun FaqRow(q: String, a: String) {
    val space = AppTheme.dimens.space
    Column(Modifier.padding(vertical = space.sm)) {
        Text(q, style = AppTheme.type.callout, color = AppTheme.colors.ink)
        Text(a, style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
    }
}
