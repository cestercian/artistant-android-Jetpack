package `in`.artistant.app.feature.discover

import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.common.util.formatInrShort
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Pure decisions behind the Discover masthead, hero card and rails.
 *
 * They live outside the composables so the rules are testable without a Compose
 * runtime, and so the *reasons* below survive a UI rewrite — which is exactly
 * what happened in the Sep-2026 redesign: the full-bleed hero pager became a
 * 262dp card under a titled header, and every rule in this file carried over
 * unchanged.
 */
object DiscoverHeroLogic {

    /**
     * Country-wide fallback for the masthead when the user has no city set.
     *
     * Not "Bangalore": the catalogue is national, and a hardcoded city is a
     * statement the app can't back up for a user in Chennai. "India" is true for
     * everyone, so the empty state stays honest rather than merely plausible.
     */
    const val PLACE_FALLBACK = "India"

    /**
     * The place name in the header's subtitle — the user's city, or
     * [PLACE_FALLBACK].
     *
     * Trims first: a whitespace-only value is a blank city that happens to be
     * non-null, and rendering "· Sat 12 Oct" with a hole in front of it would be
     * worse than the fallback.
     */
    fun mastheadPlace(city: String?): String =
        city?.trim().orEmpty().ifEmpty { PLACE_FALLBACK }

    /**
     * Single-character monogram for a nameless avatar chip.
     *
     * Returns "" for a nameless user so the caller can fall back to a neutral
     * glyph instead of inventing an initial.
     */
    fun avatarInitial(name: String?): String =
        name?.trim()
            ?.firstOrNull { !it.isWhitespace() }
            ?.uppercaseChar()
            ?.toString()
            .orEmpty()

    /** "Sat 12 Oct" — the header's date and the rail titles' day. */
    fun dateLabel(date: LocalDate): String {
        val day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        val month = date.month.getDisplayName(TextStyle.SHORT, Locale.US)
        return "$day ${date.dayOfMonth} $month"
    }

    /**
     * "Bengaluru · Sat 12 Oct" — the header subtitle (screen 02).
     *
     * The design's note for this screen is that the city and the date sit in the
     * header *because* they scope every price under it. The date is TODAY, not a
     * date the user picked: Discover has no date control, and printing a date the
     * feed was not actually queried for would be the exact dishonesty the note is
     * trying to prevent.
     */
    fun headerSubtitle(city: String?, today: LocalDate): String =
        "${mastheadPlace(city)} · ${dateLabel(today)}"

    /** "Available Sat night" — the date-scoped rail's title (screen 02). */
    fun availableRailTitle(date: LocalDate): String =
        "Available ${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)} night"

    /**
     * Does [artist] publish availability for [date]?
     *
     * A second gate over the server's own `p_date` filter, and deliberately
     * belt-and-braces: `SupabaseSearchRepository.executeSearch` retries WITHOUT
     * the 0073 dimensions when the RPC signature is missing, so on an older
     * server a date-filtered query silently returns an unfiltered page — and the
     * rail would be captioned "Available Sat night" over artists who never said
     * they were.
     *
     * An artist who has published no weekdays at all passes: an empty
     * `days_available` is an absence of information, not a statement of
     * unavailability, and the app's rule everywhere else (`availabilityKicker`)
     * is that we never claim availability *for* them — here the claim is the
     * rail's, and dropping every artist who has not filled the field in would
     * empty the rail on a young roster.
     */
    fun publishesAvailability(daysAvailable: List<String>, date: LocalDate): Boolean {
        if (daysAvailable.isEmpty()) return true
        val abbr = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        return daysAvailable.any { it.equals(abbr, ignoreCase = true) }
    }

    /** What the price cell says when there is no honest figure to quote. */
    const val PRICE_ON_REQUEST = "ON REQUEST"

    /** The lower-case form for the light design's tile and row metadata. */
    const val PRICE_ON_REQUEST_SOFT = "Pricing on request"

    /**
     * The trailing `FROM ₹75K` cell on a compact strip.
     *
     * Two bugs it exists to stop, both on the same shipped expression
     * (`packages.firstOrNull()?.price ?: artist.price`):
     *
     *  - **"FROM ₹0".** `search_artists` maps a NULL `min_price` to 0, and the
     *    RPC deliberately keeps no-package artists in unfiltered results — so
     *    those artists reach the hero and quote ₹0 on the busiest screen in the
     *    app, while their own profile says "Pricing on request" for the same
     *    fact. Anything at or below zero is an absence, not a price.
     *  - **the first package is not the cheapest.** "from" means minimum, which
     *    is [PackagePricing.fromPrice]'s whole contract, and every other price
     *    surface goes through it. Discover computed its own.
     */
    fun fromPriceLabel(packages: List<ArtistPackage>, fallback: Int): String {
        val from = PackagePricing.fromPrice(packages, fallback)
        return if (from > 0) "FROM ${formatInrShort(from)}" else PRICE_ON_REQUEST
    }

    /**
     * "Techno DJ · from ₹28,000" — a rail tile's second line (screen 02).
     *
     * The design prints a bare "₹28,000" here. We print "from", because the
     * number is [PackagePricing.fromPrice] — the cheapest package, not a quote —
     * and a bare figure beside a category reads as the price of the act. One word
     * is a cheap price for not implying a quote we have not made.
     */
    fun tileMeta(artist: Artist): String {
        val price = PackagePricing.fromPrice(artist.packages, artist.price)
        val category = artist.category.trim()
        val money = if (price > 0) "from ${formatInr(price)}" else PRICE_ON_REQUEST_SOFT
        return if (category.isEmpty()) money else "$category · $money"
    }

    /**
     * "Indie folk band · 5 pc · Bengaluru" — the hero card's meta line.
     *
     * Fields that are blank on a tile projection drop out entirely rather than
     * leaving an empty slot between separators.
     */
    fun heroMeta(artist: Artist): String? =
        listOf(artist.genre, artist.category, artist.city)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")

    /** "₹42,000" for the hero's price cell, or null when there is no figure. */
    fun heroPrice(artist: Artist): String? =
        PackagePricing.fromPrice(artist.packages, artist.price)
            .takeIf { it > 0 }
            ?.let { "from ${formatInr(it)}" }

    /**
     * "4.92 (128)" — the hero's rating cell, or null.
     *
     * Null when the artist has no rating at all: a fresh act reads "0.0 (0)"
     * otherwise, which is a worse claim than saying nothing. The count in
     * parentheses is completed shows, which is what the number is an average of.
     */
    fun heroRating(rating: Double, gigs: Int): String? {
        if (rating <= 0.0) return null
        val stars = String.format(Locale.US, "%.2f", rating)
        return if (gigs > 0) "$stars ($gigs)" else stars
    }

    /**
     * The hero card's badge — the screen's one accent — or null.
     *
     * Driven by the score band and nothing else, because the band is the only
     * standing the backend publishes. An unranked or merely rising act gets no
     * badge at all: a badge on everyone says nothing, which is the same rule the
     * availability kicker follows.
     */
    fun heroBadge(score: Int, gigs: Int): String? = when (ScoreBands.tier(score, gigs)) {
        ScoreTier.Elite -> "Top rated"
        ScoreTier.Trusted -> "Trusted"
        ScoreTier.Rising, ScoreTier.New -> null
    }
}
