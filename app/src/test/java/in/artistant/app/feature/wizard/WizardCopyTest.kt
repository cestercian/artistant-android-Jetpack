package `in`.artistant.app.feature.wizard

import `in`.artistant.app.domain.booking.BookingMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wizard's derived copy and arithmetic — the things the light redesign added
 * that a screenshot cannot catch.
 *
 * All four groups here exist because the design asks the step to STATE something
 * rather than just collect it: the pricing step states the host's total, the
 * availability step states the badge a search row will carry, the bio step
 * states what good looks like, and the bar states which step you are on. Each of
 * those is a sentence assembled from state, and a wrong one is a lie the artist
 * acts on — which is exactly the class of bug that survives a visual review.
 */
class WizardCopyTest {

    // --- Pricing: the seed band ---------------------------------------------

    @Test
    fun `the pricing band is derived from the tiers the category actually seeds`() {
        WizardCategories.forEach { category ->
            val band = pricingBandFor(category)
            val seeded = starterPackageRows(category).mapNotNull { it.price.toIntOrNull() }
            assertNotNull("$category seeds tiers, so it has a band", band)
            // The whole point of deriving rather than hand-writing the range: the
            // sentence under the tiers and the tiers themselves cannot disagree.
            assertEquals(seeded.min(), band!!.low)
            assertEquals(seeded.max(), band.high)
            assertEquals(seeded.size, band.tiers)
        }
    }

    @Test
    fun `an unpicked category has no band to quote`() {
        // Not a zero range. The identity step renders a different sentence when
        // there is nothing seeded yet, and "₹0–₹0" would be a number we invented.
        assertNull(pricingBandFor(""))
    }

    @Test
    fun `every category seeds a band inside its own tiers`() {
        val band = pricingBandFor("DJ")!!
        assertTrue(band.low <= band.high)
        assertTrue(band.tiers >= 1)
    }

    // --- Pricing: fee in, all-in out -----------------------------------------

    @Test
    fun `the all-in figure is the checkout's own arithmetic`() {
        // Not "about 25% on top" — the same platform-then-GST rounding the
        // booking math does, because the two numbers are shown to two people
        // about one gig.
        listOf(1, 14_000, 22_000, 36_000, 99_999).forEach { fee ->
            assertEquals(BookingMath.compute(fee).total, packageAllInInr(fee.toString()))
        }
    }

    @Test
    fun `a price that is not a number yet is not a free gig`() {
        // Null, not 0. A half-typed row would otherwise render "Host sees ₹0"
        // under a tier the artist is in the middle of writing.
        assertNull(packageAllInInr(""))
        assertNull(packageAllInInr("0"))
        assertNull(packageAllInInr("   "))
        assertNull(packageAllInInr("twenty thousand"))
    }

    @Test
    fun `a price typed with separators still resolves`() {
        assertEquals(BookingMath.compute(26_000).total, packageAllInInr("26,000"))
    }

    // --- Availability: one badge ---------------------------------------------

    @Test
    fun `a contiguous run of days is dashed`() {
        assertEquals(
            "Thu–Sun evenings",
            availabilityBadge(setOf("Thu", "Fri", "Sat", "Sun"), setOf("7:30 PM", "9:00 PM")),
        )
    }

    @Test
    fun `a scattered week is listed rather than dashed`() {
        // "Tue–Sat" would claim five days from three. The dash is a range
        // promise, so it is only allowed when the range really is contiguous.
        assertEquals(
            "Tue, Thu, Sat evenings",
            availabilityBadge(setOf("Sat", "Tue", "Thu"), setOf("9:00 PM")),
        )
    }

    @Test
    fun `a run that wraps the week end is not dashed`() {
        // Sat, Sun, Mon reads left to right as a five-day block if it is dashed.
        // The calendar week does not wrap on a profile the way it does in the
        // artist's head.
        assertEquals(
            "Mon, Sat, Sun evenings",
            availabilityBadge(setOf("Sat", "Sun", "Mon"), setOf("9:00 PM")),
        )
    }

    @Test
    fun `two adjacent days are named rather than dashed`() {
        // "Sat–Sun" and "Sat, Sun" are the same length and the second cannot be
        // misread as a longer range.
        assertEquals("Sat, Sun evenings", availabilityBadge(setOf("Sat", "Sun"), setOf("9:00 PM")))
    }

    @Test
    fun `all seven days collapse to a phrase`() {
        assertEquals(
            "Every day evenings",
            availabilityBadge(WizardWeekdays.toSet(), setOf("9:00 PM")),
        )
    }

    @Test
    fun `one day stands on its own`() {
        assertEquals("Fri evenings", availabilityBadge(setOf("Fri"), setOf("9:00 PM")))
    }

    @Test
    fun `the time half describes when, not which clock times`() {
        val days = setOf("Fri")
        // Everything at or after 10pm is a late-night act, not an evening one.
        assertEquals("Fri late nights", availabilityBadge(days, setOf("10:00 PM", "11:00 PM")))
        assertEquals("Fri evenings", availabilityBadge(days, setOf("6:00 PM", "9:00 PM")))
        // A slot before 5pm widens the phrase rather than being dropped from it.
        assertEquals(
            "Fri afternoons & evenings",
            availabilityBadge(days, setOf("2:00 PM", "9:00 PM")),
        )
        assertEquals("Fri afternoons", availabilityBadge(days, setOf("2:00 PM", "4:00 PM")))
    }

    @Test
    fun `both halves are required for a badge to exist`() {
        // No badge is a real outcome, and the step says so rather than drawing an
        // empty pill — an artist with days but no times has not made a schedule.
        assertNull(availabilityBadge(emptySet(), setOf("9:00 PM")))
        assertNull(availabilityBadge(setOf("Fri"), emptySet()))
        assertNull(availabilityBadge(emptySet(), emptySet()))
    }

    @Test
    fun `every stored slot parses to an hour`() {
        // The badge falls back to "evenings" for an unparseable slot, which would
        // silently mis-describe a late-night act. Our own vocabulary must never
        // take that path.
        WizardTimeSlots.forEach { slot ->
            assertNotNull("$slot should parse", slotHour24(slot))
        }
        assertEquals(0, slotHour24("12:00 AM"))
        assertEquals(12, slotHour24("12:00 PM"))
        assertEquals(23, slotHour24("11:00 PM"))
        assertNull(slotHour24("evening"))
    }

    // --- Bio guidance ---------------------------------------------------------

    @Test
    fun `bio guidance says something different at every stage of writing`() {
        val stages = listOf(0, 30, 100, 190, WIZARD_BIO_MAX).map(::bioGuidance)
        // Five lengths, five distinct sentences: a hint that repeats is a hint
        // the artist stops reading.
        assertEquals(stages.size, stages.distinct().size)
    }

    @Test
    fun `an empty bio is prompted, not scolded`() {
        assertTrue(bioGuidance(0).contains("Two sentences"))
        assertTrue(bioGuidance(0).contains("What you sound like"))
    }

    @Test
    fun `the ceiling says what to do about it`() {
        assertTrue(bioGuidance(WIZARD_BIO_MAX).contains("trim"))
        // And the counter has gone loud by then.
        assertEquals(WizardCounterTone.Over, bioCounterTone(WIZARD_BIO_MAX))
    }

    // --- The step bar ---------------------------------------------------------

    @Test
    fun `the counter is zero-padded so it cannot jitter under the track`() {
        // Ten, not the design's eleven: the design's flow has one step this app
        // does not, and the total is derived from WizardFlowOrder rather than
        // typed, so the bar, the counter and the sheet all count the same steps.
        assertEquals("01 / 10", wizardStepCounter(WizardStep.Identity))
        assertEquals("02 / 10", wizardStepCounter(WizardStep.Location))
        assertEquals("10 / 10", wizardStepCounter(WizardStep.Preview))
        // Done carries no segment, so it carries no counter.
        assertNull(wizardStepCounter(WizardStep.Done))
    }

    @Test
    fun `saved-so-far counts steps left behind, matching the filled segments`() {
        // Standing on step one, nothing is banked yet — and the bar under the
        // label draws zero filled cells, so the two must agree.
        assertEquals("0 of 10", wizardSavedSoFarLabel(WizardStep.Identity))
        assertEquals("6 of 10", wizardSavedSoFarLabel(WizardStep.Socials))
        assertEquals("9 of 10", wizardSavedSoFarLabel(WizardStep.Preview))
    }

    @Test
    fun `the footer note is an action only where the CTA has not already said it`() {
        fun at(step: WizardStep, bio: String = "") =
            WizardUiState(step = step, bio = bio)

        // Empty optional step: the button itself says Skip, so the line below
        // would be the same word twice.
        assertNull(wizardFooterNote(at(WizardStep.Bio)))
        assertEquals("Skip for now", wizardFooterNote(at(WizardStep.Bio, bio = "Rooftop sets")))
        assertTrue(wizardFooterNote(at(WizardStep.Pricing))!!.startsWith("Step 3 of 10"))
        assertEquals("You can keep editing after you publish", wizardFooterNote(at(WizardStep.Preview)))
        assertNull(wizardFooterNote(at(WizardStep.Identity)))
    }

    // --- The public address ---------------------------------------------------

    @Test
    fun `the address is only shown once there is a handle to show`() {
        assertEquals("artistant.in/@tiltcollective", wizardPublicAddress("tiltcollective"))
        // Not "artistant.in/@" — a half-formed URL reads as a rendering fault.
        assertNull(wizardPublicAddress(""))
        assertNull(wizardPublicAddress("   "))
    }

    // --- Travel radius --------------------------------------------------------

    @Test
    fun `the radius floor reads as a constraint rather than as zero kilometres`() {
        assertEquals("City only", travelRadiusLabel(0))
        assertEquals("Up to 150 km", travelRadiusLabel(150))
        // Every offered option has a label the chip row can render.
        WizardTravelRadii.forEach { assertTrue(travelRadiusLabel(it).isNotBlank()) }
    }
}
