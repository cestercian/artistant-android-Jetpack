package `in`.artistant.app.feature.signup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The pure step machine (the testable half of the iOS `OnboardingStore`). Covers the two flow
 * orders + advance/back clamping + the progress mapping — no coroutine/StateFlow needed.
 *
 * The Sep-2026 redesign inserted `.Code` (design screen 119) between `.Auth` and whatever
 * follows it, so most of what is asserted here is the shape of that insertion: the code screen
 * is on the walk for the phone path and retired by a live session for every other one.
 */
class SignupStepTest {

    @Test
    fun `signup order is welcome role auth code profile notif done`() {
        assertEquals(
            listOf(
                SignupStep.Welcome, SignupStep.Role, SignupStep.Auth, SignupStep.Code,
                SignupStep.Profile, SignupStep.Notif, SignupStep.Done,
            ),
            stepOrder(SignupMode.Signup),
        )
    }

    @Test
    fun `login order skips role and profile but keeps the code step`() {
        assertEquals(
            listOf(
                SignupStep.Welcome, SignupStep.Auth, SignupStep.Code,
                SignupStep.Notif, SignupStep.Done,
            ),
            stepOrder(SignupMode.Login),
        )
    }

    @Test
    fun `advance walks the signup order and clamps at done`() {
        assertEquals(SignupStep.Role, nextStep(SignupStep.Welcome, SignupMode.Signup))
        assertEquals(SignupStep.Auth, nextStep(SignupStep.Role, SignupMode.Signup))
        assertEquals(SignupStep.Code, nextStep(SignupStep.Auth, SignupMode.Signup))
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Code, SignupMode.Signup))
        assertEquals(SignupStep.Notif, nextStep(SignupStep.Profile, SignupMode.Signup))
        assertEquals(SignupStep.Done, nextStep(SignupStep.Notif, SignupMode.Signup))
        // Clamp: advancing past the end stays put.
        assertEquals(SignupStep.Done, nextStep(SignupStep.Done, SignupMode.Signup))
    }

    @Test
    fun `advance in login mode skips role and profile`() {
        assertEquals(SignupStep.Code, nextStep(SignupStep.Auth, SignupMode.Login))
        assertEquals(SignupStep.Notif, nextStep(SignupStep.Code, SignupMode.Login))
    }

    @Test
    fun `back walks the order and clamps at welcome`() {
        assertEquals(SignupStep.Code, prevStep(SignupStep.Profile, SignupMode.Signup))
        // "Change number" on the code screen is this transition — back to the field the code
        // was sent for, not out of the sign-in entirely.
        assertEquals(SignupStep.Auth, prevStep(SignupStep.Code, SignupMode.Signup))
        assertEquals(SignupStep.Role, prevStep(SignupStep.Auth, SignupMode.Signup))
        assertEquals(SignupStep.Welcome, prevStep(SignupStep.Role, SignupMode.Signup))
        // Clamp at the start.
        assertEquals(SignupStep.Welcome, prevStep(SignupStep.Welcome, SignupMode.Signup))
    }

    @Test
    fun `a live session retires auth and code in both directions`() {
        // Neither has a forward control of its own once a session exists — `.Auth` only moves
        // when a sign-in completes and `.Code` is the second half of that same act — so a
        // signed-in user must never be walked onto either. Role's Continue jumps both...
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Role, SignupMode.Signup, signedIn = true))
        // ...and the profile step's back chevron clears them too, landing on Role.
        assertEquals(SignupStep.Role, prevStep(SignupStep.Profile, SignupMode.Signup, signedIn = true))
    }

    @Test
    fun `a live session retires welcome, so back clamps at role`() {
        // Welcome's "I already have an account" switches to the LOGIN order, which skips the
        // profile step a signed-in-but-incomplete user still owes — a one-way trip to a stuck
        // Done screen.
        assertEquals(SignupStep.Role, prevStep(SignupStep.Role, SignupMode.Signup, signedIn = true))
        assertEquals(SignupStep.Welcome, prevStep(SignupStep.Role, SignupMode.Signup)) // unchanged signed out
    }

    @Test
    fun `standing on auth or code with a session resolves forward rather than sticking`() {
        // The profile-save session guard can drop a user onto `.Auth`, and a verified code
        // leaves them standing on `.Code`; both orders must have a step to move them to.
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Auth, SignupMode.Signup, signedIn = true))
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Code, SignupMode.Signup, signedIn = true))
        assertEquals(SignupStep.Notif, nextStep(SignupStep.Auth, SignupMode.Login, signedIn = true))
        assertEquals(SignupStep.Notif, nextStep(SignupStep.Code, SignupMode.Login, signedIn = true))
    }

    @Test
    fun `a step not in the mode order stays put on advance`() {
        // Profile isn't in the login order — advancing from it is a no-op (defensive; the flow
        // never puts a login-mode user on Profile, but the machine must not throw/NPE).
        assertEquals(SignupStep.Profile, nextStep(SignupStep.Profile, SignupMode.Login))
        assertEquals(SignupStep.Profile, prevStep(SignupStep.Profile, SignupMode.Login))
    }

    @Test
    fun `only the handle step draws a progress strip, and it reads 04 of 06`() {
        // The extracted design puts the strip on screens 29 and 90 and nowhere else: 11, 12,
        // 27, 28 and 119 all render an empty header middle, and 13 and 30 have no header.
        assertEquals(ProgressBar(3, 6), progressIndex(SignupStep.Profile, SignupMode.Signup))
        assertEquals("04 / 06", progressIndex(SignupStep.Profile, SignupMode.Signup)?.label)
        SignupStep.entries.filter { it != SignupStep.Profile }.forEach { step ->
            assertNull("$step should draw no strip", progressIndex(step, SignupMode.Signup))
        }
    }

    @Test
    fun `login collects none of the six numbered steps, so it carries no strip`() {
        SignupStep.entries.forEach { step ->
            assertNull("$step should draw no strip in login", progressIndex(step, SignupMode.Login))
        }
    }

    @Test
    fun `the strip label is one-based and zero-padded`() {
        assertEquals("01 / 06", ProgressBar(0, 6).label)
        assertEquals("06 / 06", ProgressBar(5, 6).label)
    }
}
