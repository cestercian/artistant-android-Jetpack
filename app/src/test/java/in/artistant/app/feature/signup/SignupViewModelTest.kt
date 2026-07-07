package `in`.artistant.app.feature.signup

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.data.repository.FakeUsersRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.platform.observability.NoopAnalytics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * The stateful half of the signup port: synchronous handle-status mapping, the terms gate, and
 * the three profile-save outcomes (success advances, raced handle bounces to the field, lost
 * session bounces to auth). The debounced RPC check itself is exercised via advanceUntilIdle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm(users: UsersRepository = FakeUsersRepository()) =
        SignupViewModel(users, NoopAnalytics())

    /** Minimal test double: reports every valid-format handle available, and lets the caller
     *  pick what upsert throws — enough to model the "available-then-raced/lost-session" paths
     *  the FakeUsersRepository can't (it derives availability from its taken set). */
    private class UpsertStub(private val onUpsert: () -> Unit) : UsersRepository {
        override suspend fun handleIsAvailable(handle: String) = HandleAvailability.Available
        override suspend fun fetchSelfProfile() = null
        override suspend fun upsertSelfProfile(
            handle: String, fullName: String, city: String,
            role: AppRole, termsAccepted: Boolean,
        ) = onUpsert()
    }

    @Test
    fun `handle status maps empty invalid checking synchronously`() {
        val vm = vm()
        assertEquals(HandleStatus.Empty, vm.state.value.handleStatus)
        vm.setHandle("ab") // too short → invalid format
        assertEquals(HandleStatus.Invalid, vm.state.value.handleStatus)
        vm.setHandle("valid_handle") // valid format → checking (async upgrades later)
        assertEquals(HandleStatus.Checking, vm.state.value.handleStatus)
    }

    @Test
    fun `handle input is live-cleaned to lowercase alnum underscore`() {
        val vm = vm()
        vm.setHandle("Yash Kumar!") // space + capitals + punctuation dropped
        assertEquals("yashkumar", vm.state.value.handle)
    }

    @Test
    fun `debounced check flips a free handle to available`() = runTest(dispatcher) {
        val vm = vm(FakeUsersRepository()) // fake reports valid-format unknown handles available
        vm.setHandle("brand_new_handle")
        assertEquals(HandleStatus.Checking, vm.state.value.handleStatus)
        advanceUntilIdle() // run the 350ms debounce + RPC
        assertEquals(HandleStatus.Available, vm.state.value.handleStatus)
    }

    @Test
    fun `debounced check flips a taken handle to taken`() = runTest(dispatcher) {
        val vm = vm(FakeUsersRepository(taken = setOf("taken_one")))
        vm.setHandle("taken_one")
        advanceUntilIdle()
        assertEquals(HandleStatus.Taken, vm.state.value.handleStatus)
    }

    @Test
    fun `terms gate toggles independently of the flow`() {
        val vm = vm()
        assertFalse(vm.state.value.termsAccepted)
        vm.setTerms(true)
        assertTrue(vm.state.value.termsAccepted)
    }

    @Test
    fun `startSignup and startLogin seed the right mode and step`() {
        val vm = vm()
        vm.startSignup()
        assertEquals(SignupMode.Signup, vm.state.value.mode)
        assertEquals(SignupStep.Role, vm.state.value.step)
        vm.startLogin()
        assertEquals(SignupMode.Login, vm.state.value.mode)
        assertEquals(SignupStep.Auth, vm.state.value.step)
    }

    @Test
    fun `profileValid needs an available handle plus name and city`() = runTest(dispatcher) {
        val vm = vm()
        vm.setName("Yash")
        vm.setCity("Bangalore")
        vm.setHandle("free_handle")
        advanceUntilIdle()
        assertTrue(vm.state.value.profileValid)
        vm.setCity("") // drop city → invalid
        assertFalse(vm.state.value.profileValid)
    }

    @Test
    fun `saveProfile success advances to notif`() = runTest(dispatcher) {
        val vm = vm()
        vm.resumeAt(SignupStep.Profile)
        vm.setName("Yash"); vm.setCity("Bangalore"); vm.setHandle("free_handle")
        advanceUntilIdle()
        vm.saveProfile()
        advanceUntilIdle()
        assertEquals(SignupStep.Notif, vm.state.value.step)
        assertNull(vm.state.value.saveError)
    }

    @Test
    fun `saveProfile with a raced handle bounces to the field as taken`() = runTest(dispatcher) {
        // Handle passes the availability check but the upsert 23505s (raced onto another device).
        val vm = vm(UpsertStub { throw AppError.UniqueViolation })
        vm.resumeAt(SignupStep.Profile)
        vm.setName("Yash"); vm.setCity("Bangalore"); vm.setHandle("raced_handle")
        advanceUntilIdle()
        vm.saveProfile()
        advanceUntilIdle()
        assertEquals(SignupStep.Profile, vm.state.value.step) // stayed put
        assertEquals(HandleStatus.Taken, vm.state.value.handleStatus)
        assertTrue(vm.state.value.saveError!!.contains("just taken"))
    }

    @Test
    fun `saveProfile with no session bounces to auth with a notice`() = runTest(dispatcher) {
        val vm = vm(UpsertStub { throw AppError.NotFoundOrUnauthorized })
        vm.resumeAt(SignupStep.Profile)
        vm.setName("Yash"); vm.setCity("Bangalore"); vm.setHandle("some_handle")
        advanceUntilIdle()
        vm.saveProfile()
        advanceUntilIdle()
        assertEquals(SignupStep.Auth, vm.state.value.step)
        assertTrue(vm.state.value.authNotice!!.contains("sign in again"))
    }

    @Test
    fun `onAuthCompleted advances past auth and clears the notice`() {
        val vm = vm()
        vm.startLogin() // lands on Auth
        vm.onAuthCompleted()
        assertEquals(SignupStep.Notif, vm.state.value.step) // login: auth → notif
        assertNull(vm.state.value.authNotice)
    }
}
