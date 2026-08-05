package `in`.artistant.app.feature.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Rules behind the Discover masthead + hero carousel. Pure — no Compose runtime.
 *
 * The interesting cases here are all "the input is technically present but
 * useless": a whitespace-only city, a name that starts with a space, a
 * single-slide carousel. Each of those shipped as a visible bug in some
 * incarnation of this screen, so they get a test rather than a comment.
 */
class DiscoverHeroLogicTest {

    // ── mastheadPlace ────────────────────────────────────────────────────────

    @Test
    fun `masthead uses the user's city when set`() {
        assertEquals("Chennai", DiscoverHeroLogic.mastheadPlace("Chennai"))
    }

    @Test
    fun `masthead trims surrounding whitespace`() {
        assertEquals("Mumbai", DiscoverHeroLogic.mastheadPlace("  Mumbai "))
    }

    @Test
    fun `masthead falls back to the country for a null city`() {
        assertEquals(DiscoverHeroLogic.PLACE_FALLBACK, DiscoverHeroLogic.mastheadPlace(null))
    }

    @Test
    fun `masthead falls back for a blank city rather than rendering an empty headline`() {
        assertEquals(DiscoverHeroLogic.PLACE_FALLBACK, DiscoverHeroLogic.mastheadPlace(""))
        assertEquals(DiscoverHeroLogic.PLACE_FALLBACK, DiscoverHeroLogic.mastheadPlace("   "))
    }

    // ── avatarInitial ────────────────────────────────────────────────────────

    @Test
    fun `avatar initial is a single uppercase letter`() {
        assertEquals("Y", DiscoverHeroLogic.avatarInitial("yash faid"))
    }

    @Test
    fun `avatar initial skips leading whitespace`() {
        assertEquals("A", DiscoverHeroLogic.avatarInitial("   ada lovelace"))
    }

    @Test
    fun `avatar initial never returns two letters even for a full name`() {
        assertEquals(1, DiscoverHeroLogic.avatarInitial("Test User").length)
    }

    @Test
    fun `avatar initial is empty for a missing name so the caller can show a glyph`() {
        assertEquals("", DiscoverHeroLogic.avatarInitial(null))
        assertEquals("", DiscoverHeroLogic.avatarInitial("   "))
    }

    @Test
    fun `avatar initial keeps a non-latin first character`() {
        assertEquals("अ", DiscoverHeroLogic.avatarInitial("अनु"))
    }

    // ── auto-advance ─────────────────────────────────────────────────────────

    @Test
    fun `carousel advances when there is more than one slide`() {
        assertTrue(DiscoverHeroLogic.shouldAutoAdvance(pageCount = 5, animationsEnabled = true))
    }

    @Test
    fun `carousel does not advance a single slide`() {
        assertFalse(DiscoverHeroLogic.shouldAutoAdvance(pageCount = 1, animationsEnabled = true))
        assertFalse(DiscoverHeroLogic.shouldAutoAdvance(pageCount = 0, animationsEnabled = true))
    }

    @Test
    fun `carousel does not advance when the user reduced motion`() {
        assertFalse(DiscoverHeroLogic.shouldAutoAdvance(pageCount = 5, animationsEnabled = false))
    }

    // ── nextPage ─────────────────────────────────────────────────────────────

    @Test
    fun `next page wraps at the end`() {
        assertEquals(1, DiscoverHeroLogic.nextPage(current = 0, pageCount = 3))
        assertEquals(0, DiscoverHeroLogic.nextPage(current = 2, pageCount = 3))
    }

    @Test
    fun `next page is zero for an empty carousel instead of dividing by zero`() {
        assertEquals(0, DiscoverHeroLogic.nextPage(current = 0, pageCount = 0))
    }
}
