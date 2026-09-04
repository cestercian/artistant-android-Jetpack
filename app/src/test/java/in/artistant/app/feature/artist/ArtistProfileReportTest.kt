package `in`.artistant.app.feature.artist

import `in`.artistant.app.data.repository.PendingReport
import `in`.artistant.app.data.repository.ReportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One tap, one row in `public.reports`.
 *
 * `ArtistProfileViewModel.submitReport` had no in-flight guard, so the failure
 * banner's "Try again" — which is live for the whole round trip — filed the same
 * report on every tap. Duplicates are not cosmetic: they are two rows against one
 * person for one incident, which the moderation queue reads as a pattern.
 *
 * The ViewModel itself cannot be built here (it reaches `SavedStore`, which reaches
 * DataStore, and this module has no Robolectric), so the guard and the completion
 * are the pure pair the ViewModel is a thin caller of. A tap that reaches the seam
 * is exactly a `startingReport()` that returns non-null.
 */
class ArtistProfileReportTest {

    private val pending = PendingReport("Spam or a scam", "link in every message")

    /** How many of N taps in a row would reach `reportsRepository.reportArtist`. */
    private fun tapsThatFile(times: Int, from: ArtistProfileUiState): Int {
        var state = from
        var filed = 0
        repeat(times) {
            state.startingReport()?.let {
                filed += 1
                state = it
            }
        }
        return filed
    }

    @Test
    fun `a double tap on the failure banner files the report once`() {
        val banner = ArtistProfileUiState(failedReport = pending)
        assertEquals("the second tap must not reach the seam", 1, tapsThatFile(2, banner))
    }

    @Test
    fun `hammering the banner still files once`() {
        assertEquals(1, tapsThatFile(7, ArtistProfileUiState(failedReport = pending)))
    }

    @Test
    fun `the first tap locks the screen and closes the sheet`() {
        val started = ArtistProfileUiState(showReportSheet = true).startingReport()
        assertNotNull(started)
        assertTrue(started!!.isSubmittingReport)
        assertFalse("the sheet closes on submit", started.showReportSheet)
    }

    @Test
    fun `a retry keeps the banner up instead of blinking it out`() {
        // The reader tapped "Try again" on a banner that says nothing is holding
        // their report. Clearing it for the length of the round trip made it vanish
        // and — when the retry failed too — come straight back.
        val started = ArtistProfileUiState(failedReport = pending).startingReport()
        assertEquals(pending, started?.failedReport)
    }

    @Test
    fun `the answer unlocks the screen so a later tap can retry`() {
        val started = ArtistProfileUiState(failedReport = pending).startingReport()!!
        val settled = started.settlingReport(ReportOutcome.Failed, pending, superseded = false)
        assertFalse("a locked-open flag would wedge the retry shut for good", settled.isSubmittingReport)
        assertEquals(1, tapsThatFile(1, settled))
    }

    @Test
    fun `a landed report clears the failure and raises a receipt`() {
        val started = ArtistProfileUiState(failedReport = pending).startingReport()!!
        val settled = started.settlingReport(ReportOutcome.Sent, pending, superseded = false)
        assertNull("the loss is over — it must not sit under the receipt", settled.failedReport)
        assertEquals(ReportOutcome.Sent, settled.reportOutcome)
    }

    @Test
    fun `a queued report is a receipt, not a loss`() {
        val settled = ArtistProfileUiState(isSubmittingReport = true)
            .settlingReport(ReportOutcome.Queued, pending, superseded = false)
        assertEquals(ReportOutcome.Queued, settled.reportOutcome)
        assertNull(settled.failedReport)
    }

    @Test
    fun `a lost report is a banner, not a toast`() {
        val settled = ArtistProfileUiState(isSubmittingReport = true)
            .settlingReport(ReportOutcome.Failed, pending, superseded = false)
        assertEquals(pending, settled.failedReport)
        assertNull("Failed never toasts — it is a state with an action", settled.reportOutcome)
    }

    @Test
    fun `a superseded completion claims nothing but still unlocks`() {
        // The reader discarded the report while the retry was in the air, which bumps
        // the generation. The write that lands afterwards must not re-raise the banner
        // they just dismissed — and must not leave the screen locked either.
        val discarded = ArtistProfileUiState(isSubmittingReport = true, failedReport = null)
        val settled = discarded.settlingReport(ReportOutcome.Failed, pending, superseded = true)
        assertNull("a discarded banner must not come back from the dead", settled.failedReport)
        assertNull(settled.reportOutcome)
        assertFalse(settled.isSubmittingReport)
    }
}
