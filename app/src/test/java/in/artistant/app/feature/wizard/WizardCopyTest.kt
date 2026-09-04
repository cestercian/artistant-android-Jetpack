package `in`.artistant.app.feature.wizard

import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.domain.booking.BookingMath
import `in`.artistant.app.feature.epk.PackageRow
import `in`.artistant.app.feature.epk.packageDrafts
import `in`.artistant.app.feature.epk.shareLinkUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `the all-in the wizard quotes is the total the checkout will charge`() {
        // End to end through the two things that can drift, not just one. The
        // wizard reads a TYPED string and the checkout reads a STORED Int, so
        // the number only holds if both the parse (`parsePrice`, via
        // `packageDrafts`) and the arithmetic (`BookingMath`, via
        // `BookingDraft.charges`) are shared. A private copy of either — a
        // digits-only filter here, a 1.239 factor there — is how the artist
        // publishes one number and a client is shown another.
        listOf("1", "14000", "22,000", " 36000 ", "₹99,999").forEach { typed ->
            val row = PackageRow("k", "Peak Time", "2h", typed, popular = false)
            val published = packageDrafts(listOf(row)).single()
            val checkout = BookingDraft(
                artistId = "a",
                feeInr = published.priceInr,
                date = "Fri, Mar 6, 2026",
                dateRawEpochMs = 0L,
                time = "8:00 PM",
            )

            assertEquals(checkout.charges.total, packageAllInInr(typed))
        }
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
        // Nine, not the design's eleven: the design's flow has steps this app
        // does not, and the total is derived from WizardFlowOrder rather than
        // typed, so the bar, the counter and the sheet all count the same steps.
        assertEquals("01 / 09", wizardStepCounter(WizardStep.Identity))
        assertEquals("02 / 09", wizardStepCounter(WizardStep.Location))
        // The counter's LAST screen reads N of N. It used to read "09 / 10",
        // counting a Preview step that hides the counter entirely — so the tenth
        // cell belonged to no screen and the track never completed.
        assertEquals("09 / 09", wizardStepCounter(WizardStep.Samples))
        // Preview swaps the track for its own title; Done has no chrome. Neither
        // is counted, so neither has a counter to show.
        assertNull(wizardStepCounter(WizardStep.Preview))
        assertNull(wizardStepCounter(WizardStep.Done))
    }

    @Test
    fun `saved-so-far counts steps left behind, matching the filled segments`() {
        // Standing on step one, nothing is banked yet — and the bar under the
        // label draws zero filled cells, so the two must agree.
        assertEquals("0 of 9", wizardSavedSoFarLabel(WizardStep.Identity))
        assertEquals("6 of 9", wizardSavedSoFarLabel(WizardStep.Socials))
        // Everything is banked by the time the artist is reviewing it.
        assertEquals("9 of 9", wizardSavedSoFarLabel(WizardStep.Preview))
    }

    @Test
    fun `the counter, the sheet and the track cannot disagree`() {
        // Three surfaces, one arithmetic. The counter says which step you are
        // on (1-based), the sheet says how many are behind you (0-based), and
        // the bar fills that many cells — so for every step that shows the
        // counter, the sheet's number is exactly one less.
        WizardFlowOrder.forEach { step ->
            val counter = wizardStepCounter(step) ?: return@forEach
            val position = counter.substringBefore(" /").trimStart('0').toInt()
            assertEquals(position - 1, wizardProgressFilled(step))
            assertEquals("${position - 1} of ${wizardProgressTotal()}", wizardSavedSoFarLabel(step))
        }
    }

    @Test
    fun `the footer note is an action only where the CTA has not already said it`() {
        fun at(step: WizardStep, bio: String = "") =
            WizardUiState(step = step, bio = bio)

        // Empty optional step: the button itself says Skip, so the line below
        // would be the same word twice.
        assertNull(wizardFooterNote(at(WizardStep.Bio)))
        assertEquals("Skip for now", wizardFooterNote(at(WizardStep.Bio, bio = "Rooftop sets")))
        assertTrue(wizardFooterNote(at(WizardStep.Pricing))!!.startsWith("Step 3 of 9"))
        assertEquals("You can keep editing after you publish", wizardFooterNote(at(WizardStep.Preview)))
        assertNull(wizardFooterNote(at(WizardStep.Identity)))
    }

    // --- The public address ---------------------------------------------------

    @Test
    fun `the address the wizard shows is the one the app shares`() {
        // One builder, three surfaces: the wizard's identity hint and its
        // "You're live" copy row, the press-kit editor's Copy row, and the
        // profile share sheet. The wizard used to render `artistant.in/@handle`
        // while the share intent sent `artistant.in/handle` — and the wizard's
        // is the one an artist pastes to a venue, having just been told it is
        // where their profile lives.
        assertEquals("artistant.in/tiltcollective", shareLinkUrl("tiltcollective"))
        // Not "artistant.in/" — a half-formed URL reads as a rendering fault.
        assertNull(shareLinkUrl(""))
        assertNull(shareLinkUrl("   "))
    }

    // --- Travel radius --------------------------------------------------------

    @Test
    fun `the radius floor reads as a constraint rather than as zero kilometres`() {
        assertEquals("City only", travelRadiusLabel(0))
        assertEquals("Up to 150 km", travelRadiusLabel(150))
        // Every offered option has a label the chip row can render.
        WizardTravelRadii.forEach { assertTrue(travelRadiusLabel(it).isNotBlank()) }
    }

    // --- Preview: every section, and the step that owns it ---------------------

    private fun previewState() = WizardUiState(
        step = WizardStep.Preview,
        stageName = "The Tilt Collective",
        category = "DJ",
        genre = "Indie folk",
        baseCity = "Bengaluru",
        travelRadiusKm = 150,
        bio = "Rooftop sets.",
        packageRows = starterPackageRows("DJ"),
        techItems = listOf("2x DI box"),
        serviceTags = listOf("dj-set"),
        instagramHandle = "tiltcollective",
    )

    @Test
    fun `every preview row's Edit lands on the step that owns its fields`() {
        // The mapping, spelled out, because it is the kind of wrong that renders
        // perfectly: tap Edit, land on a plausible screen, and the field you came
        // for is not on it. Two of these are counter-intuitive and both used to
        // be wrong — the city is drawn in the identity header but lives on the
        // LOCATION step, and the service picker is drawn under its own heading
        // but lives on the BIO step.
        assertEquals(
            mapOf(
                "Where you play" to WizardStep.Location,
                "Bio" to WizardStep.Bio,
                "Packages" to WizardStep.Pricing,
                "Tech rider" to WizardStep.Tech,
                "Availability" to WizardStep.Availability,
                "Samples" to WizardStep.Samples,
                "Services" to WizardStep.Bio,
                "Socials" to WizardStep.Socials,
            ),
            wizardPreviewRows(previewState()).associate { it.label to it.step },
        )
    }

    @Test
    fun `the preview can send the artist back to every step they filled in`() {
        // The property worth locking, and the one a hard-coded index breaks
        // silently: add or move a step and this fails rather than quietly
        // stranding whatever that step holds on the last screen before publish —
        // which is the screen an artist is on precisely because they want to
        // change something.
        val reachable = wizardPreviewRows(previewState()).map { it.step }.toSet() +
            // The cover hero and the identity header carry these two; the rest
            // are rows. Nothing may point at Preview or Done: a chip that
            // reopens the screen it is drawn on does nothing.
            setOf(WizardStep.Cover, WizardStep.Identity)

        assertEquals(
            WizardFlowOrder.filter { it != WizardStep.Preview && it != WizardStep.Done }.toSet(),
            reachable,
        )
    }

    @Test
    fun `a skipped section says so rather than rendering a blank line`() {
        // A blank value reads as a rendering fault, and the artist cannot tell a
        // thin profile from a broken screen. Discovering it here costs one tap;
        // discovering it from a week of silence costs the gig.
        val rows = wizardPreviewRows(WizardUiState(step = WizardStep.Preview))
        rows.forEach { assertTrue("${it.label} renders a value", it.value.isNotBlank()) }
        assertEquals("Not added", rows.single { it.label == "Bio" }.value)
        assertEquals("No publishable tier yet", rows.single { it.label == "Packages" }.value)
        assertFalse(rows.single { it.label == "Where you play" }.filled)

        val filled = wizardPreviewRows(previewState())
        assertEquals("Bengaluru · Up to 150 km", filled.single { it.label == "Where you play" }.value)
        assertEquals("3 tiers · ₹18,000–₹60,000", filled.single { it.label == "Packages" }.value)
        assertEquals("1 line", filled.single { it.label == "Tech rider" }.value)
        assertEquals("Instagram", filled.single { it.label == "Socials" }.value)
    }
}
