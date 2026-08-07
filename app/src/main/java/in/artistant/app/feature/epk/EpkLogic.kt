package `in`.artistant.app.feature.epk

import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.repository.PackageDraft

/**
 * Every decision the EPK editor makes, extracted from the Composables and the
 * ViewModel so it can actually be covered.
 *
 * The test classpath here is junit + kotlinx-coroutines-test only — no
 * Robolectric, no Compose test rule — so anything that lives inside a
 * `@Composable` or behind Hilt is unreachable from a unit test by construction.
 * The editor's risky parts are all *arithmetic and guards* (what counts as a
 * savable tier, when a whole-set replace is allowed to run, where a moved photo
 * lands), which is exactly the sort of thing that fails silently on a device.
 * So they live here as free functions over plain data, and the ViewModel becomes
 * a thin shell that sequences them.
 */

/**
 * Sample ceiling, mirroring iOS. It is a bucket-bandwidth budget, not a product
 * rule: the wizard and the editor both have to honour the same number or the
 * post-wizard add path becomes a way around the wizard's cap.
 */
const val MAX_SAMPLES: Int = 6

/**
 * Photo ceiling. Enforced server-side on `artist_media`; repeated here so the
 * "Add photo" affordance can disappear at the cap instead of letting the artist
 * pick a file, wait for an upload, and then read a rejection.
 */
const val MAX_PHOTOS: Int = 6

/** Longest price we will accept, in rupees. Guards a paste, not a business rule. */
const val MAX_PRICE_INR: Int = 10_000_000

/**
 * Stock tech-rider items, same set as iOS. Presets exist because a rider is a
 * checklist an artist recognises rather than composes — typing "4 vocal mics"
 * from scratch every time is how riders end up inconsistent across artists and
 * therefore unsearchable.
 */
val TECH_PRESETS: List<String> = listOf(
    "4 vocal mics",
    "2 wedge monitors",
    "1 DI box",
    "Drum kit (5pc)",
    "Mixing console (8ch+)",
    "Stage lights × 4",
    "Power: 16A × 2",
)

// ── Pricing tiers ────────────────────────────────────────────────────────────

/**
 * One row of the pricing editor.
 *
 * Price is a **String**, not an Int, because this is a live text field: an Int
 * cannot represent "the artist has cleared the field and not typed the new
 * number yet", and coercing an empty field to 0 mid-keystroke is how an editor
 * ends up autosaving a free gig. [toDraft] is where it becomes a number, and it
 * refuses rather than defaults.
 *
 * [key] is a stable identity for the list — server rows reuse their row id, new
 * rows get a generated one. It is passed in rather than generated here so the
 * function stays pure and the tests stay deterministic.
 */
data class PackageRow(
    val key: String,
    val name: String = "",
    val duration: String = "",
    val price: String = "",
    val popular: Boolean = false,
)

/** Server rows → editor rows. */
fun packageRows(packages: List<ArtistPackage>): List<PackageRow> =
    packages.map {
        PackageRow(
            key = it.id,
            name = it.name,
            duration = it.duration,
            price = it.price.toString(),
            popular = it.popular,
        )
    }

/**
 * Keystroke filter for the price field: digits only, and never longer than
 * [MAX_PRICE_INR].
 *
 * Filtering on the way IN rather than validating on the way out means the field
 * can never hold a value the save path will later reject — there is no state in
 * which the artist sees a number on screen that the editor considers unsavable.
 * Leading zeros are stripped so "0" + "5" reads 5, not 05, but a lone "0" is
 * preserved: the artist may be mid-way to typing 500.
 */
fun sanitizePriceInput(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    if (digits.isEmpty()) return ""
    val trimmed = digits.trimStart('0').ifEmpty { "0" }
    val value = trimmed.toLongOrNull() ?: return ""
    return if (value > MAX_PRICE_INR) MAX_PRICE_INR.toString() else trimmed
}

/** The typed price as a number, or null when the field cannot yield one. */
fun parsePrice(raw: String): Int? = raw.filter { it.isDigit() }.toIntOrNull()

/**
 * A row is savable when it has a name AND a price above zero.
 *
 * Both halves matter and for different reasons. A nameless tier renders as a
 * blank line with a price beside it on the client's booking screen; a ₹0 tier
 * renders as a free booking. Neither is a state the artist meant to publish, and
 * a whole-set replace has no way to "partially" save, so an unsavable row has to
 * be excluded rather than sent.
 */
fun packageRowIsSavable(row: PackageRow): Boolean =
    row.name.isNotBlank() && (parsePrice(row.price) ?: 0) > 0

/**
 * Editor rows → the payload for `replace_packages`.
 *
 * Incomplete rows are DROPPED, not defaulted. The alternative — filling a blank
 * name with "Set" and a blank price with 0, which is what the previous Android
 * editor did — publishes a tier the artist never described at a price they never
 * chose, and does it silently because the row still looks half-typed on screen.
 *
 * `popular` round-trips per row and defaults to false. It used to be hardcoded
 * `true` on every write, which made the badge a constant and therefore
 * meaningless app-wide; `PackagePricing.popularBadgeIsMeaningful` is the reader
 * side of the same fact.
 */
fun packageDrafts(rows: List<PackageRow>): List<PackageDraft> =
    rows.filter(::packageRowIsSavable).map { row ->
        PackageDraft(
            name = row.name.trim(),
            durationLabel = row.duration.trim().ifBlank { "set" },
            priceInr = parsePrice(row.price) ?: 0,
            popular = row.popular,
        )
    }

/**
 * Editor rows → domain packages, for READ-ONLY derivation only.
 *
 * It exists so the editor's live "from ₹X" line can be computed by
 * `PackagePricing.fromPrice` — the one helper allowed to answer that question —
 * against what is currently typed rather than against what was last saved. Both
 * the public profile and this editor therefore go through the same arithmetic,
 * which is the whole reason that helper exists.
 *
 * Unsavable rows are excluded so a half-typed tier cannot momentarily become the
 * cheapest one and flash a wrong "from" figure while the artist types.
 */
fun previewPackages(rows: List<PackageRow>): List<ArtistPackage> =
    rows.filter(::packageRowIsSavable).map { row ->
        ArtistPackage(
            id = row.key,
            name = row.name.trim(),
            duration = row.duration.trim(),
            price = parsePrice(row.price) ?: 0,
            includes = emptyList(),
            popular = row.popular,
        )
    }

/**
 * Should the pricing editor bother rendering "Popular" controls as *meaningful*?
 *
 * A badge is a comparison. With one tier, or with every tier flagged, it splits
 * nothing — the artist is toggling a control that will render identically no
 * matter which way it sits. The editor says so rather than letting them discover
 * it by looking at their own public profile.
 *
 * This asks the question of the DRAFT rows (what is on screen right now), while
 * `PackagePricing.popularBadgeIsMeaningful` asks it of the saved set. Same rule,
 * two inputs — the editor needs the answer before the save lands.
 */
fun popularBadgeWouldMeanSomething(rows: List<PackageRow>): Boolean {
    val savable = rows.filter(::packageRowIsSavable)
    return savable.any { it.popular } && savable.any { !it.popular }
}

// ── Whole-set write guard ────────────────────────────────────────────────────

/**
 * The wipe guard, and the single most important function in this file.
 *
 * Packages, the tech rider and the photo order all persist through a **whole-set
 * replace**: the client sends the complete list and the server makes reality
 * match it. That is fine when the list on screen came from the server, and
 * catastrophic when it did not — an empty in-memory list plus one edit replaces
 * the artist's published tiers with nothing, from a device that never saw them.
 *
 * So a replace runs only when the read that produced the list actually
 * succeeded. A FAILED load leaves the gate shut, which renders the section
 * read-only: worse to use, impossible to lose data with. The Retry path reopens
 * it by re-running the load.
 *
 * (iOS additionally needs an echo-suppressor, because its editor observes the
 * store and hydration therefore *looks* like an edit. The Android shape has no
 * equivalent hazard: saves are triggered from explicit user-edit callbacks, and
 * assigning hydrated state calls none of them.)
 */
fun canReplaceWholeSet(hydrated: Boolean, hasSession: Boolean): Boolean =
    hydrated && hasSession

// ── Ordering ─────────────────────────────────────────────────────────────────

/**
 * Move one item and return the new list, or the list unchanged when the move is
 * a no-op or out of bounds.
 *
 * Returning the input on a bad index rather than throwing is deliberate: the
 * callers are tap handlers on a list that a background refresh can shorten
 * underneath them, and an out-of-date index is an ordinary race, not a bug worth
 * crashing over.
 */
fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from == to) return items
    if (from !in items.indices || to !in items.indices) return items
    val out = items.toMutableList()
    out.add(to, out.removeAt(from))
    return out
}

// ── Tech rider ───────────────────────────────────────────────────────────────

/**
 * Preset chip toggle. Removal is exact-match; addition appends so the rider
 * keeps the order the artist built it in (the server stores `position`, and a
 * rider read top-to-bottom by a sound engineer is an ordered document).
 */
fun toggleTechItem(items: List<String>, item: String): List<String> =
    if (items.any { it.equals(item, ignoreCase = true) }) {
        items.filterNot { it.equals(item, ignoreCase = true) }
    } else {
        items + item
    }

/**
 * Free-text add. Trims, drops blanks, and refuses a case-insensitive duplicate —
 * "4 Vocal Mics" under "4 vocal mics" is the same line item twice on a rider a
 * venue has to act on.
 */
fun addTechItem(items: List<String>, raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return items
    if (items.any { it.equals(trimmed, ignoreCase = true) }) return items
    return items + trimmed
}

// ── Links ────────────────────────────────────────────────────────────────────

/**
 * Make a pasted link openable.
 *
 * Artists paste "bandcamp.com/kaavya" far more often than they paste a scheme,
 * and a scheme-less string is not a URL — it will not open from a client's
 * profile view. Defaulting to https rather than http because every host worth
 * linking serves it and the fallback direction should be the safe one. An
 * existing scheme of any kind is left alone (mailto: and tel: are legitimate
 * "anywhere a client should land" targets).
 */
fun normalizeLinkUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    return if (trimmed.contains("://") || trimmed.startsWith("mailto:") || trimmed.startsWith("tel:")) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

/** A link needs both halves: a bare URL has nothing to label it in the list. */
fun linkIsSavable(label: String, url: String): Boolean =
    label.isNotBlank() && url.isNotBlank()

// ── Share link ───────────────────────────────────────────────────────────────

/**
 * The artist's public URL, or null when there isn't one yet.
 *
 * Null rather than a `artistant.in/yourhandle` placeholder: a fake link with a
 * Copy button beside it is worse than no link, because the artist copies it and
 * sends it to a venue. An artist without a handle has not published, and the
 * section says that instead.
 */
fun shareLinkUrl(handle: String?): String? {
    val trimmed = handle?.trim().orEmpty()
    return if (trimmed.isEmpty()) null else "artistant.in/$trimmed"
}

// ── Samples / photos capacity ────────────────────────────────────────────────

fun canAddSample(currentCount: Int, uploadInFlight: Boolean): Boolean =
    !uploadInFlight && currentCount < MAX_SAMPLES

fun canAddPhoto(currentCount: Int, uploadInFlight: Boolean): Boolean =
    !uploadInFlight && currentCount < MAX_PHOTOS

// ── Completeness ─────────────────────────────────────────────────────────────

/**
 * What the profile is still missing, in the order a client notices it.
 *
 * This is not a score or a gamified meter — it is the section list, filtered to
 * the empty ones. Its whole value is that the artist can see, from the top of
 * the editor, which of eight scrolls-worth of sections is the one still holding
 * their profile back, without scrolling all eight.
 *
 * Ordering is by client impact, not by page order: a profile with no cover photo
 * loses the browse grid before anyone reads its tech rider.
 */
data class EpkCompleteness(
    val complete: Int,
    val total: Int,
    val missing: List<String>,
) {
    val isComplete: Boolean get() = missing.isEmpty()
}

fun epkCompleteness(
    photoCount: Int,
    bio: String,
    packageCount: Int,
    sampleCount: Int,
    techCount: Int,
    socialCount: Int,
    linkCount: Int,
): EpkCompleteness {
    // Pairs, not a map: the ORDER is the point, and a plain map's iteration
    // order is not something to hang a UI list off.
    val checks = listOf(
        "a cover photo" to (photoCount > 0),
        "a bio" to bio.isNotBlank(),
        "a pricing tier" to (packageCount > 0),
        "an audio sample" to (sampleCount > 0),
        "a linked social account" to (socialCount > 0),
        "a tech rider" to (techCount > 0),
        "an external link" to (linkCount > 0),
    )
    return EpkCompleteness(
        complete = checks.count { it.second },
        total = checks.size,
        missing = checks.filterNot { it.second }.map { it.first },
    )
}

/**
 * How many of the three social platforms carry a value.
 *
 * Blank-but-present is treated as absent: the artist row stores empty strings as
 * readily as nulls depending on which write path last touched it, and "linked"
 * has to mean the same thing either way.
 */
fun socialLinkCount(spotify: String?, instagram: String?, youtube: String?): Int =
    listOf(spotify, instagram, youtube).count { !it.isNullOrBlank() }
