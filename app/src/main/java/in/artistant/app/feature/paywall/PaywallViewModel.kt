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

/**
 * Which of design screens 25 / 91 / 92 / 93 is on screen.
 *
 * Derived from [EntitlementStore] and the store query — never set by hand at a call site, so
 * the four screens cannot disagree with what billing actually says. See [proStateFor].
 */
enum class ProState {
    /** 25 — plans loaded, not entitled. The offer. */
    Offer,

    /** 91 — a purchase flow finished and the entitlement has not landed yet. */
    Pending,

    /** 92 — the store could not be reached, or subscriptions are not on sale in this build. */
    Unavailable,

    /** 93 — entitled. */
    Active,
}

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val entitlements: EntitlementStore,
    private val billing: PlayBillingService,
) : ViewModel() {

    private val _state = MutableStateFlow(PaywallUiState())
    val state: StateFlow<PaywallUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            entitlements.isEntitled.collect { entitled -> _state.update { it.copy(entitled = entitled) } }
        }
    }

    fun bindRole(role: AppRole) {
        _state.update { it.copy(isArtist = role == AppRole.Artist) }
        load()
    }

    /**
     * Query the store, and let the ANSWER decide which screen shows.
     *
     * With subscriptions dormant (`AppEnvironment.subscriptionsEnabled` false) there is nothing
     * to query and no price to invent, so this lands on [ProState.Unavailable] — which is the
     * truthful state for every build shipping today, and the reason screen 92's copy is written
     * to protect an existing entitlement rather than to announce a loss.
     */
    fun load() = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { entitlements.refresh() }
        if (!AppEnvironment.subscriptionsEnabled) {
            _state.update { it.copy(loading = false, price = null) }
            return@launch
        }
        val price = runCatching { billing.queryMonthlyPrice() }.getOrNull()
        _state.update { it.copy(loading = false, price = price) }
    }

    fun subscribe(activity: Activity?, onComplete: () -> Unit = {}) {
        if (!AppEnvironment.subscriptionsEnabled) return
        if (activity == null) {
            _state.update { it.copy(error = "Couldn't open Google Play from here.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            val result = runCatching { billing.launchSubscribe(activity) }.getOrElse { Result.failure(it) }
            result.fold(
                onSuccess = { purchased ->
                    // A completed flow does NOT mean an entitlement: a UPI mandate or a bank
                    // SCA step can leave the purchase pending for minutes. So the store is
                    // re-queried and the answer decides — `awaitingEntitlement` is what turns a
                    // finished flow with no entitlement into screen 91 rather than into a
                    // silent no-op that looks like the button did nothing.
                    entitlements.refresh()
                    val entitled = entitlements.isEntitled.value
                    _state.update { it.copy(working = false, awaitingEntitlement = purchased && !entitled) }
                    if (entitled) onComplete()
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
        if (!AppEnvironment.subscriptionsEnabled) {
            _state.update { it.copy(error = "Subscriptions aren't on sale in this version.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            entitlements.refresh()
            val entitled = entitlements.isEntitled.value
            _state.update {
                it.copy(
                    working = false,
                    awaitingEntitlement = false,
                    error = if (entitled) null else "No active subscription found.",
                )
            }
        }
    }

    /** Stop showing screen 91 — the user chose to wait somewhere else. */
    fun dismissPending() = _state.update { it.copy(awaitingEntitlement = false) }

    /**
     * A system handoff the screen could not complete — no Play app and no browser for the
     * subscription page. Reported on the footer's own error line instead of vanishing.
     */
    fun reportError(message: String) = _state.update { it.copy(error = message) }

    fun dismissError() = _state.update { it.copy(error = null) }
}

/**
 * Which Pro screen the current facts add up to.
 *
 * A pure function, and the only place the four states are decided — the ViewModel stores
 * inputs, not a screen name, so there is exactly one rule and a test can pin it. Order matters:
 *
 *  1. **Entitled wins outright.** Screen 92's note is that a store outage must never imply a
 *     lost plan; if the entitlement is live, an unreachable store is irrelevant to what this
 *     person has, and showing them "can't load plans" over an active subscription is precisely
 *     the lie the design forbids.
 *  2. **Awaiting beats unavailable**, for the same reason — a mandate settling is not an outage.
 *  3. **No price means no offer.** A paywall with no price is not an offer, it is a broken
 *     screen pretending to be one.
 */
fun proStateFor(entitled: Boolean, awaitingEntitlement: Boolean, price: String?): ProState = when {
    entitled -> ProState.Active
    awaitingEntitlement -> ProState.Pending
    price.isNullOrBlank() -> ProState.Unavailable
    else -> ProState.Offer
}
