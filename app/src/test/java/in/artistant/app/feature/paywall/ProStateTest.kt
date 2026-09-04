package `in`.artistant.app.feature.paywall

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which of design screens 25 / 91 / 92 / 93 the paywall shows.
 *
 * The rule lives in one pure function precisely so it can be pinned here, and the ordering it
 * encodes is not cosmetic. Screen 92's design note is that a store outage **never implies a
 * lost plan** — so an entitled user must never be shown "can't load plans", however badly the
 * store is behaving. That is one line in [proStateFor] and one silent, severe regression if it
 * moves.
 */
class ProStateTest {

    @Test
    fun `a price and no entitlement is the offer`() {
        assertEquals(ProState.Offer, proStateFor(entitled = false, awaitingEntitlement = false, price = "₹499"))
    }

    @Test
    fun `an entitlement is its own screen`() {
        assertEquals(ProState.Active, proStateFor(entitled = true, awaitingEntitlement = false, price = "₹499"))
    }

    @Test
    fun `an entitled user is NEVER shown the outage screen`() {
        // The regression this ordering exists to prevent: the store went down, we have no
        // price, and the person reading the screen is a paying subscriber. Telling them the
        // plans can't load over an active subscription is exactly the implication screen 92's
        // copy is written to avoid.
        assertEquals(ProState.Active, proStateFor(entitled = true, awaitingEntitlement = false, price = null))
    }

    @Test
    fun `an entitlement beats a stale pending flag too`() {
        assertEquals(ProState.Active, proStateFor(entitled = true, awaitingEntitlement = true, price = "₹499"))
    }

    @Test
    fun `a finished flow with no entitlement yet is pending, not a silent no-op`() {
        // UPI mandates and bank SCA can sit pending for minutes. Without this state the
        // Subscribe button would appear to do nothing at all.
        assertEquals(ProState.Pending, proStateFor(entitled = false, awaitingEntitlement = true, price = "₹499"))
    }

    @Test
    fun `a pending purchase survives losing the price`() {
        assertEquals(ProState.Pending, proStateFor(entitled = false, awaitingEntitlement = true, price = null))
    }

    @Test
    fun `no price means no offer`() {
        // A paywall with no price is not an offer, it is a broken screen pretending to be one.
        assertEquals(ProState.Unavailable, proStateFor(entitled = false, awaitingEntitlement = false, price = null))
        assertEquals(ProState.Unavailable, proStateFor(entitled = false, awaitingEntitlement = false, price = ""))
        assertEquals(ProState.Unavailable, proStateFor(entitled = false, awaitingEntitlement = false, price = "   "))
    }

    @Test
    fun `the dormant seam lands on unavailable, which is the truthful state today`() {
        // `AppEnvironment.subscriptionsEnabled` is a compile-time false, so `load()` never
        // queries a price. That is not a placeholder — 92 is the correct screen for a
        // subscription that is not on sale, and its copy protects any entitlement that exists.
        assertEquals(ProState.Unavailable, PaywallUiState(loading = false).proState)
    }

    @Test
    fun `the ui state derives its screen rather than storing one`() {
        // Nothing may set the screen by hand, or the four screens can disagree with billing.
        assertEquals(
            proStateFor(entitled = true, awaitingEntitlement = false, price = "₹499"),
            PaywallUiState(loading = false, entitled = true, price = "₹499").proState,
        )
    }
}
