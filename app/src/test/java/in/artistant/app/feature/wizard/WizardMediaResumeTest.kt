package `in`.artistant.app.feature.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resuming the wizard's staged media after a process kill.
 *
 * The scalars have been persisted since the wizard rebuild, but the cover photo
 * and the audio samples were held only in `WizardUiState`. The files themselves
 * survived — `WizardMediaCache` writes them to disk precisely so a restart can
 * find them — so a kill mid-wizard left the artist looking at an empty Cover
 * step while their photo sat in `cacheDir/artist-wizard/` under a UUID name that
 * nothing referenced any more. They would re-pick, and the first copy would stay
 * on disk forever.
 *
 * Two things make this more than "write the filename down":
 *
 * 1. **A sample's title is not recoverable from its file.** The filename is a
 *    UUID; the title is either derived from the picked document's display name
 *    or typed by the artist afterwards. If the draft doesn't carry it, a
 *    restored sample comes back as "Sample" and the artist's edit is silently
 *    reverted. Same for duration.
 * 2. **The files live in `cacheDir`, which the OS is allowed to delete.** That is
 *    the contract of that directory, not an edge case. A restored reference to a
 *    file the system reclaimed would render a broken preview and enqueue an
 *    upload of nothing, so every reference is checked against the disk before it
 *    is adopted back into the form.
 */
class WizardMediaResumeTest {

    /** Stand-in for the cache directory: whatever is in this set exists. */
    private fun disk(vararg names: String): (String) -> Boolean = { it in names.toSet() }

    private fun sample(file: String, title: String, seconds: Double = 0.0) =
        DraftSample(fileName = file, title = title, durationSeconds = seconds)

    @Test
    fun `a resumed draft keeps the cover and the samples still on disk`() {
        val restored = restoredWizardMedia(
            coverFileName = "photo-a.jpg",
            samples = listOf(sample("audio-a.m4a", "Opening set"), sample("audio-b.m4a", "Encore")),
            isOnDisk = disk("photo-a.jpg", "audio-a.m4a", "audio-b.m4a"),
        )

        assertEquals("photo-a.jpg", restored.coverFileName)
        assertEquals(listOf("audio-a.m4a", "audio-b.m4a"), restored.samples.map { it.fileName })
    }

    @Test
    fun `an edited sample title and its duration survive the restart`() {
        // The whole reason the draft carries titles: "Opening set" was typed by
        // the artist after the pick, and the UUID filename cannot reproduce it.
        val restored = restoredWizardMedia(
            coverFileName = null,
            samples = listOf(sample("audio-a.m4a", "Opening set", seconds = 212.5)),
            isOnDisk = disk("audio-a.m4a"),
        )

        assertEquals("Opening set", restored.samples.single().title)
        assertEquals(212.5, restored.samples.single().durationSeconds, 0.001)
    }

    @Test
    fun `a sample whose cached file was evicted is dropped, not restored broken`() {
        val restored = restoredWizardMedia(
            coverFileName = null,
            samples = listOf(sample("audio-gone.m4a", "Gone"), sample("audio-here.m4a", "Here")),
            isOnDisk = disk("audio-here.m4a"),
        )

        assertEquals(listOf("audio-here.m4a"), restored.samples.map { it.fileName })
    }

    @Test
    fun `a cover whose cached file was evicted restores as no cover`() {
        val restored = restoredWizardMedia(
            coverFileName = "photo-gone.jpg",
            samples = emptyList(),
            isOnDisk = disk(),
        )

        assertNull(restored.coverFileName)
    }

    @Test
    fun `restore honours the sample cap even if the draft somehow exceeds it`() {
        // The picker caps at WIZARD_MAX_SAMPLES, so an over-long list means a
        // hand-edited or older draft. Truncate rather than hand the media step a
        // list it will not let the artist shrink back below the cap.
        val many = (1..WIZARD_MAX_SAMPLES + 3).map { sample("audio-$it.m4a", "Take $it") }
        val restored = restoredWizardMedia(
            coverFileName = null,
            samples = many,
            isOnDisk = { true },
        )

        assertEquals(WIZARD_MAX_SAMPLES, restored.samples.size)
    }

    @Test
    fun `files no draft references are orphans and get swept`() {
        // Left behind by a wizard the artist abandoned, or by a pick that was
        // removed from the form. Nothing will ever reference them again, and
        // they are audio/photo sized, so they are worth deleting on resume.
        val orphans = orphanWizardMediaFiles(
            onDisk = listOf("photo-a.jpg", "audio-a.m4a", "audio-stale.m4a", "photo-stale.jpg"),
            referenced = setOf("photo-a.jpg", "audio-a.m4a"),
        )

        assertEquals(listOf("audio-stale.m4a", "photo-stale.jpg"), orphans.sorted())
    }

    // ── Who is allowed to authorize a sweep ──────────────────────────────────
    //
    // The sweep deletes files, and its reference set comes from the draft. So
    // "I read no draft" must not be one answer — the cache is a singleton whose
    // files deliberately outlive sign-out, and an empty reference set derived
    // from someone else's draft (or an unparseable one) reads as "delete every
    // staged file on this device".

    private fun draft(owner: String) = WizardDraft(ownerId = owner, coverFileName = "photo-a.jpg")

    @Test
    fun `my own draft reads as mine`() {
        val read = classifyWizardDraftRead(
            rawPresent = true,
            decoded = draft("abc-123"),
            ownerId = "abc-123",
        )
        assertEquals(WizardDraftRead.Mine(draft("abc-123")), read)
    }

    @Test
    fun `owner matching ignores case, because uids arrive both ways`() {
        val read = classifyWizardDraftRead(
            rawPresent = true,
            decoded = draft("ABC-123"),
            ownerId = "abc-123",
        )
        assertTrue(read is WizardDraftRead.Mine)
    }

    @Test
    fun `an empty slot is Empty, and only an empty slot is`() {
        assertEquals(
            WizardDraftRead.Empty,
            classifyWizardDraftRead(rawPresent = false, decoded = null, ownerId = "abc-123"),
        )
    }

    @Test
    fun `another artist's draft is unclaimable, not empty`() {
        // Sign out, someone else signs in, opens the wizard. Their reference set
        // is empty; the previous artist's cover is still the only copy there is.
        val read = classifyWizardDraftRead(
            rawPresent = true,
            decoded = draft("someone-else"),
            ownerId = "abc-123",
        )
        assertEquals(WizardDraftRead.Unclaimable, read)
    }

    @Test
    fun `a draft that will not decode is unclaimable, not empty`() {
        // Bytes are there, so media is staged for somebody — we just cannot say
        // whose or which files. Guessing "none" would delete all of them.
        val read = classifyWizardDraftRead(
            rawPresent = true,
            decoded = null,
            ownerId = "abc-123",
        )
        assertEquals(WizardDraftRead.Unclaimable, read)
    }

    @Test
    fun `a referenced file is never swept, and an empty disk sweeps nothing`() {
        assertEquals(
            emptyList<String>(),
            orphanWizardMediaFiles(onDisk = listOf("photo-a.jpg"), referenced = setOf("photo-a.jpg")),
        )
        assertEquals(
            emptyList<String>(),
            orphanWizardMediaFiles(onDisk = emptyList(), referenced = setOf("photo-a.jpg")),
        )
    }
}
