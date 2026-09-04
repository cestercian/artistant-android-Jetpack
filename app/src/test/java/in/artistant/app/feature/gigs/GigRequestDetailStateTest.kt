package `in`.artistant.app.feature.gigs

import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
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

    // ── Load outcome: a failure is not an absence ────────────────────────────
    //
    // `request` is null in two very different situations, and the screen used to
    // treat them as one: it rendered screen 109's "may have expired or been
    // withdrawn by the client" over a read that had simply thrown, with no
    // Retry. That copy is false (nobody withdrew anything), it blames the
    // client for the artist's dropped connection, and it reads as terminal — so
    // the artist backs out instead of trying again.

    @Test
    fun aFailedRead_isFailed_notNotFound() {
        // The whole finding, in one line: an error wins over an absent row,
        // because a read that threw never learned whether the row exists.
        assertEquals(
            GigRequestLoad.Failed,
            gigRequestLoad(found = null, error = RuntimeException("timeout")),
        )
    }

    @Test
    fun aSuccessfulReadWithNoMatch_isTheOnlyNotFound() {
        assertEquals(GigRequestLoad.NotFound, gigRequestLoad(found = null, error = null))
    }

    @Test
    fun aReadThatReturnedTheRow_isLoaded() {
        assertEquals(GigRequestLoad.Loaded, gigRequestLoad(found = request(), error = null))
    }

    @Test
    fun anErrorOverALoadedRequest_isStillFailed_soTheBannerShows() {
        // The stale case: a refresh drops while the artist is reading. The row
        // is kept (blanking it loses the offer they were looking at) but the
        // screen has to say the numbers may be old.
        assertEquals(
            GigRequestLoad.Failed,
            gigRequestLoad(found = request(), error = RuntimeException("offline")),
        )
    }

    @Test
    fun theDefaultStateIsLoading_andIsLoadingFollowsIt() {
        // `isLoading` is derived now, so this pins that the spinner and the
        // load outcome cannot disagree.
        assertEquals(GigRequestLoad.Loading, GigRequestDetailUiState().load)
        assertTrue(GigRequestDetailUiState().isLoading)
        assertFalse(GigRequestDetailUiState(load = GigRequestLoad.Failed).isLoading)
    }

    @Test
    fun everyLoadOutcomeIsNamed_soNoneOfThemFallsBackToANullRequest() {
        // A fifth case added without a branch on the screen is the regression
        // this pins — the same shape as the dock-action test above.
        assertEquals(
            listOf(
                GigRequestLoad.Loading,
                GigRequestLoad.Loaded,
                GigRequestLoad.NotFound,
                GigRequestLoad.Failed,
            ),
            GigRequestLoad.entries.toList(),
        )
    }

    private fun request() = StoredRequest(
        raw = GigRequest(
            id = "r1",
            client = null,
            message = "",
            date = "Sat, Sep 12, 2026",
            amount = 38_000,
        ),
        status = GigRequestStatus.Open,
    )
}
