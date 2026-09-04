package `in`.artistant.app.feature.system

import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The rating prompt's three transitions, launched so they overlap.
 *
 * Its own suite because of the dispatcher: [SystemViewModelsTest] runs on an
 * UNCONFINED one, which drives each `viewModelScope.launch` straight to
 * completion before the next call is made — so nothing there can interleave, and
 * a race test written against it would pass on an implementation with no
 * serialization at all. A `StandardTestDispatcher` queues instead, so a coroutine
 * that suspends at [SlowSystemPreferences]'s read hands the turn to the next one
 * and the stale-read window this suite is about actually opens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RatePromptTransitionTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test
    fun `a late arm can never regress a rating that already happened`() = runTest {
        // Three read-modify-writes over one record, from UI callbacks that
        // genuinely overlap: the scaffold arms from a composition effect that
        // re-runs on a configuration change while the sheet's own button rates.
        // Unserialized, the second arm writes from a read taken before `rated()`
        // landed, `asked`/`rated` go back to false, and the prompt the user has
        // already answered returns on their next review.
        val prefs = SlowSystemPreferences()
        val vm = RatePromptViewModel(prefs)

        vm.recordReviewSubmitted()
        vm.rated()
        vm.recordReviewSubmitted()
        advanceUntilIdle()

        assertTrue("rated must never be un-set", prefs.snapshot.rated)
        assertTrue("asked must never be un-set", prefs.snapshot.asked)
        assertFalse(vm.visible.value)
    }

    @Test
    fun `a late arm can never re-open a prompt that was dismissed`() = runTest {
        val prefs = SlowSystemPreferences()
        val vm = RatePromptViewModel(prefs)

        vm.recordReviewSubmitted()
        vm.dismiss()
        vm.recordReviewSubmitted()
        advanceUntilIdle()

        assertTrue(prefs.snapshot.asked)
        assertFalse("'Not now' means not ever", vm.visible.value)
    }

    @Test
    fun `two dismissals racing each other still land as asked`() = runTest {
        // "Not now", the cross and a scrim tap are three controls with one
        // answer, and a modal can emit more than one of them on the way out.
        val prefs = SlowSystemPreferences()
        val vm = RatePromptViewModel(prefs)

        vm.recordReviewSubmitted()
        advanceUntilIdle()
        assertTrue(vm.visible.value)

        vm.dismiss()
        vm.dismiss()
        advanceUntilIdle()

        assertTrue(prefs.snapshot.asked)
        assertFalse(vm.visible.value)
    }

    @Test
    fun `the record is written before the sheet goes`() = runTest {
        // A process death in the window between hiding and persisting is a
        // second ask, which is the one thing screen 138 promises never happens.
        val prefs = SlowSystemPreferences()
        val vm = RatePromptViewModel(prefs)
        vm.recordReviewSubmitted()
        advanceUntilIdle()

        vm.rated()
        advanceUntilIdle()

        assertTrue(prefs.snapshot.rated)
        assertFalse(vm.visible.value)
    }
}
