package `in`.artistant.app.designsystem.theme

import androidx.compose.material3.Typography
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
 * Plus Jakarta Sans and JetBrains Mono are variable .ttf files with a `wght`
 * axis. The failure mode this file exists for is declaring five weights that all
 * resolve to the font's default instance (400): the ramp looks correctly wired,
 * `Medium`, `SemiBold` and `Bold` all compile, and every one of them renders at
 * Regular. Nothing throws, nothing logs — it only shows up in a side-by-side.
 *
 * So these assert the properties that make the ramp real:
 *  1. each declared face carries its OWN `wght` variation value;
 *  2. the top rung is a weight the FILES actually reach — both axes stop at 800,
 *     so declaring a 900 would be a lie the matcher silently absorbs;
 *  3. no ramp style asks a family for a weight that family never declared.
 *
 * The rendering half of the proof can't live here — it needs a device.
 */
@OptIn(ExperimentalTextApi::class)
@Suppress("DEPRECATION") // SerifFamily is a retired alias; one test below pins that.
class BrandFontsTest {

    // wght does not need a density to resolve (only optical-size-style axes do),
    // so any density works for reading the axis value back out.
    private val density = Density(1f)

    /**
     * The weights the ramp declares, top rung included.
     *
     * 800 rather than 900 is the whole point of the redesign's font swap: Plus
     * Jakarta Sans runs 200–800 and JetBrains Mono 100–800, so `FontWeight.Black`
     * has no master in either. The six `FontWeight.Black` call sites left in the
     * app resolve to this entry.
     */
    private val rampWeights = listOf(400, 500, 600, 700, 800)

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
    fun `every declared face is instantiated at its own wght`() {
        for ((name, family) in listOf("Plus Jakarta Sans" to SansFamily, "JetBrains Mono" to MonoFamily)) {
            for (face in faces(name, family)) {
                val axis = wght(face)
                assertNotNull(
                    "$name ${face.weight.weight} carries no wght setting — it would render at the file's default 400",
                    axis,
                )
                // The matcher key and the axis value have to agree — a face declared
                // as SemiBold but instantiated at 400 is the silent-flat-ramp bug.
                assertEquals(
                    "$name ${face.weight.weight} is instantiated at the wrong axis value",
                    face.weight.weight.toFloat(),
                    axis!!,
                    0f,
                )
            }
        }
    }

    @Test
    fun `mono declares exactly the ramp weights, one file`() {
        val declared = faces("JetBrains Mono", MonoFamily)
        assertEquals(
            "JetBrains Mono should declare exactly the weights the ramp uses",
            rampWeights,
            declared.map { it.weight.weight },
        )
        // One variable file driven by an axis, not five static cuts. If this ever
        // becomes five resource ids, the APK grew and someone should know.
        assertEquals("JetBrains Mono should be one variable file", 1, declared.map { it.resId }.toSet().size)
        assertEquals(
            "JetBrains Mono resolves several weights to the same instance",
            rampWeights.size,
            declared.mapNotNull { wght(it) }.toSet().size,
        )
    }

    @Test
    fun `sans declares the ramp weights in both a roman and a real italic`() {
        val declared = faces("Plus Jakarta Sans", SansFamily)
        val byStyle = declared.groupBy { it.style }
        assertEquals(
            "the sans should declare a roman AND an italic cut",
            setOf(FontStyle.Normal, FontStyle.Italic),
            byStyle.keys,
        )
        for ((style, cut) in byStyle) {
            assertEquals(
                "the $style cut should declare exactly the weights the ramp uses",
                rampWeights,
                cut.map { it.weight.weight },
            )
        }
        // The italic is load-bearing, not ornamental: signup's editorial headlines
        // set their accent word as an italic span. Without a declared italic file
        // Android obliques the roman, which is a slant rather than the designed
        // letterforms — and it fails silently, which is why it is pinned here.
        assertEquals(
            "roman and italic must be two separate files",
            2,
            declared.map { it.resId }.toSet().size,
        )
    }

    @Test
    fun `no family declares a weight past the axis maximum`() {
        // Both files stop at 800. A declared 900 would resolve to the same outline
        // as 800 while telling the matcher a heavier master exists — so a future
        // style asking for Black would silently get ExtraBold and look "wired".
        for ((name, family) in listOf("Plus Jakarta Sans" to SansFamily, "JetBrains Mono" to MonoFamily)) {
            val tooHeavy = faces(name, family).map { it.weight.weight }.filter { it > AXIS_MAX }
            assertTrue("$name declares weights the file cannot reach: $tooHeavy", tooHeavy.isEmpty())
        }
    }

    // ── the retired serif ────────────────────────────────────────────────────

    @Test
    fun `the serif is retired to an alias of the sans`() {
        // The editorial serif is gone from the design language (REDESIGN_2026-09
        // §2). `SerifFamily` survives only so inherited call sites compile, and it
        // has to BE the sans — if it ever became a family of its own again, every
        // one of those sites would silently start rendering in a face the design
        // does not have.
        assertEquals("SerifFamily must resolve to the sans", SansFamily, SerifFamily)
    }

    // ── the ramp as a whole ──────────────────────────────────────────────────

    @Test
    fun `every ramp style is set in a brand face`() {
        val brand = setOf(SansFamily, MonoFamily)
        val strays = rampStyles().filterNot { (_, style) -> style.fontFamily in brand }
        assertTrue(
            "these styles fell back to a platform font instead of a bundled brand face: " +
                "${strays.map { it.first }}",
            strays.isEmpty(),
        )
    }

    @Test
    fun `the Material fallback scale is on the brand sans too`() {
        // MaterialTheme publishes bodyLarge as the ambient LocalTextStyle, so a
        // style-less Text() and every self-typing Material component reads from
        // here. A stray FontFamily.Default in this scale is the system font
        // leaking back in beside the ramp.
        val strays = Typography::class.java.methods
            .filter { it.parameterCount == 0 && it.returnType == TextStyle::class.java && it.name.startsWith("get") }
            .map { it.name.removePrefix("get").replaceFirstChar(Char::lowercaseChar) to it.invoke(BrandTypography) as TextStyle }
            .filterNot { (_, style) -> style.fontFamily == SansFamily }
        assertTrue("Material scale styles still on a platform font: ${strays.map { it.first }}", strays.isEmpty())
    }

    @Test
    fun `every weight the ramp requests has a declared master`() {
        // Compose does not fail on an undeclared weight, it snaps to the nearest
        // declared one. So the ramp asking for a weight the family never declared
        // is a silent downgrade — catch it here instead of in a screenshot diff.
        val declaredSans = faces("Plus Jakarta Sans", SansFamily).map { it.weight }.toSet()
        val declaredMono = faces("JetBrains Mono", MonoFamily).map { it.weight }.toSet()
        for ((name, style) in rampStyles()) {
            val weight = style.fontWeight ?: continue
            when (style.fontFamily) {
                SansFamily -> assertTrue("$name asks the sans for undeclared ${weight.weight}", weight in declaredSans)
                MonoFamily -> assertTrue("$name asks the mono for undeclared ${weight.weight}", weight in declaredMono)
                else -> Unit
            }
        }
    }

    private companion object {
        /** Where the `wght` axis ends on BOTH bundled files. */
        const val AXIS_MAX = 800
    }
}
