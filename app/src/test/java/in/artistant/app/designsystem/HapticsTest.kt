package `in`.artistant.app.designsystem

import android.view.HapticFeedbackConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Pins the haptic vocabulary's kind → platform-constant mapping and its version
 * floors. `hapticConstantFor` compiles to a `when` over inlined `static final`
 * ints, so this runs on the plain JVM with no Robolectric.
 */
class HapticsTest {

    private companion object {
        const val OREO = 26 // minSdk
        const val PIE = 28
        const val R = 30
        const val S = 31
        const val UDC = 34
    }

    @Test
    fun `outcomes use the platform confirm-reject pair from API 30`() {
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstantFor(HapticKind.Success, R))
        assertEquals(HapticFeedbackConstants.CONFIRM, hapticConstantFor(HapticKind.Success, UDC))
        assertEquals(HapticFeedbackConstants.REJECT, hapticConstantFor(HapticKind.Warning, R))
        assertEquals(HapticFeedbackConstants.REJECT, hapticConstantFor(HapticKind.Error, S))
    }

    @Test
    fun `outcomes fall back to a long press below API 30`() {
        for (sdk in intArrayOf(OREO, PIE, 29)) {
            assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(HapticKind.Success, sdk))
            assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(HapticKind.Warning, sdk))
            assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(HapticKind.Error, sdk))
        }
    }

    @Test
    fun `select ticks a segment from API 34 and a clock below it`() {
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, hapticConstantFor(HapticKind.Select, UDC))
        assertEquals(HapticFeedbackConstants.SEGMENT_TICK, hapticConstantFor(HapticKind.Select, 36))
        for (sdk in intArrayOf(OREO, R, S, 33)) {
            assertEquals(HapticFeedbackConstants.CLOCK_TICK, hapticConstantFor(HapticKind.Select, sdk))
        }
    }

    @Test
    fun `impact weights need no version branch`() {
        for (sdk in intArrayOf(OREO, R, UDC, 36)) {
            assertEquals(HapticFeedbackConstants.KEYBOARD_TAP, hapticConstantFor(HapticKind.Tap, sdk))
            assertEquals(HapticFeedbackConstants.CONTEXT_CLICK, hapticConstantFor(HapticKind.Impact, sdk))
            assertEquals(HapticFeedbackConstants.LONG_PRESS, hapticConstantFor(HapticKind.Heavy, sdk))
        }
    }

    /**
     * The three families have to stay tellable apart on a modern device, or the
     * vocabulary is decorative: a failed send and a picked chip would buzz the
     * same. (Warning and Error deliberately share `REJECT` — see the mapping's
     * doc — so they are not part of this claim.)
     */
    @Test
    fun `outcome, selection and tap are three different feels on API 34`() {
        val success = hapticConstantFor(HapticKind.Success, UDC)
        val error = hapticConstantFor(HapticKind.Error, UDC)
        val select = hapticConstantFor(HapticKind.Select, UDC)
        val tap = hapticConstantFor(HapticKind.Tap, UDC)
        assertNotEquals(success, error)
        assertNotEquals(success, select)
        assertNotEquals(select, tap)
        assertNotEquals(error, tap)
    }

    @Test
    fun `every kind resolves at every supported API level`() {
        for (kind in HapticKind.entries) {
            for (sdk in OREO..36) {
                // NO_HAPTICS would silently swallow the buzz; nothing may map to it.
                assertNotEquals(
                    "$kind on API $sdk",
                    HapticFeedbackConstants.NO_HAPTICS,
                    hapticConstantFor(kind, sdk),
                )
            }
        }
    }
}
