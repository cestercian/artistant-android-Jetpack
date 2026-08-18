package `in`.artistant.app.platform.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.artistant.app.core.config.AppEnvironment
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Play Billing seam — port of iOS StoreKit SubscriptionService.
 * Fully wired but inert until [AppEnvironment.subscriptionsEnabled] flips.
 * Product id matches iOS ASC: `in.artistant.app.subscription.monthly`.
 */
@Singleton
class PlayBillingService @Inject constructor(
    @ApplicationContext private val context: Context,
) : PurchasesUpdatedListener {

    private val connectMutex = Mutex()

    @Volatile
    private var client: BillingClient? = null

    @Volatile
    private var cachedDetails: ProductDetails? = null

    /**
     * The pending [launchSubscribe] continuation, or null when no flow is open.
     *
     * Atomic because it is written from a coroutine dispatcher and read from Play's
     * own callback thread in [onPurchasesUpdated] — a plain `var` published neither
     * write reliably — and because "take it and clear it" has to be one step: two
     * flows racing could otherwise both read the same callback and resume one
     * continuation twice.
     */
    private val purchaseCallback = AtomicReference<((Result<Boolean>) -> Unit)?>(null)

    fun isEnabled(): Boolean = AppEnvironment.subscriptionsEnabled

    /**
     * The connected client, or null when billing is off or the connection failed.
     *
     * Serialised on [connectMutex], and it hands the caller the reference it
     * connected rather than letting them read the field back. Both halves matter:
     *
     *  - unguarded, two callers arriving together (the paywall's price query racing
     *    `EntitlementStore.refresh`'s purchase query) each built a [BillingClient]
     *    and each overwrote the field. A BillingClient binds an AIDL service, so the
     *    loser's binding stayed alive for the whole process — this class is a
     *    `@Singleton`, nothing ever disposes it — and it kept the listener
     *    registration with it;
     *  - a caller that re-read the field could dereference a client that a
     *    reconnect had already replaced or nulled underneath it.
     *
     * The `endConnection()` on the way past a stale client is the only disposal path:
     * `onBillingServiceDisconnected` deliberately does not close anything (Play
     * raises it from inside its own teardown), so the next connect is what unbinds
     * the dead one.
     */
    private suspend fun connectedClient(): BillingClient? {
        if (!isEnabled()) return null
        val ready = client
        if (ready?.isReady == true) return ready
        return connectMutex.withLock {
            val current = client
            if (current?.isReady == true) {
                // Someone else connected while we waited for the lock.
                current
            } else {
                current?.endConnection()
                client = null
                val billing = BillingClient.newBuilder(context)
                    .setListener(this@PlayBillingService)
                    .enablePendingPurchases(
                        PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
                    )
                    .build()
                client = billing
                if (startConnection(billing)) billing else null
            }
        }
    }

    private suspend fun startConnection(billing: BillingClient): Boolean =
        suspendCancellableCoroutine { cont ->
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) {
                        cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // Logged only. `isReady` is false from here on, so the next
                    // connectedClient() ends this instance and builds a fresh one;
                    // reconnecting from inside Play's callback would race that.
                    Timber.w("Play Billing disconnected")
                }
            })
        }

    suspend fun queryMonthlyPrice(): String? {
        val billing = connectedClient() ?: return null
        val details = queryProductDetails(billing) ?: return null
        cachedDetails = details
        return details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
            ?: "₹99"
    }

    suspend fun launchSubscribe(activity: Activity): Result<Boolean> {
        val billing = connectedClient()
            ?: return Result.failure(IllegalStateException("Billing unavailable"))
        val details = cachedDetails ?: queryProductDetails(billing)
            ?: return Result.failure(IllegalStateException("Product not found"))
        cachedDetails = details
        val offer = details.subscriptionOfferDetails?.firstOrNull()
            ?: return Result.failure(IllegalStateException("No offer"))
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offer.offerToken)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        return suspendCancellableCoroutine { cont ->
            val callback: (Result<Boolean>) -> Unit = { cont.resume(it) }
            // One flow at a time, and the one being displaced is told so. Overwriting
            // the slot silently — what this did before — stranded the first
            // continuation forever: nothing would ever resume it, so its caller's
            // "working" spinner never cleared and the coroutine leaked until the
            // ViewModel died.
            purchaseCallback.getAndSet(callback)?.invoke(
                Result.failure(IllegalStateException("Superseded by another purchase")),
            )
            // A cancelled scope must not leave the slot armed, or the next real
            // purchase result is delivered to a dead continuation and dropped.
            cont.invokeOnCancellation { purchaseCallback.compareAndSet(callback, null) }
            val result = billing.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK &&
                purchaseCallback.compareAndSet(callback, null)
            ) {
                cont.resume(Result.failure(IllegalStateException(result.debugMessage)))
            }
        }
    }

    suspend fun hasActiveSubscription(): Boolean {
        val billing = connectedClient() ?: return false
        return suspendCancellableCoroutine { cont ->
            billing.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            ) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    cont.resume(false)
                    return@queryPurchasesAsync
                }
                cont.resume(
                    purchases.any {
                        it.products.contains(PRODUCT_ID) &&
                            it.purchaseState == Purchase.PurchaseState.PURCHASED
                    },
                )
            }
        }
    }

    /**
     * Play's global purchase listener. It also fires for purchases that finished
     * outside our flow — a renewal, a purchase completed in the Play Store app — so
     * acknowledgement happens whether or not a call is waiting (an unacknowledged
     * purchase is auto-refunded after three days), while the pending continuation is
     * taken atomically and only ever resumed once.
     */
    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val cb = purchaseCallback.getAndSet(null)
        if (result.responseCode == BillingClient.BillingResponseCode.OK && !purchases.isNullOrEmpty()) {
            purchases.forEach { acknowledgeIfNeeded(it) }
            cb?.invoke(Result.success(true))
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            cb?.invoke(Result.success(false))
        } else {
            cb?.invoke(Result.failure(IllegalStateException(result.debugMessage)))
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        client?.acknowledgePurchase(params) { /* fire-and-forget */ }
    }

    private suspend fun queryProductDetails(billing: BillingClient): ProductDetails? =
        suspendCancellableCoroutine { cont ->
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()
            billing.queryProductDetailsAsync(params) { result, list ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    cont.resume(null)
                } else {
                    cont.resume(list.firstOrNull())
                }
            }
        }

    companion object {
        const val PRODUCT_ID = "in.artistant.app.subscription.monthly"
    }
}
