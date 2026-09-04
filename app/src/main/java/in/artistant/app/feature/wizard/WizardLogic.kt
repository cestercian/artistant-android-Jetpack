package `in`.artistant.app.feature.wizard

import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.HandleRules
import `in`.artistant.app.data.model.SearchCatalog
import `in`.artistant.app.domain.artist.ServiceTags
import `in`.artistant.app.domain.booking.BookingMath
import `in`.artistant.app.data.repository.WizardProfileDraft
import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.feature.epk.PackageRow
import `in`.artistant.app.feature.epk.packageRowIsSavable
import `in`.artistant.app.feature.epk.parsePrice
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

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

/**
 * The steps the track counts: the ones the artist fills in, and only those.
 *
 * Preview and Done are both excluded because neither DRAWS the track — Preview
 * swaps it for its own centred title (screen 45) and Done has no chrome at all.
 * Counting a step that never renders a segment made the bar promise a cell
 * nothing could fill: the last screen that shows the counter read "09 / 10", and
 * an artist who finished every form step never saw the track complete. A
 * progress bar that is wrong on the last step is wrong on the step it matters
 * most on.
 */
private val WizardProgressSteps: List<WizardStep> =
    WizardFlowOrder.filter { it != WizardStep.Preview && it != WizardStep.Done }

/** Segment index for the counter, or null on the steps that hide the track. */
fun wizardProgressIndex(step: WizardStep): Int? =
    WizardProgressSteps.indexOf(step).takeIf { it >= 0 }

fun wizardProgressTotal(): Int = WizardProgressSteps.size

/**
 * How many segments are FILLED — the steps left behind.
 *
 * Preview and Done sit past every form step, so they fill the whole track rather
 * than none of it. That matters because Save & exit is reachable from Preview
 * and draws this same bar: keyed on the index alone it had nothing to draw and
 * rendered an empty track over the words "9 of 10".
 */
fun wizardProgressFilled(step: WizardStep): Int =
    wizardProgressIndex(step) ?: wizardProgressTotal()

/**
 * What a screen reader is told about the track.
 *
 * Past the form steps there is no "step N" left to announce, so it says the
 * thing the filled bar is showing instead of counting to a number that is no
 * longer the artist's position.
 */
fun wizardProgressLabel(step: WizardStep): String {
    val total = wizardProgressTotal()
    val index = wizardProgressIndex(step)
    return if (index == null) "All $total steps done" else "Step ${index + 1} of $total"
}

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

/**
 * Which step a saved draft should reopen on.
 *
 * Never Done. A draft is a form in progress; if one ever names the celebration
 * step — an older build, or a debounced write that raced the publish that
 * cleared it — restoring it verbatim would show "You're live" to an artist who
 * is not. Preview is the honest landing: everything they typed is still there
 * and one tap publishes it for real. An unrecognised name starts over.
 */
fun wizardResumeStep(rawStepName: String?): WizardStep =
    when (val step = wizardStepFromName(rawStepName)) {
        null -> WizardStep.Identity
        WizardStep.Done -> WizardStep.Preview
        else -> step
    }

// ── Staged media resume ──────────────────────────────────────────────────────

/** What a restart is allowed to adopt back into the form. */
data class RestoredWizardMedia(
    val coverFileName: String?,
    val samples: List<DraftSample>,
)

/**
 * Which of a draft's staged media references are still usable.
 *
 * Every reference is checked against the disk before it is adopted, because the
 * files live in `cacheDir` and the OS is *entitled* to reclaim that directory
 * under storage pressure — that is the contract of the directory, not an edge
 * case. Restoring a reference to a reclaimed file would render a broken preview
 * on the Cover step and hand the upload queue a job with nothing behind it, so a
 * missing file reads as "not picked" and the artist is simply asked again.
 *
 * [isOnDisk] is a parameter rather than a `File.exists()` call so the decision
 * stays testable without a filesystem; the ViewModel passes the cache's own
 * lookup.
 */
fun restoredWizardMedia(
    coverFileName: String?,
    samples: List<DraftSample>,
    isOnDisk: (String) -> Boolean,
): RestoredWizardMedia = RestoredWizardMedia(
    coverFileName = coverFileName?.takeIf { it.isNotBlank() && isOnDisk(it) },
    // Truncate rather than trust the draft: the picker enforces the cap, so an
    // over-long list means an older or hand-edited draft, and handing the media
    // step more rows than it will let the artist delete back down to strands
    // them above a limit they cannot satisfy.
    samples = samples.filter { it.fileName.isNotBlank() && isOnDisk(it.fileName) }
        .take(WIZARD_MAX_SAMPLES),
)

/**
 * Staged files nothing references any more.
 *
 * Left behind by an abandoned wizard, by a pick the artist removed from the
 * form, or by the pre-fix builds that lost their in-memory references on every
 * process death. They are photo- and audio-sized and no code path will ever name
 * them again, so resume sweeps them.
 */
fun orphanWizardMediaFiles(
    onDisk: List<String>,
    referenced: Set<String>,
): List<String> = onDisk.filterNot { it in referenced }

/**
 * May Save & exit write the form to the draft store?
 *
 * Not while the draft is still being restored. Until the restore lands the form
 * holds a default [WizardUiState] — Identity step, blank stage name and handle,
 * no tiers, no media references — and that window is not short: the restore
 * awaits a profile read with no timeout, and Save & exit sits in the top bar
 * throughout it. Saving then would replace a real draft with a blank one, sign
 * the artist out, and on the next sign-in the blank draft would restore, find no
 * media references, and let the orphan sweep delete the staged cover and every
 * sample off disk. Skipping the write keeps the stored draft, which is exactly
 * what the confirm dialog promises.
 *
 * The debounced writer already refuses the pre-restore state; this is the same
 * rule applied to the one control that is live during that window.
 */
fun wizardExitMaySaveDraft(state: WizardUiState): Boolean = !state.isRestoring

// ── Draft writer ─────────────────────────────────────────────────────────────

/**
 * How long a burst of typing collapses before it reaches the draft store.
 *
 * Long enough that a text field's per-character emissions become one file write,
 * short enough that a task-kill mid-form loses at most a word.
 */
const val WIZARD_DRAFT_DEBOUNCE_MS: Long = 600

/**
 * May this snapshot be written to the draft store?
 *
 * Three refusals, each of them load-bearing:
 *  * **restoring** — the form still holds the blank default, so writing it would
 *    replace a real draft with an empty one (see [wizardExitMaySaveDraft]).
 *  * **publishing** — publish snapshots the form, then clears the draft; a write
 *    from inside that window re-creates what publish is about to delete.
 *  * **Done** — the celebration step. A restored draft naming it would open the
 *    wizard on "You're live" for an artist who is not. [wizardResumeStep] is the
 *    second guard, on the read side.
 */
fun wizardMaySaveDraft(state: WizardUiState): Boolean =
    !state.isRestoring && !state.isPublishing && state.step != WizardStep.Done

/**
 * The debounced stream of snapshots worth persisting.
 *
 * The operator order is the whole point, and it is not visible from a reading of
 * the ViewModel. A `filter` placed *upstream* of `debounce` means a refused
 * emission never reaches the timer, so it can neither restart nor supersede it.
 * That is how a publish used to resurrect its own draft: the last accepted
 * snapshot was the Preview/going-live one, its 600ms timer kept running through
 * `setPublished`, the enqueues and `draftStore.clear()` — a tail that usually
 * finishes well inside the window — and then fired, writing a full draft back
 * into the slot publish had just emptied. Refusing *after* the debounce lets the
 * Done emission win the race and then be dropped, which is what "the draft stays
 * deleted" actually requires.
 *
 * `drop(1)` discards the first post-restore snapshot: that is the state
 * `restore()` just built out of the stored draft, so writing it back is churn.
 */
@OptIn(FlowPreview::class)
fun wizardDraftWrites(
    states: Flow<WizardUiState>,
    debounceMillis: Long = WIZARD_DRAFT_DEBOUNCE_MS,
): Flow<WizardUiState> = states
    .filter { !it.isRestoring }
    .drop(1)
    .debounce(debounceMillis)
    .filter { wizardMaySaveDraft(it) }

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

// ── Pricing: fee in, all-in out ──────────────────────────────────────────────

/**
 * The band the starter tiers span for a category, and how many of them there are.
 *
 * This is a statement about the FORM, not about the market. The wizard has no
 * access to what other acts charge — there is no such aggregate on this backend —
 * so a "acts in your genre near Bengaluru charge ₹31,000–₹45,000" line would be
 * a number we invented, printed next to real money the artist is about to
 * publish. What we can say honestly is what we just put in their form and where
 * it came from, which is what this describes.
 */
data class WizardPricingBand(val low: Int, val high: Int, val tiers: Int)

/**
 * The band [starterPackageRows] seeds for [category].
 *
 * Derived from the seed rows rather than written out a second time: a hand-kept
 * copy is how the sentence under the tiers starts quoting a range the tiers
 * above it no longer contain.
 */
fun pricingBandFor(category: String): WizardPricingBand? {
    // A blank category has not seeded anything yet — `starterPackageRows` falls
    // through to its catch-all for any name it does not know, which is right for
    // a category the server grew before we did and wrong for "the artist has not
    // picked one". Quoting the fallback's range on the identity step would
    // promise tiers that do not exist until they choose.
    if (category.isBlank()) return null
    val prices = starterPackageRows(category).mapNotNull { it.price.toIntOrNull() }.filter { it > 0 }
    if (prices.isEmpty()) return null
    return WizardPricingBand(low = prices.min(), high = prices.max(), tiers = prices.size)
}

/**
 * What the host pays, from what the artist takes home.
 *
 * BOTH halves are borrowed rather than re-derived, because both can drift. The
 * fee comes out of the typed field through [parsePrice] — the same parser
 * `packageDrafts` publishes with, so the number this row quotes is the number
 * that will actually be stored on the tier — and the total comes out of
 * [BookingMath], the same 5%-then-18% the checkout charges against it
 * (`BookingDraft.charges`). A second copy of either is how the wizard and the
 * checkout start quoting different totals for one gig, and the artist finds out
 * from a client.
 *
 * A blank or unparseable price returns null rather than 0: "no number yet" and
 * "a free gig" are different, and rendering the second for the first puts "Host
 * sees ₹0" under a row the artist is halfway through typing.
 */
fun packageAllInInr(price: String): Int? {
    val fee = parsePrice(price)?.takeIf { it > 0 } ?: return null
    return BookingMath.compute(fee).total
}

// ── Location: radius and event types ─────────────────────────────────────────

/**
 * Travel radius options, in km, plus the "won't travel" floor.
 *
 * A radius is a real constraint on which gigs are worth quoting for, and the
 * design puts it on the location step so travel is priced on top of the fee
 * rather than silently eaten out of it. It is held in the DRAFT only: `artists`
 * has no radius column on this backend, so publishing it would mean inventing a
 * write. See the PR body.
 */
val WizardTravelRadii: List<Int> = listOf(0, 25, 50, 150, 500)

/** "Up to 150 km", or "City only" for the floor. */
fun travelRadiusLabel(km: Int): String = if (km <= 0) "City only" else "Up to $km km"

/**
 * The event types a host can filter by.
 *
 * Sourced from the search filter's own vocabulary rather than redeclared: an
 * event type is only worth ticking if a client ticking the same chip finds you,
 * and two lists is how those two halves drift apart.
 */
val WizardEventTypes: List<String> get() = SearchCatalog.eventTypes

// ── Availability: one badge ──────────────────────────────────────────────────

/**
 * Everything the availability step knows, compressed to the one pill a search
 * result has room for — "Thu–Sun evenings" — or null when there is nothing to
 * say yet.
 *
 * Compression is the whole point of the step. Seven day chips and six start
 * times are 13 facts, and a search row can carry one; an artist who never sees
 * the compressed form has no way to tell that picking Tue and Thu and Sat reads
 * as "Tue, Thu, Sat" while Thu through Sun reads as a weekend act.
 *
 * Null on either half missing, and both halves are load-bearing: days with no
 * times is not a schedule a client can book against, and times with no days is
 * not a schedule at all.
 */
fun availabilityBadge(days: Set<String>, slots: Set<String>): String? {
    val ordered = sortedWeekdays(days)
    if (ordered.isEmpty() || slots.isEmpty()) return null
    return "${weekdayRunLabel(ordered)} ${timeOfDayLabel(slots)}"
}

/**
 * "Thu–Sun" for a contiguous run, "Every day" for all seven, otherwise the days
 * themselves.
 *
 * Contiguity is checked against [WizardWeekdays] order and does NOT wrap. A
 * Saturday-to-Monday act is three days written as three days rather than as
 * "Sat–Mon", which would read as a five-day run to anyone scanning left to
 * right — the calendar week does not wrap on a profile the way it does in the
 * artist's head.
 */
private fun weekdayRunLabel(ordered: List<String>): String {
    if (ordered.size == WizardWeekdays.size) return "Every day"
    if (ordered.size == 1) return ordered.first()
    val indices = ordered.map { WizardWeekdays.indexOf(it) }
    val contiguous = indices.zipWithNext().all { (a, b) -> b == a + 1 }
    // Two adjacent days read better named than dashed: "Sat–Sun" and "Sat, Sun"
    // are the same length and the second is unambiguous.
    return if (contiguous && ordered.size > 2) {
        "${ordered.first()}–${ordered.last()}"
    } else {
        ordered.joinToString(", ")
    }
}

/**
 * The half of the badge that describes WHEN, derived from the stored slots.
 *
 * The stored vocabulary is clock times ("7:30 PM") because that is what the
 * client's booking grid renders and what `artists.default_time_slots` holds. A
 * badge cannot carry six clock times, so they collapse into the two words a
 * host actually filters on.
 */
private fun timeOfDayLabel(slots: Set<String>): String {
    val hours = slots.mapNotNull(::slotHour24)
    if (hours.isEmpty()) return "evenings"
    val late = hours.all { it >= LATE_NIGHT_HOUR }
    val early = hours.all { it < EVENING_HOUR }
    return when {
        late -> "late nights"
        early -> "afternoons"
        hours.any { it < EVENING_HOUR } -> "afternoons & evenings"
        else -> "evenings"
    }
}

/** "7:30 PM" → 19. Null for anything that is not one of our own slot strings. */
internal fun slotHour24(slot: String): Int? {
    val match = SLOT_PATTERN.find(slot.trim()) ?: return null
    val hour12 = match.groupValues[1].toIntOrNull() ?: return null
    val pm = match.groupValues[3].uppercase() == "PM"
    return when {
        pm && hour12 == 12 -> 12
        pm -> hour12 + 12
        hour12 == 12 -> 0
        else -> hour12
    }
}

private val SLOT_PATTERN = Regex("""^(\d{1,2}):(\d{2})\s*([AaPp][Mm])${'$'}""")
private const val EVENING_HOUR = 17
private const val LATE_NIGHT_HOUR = 22

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
 * May the artist move between steps at all right now?
 *
 * Not while a publish is in flight. `publish()` snapshots the form before its
 * three round trips, so anything edited during that window reaches neither the
 * server nor the draft — the successful publish clears the draft — and the
 * publish ends by forcing the step to Done, yanking the artist off whatever they
 * had opened. [wizardCanAdvance] already refused the CTA during a publish; the
 * preview's per-section EDIT jumps and system Back did not, and they are the two
 * ways out of the Preview step.
 */
fun wizardMayChangeStep(state: WizardUiState): Boolean = !state.isPublishing

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

// ── Copy ────────────────────────────────────────────────────────────────────

/**
 * The step headline — one plain sentence, set in the design's 26/700 screen
 * title.
 *
 * The old dark design split this into a lead/accent/tail triple so one word
 * could be tinted and italicised. The light design has no second voice to switch
 * into: it asks the artist a question in one weight and one colour, and the only
 * accent on the screen is the CTA. So the triple is gone and this returns a
 * String.
 */
fun wizardStepTitle(step: WizardStep): String = when (step) {
    WizardStep.Identity -> "Who's playing?"
    WizardStep.Location -> "Where do you play?"
    WizardStep.Pricing -> "What do you charge?"
    WizardStep.Tech -> "What must the venue provide?"
    WizardStep.Availability -> "When are you open?"
    WizardStep.Cover -> "Your cover"
    WizardStep.Socials -> "Where can we hear you?"
    WizardStep.Bio -> "Say it in your words"
    WizardStep.Samples -> "Add a few clips"
    WizardStep.Preview -> "Preview"
    WizardStep.Done -> "You're live."
}

fun wizardStepSubtitle(step: WizardStep): String = when (step) {
    WizardStep.Identity -> "This is the name on your profile and in search."
    WizardStep.Location -> "Base city routes gigs to you. Travel is quoted on top."
    WizardStep.Pricing -> "Hosts see one all-in number. Travel is added on top, never taken out of your fee."
    WizardStep.Tech -> "Hosts see this before they book, so nothing derails load-in."
    WizardStep.Availability -> "Hosts see this on your profile and filter search by it."
    WizardStep.Cover -> "One photo. This is the first thing a host sees."
    WizardStep.Socials -> "Links feed the social proof part of your Bookability Score."
    WizardStep.Bio -> "A short bio and the tags hosts filter by."
    WizardStep.Samples -> "30 seconds to 2 minutes each. Live recordings beat studio ones."
    WizardStep.Preview -> "Exactly what clients see"
    WizardStep.Done -> ""
}

/**
 * The CTA label.
 *
 * Optional steps flip to "Skip for now" the moment they are empty, which is the
 * only signal the artist gets that continuing past them costs nothing. A
 * disabled button with unchanged copy reads as a broken form instead.
 *
 * The Pricing step names its destination ("Next — tech rider") because it is the
 * one step where the artist has just been shown numbers and needs to know that
 * Continue is not Publish.
 */
fun wizardCtaLabel(state: WizardUiState): String = when (state.step) {
    WizardStep.Preview -> if (state.isPublishing) "Publishing…" else "Publish my profile"
    WizardStep.Done -> "Open my dashboard"
    WizardStep.Pricing -> "Next — tech rider"
    WizardStep.Cover -> if (state.pendingCover == null) "Skip for now" else "Continue"
    WizardStep.Samples -> if (state.pendingSamples.isEmpty()) "Skip for now" else "Continue"
    WizardStep.Bio -> if (state.bio.isBlank()) "Skip for now" else "Continue"
    WizardStep.Socials -> if (!wizardStepIsFilled(state, WizardStep.Socials)) "Skip for now" else "Continue"
    else -> "Continue"
}

/**
 * The quiet line UNDER the CTA, or null when the step has nothing to add.
 *
 * The design puts one there on five screens and it is doing three different
 * jobs: an escape hatch on the optional steps ("Skip for now", rendered as a
 * tappable line beside a filled Continue), a progress estimate on Pricing, and a
 * reassurance on Preview. Returning them from one place keeps the footer from
 * growing a `when` per job.
 */
fun wizardFooterNote(state: WizardUiState): String? = when (state.step) {
    WizardStep.Pricing -> "${wizardProgressLabel(state.step)} · takes about 6 minutes"
    WizardStep.Preview -> "You can keep editing after you publish"
    // Only when the CTA still says Continue: the label already says "Skip for
    // now" when the step is empty, and repeating it under the button reads as
    // two different skips.
    WizardStep.Socials, WizardStep.Bio, WizardStep.Samples ->
        "Skip for now".takeIf { wizardCtaLabel(state) == "Continue" }
    else -> null
}

/**
 * The "01 / 09" counter on the step bar.
 *
 * Both halves zero-padded, because the alternative jitters: "9 / 10" and
 * "10 / 10" are different widths, and the counter sits at the trailing edge of a
 * bar whose other half is a progress track that must not move under it. Padding
 * the total as well keeps the design's two-digit shape when the flow has fewer
 * than ten form steps, which it does.
 */
fun wizardStepCounter(step: WizardStep): String? {
    val index = wizardProgressIndex(step) ?: return null
    val total = wizardProgressTotal().toString().padStart(2, '0')
    return "${(index + 1).toString().padStart(2, '0')} / $total"
}

/**
 * "6 of 10" — how much of the form the Save & exit sheet is promising to keep.
 *
 * Counts steps LEFT BEHIND, not the one being looked at, so the number agrees
 * with the filled segments on the bar above it. Claiming the current step is
 * saved would be the one number on that sheet that is not quite true — the
 * artist may be standing on it with the field still empty.
 */
fun wizardSavedSoFarLabel(step: WizardStep): String =
    "${wizardProgressFilled(step)} of ${wizardProgressTotal()}"

// The public address the profile will answer on is `shareLinkUrl` in
// `EpkLogic` — the same builder the press-kit editor's Copy row and the profile
// share sheet already use. The wizard had its own, which rendered the handle as
// `artistant.in/@tiltcollective`; the app shares `artistant.in/tiltcollective`.
// The wizard's copy was the one an artist is most likely to paste to a venue,
// having just been told it is where their profile lives, and it is the one that
// does not resolve. The design mock draws the `@`, but a link that has to work
// beats a link that has to match a mock, and one builder is what keeps the two
// surfaces from drifting again.

// ── Preview: every section, and the step that owns it ────────────────────────

/**
 * One row of the preview list: what the section holds, and where Edit goes.
 *
 * The target is the [WizardStep] itself, never a position in the flow. A row
 * that remembers "step 8" is a row that silently points at the wrong screen the
 * next time the flow gains or loses one — and the flow order is a product
 * decision that has already been changed once (see [WizardFlowOrder]). Carrying
 * the enum means a reordered wizard cannot misroute an Edit chip, only
 * [WizardFlowOrder] can, in one place.
 *
 * Built as data rather than inline in the Composable so the mapping is a thing a
 * test can read: "does every section's Edit land on the step that owns its
 * fields?" is exactly the question that cannot be answered from a screenshot,
 * because both screens look plausible.
 */
data class WizardPreviewRow(
    val label: String,
    val value: String,
    val filled: Boolean,
    val step: WizardStep,
)

/**
 * Every section as a row that states what it holds.
 *
 * Skipped steps say "Not added" rather than disappearing — the artist should
 * discover a thin profile here, where one tap fixes it, and not from a week of
 * silence. The value line is the point: "2 tiers · ₹15,000–₹38,000" is a fact
 * they can check against what they meant, where "Packages ›" is a door they have
 * to open to find out.
 *
 * The cover hero and the identity header carry the other two Edit jumps
 * ([WizardStep.Cover] and [WizardStep.Identity]) because they render as the
 * picture and the headline rather than as rows; between them and this list,
 * every step the artist filled in has a way back.
 */
fun wizardPreviewRows(state: WizardUiState): List<WizardPreviewRow> {
    val badge = availabilityBadge(state.daysAvailable, state.timeSlots)
    return listOf(
        // Base city lives on the LOCATION step, not identity — it is rendered up
        // in the identity header beside genre and category (that is where a
        // client reads it), and an Edit chip that followed the picture would land
        // the artist on a screen with no city field on it.
        WizardPreviewRow(
            label = "Where you play",
            value = wizardLocationSummary(state),
            filled = state.baseCity.isNotBlank(),
            step = WizardStep.Location,
        ),
        WizardPreviewRow(
            label = "Bio",
            value = if (state.bio.isBlank()) NOT_ADDED else "${state.bio.length} characters",
            filled = state.bio.isNotBlank(),
            step = WizardStep.Bio,
        ),
        WizardPreviewRow(
            label = "Packages",
            value = wizardPackagesSummary(state),
            filled = state.previewPackages.isNotEmpty(),
            step = WizardStep.Pricing,
        ),
        WizardPreviewRow(
            label = "Tech rider",
            value = if (state.techItems.isEmpty()) {
                NOT_ADDED
            } else {
                "${state.techItems.size} line${plural(state.techItems.size)}"
            },
            filled = state.techItems.isNotEmpty(),
            step = WizardStep.Tech,
        ),
        WizardPreviewRow(
            label = "Availability",
            value = badge ?: "No badge yet",
            filled = badge != null,
            step = WizardStep.Availability,
        ),
        WizardPreviewRow(
            label = "Samples",
            value = if (state.pendingSamples.isEmpty()) {
                NOT_ADDED
            } else {
                "${state.pendingSamples.size} clip${plural(state.pendingSamples.size)} " +
                    "— upload after you publish"
            },
            filled = state.pendingSamples.isNotEmpty(),
            step = WizardStep.Samples,
        ),
        // The service picker sits ON the bio step, so that is the step that owns
        // it — not the one whose name matches the row.
        WizardPreviewRow(
            label = "Services",
            value = if (state.serviceTags.isEmpty()) {
                NOT_ADDED
            } else {
                ServiceTags.labels(state.serviceTags).joinToString(", ")
            },
            filled = state.serviceTags.isNotEmpty(),
            step = WizardStep.Bio,
        ),
        WizardPreviewRow(
            label = "Socials",
            value = wizardSocialSummary(state),
            filled = wizardStepIsFilled(state, WizardStep.Socials),
            step = WizardStep.Socials,
        ),
    )
}

/** What a skipped section says. One string, so eight rows cannot word it eight ways. */
private const val NOT_ADDED = "Not added"

private fun plural(count: Int): String = if (count == 1) "" else "s"

/** "Bengaluru · Up to 150 km". The radius is draft-only, and the step says so. */
private fun wizardLocationSummary(state: WizardUiState): String {
    val city = state.baseCity.trim()
    if (city.isEmpty()) return NOT_ADDED
    return "$city · ${travelRadiusLabel(state.travelRadiusKm)}"
}

/** "2 tiers · ₹15,000–₹38,000", derived through the same filter publish uses. */
private fun wizardPackagesSummary(state: WizardUiState): String {
    val savable = state.previewPackages
    if (savable.isEmpty()) return "No publishable tier yet"
    val prices = savable.map { it.price }
    val range = if (prices.min() == prices.max()) {
        formatInr(prices.min())
    } else {
        "${formatInr(prices.min())}–${formatInr(prices.max())}"
    }
    return "${savable.size} tier${plural(savable.size)} · $range"
}

private fun wizardSocialSummary(state: WizardUiState): String {
    val present = buildList {
        if (state.instagramHandle.isNotBlank()) add("Instagram")
        if (state.spotifyArtistUrl.isNotBlank()) add("Spotify")
        if (state.youtubeChannelUrl.isNotBlank()) add("YouTube")
    }
    return if (present.isEmpty()) NOT_ADDED else present.joinToString(", ")
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

/** The line a publish failure falls back to when nothing better can be said. */
const val WIZARD_PUBLISH_FAILED = "Couldn't publish. Try again."

/**
 * What the Preview step says after a publish threw.
 *
 * Typed on [AppError] only. Those messages are ours — written to be read by an
 * artist — and the handle collision gets the one sentence that names the fix.
 * Anything else is raw platform text: a PostgREST dump, an OkHttp stack message,
 * an OutOfMemoryError's allocation figures. None of that is a sentence to put in
 * front of someone who just tapped Publish, so it goes to Timber and the artist
 * gets the line that is both true and actionable.
 */
fun wizardPublishFailureMessage(error: Throwable): String = when {
    error is AppError.UniqueViolation -> "That handle is already taken."
    error is AppError -> error.message?.takeIf { it.isNotBlank() } ?: WIZARD_PUBLISH_FAILED
    else -> WIZARD_PUBLISH_FAILED
}

/**
 * The state a failed publish leaves behind.
 *
 * The flag reset is the load-bearing part. `publish()` used to catch [Exception]
 * and a `Throwable` that is not one — a `LinkageError` off a bad OEM split, a
 * `NoClassDefFoundError`, an OOM mid-upload — walked straight past every arm
 * with `isPublishing` still true. That state disables the CTA, refuses every
 * step change ([wizardMayChangeStep]) and narrates "Publishing…" forever: the
 * wizard is a gate with no screen behind it, so the artist's only way out was to
 * kill the app. A crash would at least have said something.
 */
fun wizardPublishFailed(state: WizardUiState, error: Throwable): WizardUiState =
    state.copy(
        isPublishing = false,
        publishPhase = WizardPublishPhase.Idle,
        publishError = wizardPublishFailureMessage(error),
    )

// ── Bio counter and guidance ─────────────────────────────────────────────────

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

/**
 * What good looks like, at this length.
 *
 * The design's note on the bio step is that the hint "says what good looks like
 * instead of only counting characters", and that is a different sentence at
 * every stage of writing: an empty box needs a prompt, one sentence needs a
 * second, two sentences are finished, and a bio pressed against the ceiling
 * needs to be told that it is about to stop accepting keystrokes. A single
 * static line would be wrong three times out of four.
 *
 * The thresholds are in characters rather than in sentences because a sentence
 * counter would have to parse prose, and it would be wrong about "Bengaluru-
 * based. Weddings, pubs, brand nights." — three full stops, one sentence.
 */
fun bioGuidance(length: Int): String = when {
    length == 0 -> "Two sentences is plenty. What you sound like, and what you'll play."
    length < BIO_ONE_SENTENCE -> "Keep going — one more line about your live sets."
    length < BIO_TWO_SENTENCES -> "Good. A second sentence about the rooms you play would finish it."
    length < WIZARD_BIO_MAX -> "Two sentences is plenty."
    else -> "That's the limit — trim a line to keep editing."
}

/** Roughly one sentence of prose, and roughly two. */
private const val BIO_ONE_SENTENCE = 60
private const val BIO_TWO_SENTENCES = 140

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

/**
 * The array a publish should send to `artists.service_tags`, or null for "send
 * nothing".
 *
 * The column is WHOLE-SET: what goes replaces what is stored. The wizard is
 * re-enterable — a dropped `setup_complete` write, an artist pushed back through
 * the gate — and `service_tags` has a second writer in the press-kit editor, so
 * an artist can reach Publish with services already on their row. Sending the
 * picker's list on top of a set this session never read is how a wizard re-entry
 * silently un-publishes them, and the same slugs back Discover's services
 * filter, so the artist stops being findable by what they actually do.
 *
 * @param picked what the picker holds.
 * @param published the row's current array, or null when it could not be read.
 * @param seeded whether [picked] STARTED as a copy of the published array. When
 *   it did, an unticked chip is a real untick and the list stands as written.
 *   When it did not, the picker opened empty and every tick is an ADDITION to a
 *   set nobody has seen, so the only honest write is the union — and with
 *   nothing to union against, no write at all.
 */
fun wizardServiceTagsToPublish(
    picked: List<String>,
    published: List<String>?,
    seeded: Boolean,
): List<String>? = when {
    // Nothing to say. Never an empty whole-set write: on a re-entry that is a
    // deletion the artist did not ask for, and on a first run it is a no-op with
    // a round trip attached.
    picked.isEmpty() -> null
    seeded -> ServiceTags.normalize(picked)
    published == null -> null
    // Union, published first: the artist's stored order survives and the cap
    // falls on the tail, so an over-cap row written elsewhere loses the ticks
    // made here rather than the claims already on the profile.
    else -> ServiceTags.normalize(published + picked)
}

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
