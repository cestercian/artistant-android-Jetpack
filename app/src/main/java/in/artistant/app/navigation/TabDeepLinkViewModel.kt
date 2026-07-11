package `in`.artistant.app.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.state.DeepLinkRouter
import `in`.artistant.app.state.DeepLinkTab
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * The seam that lets the (composable, VM-less) tab scaffolds + tab CONTAINERS observe the
 * @Singleton [DeepLinkRouter] — a @Composable can't inject a Hilt singleton directly, so this
 * thin ViewModel re-exposes the channels they need:
 *
 *  - [pendingTab] — the OUTER scaffold switches the bottom tab to it (when the target's role
 *    matches its own) then [consumePendingTab].
 *  - [pendingThreadId] / [pendingBookingId] / [pendingRequestId] — a tab CONTAINER observes its
 *    one channel to FORCE-ROOT its nested nav (see the deep-stack fix in the scaffolds). The
 *    container does NOT consume the id — it only pops its inner stack to the tab root so the
 *    root screen's existing LaunchedEffect consumer runs; that consumer clears the channel.
 */
@HiltViewModel
class TabDeepLinkViewModel @Inject constructor(
    private val deepLink: DeepLinkRouter,
) : ViewModel() {
    val pendingTab: StateFlow<DeepLinkTab?> = deepLink.pendingTab
    val pendingThreadId: StateFlow<String?> = deepLink.pendingThreadId
    val pendingBookingId: StateFlow<String?> = deepLink.pendingBookingId
    val pendingRequestId: StateFlow<String?> = deepLink.pendingRequestId
    fun consumePendingTab() = deepLink.consumePendingTab()
}
