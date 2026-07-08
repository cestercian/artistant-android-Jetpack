package `in`.artistant.app.feature.wizard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of the wizard port: step nav over the flow order + the per-step validation
 * gates. No ViewModel/Compose/Context — exactly what the M5b DoD's "advance/back over flowOrder,
 * per-step validation gates" asks for.
 */
class WizardStepTest {

    // --- Step navigation ------------------------------------------------

    @Test
    fun `advance walks the full flow order and clamps at Done`() {
        var step = WizardStep.Identity
        val walked = mutableListOf(step)
        repeat(WIZARD_FLOW.size + 2) { // over-run to prove clamping
            step = advanceWizard(step)
            if (walked.last() != step) walked.add(step)
        }
        assertEquals(WIZARD_FLOW, walked)
        assertEquals(WizardStep.Done, advanceWizard(WizardStep.Done)) // clamps
    }

    @Test
    fun `back walks the flow order in reverse and clamps at Identity`() {
        assertEquals(WizardStep.Samples, backWizard(WizardStep.Preview))
        assertEquals(WizardStep.Availability, backWizard(WizardStep.Cover))
        assertEquals(WizardStep.Identity, backWizard(WizardStep.Identity)) // clamps
    }

    @Test
    fun `flow order is the 11 steps ending identity to done`() {
        assertEquals(11, WIZARD_FLOW.size)
        assertEquals(WizardStep.Identity, WIZARD_FLOW.first())
        assertEquals(WizardStep.Done, WIZARD_FLOW.last())
    }

    // --- Validation gates -----------------------------------------------

    @Test
    fun `identity needs stage name, a 3-char available handle, and a category`() {
        assertFalse(WizardValidation.identity("", 5, true, "DJ")) // no stage name
        assertFalse(WizardValidation.identity("Kaavya", 2, true, "DJ")) // handle too short
        assertFalse(WizardValidation.identity("Kaavya", 5, false, "DJ")) // handle not available
        assertFalse(WizardValidation.identity("Kaavya", 5, true, "")) // no category
        assertTrue(WizardValidation.identity("Kaavya", 5, true, "DJ"))
    }

    @Test
    fun `location needs a base city`() {
        assertFalse(WizardValidation.location(""))
        assertTrue(WizardValidation.location("Bangalore"))
    }

    @Test
    fun `pricing needs at least one tier, all named and priced at or above 1000`() {
        assertFalse(WizardValidation.pricing(emptyList()))
        assertFalse(WizardValidation.pricing(listOf(PricingTier(name = "Set", duration = "1h", price = 500, popular = false))))
        assertFalse(WizardValidation.pricing(listOf(PricingTier(name = "", duration = "1h", price = 5000, popular = false))))
        assertTrue(WizardValidation.pricing(listOf(PricingTier(name = "Set", duration = "1h", price = 1000, popular = false))))
    }

    @Test
    fun `availability needs at least one day`() {
        assertFalse(WizardValidation.availability(emptySet()))
        assertTrue(WizardValidation.availability(setOf("Fri")))
    }

    @Test
    fun `bio caps at 200 chars`() {
        assertTrue(WizardValidation.bio("a".repeat(200)))
        assertFalse(WizardValidation.bio("a".repeat(201)))
    }

    @Test
    fun `samples cap at 6`() {
        assertTrue(WizardValidation.samples(6))
        assertFalse(WizardValidation.samples(7))
    }

    @Test
    fun `tech, cover, socials are always valid (optional)`() {
        assertTrue(WizardValidation.tech())
        assertTrue(WizardValidation.cover())
        assertTrue(WizardValidation.socials())
    }

    @Test
    fun `starter packages are seeded per category and all pass the pricing gate`() {
        listOf("DJ", "Stand-up", "Acoustic", "Singer", "Indie Band", "Magician", "Host").forEach { cat ->
            val tiers = WizardConstants.starterPackages(cat)
            assertTrue("$cat should seed tiers", tiers.isNotEmpty())
            assertTrue("$cat tiers should be publishable", WizardValidation.pricing(tiers))
        }
    }

    @Test
    fun `sortedTimeSlots returns chronological order, not lexicographic`() {
        val picked = setOf("10:00 PM", "6:00 PM", "9:00 PM")
        assertEquals(listOf("6:00 PM", "9:00 PM", "10:00 PM"), WizardConstants.sortedTimeSlots(picked))
    }
}
