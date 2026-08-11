package `in`.artistant.app.feature.epk

import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.feature.wizard.WIZARD_BIO_MAX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The EPK editor's decisions.
 *
 * The screen and the ViewModel are unreachable from this classpath (no
 * Robolectric, no Compose test rule, and the ViewModel takes a concrete
 * `SessionManager`), so everything that could be wrong at runtime was pushed
 * into `EpkLogic.kt` and is asserted here. The two clusters that matter most are
 * the whole-set write guard — the one that decides whether an artist's published
 * pricing survives a bad network read — and the package draft rules, which is
 * where the "every tier is Popular" bug lived.
 */
class EpkLogicTest {

    private fun row(
        key: String = "k",
        name: String = "Headline set",
        duration: String = "60 min",
        price: String = "50000",
        popular: Boolean = false,
    ) = PackageRow(key = key, name = name, duration = duration, price = price, popular = popular)

    // ── Price input ──────────────────────────────────────────────────────────

    @Test
    fun sanitizePrice_keepsOnlyDigits() {
        assertEquals("50000", sanitizePriceInput("₹50,000"))
        assertEquals("1234", sanitizePriceInput("1a2b3c4"))
    }

    @Test
    fun sanitizePrice_stripsLeadingZeros_butKeepsALoneZero() {
        assertEquals("5", sanitizePriceInput("05"))
        assertEquals("0", sanitizePriceInput("0"))
        assertEquals("0", sanitizePriceInput("0000"))
    }

    @Test
    fun sanitizePrice_emptyStaysEmpty_soAClearedFieldIsNotCoercedToZero() {
        assertEquals("", sanitizePriceInput(""))
        assertEquals("", sanitizePriceInput("abc"))
    }

    @Test
    fun sanitizePrice_clampsAPasteAtTheCeiling() {
        assertEquals(MAX_PRICE_INR.toString(), sanitizePriceInput("999999999"))
    }

    @Test
    fun parsePrice_returnsNullWhenThereIsNoNumber() {
        assertNull(parsePrice(""))
        assertEquals(4200, parsePrice("4200"))
    }

    // ── Package rows ─────────────────────────────────────────────────────────

    @Test
    fun aRowNeedsBothANameAndANonZeroPrice() {
        assertTrue(packageRowIsSavable(row()))
        assertFalse(packageRowIsSavable(row(name = "   ")))
        assertFalse(packageRowIsSavable(row(price = "")))
        assertFalse(packageRowIsSavable(row(price = "0")))
    }

    @Test
    fun drafts_dropIncompleteRowsInsteadOfDefaultingThem() {
        val drafts = packageDrafts(
            listOf(
                row(key = "a", name = "Acoustic", price = "22000"),
                row(key = "b", name = "", price = "80000"),
                row(key = "c", name = "Full band", price = ""),
            ),
        )

        assertEquals(listOf("Acoustic"), drafts.map { it.name })
    }

    @Test
    fun drafts_trimAndFallBackOnDurationOnly() {
        val draft = packageDrafts(listOf(row(name = "  Acoustic  ", duration = "  "))).single()

        assertEquals("Acoustic", draft.name)
        assertEquals("set", draft.durationLabel)
    }

    /**
     * The regression this file exists for. A writer that hardcoded `popular =
     * true` badged every artist's every tier, which made the badge a constant
     * and therefore meaningless everywhere it was read.
     */
    @Test
    fun drafts_defaultPopularToFalseAndRoundTripTheArtistsChoice() {
        val drafts = packageDrafts(
            listOf(
                row(key = "a", name = "Acoustic", popular = false),
                row(key = "b", name = "Full band", popular = true),
            ),
        )

        assertEquals(listOf(false, true), drafts.map { it.popular })
    }

    @Test
    fun drafts_neverMarkPopularOnASetTheArtistNeverFlagged() {
        val drafts = packageDrafts(listOf(row(key = "a"), row(key = "b", name = "Second")))

        assertTrue(drafts.none { it.popular })
    }

    @Test
    fun popularBadge_meansNothingWhenEveryTierCarriesIt() {
        val all = listOf(row(key = "a", popular = true), row(key = "b", name = "Two", popular = true))

        assertFalse(popularBadgeWouldMeanSomething(all))
    }

    @Test
    fun popularBadge_meansNothingForASingleTier() {
        assertFalse(popularBadgeWouldMeanSomething(listOf(row(popular = true))))
    }

    @Test
    fun popularBadge_meansSomethingOnceItSplitsTheSet() {
        val split = listOf(row(key = "a", popular = true), row(key = "b", name = "Two", popular = false))

        assertTrue(popularBadgeWouldMeanSomething(split))
    }

    // ── "from" price ─────────────────────────────────────────────────────────

    @Test
    fun previewPackages_excludeUnsavableRows() {
        val preview = previewPackages(
            listOf(row(key = "a", name = "Acoustic", price = "22000"), row(key = "b", name = "", price = "1")),
        )

        assertEquals(1, preview.size)
        assertEquals(22000, preview.single().price)
    }

    /**
     * The editor must quote the same "from" figure the public profile does, so
     * the preview feeds the shared helper rather than computing a minimum of its
     * own. Asserted end-to-end here because two surfaces disagreeing about one
     * price list is the bug that helper was written for.
     */
    @Test
    fun fromPrice_isTheMinimumOfTheTypedTiers_notTheFirstOne() {
        val rows = listOf(
            row(key = "a", name = "Full band", price = "83000"),
            row(key = "b", name = "Acoustic", price = "22000"),
        )

        assertEquals(22000, PackagePricing.fromPrice(previewPackages(rows), fallback = 51000))
    }

    @Test
    fun fromPrice_fallsBackOnlyWhenNothingIsTyped() {
        assertEquals(51000, PackagePricing.fromPrice(previewPackages(emptyList()), fallback = 51000))
    }

    @Test
    fun previewPackages_agreeWithSavedPackagesForTheSameSet() {
        val rows = listOf(row(key = "a", name = "Acoustic", price = "22000"))
        val saved = listOf(
            ArtistPackage(id = "a", name = "Acoustic", duration = "60 min", price = 22000, includes = emptyList()),
        )

        assertEquals(
            PackagePricing.fromPrice(saved, fallback = 0),
            PackagePricing.fromPrice(previewPackages(rows), fallback = 0),
        )
    }

    // ── The wipe guard ───────────────────────────────────────────────────────

    @Test
    fun aWholeSetReplaceIsRefusedUntilTheServerListHasBeenRead() {
        assertFalse(canReplaceWholeSet(hydrated = false, hasSession = true))
    }

    @Test
    fun aWholeSetReplaceIsRefusedWithoutASession() {
        assertFalse(canReplaceWholeSet(hydrated = true, hasSession = false))
    }

    @Test
    fun aWholeSetReplaceIsAllowedOnceBothHold() {
        assertTrue(canReplaceWholeSet(hydrated = true, hasSession = true))
    }

    // ── Ordering ─────────────────────────────────────────────────────────────

    @Test
    fun moveItem_promotesAPhotoToCover() {
        assertEquals(listOf("c", "a", "b"), moveItem(listOf("a", "b", "c"), from = 2, to = 0))
    }

    @Test
    fun moveItem_shiftsByOneInEitherDirection() {
        assertEquals(listOf("b", "a", "c"), moveItem(listOf("a", "b", "c"), from = 0, to = 1))
        assertEquals(listOf("a", "c", "b"), moveItem(listOf("a", "b", "c"), from = 2, to = 1))
    }

    /** A stale index from a list a background refresh shortened is a race, not a crash. */
    @Test
    fun moveItem_returnsTheSameListForANoOpOrAnOutOfBoundsIndex() {
        val items = listOf("a", "b")

        assertSame(items, moveItem(items, from = 1, to = 1))
        assertSame(items, moveItem(items, from = 5, to = 0))
        assertSame(items, moveItem(items, from = 0, to = -1))
    }

    // ── Tech rider ───────────────────────────────────────────────────────────

    @Test
    fun toggleTech_addsThenRemoves() {
        val once = toggleTechItem(emptyList(), "1 DI box")
        assertEquals(listOf("1 DI box"), once)
        assertEquals(emptyList<String>(), toggleTechItem(once, "1 DI box"))
    }

    @Test
    fun toggleTech_removesRegardlessOfCase() {
        assertEquals(emptyList<String>(), toggleTechItem(listOf("1 DI Box"), "1 di box"))
    }

    @Test
    fun toggleTech_appendsSoTheRiderKeepsItsOrder() {
        assertEquals(listOf("a", "b"), toggleTechItem(listOf("a"), "b"))
    }

    @Test
    fun addTechItem_trimsAndIgnoresBlanks() {
        assertEquals(listOf("Wedge monitor"), addTechItem(emptyList(), "  Wedge monitor  "))
        assertEquals(emptyList<String>(), addTechItem(emptyList(), "   "))
    }

    @Test
    fun addTechItem_refusesACaseInsensitiveDuplicate() {
        val items = listOf("4 vocal mics")

        assertEquals(items, addTechItem(items, "4 Vocal Mics"))
    }

    // ── Links ────────────────────────────────────────────────────────────────

    @Test
    fun normalizeLink_addsHttpsToASchemeLessHost() {
        assertEquals("https://bandcamp.com/kaavya", normalizeLinkUrl("bandcamp.com/kaavya"))
    }

    @Test
    fun normalizeLink_leavesAnExistingSchemeAlone() {
        assertEquals("http://example.com", normalizeLinkUrl("http://example.com"))
        assertEquals("https://example.com", normalizeLinkUrl("  https://example.com  "))
        assertEquals("mailto:hi@example.com", normalizeLinkUrl("mailto:hi@example.com"))
    }

    @Test
    fun normalizeLink_leavesAnEmptyStringEmptySoTheSaveGateCanRefuseIt() {
        assertEquals("", normalizeLinkUrl("   "))
        assertFalse(linkIsSavable("Bandcamp", normalizeLinkUrl("   ")))
    }

    @Test
    fun aLinkNeedsBothHalves() {
        assertTrue(linkIsSavable("Bandcamp", "https://bandcamp.com"))
        assertFalse(linkIsSavable("", "https://bandcamp.com"))
        assertFalse(linkIsSavable("Bandcamp", ""))
    }

    // ── Share link ───────────────────────────────────────────────────────────

    @Test
    fun shareLink_isNullUntilTheArtistHasAHandle() {
        assertNull(shareLinkUrl(null))
        assertNull(shareLinkUrl("   "))
    }

    @Test
    fun shareLink_isTheHandleUnderTheBrandDomain() {
        assertEquals("artistant.in/kaavya", shareLinkUrl(" kaavya "))
    }

    // ── Capacity ─────────────────────────────────────────────────────────────

    @Test
    fun sampleAdds_stopAtTheCapAndDuringAnUpload() {
        assertTrue(canAddSample(currentCount = MAX_SAMPLES - 1, uploadInFlight = false))
        assertFalse(canAddSample(currentCount = MAX_SAMPLES, uploadInFlight = false))
        assertFalse(canAddSample(currentCount = 0, uploadInFlight = true))
    }

    @Test
    fun photoAdds_stopAtTheCapAndDuringAnUpload() {
        assertTrue(canAddPhoto(currentCount = MAX_PHOTOS - 1, uploadInFlight = false))
        assertFalse(canAddPhoto(currentCount = MAX_PHOTOS, uploadInFlight = false))
        assertFalse(canAddPhoto(currentCount = 0, uploadInFlight = true))
    }

    // ── Completeness ─────────────────────────────────────────────────────────

    @Test
    fun completeness_countsAFullProfileAsDone() {
        val result = epkCompleteness(
            photoCount = 2,
            bio = "Bangalore four-piece.",
            packageCount = 1,
            sampleCount = 1,
            techCount = 3,
            socialCount = 1,
            linkCount = 1,
        )

        assertTrue(result.isComplete)
        assertEquals(result.total, result.complete)
        assertTrue(result.missing.isEmpty())
    }

    @Test
    fun completeness_namesWhatIsMissing_inClientImpactOrder() {
        val result = epkCompleteness(
            photoCount = 0,
            bio = "   ",
            packageCount = 1,
            sampleCount = 1,
            techCount = 1,
            socialCount = 1,
            linkCount = 1,
        )

        assertEquals(listOf("a cover photo", "a bio"), result.missing)
        assertEquals(5, result.complete)
    }

    @Test
    fun completeness_treatsAWhitespaceBioAsMissing() {
        val result = epkCompleteness(0, "\n\t ", 0, 0, 0, 0, 0)

        assertEquals(result.total, result.missing.size)
        assertEquals(0, result.complete)
    }

    @Test
    fun socialCount_treatsBlankAsUnlinked() {
        assertEquals(0, socialLinkCount(null, "", "   "))
        assertEquals(2, socialLinkCount("https://open.spotify.com/x", null, "https://youtube.com/@y"))
    }

    // ── Bio ──────────────────────────────────────────────────────────────────

    /**
     * The editor is the post-wizard write path for the same column, so a looser
     * cap here would be a way around the wizard's. The constant is deliberately
     * re-declared per feature (as the sample cap already is); this is the guard
     * that keeps the copies honest, because a duplicated number with nothing
     * watching it is a number that drifts.
     */
    @Test
    fun bioCap_agreesWithTheWizardsCap() {
        assertEquals(WIZARD_BIO_MAX, MAX_BIO_CHARS)
    }

    @Test
    fun clampBio_truncatesAPasteInsteadOfAcceptingIt() {
        val essay = "x".repeat(MAX_BIO_CHARS + 50)

        assertEquals(MAX_BIO_CHARS, clampBioInput(essay).length)
    }

    @Test
    fun clampBio_leavesAnythingWithinTheCapExactlyAsTyped() {
        assertEquals("Bangalore four-piece.", clampBioInput("Bangalore four-piece."))
        assertEquals("", clampBioInput(""))
    }

    @Test
    fun bioCounter_onlyGoesLoudAtTheWall() {
        assertFalse(bioIsAtCap(0))
        assertFalse(bioIsAtCap(MAX_BIO_CHARS - 1))
        assertTrue(bioIsAtCap(MAX_BIO_CHARS))
    }

    @Test
    fun bioSave_isSkippedWhenNothingActuallyChanged() {
        assertFalse(bioNeedsSave(draft = "Bangalore four-piece.", saved = "Bangalore four-piece."))
        assertFalse(bioNeedsSave(draft = "", saved = ""))
    }

    /**
     * The write trims, so an untrimmed draft that differs only in whitespace is
     * already what the server holds. Comparing raw would make every save look
     * like a change and re-send the bio on every debounce forever.
     */
    @Test
    fun bioSave_ignoresWhitespaceTheWriteWouldHaveTrimmedAnyway() {
        assertFalse(bioNeedsSave(draft = "  Bangalore four-piece.  ", saved = "Bangalore four-piece."))
    }

    @Test
    fun bioSave_firesOnARealEdit_includingClearingIt() {
        assertTrue(bioNeedsSave(draft = "Bangalore five-piece.", saved = "Bangalore four-piece."))
        assertTrue(bioNeedsSave(draft = "", saved = "Bangalore four-piece."))
    }

    // ── Cover palette ────────────────────────────────────────────────────────

    @Test
    fun shownCover_prefersThePickOverThePublishedRow() {
        assertEquals(3, shownCoverGradient(pending = 3, published = 1))
        assertEquals(1, shownCoverGradient(pending = null, published = 1))
    }

    /** A row written before the palette list grew must not index off the end. */
    @Test
    fun shownCover_clampsAnOutOfRangePublishedIndex() {
        val last = ArtistGradient.count - 1

        assertEquals(last, shownCoverGradient(pending = null, published = 99))
        assertEquals(0, shownCoverGradient(pending = null, published = -1))
    }

    /**
     * The same gate the pricing whole-set replace has, for the same reason: the
     * palette is a column on the artist row, and writing that row before reading
     * it is how an editor overwrites a value it never showed the artist.
     */
    @Test
    fun coverPick_isRefusedUntilTheArtistRowHasBeenRead() {
        assertNull(coverGradientPickToWrite(hydrated = false, pending = null, published = 0, requested = 2))
    }

    @Test
    fun coverPick_writesTheClampedIndexOnARealChange() {
        assertEquals(2, coverGradientPickToWrite(hydrated = true, pending = null, published = 0, requested = 2))
        assertEquals(
            ArtistGradient.count - 1,
            coverGradientPickToWrite(hydrated = true, pending = null, published = 0, requested = 99),
        )
    }

    @Test
    fun coverPick_isANoOpWhenItRestatesThePublishedPalette() {
        assertNull(coverGradientPickToWrite(hydrated = true, pending = null, published = 4, requested = 4))
    }

    /**
     * The subtle one. Re-tapping a swatch the artist already picked this session
     * looks like nothing happening, so it has to BE nothing — comparing against
     * the published row instead of the pending pick would spend a request per
     * double-tap restating a value the server is already being told.
     */
    @Test
    fun coverPick_isANoOpWhenItRestatesAPickTheWriteHasNotConfirmedYet() {
        assertNull(coverGradientPickToWrite(hydrated = true, pending = 3, published = 0, requested = 3))
    }

    @Test
    fun coverPick_allowsPickingBackToThePublishedPaletteAfterAnUnconfirmedPick() {
        assertEquals(0, coverGradientPickToWrite(hydrated = true, pending = 3, published = 0, requested = 0))
    }
}
