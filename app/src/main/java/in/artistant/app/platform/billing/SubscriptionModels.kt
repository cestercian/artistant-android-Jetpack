package `in`.artistant.app.platform.billing

/**
 * Play-Billing-free description of a subscription product, for the paywall (iOS
 * `SubscriptionProduct`). [PlayBillingSubscriptionService] maps a Play `ProductDetails`
 * → this DTO so the UI never touches BillingClient types, and [MockSubscriptionService]
 * can stand in with zero Play dependency (exactly how the payments seam's mock does).
 */
data class SubscriptionProduct(
    val id: String,            // the Play product id
    val displayName: String,   // localized, from Play Console
    val displayPrice: String,  // localized + currency-correct, e.g. "₹99.00"
    val periodLabel: String,   // recurring period, e.g. "month"
    /**
     * Localized intro-offer line, e.g. "Free for 3 months, then ₹99/month", or null when
     * the account isn't eligible / the product has no intro (the client product has none —
     * its "free" is the server booking quota, not a Play trial).
     */
    val introOffer: String?,
)

/** The three terminal states of a `purchase()` call (iOS `SubscriptionPurchaseOutcome`). */
enum class PurchaseOutcome {
    /** Acknowledged + verified — entitlement is now active. */
    Purchased,

    /**
     * Deferred: a UPI-Autopay mandate awaiting approval (common in India) or Ask-to-Buy.
     * Do NOT unlock — the transaction-updates flow delivers it once Play/RTDN approves.
     */
    Pending,

    /** User backed out, or an unverified/failed result. */
    Cancelled,
}

/** Failure modes a subscription backend can surface (iOS `SubscriptionError`). */
sealed class SubscriptionError(message: String) : Exception(message) {
    data object ProductNotFound : SubscriptionError("Subscription product not found")
    data object BillingUnavailable : SubscriptionError("Play billing unavailable")
}
