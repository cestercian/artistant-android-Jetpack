package `in`.artistant.app.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.billing.PurchaseOutcome
import `in`.artistant.app.state.EntitlementStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives [PaywallScreen] — a thin adapter over the @Singleton [EntitlementStore] (port of how
 * the iOS `PaywallView` reads `EntitlementStore` + `AuthService`). Re-exposes the store's
 * flows and resolves the buyer's user id from [SessionManager] for the purchase.
 *
 * All of this is a no-op with `subscriptionsEnabled` off: the store is inert, so `products`
 * stays empty and `subscribe`/`restore` short-circuit to Cancelled.
 */
@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlements: EntitlementStore,
    private val session: SessionManager,
) : ViewModel() {

    val products = entitlements.products
    val loadingProducts = entitlements.loadingProducts
    val purchasePending = entitlements.purchasePending

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    init {
        // Defensive re-pull in case the paywall opened before the store's own init-load settled
        // (mirrors iOS `.task { if products.isEmpty { loadProducts() } }`). Inert when disabled.
        viewModelScope.launch { entitlements.loadProducts() }
    }

    /**
     * Buy [productId]. On [PurchaseOutcome.Purchased] invokes [onPurchased] (the gate resumes +
     * the sheet dismisses); Pending shows the "waiting for approval" line via the store's
     * `purchasePending`; Cancelled is a no-op.
     */
    fun subscribe(productId: String, onPurchased: () -> Unit) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            val uid = session.currentUserId
            val outcome =
                if (uid == null) PurchaseOutcome.Cancelled
                else entitlements.purchase(productId, uid)
            _working.value = false
            if (outcome == PurchaseOutcome.Purchased) onPurchased()
        }
    }

    /** Restore purchases (App-review-required affordance). Resumes the gate if now entitled. */
    fun restore(productId: String, onPurchased: () -> Unit) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            entitlements.restore()
            _working.value = false
            if (entitlements.isEntitled(productId)) onPurchased()
        }
    }
}
