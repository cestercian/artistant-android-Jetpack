package `in`.artistant.app.feature.epk

import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.repository.SupabaseSamplesRepository
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.domain.artist.ServiceTags
import `in`.artistant.app.feature.wizard.WIZARD_BIO_MAX
import `in`.artistant.app.platform.media.UploadQueue
import `in`.artistant.app.testsupport.ARTIST_ID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
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

    // ── Tier blockers ────────────────────────────────────────────────────────

    /**
     * The blocker has to agree with the writer exactly. If a row can be savable
     * and still carry a "won't publish" note — or worse, be silently dropped
     * while claiming it is fine — the note is misinformation rather than help.
     */
    @Test
    fun tierBlocker_isSilentExactlyWhenTheRowWouldBeSaved() {
        val cases = listOf(
            row(),
            row(name = ""),
            row(price = ""),
            row(price = "0"),
            row(name = "", price = ""),
        )

        for (case in cases) {
            assertEquals(packageRowIsSavable(case), packageRowBlocker(case) == null)
        }
    }

    @Test
    fun tierBlocker_namesTheHalfThatIsMissing() {
        assertTrue(packageRowBlocker(row(price = ""))!!.contains("price"))
        assertTrue(packageRowBlocker(row(name = ""))!!.contains("name"))
        assertNull(packageRowBlocker(row()))
    }

    /** A zero price is a free gig, not a price — it has to read as missing. */
    @Test
    fun tierBlocker_treatsAZeroPriceAsNoPrice() {
        assertTrue(packageRowBlocker(row(price = "0"))!!.contains("price"))
    }

    @Test
    fun tierBlocker_separatesAFreshRowFromAHalfTypedOne() {
        assertFalse(packageRowIsPartiallyFilled(row(name = "", duration = "", price = "")))
        assertTrue(packageRowIsPartiallyFilled(row(price = "")))
        assertTrue(packageRowIsPartiallyFilled(row(name = "", duration = "", price = "50000")))
        // A savable row is never "partially filled" — it is just filled.
        assertFalse(packageRowIsPartiallyFilled(row()))
    }

    // ── New-artist offer ─────────────────────────────────────────────────────

    @Test
    fun newArtistOffer_togglesBetweenOffAndTheStandardFigure() {
        assertEquals(NEW_ARTIST_DISCOUNT_PCT, newArtistDiscountToggleTarget(0))
        assertEquals(0, newArtistDiscountToggleTarget(NEW_ARTIST_DISCOUNT_PCT))
    }

    /**
     * A row written by another client can hold a percentage this one never
     * offers. Turning it off has to work anyway — the whole point of the control
     * is being able to withdraw a promise the artist did not make here.
     */
    @Test
    fun newArtistOffer_turnsOffAnyNonZeroValue_notJustTheStandardOne() {
        assertEquals(0, newArtistDiscountToggleTarget(15))
        assertEquals(0, newArtistDiscountToggleTarget(5))
    }

    /** The optimistic tap wins, and clearing it falls back to what is published. */
    @Test
    fun newArtistOffer_showsThePendingTapOverThePublishedValue() {
        assertEquals(0, shownNewArtistDiscount(pending = 0, published = 20))
        assertEquals(20, shownNewArtistDiscount(pending = null, published = 20))
        assertEquals(0, shownNewArtistDiscount(pending = null, published = 0))
    }

    /**
     * The editor must not claim a different figure than the public profile
     * prints, so the label reads the stored percentage rather than assuming the
     * standard one.
     */
    @Test
    fun newArtistOffer_labelsWhateverIsActuallyStored() {
        assertTrue(newArtistDiscountLabel(15).startsWith("15%"))
        assertTrue(newArtistDiscountLabel(NEW_ARTIST_DISCOUNT_PCT).startsWith("20%"))
    }

    /** Off still has to offer a figure, or the switch has no label to turn on to. */
    @Test
    fun newArtistOffer_labelsTheStandardFigureWhenOff() {
        assertTrue(newArtistDiscountLabel(0).startsWith("$NEW_ARTIST_DISCOUNT_PCT%"))
    }

    // ── Weekend premium ──────────────────────────────────────────────────────

    @Test
    fun weekendPremium_stepsUpThroughTheLadder() {
        assertEquals(10, weekendPremiumStepTarget(0))
        assertEquals(15, weekendPremiumStepTarget(10))
        assertEquals(30, weekendPremiumStepTarget(25))
    }

    /** Past the top the cycle has to come back to off, or it cannot be withdrawn. */
    @Test
    fun weekendPremium_wrapsToOffAtTheTop() {
        assertEquals(0, weekendPremiumStepTarget(WEEKEND_PREMIUM_STEPS.last()))
        assertEquals(0, weekendPremiumStepTarget(90))
    }

    /**
     * The reference client offers a continuous slider, so an off-ladder value is
     * an ordinary thing to read. A tap means "more" — snapping someone's 12% to
     * off would be a surprising way to lose it.
     */
    @Test
    fun weekendPremium_advancesPastAnOffLadderValueRatherThanResetting() {
        assertEquals(15, weekendPremiumStepTarget(12))
        assertEquals(10, weekendPremiumStepTarget(7))
    }

    @Test
    fun weekendPremium_showsThePendingTapOverThePublishedValue() {
        assertEquals(0, shownWeekendPremium(pending = 0, published = 20))
        assertEquals(20, shownWeekendPremium(pending = null, published = 20))
    }

    /**
     * Clamped to the COLUMN's range, not to the step ladder: rounding another
     * client's value on read would misreport what clients are actually shown.
     */
    @Test
    fun weekendPremium_clampsToTheColumnRangeButKeepsOffLadderValues() {
        assertEquals(12, shownWeekendPremium(pending = null, published = 12))
        assertEquals(100, shownWeekendPremium(pending = null, published = 220))
        assertEquals(0, shownWeekendPremium(pending = null, published = -5))
    }

    /** Labels what is stored, so the editor cannot disagree with the public page. */
    @Test
    fun weekendPremium_labelsWhateverIsActuallyStored() {
        assertTrue(weekendPremiumLabel(12).startsWith("12%"))
        assertEquals("Off", weekendPremiumLabel(0))
    }

    // ── Services offered ─────────────────────────────────────────────────────

    /** Same optimistic contract as the offer and the palette. */
    @Test
    fun serviceTags_showThePendingSetOverThePublishedOne() {
        assertEquals(
            listOf("dj-set"),
            shownServiceTags(pending = listOf("dj-set"), published = listOf("full-band")),
        )
        assertEquals(
            listOf("full-band"),
            shownServiceTags(pending = null, published = listOf("full-band")),
        )
    }

    /**
     * A pending EMPTY set is a real state — the artist unticking their last
     * service — and must not be mistaken for "nothing pending". Null is the only
     * thing that means that.
     */
    @Test
    fun serviceTags_treatAnEmptyPendingSetAsAnEdit_notAsAbsent() {
        assertTrue(shownServiceTags(pending = emptyList(), published = listOf("dj-set")).isEmpty())
    }

    /**
     * The chips can never show a set the column would reject, so an over-cap or
     * duplicated set read from the server is normalized before it is rendered.
     */
    @Test
    fun serviceTags_normalizeWhatTheyShow() {
        assertEquals(
            listOf("dj-set"),
            shownServiceTags(pending = null, published = listOf("dj-set", "dj-set", " ")),
        )
        assertEquals(
            ServiceTags.MAX_TAGS,
            shownServiceTags(pending = null, published = ServiceTags.catalog.map { it.first }).size,
        )
    }

    // ── Connected accounts ───────────────────────────────────────────────────

    private val linked = SocialDraft(
        spotify = "https://open.spotify.com/artist/x",
        instagram = "@kaavya",
        youtube = "https://youtube.com/@kaavya",
    )

    /**
     * The seeding round-trip, which is what the hydration gate is protecting.
     *
     * A row where all three are NULL and a draft where all three are empty have
     * to be the SAME state, or the very first hydrate would look like an edit and
     * schedule a save of three blanks against a row nobody read.
     */
    @Test
    fun socialDraft_treatsAnUnsetRowAsAnEmptyDraft_notAsAnEdit() {
        val fromEmptyRow = socialDraftOf(spotify = null, instagram = null, youtube = null)

        assertEquals(SocialDraft(), fromEmptyRow)
        assertFalse(socialsNeedSave(draft = SocialDraft(), saved = fromEmptyRow))
    }

    /**
     * The field-order guard. [SocialDraft.value] and the row mapping disagree on
     * argument order with the repository's writer, which is the whole reason the
     * draft exists — this asserts the mapping itself lands each value on its own
     * platform rather than one seat over.
     */
    @Test
    fun socialDraft_mapsEachColumnToItsOwnPlatform() {
        val draft = socialDraftOf(
            spotify = "https://open.spotify.com/artist/x",
            instagram = "@kaavya",
            youtube = "https://youtube.com/@kaavya",
        )

        assertEquals("https://open.spotify.com/artist/x", draft.value(SocialPlatform.Spotify))
        assertEquals("@kaavya", draft.value(SocialPlatform.Instagram))
        assertEquals("https://youtube.com/@kaavya", draft.value(SocialPlatform.YouTube))
    }

    @Test
    fun socialDraft_withEditsOnlyTheOnePlatform() {
        val edited = linked.with(SocialPlatform.Instagram, "@kaavyalive")

        assertEquals("@kaavyalive", edited.instagram)
        assertEquals(linked.spotify, edited.spotify)
        assertEquals(linked.youtube, edited.youtube)
    }

    @Test
    fun socialSave_isSkippedWhenNothingActuallyChanged() {
        assertFalse(socialsNeedSave(draft = linked, saved = linked))
        assertFalse(socialsNeedSave(draft = SocialDraft(), saved = SocialDraft()))
    }

    /** Same reason as the bio's: the write trims, so whitespace is not a change. */
    @Test
    fun socialSave_ignoresWhitespaceTheWriteWouldHaveTrimmedAnyway() {
        val padded = linked.copy(instagram = "  @kaavya  ")

        assertFalse(socialsNeedSave(draft = padded, saved = linked))
    }

    /**
     * Clearing a field IS the unlink affordance, so it has to read as a change —
     * if it did not, an artist could never remove an account.
     */
    @Test
    fun socialSave_firesOnAnyOneOfTheThree_includingClearingIt() {
        assertTrue(socialsNeedSave(draft = linked.copy(spotify = ""), saved = linked))
        assertTrue(socialsNeedSave(draft = linked.copy(instagram = "@other"), saved = linked))
        assertTrue(socialsNeedSave(draft = linked.copy(youtube = ""), saved = linked))
    }

    @Test
    fun socialDraft_countsBlankFieldsAsUnlinked() {
        assertEquals(0, SocialDraft().linkedCount)
        assertEquals(3, linked.linkedCount)
        assertEquals(2, linked.copy(youtube = "   ").linkedCount)
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

    // ── Sample titles ────────────────────────────────────────────────────────

    /**
     * The bug this rule exists for. `OpenDocument()` hands back a document URI
     * whose last path segment is a provider id, and the editor used to publish it
     * verbatim as the clip's title — on the artist's PUBLIC profile, with no
     * rename anywhere on the screen to correct it. Only `DISPLAY_NAME` is a name,
     * and when the provider won't give one the fallback has to be neutral rather
     * than plausible-looking garbage.
     */
    @Test
    fun sampleTitle_fallsBackWhenTheProviderHasNoNameToGive() {
        assertEquals("Sample", sampleTitleFrom(null))
        assertEquals("Sample", sampleTitleFrom(""))
        assertEquals("Sample", sampleTitleFrom("   "))
        // A name that is nothing but an extension leaves nothing behind either.
        assertEquals("Sample", sampleTitleFrom(".mp3"))
    }

    @Test
    fun sampleTitle_dropsTheExtensionAndTrims() {
        assertEquals("Rooftop set", sampleTitleFrom("  Rooftop set.mp3  "))
        assertEquals("mix.final", sampleTitleFrom("mix.final.wav"))
    }

    /**
     * `substringBeforeLast` returns the WHOLE string when there is no '.', which
     * is exactly how a document id used to survive the old derivation intact. A
     * name with no extension is still a name, so it passes through — the fix is
     * upstream, in asking the provider for a name at all.
     */
    @Test
    fun sampleTitle_keepsANameThatHasNoExtension() {
        assertEquals("Rooftop set", sampleTitleFrom("Rooftop set"))
    }

    // ── Upload queue failures ────────────────────────────────────────────────

    private fun audioTask(id: String = "a-1") = UploadQueue.Task.AudioSample(
        id = id,
        artistId = ARTIST_ID,
        filePath = "/tmp/$id.m4a",
        title = "Rooftop set",
        durationSeconds = 90.0,
    )

    private fun photoTask(id: String = "p-1") = UploadQueue.Task.CoverPhoto(
        id = id,
        artistId = ARTIST_ID,
        filePath = "/tmp/$id.jpg",
    )

    /**
     * The queue is shared with the wizard, which puts the cover photo through it on
     * publish. Reporting every failure as "a sample didn't upload" would send the
     * artist to the samples section over a photo, so the message names the kind.
     */
    @Test
    fun failedUpload_namesTheClipWhenAClipStalled() {
        assertEquals(
            "A sample didn't finish uploading — check your connection and try again.",
            failedUploadMessage(listOf(audioTask())),
        )
        assertEquals(
            "2 samples didn't finish uploading — check your connection and try again.",
            failedUploadMessage(listOf(audioTask("a-1"), audioTask("a-2"))),
        )
    }

    /**
     * The regression this replaced: a cover photo that burned its three attempts was
     * counted as zero failed samples, so the profile went live with no cover, no
     * banner, and a staged file kept on disk for a retry nobody could request.
     */
    @Test
    fun failedUpload_reportsTheWizardsCoverPhoto() {
        assertEquals(
            "Your cover photo didn't finish uploading — check your connection and try again.",
            failedUploadMessage(listOf(photoTask())),
        )
    }

    @Test
    fun failedUpload_staysVagueWhenBothKindsStalled() {
        assertEquals(
            "Some of your uploads didn't finish — check your connection and try again.",
            failedUploadMessage(listOf(photoTask(), audioTask())),
        )
    }

    @Test
    fun failedUpload_isSilentWhenNothingHasGivenUp() {
        assertNull(failedUploadMessage(emptyList()))
    }

    // ── Save guard ───────────────────────────────────────────────────────────

    /**
     * The false-banner bug. Every debounced save is launched into a Job the NEXT
     * edit cancels, and `runCatching` catches Throwable — so tapping a second
     * preset chip routed the first save's cancellation into the failure path and
     * told the artist their tech rider hadn't saved, over a write that was about
     * to succeed and had already been replaced.
     */
    @Test
    fun saveCatching_rethrowsACancellationRatherThanReportingIt() = runTest {
        var reported = false

        val thrown = runCatching {
            saveCatching { throw CancellationException("replaced by a newer edit") }
                .onFailure { reported = true }
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
        assertFalse("a cancelled save must not reach the failure path", reported)
    }

    @Test
    fun saveCatching_stillReportsARealFailure() = runTest {
        val result = saveCatching { throw IllegalStateException("offline") }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun saveCatching_passesTheValueThroughOnSuccess() = runTest {
        assertEquals("saved", saveCatching { "saved" }.getOrNull())
    }

    // ── Removing a sample ────────────────────────────────────────────────────

    /**
     * `deleteSample` used to pass null here, and the repository's storage delete
     * is guarded by `storagePathFromUrl(...) ?: return` — so every removed clip
     * kept its object in the PUBLIC `artist-samples` bucket, still playable by
     * anyone holding the link. These pin the two halves of that fix: null really
     * was a guaranteed no-op, and the `audioUrl` now passed instead really does
     * resolve to the bucket path.
     */
    @Test
    fun deletingASample_withNoUrlCouldNeverHaveRemovedTheObject() {
        assertNull(SupabaseSamplesRepository.storagePathFromUrl(null))
        assertNull(SupabaseSamplesRepository.storagePathFromUrl(""))
    }

    @Test
    fun deletingASample_resolvesTheStoredAudioUrlBackToItsBucketPath() {
        val sample = Sample(
            id = "s-1",
            title = "Rooftop set",
            duration = "1:30",
            audioUrl = "https://xyz.supabase.co/storage/v1/object/public/artist-samples/$ARTIST_ID/s-1.m4a",
        )

        assertEquals(
            "$ARTIST_ID/s-1.m4a",
            SupabaseSamplesRepository.storagePathFromUrl(sample.audioUrl),
        )
    }
}
