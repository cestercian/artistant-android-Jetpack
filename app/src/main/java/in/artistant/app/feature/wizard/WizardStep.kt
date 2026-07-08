package `in`.artistant.app.feature.wizard

import `in`.artistant.app.data.model.ArtistPackage
import java.util.UUID

/**
 * The 11-step artist onboarding wizard (port of iOS `ArtistWizardStep`). Unlike iOS
 * there's no persisted snapshot on Android — the whole draft lives in the (config-
 * change-surviving) ViewModel, never in DataStore — so declaration order IS the flow
 * order and we don't need the iOS append-only-rawValue / separate-`flowOrder` dance.
 *
 * ponytail: no on-disk wizard snapshot. The M1b signup flow doesn't persist its draft
 * either (state lives in the VM); the pending MEDIA already survives on disk via
 * WizardMediaCache. Add snapshot persistence only if "resume after app-kill mid-wizard"
 * becomes a real requirement.
 */
enum class WizardStep {
    Identity, Location, Pricing, Tech, Availability, Cover, Socials, Bio, Samples, Preview, Done
}

/** The user-facing order the wizard walks through. */
val WIZARD_FLOW: List<WizardStep> = WizardStep.entries.toList()

/** Move forward one step, clamping at the end (iOS `advance`). */
fun advanceWizard(step: WizardStep): WizardStep {
    val i = WIZARD_FLOW.indexOf(step)
    return if (i in 0 until WIZARD_FLOW.lastIndex) WIZARD_FLOW[i + 1] else step
}

/** Move back one step, clamping at the start (iOS `back`). */
fun backWizard(step: WizardStep): WizardStep {
    val i = WIZARD_FLOW.indexOf(step)
    return if (i > 0) WIZARD_FLOW[i - 1] else step
}

/**
 * One editable pricing tier in the wizard's pricing step (iOS `EPKPackage`). Carries a
 * stable [id] so a `LazyColumn`/row edit keeps its identity across recompositions; maps
 * to the wire [ArtistPackage] (with an empty `includes`) at publish time.
 */
data class PricingTier(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val duration: String,
    val price: Int,
    val popular: Boolean,
) {
    fun toArtistPackage() = ArtistPackage(
        name = name.trim(),
        duration = duration.trim(),
        price = price,
        includes = emptyList(),
        popular = popular,
    )
}

/**
 * Static wizard content — categories, days, time slots, event types, tech presets, and
 * cities. Ports the iOS `ArtistOnboardingStore` static tables + `EPKStore.techPresets`.
 * The time-slot strings are stored verbatim in `artists.default_time_slots`, so editing
 * a label here is a data migration (same contract as iOS).
 */
object WizardConstants {
    val categories = listOf("Indie Band", "DJ", "Stand-up", "Acoustic", "Singer", "Magician", "Host")
    val allDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val allTimeSlots = listOf("6:00 PM", "7:30 PM", "8:30 PM", "9:00 PM", "10:00 PM", "11:00 PM")
    val allEventTypes = listOf("College fest", "Cafe / pub", "Wedding", "Corporate", "Private party", "Club night")
    val techPresets = listOf(
        "4 vocal mics", "2 wedge monitors", "1 DI box", "Drum kit (5pc)",
        "Mixing console (8ch+)", "Stage lights × 4", "Power: 16A × 2",
    )

    // Same list the signup ProfileScreen offers (iOS `AppEnvironment.supportedCities`) so a
    // city an artist can pick in signup can also be picked as their base city here.
    val cities = listOf("Bangalore", "Chennai", "Delhi", "Goa", "Hyderabad", "Kolkata", "Mumbai", "Pune")

    /** Default weekday/time picks so a brand-new wizard is never gated on an empty set. */
    val defaultDays = setOf("Fri", "Sat")
    val defaultTimeSlots = setOf("7:30 PM", "9:00 PM")

    /** Category-sized starter pricing tiers, prefilled the moment a category is chosen (iOS
     *  `starterPackages`). The artist edits these in the pricing step. */
    fun starterPackages(category: String): List<PricingTier> = when (category) {
        "DJ" -> listOf(
            PricingTier(name = "Warm-up Set", duration = "90 min", price = 18000, popular = false),
            PricingTier(name = "Peak Time", duration = "2h", price = 35000, popular = true),
            PricingTier(name = "Full Night", duration = "4h", price = 60000, popular = false),
        )
        "Stand-up" -> listOf(
            PricingTier(name = "Corporate Clean", duration = "30 min", price = 28000, popular = false),
            PricingTier(name = "Club Set", duration = "45 min", price = 45000, popular = true),
        )
        "Acoustic", "Singer" -> listOf(
            PricingTier(name = "Cafe Set", duration = "60 min", price = 14000, popular = true),
            PricingTier(name = "Lounge Set", duration = "90 min", price = 22000, popular = false),
        )
        else -> listOf( // Indie Band, Magician, Host
            PricingTier(name = "Acoustic Trio", duration = "45 min", price = 15000, popular = false),
            PricingTier(name = "Full Band", duration = "60 min", price = 22000, popular = true),
            PricingTier(name = "Headline Set", duration = "90 min", price = 38000, popular = false),
        )
    }

    /** Canonical chronological order for a picked time-slot set (iOS `sortedTimeSlots`). A bare
     *  `sorted()` would put "10:00 PM" before "6:00 PM" lexicographically, so filter the ordered
     *  master list instead. */
    fun sortedTimeSlots(slots: Set<String>): List<String> = allTimeSlots.filter { it in slots }
}

/**
 * Per-step validation gates (iOS `ArtistOnboardingStore.*Valid`). Pure so the wizard's CTA
 * gating is unit-testable without a ViewModel/Compose. Optional steps (tech/cover/socials)
 * always pass; the required ones gate exactly like iOS except where the M5b spec relaxed
 * them (availability = at least one day; tech optional).
 */
object WizardValidation {
    /** Stage name + a >=3-char handle that resolved available (or errored — a transient RPC
     *  blip shouldn't trap the artist; the upsert's unique constraint is the backstop) + a
     *  chosen category. */
    fun identity(stageName: String, handleLength: Int, handleAvailable: Boolean, category: String): Boolean =
        stageName.isNotBlank() && handleLength >= 3 && category.isNotBlank() && handleAvailable

    fun location(baseCity: String): Boolean = baseCity.isNotBlank()

    /** At least one tier, all with a name and a price >= ₹1000 (below that reads as a typo). */
    fun pricing(tiers: List<PricingTier>): Boolean =
        tiers.isNotEmpty() && tiers.all { it.price >= 1000 && it.name.isNotBlank() }

    /** Optional — the artist can skip the tech rider (M5b spec relaxed from iOS's non-empty). */
    fun tech(): Boolean = true

    /** At least one day picked (BookingView falls back to a generic grid otherwise). */
    fun availability(days: Set<String>): Boolean = days.isNotEmpty()

    fun bio(bio: String): Boolean = bio.length <= 200

    fun samples(count: Int): Boolean = count <= 6

    fun cover(): Boolean = true
    fun socials(): Boolean = true
}
