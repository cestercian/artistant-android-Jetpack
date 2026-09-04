package `in`.artistant.app.feature.epk

import `in`.artistant.app.data.model.ArtistPrompt
import `in`.artistant.app.platform.media.UploadQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The press kit's narration — design screens 23 / 87 / 76 / 66.
 *
 * Everything here is copy that states a fact, which is exactly the sort of thing
 * that goes wrong silently: a section row saying "2 packages" when there is one,
 * a meter reading 100% with the tech rider empty, a banner counting an upload
 * that has already landed. None of it is reachable from a Compose test on this
 * classpath (junit + coroutines-test only), which is why it lives in
 * `EpkPressKit.kt` as free functions in the first place.
 */
class EpkPressKitTest {

    // ── Section rows ─────────────────────────────────────────────────────────

    private fun rows(
        bio: String = "",
        serviceTagCount: Int = 0,
        answered: Int = 0,
        samples: Int = 0,
        packages: Int = 0,
        fromPrice: Int? = null,
        tech: Int = 0,
        links: Int = 0,
        socials: Int = 0,
    ) = epkSectionRows(
        bio = bio,
        serviceTagCount = serviceTagCount,
        answeredPromptCount = answered,
        promptTotal = 4,
        sampleCount = samples,
        packageCount = packages,
        fromPriceInr = fromPrice,
        techCount = tech,
        linkCount = links,
        socialCount = socials,
    )

    private fun row(rows: List<EpkSectionRow>, key: EpkSectionKey) = rows.first { it.key == key }

    @Test
    fun anEmptyKitStatesTheEffectOfEveryGap() {
        val empty = rows()

        assertTrue(empty.none { it.filled })
        // The design's own framing — a gap is something to gain, so every one of
        // them opens on the same word rather than on "You haven't…".
        assertTrue(empty.all { it.detail.startsWith("Missing — ") })
        // And no gap invents a percentage. Screen 23 spells one out for the live
        // video and that figure is the design owner's; the rest state the effect
        // without claiming a measurement this app has never taken.
        assertTrue(empty.none { it.detail.contains('%') })
    }

    @Test
    fun everyEmptyRowSaysWhatBelongsInIt() {
        // Screen 87's whole premise: the kit explains itself, so no invitation
        // may be blank and none may simply repeat its own title.
        rows().forEach { row ->
            assertTrue(row.key.toString(), row.invitation.isNotBlank())
            assertFalse(row.key.toString(), row.invitation.equals(row.title, ignoreCase = true))
        }
    }

    @Test
    fun aFilledRowStatesTheFactInsteadOfTheEffect() {
        val filled = rows(
            bio = "Warm four-part harmonies and a live cajón",
            serviceTagCount = 2,
            answered = 1,
            samples = 3,
            packages = 2,
            fromPrice = 26_000,
            tech = 4,
            links = 1,
            socials = 2,
        )

        assertTrue(filled.all { it.filled })
        assertEquals("7 words · 2 services", row(filled, EpkSectionKey.Bio).detail)
        assertEquals("1 of 4 answered", row(filled, EpkSectionKey.Personality).detail)
        assertEquals("3 clips", row(filled, EpkSectionKey.Samples).detail)
        assertEquals("2 packages · from ₹26K", row(filled, EpkSectionKey.Packages).detail)
        assertEquals("4 items", row(filled, EpkSectionKey.Tech).detail)
        assertEquals("1 link · 2 accounts", row(filled, EpkSectionKey.Links).detail)
    }

    @Test
    fun oneOfAnythingIsSingular() {
        val one = rows(bio = "Loud", serviceTagCount = 1, samples = 1, packages = 1, tech = 1, links = 1)

        assertEquals("1 word · 1 service", row(one, EpkSectionKey.Bio).detail)
        assertEquals("1 clip", row(one, EpkSectionKey.Samples).detail)
        assertEquals("1 item", row(one, EpkSectionKey.Tech).detail)
        assertEquals("1 link", row(one, EpkSectionKey.Links).detail)
    }

    @Test
    fun aBioWithNoServicesDoesNotTrailAnEmptyClause() {
        assertEquals("2 words", row(rows(bio = "Just us"), EpkSectionKey.Bio).detail)
    }

    @Test
    fun packagesWithNoQuotableMinimumOmitTheFromPrice() {
        // `fromPriceInr` is null when the tiers have no savable price yet. The
        // row must not print "from ₹0" — that is a number a client would read.
        assertEquals("2 packages", row(rows(packages = 2), EpkSectionKey.Packages).detail)
    }

    @Test
    fun linkedSocialsAloneFillTheLinksRow() {
        // The row covers both `artist_links` and the three account columns, so an
        // artist with only a Spotify URL has not left it empty.
        val onlySocials = row(rows(socials = 1), EpkSectionKey.Links)

        assertTrue(onlySocials.filled)
        assertEquals("1 account", onlySocials.detail)
    }

    @Test
    fun aWhitespaceOnlyBioIsNotABio() {
        assertFalse(row(rows(bio = "  \n\t "), EpkSectionKey.Bio).filled)
    }

    // ── Completion ───────────────────────────────────────────────────────────

    @Test
    fun theCoverCountsTowardCompletion() {
        val full = rows(bio = "Us", samples = 1, packages = 1, tech = 1, links = 1, answered = 1)

        assertEquals(6, epkCompletion(full, hasCover = false).filled)
        assertEquals(7, epkCompletion(full, hasCover = false).total)
        assertTrue(epkCompletion(full, hasCover = true).isComplete)
    }

    @Test
    fun sixOfSevenNeverReadsAsAFinishedKit() {
        val full = rows(bio = "Us", samples = 1, packages = 1, tech = 1, links = 1, answered = 1)

        val almost = epkCompletion(full, hasCover = false)

        // 6/7 is 85.7%. Rounding it would print 86% — which is fine — but the
        // arithmetic must never reach 100 while `isComplete` is false, or the
        // meter and the sentence under it contradict each other.
        assertEquals(85, almost.percent)
        assertFalse(almost.isComplete)
    }

    @Test
    fun anEmptyKitIsZeroPercentAndNotComplete() {
        val nothing = epkCompletion(rows(), hasCover = false)

        assertEquals(0, nothing.percent)
        assertEquals(0f, nothing.fraction, 0f)
        assertFalse(nothing.isComplete)
    }

    @Test
    fun theSummaryNamesTheGapsAsASentence() {
        assertEquals(
            "Two things left: a cover photo and a bio.",
            completionSummary(listOf("a cover photo", "a bio")),
        )
        assertEquals("One thing left: a bio.", completionSummary(listOf("a bio")))
        assertEquals(
            "Three things left: a bio, an audio clip and a package.",
            completionSummary(listOf("a bio", "an audio clip", "a package")),
        )
    }

    @Test
    fun aLongBacklogIsTruncatedRatherThanRunOut() {
        // Past three clauses the sentence stops being a next action, and the rows
        // below it already itemise the rest.
        assertEquals(
            "Five things left: a, b, c, and 2 more.",
            completionSummary(listOf("a", "b", "c", "d", "e")),
        )
    }

    @Test
    fun aFinishedKitSaysSoAndOffersNothingToDo() {
        val done = completionSummary(emptyList())

        assertTrue(done.startsWith("Your press kit is complete"))
        assertFalse(done.contains("left"))
    }

    @Test
    fun theCompletedCountAndTheSummaryAgree() {
        // The two halves of screen 23 are computed separately, so this is the one
        // place they are checked against each other.
        val partial = rows(bio = "Us", samples = 2)
        val completion = epkCompletion(partial, hasCover = true)

        assertEquals(3, completion.filled)
        assertEquals("Four things left: a prompt answer, a package, a tech rider, and 1 more.", completion.summary)
    }

    // ── Upload banner ────────────────────────────────────────────────────────

    private fun clip(id: String, title: String = "Encore", attempts: Int = 0) =
        UploadQueue.Task.AudioSample(
            id = id,
            artistId = ARTIST,
            filePath = "/staged/$id.m4a",
            title = title,
            durationSeconds = 62.0,
            attempts = attempts,
        )

    private fun cover(id: String, attempts: Int = 0) = UploadQueue.Task.CoverPhoto(
        id = id,
        artistId = ARTIST,
        filePath = "/staged/$id.jpg",
        attempts = attempts,
    )

    @Test
    fun anIdleQueueRaisesNoBanner() {
        assertNull(uploadBannerFor(UploadQueue.State()))
    }

    @Test
    fun workInFlightCountsItsPositionInTheBatch() {
        val state = UploadQueue.State(
            pending = listOf(clip("b"), clip("c")),
            isRunning = true,
            batchTotal = 3,
            batchCompleted = 1,
        )

        val banner = uploadBannerFor(state) as EpkUploadBanner.Working

        assertEquals("Uploading 2 of 3", banner.title)
        assertEquals("Encore", banner.detail)
        assertEquals(1f / 3f, banner.fraction, TOLERANCE)
    }

    @Test
    fun aRestoredBatchCannotCountPastItsOwnTotal() {
        // A snapshot can hand back a `batchTotal` the completed count has already
        // overtaken, and "Uploading 4 of 2" is the kind of number that makes a
        // whole screen untrustworthy.
        val state = UploadQueue.State(
            pending = listOf(clip("z")),
            batchTotal = 2,
            batchCompleted = 3,
        )

        val banner = uploadBannerFor(state) as EpkUploadBanner.Working

        assertEquals("Uploading 4 of 4", banner.title)
    }

    @Test
    fun failuresOutrankWorkStillInFlight() {
        // The artist has something to do about a failure and nothing to do about
        // progress, so the one banner slot carries the failure.
        val state = UploadQueue.State(
            pending = listOf(clip("b")),
            failed = listOf(cover("a", attempts = 3)),
            batchTotal = 2,
            batchCompleted = 0,
        )

        val banner = uploadBannerFor(state)

        assertTrue(banner is EpkUploadBanner.Stalled)
        assertEquals("One upload couldn't finish", (banner as EpkUploadBanner.Stalled).title)
        assertEquals(1, banner.count)
    }

    @Test
    fun severalFailuresAreCountedInWords() {
        val state = UploadQueue.State(
            failed = listOf(cover("a", 3), clip("b", attempts = 3)),
        )

        val banner = uploadBannerFor(state) as EpkUploadBanner.Stalled

        assertEquals("Two uploads couldn't finish", banner.title)
        // The sheet's own promise, restated on the banner that opens it.
        assertTrue(banner.detail.contains("saved on this device"))
    }

    @Test
    fun aClipIsNamedByItsTitleAndACoverByWhatItIs() {
        assertEquals("Encore, Bengaluru", uploadTaskLabel(clip("a", title = "Encore, Bengaluru")))
        assertEquals("Cover photo", uploadTaskLabel(cover("b")))
        // A clip whose title never resolved falls back to the same placeholder
        // the repository publishes under, not to an empty banner line.
        assertEquals(DEFAULT_SAMPLE_TITLE, uploadTaskLabel(clip("c", title = "   ")))
    }

    // ── Stalled rows ─────────────────────────────────────────────────────────

    @Test
    fun aStalledRowStatesTheSizeAndTheAttempts() {
        val state = UploadQueue.State(failed = listOf(clip("a", title = "Encore", attempts = 3)))

        val rows = stalledRowsFor(state) { 8_812_954L }

        assertEquals(1, rows.size)
        assertEquals("Encore", rows[0].label)
        // The design's "stopped at 64%" is a number the queue cannot produce —
        // supabase-kt's upload reports no byte counter — so the row states what
        // IS known instead of animating a fiction.
        assertEquals("8.4 MB · stopped after 3 tries", rows[0].detail)
    }

    @Test
    fun aCoverGoesByItsStagedFileNameSoTwoOfThemAreTellableApart() {
        val state = UploadQueue.State(failed = listOf(cover("a", attempts = 1), cover("b", attempts = 1)))

        val rows = stalledRowsFor(state) { 0L }

        assertEquals(listOf("a.jpg", "b.jpg"), rows.map { it.label })
    }

    @Test
    fun anEvictedFileReportsTheAttemptsAndNotZeroBytes() {
        // A missing staged file measures 0, and it is also WHY the upload keeps
        // failing. "0 B" would read as an empty file the artist picked.
        assertEquals("stopped after 3 tries", stalledUploadDetail(bytes = 0, attempts = 3))
        assertEquals("stopped after 1 try", stalledUploadDetail(bytes = -1, attempts = 1))
    }

    @Test
    fun fileSizesReadTheWayAPersonReadsThem() {
        assertEquals("8.4 MB", formatFileSize(8_812_954))
        assertEquals("3.1 MB", formatFileSize(3_250_586))
        assertEquals("512 KB", formatFileSize(524_288))
        assertEquals("900 B", formatFileSize(900))
        assertEquals("0 B", formatFileSize(0))
    }

    // ── Link validation ──────────────────────────────────────────────────────

    @Test
    fun aSchemelessHostIsAcceptedBecauseTheNormaliserAddsOne() {
        assertNull(linkUrlProblem("tiltcollective.bandcamp.com"))
        assertNull(linkUrlProblem("https://soundcloud.com/tilt"))
        assertNull(linkUrlProblem("  bandcamp.com/kaavya  "))
    }

    @Test
    fun aHostWithNoDotIsATypoNotASite() {
        // What actually goes wrong: a half-typed domain that used to save and then
        // render on the artist's PUBLIC profile as a tap target going nowhere.
        assertNotNull(linkUrlProblem("banccamp"))
        assertNotNull(linkUrlProblem("https://localhost"))
        assertNotNull(linkUrlProblem("bandcamp."))
    }

    @Test
    fun aPastedStringWithASpaceIsRejectedByName() {
        assertEquals("A web address can't contain spaces.", linkUrlProblem("my band camp"))
    }

    @Test
    fun anUnopenableSchemeIsRejected() {
        assertNotNull(linkUrlProblem("ftp://files.example.com"))
        assertNotNull(linkUrlProblem("javascript://example.com"))
    }

    @Test
    fun mailtoAndTelAreLegitimatePlacesForAClientToLand() {
        assertNull(linkUrlProblem("mailto:bookings@tilt.in"))
        assertNull(linkUrlProblem("tel:+919845012345"))
        assertNotNull(linkUrlProblem("mailto:"))
    }

    @Test
    fun anEmptyAddressAsksForOneRatherThanFailingSilently() {
        assertNotNull(linkUrlProblem(""))
        assertNotNull(linkUrlProblem("   "))
    }

    @Test
    fun aLinkNeedsBothHalvesToBeSavable() {
        assertTrue(linkIsSavable("Bandcamp", "tiltcollective.bandcamp.com"))
        assertFalse(linkIsSavable("", "tiltcollective.bandcamp.com"))
        // The half that used to slip through: a label with a broken address.
        assertFalse(linkIsSavable("Bandcamp", "banccamp"))
        assertNotNull(linkLabelProblem(" "))
        assertNull(linkLabelProblem("Bandcamp"))
    }

    // ── Cancel on a sheet whose every field autosaves ────────────────────────

    private fun snapshot(
        bio: String = "Original bio.",
        services: List<String> = listOf("wedding"),
        prompts: List<ArtistPrompt> = listOf(ArtistPrompt("Your dream venue?", "A rooftop.")),
    ) = EpkEditSnapshot(bio = bio, services = services, prompts = prompts)

    /**
     * The bug Greptile found: Cancel dismissed the sheet and nothing else, so an
     * edit that had already armed its debounced save went on to publish. The
     * revert has to name the bio as something to put back AND write back.
     */
    @Test
    fun cancellingAfterEditingTheBioRevertsIt() {
        val snap = snapshot()

        val revert = epkEditRevert(
            snapshot = snap,
            bio = "A completely different bio the artist thought better of.",
            services = snap.services,
            prompts = snap.prompts,
        )

        assertEquals("Original bio.", revert.bio)
        assertNull(revert.services)
        assertNull(revert.prompts)
        assertFalse(revert.isEmpty)
    }

    /** Cancel with nothing typed is a plain dismissal — no write of any kind. */
    @Test
    fun cancellingWithoutEditingWritesNothing() {
        val snap = snapshot()

        val revert = epkEditRevert(
            snapshot = snap,
            bio = snap.bio,
            services = snap.services,
            prompts = snap.prompts,
        )

        assertTrue(revert.isEmpty)
        assertNull(revert.bio)
        assertNull(revert.services)
        assertNull(revert.prompts)
    }

    /**
     * Service chips never debounced — they wrote on the tap — so a cancelled
     * chip is always already published and the write-back is the only thing that
     * can undo it.
     */
    @Test
    fun cancellingAfterTogglingAServiceChipWritesTheOldSetBack() {
        val snap = snapshot(services = listOf("wedding"))

        val revert = epkEditRevert(
            snapshot = snap,
            bio = snap.bio,
            services = listOf("wedding", "corporate"),
            prompts = snap.prompts,
        )

        assertEquals(listOf("wedding"), revert.services)
        assertNull(revert.bio)
    }

    /** Skip on design 68 discards the answers typed into it, not just the sheet. */
    @Test
    fun skippingAfterAnsweringAPromptRevertsTheDeck() {
        val snap = snapshot(prompts = emptyList())

        val revert = epkEditRevert(
            snapshot = snap,
            bio = snap.bio,
            services = snap.services,
            prompts = listOf(ArtistPrompt("Your dream venue?", "Half-written thought")),
        )

        assertEquals(emptyList<ArtistPrompt>(), revert.prompts)
        assertFalse(revert.isEmpty)
    }

    /** One Cancel can owe three write-backs; the bio sheet edits two of them. */
    @Test
    fun cancellingRevertsEveryFieldThatMoved() {
        val snap = snapshot()

        val revert = epkEditRevert(
            snapshot = snap,
            bio = "New",
            services = listOf("corporate"),
            prompts = emptyList(),
        )

        assertEquals("Original bio.", revert.bio)
        assertEquals(listOf("wedding"), revert.services)
        assertEquals(snap.prompts, revert.prompts)
    }

    /**
     * Reordering is not editing. `ArtistPrompts.upsert` rebuilds the list, so a
     * revert keyed on identity rather than value would fire on every keystroke's
     * no-op and write the same deck back on a Cancel that changed nothing.
     */
    @Test
    fun anEqualDeckRebuiltInPlaceIsNotAChange() {
        val prompts = listOf(ArtistPrompt("Your dream venue?", "A rooftop."))
        val snap = snapshot(prompts = prompts)

        val revert = epkEditRevert(
            snapshot = snap,
            bio = snap.bio,
            services = snap.services,
            prompts = prompts.map { it.copy() },
        )

        assertTrue(revert.isEmpty)
    }

    private companion object {
        const val ARTIST = "11111111-1111-1111-1111-111111111111"
        const val TOLERANCE = 0.0001f
    }
}
