package `in`.artistant.app.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The motion system's decisions, tested without a Compose runtime.
 *
 * Everything covered here is a rule that is invisible in a screenshot and easy
 * to regress silently: whether reduce-motion was honoured, whether a duration
 * actually collapsed, which direction a screen slides. The animations themselves
 * are a device-verification job; these are the choices *behind* them.
 */
class MotionSpecsTest {

    // ── reduce-motion resolution ─────────────────────────────────────────────
    //
    // Android has no single reduce-motion flag, so this is the one place that
    // decides what "the user turned animations off" means. Getting it wrong
    // fails silently in exactly the direction that hurts: we keep animating for
    // someone who asked us not to.

    @Test
    fun `default scales mean motion is on`() {
        assertFalse(MotionSpecs.isReduceMotion(animator = 1f, transition = 1f, window = 1f))
    }

    @Test
    fun `a zeroed animator scale means reduce motion`() {
        assertTrue(MotionSpecs.isReduceMotion(animator = 0f))
    }

    @Test
    fun `a zeroed transition scale alone means reduce motion`() {
        // Developer Options can zero these individually; accessibility's
        // "Remove animations" zeroes all three. Any one of them is intent.
        assertTrue(MotionSpecs.isReduceMotion(animator = 1f, transition = 0f, window = 1f))
    }

    @Test
    fun `a zeroed window scale alone means reduce motion`() {
        assertTrue(MotionSpecs.isReduceMotion(animator = 1f, transition = 1f, window = 0f))
    }

    @Test
    fun `a merely slowed scale is not reduce motion`() {
        // 0.5x and 2x are speed preferences, not a request to stop moving.
        assertFalse(MotionSpecs.isReduceMotion(animator = 0.5f))
        assertFalse(MotionSpecs.isReduceMotion(animator = 2f))
    }

    // ── durations ────────────────────────────────────────────────────────────

    @Test
    fun `a duration passes through unchanged when motion is on`() {
        assertEquals(300, MotionSpecs.durationMillis(300, reduceMotion = false))
    }

    @Test
    fun `reduce motion collapses a duration to zero rather than shortening it`() {
        // The setting means "do not animate", not "animate faster" — a merely
        // quicker slide is still a slide.
        assertEquals(0, MotionSpecs.durationMillis(300, reduceMotion = true))
        assertEquals(0, MotionSpecs.durationMillis(1_100, reduceMotion = true))
    }

    // ── press feedback ───────────────────────────────────────────────────────

    @Test
    fun `a held control shrinks`() {
        assertEquals(0.98f, MotionSpecs.pressScale(pressed = true, reduceMotion = false, 0.98f), 0f)
    }

    @Test
    fun `a released control returns to rest`() {
        assertEquals(1f, MotionSpecs.pressScale(pressed = false, reduceMotion = false, 0.98f), 0f)
    }

    @Test
    fun `reduce motion pins press scale at rest instead of snapping to it`() {
        // Applying 0.98 instantly would be a flicker — strictly worse than no
        // kinetic feedback at all. The ripple still fires either way.
        assertEquals(1f, MotionSpecs.pressScale(pressed = true, reduceMotion = true, 0.98f), 0f)
    }

    // ── shimmer ──────────────────────────────────────────────────────────────

    @Test
    fun `shimmer runs normally and stops under reduce motion`() {
        assertTrue(MotionSpecs.shouldShimmer(reduceMotion = false))
        // Hard off, not zero-duration: an infinite animation given a zero
        // duration restarts every frame, which is a busy loop AND a strobe.
        assertFalse(MotionSpecs.shouldShimmer(reduceMotion = true))
    }

    // ── slide geometry ───────────────────────────────────────────────────────

    @Test
    fun `slide travels a fraction of the width, not the whole of it`() {
        assertEquals(500, MotionSpecs.slideOffsetPx(1_000, 0.5f, towardsStart = false))
        assertEquals(300, MotionSpecs.slideOffsetPx(1_200, 0.25f, towardsStart = false))
    }

    @Test
    fun `travelling towards the start is negative`() {
        // Sign is the whole direction cue: flip it and a back gesture animates
        // like a forward push.
        assertEquals(-500, MotionSpecs.slideOffsetPx(1_000, 0.5f, towardsStart = true))
    }

    @Test
    fun `forward and back offsets are exact mirrors`() {
        val forward = MotionSpecs.slideOffsetPx(1_080, 0.33f, towardsStart = false)
        val back = MotionSpecs.slideOffsetPx(1_080, 0.33f, towardsStart = true)
        assertEquals(forward, -back)
    }

    @Test
    fun `a zero-width screen produces no offset`() {
        assertEquals(0, MotionSpecs.slideOffsetPx(0, 0.33f, towardsStart = false))
    }

    // ── token relationships ──────────────────────────────────────────────────
    //
    // These pin the brief's constraints as assertions rather than as prose, so
    // a later "let's make it a bit slower" has to argue with a failing test.

    @Test
    fun `a stack push stays under the reference platform's push duration`() {
        // The brief's explicit ceiling: iOS's default is ~350ms and anything
        // slower reads as sluggish.
        assertTrue(Motion().stackPush <= 350)
    }

    @Test
    fun `switching tabs is quicker than pushing a screen`() {
        val motion = Motion()
        assertTrue(motion.tabSwitch < motion.stackPush)
    }

    @Test
    fun `press feedback is the shortest motion in the system`() {
        val motion = Motion()
        assertTrue(motion.press < motion.tabSwitch)
        assertTrue(motion.press < motion.indicator)
        assertTrue(motion.press < motion.contentReveal)
    }

    @Test
    fun `every semantic duration is drawn from the Material 3 scale`() {
        // No surface gets to invent a number; the aliases must map onto the
        // documented ramp.
        val motion = Motion()
        val ramp = setOf(motion.short2, motion.short4, motion.medium2, motion.medium4)
        assertTrue(motion.stackPush in ramp)
        assertTrue(motion.tabSwitch in ramp)
        assertTrue(motion.indicator in ramp)
        assertTrue(motion.press in ramp)
        assertTrue(motion.contentReveal in ramp)
    }

    @Test
    fun `a screen slides part of its width so the outgoing screen is not raced off`() {
        val fraction = Motion().slideFraction
        assertTrue(fraction > 0f)
        assertTrue(fraction < 1f)
    }
}
