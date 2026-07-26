package `in`.artistant.app.feature.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.designsystem.theme.AppRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlements: EntitlementStore,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    fun bindRole(role: AppRole) {
        _state.update {
            it.copy(
                isArtist = role == AppRole.Artist,
                productPrice = if (AppEnvironment.subscriptionsEnabled) "₹99" else null,
            )
        }
    }

    fun subscribe(onComplete: () -> Unit = {}) {
        if (!AppEnvironment.subscriptionsEnabled) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            // Play Billing seam — dormant until operator flips the flag.
            _state.update {
                it.copy(
                    working = false,
                    error = "Subscriptions aren't available yet. Check back soon.",
                )
            }
        }
    }

    fun restore() {
        if (!AppEnvironment.subscriptionsEnabled) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            entitlements.refresh()
            _state.update {
                it.copy(
                    working = false,
                    error = "No active subscription found.",
                )
            }
        }
    }
}
