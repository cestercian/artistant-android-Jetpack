package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the app-level gate should draw instead of the app.
 *
 * A separate type from [SystemStatus] because the two answer different
 * questions: the status is what the server says, the gate is what the user gets
 * — and the difference between them is the session dismissal on the outage
 * screen, which nothing outside this ViewModel should have to know about.
 */
sealed interface SystemGate {
    data object None : SystemGate
    data class Update(val installed: String, val minimum: String) : SystemGate
    data class Outage(val impact: String, val startedLabel: String?) : SystemGate

    companion object {
        /**
         * Pure resolution — the whole gate policy in one testable function.
         *
         * Two rules, both from the designs:
         *
         *  - **Update never yields.** Screen 120's own footnote says there is no
         *    dismiss by design, so [outageDismissed] does not reach it. An
         *    unsupported client can write booking state the server no longer
         *    understands, and "let them through just this once" is how that
         *    happens.
         *  - **Outage does.** Screen 121 draws a back control, so an outage is a
         *    wall the user may step around: the parts of the app that read
         *    cached state still work, and trapping someone behind a screen that
         *    says "this will clear itself" is worse than letting them look.
         */
        fun resolve(status: SystemStatus, outageDismissed: Boolean): SystemGate = when (status) {
            SystemStatus.Normal -> None
            is SystemStatus.UpdateRequired -> Update(status.installed, status.minimum)
            is SystemStatus.Outage ->
                if (outageDismissed) None else Outage(status.impact, status.startedLabel)
        }
    }
}

data class SystemGateUiState(
    val gate: SystemGate = SystemGate.None,
    /** "Check again" is in flight — the CTA says so rather than looking inert. */
    val checking: Boolean = false,
)

/**
 * Drives the two hard gates above the whole NavHost.
 *
 * Hoisted to the root rather than to a destination because a gate that only
 * covers the tab shells is not a gate: a signed-out user walking the signup flow
 * against an unsupported build writes the same rows.
 */
@HiltViewModel
class SystemGateViewModel @Inject constructor(
    private val source: SystemStatusSource,
) : ViewModel() {

    /**
     * Session-scoped, deliberately not persisted. An outage the user stepped
     * around an hour ago is not evidence about this launch, and a dismissal that
     * survives a restart would hide the screen for the one user most likely to
     * relaunch — the one who just hit the outage.
     */
    private val outageDismissed = MutableStateFlow(false)
    private val checking = MutableStateFlow(false)

    val state: StateFlow<SystemGateUiState> =
        combine(source.status, outageDismissed, checking) { status, dismissed, busy ->
            SystemGateUiState(
                gate = SystemGate.resolve(status, dismissed),
                checking = busy,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS),
            initialValue = SystemGateUiState(),
        )

    /** Screen 121's CTA. */
    fun checkAgain() {
        if (checking.value) return
        viewModelScope.launch {
            checking.value = true
            try {
                source.refresh()
            } finally {
                checking.value = false
            }
        }
    }

    /** Screen 121's back control. Never reachable from screen 120 — see [SystemGate.resolve]. */
    fun dismissOutage() {
        outageDismissed.value = true
    }

    private companion object {
        /** Survive a configuration change without re-subscribing the source. */
        const val SUBSCRIPTION_GRACE_MS = 5_000L
    }
}
