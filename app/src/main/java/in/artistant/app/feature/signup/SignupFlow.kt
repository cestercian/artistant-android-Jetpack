package `in`.artistant.app.feature.signup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween

/**
 * The signup container (iOS `SignupFlowView`): switches on `step` with a crossfade, ships the
 * hydration-error banner, and routes one-shot events (haptics + finish). The gate presents this
 * at the right entry step — welcome for a not-signed-in user, profile for a signed-in-but-
 * incomplete one — via [startStep]/[startMode]/[resume].
 *
 * @param profileHydrationError a login-hydrate failure the gate surfaces here as a Retry banner.
 * @param onRetryHydration re-runs the failed profile fetch (gate-owned, since it drives routing).
 * @param onFinished fires when the user taps "Start exploring" — the gate re-routes into tabs.
 * @param signedIn whether a live session exists — true on the gate's signed-in-but-incomplete
 *   tier, false on the not-signed-in one. The flow can't read the session itself (the repository
 *   seam), and the gate value alone can't tell it either: `RootGate.Onboarding` is a `data
 *   object`, so a re-route to the same tier is conflated and re-fires nothing.
 * @param reduceMotion freezes the auth lineup for a11y.
 */
@Composable
fun SignupFlow(
    startStep: SignupStep,
    startMode: SignupMode,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    profileHydrationError: String? = null,
    onRetryHydration: () -> Unit = {},
    signedIn: Boolean = false,
    reduceMotion: Boolean = false,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    // Seed the flow at the gate's entry step once. resumeAt is idempotent, so a recomposition or
    // a gate re-render (NotSignedIn → Onboarding) won't clobber the user's in-flow progress.
    LaunchedEffect(startStep, startMode) { viewModel.resumeAt(startStep, startMode) }

    // Report the gate's session bit into the flow (iOS RootView.handleAuthChange →
    // didCompleteAuth). Keyed on the STEP as well as the bit, so a landing on `.Auth` while a
    // session is live — the profile-save session guard, a back press that raced this — is
    // corrected the moment it happens. Runs after resumeAt above (declaration order), so the
    // gate's entry step is already seeded and this only ever fixes a genuinely stranded step.
    LaunchedEffect(signedIn, state.step) { viewModel.setSignedIn(signedIn) }

    // Route one-shot events: haptics + the finish hand-off. Collected once for the flow's life.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                SignupEvent.SelectionHaptic -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                SignupEvent.SuccessHaptic -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                SignupEvent.Finished -> onFinished()
            }
        }
    }

    // System back per step: Role, Auth and Profile have an in-flow back target; on the others we
    // don't intercept (Welcome/Done have nowhere to go; Notif is past the point of return), so the
    // OS/gate handles it. `canGoBack` covers the signed-in case where every earlier step is
    // retired — swallowing the gesture to do nothing is worse than letting the OS have it.
    //
    // `.Auth` was left out on the theory that back there "lands on a screen the user can't
    // meaningfully return to mid-auth". It doesn't: in signup mode it lands on `.Role`, a purely
    // local choice with no auth state attached, and in login mode on `.Welcome`. Unhandled, the
    // press fell through to the Activity — so the only way out of a role the user had just picked
    // was to quit the app.
    BackHandler(
        enabled = (
            state.step == SignupStep.Role ||
                state.step == SignupStep.Auth ||
                state.step == SignupStep.Profile
            ) && state.canGoBack,
    ) {
        viewModel.back()
    }

    // The flow has no Scaffold, and MainActivity is edge-to-edge, so the bottom inset is this
    // container's job: every step bottoms out at `space.xxl` (32dp), which a 48dp 3-button nav
    // bar overlapped — including "Start exploring →", the terminal action. Applied AFTER the
    // background so the bar still sits over `colors.bg` rather than a bare window.
    Box(modifier = modifier.fillMaxSize().background(AppTheme.colors.bg).navigationBarsPadding()) {
        // Built here rather than inside `transitionSpec`, which is not a composable scope.
        // motionTween owns the reduce-motion branch, so the step swap is instant for a user who
        // has asked the system to stop animating — a raw `tween` animated regardless.
        val fade = motionTween<Float>(AppTheme.motion.tabSwitch)
        AnimatedContent(
            targetState = state.step,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            label = "signupStep",
        ) { step ->
            when (step) {
                SignupStep.Welcome -> WelcomeScreen(
                    termsAccepted = state.termsAccepted,
                    onTermsToggle = viewModel::setTerms,
                    onGetStarted = viewModel::startSignup,
                    onLogin = viewModel::startLogin,
                )
                SignupStep.Role -> {
                    if (state.communityAgreed) {
                        RoleScreen(
                            onPick = viewModel::pickRole,
                            onAdvance = viewModel::advance,
                        )
                    } else {
                        CommunityCommitmentScreen(
                            onAgree = viewModel::agreeCommunity,
                            onBack = viewModel::back,
                        )
                    }
                }
                SignupStep.Auth -> SignupAuthScreen(
                    mode = state.mode,
                    authNotice = state.authNotice,
                    reduceMotion = reduceMotion,
                )
                SignupStep.Profile -> ProfileScreen(
                    state = state,
                    onHandleChange = viewModel::setHandle,
                    onNameChange = viewModel::setName,
                    onCityChange = viewModel::setCity,
                    onBack = viewModel::back,
                    onContinue = viewModel::saveProfile,
                )
                SignupStep.Notif -> NotifPermissionScreen(
                    progress = progressIndex(SignupStep.Notif, state.mode),
                    onAdvance = viewModel::advance,
                )
                SignupStep.Done -> DoneScreen(
                    firstName = state.firstName,
                    city = state.city,
                    onStartExploring = viewModel::finish,
                )
            }
        }

        // Top hydration-error strip (iOS `SignupFlowView.hydrationErrorBanner`) — a returning
        // artist whose role never hydrates would otherwise land in the wrong tabs with no signal.
        if (profileHydrationError != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(AppTheme.colors.bgCard)
                    .statusBarsPadding()
                    .padding(horizontal = AppTheme.dimens.space.xl, vertical = AppTheme.dimens.space.sm)
                    .semantics { testTag = "signup.hydrate.errorBanner" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(profileHydrationError, style = AppTheme.type.footnote, color = AppTheme.colors.ink3, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(AppTheme.dimens.space.sm))
                Text(
                    "Retry",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = AppTheme.colors.accentInk,
                    modifier = Modifier
                        .clickable { onRetryHydration() }
                        .semantics { testTag = "signup.hydrate.retry"; contentDescription = "Retry loading your profile" },
                )
            }
        }
    }
}
