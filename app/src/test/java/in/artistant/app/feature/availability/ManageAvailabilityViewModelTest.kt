package `in`.artistant.app.feature.availability

import `in`.artistant.app.data.model.SelfAvailability
import `in`.artistant.app.data.repository.FakeArtistsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Seeds from the artist's row and saves in canonical chip order (not Set iteration order). */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageAvailabilityViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `seeds from the row then saves days and times in canonical order`() = runTest(dispatcher) {
        val artists = FakeArtistsRepository().apply {
            availability = SelfAvailability(days = listOf("Fri", "Sat"), times = listOf("9:00 PM"))
        }
        val vm = ManageAvailabilityViewModel(artists)
        advanceUntilIdle()
        assertEquals(setOf("Fri", "Sat"), vm.state.value.days)

        // Toggle in a deliberately out-of-canonical order.
        vm.toggleTime("6:00 PM")   // earlier slot added after 9:00 PM
        vm.toggleDay("Mon")        // earlier day added after Fri/Sat
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.state.value.saved)
        // allDays = Mon..Sun, allTimeSlots ascending — persisted order follows those.
        assertEquals(listOf("Mon", "Fri", "Sat"), artists.availability.days)
        assertEquals(listOf("6:00 PM", "9:00 PM"), artists.availability.times)
    }
}
