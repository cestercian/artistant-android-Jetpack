package `in`.artistant.app.feature.bookings

import androidx.lifecycle.SavedStateHandle
import `in`.artistant.app.data.repository.FakeReviewsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The ReviewSheet submit lifecycle: a first submit against a completed booking
 * succeeds; a second submit for the same booking surfaces the already-reviewed
 * message (the unique-on-booking constraint the fake mirrors).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = ReviewViewModel(
        SavedStateHandle(mapOf("bookingId" to "b1")),
        FakeReviewsRepository(completedBookingIds = setOf("b1")),
    )

    @Test
    fun `first submit succeeds, second reports already reviewed`() = runTest(dispatcher) {
        val model = vm()

        model.submit(5, "great show")
        advanceUntilIdle()
        assertTrue(model.state.value.done)
        assertNull(model.state.value.error)

        model.submit(5, "again")
        advanceUntilIdle()
        assertTrue(model.state.value.error?.contains("already", ignoreCase = true) == true)
    }

    @Test
    fun `an invalid rating never submits`() = runTest(dispatcher) {
        val model = vm()
        model.submit(0, null)
        advanceUntilIdle()
        assertEquals(false, model.state.value.done)
        assertNull(model.state.value.error) // guarded before the write, so no error surfaced
    }
}
