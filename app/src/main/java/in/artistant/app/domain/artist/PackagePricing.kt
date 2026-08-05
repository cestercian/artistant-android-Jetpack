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
}
