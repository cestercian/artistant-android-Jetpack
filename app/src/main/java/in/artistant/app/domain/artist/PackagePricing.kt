package `in`.artistant.app.domain.artist

import `in`.artistant.app.data.model.ArtistPackage

/**
 * Pure arithmetic over an artist's package set, shared by every surface that
 * renders a price or a "Popular" badge (artist profile + booking compose).
 *
 * It lives in `domain/` and takes a plain `List<ArtistPackage>` on purpose: the
 * two call sites are Composables, which are not unit-testable on the JVM, so the
 * decisions they make have to be extracted here to be covered at all. Both call
 * sites MUST go through this object — the device bugs it fixes were two surfaces
 * disagreeing about the same package set.
 */
object PackagePricing {

    /**
     * Should a package list render "Popular" pills at all?
     *
     * A badge is a comparison, so it only means something when it splits the set:
     * at least one package carries it AND at least one does not. Two cases fall
     * out of that and both are live on the device today —
     *
     *  - **every** package popular: the writers below used to hardcode
     *    `popular = true`, so an artist with three tiers got three identical
     *    pills and the badge conveyed nothing;
     *  - a **single** package that calls itself popular: Android's wizard/EPK
     *    publish exactly one package, so "the popular one" is "the only one".
     *
     * Callers combine this with the row's own flag:
     * `if (badgeIsMeaningful && pkg.popular) Pill("Popular")`. Kept as a whole-set
     * question rather than a per-row one so both surfaces (artist profile,
     * booking compose) can never disagree about the same list.
     */
    fun popularBadgeIsMeaningful(packages: List<ArtistPackage>): Boolean =
        packages.any { it.popular } && packages.any { !it.popular }

    /**
     * The figure a "from ₹X" label may honestly quote: the MINIMUM over the
     * packages actually loaded, falling back to [fallback] (the artist row's own
     * price) only when the set is empty.
     *
     * Two things it deliberately does not do:
     *
     *  - it does not quote the *selected* package. "from" means minimum, and the
     *    shipped dock read `selected?.price ?: artist.price`, so picking the
     *    dearest tier made the page advertise "from ₹83,000".
     *  - it does not trust [fallback] as the minimum. That value comes from the
     *    server's denormalized `artists.min_price`, which is confirmed stale on
     *    dev — one artist's row says ₹51,000 while a ₹22,000 package exists — so
     *    computing from the loaded packages client-side is the robust choice.
     *    The fallback only covers profiles rendered before/without packages.
     *
     * Matches iOS `Screens/ArtistView.swift`'s `cheapestPackage`, which is shared
     * by its Booking section and its bottom dock so the two surfaces can never
     * quote different figures for the same artist.
     */
    fun fromPrice(packages: List<ArtistPackage>, fallback: Int): Int =
        packages.minOfOrNull { it.price } ?: fallback

    /**
     * The figure the bottom dock may quote, or `null` when the page has no
     * honest number to show.
     *
     * [fromPrice] falls back to the artist row's denormalized price so a profile
     * rendered BEFORE its packages load still has something in the dock. That is
     * right during hydration and wrong once the packages are known to be empty:
     * an artist with no published tiers takes custom requests, the Booking block
     * correctly says "Pricing on request", and the dock underneath it was still
     * quoting the stale server column — one screen stating two different things
     * about the same artist's price. Seen on-device: the block read "Pricing on
     * request" while the dock read "FROM ₹2,10,000".
     *
     * [packagesLoaded] is what separates the two cases. While false the fallback
     * still applies (a dock with no number reads as broken mid-load); once true
     * and the set is empty, there is no minimum and the dock says so instead of
     * inventing one.
     *
     * A non-positive figure is not a price on EITHER branch. `SearchRepository`
     * maps a null `min_price` to `0`, so a package-less artist opened from a
     * search or browse tile arrives here with `fallback = 0` while the stitch is
     * still in flight — and the dock renders any non-null number, so that `0`
     * read as "₹0", permanently if the stitch never landed. Same rule the tiles
     * use in `DiscoverHeroLogic.fromPriceLabel`: positive or on request.
     */
    fun dockPrice(packages: List<ArtistPackage>, fallback: Int, packagesLoaded: Boolean): Int? = when {
        packages.isNotEmpty() -> packages.minOf { it.price }.takeIf { it > 0 }
        packagesLoaded -> null
        else -> fallback.takeIf { it > 0 }
    }
}
