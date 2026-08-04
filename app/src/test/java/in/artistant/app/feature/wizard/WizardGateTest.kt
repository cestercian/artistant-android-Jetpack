package `in`.artistant.app.feature.wizard

import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.testsupport.ARTIST_ID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wizard's per-step gate and the publish payload it hands the repository.
 *
 * `canAdvance` is what stands between a half-filled form and a live artist
 * profile — every early-exit here is a row a client would otherwise see with a
 * blank stage name or a ₹0 package. The step machine itself already has a test;
 * this file covers the gate + the draft mapping.
 */
class WizardGateTest {

    private fun state(
        step: WizardStep,
        stageName: String = "Nova",
        handle: String = "nova",
        category: String = "DJ",
        baseCity: String = "Bangalore",
        packageName: String = "Evening set",
        packageDuration: String = "2h",
        packagePrice: String = "25000",
        bio: String = "Rooftop sets",
    ) = WizardUiState(
        step = step,
        stageName = stageName,
        handle = handle,
        category = category,
        baseCity = baseCity,
        packageName = packageName,
        packageDuration = packageDuration,
        packagePrice = packagePrice,
        bio = bio,
    )

    // --- Identity -----------------------------------------------------------

    @Test
    fun identityNeedsAStageNameHandleAndCategory() {
        assertTrue(state(WizardStep.Identity).canAdvance)
        assertFalse(state(WizardStep.Identity, stageName = "").canAdvance)
        assertFalse(state(WizardStep.Identity, stageName = "   ").canAdvance)
        assertFalse(state(WizardStep.Identity, handle = "").canAdvance)
        assertFalse(state(WizardStep.Identity, category = "").canAdvance)
    }

    // --- Location -----------------------------------------------------------

    @Test
    fun locationNeedsABaseCity() {
        assertTrue(state(WizardStep.Location).canAdvance)
        assertFalse(state(WizardStep.Location, baseCity = "").canAdvance)
        assertFalse(state(WizardStep.Location, baseCity = "  ").canAdvance)
    }

    // --- Pricing ------------------------------------------------------------

    @Test
    fun pricingNeedsANamedPricedTier() {
        assertTrue(state(WizardStep.Pricing).canAdvance)
        assertFalse(state(WizardStep.Pricing, packageName = "").canAdvance)
        assertFalse(state(WizardStep.Pricing, packageDuration = "").canAdvance)
    }

    @Test
    fun pricingRejectsZeroNegativeAndNonNumericPrices() {
        assertFalse(state(WizardStep.Pricing, packagePrice = "0").canAdvance)
        assertFalse(state(WizardStep.Pricing, packagePrice = "-500").canAdvance)
        assertFalse(state(WizardStep.Pricing, packagePrice = "").canAdvance)
        assertFalse(state(WizardStep.Pricing, packagePrice = "twenty thousand").canAdvance)
        assertTrue(state(WizardStep.Pricing, packagePrice = "1").canAdvance)
    }

    // --- Bio ----------------------------------------------------------------

    @Test
    fun bioCapsAt200Chars() {
        assertTrue(state(WizardStep.Bio, bio = "x".repeat(200)).canAdvance)
        assertFalse(state(WizardStep.Bio, bio = "x".repeat(201)).canAdvance)
        assertTrue(state(WizardStep.Bio, bio = "").canAdvance) // bio is optional
    }

    // --- optional steps -----------------------------------------------------

    @Test
    fun techCoverSocialsAvailabilitySamplesAndPreviewAreAlwaysPassable() {
        listOf(
            WizardStep.Tech,
            WizardStep.Availability,
            WizardStep.Cover,
            WizardStep.Socials,
            WizardStep.Samples,
            WizardStep.Preview,
        ).forEach { step ->
            assertTrue("$step should be optional", state(step).canAdvance)
        }
    }

    @Test
    fun doneIsATerminalStep() {
        assertFalse(state(WizardStep.Done).canAdvance)
        assertNull(advanceWizardStep(WizardStep.Done))
    }

    // --- progress -----------------------------------------------------------

    @Test
    fun progressCountsEveryFormStepAndExcludesDone() {
        assertEquals(WizardFlowOrder.size - 1, wizardProgressTotal())
        assertEquals(0, wizardProgressIndex(WizardStep.Identity))
        assertEquals(wizardProgressTotal() - 1, wizardProgressIndex(WizardStep.Preview))
        assertNull(wizardProgressIndex(WizardStep.Done))
    }

    // --- toggles ------------------------------------------------------------

    @Test
    fun toggleInSetAddsThenRemoves() {
        val once = toggleInSet("Fri", emptySet())
        assertEquals(setOf("Fri"), once)
        assertEquals(emptySet<String>(), toggleInSet("Fri", once))
    }

    // --- publish payload ----------------------------------------------------

    @Test
    fun publishDraftCarriesTheFormThrough() {
        val ui = WizardUiState(
            stageName = "Nova",
            handle = "nova",
            category = "DJ",
            genre = "Electronic",
            baseCity = "Bangalore",
            bio = "Rooftop sets",
            coverGradientIndex = 3,
            daysAvailable = setOf("Fri", "Sat"),
            timeSlots = setOf("9:00 PM"),
            instagramHandle = "novabeats",
            spotifyArtistUrl = "https://open.spotify.com/artist/x",
            youtubeChannelUrl = "https://youtube.com/@nova",
        )

        val draft = buildWizardProfileDraft(ui, ARTIST_ID)

        assertEquals(ARTIST_ID, draft.artistId)
        assertEquals("Nova", draft.stageName)
        assertEquals("nova", draft.handle)
        assertEquals("DJ", draft.category)
        assertEquals("Electronic", draft.genre)
        assertEquals("Bangalore", draft.baseCity)
        assertEquals(3, draft.coverGradientIndex)
        assertEquals(setOf("Fri", "Sat"), draft.daysAvailable.toSet())
        assertEquals(listOf("9:00 PM"), draft.timeSlots)
        assertEquals("novabeats", draft.instagramHandle)
    }

    @Test
    fun anArtistWhoPicksNoSlotsStillPublishesWithADefaultPair() {
        // Discover filters on time slots — publishing an empty list would make
        // the artist invisible to a slot-filtered search.
        val draft = buildWizardProfileDraft(WizardUiState(timeSlots = emptySet()), ARTIST_ID)

        assertEquals(DefaultTimeSlots.take(2), draft.timeSlots)
    }

    @Test
    fun previewShowsTheTypedPackageAsThePopularTier() {
        val ui = WizardUiState(packageName = "Late set", packageDuration = "90m", packagePrice = "18000")

        val preview = ui.previewPackages.single()

        assertEquals("Late set", preview.name)
        assertEquals("90m", preview.duration)
        assertEquals(18_000, preview.price)
        assertTrue(preview.popular)
    }

    @Test
    fun anUnparseablePriceRendersAsZeroInThePreview_ratherThanCrashing() {
        assertEquals(0, WizardUiState(packagePrice = "abc").previewPackages.single().price)
    }
}
