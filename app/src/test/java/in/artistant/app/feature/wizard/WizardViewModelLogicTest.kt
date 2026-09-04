package `in`.artistant.app.feature.wizard

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.domain.artist.ServiceTags
import `in`.artistant.app.testsupport.ARTIST_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardViewModelLogicTest {

    @Test
    fun advanceWizardStep_followsIosFlowOrder() {
        var step = WizardStep.Identity
        val expected = listOf(
            WizardStep.Location,
            WizardStep.Pricing,
            WizardStep.Tech,
            WizardStep.Availability,
            WizardStep.Cover,
            WizardStep.Socials,
            WizardStep.Bio,
            WizardStep.Samples,
            WizardStep.Preview,
            WizardStep.Done,
        )
        expected.forEach { next ->
            assertEquals(next, advanceWizardStep(step))
            step = next
        }
        assertEquals(null, advanceWizardStep(WizardStep.Done))
    }

    @Test
    fun backWizardStep_returnsPreviousStep() {
        assertEquals(WizardStep.Pricing, backWizardStep(WizardStep.Tech))
        assertEquals(null, backWizardStep(WizardStep.Identity))
    }

    private fun publishDraft(artistId: String = ARTIST_ID) = buildWizardProfileDraft(
        WizardUiState(
            stageName = "Nova",
            handle = "nova",
            category = "DJ",
            baseCity = "Bangalore",
            bio = "Rooftop sets",
        ),
        artistId,
    )

    @Test
    fun publishWizardProfile_savesTheRowWithoutClaimingSetupIsDone() = runTest {
        // Publish is three non-atomic calls: row upsert, packages + tech, go-live.
        // A drop after the first one used to leave the server marked finished but
        // unpublished — past the wizard gate (RootViewModel routes on
        // artistSetupComplete), invisible in Discover, and no control in the app
        // can flip `published` back on. Landing the row must stay reversible.
        val repo = FakeArtistsRepository()

        repo.publishWizardProfile(publishDraft())

        assertEquals("nova", repo.lastPublishedDraft?.handle)
        assertFalse(repo.setupComplete)
        assertFalse(repo.published)
    }

    @Test
    fun setPublished_marksTheArtistLiveAndFinishedInOneWrite() = runTest {
        val repo = FakeArtistsRepository()
        repo.publishWizardProfile(publishDraft())

        repo.setPublished(ARTIST_ID, published = true)

        assertTrue(repo.published)
        assertTrue(repo.setupComplete)
    }

    // ── Service tags: a whole-set column the wizard does not own alone ────────

    @Test
    fun serviceTagsToPublish_mergesWhenThePickerNeverSawTheRow() {
        // Wizard re-entry: the artist already publishes two services from the
        // press-kit editor, the picker opened empty, and they ticked a third.
        // Sending the picker's list alone would un-publish the other two — and
        // the same slugs back Discover's services filter, so it also makes them
        // unfindable for the work they actually do.
        val tags = wizardServiceTagsToPublish(
            picked = listOf("dj-set"),
            published = listOf("wedding-sangeet", "corporate-set"),
            seeded = false,
        )

        assertEquals(listOf("wedding-sangeet", "corporate-set", "dj-set"), tags)
    }

    @Test
    fun serviceTagsToPublish_sendsTheListAsWrittenOnceThePickerWasSeeded() {
        // Seeded means the chips ARE the published set, so an unticked chip is a
        // real withdrawal and must survive the write. Merging here would make
        // the untick do nothing, which reads as a broken control.
        val tags = wizardServiceTagsToPublish(
            picked = listOf("wedding-sangeet"),
            published = null,
            seeded = true,
        )

        assertEquals(listOf("wedding-sangeet"), tags)
    }

    @Test
    fun serviceTagsToPublish_writesNothingWhenTheRowCouldNotBeRead() {
        // Neither read landed, so there is no set to merge into. This session's
        // ticks are recoverable from the press-kit editor; a published service
        // deleted by a guess is not.
        assertNull(
            wizardServiceTagsToPublish(
                picked = listOf("dj-set"),
                published = null,
                seeded = false,
            ),
        )
    }

    @Test
    fun serviceTagsToPublish_neverSendsAnEmptyWholeSet() {
        // An empty array replaces the column with nothing. On a first run that is
        // a round trip that changes nothing; on a re-entry it is a deletion.
        assertNull(
            wizardServiceTagsToPublish(picked = emptyList(), published = null, seeded = true),
        )
        assertNull(
            wizardServiceTagsToPublish(
                picked = emptyList(),
                published = listOf("dj-set"),
                seeded = false,
            ),
        )
    }

    @Test
    fun publish_keepsAServiceTheWizardNeverShowed() = runTest {
        // The whole finding, end to end through the repository: a tag published
        // elsewhere is still on the row after a wizard publish that never
        // displayed it.
        val repo = FakeArtistsRepository(selfId = ARTIST_ID)
        repo.seedFull(
            listOf(
                FakeArtistsRepository.sample(id = ARTIST_ID)
                    .copy(serviceTags = listOf("wedding-sangeet")),
            ),
        )

        // What `publishServiceTags` does when the entry read failed: re-read,
        // then merge rather than replace.
        val published = repo.fetchArtist(ARTIST_ID)?.serviceTags.orEmpty()
        val tags = wizardServiceTagsToPublish(
            picked = listOf("dj-set"),
            published = published,
            seeded = false,
        )
        repo.updateServiceTags(ARTIST_ID, tags!!)

        assertEquals(
            listOf("wedding-sangeet", "dj-set"),
            repo.fetchArtist(ARTIST_ID)?.serviceTags,
        )
    }

    @Test
    fun serviceTagsToPublish_keepsATagTheWizardNeverShowed_throughTheToggleItself() {
        // The finding through the operation that caused it, not around it.
        // `ServiceTags.toggle` is exactly what a chip tap calls and
        // `wizardServiceTagsToPublish` is exactly what publish sends, so this is
        // the whole path: tick one service in a picker that opened empty, and the
        // two services already on the row — which this wizard never displayed —
        // are still published afterwards.
        val published = listOf("wedding-sangeet", "corporate-set")
        val picker = ServiceTags.toggle(emptyList(), "dj-set")

        val sent = wizardServiceTagsToPublish(picker, published, seeded = false)!!

        assertTrue("hidden tags survived", sent.containsAll(published))
        assertTrue("the new tick landed", sent.contains("dj-set"))
    }

    @Test
    fun serviceTagsToPublish_stillHonoursAnUntickOnceThePickerWasSeeded() {
        // The other half, and the reason merging is not simply always right: a
        // seeded picker's unticked chip is a real withdrawal. Merging here would
        // make the control do nothing, which reads as a broken checkbox.
        val published = listOf("wedding-sangeet", "corporate-set")
        val picker = ServiceTags.toggle(published, "corporate-set")

        assertEquals(
            listOf("wedding-sangeet"),
            wizardServiceTagsToPublish(picker, published, seeded = true),
        )
    }

    // ── A publish that throws must always hand the wizard back ───────────────

    @Test
    fun publishFailed_clearsTheInFlightFlagForAThrowableThatIsNotAnException() {
        // `isPublishing` is a lock: it disables the CTA, refuses every step
        // change and narrates "Publishing…". The old catch chain ended at
        // `Exception`, so a LinkageError off a bad OEM split — or an OOM while
        // the cover was being staged — left that lock on in a gate with no
        // screen behind it. Force-quitting was the only way out.
        val inFlight = WizardUiState(
            step = WizardStep.Preview,
            isPublishing = true,
            publishPhase = WizardPublishPhase.GoingLive,
        )

        val landed = wizardPublishFailed(inFlight, LinkageError("libartistant split missing"))

        assertFalse(landed.isPublishing)
        assertEquals(WizardPublishPhase.Idle, landed.publishPhase)
        assertEquals(WIZARD_PUBLISH_FAILED, landed.publishError)
        // And the wizard is genuinely usable again, not just flagged as such.
        assertTrue(wizardMayChangeStep(landed))
        assertTrue(landed.canAdvance)
    }

    @Test
    fun publishFailed_namesTheOneFailureTheArtistCanActOn() {
        // Ours are sentences; everything else is a stack message. A PostgREST
        // dump or an allocation figure in a Banner tells the artist nothing and
        // costs them the trust that the app knows what happened.
        assertEquals(
            "That handle is already taken.",
            wizardPublishFailureMessage(AppError.UniqueViolation),
        )
        assertEquals(
            AppError.GuardedWrite.message,
            wizardPublishFailureMessage(AppError.GuardedWrite),
        )
        assertEquals(
            WIZARD_PUBLISH_FAILED,
            wizardPublishFailureMessage(IllegalStateException("PGRST204 column not found")),
        )
        assertEquals(WIZARD_PUBLISH_FAILED, wizardPublishFailureMessage(OutOfMemoryError()))
    }
}
