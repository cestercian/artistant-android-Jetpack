package `in`.artistant.app.state

import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.platform.billing.AccountTokenStore
import `in`.artistant.app.platform.billing.MockSubscriptionService
import `in`.artistant.app.platform.billing.PurchaseOutcome
import `in`.artistant.app.platform.billing.SubscriptionProduct
import `in`.artistant.app.platform.billing.SubscriptionService
import `in`.artistant.app.platform.billing.SubscriptionTokenWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// --- Reusable test doubles (also imported by BookingViewModelTest) -----------------------

/** In-memory stand-in for the DataStore account-token persistence. */
class InMemoryAccountTokenStore : AccountTokenStore {
    private val map = mutableMapOf<String, String>()
    override suspend fun read(userId: String): String? = map[userId]
    override suspend fun write(userId: String, token: String) { map[userId] = token }
}

/** Records every binding write so tests can assert the token + the write-before-purchase order. */
class RecordingTokenWriter : SubscriptionTokenWriter {
    data class Call(val token: String, val userId: String)
    val calls = mutableListOf<Call>()
    override suspend fun bind(appAccountToken: String, userId: String) {
        calls.add(Call(appAccountToken, userId))
    }
}

/** A service whose purchase() captures whether the binding row already existed when it ran. */
private class OrderProbeService(private val writer: RecordingTokenWriter) : SubscriptionService {
    var boundBeforePurchase = false
    var tokenSeen: String? = null
    override val transactionUpdates: Flow<Unit> = MutableSharedFlow()
    override suspend fun loadProducts(ids: List<String>): List<SubscriptionProduct> = emptyList()
    override suspend fun purchase(productId: String, obfuscatedAccountId: String): PurchaseOutcome {
        boundBeforePurchase = writer.calls.isNotEmpty()
        tokenSeen = obfuscatedAccountId
        return PurchaseOutcome.Purchased
    }
    override suspend fun restore() {}
    override suspend fun currentEntitlements(): Set<String> = emptySet()
}

@OptIn(ExperimentalCoroutinesApi::class)
class EntitlementStoreTest {

    private val dispatcher = StandardTestDispatcher()
    private val userId = "11111111-1111-1111-1111-111111111111"
    private val artistProduct = AppEnvironment.ARTIST_MONTHLY_PRODUCT_ID

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun store(
        service: SubscriptionService,
        tokens: AccountTokenStore = InMemoryAccountTokenStore(),
        writer: SubscriptionTokenWriter = RecordingTokenWriter(),
        enabled: Boolean = true,
    ) = EntitlementStore(service, tokens, writer, enabled)

    @Test
    fun `obfuscatedAccountId is stable per user across purchases and is not the raw uuid`() =
        runTest(dispatcher) {
            val writer = RecordingTokenWriter()
            val store = store(MockSubscriptionService(), writer = writer)

            store.purchase(artistProduct, userId)
            store.purchase(artistProduct, userId)
            advanceUntilIdle()

            val tokens = writer.calls.map { it.token }
            // Two purchases, same stable token both times.
            assertEquals(2, tokens.size)
            assertEquals(tokens[0], tokens[1])
            // The token is NOT the raw user uuid (anti-poisoning: unguessable, random).
            assertNotEquals(userId, tokens[0])
        }

    @Test
    fun `the binding row is written BEFORE the purchase call`() = runTest(dispatcher) {
        val writer = RecordingTokenWriter()
        val probe = OrderProbeService(writer)
        val store = store(probe, writer = writer)

        store.purchase(artistProduct, userId)
        advanceUntilIdle()

        assertTrue("binding must be written before service.purchase", probe.boundBeforePurchase)
        // The exact token bound is the same one the service was asked to purchase with.
        assertEquals(writer.calls.last().token, probe.tokenSeen)
    }

    @Test
    fun `the entitlement mirror updates from a successful purchase`() = runTest(dispatcher) {
        val store = store(MockSubscriptionService(outcome = PurchaseOutcome.Purchased))

        assertFalse(store.isEntitled(artistProduct))
        val outcome = store.purchase(artistProduct, userId)
        advanceUntilIdle()

        assertEquals(PurchaseOutcome.Purchased, outcome)
        assertTrue(store.isEntitled(artistProduct))
        assertTrue(store.entitledProductIds.value.contains(artistProduct))
    }

    @Test
    fun `a pending purchase sets purchasePending and does NOT entitle`() = runTest(dispatcher) {
        val store = store(MockSubscriptionService(outcome = PurchaseOutcome.Pending))

        val outcome = store.purchase(artistProduct, userId)
        advanceUntilIdle()

        assertEquals(PurchaseOutcome.Pending, outcome)
        assertTrue(store.purchasePending.value)
        assertFalse(store.isEntitled(artistProduct))
    }

    @Test
    fun `disabled store is inert - no binding write, no purchase, no entitlement`() =
        runTest(dispatcher) {
            val writer = RecordingTokenWriter()
            val store = store(MockSubscriptionService(), writer = writer, enabled = false)

            val outcome = store.purchase(artistProduct, userId)
            store.loadProducts()
            advanceUntilIdle()

            assertEquals(PurchaseOutcome.Cancelled, outcome)
            assertTrue("no binding row when flag off", writer.calls.isEmpty())
            assertTrue("no products loaded when flag off", store.products.value.isEmpty())
            assertFalse(store.isEntitled(artistProduct))
        }
}
