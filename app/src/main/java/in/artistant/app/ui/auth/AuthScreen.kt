package `in`.artistant.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Functional auth entry (the iOS `AuthScreen` port, minus the animated hero — M1b polishes
 * it). Three sign-in buttons wired to [AuthViewModel]; Email opens [EmailAuthSheet]. Uses
 * design tokens throughout (no raw hex/dp/sp beyond layout spacing constants).
 */
@Composable
fun AuthScreen(viewModel: AuthViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showEmailSheet by remember { mutableStateOf(false) }
    val colors = AppTheme.colors

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Artistant", style = AppTheme.type.displayHero, color = colors.ink)
        Text(
            "Book live artists across India.",
            style = AppTheme.type.callout,
            color = colors.ink2,
        )
        Spacer(Modifier.height(8.dp))

        // Apple = solid button; Google/Email = ghost (hairline) — one signal at a time.
        PrimaryButton(
            text = "Continue with Apple",
            onClick = viewModel::signInWithApple,
            fullWidth = true,
            enabled = !state.isAuthenticating,
        )
        PrimaryButton(
            text = "Continue with Google",
            onClick = { viewModel.signInWithGoogle(context) },
            variant = ButtonVariant.Ghost,
            fullWidth = true,
            enabled = !state.isAuthenticating,
        )
        PrimaryButton(
            text = "Continue with email",
            onClick = { showEmailSheet = true },
            variant = ButtonVariant.Ghost,
            fullWidth = true,
            enabled = !state.isAuthenticating,
        )

        if (state.isAuthenticating) {
            CircularProgressIndicator(color = colors.brand)
        }
        state.error?.let { err ->
            Text(err, style = AppTheme.type.footnote, color = colors.hot)
        }
    }

    if (showEmailSheet) {
        EmailAuthSheet(
            state = state,
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
 * Email/password sheet — sign-in and sign-up in one, toggled by [signUpMode]. Client-side
 * validation lives in [AuthViewModel]; this only collects fields and shows outcomes. On a
 * confirmation-required sign-up it shows the "check your inbox" note rather than dismissing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailAuthSheet(
    state: AuthUiState,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (email: String, password: String, fullName: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val colors = AppTheme.colors
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var signUpMode by remember { mutableStateOf(false) }

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
