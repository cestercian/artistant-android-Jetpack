package `in`.artistant.app.feature.paywall

import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.platform.billing.PlayBillingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic subscription mirror — port of iOS `EntitlementStore`.
 * Queries Play Billing when [AppEnvironment.subscriptionsEnabled]; otherwise inert.
 */
@Singleton
open class EntitlementStore {
    private val billing: PlayBillingService?

    @Inject
    constructor(billing: PlayBillingService) {
        this.billing = billing
    }

    /** JVM unit tests — no Play Billing client; always not entitled. */
    constructor() {
        this.billing = null
    }

    private val _isEntitled = MutableStateFlow(false)
    val isEntitled: StateFlow<Boolean> = _isEntitled.asStateFlow()

    open val subscriptionsActive: Boolean get() = AppEnvironment.subscriptionsEnabled

    fun isEntitled(productId: String): Boolean =
        subscriptionsActive && _isEntitled.value

    suspend fun refresh() {
        if (!subscriptionsActive || billing == null) {
            _isEntitled.value = false
            return
        }
        _isEntitled.value = runCatching { billing!!.hasActiveSubscription() }.getOrDefault(false)
    }
}

data class PaywallUiState(
    val isArtist: Boolean = true,
    val working: Boolean = false,
    val productPrice: String? = null,
    val error: String? = null,
)
