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
import timber.log.Timber
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

    private var client: BillingClient? = null
    private var cachedDetails: ProductDetails? = null
    private var purchaseCallback: ((Result<Boolean>) -> Unit)? = null

    val productId: String get() = PRODUCT_ID

    fun isEnabled(): Boolean = AppEnvironment.subscriptionsEnabled

    suspend fun ensureConnected(): Boolean {
        if (!isEnabled()) return false
        val existing = client
        if (existing?.isReady == true) return true
        return suspendCancellableCoroutine { cont ->
            val billing = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
                )
                .build()
            client = billing
            billing.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    if (cont.isActive) {
                        cont.resume(result.responseCode == BillingClient.BillingResponseCode.OK)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    Timber.w("Play Billing disconnected")
                }
            })
        }
    }

    suspend fun queryMonthlyPrice(): String? {
        if (!ensureConnected()) return null
        val details = queryProductDetails() ?: return null
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
        if (!ensureConnected()) return Result.failure(IllegalStateException("Billing unavailable"))
        val details = cachedDetails ?: queryProductDetails()
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
            purchaseCallback = { cont.resume(it) }
            val result = client!!.launchBillingFlow(activity, flowParams)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                purchaseCallback = null
                cont.resume(Result.failure(IllegalStateException(result.debugMessage)))
            }
        }
    }

    suspend fun hasActiveSubscription(): Boolean {
        if (!ensureConnected()) return false
        return suspendCancellableCoroutine { cont ->
            client!!.queryPurchasesAsync(
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

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        val cb = purchaseCallback
        purchaseCallback = null
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

    private suspend fun queryProductDetails(): ProductDetails? =
        suspendCancellableCoroutine { cont ->
            val product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()
            client!!.queryProductDetailsAsync(params) { result, list ->
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
