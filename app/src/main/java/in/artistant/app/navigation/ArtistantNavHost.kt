package `in`.artistant.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.ui.OnboardingPlaceholder
import `in`.artistant.app.ui.RootGate
import `in`.artistant.app.ui.RootViewModel
import `in`.artistant.app.ui.auth.AuthScreen

/**
 * Top-level surface switch. The gate is now driven by [RootViewModel] (session status +
 * fetched profile), replacing the M0 hardcoded value. The theme re-derives from the gated
 * role, so the whole tree re-accents when a returning artist routes in.
 */
@Composable
fun ArtistantNavHost() {
    val viewModel: RootViewModel = hiltViewModel()
    val gate by viewModel.gate.collectAsStateWithLifecycle()

    when (val g = gate) {
        // While the persisted session restores, theme with the client accent and show nothing
        // (the auth screen would flash for a returning user otherwise).
        RootGate.Loading -> ArtistantTheme(role = AppRole.Client) {}

        RootGate.NotSignedIn ->
            ArtistantTheme(role = AppRole.Client) { AuthScreen() }

        RootGate.Onboarding ->
            ArtistantTheme(role = AppRole.Client) { OnboardingPlaceholder() }

        is RootGate.Tabs ->
            ArtistantTheme(role = g.role) {
                when (g.role) {
                    AppRole.Client -> ClientTabsScaffold()
                    AppRole.Artist -> ArtistTabsScaffold()
                }
            }
    }
}
