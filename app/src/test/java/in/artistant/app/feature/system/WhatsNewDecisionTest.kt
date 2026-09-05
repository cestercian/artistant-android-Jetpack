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
    //
    // Structural, never pinned to `BuildConfig.VERSION_NAME`. A patch release
    // with no entry of its own is the NORMAL case — `decideWhatsNew` and
    // `ReleaseNotes.openedBy` both handle it deliberately — so a suite that
    // required the running version to have a note turned shipping 0.1.1 into a
    // red gate.

    @Test
    fun `every authored note carries three highlights`() {
        // The design's rule: three features, and the real fixes. A table that
        // drifts to five is a changelog nobody reads.
        assertTrue("the table must not be empty", ReleaseNotes.notes.isNotEmpty())
        ReleaseNotes.notes.forEach { note ->
            assertEquals("${note.version} needs exactly three highlights", 3, note.highlights.size)
        }
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
    fun `the table is appended in release order, and mostRecent reads the end`() {
        // The object's rule — APPEND, never prepend — stated as the invariant
        // rather than as "the last entry is the version we happen to ship".
        val versions = ReleaseNotes.notes.map { it.version }
        assertEquals("no version is authored twice", versions.distinct(), versions)
        assertEquals(
            "notes must be listed oldest first",
            versions.sortedBy(::comparableVersion),
            versions,
        )
        assertEquals(versions.last(), ReleaseNotes.mostRecent()?.version)
    }

    @Test
    fun `every highlight carries copy on both lines`() {
        ReleaseNotes.notes.flatMap { it.highlights }.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.body.isNotBlank())
        }
    }

    @Test
    fun `the settings row names the note that will actually open`() {
        // The row used to print `BuildConfig.VERSION_NAME`, so on a release with
        // no entry of its own it promised one version and opened another.
        val newest = ReleaseNotes.mostRecent()!!
        assertEquals("Version ${newest.version}", whatsNewRowSubtitle(newest.version))
        assertEquals("Version ${newest.version}", whatsNewRowSubtitle("99.99.99"))
    }

    /** Zero-padded per field, so "0.10.0" sorts after "0.9.0" rather than before it. */
    private fun comparableVersion(version: String): String =
        version.split(".").joinToString(".") { it.padStart(VERSION_FIELD_WIDTH, '0') }

    private companion object {
        const val VERSION_FIELD_WIDTH = 6
    }
}
