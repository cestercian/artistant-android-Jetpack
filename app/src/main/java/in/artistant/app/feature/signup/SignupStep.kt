package `in`.artistant.app.feature.signup

/**
 * The signup step machine (the pure half of the iOS `OnboardingStore` port). Ordering is
 * decoupled from the enum so the two flows can differ:
 *   signup: welcome → role → auth → profile → notif → done
 *   login:  welcome → auth → notif → done
 *
 * iOS also carries the "am I signed in" bit inside the same store; on Android that bit is owned
 * by SessionManager/RootViewModel (the gate), so this machine is *only* the screen ordering.
 * Extracted as a top-level enum + pure functions so [SignupViewModel]'s transitions are
 * unit-testable without a coroutine/StateFlow.
 */
enum class SignupStep { Welcome, Role, Auth, Profile, Notif, Done }

enum class SignupMode { Signup, Login }

/** The step order for a given mode. Login skips role + profile (a returning user already has
 *  both server-side — hydrated by RootViewModel, not re-collected here). */
fun stepOrder(mode: SignupMode): List<SignupStep> = when (mode) {
    SignupMode.Signup -> listOf(
        SignupStep.Welcome, SignupStep.Role, SignupStep.Auth,
        SignupStep.Profile, SignupStep.Notif, SignupStep.Done,
    )
    SignupMode.Login -> listOf(
        SignupStep.Welcome, SignupStep.Auth, SignupStep.Notif, SignupStep.Done,
    )
}

/** Next step in the mode's order, or the same step if already at the end (iOS `advance`). */
fun nextStep(step: SignupStep, mode: SignupMode): SignupStep {
    val order = stepOrder(mode)
    val i = order.indexOf(step)
    return if (i in 0 until order.lastIndex) order[i + 1] else step
}

/** Previous step in the mode's order, or the same step if already at the start (iOS `back`). */
fun prevStep(step: SignupStep, mode: SignupMode): SignupStep {
    val order = stepOrder(mode)
    val i = order.indexOf(step)
    return if (i > 0) order[i - 1] else step
}

/**
 * The 5-segment progress index (0-based) for a step, or null on Welcome/Done (which hide the
 * bar). Mirrors iOS `SignupProgressDots(stepIndex:total:)`: signup shows role=0, auth=1,
 * profile=…, and the tail steps; login collapses to a 2-segment bar. Kept pure for the same
 * reason as the transitions.
 */
fun progressIndex(step: SignupStep, mode: SignupMode): ProgressBar? = when (mode) {
    SignupMode.Signup -> when (step) {
        SignupStep.Role -> ProgressBar(0, 5)
        SignupStep.Auth -> ProgressBar(1, 5)
        SignupStep.Profile -> ProgressBar(3, 5) // iOS profile chrome lights step 4 (index 3)
        SignupStep.Notif -> ProgressBar(4, 5)
        SignupStep.Welcome, SignupStep.Done -> null
    }
    SignupMode.Login -> when (step) {
        SignupStep.Auth -> ProgressBar(0, 2)
        SignupStep.Notif -> ProgressBar(1, 2)
        else -> null
    }
}

/** How many segments a step's progress bar shows and which index is "current". */
data class ProgressBar(val index: Int, val total: Int)
