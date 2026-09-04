package `in`.artistant.app.feature.system

import `in`.artistant.app.BuildConfig
import `in`.artistant.app.data.repository.FakeBookingsRepository
import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The three ViewModels that turn section SH's decisions into screens: What's
 * new, the rating prompt and Feedback.
 */
class SystemViewModelsTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // ── What's new (137) ─────────────────────────────────────────────────────

    @Test
    fun `a returning user on a new build sees the notes`() = runTest {
        val prefs = FakeSystemPreferences(seenVersion = "0.0.1")
        val vm = WhatsNewViewModel(prefs)
        assertNotNull(vm.visibleNote.value)
        assertEquals(BuildConfig.VERSION_NAME, vm.visibleNote.value?.version)
    }

    @Test
    fun `acknowledging records the version and closes`() = runTest {
        val prefs = FakeSystemPreferences(seenVersion = "0.0.1")
        val vm = WhatsNewViewModel(prefs)
        vm.acknowledge()
        assertNull(vm.visibleNote.value)
        assertEquals(listOf(BuildConfig.VERSION_NAME), prefs.recorded)
    }

    @Test
    fun `a second acknowledge is inert`() = runTest {
        // The cross and "Got it" are the same action, and a stray recomposition
        // must not write the version twice.
        val prefs = FakeSystemPreferences(seenVersion = "0.0.1")
        val vm = WhatsNewViewModel(prefs)
        vm.acknowledge()
        vm.acknowledge()
        assertEquals(1, prefs.recorded.size)
    }

    @Test
    fun `a first install is recorded without ever showing`() = runTest {
        val prefs = FakeSystemPreferences(seenVersion = null)
        val vm = WhatsNewViewModel(prefs)
        assertNull("a brand new user has no 'before'", vm.visibleNote.value)
        assertEquals(listOf(BuildConfig.VERSION_NAME), prefs.recorded)
    }

    @Test
    fun `the same build never shows twice`() = runTest {
        val prefs = FakeSystemPreferences(seenVersion = BuildConfig.VERSION_NAME)
        val vm = WhatsNewViewModel(prefs)
        assertNull(vm.visibleNote.value)
        assertTrue(prefs.recorded.isEmpty())
    }

    // ── Rate Artistant (138) ─────────────────────────────────────────────────

    @Test
    fun `nothing prompts until a review lands`() = runTest {
        val vm = RatePromptViewModel(FakeSystemPreferences())
        assertFalse(vm.visible.value)
    }

    @Test
    fun `a submitted review opens the prompt`() = runTest {
        val vm = RatePromptViewModel(FakeSystemPreferences())
        vm.recordReviewSubmitted()
        assertTrue(vm.visible.value)
    }

    @Test
    fun `not now closes it for good`() = runTest {
        val prefs = FakeSystemPreferences()
        val vm = RatePromptViewModel(prefs)
        vm.recordReviewSubmitted()
        vm.dismiss()
        assertFalse(vm.visible.value)
        assertTrue(prefs.ratePrompt().asked)

        // A second review must not re-open it.
        vm.recordReviewSubmitted()
        assertFalse(vm.visible.value)
    }

    @Test
    fun `rating records both flags`() = runTest {
        val prefs = FakeSystemPreferences()
        val vm = RatePromptViewModel(prefs)
        vm.recordReviewSubmitted()
        vm.rated()
        assertFalse(vm.visible.value)
        val record = prefs.ratePrompt()
        assertTrue(record.asked)
        assertTrue(record.rated)
    }

    @Test
    fun `a replayed review event cannot re-open a dismissed prompt`() = runTest {
        // The scaffold arms this from a composition effect, which can re-run on
        // a configuration change with the same value.
        val prefs = FakeSystemPreferences(rate = RatePromptRecord(asked = true))
        val vm = RatePromptViewModel(prefs)
        vm.recordReviewSubmitted()
        assertFalse(vm.visible.value)
    }

    // ── Feedback (64) ────────────────────────────────────────────────────────

    @Test
    fun `a note that lands reports Sent and queues nothing`() = runTest {
        val outbox = FakeFeedbackOutbox()
        val vm = FeedbackViewModel(FakeBookingsRepository(), outbox)
        vm.setBody("The availability strip is the most useful thing in the app.")
        vm.send()
        assertEquals(FeedbackOutcome.Sent, vm.state.value.outcome)
        assertTrue(outbox.queued.isEmpty())
    }

    @Test
    fun `a note that cannot be sent is queued, not lost`() = runTest {
        // The design promises this out loud: "it queues on this device and sends
        // on your next live session".
        val repo = FakeBookingsRepository().apply { signedIn = false }
        val outbox = FakeFeedbackOutbox()
        val vm = FeedbackViewModel(repo, outbox)
        vm.setBody("Uploads stall on a slow connection.")
        vm.send()
        assertEquals(FeedbackOutcome.Queued, vm.state.value.outcome)
        assertEquals(1, outbox.queued.size)
        assertEquals("Uploads stall on a slow connection.", outbox.queued.first().body)
    }

    @Test
    fun `the bug segment rides through to the row`() = runTest {
        val repo = FakeBookingsRepository().apply { signedIn = false }
        val outbox = FakeFeedbackOutbox()
        val vm = FeedbackViewModel(repo, outbox)
        vm.setKind(FeedbackKind.Bug)
        vm.setBody("Crash on rotate.")
        vm.send()
        assertTrue(outbox.queued.first().isBug)
    }

    @Test
    fun `a blank note is not sendable`() = runTest {
        val outbox = FakeFeedbackOutbox()
        val vm = FeedbackViewModel(FakeBookingsRepository(), outbox)
        vm.setBody("   ")
        assertFalse(vm.state.value.canSend)
        vm.send()
        assertNull(vm.state.value.outcome)
        assertTrue(outbox.queued.isEmpty())
    }

    @Test
    fun `the body is capped at the column's own limit`() = runTest {
        // `app_feedback.body` is `check (length(body) between 1 and 2000)`
        // (mig 0073). Discovering that at insert time costs the user the note.
        val vm = FeedbackViewModel(FakeBookingsRepository(), FakeFeedbackOutbox())
        vm.setBody("x".repeat(FEEDBACK_MAX_CHARS + 500))
        assertEquals(FEEDBACK_MAX_CHARS, vm.state.value.body.length)
        assertEquals(0, vm.state.value.remaining)
    }

    @Test
    fun `opening the screen drains anything left over`() = runTest {
        val outbox = FakeFeedbackOutbox()
        FeedbackViewModel(FakeBookingsRepository(), outbox)
        assertEquals(1, outbox.drains)
    }

    @Test
    fun `the box empties on a send but keeps the chosen kind`() = runTest {
        val vm = FeedbackViewModel(FakeBookingsRepository(), FakeFeedbackOutbox())
        vm.setKind(FeedbackKind.Bug)
        vm.setBody("Something is off.")
        vm.send()
        assertEquals("", vm.state.value.body)
        assertEquals(FeedbackKind.Bug, vm.state.value.kind)
    }
}
