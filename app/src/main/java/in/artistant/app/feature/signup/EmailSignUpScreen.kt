package `in`.artistant.app.feature.signup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.EmailRules
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.ui.auth.AuthUiState
import `in`.artistant.app.ui.auth.AuthViewModel

/**
 * Screen 28 — **the reviewable path**.
 *
 * The design's note is the justification: "email exists for App Review and for anyone without
 * a platform account." It is not the primary way in and the screen does not pretend to be —
 * it is reached from the sign-in screen and from the code screen's escape hatch, and its
 * footer points back the other way ("Or go back for Apple and Google").
 *
 * The password rule shown is **eight** characters, not GoTrue's six. The design draws eight
 * with a tick beside it, and a rule you state and then do not enforce is worse than no rule:
 * the tick would go green on a password the screen had just called too short. So the tick and
 * the button agree, and both are stricter than the server — which is always allowed, and never
 * the reverse.
 */
@Composable
fun EmailSignUpScreen(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EmailSignUpContent(
        state = state,
        onCancel = onCancel,
        onSubmit = { email, password, name -> viewModel.signUpWithEmail(email, password, name) },
        onForgotPassword = viewModel::sendPasswordReset,
        modifier = modifier,
    )
}

@Composable
private fun EmailSignUpContent(
    state: AuthUiState,
    onCancel: () -> Unit,
    onSubmit: (email: String, password: String, fullName: String?) -> Unit,
    onForgotPassword: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val keyboard = LocalSoftwareKeyboardController.current

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var revealed by remember { mutableStateOf(false) }

    val longEnough = password.length >= MIN_PASSWORD_LENGTH
    val canSubmit = !state.isAuthenticating && EmailRules.isValid(email) && longEnough
    val submit = { keyboard?.hide(); onSubmit(email, password, name.ifBlank { null }) }

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.emailSignUp" },
        header = {
            SignupHeader(
                onBack = onCancel,
                trailing = {
                    InlineLink(
                        "Cancel",
                        onCancel,
                        style = AppTheme.type.subtitle,
                        modifier = Modifier.semantics { testTag = "emailSignUp.cancel" },
                    )
                },
            )
        },
        footer = {
            PrimaryButton(
                text = if (state.isAuthenticating) "Creating…" else "Create account",
                onClick = submit,
                fullWidth = true,
                enabled = canSubmit,
                modifier = Modifier.semantics { testTag = "emailSignUp.submit" },
            )
            Text(
                "Or go back for Apple and Google",
                style = AppTheme.type.caption,
                color = colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Spacer(Modifier.height(space.md))
        Text("Sign up with email", style = AppTheme.type.screenTitle, color = colors.ink)
        Spacer(Modifier.height(space.sm))
        Text("The fallback that always works.", style = AppTheme.type.subtitle, color = colors.ink4)

        Spacer(Modifier.height(space.xl))
        AppTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            hint = "Rhea Menon",
            enabled = !state.isAuthenticating,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.semantics { testTag = "emailSignUp.name" },
        )
        Spacer(Modifier.height(space.md))
        AppTextField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            hint = "you@studio.in",
            enabled = !state.isAuthenticating,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
            modifier = Modifier.semantics { testTag = "emailSignUp.email" },
        )
        Spacer(Modifier.height(space.md))
        AppTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            enabled = !state.isAuthenticating,
            // Masking is a DRAWING change, so the keyboard type has to say "password" too —
            // otherwise the IME runs autocorrect over it and can put it in the suggestion strip.
            visualTransformation = if (revealed) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }),
            trailing = {
                InlineLink(
                    if (revealed) "Hide" else "Show",
                    { revealed = !revealed },
                    style = AppTheme.type.caption,
                    modifier = Modifier.semantics { testTag = "emailSignUp.reveal" },
                )
            },
            modifier = Modifier.semantics { testTag = "emailSignUp.password" },
        )

        Spacer(Modifier.height(space.md))
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(dimens.size.iconMd)
                    .clip(RoundedCornerShape(dimens.radii.xs))
                    .background(if (longEnough) colors.accent else colors.hairline),
                contentAlignment = Alignment.Center,
            ) {
                if (longEnough) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.onAccent,
                        modifier = Modifier.size(dimens.size.iconSm),
                    )
                }
            }
            Text(
                "At least $MIN_PASSWORD_LENGTH characters",
                style = AppTheme.type.caption,
                color = if (longEnough) colors.ink2 else colors.ink4,
            )
        }

        Spacer(Modifier.height(space.lg))
        Banner(
            title = "Already have an account with this email? We'll sign you in instead of " +
                "creating a second one.",
            tone = BannerTone.Note,
        )

        if (state.confirmationRequired) {
            Spacer(Modifier.height(space.md))
            Banner(
                title = "Check your inbox",
                tone = BannerTone.Info,
                detail = "We've emailed you a link. Open it, then come back and sign in.",
                modifier = Modifier.semantics { testTag = "emailSignUp.confirmation" },
            )
        }
        state.error?.let { message ->
            Spacer(Modifier.height(space.md))
            Banner(
                title = message,
                tone = BannerTone.Failure,
                modifier = Modifier.semantics { testTag = "emailSignUp.error" },
            )
        }

        Spacer(Modifier.height(space.xl))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            InlineLink(
                "Forgot password?",
                { onForgotPassword(email) },
                modifier = Modifier.semantics { testTag = "emailSignUp.forgot" },
            )
        }
        Spacer(Modifier.height(space.lg))
    }
}

/**
 * The rule the screen states, and therefore the rule it enforces.
 *
 * `PasswordRules.MIN_LENGTH` is six, which is GoTrue's floor and what the sign-IN guard uses —
 * an existing account may well have a six-character password and must still be able to get in.
 * A NEW one does not have that constraint, so this screen can hold the line the design draws.
 */
private const val MIN_PASSWORD_LENGTH = 8

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun EmailSignUpPreview() {
    ArtistantTheme {
        EmailSignUpContent(
            state = AuthUiState(),
            onCancel = {},
            onSubmit = { _, _, _ -> },
            onForgotPassword = {},
        )
    }
}
