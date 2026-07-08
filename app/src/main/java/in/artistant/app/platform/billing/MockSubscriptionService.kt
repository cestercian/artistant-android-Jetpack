package `in`.artistant.app.platform.billing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Play-Billing-free stand-in (iOS `MockSubscriptionService`) — the DEFAULT bound impl in
 * v1. Fabricates the two products and tracks entitlement in memory; it touches no
 * BillingClient, so it's safe with the flag off and needs no Play services on the device.
 *
 * [outcome] makes the purchase result configurable so unit tests can drive
 * purchased / pending / cancelled; production uses the default (purchased).
 */
class MockSubscriptionService(
    private val outcome: PurchaseOutcome = PurchaseOutcome.Purchased,
    entitled: Set<String> = emptySet(),
) : SubscriptionService {

    private val entitledSet = entitled.toMutableSet()

    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val transactionUpdates: Flow<Unit> = _updates.asSharedFlow()

    override suspend fun loadProducts(ids: List<String>): List<SubscriptionProduct> =
        ids.map { id ->
            val isArtist = id.endsWith(".artist.monthly")
            SubscriptionProduct(
                id = id,
                displayName = if (isArtist) "Artist membership" else "Client membership",
                displayPrice = "₹99.00",
                periodLabel = "month",
                introOffer = if (isArtist) "Free for 3 months, then ₹99.00/month" else null,
            )
        }.sortedBy { it.id } // stable order (artist before client) — not at Play's mercy

    override suspend fun purchase(productId: String, obfuscatedAccountId: String): PurchaseOutcome {
        if (outcome == PurchaseOutcome.Purchased) {
            entitledSet.add(productId)
            _updates.tryEmit(Unit)
        }
        return outcome
    }

    override suspend fun restore() { /* nothing to sync — in-memory */ }

    override suspend fun currentEntitlements(): Set<String> = entitledSet.toSet()
}
