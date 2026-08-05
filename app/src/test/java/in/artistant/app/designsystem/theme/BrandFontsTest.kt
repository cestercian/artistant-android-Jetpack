package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the brand-font wiring in [Type.kt] — the part of it that fails SILENTLY.
 *
 * Geist and Geist Mono are single variable .ttf files with a `wght` axis. The
 * failure mode this file exists for is declaring five weights that all resolve to
 * the font's default instance (400): the ramp looks correctly wired, `Medium`,
 * `SemiBold` and `Bold` all compile, and every one of them renders at Regular.
 * Nothing throws, nothing logs — it only shows up next to the iOS build.
 *
 * So these assert the two properties that make the ramp real:
 *  1. each declared face carries its OWN `wght` variation value, and
 *  2. no style asks a family for a face that family does not have (the serif has
 *     no bold master, so a bold serif request would get a synthetic smear).
 *
 * The rendering half of the proof can't live here — it needs a device. It was done
 * offline by instantiating the .ttf at each of the five weights with fontTools and
 * measuring the `H` stem: 42 / 56 / 70 / 85 / 113 units for Geist and 42 / 58 / 74
 * / 89 / 121 for Geist Mono, i.e. the axis genuinely moves. This test guards the
 * wiring that asks for those instances.
 */
@OptIn(ExperimentalTextApi::class)
class BrandFontsTest {

    // wght does not need a density to resolve (only optical-size-style axes do),
    // so any density works for reading the axis value back out.
    private val density = Density(1f)

    /** The declared faces of a family. A file-backed FontFamily *is* a List<Font>. */
    private fun faces(name: String, family: FontFamily): List<ResourceFont> {
        assertTrue("$name should be a bundled font family, not a platform fallback", family is List<*>)
        return (family as List<*>).map { it as ResourceFont }
    }

    /** The `wght` axis value a face will actually be instantiated at, or null. */
    private fun wght(face: ResourceFont): Float? =
        face.variationSettings.settings
            .firstOrNull { it.axisName == "wght" }
            ?.toVariationValue(density)

    /** Every TextStyle on the ramp, paired with its property name for readable failures. */
    private fun rampStyles(): List<Pair<String, TextStyle>> {
        val type = AppType()
        return AppType::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java && it.name.startsWith("get") }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) to it.invoke(type) as TextStyle }
            .sortedBy { it.first }
    }

    // ── the variable families ────────────────────────────────────────────────

    @Test
    fun `sans and mono declare one distinct wght instance per ramp weight`() {
        val expected = listOf(400, 500, 600, 700, 900)
        for ((name, family) in listOf("Geist" to SansFamily, "Geist Mono" to MonoFamily)) {
            val declared = faces(name, family)
            assertEquals(
                "$name should declare exactly the weights the ramp uses",
                expected,
                declared.map { it.weight.weight },
            )
            // The matcher key and the axis value have to agree — a face declared as
            // SemiBold but instantiated at 400 is the silent-flat-ramp bug.
            for (face in declared) {
                val axis = wght(face)
                assertNotNull("$name ${face.weight.weight} carries no wght setting — it would render at the file's default 400", axis)
                assertEquals("$name ${face.weight.weight} is instantiated at the wrong axis value", face.weight.weight.toFloat(), axis!!, 0f)
            }
            // Belt and braces: five faces, five different points on the axis.
            assertEquals("$name resolves several weights to the same instance", expected.size, declared.mapNotNull { wght(it) }.toSet().size)
        }
    }

    @Test
    fun `variable families reuse one resource per family`() {
        // Each is a single file driven by an axis, not five static cuts. If this
        // ever becomes five resource ids, the APK grew and someone should know.
        for ((name, family) in listOf("Geist" to SansFamily, "Geist Mono" to MonoFamily)) {
            assertEquals("$name should be one variable file", 1, faces(name, family).map { it.resId }.toSet().size)
        }
    }

    // ── the static serif ─────────────────────────────────────────────────────

    @Test
    fun `serif declares a roman and a real italic`() {
        val declared = faces("Instrument Serif", SerifFamily)
        assertEquals("Instrument Serif should declare exactly a roman and an italic", 2, declared.size)
        // The Discover masthead's city and every EditorialHeadline accent word are
        // italic spans over a serif style. Without a declared italic face Android
        // obliques the roman, which on a serif with a true cursive italic is
        // visibly the wrong letterforms rather than a slanted version of them.
        assertEquals(
            "the italic face is missing — italic serif spans would be synthesised",
            setOf(FontStyle.Normal, FontStyle.Italic),
            declared.map { it.style }.toSet(),
        )
        assertEquals(
            "Instrument Serif has no bold master; every declared face must be Normal weight",
            setOf(FontWeight.Normal),
            declared.map { it.weight }.toSet(),
        )
        // Two files, not one file reused — the italic is a separate cut.
        assertEquals(2, declared.map { it.resId }.toSet().size)
    }

    @Test
    fun `no ramp style asks the serif for a weight it does not have`() {
        val offenders = rampStyles().filter { (_, style) ->
            style.fontFamily == SerifFamily &&
                style.fontWeight != null &&
                style.fontWeight != FontWeight.Normal
        }
        assertTrue(
            "Instrument Serif ships Regular + Italic only — these would render as a " +
                "synthetic smear: ${offenders.map { it.first }}",
            offenders.isEmpty(),
        )
    }

    // ── the ramp as a whole ──────────────────────────────────────────────────

    @Test
    fun `every ramp style is set in a brand face`() {
        val brand = setOf(SerifFamily, SansFamily, MonoFamily)
        val strays = rampStyles().filterNot { (_, style) -> style.fontFamily in brand }
        assertTrue(
            "these styles fell back to a platform font instead of a bundled brand face: " +
                "${strays.map { it.first }}",
            strays.isEmpty(),
        )
    }

    @Test
    fun `every weight the ramp requests has a declared master`() {
        // Compose does not fail on an undeclared weight, it snaps to the nearest
        // declared one. So the ramp asking for a weight the family never declared
        // is a silent downgrade — catch it here instead of in a screenshot diff.
        val declaredSans = faces("Geist", SansFamily).map { it.weight }.toSet()
        val declaredMono = faces("Geist Mono", MonoFamily).map { it.weight }.toSet()
        for ((name, style) in rampStyles()) {
            val weight = style.fontWeight ?: continue
            when (style.fontFamily) {
                SansFamily -> assertTrue("$name asks Geist for undeclared ${weight.weight}", weight in declaredSans)
                MonoFamily -> assertTrue("$name asks Geist Mono for undeclared ${weight.weight}", weight in declaredMono)
                // The serif has its own, stricter test above.
                else -> Unit
            }
        }
    }
}
