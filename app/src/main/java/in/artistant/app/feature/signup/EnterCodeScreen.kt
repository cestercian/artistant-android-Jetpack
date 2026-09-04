package `in`.artistant.app.feature.signup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.OtpField
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.ui.auth.AuthUiState
import `in`.artistant.app.ui.auth.AuthViewModel
import `in`.artistant.app.ui.auth.OtpChannel

/**
 * Screen 119 — **OTP is the primary path**.
 *
 * The design's note names the three things a code screen always needs, and all three are here:
 * autofill (one real field behind six drawn boxes, which is what lets the platform paste a
 * whole code into it — see [OtpField]), a resend timer, and an escape to email.
 *
 * The escape is not offered immediately. The screen's own copy explains why — "Indian carriers
 * can delay OTPs by a minute or two. After two failed sends we offer email sign-in instead" —
 * so the password form appears after the second send and not before, which is
 * [AuthUiState.offersEmailEscape].
 *
 * **"Sent to …" is the real destination**, read back from what was actually sent
 * ([AuthUiState.codeDestination]) rather than re-derived from the field. Those two can differ
 * — the user can keep typing after tapping Send — and a code screen that names the wrong
 * number is a code screen that makes people re-check a message that was correct.
 */
@Composable
fun EnterCodeScreen(
    onChangeNumber: () -> Unit,
    onUseEmailInstead: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EnterCodeContent(
        state = state,
        onCodeChange = viewModel::setCode,
        onVerify = viewModel::verifyCode,
        onResend = { viewModel.sendCode() },
        onChangeNumber = {
            viewModel.resetCodeAttempt()
            onChangeNumber()
        },
        onUseEmailInstead = {
            viewModel.resetCodeAttempt()
            onUseEmailInstead()
        },
        modifier = modifier,
    )
}

@Composable
private fun EnterCodeContent(
    state: AuthUiState,
    onCodeChange: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onChangeNumber: () -> Unit,
    onUseEmailInstead: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val bySms = state.codeChannel == OtpChannel.Sms
    val destination = if (bySms) PhoneRules.display(state.codeDestination) else state.codeDestination

    SignupScaffold(
        modifier = modifier.semantics { testTag = "screen.code" },
        header = { SignupHeader(onBack = onChangeNumber) },
        footer = {
            PrimaryButton(
                text = if (state.isVerifying) "Verifying…" else "Verify",
                onClick = onVerify,
                fullWidth = true,
                enabled = state.canVerify,
                modifier = Modifier.semantics { testTag = "code.verify" },
            )
        },
    ) {
        Spacer(Modifier.height(space.md))
        Text("Enter the code", style = AppTheme.type.screenTitle, color = colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(
            if (destination.isBlank()) {
                "We've sent you a six-digit code."
            } else if (bySms) {
                "Sent to $destination by SMS."
            } else {
                "Sent to $destination by email."
            },
            style = AppTheme.type.subtitle,
            color = colors.ink4,
        )

        Spacer(Modifier.height(space.xl))
        OtpField(
            value = state.code,
            onValueChange = onCodeChange,
            enabled = !state.isVerifying,
            isError = state.codeError != null,
            onFilled = onVerify,
            modifier = Modifier.semantics { testTag = "code.field" },
        )

        if (state.codeError != null) {
            Spacer(Modifier.height(space.sm))
            Text(
                state.codeError,
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.danger,
                modifier = Modifier.semantics { testTag = "code.error" },
            )
        }

        Spacer(Modifier.height(space.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // While the cooldown runs this is a statement, not a control — so it is a Text,
            // not a disabled link. A greyed-out "Resend in 0:24" invites taps that do nothing.
            if (state.canResend) {
                InlineLink(
                    OtpResend.label(state.resendSeconds),
                    onResend,
                    modifier = Modifier.semantics { testTag = "code.resend" },
                )
            } else {
                Text(
                    OtpResend.label(state.resendSeconds),
                    style = AppTheme.type.subtitle,
                    color = colors.ink3,
                    modifier = Modifier.semantics { testTag = "code.resendCountdown" },
                )
            }
            InlineLink(
                if (bySms) "Change number" else "Change email",
                onChangeNumber,
                modifier = Modifier.semantics { testTag = "code.change" },
            )
        }

        Spacer(Modifier.height(space.lg))
        Banner(
            title = "Autofill works — if the SMS arrives while you're here, tap the keyboard suggestion.",
            tone = BannerTone.Note,
        )

        Spacer(Modifier.height(space.xl))
        SignupEyebrow("NOT ARRIVING?")
        Spacer(Modifier.height(space.sm))
        Text(
            "Indian carriers can delay OTPs by a minute or two. After two failed sends we offer " +
                "email sign-in instead.",
            style = AppTheme.type.subtitle,
            color = colors.ink2,
        )
        if (state.offersEmailEscape) {
            Spacer(Modifier.height(space.sm))
            InlineLink(
                "Use email and a password instead",
                onUseEmailInstead,
                modifier = Modifier.semantics { testTag = "code.emailEscape" },
            )
        }
        Spacer(Modifier.height(space.xl))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 860)
@Composable
private fun EnterCodePreview() {
    ArtistantTheme {
        EnterCodeContent(
            state = AuthUiState(
                code = "4729",
                codeDestination = "+919845012345",
                resendSeconds = 24,
                sendCount = 1,
            ),
            onCodeChange = {},
            onVerify = {},
            onResend = {},
            onChangeNumber = {},
            onUseEmailInstead = {},
        )
    }
}
