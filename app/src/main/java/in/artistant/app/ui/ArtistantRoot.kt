package `in`.artistant.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Auth gate — the ArtistantRoot / iOS `authGatedContent` analogue. Three tiers, decided
 * by [RootViewModel] from the session status + the fetched server profile:
 *
 *   NotSignedIn ......... AuthScreen (Apple / Google / Email)
 *   Onboarding .......... onboarding placeholder (full 8-screen flow lands in M1b)
 *   Tabs(role) .......... role tabs
 *
 * The Onboarding tier stands in for the iOS signup flow past `.auth` (role/profile/notif).
 * ponytail: no per-screen onboarding UX yet — M1b owns that; a placeholder proves the gate.
 */
sealed interface RootGate {
    /** Still restoring the persisted session — show nothing (avoids an auth-screen flash). */
    data object Loading : RootGate
    data object NotSignedIn : RootGate
    data object Onboarding : RootGate
    data class Tabs(val role: AppRole) : RootGate
}

/**
 * Placeholder for the post-auth onboarding flow (role → profile → notif → done). Real
 * screens land in M1b; this proves the "signed-in but incomplete profile" gate tier.
 */
@Composable
fun OnboardingPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Almost there", style = AppTheme.type.displayHero, color = AppTheme.colors.ink)
        Text(
            "Signed in — the profile/role onboarding flow lands in M1b.",
            style = AppTheme.type.callout,
            color = AppTheme.colors.ink2,
        )
    }
}
