package `in`.artistant.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (signUpMode) "Create your account" else "Sign in",
                style = AppTheme.type.title,
                color = colors.ink,
            )

            if (signUpMode) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
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
                onClick = {
                    if (signUpMode) onSignUp(email, password, fullName.ifBlank { null })
                    else onSignIn(email, password)
                },
                fullWidth = true,
                enabled = !state.isAuthenticating && email.isNotBlank() && password.isNotBlank(),
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
