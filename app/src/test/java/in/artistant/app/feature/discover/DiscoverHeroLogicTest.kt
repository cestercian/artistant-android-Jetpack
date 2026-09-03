package `in`.artistant.app.feature.discover

import `in`.artistant.app.testsupport.pkg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Rules behind the Discover header, hero card and rails. Pure — no Compose runtime.
 *
 * The interesting cases here are all "the input is technically present but
 * useless": a whitespace-only city, a name that starts with a space, an
 * unranked artist with a high raw score. Each of those shipped as a visible bug
 * in some incarnation of this screen, so they get a test rather than a comment.
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

    // ── header subtitle + rail titles ────────────────────────────────────────
    //
    // The design's note for screen 02 is that the city and the date sit in the
    // header BECAUSE they scope every price under it. Both halves therefore have
    // to be facts: the city is the profile's, the date is today's, and the rail
    // title names the same day the rail's own query was scoped to.

    @Test
    fun `header subtitle joins the city and today's date`() {
        assertEquals(
            "Chennai \u00b7 Sat 10 Oct",
            DiscoverHeroLogic.headerSubtitle("Chennai", LocalDate.of(2026, 10, 10)),
        )
    }

    @Test
    fun `header subtitle still names the country when the city is missing`() {
        assertTrue(
            DiscoverHeroLogic.headerSubtitle(null, LocalDate.of(2026, 10, 10))
                .startsWith(DiscoverHeroLogic.PLACE_FALLBACK),
        )
    }

    @Test
    fun `the availability rail is titled with the day it queried`() {
        assertEquals(
            "Available Sat night",
            DiscoverHeroLogic.availableRailTitle(LocalDate.of(2026, 10, 10)),
        )
        assertEquals(
            "Available Wed night",
            DiscoverHeroLogic.availableRailTitle(LocalDate.of(2026, 10, 14)),
        )
    }

    // ── publishesAvailability ────────────────────────────────────────────────

    @Test
    fun `an artist who published that weekday passes the second gate`() {
        assertTrue(
            DiscoverHeroLogic.publishesAvailability(listOf("Fri", "Sat"), LocalDate.of(2026, 10, 10)),
        )
    }

    @Test
    fun `an artist who published other weekdays is dropped from the rail`() {
        assertFalse(
            DiscoverHeroLogic.publishesAvailability(listOf("Mon", "Tue"), LocalDate.of(2026, 10, 10)),
        )
    }

    /**
     * An empty `days_available` is an absence of information, not a statement of
     * unavailability — dropping everyone who has not filled the field in would
     * empty the rail on a young roster.
     */
    @Test
    fun `an artist who published nothing is not treated as unavailable`() {
        assertTrue(DiscoverHeroLogic.publishesAvailability(emptyList(), LocalDate.of(2026, 10, 10)))
    }

    // ── hero badge and rating ────────────────────────────────────────────────

    @Test
    fun `only a top band earns the hero badge`() {
        assertEquals("Top rated", DiscoverHeroLogic.heroBadge(score = 94, gigs = 20))
        assertEquals("Trusted", DiscoverHeroLogic.heroBadge(score = 80, gigs = 20))
        assertNull(DiscoverHeroLogic.heroBadge(score = 65, gigs = 20))
        assertNull(DiscoverHeroLogic.heroBadge(score = 0, gigs = 0))
    }

    /**
     * A 94-scoring artist with four completed gigs is unranked — ScoreBands says
     * so — so the badge must not promote them on the busiest screen in the app.
     */
    @Test
    fun `an unranked artist gets no badge however high the raw score`() {
        assertNull(DiscoverHeroLogic.heroBadge(score = 94, gigs = 4))
    }

    @Test
    fun `a fresh act with no rating shows no rating cell rather than zero`() {
        assertNull(DiscoverHeroLogic.heroRating(rating = 0.0, gigs = 0))
    }

    @Test
    fun `the rating cell carries the count it is an average of`() {
        assertEquals("4.92 (128)", DiscoverHeroLogic.heroRating(rating = 4.92, gigs = 128))
        assertEquals("4.50", DiscoverHeroLogic.heroRating(rating = 4.5, gigs = 0))
    }

    // ── fromPriceLabel ───────────────────────────────────────────────────────
    //
    // `search_artists` maps a NULL `min_price` to 0 and the RPC deliberately
    // keeps no-package artists in unfiltered results, so those artists reach the
    // hero. The shipped expression quoted them at "FROM ₹0" while their own
    // profile said "Pricing on request" for the same fact.

    @Test
    fun `a no-package artist with no server price is quoted on request, not free`() {
        assertEquals(DiscoverHeroLogic.PRICE_ON_REQUEST, DiscoverHeroLogic.fromPriceLabel(emptyList(), 0))
    }

    @Test
    fun `a negative server price is an absence too`() {
        assertEquals(DiscoverHeroLogic.PRICE_ON_REQUEST, DiscoverHeroLogic.fromPriceLabel(emptyList(), -1))
    }

    @Test
    fun `the row price carries a no-package artist that has one`() {
        assertEquals("FROM ₹25K", DiscoverHeroLogic.fromPriceLabel(emptyList(), 25_000))
    }

    @Test
    fun `from means the cheapest tier, not the first one listed`() {
        // The shipped expression was `packages.firstOrNull()?.price`, so an
        // artist whose dearest tier happens to be listed first advertised it as
        // their floor — the exact bug PackagePricing exists to stop.
        val packages = listOf(
            pkg(id = "p1", name = "Full band", price = 83_000),
            pkg(id = "p2", name = "Acoustic", price = 22_000),
        )
        assertEquals("FROM ₹22K", DiscoverHeroLogic.fromPriceLabel(packages, 51_000))
    }

    @Test
    fun `packages beat a stale denormalized row price`() {
        // `artists.min_price` is confirmed stale on dev — see PackagePricing.
        assertEquals(
            "FROM ₹22K",
            DiscoverHeroLogic.fromPriceLabel(listOf(pkg(id = "p1", name = "Acoustic", price = 22_000)), 51_000),
        )
    }
}
