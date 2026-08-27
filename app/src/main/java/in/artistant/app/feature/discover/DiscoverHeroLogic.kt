package `in`.artistant.app.feature.discover

import `in`.artistant.app.common.util.formatInrShort
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.domain.artist.PackagePricing

/**
 * Pure decisions behind the Discover masthead + hero carousel.
 *
 * They live outside the composables so the rules are testable without a Compose
 * runtime, and so the *reasons* below survive a UI rewrite.
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
     * The place name shown in "Tonight in <X>." — the user's city, or
     * [PLACE_FALLBACK].
     *
     * Trims first: a whitespace-only value is a blank city that happens to be
     * non-null, and rendering "Tonight in  ." would be worse than the fallback.
     */
    fun mastheadPlace(city: String?): String =
        city?.trim().orEmpty().ifEmpty { PLACE_FALLBACK }

    /**
     * Single-character monogram for the masthead avatar chip.
     *
     * One character, not two: the chip is only 40dp and a two-letter monogram
     * collides with the ring at that size. Returns "" for a nameless user so the
     * caller can fall back to a neutral glyph instead of inventing an initial.
     */
    fun avatarInitial(name: String?): String =
        name?.trim()
            ?.firstOrNull { !it.isWhitespace() }
            ?.uppercaseChar()
            ?.toString()
            .orEmpty()

    /**
     * Should the hero rotate on this tick?
     *
     * Two gates, both about not animating for nothing:
     * - one slide can't rotate anywhere, and a self-advancing single-page pager
     *   would just churn state forever;
     * - a user who has asked the system to reduce motion should get a carousel
     *   they page themselves, not one that moves under them.
     */
    fun shouldAutoAdvance(pageCount: Int, animationsEnabled: Boolean): Boolean =
        pageCount > 1 && animationsEnabled

    /** Next slide index, wrapping. Returns 0 for an empty carousel. */
    fun nextPage(current: Int, pageCount: Int): Int =
        if (pageCount <= 0) 0 else (current + 1) % pageCount

    /** What the price cell says when there is no honest figure to quote. */
    const val PRICE_ON_REQUEST = "ON REQUEST"

    /**
     * The trailing `FROM ₹75K` cell on the hero strip and the featured frame.
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
}
