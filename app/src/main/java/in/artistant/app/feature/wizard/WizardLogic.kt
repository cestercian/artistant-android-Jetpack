package `in`.artistant.app.feature.wizard

import `in`.artistant.app.data.model.HandleRules
import `in`.artistant.app.data.repository.WizardProfileDraft
import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.feature.epk.PackageRow
import `in`.artistant.app.feature.epk.packageRowIsSavable

/**
 * Every decision the onboarding wizard makes, extracted from the Composables and
 * the ViewModel so it can actually be covered.
 *
 * Same reasoning as `EpkLogic`: the test classpath is junit + coroutines-test
 * only, so anything inside a `@Composable` or behind Hilt is unreachable from a
 * unit test by construction. The wizard's risky parts are all *gates and
 * catalogs* — which step may advance, what a handle is allowed to be, which
 * fields reach the publish payload — and those fail silently rather than
 * loudly. So they live here as free functions over plain data and the
 * ViewModel becomes a shell that sequences them.
 */

// ── Step machine ─────────────────────────────────────────────────────────────

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

/**
 * User-facing flow order.
 *
 * Declared separately from the enum's own ordinal because the two are allowed to
 * diverge: the enum's order is a persistence contract (a stored draft names its
 * step), the flow order is a product decision. Keeping them as one thing is how
 * reordering a step silently invalidates every saved draft.
 */
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

/** The steps that carry a progress segment — everything the artist fills in. */
private val WizardFormSteps: List<WizardStep> = WizardFlowOrder.filter { it != WizardStep.Done }

/** Segment index for the progress bar, or null on the steps that hide it. */
fun wizardProgressIndex(step: WizardStep): Int? =
    WizardFormSteps.indexOf(step).takeIf { it >= 0 }

fun wizardProgressTotal(): Int = WizardFormSteps.size

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

/**
 * Resolve a harness / deep-link step name to a real step.
 *
 * Case-insensitive and forgiving of the name only; an unknown name returns null
 * rather than defaulting to Identity, so a typo in a launch argument shows up as
 * "the flag did nothing" instead of silently landing somewhere plausible.
 */
fun wizardStepFromName(raw: String?): WizardStep? {
    val name = raw?.trim().orEmpty()
    if (name.isEmpty()) return null
    return WizardFlowOrder.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

// ── Catalogs ─────────────────────────────────────────────────────────────────

/** The seven categories the server's `category` enum accepts. */
val WizardCategories = listOf(
    "Indie Band", "DJ", "Stand-up", "Acoustic", "Singer", "Magician", "Host",
)

/**
 * Cities we route gigs in.
 *
 * A closed list rather than the free-text field this step used to be: base city
 * is a search predicate, and "Bengaluru" typed by one artist and "Bangalore" by
 * the next puts them in different result sets for the same query. The client
 * signup surface picks from the same shape of list, so an artist can never pick
 * a city a client cannot search.
 */
val WizardCities = listOf(
    "Bangalore", "Chennai", "Delhi", "Goa", "Hyderabad", "Kolkata", "Mumbai", "Pune",
)

val WizardWeekdays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

/** Toggle membership — one implementation shared by every chip grid in the flow. */
fun toggleInSet(value: String, set: Set<String>): Set<String> =
    if (value in set) set - value else set + value

/**
 * Time slots, sourced from the booking screen's list rather than redeclared.
 *
 * These strings are stored verbatim in `artists.default_time_slots` AND rendered
 * as the client's booking grid, so a second copy here would be a data migration
 * waiting to happen the first time someone "tidies" one of them.
 */
val WizardTimeSlots: List<String> = DefaultTimeSlots

/** Bio ceiling, mirroring the server column. */
const val WIZARD_BIO_MAX: Int = 200

/** Sample ceiling — the same number the EPK editor enforces post-wizard. */
const val WIZARD_MAX_SAMPLES: Int = 6

/** Pricing tiers an artist may publish from the wizard. */
const val WIZARD_MAX_PACKAGES: Int = 6

/**
 * Three starter tiers sized by category, so the pricing step opens with
 * something to edit rather than an empty form.
 *
 * `popular` is set on exactly one row per category and false on the rest. That
 * is the only shape in which the badge means anything — flagging all of them, or
 * the single row a one-tier artist publishes, badges every profile in the app
 * and the signal dies. `popularBadgeWouldMeanSomething` is the reader side of
 * the same fact.
 */
fun starterPackageRows(category: String): List<PackageRow> = when (category) {
    "DJ" -> listOf(
        PackageRow("starter-0", "Warm-up Set", "90 min", "18000", popular = false),
        PackageRow("starter-1", "Peak Time", "2h", "35000", popular = true),
        PackageRow("starter-2", "Full Night", "4h", "60000", popular = false),
    )
    "Stand-up" -> listOf(
        PackageRow("starter-0", "Corporate Clean", "30 min", "28000", popular = false),
        PackageRow("starter-1", "Club Set", "45 min", "45000", popular = true),
    )
    "Acoustic", "Singer" -> listOf(
        PackageRow("starter-0", "Cafe Set", "60 min", "14000", popular = true),
        PackageRow("starter-1", "Lounge Set", "90 min", "22000", popular = false),
    )
    // Indie Band, Magician, Host — and any category the server grows before we do.
    else -> listOf(
        PackageRow("starter-0", "Acoustic Trio", "45 min", "15000", popular = false),
        PackageRow("starter-1", "Full Band", "60 min", "22000", popular = true),
        PackageRow("starter-2", "Headline Set", "90 min", "38000", popular = false),
    )
}

// ── Handle ───────────────────────────────────────────────────────────────────

/**
 * What the identity step currently knows about the typed handle.
 *
 * [Error] is deliberately distinct from [Taken]: a network blip must not read as
 * "someone owns this". The gate treats Error as passable and lets the unique
 * constraint on `users.handle` be the real backstop, which is the same contract
 * `HandleAvailability.Failure` documents on the repository side.
 */
enum class WizardHandleStatus { Empty, Invalid, Checking, Available, Taken, Error }

/**
 * Keystroke filter: drop anything a valid handle cannot contain.
 *
 * Filtering on the way in rather than validating on the way out means the field
 * can never hold a value the regex would reject — the artist never types a
 * character, sees it appear, and then reads that their handle is invalid.
 */
fun sanitizeHandleInput(raw: String): String =
    raw.lowercase().filter { it.isLetterOrDigit() || it == '_' }

/**
 * The status derivable without a round-trip. The debounced availability check
 * upgrades Checking → Available/Taken/Error asynchronously.
 */
fun wizardHandleSyncStatus(handle: String): WizardHandleStatus = when {
    handle.isEmpty() -> WizardHandleStatus.Empty
    !HandleRules.isValidFormat(handle) -> WizardHandleStatus.Invalid
    else -> WizardHandleStatus.Checking
}

/**
 * May the artist move past the identity step with this handle?
 *
 * Checking blocks — advancing mid-flight would let a taken handle through and
 * fail at publish, several steps later, with nothing on screen explaining why.
 * Error passes, per the contract above.
 */
fun wizardHandleAllowsAdvance(status: WizardHandleStatus): Boolean = when (status) {
    WizardHandleStatus.Available, WizardHandleStatus.Error -> true
    WizardHandleStatus.Empty,
    WizardHandleStatus.Invalid,
    WizardHandleStatus.Checking,
    WizardHandleStatus.Taken,
    -> false
}

/** The one-line hint under the handle field; null when the field should stay quiet. */
fun wizardHandleHint(status: WizardHandleStatus): String? = when (status) {
    WizardHandleStatus.Taken -> "That handle's taken — try another."
    WizardHandleStatus.Invalid -> "3–24 characters: lowercase letters, numbers, underscore."
    else -> null
}

// ── Per-step gates ───────────────────────────────────────────────────────────

/**
 * Can the artist leave [WizardUiState.step]?
 *
 * Every early exit here is a row a client would otherwise see with a hole in it:
 * a blank stage name in search results, a ₹0 tier on the booking sheet, an
 * artist with no days who falls back to a generic availability grid. Optional
 * steps (cover, socials, bio, samples) pass unconditionally — the CTA copy is
 * what tells the artist they are skipping rather than completing.
 */
fun wizardCanAdvance(state: WizardUiState): Boolean = when (state.step) {
    WizardStep.Identity ->
        state.stageName.isNotBlank() &&
            state.category.isNotBlank() &&
            HandleRules.isValidFormat(state.handle) &&
            wizardHandleAllowsAdvance(state.handleStatus)
    WizardStep.Location -> state.baseCity.isNotBlank()
    // At least one publishable tier. `packageRowIsSavable` is the same predicate
    // the publish path filters on, so the gate and the write can never disagree
    // about whether a row counts.
    WizardStep.Pricing -> state.packageRows.any(::packageRowIsSavable)
    WizardStep.Tech -> state.techItems.isNotEmpty()
    // Both halves required: a day with no time, or a time with no day, is not a
    // schedule a client can book against.
    WizardStep.Availability -> state.daysAvailable.isNotEmpty() && state.timeSlots.isNotEmpty()
    WizardStep.Cover, WizardStep.Socials, WizardStep.Samples -> true
    WizardStep.Bio -> state.bio.length <= WIZARD_BIO_MAX
    WizardStep.Preview -> !state.isPublishing
    // Done advances through its own CTA, not the step machine.
    WizardStep.Done -> false
}

/**
 * Has this step been given anything?
 *
 * Distinct from [wizardCanAdvance]: the optional steps always advance, but the
 * preview still wants to say which ones the artist actually filled. Used for the
 * per-step completeness dots and the preview's "skipped" copy.
 */
fun wizardStepIsFilled(state: WizardUiState, step: WizardStep): Boolean = when (step) {
    WizardStep.Identity -> state.stageName.isNotBlank() && state.handle.isNotBlank() && state.category.isNotBlank()
    WizardStep.Location -> state.baseCity.isNotBlank()
    WizardStep.Pricing -> state.packageRows.any(::packageRowIsSavable)
    WizardStep.Tech -> state.techItems.isNotEmpty()
    WizardStep.Availability -> state.daysAvailable.isNotEmpty() && state.timeSlots.isNotEmpty()
    WizardStep.Cover -> state.pendingCover != null
    WizardStep.Socials -> state.instagramHandle.isNotBlank() ||
        state.spotifyArtistUrl.isNotBlank() ||
        state.youtubeChannelUrl.isNotBlank()
    WizardStep.Bio -> state.bio.isNotBlank()
    WizardStep.Samples -> state.pendingSamples.isNotEmpty()
    WizardStep.Preview, WizardStep.Done -> true
}

// ── Copy ─────────────────────────────────────────────────────────────────────

/**
 * The step headline, split so the accent word can be set in italic brand.
 *
 * Returned as a triple rather than one string because the editorial headline is
 * a single paragraph with one tinted run — three separate Texts would wrap
 * mid-phrase, which is the whole reason `EditorialHeadline` exists.
 */
data class WizardHeadline(val lead: String, val accent: String, val tail: String)

fun wizardStepHeadline(step: WizardStep): WizardHeadline = when (step) {
    WizardStep.Identity -> WizardHeadline("Your stage ", "identity", "")
    WizardStep.Location -> WizardHeadline("Where you ", "play", "")
    WizardStep.Pricing -> WizardHeadline("Set your ", "pricing", "")
    WizardStep.Tech -> WizardHeadline("Tech ", "rider", "")
    WizardStep.Availability -> WizardHeadline("When you ", "play", "")
    WizardStep.Cover -> WizardHeadline("Pick your ", "cover", "")
    WizardStep.Socials -> WizardHeadline("Plug in your ", "sound", "")
    WizardStep.Bio -> WizardHeadline("Your story, ", "briefly", "")
    WizardStep.Samples -> WizardHeadline("Drop in a few ", "clips", "")
    WizardStep.Preview -> WizardHeadline("How does this ", "look", "?")
    WizardStep.Done -> WizardHeadline("You're ", "live", ".")
}

fun wizardStepSubtitle(step: WizardStep): String = when (step) {
    WizardStep.Identity -> "How clients will discover and book you."
    WizardStep.Location -> "Helps us route the right gigs your way."
    WizardStep.Pricing -> "We seeded these from your category — tweak to taste."
    WizardStep.Tech -> "What you'll need from the venue. Pick all that apply."
    WizardStep.Availability ->
        "Pick the days and start times you take gigs. Clients see this on your profile and in search."
    WizardStep.Cover -> "Drop a photo, or pick a gradient if you'd rather skip uploads for now."
    WizardStep.Socials -> "Paste any of these — they show up on your profile and feed the Bookability Score later."
    WizardStep.Bio -> "A line or two about your sound, your live energy, or what makes your gigs different."
    WizardStep.Samples ->
        "Up to six short audio samples. Anything 30s–2 min works — give clients a real sense of your sound."
    WizardStep.Preview -> "This is how clients will see you — take a last look before you go live."
    WizardStep.Done -> ""
}

/**
 * The CTA label.
 *
 * Optional steps flip to "Skip for now" the moment they are empty, which is the
 * only signal the artist gets that continuing past them costs nothing. A
 * disabled button with unchanged copy reads as a broken form instead.
 */
fun wizardCtaLabel(state: WizardUiState): String = when (state.step) {
    WizardStep.Preview -> if (state.isPublishing) "Publishing…" else "Looks good, publish"
    WizardStep.Done -> "Open dashboard"
    WizardStep.Cover -> if (state.pendingCover == null) "Skip for now" else "Continue"
    WizardStep.Samples -> if (state.pendingSamples.isEmpty()) "Skip for now" else "Continue"
    WizardStep.Bio -> if (state.bio.isBlank()) "Skip for now" else "Continue"
    WizardStep.Socials -> if (!wizardStepIsFilled(state, WizardStep.Socials)) "Skip for now" else "Continue"
    else -> "Continue"
}

/** Narration for the publish overlay — the artist should never watch a bare spinner. */
fun wizardPublishProgressLabel(phase: WizardPublishPhase): String = when (phase) {
    WizardPublishPhase.Idle -> ""
    WizardPublishPhase.SavingProfile -> "Saving your profile…"
    WizardPublishPhase.SavingDetails -> "Saving your tiers and rider…"
    WizardPublishPhase.GoingLive -> "Going live…"
}

/** Where the publish sequence currently is. Drives the CTA's narration only. */
enum class WizardPublishPhase { Idle, SavingProfile, SavingDetails, GoingLive }

// ── Bio counter ──────────────────────────────────────────────────────────────

/** How loudly to render the bio character counter. */
enum class WizardCounterTone { Quiet, Warn, Over }

/**
 * Quiet by default, warm in the last 20 characters, hot at the ceiling.
 *
 * The counter is the only place the artist learns the bio has a limit at all, so
 * it changes colour before the limit rather than at it — arriving at a hard stop
 * with no warning reads as the field being broken.
 */
fun bioCounterTone(length: Int): WizardCounterTone = when {
    length >= WIZARD_BIO_MAX -> WizardCounterTone.Over
    WIZARD_BIO_MAX - length <= 20 -> WizardCounterTone.Warn
    else -> WizardCounterTone.Quiet
}

/** Clamp on the way in so a pasted essay truncates instead of silently overflowing. */
fun clampBio(raw: String): String = if (raw.length <= WIZARD_BIO_MAX) raw else raw.take(WIZARD_BIO_MAX)

// ── Publish payload ──────────────────────────────────────────────────────────

/**
 * Canonical chronological order for the stored time slots.
 *
 * The wizard holds them in a `Set` because the UI is a toggle grid, and a Set has
 * no order — `sorted()` on the raw strings would file "10:00 PM" before "6:00 PM"
 * lexicographically. Ordering through the catalog gives the server row and the
 * client's booking grid the same reading order.
 */
fun sortedTimeSlots(slots: Set<String>): List<String> = WizardTimeSlots.filter { it in slots }

/** Same idea for weekdays: calendar order, not hash order. */
fun sortedWeekdays(days: Set<String>): List<String> = WizardWeekdays.filter { it in days }

/**
 * Empty means "skipped", and skipped must reach the server as NULL rather than
 * as a literal empty string — the profile renderer decides whether to show a
 * social row at all by testing for null.
 */
fun nullIfBlank(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }

/** Maps wizard UI state to the repository publish payload (the test seam). */
fun buildWizardProfileDraft(state: WizardUiState, artistId: String): WizardProfileDraft =
    WizardProfileDraft(
        artistId = artistId,
        stageName = state.stageName.trim(),
        handle = HandleRules.normalize(state.handle),
        category = state.category,
        baseCity = state.baseCity,
        genre = state.genre.trim(),
        bio = state.bio.trim(),
        coverGradientIndex = state.coverGradientIndex,
        daysAvailable = sortedWeekdays(state.daysAvailable),
        // A publish with no slots would leave the booking grid with nothing to
        // offer, so fall back to the two most common evening starts rather than
        // writing an empty array.
        timeSlots = sortedTimeSlots(state.timeSlots).ifEmpty { WizardTimeSlots.take(2) },
        instagramHandle = nullIfBlank(state.instagramHandle),
        spotifyArtistUrl = nullIfBlank(state.spotifyArtistUrl),
        youtubeChannelUrl = nullIfBlank(state.youtubeChannelUrl),
    )
