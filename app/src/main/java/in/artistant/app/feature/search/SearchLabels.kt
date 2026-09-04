package `in`.artistant.app.feature.search

import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.common.util.formatInrShort
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.SearchCatalog
import `in`.artistant.app.data.model.SearchFacets
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Every string Search puts on screen that is computed rather than typed lives in
// this file, and every one of them is a pure function.
//
// That is not tidiness. Search is the screen where the app makes CLAIMS — "40
// acts free", "3 filters active", "nothing matches X" — and each of those has
// already shipped wrong at least once in some form: a count that said "1 result"
// for zero, a budget summary that announced a range nobody had set, a filter
// badge that counted something the query did not actually post. Claims belong
// where a JVM test can hold them to account.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * How many results the header should claim it is showing.
 *
 * The trailing "+" is doing real work — the list pages, so the number on screen
 * is a floor rather than a total, and a bare "12 acts" above a list that keeps
 * growing as you scroll is simply wrong.
 */
internal fun searchResultCountLabel(
    count: Int,
    isLoading: Boolean,
    canLoadMore: Boolean,
): String {
    if (isLoading && count == 0) return "Searching…"
    val base = if (count == 1) "1 act" else "$count acts"
    return if (canLoadMore) "$base+" else base
}

/**
 * "Bands · Bengaluru" — what the results screen calls the search it is showing
 * (screen 03).
 *
 * Built from what the user actually asked for, in the order they would say it:
 * the words they typed, then the act types, then the city. Falls back to the
 * screen's own name rather than to an empty bar — this composes to "" for a
 * search narrowed only by price or score, which is a legal state.
 */
internal fun searchResultsTitle(state: SearchUiState): String {
    val parts = buildList {
        state.query.trim().takeIf { it.isNotEmpty() }?.let(::add)
        state.categories.takeIf { it.isNotEmpty() }?.let { add(it.sorted().joinToString(", ")) }
        state.city?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
    }
    return if (parts.isEmpty()) "Search" else parts.joinToString(" · ")
}

/**
 * "Sat 12 Oct · 40 acts free" — the line under [searchResultsTitle].
 *
 * "free" is appended ONLY when a date filter is actually posted. Without a date
 * the count is of acts that match, which is a different claim from acts that are
 * free on a night, and conflating the two is the kind of thing a host books
 * against.
 */
internal fun searchResultsSubtitle(state: SearchUiState): String {
    val count = searchResultCountLabel(
        count = state.results.size,
        isLoading = state.isLoading,
        canLoadMore = state.canLoadMore,
    )
    val scoped = if (state.dateIso != null && !state.isLoading) "$count free" else count
    val date = state.dateIso?.let(::searchDateLabel)
    return listOfNotNull(date, scoped).joinToString(" · ")
}

/**
 * "Sat 12 Oct" from a `yyyy-MM-dd` filter value.
 *
 * An unparseable value is returned verbatim rather than dropped: the ISO string
 * came from a control in this app, so a value we cannot read is a bug worth
 * seeing on screen, not one worth hiding behind a blank.
 */
internal fun searchDateLabel(iso: String): String = try {
    val date = LocalDate.parse(iso)
    val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.US)
    "$day ${date.dayOfMonth} $month"
} catch (_: DateTimeParseException) {
    iso
}

/** The same date without its weekday — the form a summary chip carries. */
internal fun searchDateChipLabel(iso: String, flexDays: Int): String {
    val base = try {
        val date = LocalDate.parse(iso)
        "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.US)}"
    } catch (_: DateTimeParseException) {
        iso
    }
    return if (flexDays > 0) "$base ±${flexDays}d" else base
}

/**
 * The Date section's preset chips (screen 15).
 *
 * Presets rather than a calendar: `p_date` takes one day, the useful days are all
 * within the next fortnight, and a full picker on top of a sheet is a second
 * modal for a one-field decision. A picker can replace this without touching the
 * ViewModel contract.
 *
 * **Every weekday shortcut goes through [TemporalAdjusters.nextOrSame].**
 * `LocalDate.with(DayOfWeek.SATURDAY)` resolves within the CURRENT ISO week,
 * which starts on Monday — so on a Sunday it hands back yesterday, and "This Sat"
 * posted a `p_date` in the past that no artist can be free on. `nextOrSame` is
 * the only form that means what the label says on all seven days.
 *
 * Pure and `internal` so the Sunday case can be pinned by a JVM test; the sheet
 * passes the day in rather than reading the clock inside a composable.
 */
internal fun searchDatePresets(today: LocalDate): List<Pair<String?, String>> {
    val saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
    return listOf(
        null to "Any day",
        today.toString() to "Today",
        today.plusDays(1).toString() to "Tomorrow",
        saturday.toString() to "This Sat",
        saturday.plusWeeks(1).toString() to "Next Sat",
    )
}

/** Which filter a summary chip stands for, and therefore what dropping it clears. */
enum class SearchFilterKind { City, Date, Category, EventType, Service, Price, Score }

/** One chip in the sheet's "active filters" row (screen 104). */
data class SearchFilterChip(val kind: SearchFilterKind, val label: String)

/**
 * The active-filter summary (screen 104).
 *
 * **One chip per FILTER, not per value** — two act types are one "Band, DJ" chip,
 * because the design's own row draws it that way and because the thing a user
 * undoes is "the act-type narrowing", not one member of a set they picked from a
 * multi-select. The list is in the order the sheet presents the filters, so the
 * row reads top-to-bottom the same way the sheet below it does.
 *
 * Its length is [SearchUiState.activeFilterCount] by construction, which is the
 * point: the badge on the button, the "N filters active" line and the number of
 * chips are one computation, and cannot disagree the way they did when each
 * surface counted for itself.
 */
internal fun searchFilterChips(state: SearchUiState): List<SearchFilterChip> = buildList {
    state.city?.let { add(SearchFilterChip(SearchFilterKind.City, it)) }
    state.dateIso?.let {
        add(SearchFilterChip(SearchFilterKind.Date, searchDateChipLabel(it, state.flexDays)))
    }
    state.categories.takeIf { it.isNotEmpty() }?.let {
        add(SearchFilterChip(SearchFilterKind.Category, it.sorted().joinToString(", ")))
    }
    state.eventType?.let { add(SearchFilterChip(SearchFilterKind.EventType, it)) }
    state.services.takeIf { it.isNotEmpty() }?.let { slugs ->
        add(SearchFilterChip(SearchFilterKind.Service, slugs.sorted().joinToString(", ", transform = ::serviceLabel)))
    }
    if (state.isPriceNarrowed) {
        add(
            SearchFilterChip(
                SearchFilterKind.Price,
                "${formatInrShort(state.minPrice)}–${formatInrShort(state.maxPrice)}",
            ),
        )
    }
    if (state.minScore > 0) {
        add(SearchFilterChip(SearchFilterKind.Score, "Score ${state.minScore}+"))
    }
}

/** A service slug's display label, or the slug when the catalogue has no entry. */
internal fun serviceLabel(slug: String): String =
    SearchCatalog.services.firstOrNull { it.first == slug }?.second ?: slug

/** "6 filters active · tap any chip to drop it" — the line under the chip row. */
internal fun searchFilterSummaryLine(count: Int): String =
    if (count == 1) {
        "1 filter active · tap the chip to drop it"
    } else {
        "$count filters active · tap any chip to drop it"
    }

/**
 * The Budget row's collapsed summary.
 *
 * Reads [SearchUiState.isPriceNarrowed] — the same predicate the filter badge and
 * the `search_artists` price arguments read — instead of comparing the selection
 * against the `SearchTuning` ₹10k/₹80k constants. Those constants describe no
 * roster: once `price_histogram` lands, an untouched selection SNAPS onto the
 * learned span, so on any roster that isn't exactly ₹10k–₹80k the old comparison
 * failed and a cold sheet announced a price range nobody had set, sitting under a
 * badge that correctly read 0. Third source of truth for the one thing
 * [SearchUiState.activePriceFloor] is documented as owning.
 *
 * Takes the whole state rather than loose numbers on purpose — the bug WAS an
 * inconsistent triple, and a signature that can express one proves nothing.
 */
internal fun searchBudgetSummary(state: SearchUiState): String =
    if (!state.isPriceNarrowed) {
        "Any"
    } else {
        "${formatInr(state.minPrice)}–${formatInr(state.maxPrice)}"
    }

/**
 * What the sheet's primary CTA claims it will show — a straight port of iOS
 * `SearchFilterSheet.applyLabel`.
 *
 * It replaces a `coerceAtLeast(1)` that had been added to dodge "Show 0 artists"
 * and instead turned a zero into a claim — a cold Search tab (nothing typed, no
 * filter, so [SearchUiState.hasActiveQuery] false and the "No matches" branch
 * skipped) opened the sheet on "Show 1 artist".
 *
 * Nothing set at all is its own answer: closing the sheet then goes back to the
 * browse rails, so the honest label is the unnumbered one.
 *
 * The trailing "+" carries the same caveat as the header's count — the list
 * pages, so the number is a floor rather than a total.
 */
internal fun searchApplyLabel(
    resultCount: Int,
    hasActiveQuery: Boolean,
    isLoading: Boolean,
    canLoadMore: Boolean,
): String = when {
    isLoading -> "Searching…"
    !hasActiveQuery -> "Show artists"
    resultCount == 0 -> "No matches"
    canLoadMore -> "Show $resultCount+ acts"
    resultCount == 1 -> "Show 1 act"
    else -> "Show $resultCount acts"
}

/**
 * The empty-results body (screen 57).
 *
 * Says which of the two things is narrowing the search, because the two have
 * different escapes: a query is fixed by retyping it, filters by clearing them,
 * and a sentence that mentions only one sends half the users to the wrong
 * control. Numbers under ten are spelled, which is the design's own copy.
 */
internal fun searchNoResultsBody(query: String, filterCount: Int): String {
    val q = query.trim()
    val filters = spelledFilters(filterCount)
    return when {
        q.isNotEmpty() && filters != null -> "Nothing matches \"$q\" with your $filters on."
        q.isNotEmpty() -> "Nothing matches \"$q\"."
        filters != null -> "Nothing matches your $filters."
        else -> "Nothing matches this search."
    }
}

/** The two buttons under an empty result (screen 57). */
internal data class SearchEmptyActions(val primary: String?, val secondary: String?)

/**
 * What the empty state offers to do about itself.
 *
 * The design's principle is that every empty state carries an action, and its
 * own screen offers two — "Notify me when one joins" over "Clear filters". The
 * first has nowhere to be stored in this backend (no search-alerts table, and
 * `waitlist_signups` denies every client read and write), so it is omitted rather
 * than shipped as a promise nothing can keep, and what is left has to cover both
 * ways a search can be over-narrowed: the filters and the words.
 *
 * With both on, clearing the FILTERS is primary — it is the larger cut, and the
 * words are the thing the user chose to say.
 */
internal fun searchNoResultsActions(query: String, filterCount: Int): SearchEmptyActions {
    val hasQuery = query.isNotBlank()
    val hasFilters = filterCount > 0
    return when {
        hasFilters && hasQuery -> SearchEmptyActions("Clear filters", "Clear search")
        hasFilters -> SearchEmptyActions("Clear filters", null)
        hasQuery -> SearchEmptyActions("Clear search", null)
        else -> SearchEmptyActions(null, null)
    }
}

/** "three filters" / "1 filter", or null for none. */
private fun spelledFilters(count: Int): String? = when {
    count <= 0 -> null
    count == 1 -> "one filter"
    else -> "${spellOut(count)} filters"
}

/**
 * Small numbers as words.
 *
 * Only up to nine: past that the word is longer than the digits and stops
 * helping, and a filter set that large is not something a sentence should be
 * counting out loud anyway.
 */
private fun spellOut(n: Int): String = when (n) {
    2 -> "two"
    3 -> "three"
    4 -> "four"
    5 -> "five"
    6 -> "six"
    7 -> "seven"
    8 -> "eight"
    9 -> "nine"
    else -> n.toString()
}

/** One row of the typing-time suggestion list (screen 14). */
sealed interface SearchSuggestion {
    val key: String

    /** A search TERM the roster can answer, with the count that answers it. */
    data class Term(val text: String, val detail: String) : SearchSuggestion {
        override val key: String get() = "term:$text"
    }

    /** A specific act already in the live result page. */
    data class Act(val artist: Artist) : SearchSuggestion {
        override val key: String get() = "act:${artist.id}"
    }
}

/**
 * What to offer under the field while the user is typing (screen 14).
 *
 * The design's note is "counts sit next to every suggestion", and the counts here
 * are the ONLY real ones the backend publishes: `search_facets` returns a row per
 * category and per city with its artist count. Nothing else on this list carries
 * a number, because nothing else has one — an invented "31 acts" beside a term is
 * exactly the fabrication the redesign's data-honesty rule forbids.
 *
 * Terms come first and acts are interleaved after the first term, matching the
 * design's own ordering (a term, a specific act, a term). Acts are taken from the
 * live result page, so they are literally what pressing Search would show.
 */
internal fun searchSuggestions(
    query: String,
    facets: SearchFacets,
    results: List<Artist>,
    limit: Int = 6,
): List<SearchSuggestion> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()

    val terms = buildList {
        facets.categories
            .filter { it.label.contains(q, ignoreCase = true) && it.count > 0 }
            .forEach { add(SearchSuggestion.Term(it.label, "${it.count} acts")) }
        facets.cities
            .filter { it.label.contains(q, ignoreCase = true) && it.count > 0 }
            .forEach { add(SearchSuggestion.Term(it.label, "${it.count} acts in ${it.label}")) }
    }
    val acts = results.map { SearchSuggestion.Act(it) }

    // Interleave so the list never reads as two stacked sections without labels:
    // term, act, term, act… then whatever is left of the longer side.
    val merged = buildList {
        val a = terms.iterator()
        val b = acts.iterator()
        while (a.hasNext() || b.hasNext()) {
            if (a.hasNext()) add(a.next())
            if (b.hasNext()) add(b.next())
        }
    }
    return merged.take(limit)
}
