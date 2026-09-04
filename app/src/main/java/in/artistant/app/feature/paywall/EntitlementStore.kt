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
class EntitlementStore {
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

    val subscriptionsActive: Boolean get() = AppEnvironment.subscriptionsEnabled

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

/**
 * Everything design screens 25 / 91 / 92 / 93 are drawn from.
 *
 * Note there is no `state` field: which of the four screens shows is DERIVED by [proStateFor]
 * from the three facts below, so it cannot be set to something billing disagrees with.
 */
data class PaywallUiState(
    val isArtist: Boolean = true,
    /** The first store query is still out. */
    val loading: Boolean = true,
    /** A purchase or restore is in flight. */
    val working: Boolean = false,
    /** Play Billing says there is an active subscription. */
    val entitled: Boolean = false,
    /** A purchase flow finished and the entitlement has not landed — a deferred payment. */
    val awaitingEntitlement: Boolean = false,
    /** The formatted price from Play, or null when the store could not answer. */
    val price: String? = null,
    val error: String? = null,
) {
    val proState: ProState get() = proStateFor(entitled, awaitingEntitlement, price)
}
