package `in`.artistant.app.feature.booking

import `in`.artistant.app.data.model.ArtistPackage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one line of non-Composable logic behind a package row: the "60 min · Live
 * band · Sound check" summary under the tier name.
 *
 * Worth pinning because the row that renders it is shared by the artist profile
 * and the booking screen, and because the input is wizard-authored — an artist
 * who published a tier with no `includes`, or with a blank one, is the ordinary
 * case, not an edge case. A naive `duration + " · " + includes.joinToString()`
 * renders a dangling separator for exactly those artists.
 */
class PackageIncludesLineTest {

    private fun pkg(duration: String, includes: List<String>) = ArtistPackage(
        id = "p0",
        name = "Evening set",
        duration = duration,
        price = 20_000,
        includes = includes,
        popular = false,
    )

    @Test
    fun joinsTheDurationAndEveryInclusion() {
        assertEquals(
            "60 min · Live band · Sound check · Backline",
            packageIncludesLine(pkg("60 min", listOf("Live band", "Sound check", "Backline"))),
        )
    }

    @Test
    fun aPackageWithNoInclusionsReadsAsItsDurationAlone() {
        // No trailing separator: the line has to end on a word, not on a dot.
        assertEquals("90 min", packageIncludesLine(pkg("90 min", emptyList())))
    }

    @Test
    fun blankPartsAreDroppedRatherThanJoined() {
        // Both halves can arrive blank from the wizard, in any combination.
        assertEquals("Live band", packageIncludesLine(pkg("", listOf("Live band"))))
        assertEquals("60 min", packageIncludesLine(pkg("60 min", listOf("", "  "))))
    }

    @Test
    fun anEntirelyEmptyPackageYieldsAnEmptyLine() {
        // The caller hides the line on blank, so this must be blank and not " · ".
        assertEquals("", packageIncludesLine(pkg("", emptyList())))
    }
}
