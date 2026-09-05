package `in`.artistant.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One tap, one row in `public.reports` — and one reducer saying so, for both surfaces.
 *
 * These rules used to exist twice: `ArtistProfileFacts.startingReport/settlingReport` for
 * screen 56, and an inline `when` inside `ChatViewModel.reportConversation` for screen 73.
 * They had already drifted on the retry case, and only the profile's copy was covered — which
 * is exactly how the chat's version kept its bug. [ReportSubmission] is now the single answer
 * and this is its test.
 *
 * Neither ViewModel can be built here (one reaches `SavedStore` → DataStore, the other reaches
 * Realtime), so the reducer being pure is what makes any of this assertable at all: a tap that
 * reaches the repository is exactly a `starting()` that returns non-null.
 */
class ReportSubmissionTest {

    private val pending = PendingReport("Spam or a scam", "link in every message")
    private val other = PendingReport("Harassment", null)

    /** How many of N taps in a row would reach `ReportsRepository`. */
    private fun tapsThatFile(times: Int, from: ReportSubmission): Int {
        var state = from
        var filed = 0
        repeat(times) {
            state.starting()?.let {
                filed += 1
                state = it
            }
        }
        return filed
    }

    @Test
    fun `a double tap on the failure banner files the report once`() {
        val banner = ReportSubmission(failed = pending)
        assertEquals("the second tap must not reach the seam", 1, tapsThatFile(2, banner))
    }

    @Test
    fun `hammering the banner still files once`() {
        assertEquals(1, tapsThatFile(7, ReportSubmission(failed = pending)))
    }

    @Test
    fun `the first tap locks the screen`() {
        val started = ReportSubmission().starting()
        assertNotNull(started)
        assertTrue(started!!.inFlight)
    }

    @Test
    fun `a retry keeps the banner up instead of blinking it out`() {
        // The reader tapped "Try again" on a banner that says nothing is holding their
        // report. Clearing it for the length of the round trip made it vanish and — when the
        // retry failed too — come straight back. This is the rule the chat's copy got wrong,
        // and picking it for both is why `Banner` grew `actionEnabled`: the banner stays,
        // wearing "Sending report…" on a pill that no longer takes taps.
        val started = ReportSubmission(failed = pending).starting()
        assertEquals(pending, started?.failed)
    }

    @Test
    fun `a new attempt clears the previous receipt`() {
        // Chat renders its receipt in place. A "Report received" from the last attempt
        // sitting over a new one in flight is a claim about a report that has not landed.
        val started = ReportSubmission(outcome = ReportOutcome.Sent).starting()
        assertNull(started?.outcome)
    }

    @Test
    fun `the answer unlocks the screen so a later tap can retry`() {
        val started = ReportSubmission(failed = pending).starting()!!
        val settled = started.settling(ReportOutcome.Failed, pending, started.generation)
        assertFalse("a locked-open flag would wedge the retry shut for good", settled.inFlight)
        assertEquals(1, tapsThatFile(1, settled))
    }

    @Test
    fun `a landed retry clears the banner it retried`() {
        val started = ReportSubmission(failed = pending).starting()!!
        val settled = started.settling(ReportOutcome.Sent, pending, started.generation)
        assertNull("the loss is over — it must not sit under the receipt", settled.failed)
        assertEquals(ReportOutcome.Sent, settled.outcome)
    }

    @Test
    fun `a queued report is a receipt, not a loss`() {
        // Queued is a soft-fail into the local log, and screen 56's note says so plainly.
        // It is NOT the failure banner: something is holding the report.
        val started = ReportSubmission(failed = pending).starting()!!
        val settled = started.settling(ReportOutcome.Queued, pending, started.generation)
        assertNull(settled.failed)
        assertEquals(ReportOutcome.Queued, settled.outcome)
    }

    @Test
    fun `a lost report is a banner, not a toast`() {
        val started = ReportSubmission().starting()!!
        val settled = started.settling(ReportOutcome.Failed, pending, started.generation)
        assertEquals(pending, settled.failed)
        assertNull("Failed is never a receipt — it is a loss with an action", settled.outcome)
        // The other half of "not a toast" is `reportToast(Failed) == null`, which
        // ArtistProfileFactsTest holds — the two together are what stops one lost report
        // from raising a banner AND a receipt that contradicts it.
    }

    @Test
    fun `a superseded completion claims nothing but still unlocks`() {
        // The reader discarded the report while the retry was in the air, which bumps the
        // generation. The write that lands afterwards must not re-raise the banner they just
        // dismissed — and must not leave the screen locked either.
        val discarded = ReportSubmission(inFlight = true, failed = pending).dismissing()
        val settled = discarded.settling(ReportOutcome.Failed, pending, generation = 0)
        assertNull("a discarded banner must not come back from the dead", settled.failed)
        assertFalse(settled.inFlight)
    }

    @Test
    fun `a superseded landing leaves the standing banner alone`() {
        // The mirror of the case above, and the reason a stale generation returns early
        // rather than clearing: a stale Sent must not take down a banner raised by the report
        // that replaced it.
        val standing = ReportSubmission(inFlight = true, failed = pending, generation = 4)
        val settled = standing.settling(ReportOutcome.Sent, pending, generation = 3)
        assertEquals(pending, settled.failed)
        assertNull(settled.outcome)
        assertFalse(settled.inFlight)
    }

    @Test
    fun `discarding a report in flight releases the guard it was holding`() {
        // The bug: `dismiss` bumped the generation and cleared the banner but left
        // `inFlight` set — and the abandoned attempt's completion is ignored BY that same
        // generation, so nothing else would ever clear it. The screen-wide guard then
        // swallowed every later report in silence, including a fresh one from the sheet
        // about a different person.
        val abandoned = ReportSubmission(inFlight = true, failed = pending).dismissing()
        assertFalse(abandoned.inFlight)
        assertNull(abandoned.failed)
        assertEquals("a fresh report must reach the seam", 1, tapsThatFile(1, abandoned))
    }

    @Test
    fun `the abandoned attempt still cannot write after the discard`() {
        // The other half of the discard: releasing the flag must not also make the retired
        // attempt current again.
        val abandoned = ReportSubmission(inFlight = true, failed = pending, generation = 2)
        val discarded = abandoned.dismissing()
        val late = discarded.settling(ReportOutcome.Failed, pending, generation = 2)
        assertNull(late.failed)
    }

    @Test
    fun `a second report started before the first lands wins the state`() {
        // The overlap the generation exists for: two attempts about two different things.
        // The older completion must not write its own answer over the newer attempt's.
        val first = ReportSubmission().starting()!!
        val second = first.settling(ReportOutcome.Failed, pending, first.generation).starting()!!
        val stale = second.settling(ReportOutcome.Sent, pending, first.generation)
        assertEquals("the older attempt's receipt must not clear the newer state", pending, stale.failed)
        val current = stale.settling(ReportOutcome.Failed, other, second.generation)
        assertEquals(other, current.failed)
    }

    @Test
    fun `a retired screen writes nothing`() {
        // onCleared: viewModelScope is cancelled, but a pass past its last suspension point
        // runs on to its writes regardless, and they belong to a screen that is gone.
        val inFlight = ReportSubmission(inFlight = true)
        val retired = inFlight.retired()
        val settled = retired.settling(ReportOutcome.Failed, pending, inFlight.generation)
        assertNull(settled.failed)
    }
}
