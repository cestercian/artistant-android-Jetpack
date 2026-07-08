package `in`.artistant.app.state

import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.platform.billing.AccountTokenStore
import `in`.artistant.app.platform.billing.PurchaseOutcome
import `in`.artistant.app.platform.billing.SubscriptionProduct
import `in`.artistant.app.platform.billing.SubscriptionService
import `in`.artistant.app.platform.billing.SubscriptionTokenWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * The fast/optimistic mirror of the server-authoritative `public.subscriptions` table (port of
 * iOS `EntitlementStore`). A single @Singleton (provided in [BillingModule]) the paywall + the
 * entitlement gates read. Server enforcement (`has_active_subscription`) is the real gate; this
 * is for instant UX (show/hide the paywall).
 *
 * INERT unless [enabled] (= [AppEnvironment.subscriptionsEnabled]): the constructor starts no
 * transaction listener and loads nothing, and every side-effectful action ([purchase],
 * [restore], [loadProducts], [refreshEntitlements]) early-returns. So with the flag off (v1) the
 * whole feature adds zero launch cost / network / Play work — and the bound [SubscriptionService]
 * is the Mock, so nothing touches Play services either. [enabled] is a constructor param (not a
 * direct AppEnvironment read) purely so unit tests can exercise the on-path.
 */
class EntitlementStore(
    private val service: SubscriptionService,
    private val accountTokens: AccountTokenStore,
    private val tokenWriter: SubscriptionTokenWriter,
    private val enabled: Boolean,
) {
    // Lifetime scope for the transaction listener + initial load (mirrors SessionManager's
    // pattern). SupervisorJob so one failed child doesn't tear the listener down.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _products = MutableStateFlow<List<SubscriptionProduct>>(emptyList())
    val products: StateFlow<List<SubscriptionProduct>> = _products.asStateFlow()

    private val _entitledProductIds = MutableStateFlow<Set<String>>(emptySet())
    val entitledProductIds: StateFlow<Set<String>> = _entitledProductIds.asStateFlow()

    private val _loadingProducts = MutableStateFlow(false)
    val loadingProducts: StateFlow<Boolean> = _loadingProducts.asStateFlow()

    /** Set when a purchase returns [PurchaseOutcome.Pending] (UPI-Autopay SCA / Ask-to-Buy). */
    private val _purchasePending = MutableStateFlow(false)
    val purchasePending: StateFlow<Boolean> = _purchasePending.asStateFlow()

    init {
        if (enabled) {
            // Lifetime listener — renew/revoke/approved-after-pending flip entitlement live.
            scope.launch { service.transactionUpdates.collect { refreshEntitlements() } }
            scope.launch { refresh() }
        }
    }

    // MARK: - Reads

    /** Is the user entitled to [productId]? Gates ask this with the role's product id. */
    fun isEntitled(productId: String): Boolean = _entitledProductIds.value.contains(productId)

    fun product(id: String): SubscriptionProduct? = _products.value.firstOrNull { it.id == id }

    // MARK: - Loads

    suspend fun refresh() {
        loadProducts()
        refreshEntitlements()
    }

    suspend fun refreshEntitlements() {
        if (!enabled) return
        _entitledProductIds.value = service.currentEntitlements()
        if (_entitledProductIds.value.isNotEmpty()) _purchasePending.value = false
    }

    suspend fun loadProducts() {
        if (!enabled) return
        _loadingProducts.value = true
        try {
            _products.value = service.loadProducts(AppEnvironment.subscriptionProductIds)
        } catch (t: Throwable) {
            _products.value = emptyList()
            Timber.d(t, "loadProducts failed — usually a config issue (no Play products yet).")
        } finally {
            _loadingProducts.value = false
        }
    }

    // MARK: - Actions

    /**
     * Buy [productId] for [userId]. Resolves a STABLE RANDOM obfuscatedAccountId, writes the
     * token→user binding BEFORE purchasing (the future Play RTDN handler attributes the sub
     * ONLY from that authenticated binding, never the client-set token — and a RANDOM token,
     * not the raw uuid, closes the pre-registration poisoning window: an attacker who knows the
     * victim's uid still can't predict the token). Refreshes entitlements on success.
     */
    suspend fun purchase(productId: String, userId: String): PurchaseOutcome {
        if (!enabled) return PurchaseOutcome.Cancelled
        return try {
            val token = obfuscatedAccountId(userId)
            tokenWriter.bind(token, userId)                       // BEFORE purchase — see doc above
            val outcome = service.purchase(productId, token)
            when (outcome) {
                PurchaseOutcome.Purchased -> refreshEntitlements()
                PurchaseOutcome.Pending -> _purchasePending.value = true
                PurchaseOutcome.Cancelled -> Unit
            }
            outcome
        } catch (t: Throwable) {
            Timber.w(t, "purchase failed")
            PurchaseOutcome.Cancelled
        }
    }

    suspend fun restore() {
        if (!enabled) return
        try {
            service.restore()
            refreshEntitlements()
        } catch (t: Throwable) {
            Timber.w(t, "restore failed")
        }
    }

    /**
     * A STABLE, RANDOM obfuscatedAccountId for [userId], persisted locally so all of this user's
     * purchases + renewals resolve to the same server-side binding. Random (NOT the raw uuid) so
     * it's unguessable — see [purchase]. Generation lives here (not in the store's real impl) so
     * the stability + not-raw-uuid property is unit-testable with an in-memory [AccountTokenStore].
     */
    private suspend fun obfuscatedAccountId(userId: String): String {
        accountTokens.read(userId)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        accountTokens.write(userId, fresh)
        return fresh
    }
}
