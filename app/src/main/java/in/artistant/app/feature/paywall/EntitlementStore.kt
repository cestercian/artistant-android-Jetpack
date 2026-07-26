package `in`.artistant.app.feature.paywall

import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optimistic subscription mirror — port of iOS `EntitlementStore`.
 *
 * INERT when [AppEnvironment.subscriptionsEnabled] is false: no Play Billing
 * work, always reports not-subscribed. Real billing lands in M7 go-live.
 */
@Singleton
class EntitlementStore @Inject constructor() {
    private val _isEntitled = MutableStateFlow(false)
    val isEntitled: StateFlow<Boolean> = _isEntitled.asStateFlow()

    val subscriptionsActive: Boolean get() = AppEnvironment.subscriptionsEnabled

    fun isEntitled(productId: String): Boolean =
        subscriptionsActive && _isEntitled.value

    /** No-op stub until Play Billing is wired. */
    suspend fun refresh() {
        if (!subscriptionsActive) _isEntitled.value = false
    }
}

data class PaywallUiState(
    val isArtist: Boolean = true,
    val working: Boolean = false,
    val productPrice: String? = null,
    val error: String? = null,
)
