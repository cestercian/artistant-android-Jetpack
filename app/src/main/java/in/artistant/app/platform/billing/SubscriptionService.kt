package `in`.artistant.app.platform.billing

import kotlinx.coroutines.flow.Flow

/**
 * The seam every subscription/paywall flow goes through (port of the iOS
 * `SubscriptionService` protocol). The UI never touches Play Billing types — the real
 * impl maps `ProductDetails` → [SubscriptionProduct] and takes/returns plain values, so
 * [MockSubscriptionService] can stand in with no Play dependency.
 *
 * Two impls exist: [MockSubscriptionService] (the DEFAULT bound impl in v1) and
 * [PlayBillingSubscriptionService] (bound only when `subscriptionsEnabled`). The server
 * (`public.subscriptions`, written by the future Play RTDN handler) is authoritative;
 * this on-device layer is the fast/optimistic mirror for UX.
 */
interface SubscriptionService {
    /**
     * Fetch + map the products for [ids]. Returns `[]` when none resolve (no Play config
     * yet) rather than throwing, so the paywall can show an "unavailable" state.
     */
    suspend fun loadProducts(ids: List<String>): List<SubscriptionProduct>

    /**
     * Buy [productId], tagging the Play purchase with [obfuscatedAccountId] (the stable,
     * RANDOM per-user token — NEVER the raw user uuid) so the server can attribute it via
     * the authenticated `subscription_account_tokens` binding.
     */
    suspend fun purchase(productId: String, obfuscatedAccountId: String): PurchaseOutcome

    /** Restore: re-query Play for active purchases; entitlements are re-read after. */
    suspend fun restore()

    /** Product ids the user is currently entitled to (active/grace subscriptions only). */
    suspend fun currentEntitlements(): Set<String>

    /**
     * Emits whenever the entitlement set may have changed (renewal, revoke, approved-after-
     * pending). Collected once by [EntitlementStore] for the app's lifetime — the Android
     * analogue of StoreKit's `Transaction.updates`, fed by the `PurchasesUpdatedListener`.
     */
    val transactionUpdates: Flow<Unit>
}
