package `in`.artistant.app.feature.signup

/**
 * The signup step machine (the pure half of the iOS `OnboardingStore` port). Ordering is
 * decoupled from the enum so the two flows can differ:
 *   signup: welcome → role → auth → code → profile → notif → done
 *   login:  welcome → auth → code → notif → done
 *
 * iOS also carries the "am I signed in" bit inside the same store; on Android that bit is OWNED
 * by SessionManager/RootViewModel (the gate) and only passed down here, as the `signedIn`
 * argument that retires the pre-auth steps — this machine still holds no session state of its
 * own. Extracted as a top-level enum + pure functions so [SignupViewModel]'s transitions are
 * unit-testable without a coroutine/StateFlow.
 */
enum class SignupStep {
    Welcome,
    Role,
    Auth,

    /**
     * Screen 119 — the six-box one-time code.
     *
     * Its own step rather than a mode of [Auth] because it is a different screen with a
     * different back target: back from the code screen returns to the number you typed it
     * for, and back from the auth screen leaves the sign-in entirely. It is also SKIPPED by
     * three of the four sign-in paths (Apple, Google, email+password all land a session
     * directly), which is exactly what [retiredSteps] expresses — a live session retires it
     * the same way it retires [Auth].
     */
    Code,
    Profile,
    Notif,
    Done,
}

enum class SignupMode { Signup, Login }

/** The step order for a given mode. Login skips role + profile (a returning user already has
 *  both server-side — hydrated by RootViewModel, not re-collected here). */
fun stepOrder(mode: SignupMode): List<SignupStep> = when (mode) {
    SignupMode.Signup -> listOf(
        SignupStep.Welcome, SignupStep.Role, SignupStep.Auth, SignupStep.Code,
        SignupStep.Profile, SignupStep.Notif, SignupStep.Done,
    )
    SignupMode.Login -> listOf(
        SignupStep.Welcome, SignupStep.Auth, SignupStep.Code, SignupStep.Notif, SignupStep.Done,
    )
}

/**
 * The steps a live session retires.
 *
 * `.Auth` has no forward control of its own — it only ever moves when a sign-in completes —
 * and `.Code` is the second half of that same act, so once a session exists neither has
 * anything left to do. `.Welcome`'s two actions are "Get started" (restarts the walk) and
 * "I already have an account" (switches to the LOGIN order, which skips the profile step a
 * signed-in-but-incomplete user still owes). All three are dead ends for a user who is
 * already signed in, so every transition steps over them rather than parking anyone there.
 */
private fun retiredSteps(signedIn: Boolean): Set<SignupStep> =
    if (signedIn) setOf(SignupStep.Welcome, SignupStep.Auth, SignupStep.Code) else emptySet()

/**
 * Next step in the mode's order, or the same step if already at the end (iOS `advance`).
 * With [signedIn] the retired pre-auth steps are skipped, so role → profile jumps straight over
 * `.Auth`/`.Code` and a bounce ONTO either resolves forward to the step that follows them.
 */
fun nextStep(step: SignupStep, mode: SignupMode, signedIn: Boolean = false): SignupStep {
    val order = stepOrder(mode)
    val i = order.indexOf(step)
    if (i < 0) return step
    val retired = retiredSteps(signedIn)
    return order.drop(i + 1).firstOrNull { it !in retired } ?: step
}

/**
 * Previous step in the mode's order, or the same step if there is no earlier one the user can
 * still act on (iOS `back`). With [signedIn] the retired pre-auth steps are skipped, so the
 * profile step's back chevron lands on `.Role` instead of stranding the user on `.Code`.
 */
fun prevStep(step: SignupStep, mode: SignupMode, signedIn: Boolean = false): SignupStep {
    val order = stepOrder(mode)
    val i = order.indexOf(step)
    if (i < 0) return step
    val retired = retiredSteps(signedIn)
    return order.take(i).lastOrNull { it !in retired } ?: step
}

/**
 * The step strip's (0-based index, total), or null on a step that draws none.
 *
 * **Only the handle step draws one**, and it draws "04 / 06" (screens 29 and 90). That is not
 * an omission in the extracted design: 11, 12, 27, 28 and 119 all render a header with an
 * empty middle slot, and 13 and 30 have no header at all. The strip exists on the one screen
 * where the user is filling in a form and needs to know how much form is left; everywhere else
 * the screen's own copy says where they are. REDESIGN_2026-09 §5 says match the screen rather
 * than improve it, so this returns null for the other six.
 *
 * The six counted steps are the six DECISIONS a new account makes — pledge, role, sign in,
 * handle and city, notifications, and the score primer — which is what puts the handle step at
 * four. Login collects none of them, so it carries no strip at all.
 */
fun progressIndex(step: SignupStep, mode: SignupMode): ProgressBar? = when (mode) {
    SignupMode.Signup -> when (step) {
        SignupStep.Profile -> ProgressBar(HANDLE_STEP_INDEX, SIGNUP_STEP_COUNT)
        else -> null
    }
    SignupMode.Login -> null
}

/** "04 / 06" — 0-based, so the fourth step is index three. */
private const val HANDLE_STEP_INDEX = 3
private const val SIGNUP_STEP_COUNT = 6

/** How many segments a step's progress bar shows and which index is "current". */
data class ProgressBar(val index: Int, val total: Int) {
    /** The design's own label for the strip — "04 / 06". */
    val label: String get() = "%02d / %02d".format(index + 1, total)
}
