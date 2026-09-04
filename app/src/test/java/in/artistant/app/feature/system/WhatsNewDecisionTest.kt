package `in`.artistant.app.feature.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Once per version, and never to somebody with no "before" (design 137). */
class WhatsNewDecisionTest {

    @Test
    fun `a new version with notes shows once`() {
        assertEquals(
            WhatsNewDecision.Show,
            decideWhatsNew(seenVersion = "0.1.0", currentVersion = "0.2.0", hasNotes = true),
        )
    }

    @Test
    fun `the same version never shows twice`() {
        assertEquals(
            WhatsNewDecision.Nothing,
            decideWhatsNew(seenVersion = "0.2.0", currentVersion = "0.2.0", hasNotes = true),
        )
    }

    @Test
    fun `a first install records without showing`() {
        // The one case worth pinning: null is BOTH a fresh install and an upgrade
        // from a build that predates the bookkeeping, and greeting a brand-new
        // user with "what's new" is the worse of the two errors.
        assertEquals(
            WhatsNewDecision.RecordSilently,
            decideWhatsNew(seenVersion = null, currentVersion = "0.1.0", hasNotes = true),
        )
    }

    @Test
    fun `a release with no notes records rather than re-deciding every launch`() {
        assertEquals(
            WhatsNewDecision.RecordSilently,
            decideWhatsNew(seenVersion = "0.1.0", currentVersion = "0.1.1", hasNotes = false),
        )
    }

    @Test
    fun `a blank version name is never acted on`() {
        // Belt and braces against a BuildConfig that has been stripped: recording
        // "" as seen would make the next real version look already-seen.
        assertEquals(
            WhatsNewDecision.Nothing,
            decideWhatsNew(seenVersion = null, currentVersion = "", hasNotes = true),
        )
    }

    @Test
    fun `a downgrade still shows, because the notes describe the running binary`() {
        assertEquals(
            WhatsNewDecision.Show,
            decideWhatsNew(seenVersion = "0.3.0", currentVersion = "0.2.0", hasNotes = true),
        )
    }

    // ── the table itself ─────────────────────────────────────────────────────

    @Test
    fun `the shipped version has notes and three highlights`() {
        // The design's rule: three features, and the real fixes. A table that
        // drifts to five is a changelog nobody reads.
        val note = ReleaseNotes.forVersion(`in`.artistant.app.BuildConfig.VERSION_NAME)
        assertNotNull("the running version needs a release note", note)
        assertEquals(3, note!!.highlights.size)
    }

    @Test
    fun `an unknown version has no note`() {
        assertEquals(null, ReleaseNotes.forVersion("99.99.99"))
    }

    @Test
    fun `there is always something for the account list's row to open`() {
        // "What's new" is a settings row now, tappable on any build. A patch
        // release with no entry of its own falls back to `mostRecent`, so an
        // empty table would turn that row into a tap that does nothing.
        assertNotNull(ReleaseNotes.mostRecent())
    }

    @Test
    fun `mostRecent is the newest authored note`() {
        // The table is appended in release order — see the object's note. Pinned
        // so a future entry prepended by habit is caught here rather than by a
        // user reading last year's notes.
        assertEquals(`in`.artistant.app.BuildConfig.VERSION_NAME, ReleaseNotes.mostRecent()?.version)
    }

    @Test
    fun `every highlight carries copy on both lines`() {
        ReleaseNotes.forVersion(`in`.artistant.app.BuildConfig.VERSION_NAME)!!.highlights
            .forEach {
                assertTrue(it.title.isNotBlank())
                assertTrue(it.body.isNotBlank())
            }
    }
}
