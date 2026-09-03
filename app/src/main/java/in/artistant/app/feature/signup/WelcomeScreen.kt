package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The signup entry (iOS `SignupWelcomeView`): abstract radial-gradient hero, wordmark, editorial
 * italic-accent headline, and a terms-gate checkbox that must be on before either CTA works.
 * "Get started" runs the signup order; "I already have an account" runs the login order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(
    termsAccepted: Boolean,
    onTermsToggle: (Boolean) -> Unit,
    onGetStarted: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val haptic = LocalHapticFeedback.current
    var legalDoc by remember { mutableStateOf<LegalDoc?>(null) }

    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        // Abstract two-tone radial hero, held to the top band; it sits behind the content
        // column, so it can never push it. A real height, not `heightIn(max =)`: an empty
        // Box measures to the minimum it is given, which in a fillMaxSize Box is zero, and
        // both washes then paint into nothing. Drawn in `drawBehind` so the centres and radii
        // are placed against the MEASURED size — as fixed pixel constants they landed
        // somewhere different on every density.
        //
        // The alphas were 0.35 / 0.18, tuned for lime and violet glowing out of near-black.
        // On `page` the same numbers are a lime fog over the whole top half of the first
        // screen anyone sees — the accent stops being a signal and becomes the background.
        // Dropped to a tint that warms the corner and nothing more; screen 118 draws this
        // page as plain off-white, so this is already the generous reading of it.
        Box(
            Modifier
                .fillMaxWidth()
                .height(AppTheme.dimens.size.heroTall)
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(colors.accent.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(size.width * 0.3f, size.height * 0.2f),
                            radius = size.width * 0.7f,
                        ),
                    )
                    drawRect(
                        Brush.radialGradient(
                            colors = listOf(colors.brand.copy(alpha = 0.06f), Color.Transparent),
                            center = Offset(size.width * 0.85f, size.height * 0.3f),
                            radius = size.width * 0.6f,
                        ),
                    )
                },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = space.xl)
                .padding(bottom = space.xxl),
        ) {
            // Wordmark
            Row(
                modifier = Modifier.padding(top = space.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(AppTheme.dimens.size.iconXl)
                        .clip(CircleShape)
                        .background(colors.brand),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "A",
                        color = colors.brandInk,
                        fontWeight = FontWeight.Black,
                        style = AppTheme.type.body,
                    )
                }
                Text(
                    "ARTISTANT",
                    color = colors.ink,
                    fontWeight = FontWeight.Black,
                    style = AppTheme.type.caption,
                )
            }

            Spacer(Modifier.weight(1f))

            // Editorial headline + subhead — "best" is the italic-lime accent (iOS parity).
            EditorialHeadline(
                lead = "Book India's\n",
                accent = "best",
                tail = " artists.",
                style = AppTheme.type.welcomeDisplay,
            )
            Spacer(Modifier.height(space.lg))
            Text(
                "Transparent pricing. Verified talent. Book with confidence.",
                style = AppTheme.type.callout,
                color = colors.ink2,
                modifier = Modifier.width(320.dp),
            )
            Spacer(Modifier.height(space.xl))

            // Terms gate. The checkbox row toggles; the Terms/Privacy words open the sheet.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTermsToggle(!termsAccepted)
                        }
                        .semantics { testTag = "welcome.terms" },
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    TermsCheckbox(checked = termsAccepted)
                    Text(
                        "I'm 18+ and agree to the platform terms.",
                        style = AppTheme.type.footnote,
                        color = colors.ink2,
                    )
                }
                // The two links are 13sp words, so the touch floor has to be added under them:
                // the size modifiers sit INSIDE the clickable, which grows the tappable node
                // while `wrapContentHeight` keeps the word itself centred at its own size.
                Row(
                    modifier = Modifier.padding(start = AppTheme.dimens.size.iconLg + space.md),
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Terms",
                        style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accentInk,
                        modifier = Modifier
                            .clickable(role = Role.Button) { legalDoc = LegalDoc.Terms }
                            .sizeIn(minHeight = AppTheme.dimens.size.controlMin)
                            .wrapContentHeight(),
                    )
                    Text("·", color = colors.ink3)
                    Text(
                        "Privacy",
                        style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.accentInk,
                        modifier = Modifier
                            .clickable(role = Role.Button) { legalDoc = LegalDoc.Privacy }
                            .sizeIn(minHeight = AppTheme.dimens.size.controlMin)
                            .wrapContentHeight(),
                    )
                }
            }
            Spacer(Modifier.height(space.md))

            // Both CTAs gate on terms — a disabled "Get started" reads as "not yet".
            PrimaryButton(
                text = "Get started",
                onClick = onGetStarted,
                fullWidth = true,
                enabled = termsAccepted,
                modifier = Modifier.semantics { testTag = "welcome.getStarted" },
            )
            Spacer(Modifier.height(space.sm))
            PrimaryButton(
                text = "I already have an account",
                onClick = onLogin,
                variant = ButtonVariant.Ghost,
                fullWidth = true,
                enabled = termsAccepted,
                modifier = Modifier.semantics { testTag = "welcome.login" },
            )
        }
    }

    legalDoc?.let { doc ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { legalDoc = null }, sheetState = sheetState) {
            LegalScreen(doc = doc, onClose = { legalDoc = null })
        }
    }
}
