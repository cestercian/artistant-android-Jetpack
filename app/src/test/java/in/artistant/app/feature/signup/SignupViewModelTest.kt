package `in`.artistant.app.feature.signup

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.data.repository.FakeUsersRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.platform.observability.NoopAnalytics
import `in`.artistant.app.platform.storage.CommunityPledgeStore
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
 * The stateful half of the signup port: synchronous handle-status mapping, the terms gate, the
 * profile-save outcomes (success advances, raced handle bounces to the field, lost session
 * bounces to auth, RLS denial stays put), and the signed-in step rules — with a live session
 * `.Auth` and `.Welcome` are retired so nothing can strand the user on a screen with no forward
 * control. The debounced RPC check itself is exercised via advanceUntilIdle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignupViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakePledgeStore(
        agreed: Boolean = false,
    ) : CommunityPledgeStore {
        private val _agreed = MutableStateFlow(agreed)
        override val communityAgreed: StateFlow<Boolean> = _agreed.asStateFlow()
        override suspend fun setCommunityAgreed(agreed: Boolean) {
            _agreed.value = agreed
        }
    }

    private fun vm(
        users: UsersRepository = FakeUsersRepository(),
        pledge: CommunityPledgeStore = FakePledgeStore(),
    ) = SignupViewModel(users, NoopAnalytics(), pledge)

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
    fun `communityAgreed hydrates from pledge store`() = runTest(dispatcher) {
        val vm = vm(pledge = FakePledgeStore(agreed = true))
        advanceUntilIdle()
        assertTrue(vm.state.value.communityAgreed)
    }

    @Test
    fun `agreeCommunity persists pledge and flips state`() = runTest(dispatcher) {
        val pledge = FakePledgeStore(agreed = false)
        val vm = vm(pledge = pledge)
        advanceUntilIdle()
        assertFalse(vm.state.value.communityAgreed)
        vm.agreeCommunity()
        advanceUntilIdle()
        assertTrue(vm.state.value.communityAgreed)
        assertTrue(pledge.communityAgreed.value)
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
    fun `saveProfile with a live session keeps an RLS denial on the profile step`() = runTest(dispatcher) {
        // Same typed error, opposite cause: PGRST116 is "row missing OR blocked by RLS". With a
        // session the auth step is retired, so bouncing there would show a banner for one frame
        // and swallow the failure — surface it inline instead.
        val vm = vm(UpsertStub { throw AppError.NotFoundOrUnauthorized })
        vm.resumeAt(SignupStep.Profile)
        vm.setSignedIn(true)
        vm.setName("Yash"); vm.setCity("Bangalore"); vm.setHandle("some_handle")
        advanceUntilIdle()
        vm.saveProfile()
        advanceUntilIdle()
        assertEquals(SignupStep.Profile, vm.state.value.step)
        assertNull(vm.state.value.authNotice)
        assertTrue(vm.state.value.saveError!!.contains("Couldn't save"))
    }

    @Test
    fun `onAuthCompleted advances past auth and clears the notice`() {
        val vm = vm()
        vm.startLogin() // lands on Auth
        vm.onAuthCompleted()
        assertEquals(SignupStep.Notif, vm.state.value.step) // login: auth → notif
        assertNull(vm.state.value.authNotice)
    }

    @Test
    fun `setSignedIn lifts a user stranded on auth back into the flow`() {
        // The production call site onAuthCompleted never had: nothing else moved the flow off
        // `.Auth`, and the gate can't — re-routing to the same `RootGate.Onboarding` is
        // conflated by MutableStateFlow, so its LaunchedEffect keys never change.
        val vm = vm()
        vm.startLogin() // parks on Auth
        vm.setSignedIn(true)
        assertEquals(SignupStep.Notif, vm.state.value.step)
        assertTrue(vm.state.value.signedIn)
    }

    @Test
    fun `back from profile skips the auth step once signed in`() {
        val vm = vm()
        vm.resumeAt(SignupStep.Profile) // the gate's signed-in-but-incomplete entry
        vm.setSignedIn(true)
        vm.back()
        assertEquals(SignupStep.Role, vm.state.value.step) // NOT Auth — that was the dead end
    }

    @Test
    fun `back clamps at role for a signed-in user and reports it cannot move`() {
        val vm = vm()
        vm.resumeAt(SignupStep.Profile)
        vm.setSignedIn(true)
        vm.back() // Profile → Role
        assertFalse(vm.state.value.canGoBack) // Welcome is retired: nothing behind Role
        vm.back()
        assertEquals(SignupStep.Role, vm.state.value.step)
    }

    @Test
    fun `role continue jumps straight to profile once signed in`() {
        val vm = vm()
        vm.startSignup()
        vm.setSignedIn(true)
        vm.advance()
        assertEquals(SignupStep.Profile, vm.state.value.step)
    }
}
