package `in`.artistant.app.feature.signup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.motionTween
import `in`.artistant.app.ui.auth.AuthViewModel

/**
 * The signup container (iOS `SignupFlowView`): switches on `step` with a crossfade and routes
 * one-shot events (haptics + finish). The gate presents this at the right entry step — welcome
 * for a not-signed-in user, profile for a signed-in-but-incomplete one — via
 * [startStep]/[startMode]/[resume].
 *
 * **The hydration banner moved.** It used to be a strip this container drew over whatever step
 * was on screen. Design screen 71 puts it INSIDE the role picker — the screen a failed
 * hydration actually lands on — with a Retry pill beside it, so the failure and the question it
 * caused are one thing rather than two stacked ones. The banner is passed down to [RoleScreen]
 * now, and nothing floats over the flow.
 *
 * @param profileHydrationError a login-hydrate failure the gate surfaces on the role step.
 * @param onRetryHydration re-runs the failed profile fetch (gate-owned, since it drives routing).
 * @param onFinished fires when the user taps the last step's CTA — the gate re-routes into tabs.
 * @param signedIn whether a live session exists — true on the gate's signed-in-but-incomplete
 *   tier, false on the not-signed-in one. The flow can't read the session itself (the repository
 *   seam), and the gate value alone can't tell it either: `RootGate.Onboarding` is a `data
 *   object`, so a re-route to the same tier is conflated and re-fires nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupFlow(
    startStep: SignupStep,
    startMode: SignupMode,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    profileHydrationError: String? = null,
    onRetryHydration: () -> Unit = {},
    signedIn: Boolean = false,
    viewModel: SignupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current
    var legalDoc by remember { mutableStateOf<LegalDoc?>(null) }

    // The auth VM is Activity-scoped (these screens sit outside any NavHost), so the flow has
    // to reach it to drop an abandoned one-time-code attempt. `back()` alone only moved
    // SignupStep: the destination, the typed digits, the send count and the running cooldown
    // all survived, so the FIRST send for the next number was treated as a resend — offering
    // "use email instead" before a single message had had a chance to arrive.
    val auth: AuthViewModel = hiltViewModel()
    val leaveStep: () -> Unit = {
        if (state.step == SignupStep.Code) auth.clearOtp()
        viewModel.back()
    }

    // Seed the flow at the gate's entry step once. resumeAt is idempotent, so a recomposition or
    // a gate re-render (NotSignedIn → Onboarding) won't clobber the user's in-flow progress.
    LaunchedEffect(startStep, startMode) { viewModel.resumeAt(startStep, startMode) }

    // Report the gate's session bit into the flow (iOS RootView.handleAuthChange →
    // didCompleteAuth). Keyed on the STEP as well as the bit, so a landing on `.Auth`/`.Code`
    // while a session is live — the profile-save session guard, a back press that raced this —
    // is corrected the moment it happens. Runs after resumeAt above (declaration order), so the
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

    // System back, in priority order. The password form is a modal over the auth step, so back
    // closes it before anything else gets a say; otherwise the steps with an in-flow back target
    // take the gesture and the rest let the OS have it (Welcome and Done have nowhere to go,
    // Notif is past the point of return). `canGoBack` covers the signed-in case where every
    // earlier step is retired — swallowing the gesture to do nothing is worse than not taking it.
    BackHandler(enabled = state.emailSignUp) { viewModel.closeEmailSignUp() }
    BackHandler(
        enabled = !state.emailSignUp && state.canGoBack && state.step in BACKABLE_STEPS,
    ) {
        leaveStep()
    }

    Box(modifier = modifier.fillMaxSize().background(AppTheme.colors.surface)) {
        // Built here rather than inside `transitionSpec`, which is not a composable scope.
        // motionTween owns the reduce-motion branch, so the step swap is instant for a user who
        // has asked the system to stop animating — a raw `tween` animated regardless.
        val fade = motionTween<Float>(AppTheme.motion.tabSwitch)
        AnimatedContent(
            targetState = if (state.emailSignUp) EMAIL_SIGN_UP_KEY else state.step.name,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            label = "signupStep",
        ) { key ->
            when {
                key == EMAIL_SIGN_UP_KEY -> EmailSignUpScreen(onCancel = viewModel::closeEmailSignUp)

                key == SignupStep.Welcome.name -> WelcomeScreen(
                    termsAccepted = state.termsAccepted,
                    onTermsToggle = viewModel::setTerms,
                    onGetStarted = viewModel::startSignup,
                    onLogin = viewModel::startLogin,
                )

                key == SignupStep.Role.name ->
                    if (state.communityAgreed) {
                        RoleScreen(
                            selected = state.role,
                            onPick = viewModel::pickRole,
                            onAdvance = viewModel::advance,
                            onBack = if (state.canGoBack) viewModel::back else null,
                            hydrationError = profileHydrationError,
                            onRetryHydration = onRetryHydration,
                        )
                    } else {
                        CommunityCommitmentScreen(
                            onAgree = viewModel::agreeCommunity,
                            onBack = if (state.canGoBack) viewModel::back else null,
                        )
                    }

                key == SignupStep.Auth.name -> SignupAuthScreen(
                    mode = state.mode,
                    authNotice = state.authNotice,
                    onCodeSent = viewModel::goToCode,
                    onOpenEmailSignUp = viewModel::openEmailSignUp,
                    onStartSignup = viewModel::switchToSignup,
                    onBack = if (state.canGoBack) viewModel::back else null,
                    onOpenLegal = { legalDoc = it },
                )

                // EnterCodeScreen clears the attempt itself on both of these — it is the
                // screen that knows they are exits. `leaveStep` is here for the system back
                // gesture, which never reaches a callback at all.
                key == SignupStep.Code.name -> EnterCodeScreen(
                    onChangeNumber = viewModel::back,
                    onUseEmailInstead = viewModel::openEmailSignUp,
                )

                key == SignupStep.Profile.name -> ProfileScreen(
                    state = state,
                    onHandleChange = viewModel::setHandle,
                    onNameChange = viewModel::setName,
                    onCityChange = viewModel::setCity,
                    onBack = viewModel::back,
                    onContinue = viewModel::saveProfile,
                    hydrationError = profileHydrationError,
                    onRetryHydration = onRetryHydration,
                )

                key == SignupStep.Notif.name -> NotifPermissionScreen(onAdvance = viewModel::advance)

                else -> DoneScreen(
                    firstName = state.firstName,
                    city = state.city,
                    role = state.role,
                    onStartExploring = viewModel::finish,
                )
            }
        }
    }

    legalDoc?.let { doc ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { legalDoc = null },
            sheetState = sheetState,
            containerColor = AppTheme.colors.surface,
        ) {
            LegalScreen(doc = doc, onClose = { legalDoc = null })
        }
    }
}

/**
 * The steps whose back chevron has somewhere to go.
 *
 * `.Auth` is in the list on purpose. It was left out once on the theory that back there "lands
 * on a screen the user can't meaningfully return to mid-auth" — it doesn't: in signup mode it
 * lands on `.Role`, a purely local choice with no auth state attached, and in login mode on
 * `.Welcome`. Unhandled, the press fell through to the Activity, so the only way out of a role
 * the user had just picked was to quit the app.
 */
private val BACKABLE_STEPS = setOf(
    SignupStep.Role,
    SignupStep.Auth,
    SignupStep.Code,
    SignupStep.Profile,
)

/**
 * The [AnimatedContent] key for the password form.
 *
 * The crossfade is keyed on a String rather than on the step enum because the form is not a
 * step — it is a flag over `.Auth` — and keying on the step alone would swap the two screens
 * with no transition at all, since the target value would not have changed.
 */
private const val EMAIL_SIGN_UP_KEY = "emailSignUp"
