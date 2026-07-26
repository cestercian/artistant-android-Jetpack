package `in`.artistant.app.feature.paywall

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.platform.billing.PlayBillingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlements: EntitlementStore,
    private val billing: PlayBillingService,
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
        if (AppEnvironment.subscriptionsEnabled) {
            viewModelScope.launch {
                val price = runCatching { billing.queryMonthlyPrice() }.getOrNull()
                if (price != null) _state.update { it.copy(productPrice = price) }
            }
        }
    }

    fun subscribe(activity: Activity?, onComplete: () -> Unit = {}) {
        if (!AppEnvironment.subscriptionsEnabled) return
        if (activity == null) {
            _state.update { it.copy(error = "Couldn't open Play Billing.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            val result = runCatching { billing.launchSubscribe(activity) }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { purchased ->
                    if (purchased) {
                        entitlements.refresh()
                        _state.update { it.copy(working = false) }
                        onComplete()
                    } else {
                        _state.update { it.copy(working = false) }
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(working = false, error = e.message ?: "Purchase failed.")
                    }
                },
            )
        }
    }

    fun restore() {
        if (!AppEnvironment.subscriptionsEnabled) return
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            entitlements.refresh()
            val entitled = entitlements.isEntitled.value
            _state.update {
                it.copy(
                    working = false,
                    error = if (entitled) null else "No active subscription found.",
                )
            }
        }
    }
}
