package `in`.artistant.app.domain.artist

import `in`.artistant.app.data.model.SearchCatalog

/**
 * The "what you offer" taxonomy behind `artists.service_tags` (text[], migration
 * 0073) — and, just as importantly, the reason the Discover services filter can
 * work at all.
 *
 * **Why a curated slug list rather than free text.** The column's own comment
 * describes it as free-form, and the server filter is a plain `service_tags &&
 * p_services` overlap. An overlap has no fuzziness in it: "DJ set", "dj set" and
 * "dj-set" are three different tags, and an artist who typed any of the first two
 * would be invisible to a client who ticked the chip that sends the third. So the
 * write side is pinned to the SAME nine slugs the filter sends
 * ([SearchCatalog.services]), which is what makes ticking a service actually
 * return the artists who offer it.
 *
 * That list is deliberately not re-declared here. It already existed as the
 * filter's vocabulary, and a second copy is how the two halves of an exact-match
 * filter drift apart — the failure would be silent, because a search that matches
 * nothing looks exactly like a city with no artists in it.
 *
 * Labels are hand-pinned per slug for the same reason they are on the reference
 * client: a generic title-caser renders "dj-set" as "Dj Set".
 */
object ServiceTags {

    /** (slug, label) in chip render order. One vocabulary, shared with search. */
    val catalog: List<Pair<String, String>> get() = SearchCatalog.services

    /** Every slug the picker offers, for validation. */
    val slugs: Set<String> get() = catalog.map { it.first }.toSet()

    /**
     * How many services one artist may publish.
     *
     * A cap exists because a profile that claims all nine says nothing — the
     * point of the section is telling a client what this artist is *for*. It is
     * generous enough that no honest artist hits it.
     */
    const val MAX_TAGS: Int = 6

    /**
     * Display text for a stored slug, echoing anything off-taxonomy back.
     *
     * The echo matters: the column is shared with another client and with
     * admin/backfill writes, so a tag this build has never heard of is an
     * ordinary thing to read. Rendering it verbatim shows the artist what their
     * profile actually says; dropping it would hide a claim they are still making
     * to clients.
     */
    fun label(slug: String): String =
        catalog.firstOrNull { it.first == slug }?.second ?: slug

    /** Labels for a stored set, in the artist's own stored order. */
    fun labels(tags: List<String>): List<String> = tags.map(::label)

    /**
     * Tick or untick one service, returning the new set.
     *
     * Returns the input unchanged when adding would exceed [MAX_TAGS] — refusing
     * at the boundary rather than silently truncating on the way to the server,
     * so what the artist sees selected is what is stored. Removal is never
     * refused, including from an over-cap set written elsewhere: an artist must
     * always be able to withdraw a claim.
     */
    fun toggle(current: List<String>, slug: String): List<String> =
        when {
            current.contains(slug) -> current.filterNot { it == slug }
            current.size >= MAX_TAGS -> current
            else -> current + slug
        }

    /**
     * Clean a set for READING: trims, drops blanks and de-duplicates (first
     * occurrence wins, so the artist's own ordering survives). Case-sensitive by
     * design — the slugs are lowercase by construction and case-folding an
     * off-taxonomy admin tag would rewrite a value this client did not author.
     *
     * No cap, and that is the point. The column is shared, so a row carrying more
     * than [MAX_TAGS] is an ordinary thing to read — and clamping on the way to
     * the SCREEN hides a claim the profile is still making while leaving the
     * artist's next edit to write the visible subset back over the whole column.
     * Same reasoning as [label] echoing an unknown slug: show what the row
     * actually says.
     */
    fun normalizeForDisplay(raw: List<String>): List<String> =
        raw.map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    /**
     * Clean a set on the way to the column: [normalizeForDisplay] plus the cap.
     *
     * The cap lives on the WRITE side only, where it is a rule about what this
     * client publishes rather than a rule about what it can be shown.
     */
    fun normalize(raw: List<String>): List<String> = normalizeForDisplay(raw).take(MAX_TAGS)
}
