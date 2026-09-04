package `in`.artistant.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.ToastHost
import `in`.artistant.app.designsystem.component.lightTabBarHeight
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.feature.signup.SignupFlow
import `in`.artistant.app.feature.signup.SplashScreen
import `in`.artistant.app.feature.signup.SignupMode
import `in`.artistant.app.feature.signup.SignupStep
import `in`.artistant.app.feature.signup.SignupViewModel
import `in`.artistant.app.feature.system.ServiceOutageScreen
import `in`.artistant.app.feature.system.SystemGate
import `in`.artistant.app.feature.system.SystemGateViewModel
import `in`.artistant.app.feature.system.ToastIcon
import `in`.artistant.app.feature.system.ToastViewModel
import `in`.artistant.app.feature.system.UpdateRequiredScreen
import `in`.artistant.app.feature.system.WhatsNewHost
import `in`.artistant.app.feature.wizard.WizardScreen
import `in`.artistant.app.ui.RootGate
import `in`.artistant.app.ui.RootViewModel

/**
 * Top-level surface switch. The gate is driven by [RootViewModel] (session status + fetched
 * profile). Both the not-signed-in and signed-in-but-incomplete tiers present the SAME
 * [SignupFlow], hoisted here (activity-scoped) so its ViewModel — and thus the user's in-flow
 * progress — survives the gate's re-render when a completed sign-in flips NotSignedIn →
 * Onboarding. The theme follows the flow's picked role so it re-accents live after the role step.
 *
 * Three app-level surfaces of section SH sit around that switch:
 *
 *  - the **system gates** (screens 120 / 121), which REPLACE the app and therefore
 *    out-rank the auth gate — an unsupported build writes the same rows from the
 *    signup flow as from the tabs, and an outage met on the welcome screen is the
 *    same outage;
 *  - the single **toast host** (screen 77), above every tier, so a confirmation
 *    raised by a screen that is being popped still has somewhere to render;
 *  - **What's new** (screen 137), once per version, over the tab shell only.
 *
 * [ArtistantTheme] is applied ONCE here rather than per branch, so those three
 * can share the composition without nesting a second copy of it. The role it
 * takes is the tier's own, computed below; since the redesign retired the
 * two-accent split (`withRole` is identity) the choice no longer changes a
 * colour, but navigation and the paywall still branch on it.
 */
@Composable
fun ArtistantNavHost() {
    val viewModel: RootViewModel = hiltViewModel()
    val gate by viewModel.gate.collectAsStateWithLifecycle()

    val systemVm: SystemGateViewModel = hiltViewModel()
    val system by systemVm.state.collectAsStateWithLifecycle()

    val toastVm: ToastViewModel = hiltViewModel()
    val toast by toastVm.current.collectAsStateWithLifecycle()

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

    // A session that ended — sign-out or delete-account — must not leave the
    // departing account's draft in this activity-scoped VM (see SignupViewModel.reset).
    // The gate arriving at NotSignedIn is the one signal that covers every route
    // out, including a returning user who went straight to Tabs and never composed
    // the flow at all, which is why this is keyed on the gate rather than on the
    // flow's own signed-in bit. It fires once per transition, so it cannot clobber
    // an in-flight signup: the whole pre-auth walk happens with the gate already
    // sitting on NotSignedIn. A session that EXPIRES mid-flow lands here too and
    // costs the user the fields they typed — the right side of that trade, since
    // the alternative is handing them to whoever signs in next.
    LaunchedEffect(gate) { if (gate == RootGate.NotSignedIn) signupVm.reset() }

    val hydrationError by viewModel.profileHydrationError.collectAsStateWithLifecycle()

    val themeRole = when (val g = gate) {
        RootGate.NotSignedIn, RootGate.Onboarding -> signupState.role
        RootGate.ArtistWizard -> AppRole.Artist
        is RootGate.Tabs -> g.role
        RootGate.Loading -> AppRole.Client
    }

    ArtistantTheme(role = themeRole) {
        Box(Modifier.fillMaxSize()) {
            val blocked = system.gate != SystemGate.None
            when (val blocker = system.gate) {
                is SystemGate.Update ->
                    UpdateRequiredScreen(installed = blocker.installed, minimum = blocker.minimum)

                is SystemGate.Outage -> ServiceOutageScreen(
                    impact = blocker.impact,
                    startedLabel = blocker.startedLabel,
                    checking = system.checking,
                    onCheckAgain = systemVm::checkAgain,
                    onBack = systemVm::dismissOutage,
                )

                SystemGate.None -> when (val g = gate) {
                    // While the persisted session restores, hold on design screen 01 — "the one
                    // dark room". It is the same `darkest` the launch window is painted in, so
                    // the hand-off from the pre-Compose window has no seam; the previous empty
                    // tree showed the bare window instead, which flashed white between a black
                    // launch screen and whatever came next.
                    RootGate.Loading -> SplashScreen()

                    RootGate.NotSignedIn -> SignupFlow(
                        startStep = SignupStep.Welcome,
                        startMode = SignupMode.Signup,
                        onFinished = viewModel::markSignupComplete,
                        signedIn = false,
                        viewModel = signupVm,
                    )

                    // Signed in, profile incomplete → resume mid-flow at Profile. Always in
                    // Signup mode: an incomplete profile must walk the full profile → notif →
                    // done tail, which only exists in the signup order (a login-mode user who
                    // landed here still needs it). (Incomplete-EPK artists are
                    // [RootGate.ArtistWizard], not this branch.)
                    //
                    // `signedIn = true` is what this tier MEANS — the router only reaches it
                    // from an Authenticated session. The flow needs it stated, because
                    // re-routing to this same `data object` is conflated by MutableStateFlow
                    // and re-fires none of its keys.
                    //
                    // `.Profile` is what this tier ASKS for, not always where it lands: the
                    // container resolves it through `entryStep`, which holds the entry at the
                    // community pledge (screen 27, rendered by `.Role`) until the device has
                    // taken it. Without that, this branch was a way to complete onboarding
                    // having never been shown the pledge.
                    RootGate.Onboarding -> SignupFlow(
                        startStep = SignupStep.Profile,
                        startMode = SignupMode.Signup,
                        onFinished = viewModel::markSignupComplete,
                        profileHydrationError = hydrationError,
                        onRetryHydration = viewModel::retryRouting,
                        signedIn = true,
                        viewModel = signupVm,
                    )

                    RootGate.ArtistWizard ->
                        WizardScreen(onFinished = viewModel::markWizardComplete)

                    is RootGate.Tabs -> when (g.role) {
                        AppRole.Client -> ClientTabsScaffold()
                        AppRole.Artist -> ArtistTabsScaffold()
                    }
                }
            }

            // Screen 137. Over the tab shell only: a release-notes sheet on top
            // of the signup flow interrupts the one walk that must not be
            // interrupted, and a brand-new account has nothing to be told what's
            // new about.
            if (!blocked && gate is RootGate.Tabs) WhatsNewHost()

            // Screen 77 — the ONE host. Suppressed behind a system gate, where a
            // toast raised by the app underneath would be talking about a screen
            // nobody can see.
            //
            // The bottom padding clears whatever chrome is actually on screen:
            // the tab bar owns the bottom edge of a tab shell, and the host is
            // not inside the `Scaffold` that insets content above it.
            if (!blocked) {
                val gap = AppTheme.dimens.component.toastGap
                ToastHost(
                    message = toast?.text,
                    key = toast?.id,
                    onDismiss = { toastVm.dismiss(toast?.id) },
                    icon = when (toast?.icon) {
                        ToastIcon.Flag -> Icons.Filled.Flag
                        ToastIcon.Info -> Icons.Outlined.Info
                        else -> Icons.Filled.Check
                    },
                    bottomPadding = if (gate is RootGate.Tabs) gap + lightTabBarHeight() else gap,
                )
            }
        }
    }
}
