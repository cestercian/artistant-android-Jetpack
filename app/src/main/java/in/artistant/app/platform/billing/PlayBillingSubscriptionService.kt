package `in`.artistant.app.platform.billing

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import `in`.artistant.app.core.config.AppEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber

/**
 * Real Play Billing skeleton — the Android analogue of the iOS `StoreKitSubscriptionService`.
 * Structurally complete: it wires a [PurchasesUpdatedListener], queries SUBS product details,
 * queries owned purchases (= restore on Android), and acknowledges a new purchase (required
 * within 3 days or Play auto-refunds). What it does NOT do yet is drive `launchBillingFlow` —
 * that needs an Activity handoff which is go-live wiring, not part of the dormant seam.
 *
 * DORMANT GUARANTEE (v1): this class is only *constructed* by the DI @Provides when
 * [AppEnvironment.subscriptionsEnabled] is true — with the flag off the Mock is bound and this
 * never enters the graph. Belt-and-suspenders, every method here also early-returns when the
 * flag is off and the [BillingClient] is built lazily, so even if it were bound it would never
 * touch Play services and never crash on a device without Play. See docs/API_MAPPING.md §7 for
 * the operator wiring (Play Console products + RTDN Pub/Sub → an Edge Function).
 */
class PlayBillingSubscriptionService(
    private val context: Context,
) : SubscriptionService {

    private val _updates = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    override val transactionUpdates: Flow<Unit> = _updates.asSharedFlow()

    // Play calls this after a launchBillingFlow completes. The skeleton acknowledges a
    // purchased sub and signals the store to re-read entitlements.
    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { p ->
                if (p.purchaseState == Purchase.PurchaseState.PURCHASED && !p.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(p.purchaseToken)
                        .build()
                    billing.acknowledgePurchase(ack) { /* go-live: log/retry ack failures */ }
                }
            }
            _updates.tryEmit(Unit)
        }
    }

    // Built lazily so merely constructing this service never touches Play services — the
    // client only materializes on the first real call, which the flag-guards gate anyway.
    private val billing: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()
    }

    override suspend fun loadProducts(ids: List<String>): List<SubscriptionProduct> {
        if (!AppEnvironment.subscriptionsEnabled) return emptyList()
        ensureConnected()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                ids.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val result = billing.queryProductDetails(params)
        return result.productDetailsList.orEmpty().map { it.toDto() }.sortedBy { it.id }
    }

    override suspend fun purchase(productId: String, obfuscatedAccountId: String): PurchaseOutcome {
        if (!AppEnvironment.subscriptionsEnabled) return PurchaseOutcome.Cancelled
        // A real purchase is `billing.launchBillingFlow(activity, params)` where params carries
        // BillingFlowParams.setObfuscatedAccountId(obfuscatedAccountId) — the anti-poisoning
        // RANDOM token, never the raw uuid. That needs an Activity this service doesn't hold;
        // wiring the Activity handoff (+ awaiting the PurchasesUpdatedListener) is go-live work.
        Timber.w("PlayBilling.purchase is a skeleton — the launchBillingFlow Activity handoff is go-live wiring.")
        return PurchaseOutcome.Cancelled
    }

    override suspend fun restore() {
        if (!AppEnvironment.subscriptionsEnabled) return
        // On Android "restore" is just re-querying owned purchases — currentEntitlements does it.
        currentEntitlements()
    }

    override suspend fun currentEntitlements(): Set<String> {
        if (!AppEnvironment.subscriptionsEnabled) return emptySet()
        ensureConnected()
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        val result = billing.queryPurchasesAsync(params)
        return result.purchasesList
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .flatMap { it.products }
            .toSet()
    }

    private fun ensureConnected() {
        if (billing.connectionState == BillingClient.ConnectionState.CONNECTED) return
        // ponytail: startConnection is callback-based; a real connect/retry/backoff loop is
        // go-live wiring. The flag-gated skeleton only needs to reference the API surface, not
        // run against a live Play backend — so this stays a no-op until go-live fills it in.
    }

    private fun ProductDetails.toDto(): SubscriptionProduct {
        // First offer's first pricing phase = the recurring base price. Intro-offer parsing
        // (free-trial phase) is go-live polish; the client product has no Play intro anyway.
        val phase = subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
        return SubscriptionProduct(
            id = productId,
            displayName = name,
            displayPrice = phase?.formattedPrice.orEmpty(),
            periodLabel = "month",
            introOffer = null,
        )
    }
}
