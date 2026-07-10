package `in`.artistant.app.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.state.DeepLinkRouter
import `in`.artistant.app.state.DeepLinkTab
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The seam that lets the (composable, VM-less) tab scaffolds observe the @Singleton
 * [DeepLinkRouter] — a @Composable can't inject a Hilt singleton directly, so this thin
 * ViewModel re-exposes the one channel the outer scaffold cares about: the parked [pendingTab].
 * Each scaffold switches to it (when the target's role matches its own) then [consumePendingTab].
 */
@HiltViewModel
class TabDeepLinkViewModel @Inject constructor(
    private val deepLink: DeepLinkRouter,
) : ViewModel() {
    val pendingTab: StateFlow<DeepLinkTab?> = deepLink.pendingTab
    fun consumePendingTab() = deepLink.consumePendingTab()
}
