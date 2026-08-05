package `in`.artistant.app.domain.artist

import `in`.artistant.app.testsupport.pkg
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
}
