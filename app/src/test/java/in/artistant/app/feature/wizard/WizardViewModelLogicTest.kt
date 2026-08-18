package `in`.artistant.app.feature.wizard

import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.testsupport.ARTIST_ID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
