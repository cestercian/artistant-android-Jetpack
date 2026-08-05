package `in`.artistant.app.feature.discover

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
}
