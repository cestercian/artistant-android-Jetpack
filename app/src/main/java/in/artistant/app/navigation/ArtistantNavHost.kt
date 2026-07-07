package `in`.artistant.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.feature.signup.SignupFlow
import `in`.artistant.app.feature.signup.SignupMode
import `in`.artistant.app.feature.signup.SignupStep
import `in`.artistant.app.feature.signup.SignupViewModel
import `in`.artistant.app.ui.RootGate
import `in`.artistant.app.ui.RootViewModel

/**
 * Top-level surface switch. The gate is driven by [RootViewModel] (session status + fetched
 * profile). Both the not-signed-in and signed-in-but-incomplete tiers present the SAME
 * [SignupFlow], hoisted here (activity-scoped) so its ViewModel — and thus the user's in-flow
 * progress — survives the gate's re-render when a completed sign-in flips NotSignedIn →
 * Onboarding. The theme follows the flow's picked role so it re-accents live after the role step.
 */
@Composable
fun ArtistantNavHost() {
    val viewModel: RootViewModel = hiltViewModel()
    val gate by viewModel.gate.collectAsStateWithLifecycle()

    // Hoisted above the gate `when` so the same instance is shared across the NotSignedIn →
    // Onboarding swap (a VM created inside a `when` branch dies when the branch changes).
    val signupVm: SignupViewModel = hiltViewModel()
    val signupState by signupVm.state.collectAsStateWithLifecycle()

    // Prefill the flow with a returning user's server profile (login personalization parity) as
    // soon as routing fetches one.
    val routedProfile by viewModel.profile.collectAsStateWithLifecycle()
    LaunchedEffect(routedProfile) {
        routedProfile?.let { signupVm.hydrate(it.role, it.fullName, it.city, it.handle) }
    }

    val hydrationError by viewModel.profileHydrationError.collectAsStateWithLifecycle()
    val reduceMotion = isReduceMotionOn()

    when (val g = gate) {
        // While the persisted session restores, theme with the client accent and show nothing
        // (the auth screen would flash for a returning user otherwise).
        RootGate.Loading -> ArtistantTheme(role = AppRole.Client) {}

        RootGate.NotSignedIn ->
            ArtistantTheme(role = signupState.role) {
                SignupFlow(
                    startStep = SignupStep.Welcome,
                    startMode = SignupMode.Signup,
                    onFinished = viewModel::markSignupComplete,
                    reduceMotion = reduceMotion,
                    viewModel = signupVm,
                )
            }

        RootGate.Onboarding ->
            // Signed in, profile incomplete → resume mid-flow at Profile. Always in Signup mode:
            // an incomplete profile must walk the full profile → notif → done tail, which only
            // exists in the signup order (a login-mode user who landed here still needs it).
            ArtistantTheme(role = signupState.role) {
                SignupFlow(
                    startStep = SignupStep.Profile,
                    startMode = SignupMode.Signup,
                    onFinished = viewModel::markSignupComplete,
                    profileHydrationError = hydrationError,
                    onRetryHydration = viewModel::retryRouting,
                    reduceMotion = reduceMotion,
                    viewModel = signupVm,
                )
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

/** Read the system "remove animations" a11y setting so the auth lineup can freeze (iOS
 *  reduce-motion parity). Cheap enough to read on each recomposition of the gate. */
@Composable
private fun isReduceMotionOn(): Boolean {
    val context = LocalContext.current
    // Settings.Global.ANIMATOR_DURATION_SCALE == 0 means the user disabled animations.
    val scale = android.provider.Settings.Global.getFloat(
        context.contentResolver,
        android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}
