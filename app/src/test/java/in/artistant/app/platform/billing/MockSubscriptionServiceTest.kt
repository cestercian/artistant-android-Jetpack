package `in`.artistant.app.platform.billing

import `in`.artistant.app.core.config.AppEnvironment
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Play-free default service: fabricated products + configurable purchase outcomes. */
class MockSubscriptionServiceTest {

    private val artist = AppEnvironment.ARTIST_MONTHLY_PRODUCT_ID
    private val client = AppEnvironment.CLIENT_MONTHLY_PRODUCT_ID

    @Test
    fun `loadProducts fabricates both role products with an artist-only intro offer`() = runTest {
        val products = MockSubscriptionService().loadProducts(AppEnvironment.subscriptionProductIds)

        assertEquals(2, products.size)
        val a = products.first { it.id == artist }
        val c = products.first { it.id == client }
        assertEquals("₹99.00", a.displayPrice)
        assertEquals("month", a.periodLabel)
        // Artist gets the 3-months-free intro; the client product has none (server quota instead).
        assertTrue(!a.introOffer.isNullOrEmpty())
        assertNull(c.introOffer)
    }

    @Test
    fun `purchased outcome entitles the product`() = runTest {
        val svc = MockSubscriptionService(outcome = PurchaseOutcome.Purchased)
        assertEquals(PurchaseOutcome.Purchased, svc.purchase(artist, "tok"))
        assertTrue(svc.currentEntitlements().contains(artist))
    }

    @Test
    fun `pending outcome does not entitle`() = runTest {
        val svc = MockSubscriptionService(outcome = PurchaseOutcome.Pending)
        assertEquals(PurchaseOutcome.Pending, svc.purchase(artist, "tok"))
        assertFalse(svc.currentEntitlements().contains(artist))
    }

    @Test
    fun `cancelled outcome does not entitle`() = runTest {
        val svc = MockSubscriptionService(outcome = PurchaseOutcome.Cancelled)
        assertEquals(PurchaseOutcome.Cancelled, svc.purchase(artist, "tok"))
        assertTrue(svc.currentEntitlements().isEmpty())
    }
}
