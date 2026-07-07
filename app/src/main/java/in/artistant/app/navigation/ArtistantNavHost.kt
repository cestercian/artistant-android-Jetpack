package `in`.artistant.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.ui.RootGate
import `in`.artistant.app.ui.SignupPlaceholder
import `in`.artistant.app.ui.m0Gate

/**
 * Top-level surface switch. Holds the [RootGate] as state so the M0 preview
 * buttons can flip it (signup → tabs) without a real session. The chosen
 * role also re-themes the whole tree (ArtistantTheme(role)) — proving the
 * role-reactive accent works end to end.
 *
 * At M1 the gate value comes from SessionManager/AppPreferences instead of
 * local state, and this switch stays the same.
 */
@Composable
fun ArtistantNavHost() {
    var gate by remember { mutableStateOf(m0Gate()) }

    when (val g = gate) {
        RootGate.Signup ->
            // Signup is single-role UI; theme it with the client accent.
            ArtistantTheme(role = AppRole.Client) {
                SignupPlaceholder(onEnter = { role -> gate = RootGate.Tabs(role) })
            }

        is RootGate.Tabs ->
            ArtistantTheme(role = g.role) {
                when (g.role) {
                    AppRole.Client -> ClientTabsScaffold()
                    AppRole.Artist -> ArtistTabsScaffold()
                }
            }
    }
}
