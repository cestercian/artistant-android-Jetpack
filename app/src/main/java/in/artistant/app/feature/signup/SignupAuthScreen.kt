package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.ui.auth.AuthUiState
import `in`.artistant.app.ui.auth.AuthViewModel

/**
 * Screen 12 — **OTP first, password never**.
 *
 * The design's note says it plainly: "phone is the identity in India; the privacy promise sits
 * in the legal line." So the first control on the screen is a mobile number and the primary
 * action texts a code. Apple and Google stay, below a rule, because they are one tap for
 * anyone who already has one. The password form is not on this screen at all — it is a
 * separate one (screen 28) reachable from the code step's escape hatch, which is where the
 * design puts it.
 *
 * There are two fields and one button, which is deliberate: "Or use email" is the alternative
 * ADDRESS for the same code, not a second kind of sign-in. Which one gets used is decided by
 * which one holds something valid (phone wins), so the button never has to ask.
 *
 * @param mode only changes the headline. Everything else about signing in is identical for a
 *   new account and a returning one — that is the point of a one-tap code.
 */
@Composable
fun SignupAuthScreen(
    mode: SignupMode,
    authNotice: String?,
    onCodeSent: () -> Unit,
    onOpenEmailSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenLegal: (LegalDoc) -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SignupAuthContent(
        mode = mode,
        state = state,
        authNotice = authNotice,
        onPhoneChange = viewModel::setPhone,
        onEmailChange = viewModel::setEmail,
        onSendCode = { viewModel.sendCode(onCodeSent) },
        onApple = viewModel::signInWithApple,
        onGoogle = viewModel::signInWithGoogle,
        onOpenEmailSignUp = onOpenEmailSignUp,
        onOpenLegal = onOpenLegal,
        onBack = onBack,
        modifier = modifier,
    )
}

/** The stateless half, so the previews (and a future screenshot test) can drive every state. */
@Composable
private fun SignupAuthContent(
    mode: SignupMode,
    state: AuthUiState,
    authNotice: String?,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onApple: () -> Unit,
    onGoogle: (android.content.Context) -> Unit,
    onOpenEmailSignUp: () -> Unit,
    onOpenLegal: (LegalDoc) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = state.isAuthenticating || state.isSendingCode

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.auth" },
        header = { SignupHeader(onBack = onBack) },
    ) {
        Spacer(Modifier.height(space.md))
        Text(
            if (mode == SignupMode.Login) "Welcome back" else "Let's get you in",
            style = AppTheme.type.screenTitle,
            color = colors.ink,
        )
        Spacer(Modifier.height(space.sm))
        Text(
            "We'll text a code — nothing to remember.",
            style = AppTheme.type.subtitle,
            color = colors.ink4,
        )

        if (authNotice != null) {
            Spacer(Modifier.height(space.lg))
            Banner(
                title = authNotice,
                tone = BannerTone.Note,
                modifier = Modifier.semantics { testTag = "auth.notice" },
            )
        }

        Spacer(Modifier.height(space.xl))
        AppTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = "Mobile number",
            hint = "98450 12345",
            // The rejected-number reason, under the field it is about. A number this app
            // cannot text is now REFUSED rather than trimmed to ten digits (see
            // [PhoneRules.national]), so the refusal has to be visible: without it a pasted
            // foreign number would sit in the field with a dead Send button and no reason.
            error = PhoneRules.error(state.phone),
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Next,
            ),
            leading = {
                Text(
                    PhoneRules.DIAL_CODE,
                    style = AppTheme.type.body.copy(fontWeight = FontWeight.Medium),
                    color = colors.ink2,
                )
            },
            trailing = {
                Text("IN ${PhoneRules.DIAL_CODE}", style = AppTheme.type.caption, color = colors.ink4)
            },
            modifier = Modifier.semantics { testTag = "auth.phone" },
        )
        Spacer(Modifier.height(space.md))
        AppTextField(
            value = state.email,
            onValueChange = onEmailChange,
            label = "Or use email",
            hint = "you@studio.in",
            enabled = !busy,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { if (state.canSendCode) { keyboard?.hide(); onSendCode() } }),
            modifier = Modifier.semantics { testTag = "auth.email" },
        )

        Spacer(Modifier.height(space.lg))
        PrimaryButton(
            text = if (state.isSendingCode) "Sending…" else "Send code",
            onClick = { keyboard?.hide(); onSendCode() },
            fullWidth = true,
            enabled = state.canSendCode && !busy,
            modifier = Modifier.semantics { testTag = "auth.sendCode" },
        )

        // Under the ACTION, not under a field. A failed send can come from either channel and
        // from neither field's contents — no SMS provider, a rate limit, no network — so
        // pinning it to the email input would point at the wrong thing about half the time.
        state.error?.let { message ->
            Spacer(Modifier.height(space.md))
            Banner(
                title = message,
                tone = BannerTone.Failure,
                modifier = Modifier.semantics { testTag = "auth.error" },
            )
        }

        Spacer(Modifier.height(space.xl))
        OrRule()
        Spacer(Modifier.height(space.xl))

        Box {
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                // No Apple or Google mark ships with the app yet (see the PR's follow-ups),
                // and an approximate one is worse than none: both brands publish exact
                // guidelines, and a hand-drawn logo on a sign-in screen reads as phishing.
                // The rows are labelled buttons until the assets land.
                ProviderButton(
                    title = "Continue with Apple",
                    enabled = !busy,
                    testTag = "auth.apple",
                    onClick = onApple,
                )
                ProviderButton(
                    title = "Continue with Google",
                    enabled = !busy,
                    testTag = "auth.google",
                    onClick = { onGoogle(context) },
                )
                ProviderButton(
                    title = "Sign up with email and password",
                    enabled = !busy,
                    testTag = "auth.emailPassword",
                    onClick = onOpenEmailSignUp,
                )
            }
            if (busy) {
                CircularProgressIndicator(
                    color = colors.accentInk,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        Spacer(Modifier.height(space.xl))
        LegalLine(onOpenLegal = onOpenLegal)
        Spacer(Modifier.height(space.xl))
    }
}

/** The "or" rule between the code path and the platform buttons. */
@Composable
private fun OrRule() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(Modifier.weight(1f).height(dimens.size.hairline).background(colors.hairline))
        Text("or", style = AppTheme.type.caption, color = colors.ink4)
        Box(Modifier.weight(1f).height(dimens.size.hairline).background(colors.hairline))
    }
}

/**
 * A platform sign-in row: hairline outline, centred glyph + label, `control` tall.
 *
 * Both providers are ink-on-light. The dark design gave the primary one a WHITE fill and the
 * secondary a translucent card with a white rim, which on a `#fafaf6` page is a white button
 * on off-white behind an invisible border — two controls you cannot find. Screen 12 draws both
 * as outlined rows, so the difference between them is the glyph, not the weight.
 */
@Composable
private fun ProviderButton(
    title: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.control)
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimens.size.ctaTall)
            .pressScale(interaction)
            .clip(shape)
            .background(colors.surface)
            .border(dimens.size.hairline, colors.hairline, shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                this.testTag = testTag
                contentDescription = title
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = AppTheme.type.rowTitle.copy(fontSize = AppTheme.type.body.fontSize),
                color = if (enabled) colors.ink else colors.ink4,
            )
        }
    }
}

/**
 * The legal line, and the privacy promise inside it.
 *
 * "Your number is never shown to an artist before a booking is confirmed" is not marketing —
 * it is how the platform works, and screen 62 repeats it word for word under the privacy
 * toggles precisely because it is the one thing there that is NOT a setting.
 */
@Composable
private fun LegalLine(onOpenLegal: (LegalDoc) -> Unit) {
    val colors = AppTheme.colors
    Text(
        buildAnnotatedString {
            append("By continuing you agree to the ")
            withLink(legalLink(colors.accentInk) { onOpenLegal(LegalDoc.Terms) }) { append("Terms") }
            append(" and ")
            withLink(legalLink(colors.accentInk) { onOpenLegal(LegalDoc.Privacy) }) {
                append("Privacy Policy")
            }
            append(". Your number is never shown to an artist before a booking is confirmed.")
        },
        style = AppTheme.type.caption,
        color = colors.ink4,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun SignInPreview() {
    ArtistantTheme {
        SignupAuthContent(
            mode = SignupMode.Login,
            state = AuthUiState(phone = "9845012345"),
            authNotice = null,
            onPhoneChange = {},
            onEmailChange = {},
            onSendCode = {},
            onApple = {},
            onGoogle = {},
            onOpenEmailSignUp = {},
            onOpenLegal = {},
            onBack = {},
        )
    }
}
