package `in`.artistant.app.feature.wizard

import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.feature.epk.PackageRow
import `in`.artistant.app.testsupport.ARTIST_ID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wizard's per-step gate and the publish payload it hands the repository.
 *
 * `canAdvance` is what stands between a half-filled form and a live artist
 * profile — every early exit here is a row a client would otherwise see with a
 * blank stage name, a ₹0 tier, or an availability grid nobody set. The step
 * machine has its own file; this one covers the gate, the copy that depends on
 * it, and the draft mapping.
 */
class WizardGateTest {

    private fun state(
        step: WizardStep,
        stageName: String = "Nova",
        handle: String = "nova",
        handleStatus: WizardHandleStatus = WizardHandleStatus.Available,
        category: String = "DJ",
        baseCity: String = "Bangalore",
        packageRows: List<PackageRow> = listOf(PackageRow("k", "Evening set", "2h", "25000")),
        techItems: List<String> = listOf("1 DI box"),
        daysAvailable: Set<String> = setOf("Fri"),
        timeSlots: Set<String> = setOf("9:00 PM"),
        bio: String = "Rooftop sets",
    ) = WizardUiState(
        step = step,
        stageName = stageName,
        handle = handle,
        handleStatus = handleStatus,
        category = category,
        baseCity = baseCity,
        packageRows = packageRows,
        techItems = techItems,
        daysAvailable = daysAvailable,
        timeSlots = timeSlots,
        bio = bio,
    )

    // --- Identity ------------------------------------------------------------

    @Test
    fun `identity needs a stage name, a well-formed handle and a category`() {
        assertTrue(state(WizardStep.Identity).canAdvance)
        assertFalse(state(WizardStep.Identity, stageName = "").canAdvance)
        assertFalse(state(WizardStep.Identity, stageName = "   ").canAdvance)
        assertFalse(state(WizardStep.Identity, handle = "").canAdvance)
        assertFalse(state(WizardStep.Identity, category = "").canAdvance)
        // Two characters fails the 3–24 format rule before any round-trip.
        assertFalse(state(WizardStep.Identity, handle = "ab").canAdvance)
    }

    @Test
    fun `a handle still being checked blocks, and a failed check does not`() {
        // Advancing mid-flight would let a taken handle through and fail at
        // publish, nine steps later, with nothing on screen explaining why.
        assertFalse(state(WizardStep.Identity, handleStatus = WizardHandleStatus.Checking).canAdvance)
        assertFalse(state(WizardStep.Identity, handleStatus = WizardHandleStatus.Taken).canAdvance)
        // A network blip is not evidence that someone owns the handle; the
        // unique constraint on the column is the real backstop.
        assertTrue(state(WizardStep.Identity, handleStatus = WizardHandleStatus.Error).canAdvance)
    }

    @Test
    fun `the handle field only speaks up when it is blocking`() {
        assertEquals("That handle's taken — try another.", wizardHandleHint(WizardHandleStatus.Taken))
        assertNull(wizardHandleHint(WizardHandleStatus.Available))
        assertNull(wizardHandleHint(WizardHandleStatus.Checking))
        assertNull(wizardHandleHint(WizardHandleStatus.Error))
    }

    @Test
    fun `handle input drops characters the format would reject`() {
        assertEquals("novabeats", sanitizeHandleInput("Nova Beats!"))
        assertEquals("nova_1", sanitizeHandleInput("Nova-_1"))
        assertEquals(WizardHandleStatus.Empty, wizardHandleSyncStatus(""))
        assertEquals(WizardHandleStatus.Invalid, wizardHandleSyncStatus("ab"))
        assertEquals(WizardHandleStatus.Checking, wizardHandleSyncStatus("nova"))
    }

    // --- Location ------------------------------------------------------------

    @Test
    fun `location needs a base city`() {
        assertTrue(state(WizardStep.Location).canAdvance)
        assertFalse(state(WizardStep.Location, baseCity = "").canAdvance)
    }

    // --- Pricing -------------------------------------------------------------

    @Test
    fun `pricing needs at least one named, priced tier`() {
        assertTrue(state(WizardStep.Pricing).canAdvance)
        assertFalse(state(WizardStep.Pricing, packageRows = emptyList()).canAdvance)
        assertFalse(
            state(WizardStep.Pricing, packageRows = listOf(PackageRow("k", "", "2h", "25000"))).canAdvance,
        )
    }

    @Test
    fun `pricing rejects zero and unparseable prices`() {
        fun rows(price: String) = listOf(PackageRow("k", "Set", "2h", price))
        assertFalse(state(WizardStep.Pricing, packageRows = rows("0")).canAdvance)
        assertFalse(state(WizardStep.Pricing, packageRows = rows("")).canAdvance)
        assertFalse(state(WizardStep.Pricing, packageRows = rows("twenty thousand")).canAdvance)
        assertTrue(state(WizardStep.Pricing, packageRows = rows("1")).canAdvance)
    }

    @Test
    fun `one half-typed tier does not block a set that has a good one`() {
        val rows = listOf(
            PackageRow("a", "Warm-up", "90 min", "18000"),
            PackageRow("b", "", "", ""),
        )
        assertTrue(state(WizardStep.Pricing, packageRows = rows).canAdvance)
    }

    // --- Tech / availability -------------------------------------------------

    @Test
    fun `tech and availability are required, not optional`() {
        assertFalse(state(WizardStep.Tech, techItems = emptyList()).canAdvance)
        // A day with no time, or a time with no day, is not a schedule a client
        // can book against — both halves are required.
        assertFalse(state(WizardStep.Availability, daysAvailable = emptySet()).canAdvance)
        assertFalse(state(WizardStep.Availability, timeSlots = emptySet()).canAdvance)
        assertTrue(state(WizardStep.Availability).canAdvance)
    }

    // --- Optional steps ------------------------------------------------------

    @Test
    fun `cover, socials, bio and samples never block the flow`() {
        listOf(WizardStep.Cover, WizardStep.Socials, WizardStep.Bio, WizardStep.Samples).forEach { step ->
            assertTrue("$step should be optional", state(step, bio = "").canAdvance)
        }
    }

    @Test
    fun `an empty optional step says skip rather than continue`() {
        assertEquals("Skip for now", wizardCtaLabel(state(WizardStep.Bio, bio = "")))
        assertEquals("Continue", wizardCtaLabel(state(WizardStep.Bio, bio = "Rooftop sets")))
        assertEquals("Skip for now", wizardCtaLabel(state(WizardStep.Cover)))
        assertEquals("Skip for now", wizardCtaLabel(state(WizardStep.Samples)))
        assertEquals("Continue", wizardCtaLabel(state(WizardStep.Identity)))
    }

    @Test
    fun `the publish button narrates itself while it is working`() {
        val publishing = state(WizardStep.Preview).copy(isPublishing = true)
        assertEquals("Publishing…", wizardCtaLabel(publishing))
        assertEquals("Looks good, publish", wizardCtaLabel(state(WizardStep.Preview)))
        // A second tap during the round-trip would fire the whole sequence twice.
        assertFalse(publishing.canAdvance)
    }

    // --- Bio -----------------------------------------------------------------

    @Test
    fun `bio caps at 200 characters and warns before it gets there`() {
        assertTrue(state(WizardStep.Bio, bio = "x".repeat(200)).canAdvance)
        assertEquals(WizardCounterTone.Quiet, bioCounterTone(0))
        assertEquals(WizardCounterTone.Quiet, bioCounterTone(179))
        assertEquals(WizardCounterTone.Warn, bioCounterTone(181))
        assertEquals(WizardCounterTone.Over, bioCounterTone(200))
        // Clamping happens on the way in, so the field can never hold more.
        assertEquals(200, clampBio("x".repeat(1000)).length)
    }

    // --- Step machine --------------------------------------------------------

    @Test
    fun `done is a terminal step`() {
        assertFalse(state(WizardStep.Done).canAdvance)
        assertNull(advanceWizardStep(WizardStep.Done))
    }

    @Test
    fun `progress counts every form step and excludes done`() {
        assertEquals(WizardFlowOrder.size - 1, wizardProgressTotal())
        assertEquals(0, wizardProgressIndex(WizardStep.Identity))
        assertEquals(wizardProgressTotal() - 1, wizardProgressIndex(WizardStep.Preview))
        assertNull(wizardProgressIndex(WizardStep.Done))
    }

    @Test
    fun `an unknown step name resolves to null rather than to the first step`() {
        // Silently landing on Identity would make a typo'd launch argument look
        // like it worked.
        assertEquals(WizardStep.Bio, wizardStepFromName("bio"))
        assertEquals(WizardStep.Identity, wizardStepFromName("Identity"))
        assertNull(wizardStepFromName("nonsense"))
        assertNull(wizardStepFromName(""))
        assertNull(wizardStepFromName(null))
    }

    @Test
    fun `a draft never reopens on the celebration screen`() {
        // Found on device: publish clears the draft and then moves the step, so
        // the debounced writer landed afterwards and rewrote the draft it had
        // just deleted, with step=Done in it. The next launch opened the wizard
        // on "You're live" for a profile the artist had not published. The
        // writer now skips Done; this is the second guard, on the read side.
        assertEquals(WizardStep.Preview, wizardResumeStep("Done"))
        assertEquals(WizardStep.Pricing, wizardResumeStep("Pricing"))
        assertEquals(WizardStep.Identity, wizardResumeStep(null))
        assertEquals(WizardStep.Identity, wizardResumeStep("nonsense"))
    }

    @Test
    fun `toggleInSet adds then removes`() {
        val once = toggleInSet("Fri", emptySet())
        assertEquals(setOf("Fri"), once)
        assertEquals(emptySet<String>(), toggleInSet("Fri", once))
    }

    // --- Save & exit ---------------------------------------------------------

    @Test
    fun `save and exit refuses to write while the form is still restoring`() {
        // Save & exit lives in the top bar, which is NOT behind the restore
        // spinner, and until the restore lands the state behind it is the
        // constructor default — Identity, blank name and handle, no tiers, no
        // media references. Writing that replaces the artist's real draft with a
        // blank one and signs them out; on the next sign-in the blank draft
        // restores, references nothing, and the orphan sweep deletes the staged
        // cover and every sample off disk. Unrecoverable, from one tap during a
        // profile read that has no timeout.
        assertFalse(wizardExitMaySaveDraft(WizardUiState()))
        assertFalse(wizardExitMaySaveDraft(state(WizardStep.Pricing).copy(isRestoring = true)))
    }

    @Test
    fun `save and exit writes the form once the restore has landed`() {
        assertTrue(wizardExitMaySaveDraft(state(WizardStep.Pricing).copy(isRestoring = false)))
    }

    // --- The debounced draft writer ------------------------------------------

    @Test
    fun `the writer refuses every state that would damage a draft`() {
        val form = state(WizardStep.Pricing).copy(isRestoring = false)
        assertTrue(wizardMaySaveDraft(form))
        assertFalse(
            "restoring: the form is still the blank default",
            wizardMaySaveDraft(form.copy(isRestoring = true)),
        )
        assertFalse(
            "publishing: publish is about to clear this draft",
            wizardMaySaveDraft(form.copy(isPublishing = true)),
        )
        assertFalse(
            "Done: a restore of it would open the wizard on 'You're live'",
            wizardMaySaveDraft(state(WizardStep.Done).copy(isRestoring = false)),
        )
    }

    @Test
    fun `a publish that lands inside the debounce window does not resurrect its draft`() = runTest {
        // The reason the refusals sit DOWNSTREAM of the debounce. Filtering
        // upstream meant a refused emission never reached the timer, so it could
        // neither restart nor supersede it: the last accepted snapshot was the
        // going-live one, its timer kept running through setPublished, the
        // enqueues and draftStore.clear() — a tail that usually finishes well
        // inside the window — and then fired, writing a full draft back into the
        // slot publish had just emptied.
        val restored = state(WizardStep.Preview, bio = "").copy(isRestoring = false)
        val edited = state(WizardStep.Preview, bio = "Rooftop sets").copy(isRestoring = false)

        val written = wizardDraftWrites(
            flow {
                emit(restored)
                emit(edited)
                delay(WIZARD_DRAFT_DEBOUNCE_MS + 1)
                // Publish starts, reaches go-live…
                emit(edited.copy(isPublishing = true, publishPhase = WizardPublishPhase.GoingLive))
                // …and the whole tail lands 50ms later, well inside the window.
                delay(50)
                emit(edited.copy(isPublishing = false, step = WizardStep.Done))
                delay(WIZARD_DRAFT_DEBOUNCE_MS + 1)
            },
        ).toList()

        // The edit is written; nothing from the publish window is.
        assertEquals(listOf("Rooftop sets"), written.map { it.bio })
        assertTrue(written.none { it.isPublishing || it.step == WizardStep.Done })
    }

    @Test
    fun `the first snapshot after a restore is not written back`() = runTest {
        // It is the state `restore()` just built out of the stored draft, so
        // writing it is pure churn — and it is the one snapshot that could land
        // before the artist has touched anything.
        val restored = state(WizardStep.Pricing).copy(isRestoring = false)

        val written = wizardDraftWrites(
            flow {
                emit(state(WizardStep.Pricing))          // still restoring
                emit(restored)
                delay(WIZARD_DRAFT_DEBOUNCE_MS + 1)
            },
        ).toList()

        assertEquals(emptyList<WizardUiState>(), written)
    }

    // --- The step lock during a publish --------------------------------------

    @Test
    fun `nothing moves between steps while a publish is in flight`() {
        // publish() snapshots the form before three round trips, so an edit made
        // during that window reaches neither the server nor the draft — the
        // successful publish clears it — and the publish ends by forcing the step
        // to Done, yanking the artist off whatever they had opened. The CTA was
        // already gated on this; the preview's EDIT jumps and system Back were
        // not, and they are the two ways off the Preview step.
        val preview = state(WizardStep.Preview)
        assertTrue(wizardMayChangeStep(preview))
        assertFalse(wizardMayChangeStep(preview.copy(isPublishing = true)))
        assertFalse(preview.copy(isPublishing = true).canAdvance)
    }

    // --- Publish payload -----------------------------------------------------

    @Test
    fun `the publish draft carries the form through`() {
        val ui = WizardUiState(
            stageName = "  Nova  ",
            handle = "Nova",
            category = "DJ",
            genre = "Electronic",
            baseCity = "Bangalore",
            bio = "Rooftop sets",
            coverGradientIndex = 3,
            daysAvailable = setOf("Sat", "Fri"),
            timeSlots = setOf("9:00 PM"),
            instagramHandle = "novabeats",
            spotifyArtistUrl = "https://open.spotify.com/artist/x",
            youtubeChannelUrl = "https://youtube.com/@nova",
        )

        val draft = buildWizardProfileDraft(ui, ARTIST_ID)

        assertEquals(ARTIST_ID, draft.artistId)
        assertEquals("Nova", draft.stageName)
        // Normalised, not passed through: the handle is a lookup key.
        assertEquals("nova", draft.handle)
        assertEquals("DJ", draft.category)
        assertEquals("Bangalore", draft.baseCity)
        assertEquals(3, draft.coverGradientIndex)
        assertEquals(listOf("9:00 PM"), draft.timeSlots)
        assertEquals("novabeats", draft.instagramHandle)
    }

    @Test
    fun `skipped socials reach the server as null, not as empty strings`() {
        // The profile renderer decides whether to show a social row by testing
        // for null; a literal "" renders an empty link.
        val draft = buildWizardProfileDraft(
            WizardUiState(instagramHandle = "", spotifyArtistUrl = "   ", youtubeChannelUrl = "x"),
            ARTIST_ID,
        )
        assertNull(draft.instagramHandle)
        assertNull(draft.spotifyArtistUrl)
        assertEquals("x", draft.youtubeChannelUrl)
    }

    @Test
    fun `days and slots are stored in calendar order, not hash order`() {
        // A Set has no order, and sorting the raw strings would file "10:00 PM"
        // before "6:00 PM". The server row and the booking grid have to agree.
        val draft = buildWizardProfileDraft(
            WizardUiState(
                daysAvailable = setOf("Sun", "Mon", "Fri"),
                timeSlots = setOf("10:00 PM", "6:00 PM"),
            ),
            ARTIST_ID,
        )
        assertEquals(listOf("Mon", "Fri", "Sun"), draft.daysAvailable)
        assertEquals(listOf("6:00 PM", "10:00 PM"), draft.timeSlots)
    }

    @Test
    fun `an artist who picks no slots still publishes with a default pair`() {
        // Search filters on time slots — publishing an empty list would make the
        // artist invisible to a slot-filtered query.
        val draft = buildWizardProfileDraft(WizardUiState(timeSlots = emptySet()), ARTIST_ID)

        assertEquals(DefaultTimeSlots.take(2), draft.timeSlots)
    }

    // --- Starter tiers -------------------------------------------------------

    @Test
    fun `starter tiers flag exactly one row popular`() {
        // The badge is a comparison. Flagging all of them, or the single tier a
        // one-row artist publishes, badges every profile in the app and the
        // signal dies.
        WizardCategories.forEach { category ->
            val rows = starterPackageRows(category)
            assertTrue("$category should seed tiers", rows.isNotEmpty())
            assertEquals("$category should flag one tier", 1, rows.count { it.popular })
        }
    }

    @Test
    fun `an unknown category still seeds tiers rather than an empty form`() {
        assertTrue(starterPackageRows("Beatboxer").isNotEmpty())
    }

    // --- Filled-ness ---------------------------------------------------------

    @Test
    fun `the preview can tell a skipped optional step from a filled one`() {
        val bare = WizardUiState()
        assertFalse(wizardStepIsFilled(bare, WizardStep.Socials))
        assertFalse(wizardStepIsFilled(bare, WizardStep.Bio))
        assertTrue(wizardStepIsFilled(bare.copy(youtubeChannelUrl = "x"), WizardStep.Socials))
        assertTrue(wizardStepIsFilled(bare.copy(bio = "hi"), WizardStep.Bio))
    }

    // --- Preview derivation --------------------------------------------------

    @Test
    fun `the preview shows savable tiers and never self-declares one popular`() {
        val ui = WizardUiState(
            packageRows = listOf(
                PackageRow("a", "Late set", "90m", "18000"),
                PackageRow("b", "", "", ""),
            ),
        )

        val preview = ui.previewPackages.single()

        assertEquals("Late set", preview.name)
        assertEquals(18_000, preview.price)
        assertFalse(preview.popular)
    }
}
