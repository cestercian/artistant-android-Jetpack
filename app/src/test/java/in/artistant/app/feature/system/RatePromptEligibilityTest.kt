package `in`.artistant.app.feature.system

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Asked once, after a completed booking review — never on launch" (design 138).
 *
 * The negative cases are the point of this suite: the prompt's whole design is
 * what it refuses to do.
 */
class RatePromptEligibilityTest {

    @Test
    fun `a submitted review arms the prompt`() {
        assertTrue(shouldPromptForRating(RatePromptRecord(reviewSubmitted = true)))
    }

    @Test
    fun `a bare launch never prompts`() {
        // No review, nothing else true — the exact state every launch is in.
        assertFalse(shouldPromptForRating(RatePromptRecord()))
    }

    @Test
    fun `once asked, never again`() {
        assertFalse(
            shouldPromptForRating(RatePromptRecord(reviewSubmitted = true, asked = true)),
        )
    }

    @Test
    fun `somebody who already rated is not asked again`() {
        assertFalse(
            shouldPromptForRating(RatePromptRecord(reviewSubmitted = true, rated = true)),
        )
    }

    @Test
    fun `asked without a review is still not a prompt`() {
        // Guards the ordering: `asked` must never be readable as "eligible".
        assertFalse(shouldPromptForRating(RatePromptRecord(asked = true)))
    }
}
