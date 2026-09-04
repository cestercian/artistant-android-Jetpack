package `in`.artistant.app.feature.gigs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gig dock's progress contract.
 *
 * Accept, Decline and Counter render together on an open request, so "something
 * is running" is not enough to label them: with one shared boolean, tapping
 * Accept made the dock read "Accepting…" AND "Declining…" at the same time —
 * two actions with opposite consequences for the client's request, both claiming
 * to be underway. The state names WHICH action is in flight; the label belongs
 * to that one button, and [GigRequestDetailUiState.isActing] is what disables
 * the rest.
 *
 * Asserted on the state rather than through the ViewModel: that one takes a
 * `CalendarSyncService`, which needs a Context and a live session, so it cannot
 * be constructed in a JVM test.
 */
class GigRequestDetailStateTest {

    @Test
    fun nothingInFlightMeansNoLabelAndNoDisabling() {
        val idle = GigRequestDetailUiState()

        assertNull(idle.actingAction)
        assertFalse(idle.isActing)
    }

    @Test
    fun anInFlightAccept_isAttributedToAccept_notToTheDeclineBesideIt() {
        val state = GigRequestDetailUiState(actingAction = GigRequestAction.Accept)

        assertEquals(GigRequestAction.Accept, state.actingAction)
        // The label the dock used to show on the Decline button at the same time.
        assertNotEquals(GigRequestAction.Decline, state.actingAction)
        // Every control still goes flat while one action runs.
        assertTrue(state.isActing)
    }

    @Test
    fun anInFlightDecline_isAttributedToDecline_notToTheAcceptAboveIt() {
        val state = GigRequestDetailUiState(actingAction = GigRequestAction.Decline)

        assertEquals(GigRequestAction.Decline, state.actingAction)
        assertNotEquals(GigRequestAction.Accept, state.actingAction)
        assertTrue(state.isActing)
    }

    @Test
    fun everyMutationCanBeNamed_soNeitherFallsBackToTheSharedFlag() {
        // Two mutations, two names — Counter left when it became a navigation to
        // screen 61 rather than a write this ViewModel makes. A third action
        // added here without a case is the regression this pins.
        assertEquals(
            listOf(GigRequestAction.Accept, GigRequestAction.Decline),
            GigRequestAction.entries.toList(),
        )
    }
}
