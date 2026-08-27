package `in`.artistant.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.shape.RoundedCornerShape
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Email/password sheet — sign-in and sign-up in one, toggled by [signUpMode] (seeded from
 * [startInSignUp]: signup flow opens in sign-up, login opens in sign-in). Client-side validation
 * lives in [AuthViewModel]; this only collects fields and shows outcomes. On a confirmation-
 * required sign-up it shows the "check your inbox" note rather than dismissing.
 *
 * Made a top-level public composable in M1b so the polished `SignupAuthScreen` reuses it verbatim
 * (was a private helper of the M1a AuthScreen, whose plain three-button entry the signup flow's
 * `SignupAuthScreen` replaced).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailAuthSheet(
    state: AuthUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, fullName: String?) -> Unit,
    onDismiss: () -> Unit,
    startInSignUp: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState()
    val colors = AppTheme.colors
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var signUpMode by remember { mutableStateOf(startInSignUp) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppTheme.dimens.space.xl)
                .padding(bottom = AppTheme.dimens.space.xxl),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
        ) {
            Text(
                if (signUpMode) "Create your account" else "Sign in",
                style = AppTheme.type.title,
                color = colors.ink,
            )

            // Material's default text-field corner is ~4dp — effectively square,
            // and out of step with every other surface in this app. One shape
            // for all three fields so the stack reads as a single form.
            val fieldShape = RoundedCornerShape(AppTheme.dimens.radii.md)
            val canSubmit = !state.isAuthenticating && email.isNotBlank() && password.isNotBlank()
            val submit = {
                if (signUpMode) onSignUp(email, password, fullName.ifBlank { null })
                else onSignIn(email, password)
            }
            // Every field states its own keyboard, as iOS does (EmailAuthView.field: .words on
            // the name, .emailAddress + never-capitalize on the email, secure on the password).
            // Left at the Compose default all three are plain text fields, and the password one
            // is the one that matters: `PasswordVisualTransformation` only masks what is DRAWN,
            // so the IME still runs autocorrect and predictive text over the typed password and
            // can put it in the suggestion strip. Next/Done also give the stack a keyboard walk
            // instead of making the user dismiss and tap between fields.
            if (signUpMode) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = fieldShape,
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                // The IME's Done key submits the same form the button does — and only when
                // the button itself would be tappable, so it can't fire a doomed request or
                // a second one over the one in flight.
                keyboardActions = KeyboardActions(onDone = { if (canSubmit) submit() }),
                modifier = Modifier.fillMaxWidth(),
                shape = fieldShape,
            )

            if (state.confirmationRequired) {
                Text(
                    "Check your inbox to confirm your email, then sign in.",
                    style = AppTheme.type.footnote,
                    color = colors.good,
                )
            }
            state.error?.let { Text(it, style = AppTheme.type.footnote, color = colors.hot) }

            PrimaryButton(
                text = if (signUpMode) "Create account" else "Sign in",
                onClick = submit,
                fullWidth = true,
                enabled = canSubmit,
            )
            TextButton(onClick = { signUpMode = !signUpMode }) {
                Text(
                    if (signUpMode) "Have an account? Sign in" else "New here? Create an account",
                    color = colors.ink2,
                )
            }
        }
    }
}
