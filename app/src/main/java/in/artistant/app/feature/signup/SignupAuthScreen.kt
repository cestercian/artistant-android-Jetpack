package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.ui.auth.AuthViewModel
import `in`.artistant.app.ui.auth.EmailAuthSheet
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The auth entry, polished to the iOS `SignupAuthView` "Marquee" design: an animated festival-
 * lineup background behind an editorial headline, a session-lost notice pill, and the three auth
 * buttons (Apple solid → Google glass → Email glass) in a tray. Reuses the M1a [AuthViewModel]
 * (SessionManager wiring intact) + [EmailAuthSheet] — this only restyles the presentation.
 *
 * @param mode drives the headline/subhead copy (welcome vs sign-in) + the progress bar.
 * @param authNotice the "sign in again" banner shown after a session-lost bounce, or null.
 * @param reduceMotion true to freeze the lineup (a11y) — parity with iOS `motionDisabled`.
 */
@Composable
fun SignupAuthScreen(
    mode: SignupMode,
    authNotice: String?,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEmailSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(colors.bg)) {
        // Immersive lineup + a legibility scrim (dark top/bottom, readable middle).
        LineupBackground(animated = !reduceMotion)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.00f to colors.bg.copy(alpha = 0.98f),
                    0.28f to colors.bg.copy(alpha = 0.74f),
                    0.50f to colors.bg.copy(alpha = 0.62f),
                    0.76f to colors.bg.copy(alpha = 0.93f),
                    1.00f to colors.bg,
                ),
            ),
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = space.xl).padding(bottom = space.xxl),
        ) {
            if (mode == SignupMode.Signup) {
                SignupProgressDots(bar = ProgressBar(1, 5), modifier = Modifier.padding(top = space.sm))
            }

            Spacer(Modifier.height(space.xl))
            // Editorial headline — signup leans into the marquee, login is warm.
            if (mode == SignupMode.Login) {
                EditorialHeadline("Welcome\n", "back", ".", style = AppTheme.type.displayHero)
            } else {
                EditorialHeadline("The lineup\nis ", "live", ".", style = AppTheme.type.displayHero)
            }
            Spacer(Modifier.height(space.md))
            Text(
                if (mode == SignupMode.Login) "Pick the account you signed up with."
                else "Pick how you want to sign in. We'll never post anything on your behalf.",
                style = AppTheme.type.callout,
                color = colors.ink2,
            )

            // Session-lost explainer pill.
            if (authNotice != null) {
                Spacer(Modifier.height(space.lg))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brandSoft)
                        .padding(horizontal = space.md, vertical = space.sm)
                        .semantics { testTag = "auth.notice" },
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null, tint = colors.brand, modifier = Modifier.size(AppTheme.dimens.size.iconMd))
                    Text(authNotice, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.brand)
                }
            }

            Spacer(Modifier.weight(1f))

            // Auth tray. Contract: Apple (solid) → Google → Email. Dim + block hits while a call
            // is in flight; a spinner overlays.
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (state.isAuthenticating) 0.5f else 1f),
                    verticalArrangement = Arrangement.spacedBy(space.md),
                ) {
                    AuthButton("Continue with Apple", solid = true, testTag = "auth.apple", enabled = !state.isAuthenticating) {
                        viewModel.signInWithApple()
                    }
                    AuthButton("Continue with Google", solid = false, testTag = "auth.google", enabled = !state.isAuthenticating) {
                        viewModel.signInWithGoogle(context)
                    }
                    AuthButton("Continue with Email", solid = false, glyph = { Icon(Icons.Filled.Email, contentDescription = null, tint = colors.ink, modifier = Modifier.size(AppTheme.dimens.size.iconLg)) }, testTag = "auth.email", enabled = !state.isAuthenticating) {
                        showEmailSheet = true
                    }

                    state.error?.let { err ->
                        Text(err, style = AppTheme.type.footnote, color = colors.hot, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        "By continuing you agree to our Terms and Privacy Policy.",
                        style = AppTheme.type.caption,
                        color = colors.ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.isAuthenticating) {
                    CircularProgressIndicator(color = colors.brand, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    if (showEmailSheet) {
        EmailAuthSheet(
            state = state,
            startInSignUp = mode != SignupMode.Login,
            onSignIn = viewModel::signInWithEmail,
            onSignUp = { email, pw, name -> viewModel.signUpWithEmail(email, pw, name) },
            onDismiss = {
                showEmailSheet = false
                viewModel.clearError()
            },
        )
    }
}

/**
 * Uniform auth button (iOS `authLabel`): centred icon+title group, one height. Apple is the
 * single solid-white button (App Store 4.8 — first + most prominent); Google/Email are hairline
 * "glass" (a frosted look isn't a Compose primitive, so a translucent card + hairline border is
 * the faithful approximation). Press-scale mirrors the iOS button feedback.
 */
@Composable
private fun AuthButton(
    title: String,
    solid: Boolean,
    testTag: String,
    enabled: Boolean,
    glyph: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "authPress")
    val fg = if (solid) Color.Black else colors.ink

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .scale(scale)
            .clip(shape)
            .then(if (solid) Modifier.background(Color.White) else Modifier.background(colors.bgCard.copy(alpha = 0.6f)).border(1.dp, Color.White.copy(alpha = 0.18f), shape))
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .semantics { this.testTag = testTag; contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // The Apple/Google brand glyphs aren't bundled yet (see follow-ups); use a tinted
            // initial box as a stand-in so the layout + a11y are correct until the assets drop.
            glyph?.invoke() ?: Box(
                Modifier.size(AppTheme.dimens.size.iconLg),
                contentAlignment = Alignment.Center,
            ) {
                Text(title.substringAfter("with ").trim().take(1), color = fg, fontWeight = FontWeight.Bold)
            }
            Text(title, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = fg)
        }
    }
}
