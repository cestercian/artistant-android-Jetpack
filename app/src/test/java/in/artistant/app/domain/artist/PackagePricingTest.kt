package `in`.artistant.app.domain.artist

import `in`.artistant.app.testsupport.pkg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A badge only earns its pixels if it separates one row from another. These
 * cases are the device sweep's findings turned into assertions: production rows
 * were written with `popular = true` unconditionally, so every package in every
 * set carried the same "Popular" pill.
 */
class PackagePricingTest {

    @Test
    fun popularBadge_isMeaningful_whenExactlyOnePackageInASetIsPopular() {
        val packages = listOf(
            pkg("p0", "Acoustic hour", 12_000),
            pkg("p1", "Evening set", 20_000, popular = true),
            pkg("p2", "Full night", 40_000),
        )

        assertTrue(PackagePricing.popularBadgeIsMeaningful(packages))
    }

    @Test
    fun popularBadge_isNotMeaningful_whenEveryPackageIsPopular() {
        // The shipped device symptom: three rows, three identical badges.
        val packages = listOf(
            pkg("p0", "Acoustic hour", 12_000, popular = true),
            pkg("p1", "Evening set", 20_000, popular = true),
            pkg("p2", "Full night", 40_000, popular = true),
        )

        assertFalse(PackagePricing.popularBadgeIsMeaningful(packages))
    }

    @Test
    fun popularBadge_isNotMeaningful_forALoneSelfDeclaredPopularPackage() {
        // Android's wizard/EPK publish exactly one package, so "the popular one"
        // is also "the only one" — nothing is being distinguished.
        assertFalse(PackagePricing.popularBadgeIsMeaningful(listOf(pkg("p0", "Set", 18_000, popular = true))))
    }

    @Test
    fun popularBadge_isNotMeaningful_whenNoPackageIsPopular() {
        val packages = listOf(pkg("p0", "Acoustic hour", 12_000), pkg("p1", "Evening set", 20_000))

        assertFalse(PackagePricing.popularBadgeIsMeaningful(packages))
    }

    @Test
    fun popularBadge_isNotMeaningful_forAnEmptyPackageSet() {
        assertFalse(PackagePricing.popularBadgeIsMeaningful(emptyList()))
    }

    @Test
    fun fromPrice_isTheCheapestPackage_notTheFirstOne() {
        // The exact device case: the dock said "from ₹51,000" for an artist whose
        // packages were 51,000 / 22,000 / 83,000. Package order is wizard-authored
        // and says nothing about cost.
        val packages = listOf(
            pkg("p0", "Evening set", 51_000),
            pkg("p1", "Acoustic hour", 22_000),
            pkg("p2", "Full night", 83_000),
        )

        assertEquals(22_000, PackagePricing.fromPrice(packages, fallback = 51_000))
    }

    @Test
    fun fromPrice_ignoresTheDenormalizedArtistPrice_whenPackagesAreLoaded() {
        // artists.min_price is confirmed stale on dev (row says 51,000 while a
        // 22,000 package exists), so a loaded package set always wins.
        val packages = listOf(pkg("p0", "Acoustic hour", 22_000))

        assertEquals(22_000, PackagePricing.fromPrice(packages, fallback = 51_000))
    }

    @Test
    fun fromPrice_fallsBackToTheArtistPrice_whenNoPackagesAreLoaded() {
        // Profiles can render before/without packages; the artist row's figure is
        // the only number available then.
        assertEquals(25_000, PackagePricing.fromPrice(emptyList(), fallback = 25_000))
    }

    @Test
    fun fromPrice_isStableRegardlessOfSelection_soTheLabelStaysHonest() {
        // Selecting a tier must not move the "from" figure — iOS's dock quotes
        // `cheapestPackage` whatever `pkg` is selected (Screens/ArtistView.swift).
        val packages = listOf(pkg("p0", "Acoustic hour", 12_000), pkg("p1", "Evening set", 20_000))

        assertEquals(12_000, PackagePricing.fromPrice(packages, fallback = 99_000))
    }
}
