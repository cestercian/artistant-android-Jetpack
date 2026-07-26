package `in`.artistant.app.feature.wizard

import `in`.artistant.app.data.repository.WizardProfileDraft
import `in`.artistant.app.feature.booking.DefaultTimeSlots

/**
 * Pure wizard step machine — port of iOS `ArtistWizardStep.flowOrder`. Kept
 * testable without Hilt so step advance / Done semantics stay honest.
 */
enum class WizardStep {
    Identity,
    Location,
    Pricing,
    Tech,
    Availability,
    Cover,
    Socials,
    Bio,
    Samples,
    Preview,
    Done,
}

/** User-facing flow order (NOT enum ordinal — matches iOS flowOrder). */
val WizardFlowOrder: List<WizardStep> = listOf(
    WizardStep.Identity,
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

/** Progress bar segments — excludes Welcome/Done-style bookends on iOS; here Preview is last form. */
fun wizardProgressIndex(step: WizardStep): Int? {
    val formSteps = WizardFlowOrder.filter { it != WizardStep.Done }
    val idx = formSteps.indexOf(step)
    return if (idx < 0) null else idx
}

fun wizardProgressTotal(): Int = WizardFlowOrder.count { it != WizardStep.Done }

fun advanceWizardStep(current: WizardStep): WizardStep? {
    val idx = WizardFlowOrder.indexOf(current)
    if (idx < 0 || idx >= WizardFlowOrder.lastIndex) return null
    return WizardFlowOrder[idx + 1]
}

fun backWizardStep(current: WizardStep): WizardStep? {
    val idx = WizardFlowOrder.indexOf(current)
    if (idx <= 0) return null
    return WizardFlowOrder[idx - 1]
}

/** iOS `ArtistOnboardingStore.categories`. */
val WizardCategories = listOf(
    "Indie Band", "DJ", "Stand-up", "Acoustic", "Singer", "Magician", "Host",
)

val WizardWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun toggleInSet(value: String, set: Set<String>): Set<String> =
    if (value in set) set - value else set + value

/** Maps wizard UI state to the repository publish payload (test seam). */
fun buildWizardProfileDraft(state: WizardUiState, artistId: String): WizardProfileDraft =
    WizardProfileDraft(
        artistId = artistId,
        stageName = state.stageName,
        handle = state.handle,
        category = state.category,
        baseCity = state.baseCity,
        genre = state.genre,
        bio = state.bio,
        coverGradientIndex = state.coverGradientIndex,
        daysAvailable = state.daysAvailable.toList(),
        timeSlots = state.timeSlots.toList().ifEmpty { DefaultTimeSlots.take(2) },
        instagramHandle = state.instagramHandle,
        spotifyArtistUrl = state.spotifyArtistUrl,
        youtubeChannelUrl = state.youtubeChannelUrl,
    )

fun wizardStepTitle(step: WizardStep): String = when (step) {
    WizardStep.Identity -> "Who are you on stage?"
    WizardStep.Location -> "Where do you perform?"
    WizardStep.Pricing -> "Set your packages"
    WizardStep.Tech -> "Tech rider"
    WizardStep.Availability -> "When can you play?"
    WizardStep.Cover -> "Cover photo"
    WizardStep.Socials -> "Social links"
    WizardStep.Bio -> "Your bio"
    WizardStep.Samples -> "Audio samples"
    WizardStep.Preview -> "Preview your EPK"
    WizardStep.Done -> "You're live"
}

fun wizardStepSubtitle(step: WizardStep): String = when (step) {
    WizardStep.Identity -> "Stage name, handle, and category."
    WizardStep.Location -> "Base city clients will search."
    WizardStep.Pricing -> "Starter tiers — edit names and prices."
    WizardStep.Tech -> "What you need at the venue."
    WizardStep.Availability -> "Days and preferred start times."
    WizardStep.Cover -> "Hero image for your profile."
    WizardStep.Socials -> "Paste handles or profile URLs."
    WizardStep.Bio -> "Up to 200 characters."
    WizardStep.Samples -> "Up to six clips."
    WizardStep.Preview -> "Quick read before you publish."
    WizardStep.Done -> "Clients can discover you now."
}
