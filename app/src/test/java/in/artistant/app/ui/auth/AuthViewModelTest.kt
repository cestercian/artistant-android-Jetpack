package `in`.artistant.app.ui.auth

import android.content.Context
import `in`.artistant.app.platform.auth.AuthGateway
import `in`.artistant.app.platform.auth.EmailAuthOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The decisions [AuthViewModel] makes on its own, all three of them found by review.
 *
 * - Back out of the code screen has to DROP the attempt, or the next number's first send is
 *   counted as a resend.
 * - Screen 28's one button has to try sign-IN before sign-UP, or an existing account can never
 *   be opened with its own password — which is the opposite of what the banner above it says.
 * - The LOGIN entrance has to send with `createUser = false`, or a door that collects no
 *   consent creates accounts.
 *
 * All of it runs against [FakeAuthGateway]; the real [in.artistant.app.platform.auth.SessionManager]
 * wants a SupabaseClient, an Android Context, PostHog, Sentry, DataStore, FCM and the upload
 * queue, none of which a JVM test can hold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    /** A recording stand-in: every call is remembered, every outcome is settable. */
    private class FakeAuthGateway : AuthGateway {
        private val _deepLinkError = MutableStateFlow<String?>(null)
        override val deepLinkError: StateFlow<String?> = _deepLinkError.asStateFlow()
        override fun consumeDeepLinkError() { _deepLinkError.value = null }

        private val _signInGeneration = MutableStateFlow(0)
        override val signInGeneration: StateFlow<Int> = _signInGeneration.asStateFlow()

        // ── one-time code ────────────────────────────────────────────────────
        /** Every send, as (destination, createUser). */
        val otpSends = mutableListOf<Pair<String, Boolean>>()
        var otpFailure: Throwable? = null

        override suspend fun sendPhoneOtp(phoneE164: String, createUser: Boolean) {
            otpSends += phoneE164 to createUser
            otpFailure?.let { throw it }
        }

        override suspend fun sendEmailOtp(email: String, createUser: Boolean) {
            otpSends += email to createUser
            otpFailure?.let { throw it }
        }

        override suspend fun verifyPhoneOtp(phoneE164: String, token: String) = Unit
        override suspend fun verifyEmailOtp(email: String, token: String) = Unit

        override suspend fun signInWithGoogle(activityContext: Context) = Unit
        override suspend fun signInWithApple() = Unit

        // ── email + password ─────────────────────────────────────────────────
        val signIns = mutableListOf<Pair<String, String>>()
        val signUps = mutableListOf<Triple<String, String, String?>>()

        /** Null means the account exists and the password opened it. */
        var signInFailure: Throwable? = null
        var signUpOutcome: EmailAuthOutcome = EmailAuthOutcome.SignedIn
        var signUpFailure: Throwable? = null

        override suspend fun signInWithEmail(email: String, password: String) {
            signIns += email to password
            signInFailure?.let { throw it }
        }

        override suspend fun signUpWithEmail(
            email: String,
            password: String,
            fullName: String?,
        ): EmailAuthOutcome {
            signUps += Triple(email, password, fullName)
            signUpFailure?.let { throw it }
            return signUpOutcome
        }

        override suspend fun sendPasswordReset(email: String) = Unit
    }

    /** What GoTrue says when nothing matched the credentials — for both possible reasons. */
    private fun invalidCredentials() = IllegalStateException("Invalid login credentials")

    /** What GoTrue says to a `create_user = false` send for an address it has never seen. */
    private fun otpDisabled() = IllegalStateException("Signups not allowed for otp")

    private val number = "9845012345"
    private val e164 = "+919845012345"

    // ── Back out of the code screen (finding 2) ───────────────────────────────

    @Test
    fun `the send count is zero after back, so the next first send is not a resend`() = runTest {
        val gateway = FakeAuthGateway()
        val vm = AuthViewModel(gateway)

        vm.setPhone(number)
        vm.sendCode()
        advanceUntilIdle()
        vm.sendCode()
        advanceUntilIdle()

        // Two sends in: the code screen is now offering the email escape hatch.
        assertEquals(2, vm.state.value.sendCount)
        assertTrue(vm.state.value.offersEmailEscape)

        // Back. This is what SignupFlow's back handler and the header's "Change number" both
        // call; before it existed, only SignupStep moved and every one of these survived.
        vm.clearOtp()

        assertEquals(0, vm.state.value.sendCount)
        assertFalse(vm.state.value.offersEmailEscape)
        assertEquals("", vm.state.value.codeDestination)
        assertEquals("", vm.state.value.code)
        assertEquals(0, vm.state.value.resendSeconds)
    }

    @Test
    fun `a code typed for the old number does not survive the way back`() = runTest {
        val gateway = FakeAuthGateway()
        val vm = AuthViewModel(gateway)

        vm.setPhone(number)
        vm.sendCode()
        advanceUntilIdle()
        vm.setCode("123456")
        assertTrue(vm.state.value.canVerify)

        vm.clearOtp()
        advanceUntilIdle()

        assertEquals("", vm.state.value.code)
        assertFalse(vm.state.value.canVerify)
    }

    // ── Screen 28's one button (finding 3) ────────────────────────────────────

    @Test
    fun `an existing account is opened by its own password, and nothing is created`() = runTest {
        val gateway = FakeAuthGateway() // sign-in succeeds
        val vm = AuthViewModel(gateway)

        vm.submitEmailAuth("rhea@studio.in", "correct-horse", "Rhea Menon")
        advanceUntilIdle()

        assertEquals(listOf("rhea@studio.in" to "correct-horse"), gateway.signIns)
        assertTrue("a matching password must never reach sign-up", gateway.signUps.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.confirmationRequired)
        assertFalse(vm.state.value.isAuthenticating)
    }

    @Test
    fun `a six-character password still gets its account back`() = runTest {
        // The screen draws an eight-character rule for NEW accounts. An account made before
        // that rule existed may hold six, and the rule must not lock its owner out.
        val gateway = FakeAuthGateway()
        val vm = AuthViewModel(gateway)

        vm.submitEmailAuth("rhea@studio.in", "abc123", null)
        advanceUntilIdle()

        assertEquals(listOf("rhea@studio.in" to "abc123"), gateway.signIns)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `an address nobody has used creates the account`() = runTest {
        val gateway = FakeAuthGateway().apply {
            signInFailure = invalidCredentials()
            signUpOutcome = EmailAuthOutcome.SignedIn
        }
        val vm = AuthViewModel(gateway)

        vm.submitEmailAuth("new@studio.in", "eight-plus-chars", "New Act")
        advanceUntilIdle()

        assertEquals(1, gateway.signIns.size)
        assertEquals(listOf(Triple("new@studio.in", "eight-plus-chars", "New Act")), gateway.signUps)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `a wrong password is called a wrong password, not an inbox to go and check`() = runTest {
        // GoTrue answers "invalid credentials" for both "no such user" and "wrong password", so
        // the sign-up fallback runs — and comes back with an obfuscated already-registered user.
        // Reported as ConfirmationRequired, that sent someone who mistyped their own password
        // off to look for a mail nothing had sent.
        val gateway = FakeAuthGateway().apply {
            signInFailure = invalidCredentials()
            signUpOutcome = EmailAuthOutcome.AlreadyRegistered
        }
        val vm = AuthViewModel(gateway)

        vm.submitEmailAuth("rhea@studio.in", "not-my-password", null)
        advanceUntilIdle()

        assertEquals(WRONG_PASSWORD, vm.state.value.error)
        assertFalse(vm.state.value.confirmationRequired)
    }

    @Test
    fun `a project with confirmations on says check your inbox, and only then`() = runTest {
        val gateway = FakeAuthGateway().apply {
            signInFailure = invalidCredentials()
            signUpOutcome = EmailAuthOutcome.ConfirmationRequired
        }
        val vm = AuthViewModel(gateway)

        vm.submitEmailAuth("new@studio.in", "eight-plus-chars", null)
        advanceUntilIdle()

        assertTrue(vm.state.value.confirmationRequired)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `a failure that is not about the credentials never creates a second account`() = runTest {
        // An unconfirmed address, a rate limit, a server having a bad day: falling through to
        // sign-up would answer a temporary problem by making another account.
        val gateway = FakeAuthGateway().apply {
            signInFailure = IllegalStateException("Email not confirmed")
        }
        val vm = AuthViewModel(gateway)

        vm.submitEmailAuth("rhea@studio.in", "eight-plus-chars", null)
        advanceUntilIdle()

        assertTrue(gateway.signUps.isEmpty())
        assertEquals("Email not confirmed", vm.state.value.error)
    }

    @Test
    fun `a short password that matches nothing is refused before it can create anything`() =
        runTest {
            val gateway = FakeAuthGateway().apply { signInFailure = invalidCredentials() }
            val vm = AuthViewModel(gateway)

            vm.submitEmailAuth("new@studio.in", "abc123", null)
            advanceUntilIdle()

            // Sign-in was still attempted — that is how an old six-character account gets in.
            assertEquals(1, gateway.signIns.size)
            // But nothing new is created below the rule the screen draws.
            assertTrue(gateway.signUps.isEmpty())
            assertTrue(vm.state.value.error!!.contains("8 characters"))
        }

    // ── The login entrance (finding 4) ────────────────────────────────────────

    @Test
    fun `login sends the code without permission to create an account`() = runTest {
        val gateway = FakeAuthGateway()
        val vm = AuthViewModel(gateway)

        vm.setPhone(number)
        vm.sendCode(createUser = false)
        advanceUntilIdle()

        assertEquals(listOf(e164 to false), gateway.otpSends)
    }

    @Test
    fun `signup sends the code with it, because that door collects the tick`() = runTest {
        val gateway = FakeAuthGateway()
        val vm = AuthViewModel(gateway)

        vm.setPhone(number)
        vm.sendCode(createUser = true)
        advanceUntilIdle()

        assertEquals(listOf(e164 to true), gateway.otpSends)
    }

    @Test
    fun `an unknown number on the login path is offered a signup, not shown an error`() = runTest {
        val gateway = FakeAuthGateway().apply { otpFailure = otpDisabled() }
        val vm = AuthViewModel(gateway)

        vm.setPhone(number)
        vm.sendCode(createUser = false)
        advanceUntilIdle()

        assertEquals(OtpChannel.Sms, vm.state.value.noAccountFor)
        assertNull("nothing failed — the address simply isn't here", vm.state.value.error)
        assertEquals(0, vm.state.value.sendCount)
        assertFalse(vm.state.value.isSendingCode)
    }

    @Test
    fun `the same refusal in signup mode keeps its own meaning`() = runTest {
        // Identical GoTrue string, opposite fact: with createUser = true it really does mean
        // the project has stopped taking new accounts.
        val gateway = FakeAuthGateway().apply { otpFailure = otpDisabled() }
        val vm = AuthViewModel(gateway)

        vm.setPhone(number)
        vm.sendCode(createUser = true)
        advanceUntilIdle()

        assertNull(vm.state.value.noAccountFor)
        assertEquals("New accounts are paused right now.", vm.state.value.error)
    }

    @Test
    fun `editing the number withdraws the offer it was made about`() = runTest {
        val gateway = FakeAuthGateway().apply { otpFailure = otpDisabled() }
        val vm = AuthViewModel(gateway)

        vm.setPhone(number)
        vm.sendCode(createUser = false)
        advanceUntilIdle()
        assertEquals(OtpChannel.Sms, vm.state.value.noAccountFor)

        vm.setPhone("9845012399")

        assertNull(vm.state.value.noAccountFor)
    }
}
