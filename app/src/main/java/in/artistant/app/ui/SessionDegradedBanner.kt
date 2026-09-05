package `in`.artistant.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * "The session is up but nothing can be saved" — said once, in one place.
 *
 * Drawn over BOTH tab shells while
 * [in.artistant.app.platform.auth.SessionManager.sessionDegraded] is true, and on the
 * [RootGate.Reconnecting] splash, which is the same fact met before the app ever routed.
 * Sharing the composable is what keeps the two from drifting into two different accounts of
 * one state.
 *
 * [BannerTone.Attention] rather than [BannerTone.Failure]: nothing has failed yet. The refresh
 * is still being retried, and the writes the user has already made are on the server — it is
 * the NEXT one that will not land. Failure red for a condition that usually clears itself in
 * seconds would cry wolf on the tone the app keeps for lost work.
 *
 * It is a banner and not a toast for the reason every banner in this app is one: this is a
 * state, not an event. It stands until the fact changes.
 *
 * @param onSignInAgain non-null only on the reconnect gate. A routed user needs no action —
 *   the app is theirs to use read-only and the session usually comes back — but a cold start
 *   that never routed has nothing else on screen, so it gets the way out.
 */
@Composable
fun SessionDegradedBanner(
    modifier: Modifier = Modifier,
    onSignInAgain: (() -> Unit)? = null,
) {
    Banner(
        title = SESSION_DEGRADED_TITLE,
        detail = SESSION_DEGRADED_DETAIL,
        tone = BannerTone.Attention,
        actionLabel = onSignInAgain?.let { SESSION_DEGRADED_ACTION },
        onAction = onSignInAgain,
        modifier = modifier,
    )
}

/**
 * The tab shells' half of it: the same banner as a `Scaffold` **topBar**, or nothing.
 *
 * A topBar rather than an overlay because a banner that covers the first rows of a list is a
 * banner that hides the thing it is warning you about. The `Scaffold` measures this slot and
 * pushes every destination's content below it, so the tab graph keeps its full pane and the
 * bar keeps the bottom edge.
 *
 * It resolves [RootViewModel] itself so a shell adopts it in one line. Both scaffolds sit
 * above any `NavHost`, so `LocalViewModelStoreOwner` here is still the activity and this is
 * the same instance the gate is driven by — not a second one with a second session collector.
 *
 * No action on this variant: a routed user still has the app in front of them, reads
 * everything (Postgrest falls back to the anon key, which the public tables allow), and gets
 * their session back when the network returns. Offering "sign in again" here would push them
 * to throw away a session that is about to heal.
 */
@Composable
fun SessionDegradedTopBar() {
    val viewModel: RootViewModel = hiltViewModel()
    val degraded by viewModel.sessionDegraded.collectAsStateWithLifecycle()
    if (!degraded) return
    SessionDegradedBanner(
        modifier = Modifier
            .statusBarsPadding()
            .padding(
                horizontal = AppTheme.dimens.component.gutter,
                vertical = AppTheme.dimens.space.sm,
            ),
    )
}

/**
 * The copy. Present tense and specific about the consequence, because "connection lost" tells
 * someone nothing about the edit they are part-way through: the one thing they need to know
 * is that typing more of it is wasted until the banner goes.
 */
const val SESSION_DEGRADED_TITLE = "Reconnecting…"
const val SESSION_DEGRADED_DETAIL = "Your changes will not save until we're back."
const val SESSION_DEGRADED_ACTION = "Sign in again"
