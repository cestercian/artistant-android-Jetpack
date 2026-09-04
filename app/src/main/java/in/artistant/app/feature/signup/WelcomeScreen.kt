package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Screen 118 — the welcome, and the app's first daylight.
 *
 * The design's own title for it is "Welcome — **blocked**", and the note says why: "the
 * disabled CTA is paired with an inline reason instead of failing silently on tap." So the
 * blocked state is not an edge case bolted onto a happy path — it IS the screen the designer
 * drew, and the enabled state is the same screen with the reason line gone.
 *
 * There are two things that can block it and they are both real conditions, never a
 * placeholder: the 18+/terms box is unticked, or the app has no route to the server. Nothing
 * else disables the button, because nothing else can be stated truthfully — a
 * "signups are paused" reason would need an `app_settings` read the schema does not grant
 * clients (the table is server-only), so that reason is not offered.
 *
 * @param blockedReason a non-null override — currently "no connection" — that outranks the
 *   terms tick. Null means the only gate is the checkbox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    termsAccepted: Boolean,
    onTermsToggle: (Boolean) -> Unit,
    onGetStarted: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    blockedReason: String? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val haptic = LocalHapticFeedback.current
    var legalDoc by remember { mutableStateOf<LegalDoc?>(null) }

    val reason = blockedReason ?: if (termsAccepted) null else "Tick this to continue"
    val canContinue = reason == null

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.welcome" },
        // No header: the design starts this screen at the mark, 40 below the status bar.
        // Not scrollable: the consent block is pushed to the BOTTOM of the body by a flex
        // spacer, and a weight has no meaning inside an infinitely-tall scroll. The content
        // is a mark, two paragraphs and a card, which fits the viewport by construction.
        scrollable = false,
        footer = {
            PrimaryButton(
                text = "Get started",
                onClick = onGetStarted,
                fullWidth = true,
                enabled = canContinue,
                modifier = Modifier.semantics { testTag = "welcome.getStarted" },
            )
            Text(
                "I already have an account",
                style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accentInk,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .clickable(role = Role.Button, onClick = onLogin)
                    .padding(vertical = space.sm)
                    .semantics { testTag = "welcome.login" },
            )
        },
    ) {
        Spacer(Modifier.height(space.xxl))
        AppMark()
        Spacer(Modifier.height(space.xl + space.xs))
        Text(
            "Book the act,\nnot the agency.",
            style = AppTheme.type.displayHero,
            color = colors.ink,
        )
        Spacer(Modifier.height(space.md))
        Text(
            "Transparent pricing. Verified talent. Book with confidence.",
            style = AppTheme.type.body,
            color = colors.ink4,
        )

        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(space.xxl))

        // The consent block. The whole card toggles; the two document words inside it open the
        // viewer instead, which is why they are separate nodes rather than annotated spans —
        // an annotated string cannot carry its own tap target's accessibility role.
        val interaction = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radii.lg))
                .background(colors.surface3)
                .border(
                    dimens.component.focusStroke,
                    if (termsAccepted) colors.accent else colors.hairline,
                    RoundedCornerShape(dimens.radii.lg),
                )
                .clickable(interactionSource = interaction, indication = null, role = Role.Checkbox) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTermsToggle(!termsAccepted)
                }
                .padding(space.lg)
                .semantics {
                    testTag = "welcome.terms"
                    contentDescription = "I'm 18 or older and agree to the Terms and Privacy Policy"
                    toggleableState = if (termsAccepted) ToggleableState.On else ToggleableState.Off
                },
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.Top,
        ) {
            ConsentCheckbox(checked = termsAccepted)
            Column {
                Text(
                    buildAnnotatedString {
                        append("I'm 18 or older and agree to the ")
                        withStyle(SpanStyle(color = colors.accentInk, fontWeight = FontWeight.SemiBold)) {
                            append("Terms")
                        }
                        append(" and ")
                        withStyle(SpanStyle(color = colors.accentInk, fontWeight = FontWeight.SemiBold)) {
                            append("Privacy Policy")
                        }
                        append(".")
                    },
                    style = AppTheme.type.subtitle,
                    color = colors.ink2,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
                    InlineLink("Read the Terms", { legalDoc = LegalDoc.Terms }, style = AppTheme.type.caption)
                    InlineLink("Read the Privacy Policy", { legalDoc = LegalDoc.Privacy }, style = AppTheme.type.caption)
                }
            }
        }

        // The inline reason. Present exactly when the CTA is not tappable, so the pair always
        // agrees: a disabled button with nothing under it is the silent failure this screen
        // was drawn to rule out.
        if (reason != null) {
            Row(
                modifier = Modifier
                    .padding(top = space.md)
                    .semantics(mergeDescendants = true) { testTag = "welcome.blockedReason" },
                horizontalArrangement = Arrangement.spacedBy(space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = colors.warm,
                    modifier = Modifier.size(dimens.size.iconMd),
                )
                Text(
                    reason,
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.warm,
                )
            }
        }
        Spacer(Modifier.height(space.md))
    }

    legalDoc?.let { doc ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { legalDoc = null },
            sheetState = sheetState,
            containerColor = colors.surface,
        ) {
            LegalScreen(doc = doc, onClose = { legalDoc = null })
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 760)
@Composable
private fun WelcomeBlockedPreview() {
    ArtistantTheme {
        WelcomeScreen(termsAccepted = false, onTermsToggle = {}, onGetStarted = {}, onLogin = {})
    }
}
