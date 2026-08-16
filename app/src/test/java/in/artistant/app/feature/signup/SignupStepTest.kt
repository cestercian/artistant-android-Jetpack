package `in`.artistant.app.feature.signup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure step machine (the testable half of the iOS `OnboardingStore`). Covers the two flow
 * orders + advance/back clamping + the progress mapping — no coroutine/StateFlow needed.
 */
class SignupStepTest {

    @Test
    fun `signup order is welcome role auth profile notif done`() {
        assertEquals(
            listOf(
                SignupStep.Welcome, SignupStep.Role, SignupStep.Auth,
                SignupStep.Profile, SignupStep.Notif, SignupStep.Done,
            ),
            stepOrder(SignupMode.Signup),
        )
    }

    @Test
    fun `login order skips role and profile`() {
        assertEquals(
            listOf(SignupStep.Welcome, SignupStep.Auth, SignupStep.Notif, SignupStep.Done),
            stepOrder(SignupMode.Login),
        )
    }

    @Test
    fun `advance walks the signup order and clamps at done`() {
        assertEquals(SignupStep.Role, nextStep(SignupStep.Welcome, SignupMode.Signup))
        assertEquals(SignupStep.Auth, nextStep(SignupStep.Role, SignupMode.Signup))
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Auth, SignupMode.Signup))
        assertEquals(SignupStep.Notif, nextStep(SignupStep.Profile, SignupMode.Signup))
        assertEquals(SignupStep.Done, nextStep(SignupStep.Notif, SignupMode.Signup))
        // Clamp: advancing past the end stays put.
        assertEquals(SignupStep.Done, nextStep(SignupStep.Done, SignupMode.Signup))
    }

    @Test
    fun `advance in login mode skips role and profile`() {
        // Auth advances straight to Notif in login (no Profile in between).
        assertEquals(SignupStep.Notif, nextStep(SignupStep.Auth, SignupMode.Login))
    }

    @Test
    fun `back walks the order and clamps at welcome`() {
        assertEquals(SignupStep.Auth, prevStep(SignupStep.Profile, SignupMode.Signup))
        assertEquals(SignupStep.Role, prevStep(SignupStep.Auth, SignupMode.Signup))
        assertEquals(SignupStep.Welcome, prevStep(SignupStep.Role, SignupMode.Signup))
        // Clamp at the start.
        assertEquals(SignupStep.Welcome, prevStep(SignupStep.Welcome, SignupMode.Signup))
    }

    @Test
    fun `a live session retires auth in both directions`() {
        // `.Auth` has no forward control of its own — it only moves when a sign-in completes —
        // so a signed-in user must never be walked onto it. Role's Continue jumps the gap...
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Role, SignupMode.Signup, signedIn = true))
        // ...and the profile step's back chevron lands on Role instead of stranding them there.
        assertEquals(SignupStep.Role, prevStep(SignupStep.Profile, SignupMode.Signup, signedIn = true))
    }

    @Test
    fun `a live session retires welcome, so back clamps at role`() {
        // Welcome's "Sign in" switches to the LOGIN order, which skips the profile step a
        // signed-in-but-incomplete user still owes — a one-way trip to a stuck Done screen.
        assertEquals(SignupStep.Role, prevStep(SignupStep.Role, SignupMode.Signup, signedIn = true))
        assertEquals(SignupStep.Welcome, prevStep(SignupStep.Role, SignupMode.Signup)) // unchanged signed out
    }

    @Test
    fun `standing on auth with a session resolves forward rather than sticking`() {
        // The profile-save session guard can drop a user onto `.Auth`; both orders must have a
        // step to move them to from there.
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Auth, SignupMode.Signup, signedIn = true))
        assertEquals(SignupStep.Notif, nextStep(SignupStep.Auth, SignupMode.Login, signedIn = true))
    }

    @Test
    fun `a step not in the mode order stays put on advance`() {
        // Profile isn't in the login order — advancing from it is a no-op (defensive; the flow
        // never puts a login-mode user on Profile, but the machine must not throw/NPE).
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Profile, SignupMode.Login))
    }

    @Test
    fun `progress hides on welcome and done, shows a 5-bar in signup and 2-bar in login`() {
        assertNull(progressIndex(SignupStep.Welcome, SignupMode.Signup))
        assertNull(progressIndex(SignupStep.Done, SignupMode.Signup))
        assertEquals(ProgressBar(0, 5), progressIndex(SignupStep.Role, SignupMode.Signup))
        assertEquals(ProgressBar(1, 5), progressIndex(SignupStep.Auth, SignupMode.Signup))
        assertEquals(ProgressBar(3, 5), progressIndex(SignupStep.Profile, SignupMode.Signup))
        assertEquals(ProgressBar(0, 2), progressIndex(SignupStep.Auth, SignupMode.Login))
        assertEquals(ProgressBar(1, 2), progressIndex(SignupStep.Notif, SignupMode.Login))
    }
}
