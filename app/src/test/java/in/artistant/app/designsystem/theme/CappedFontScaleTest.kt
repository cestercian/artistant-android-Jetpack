package `in`.artistant.app.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [cappedFontScale] — the one place the app refuses part of a reader's font scale, and the
 * arithmetic that makes it a pin rather than a shrink.
 *
 * The rule it serves: a two-digit date must never be drawn wider than the cell it names.
 * `sp` is multiplied by the system scale at draw time, so pinning the DRAWN size means
 * dividing the declared size by the overshoot — get that backwards and the numeral grows
 * twice as fast as it should.
 */
class CappedFontScaleTest {

    private val cap = 1.3f

    @Test
    fun `every scale up to the cap is honoured in full`() {
        for (scale in listOf(0.85f, 1f, 1.15f, 1.3f)) {
            assertEquals("scale $scale is inside the cell", 1f, cappedFontScale(scale, cap), 0f)
        }
    }

    @Test
    fun `past the cap the drawn size stops growing`() {
        // What the reader's device does: size.sp × fontScale. With the multiplier applied the
        // product must sit on the cap, not above it — at 2.0 that is 10sp drawn at 13, not 20.
        for (scale in listOf(1.31f, 1.5f, 1.8f, 2f)) {
            val drawn = DECLARED_SP * scale * cappedFontScale(scale, cap)
            assertEquals("scale $scale must draw at the cap", DECLARED_SP * cap, drawn, EPSILON)
        }
    }

    @Test
    fun `the capped numeral fits the cell it is drawn in`() {
        // The measurement the cap comes from: fourteen cells and thirteen gaps across the
        // page's content width, and JetBrains Mono digits advancing at 0.6em. Two of them
        // have to clear one cell at the app's largest supported scale.
        val cellWidth = (CONTENT_WIDTH_DP - GAP_DP * (STRIP_CELLS - 1)) / STRIP_CELLS
        val widest = DECLARED_SP * cap * MONO_DIGIT_ADVANCE * 2
        assertTrue(
            "two digits at ${DECLARED_SP * cap}sp measure $widest, cell is $cellWidth",
            widest < cellWidth,
        )
    }

    @Test
    fun `an uncapped numeral is what would not fit`() {
        // The bug, stated: at 2.0 the same pair is wider than the cell, which is why the
        // strip clipped mid-digit before this existed.
        val cellWidth = (CONTENT_WIDTH_DP - GAP_DP * (STRIP_CELLS - 1)) / STRIP_CELLS
        val widest = DECLARED_SP * 2f * MONO_DIGIT_ADVANCE * 2
        assertTrue(widest > cellWidth)
    }

    /** `AppType.monoStripDay`. */
    private companion object {
        const val DECLARED_SP = 10f
        const val MONO_DIGIT_ADVANCE = 0.6f
        const val STRIP_CELLS = 14
        const val GAP_DP = 4f
        const val CONTENT_WIDTH_DP = 350f
        const val EPSILON = 0.001f
    }
}
