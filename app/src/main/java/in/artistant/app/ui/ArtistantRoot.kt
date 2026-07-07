package `in`.artistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Auth gate — the ArtistantRoot analogue. Decides the top-level surface:
 *
 *   not signed in .......... Signup placeholder
 *   signed in + client ..... ClientTabs
 *   signed in + artist ..... ArtistTabs
 *
 * The gate STRUCTURE is real (a sealed decision the NavHost switches on); the
 * inputs are hardcoded for M0. Real session/role state (SessionManager +
 * AppPreferences) wires in at M1. ponytail: no ViewModel yet — a hardcoded
 * gate value is enough to prove the branch; add the store when auth exists.
 */
sealed interface RootGate {
    data object Signup : RootGate
    data class Tabs(val role: AppRole) : RootGate
}

/** M0 default: signed-out → Signup. Flip here to eyeball the other branches. */
fun m0Gate(): RootGate = RootGate.Signup

/**
 * The signup placeholder — a labelled screen with a note that auth lands in M1.
 * Buttons exist so the gate's two tab branches are reachable to a reviewer
 * without a debugger, but they don't fake a real sign-in.
 */
@Composable
fun SignupPlaceholder(onEnter: (AppRole) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Artistant", style = AppTheme.type.displayHero, color = AppTheme.colors.ink)
        Text(
            "Signup — auth lands in M1",
            style = AppTheme.type.callout,
            color = AppTheme.colors.ink2,
        )
        Button(
            onClick = { onEnter(AppRole.Client) },
            colors = ButtonDefaults.buttonColors(
                containerColor = AppRole.Client.let { AppTheme.colors.brand },
            ),
        ) { Text("Preview client tabs", color = AppTheme.colors.brandInk) }
        Button(onClick = { onEnter(AppRole.Artist) }) { Text("Preview artist tabs") }
    }
}
