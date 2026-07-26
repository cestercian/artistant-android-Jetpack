package `in`.artistant.app.feature.wizard

import `in`.artistant.app.data.repository.FakeArtistsRepository
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

    @Test
    fun publishWizardProfile_setsSetupCompleteOnFake() = runTest {
        val repo = FakeArtistsRepository()
        val artistId = "11111111-1111-1111-1111-111111111111"
        val draft = buildWizardProfileDraft(
            WizardUiState(
                stageName = "Nova",
                handle = "nova",
                category = "DJ",
                baseCity = "Bangalore",
                bio = "Rooftop sets",
            ),
            artistId,
        )
        assertFalse(repo.setupComplete)
        repo.publishWizardProfile(draft)
        assertTrue(repo.setupComplete)
        assertEquals("nova", repo.lastPublishedDraft?.handle)
    }
}
