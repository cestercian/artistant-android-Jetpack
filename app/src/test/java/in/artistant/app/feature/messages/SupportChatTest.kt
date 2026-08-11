package `in`.artistant.app.feature.messages

import `in`.artistant.app.testsupport.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The scripted support assistant.
 *
 * A decision tree's failure mode is a dead end — a step that offers no way
 * onward — and that is only findable if every state can be enumerated, which is
 * the main thing this file does. The rest covers the one real side effect: a
 * typed note going to `app_feedback`, and the receipt telling the truth about
 * whether it landed.
 */
class SupportChatTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    // --- the tree ------------------------------------------------------------

    @Test
    fun everyStepOffersAWayOnward() {
        SupportStep.entries.forEach { step ->
            assertTrue("$step is a dead end", SupportScript.replies(step).isNotEmpty())
        }
    }

    @Test
    fun everyStepCanReachTheRoot() {
        SupportStep.entries.forEach { step ->
            val reachesRoot = SupportScript.replies(step)
                .any { SupportScript.next(it.intent) == SupportStep.Root } ||
                step == SupportStep.Root
            assertTrue("$step cannot get back to the menu", reachesRoot)
        }
    }

    /** The free-text escape hatch is offered everywhere except right after a note. */
    @Test
    fun theTypeItOutHatchIsAvailableFromTheMenuAndAfterAnAnswer() {
        fun offersTyping(step: SupportStep) =
            SupportScript.replies(step).any { it.intent == SupportIntent.TypeItOut }

        assertTrue(offersTyping(SupportStep.Root))
        assertTrue(offersTyping(SupportStep.Answered))
        assertFalse("a sent note must not invite an immediate duplicate", offersTyping(SupportStep.NoteSent))
    }

    /** Only the bookings answer carries a destination; the rest are text. */
    @Test
    fun onlyTheBookingAnswerOffersADeepLink() {
        assertTrue(SupportScript.answer(SupportIntent.Booking, "Bookings").second)
        assertFalse(SupportScript.answer(SupportIntent.Safety, "Bookings").second)
        assertFalse(SupportScript.answer(SupportIntent.Other, "Bookings").second)
    }

    /** An artist's funnel tab is called Gigs, and the answer has to say so. */
    @Test
    fun theBookingAnswerUsesTheRolesOwnTabName() {
        assertTrue(SupportScript.answer(SupportIntent.Booking, "Gigs").first.contains("Gigs"))
        assertTrue(SupportScript.answer(SupportIntent.Booking, "Bookings").first.contains("Bookings"))
    }

    /** "Back to menu" is navigation, not something the reader said. */
    @Test
    fun goingBackDoesNotPutWordsInTheReadersMouth() {
        assertNull(SupportScript.userLine(SupportIntent.BackToMenu))
        assertEquals("Something else", SupportScript.userLine(SupportIntent.Other))
    }

    // --- the transcript ------------------------------------------------------

    @Test
    fun theTranscriptOpensOnTheGreeting() = runTest {
        val model = SupportChatViewModel(StubBookings())

        assertEquals(listOf(SupportScript.GREETING), model.state.value.lines.map { it.text })
        assertEquals(SupportStep.Root, model.state.value.step)
    }

    @Test
    fun choosingATopicEchoesTheQuestionThenAnswersIt() = runTest {
        val model = SupportChatViewModel(StubBookings())

        model.choose(SupportIntent.Safety, "Bookings")

        val lines = model.state.value.lines
        assertEquals(3, lines.size)
        assertFalse("the reader's turn comes first", lines[1].fromBot)
        assertTrue(lines[2].fromBot)
        assertEquals(SupportStep.Answered, model.state.value.step)
    }

    // --- the one real side effect -------------------------------------------

    @Test
    fun aTypedNoteReachesTheFeedbackRepository() = runTest {
        val bookings = StubBookings()
        val model = SupportChatViewModel(bookings)

        model.choose(SupportIntent.TypeItOut, "Bookings")
        model.sendNote("  The artist never replied  ")

        assertEquals(listOf("The artist never replied"), bookings.feedback)
        assertEquals(SupportStep.NoteSent, model.state.value.step)
        assertTrue(model.state.value.lines.last().text.startsWith("Thanks"))
    }

    /**
     * The honesty case. `submitFeedback` reports a failed write, so the receipt
     * must not claim the note was logged — and the composer must stay open so
     * retrying is one tap rather than a walk back through the menu.
     */
    @Test
    fun aFailedNoteSaysSoAndLeavesTheComposerOpen() = runTest {
        val bookings = StubBookings().apply { feedbackDelivers = false }
        val model = SupportChatViewModel(bookings)

        model.choose(SupportIntent.TypeItOut, "Bookings")
        model.sendNote("Something went wrong")

        assertTrue(model.state.value.composing)
        assertFalse(model.state.value.lines.last().text.contains("logged"))
    }

    @Test
    fun emptyNotesAreNeverSent() = runTest {
        val bookings = StubBookings()
        val model = SupportChatViewModel(bookings)

        model.sendNote("")
        model.sendNote("   ")

        assertTrue(bookings.feedback.isEmpty())
    }
}
