package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.designsystem.component.HRule
import androidx.compose.ui.unit.sp
import `in`.artistant.app.designsystem.component.Skeleton
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.booking.InitialAvatar
import java.time.Duration
import java.time.Instant

/**
 * Shared thread list (port of iOS `MessagesView`), both roles. A row resolves the
 * role-aware COUNTERPART (artist viewer → client, client viewer → artist), a BOOKING
 * pill for booking-linked threads, a mono timeAgo, a 2-line REDACTED preview, and an
 * unread dot. Tapping a row opens `Chat(threadId)` via [onOpenThread]. A parked push
 * thread id (DeepLinkRouter) deep-links straight into the chat.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    role: AppRole,
    onOpenThread: (String) -> Unit,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val threads by viewModel.threads.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pending by viewModel.pendingThreadId.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    // Push deep-link consumer — clear BEFORE navigating so re-composition can't
    // re-push the same chat (iOS clears pendingThreadId before path.append).
    LaunchedEffect(pending) {
        pending?.let {
            viewModel.consumePendingThread()
            onOpenThread(it)
        }
    }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        // Screen title — the tab is a root, so it carries its own header (Android has
        // no iOS large-title nav; a plain editorial heading stands in).
        Text(
            "Messages",
            style = AppTheme.type.displaySmall,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = space.xl, vertical = space.lg),
        )

        error?.let { msg -> ErrorBanner(msg, onRetry = { viewModel.refresh() }) }

        when {
            loading -> ThreadSkeleton()
            threads.isEmpty() -> EmptyState()
            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(threads, key = { it.id }) { t ->
                        ThreadRow(
                            thread = t,
                            name = viewModel.counterpartName(t, role),
                            preview = viewModel.preview(t),
                            onClick = { onOpenThread(t.id) },
                        )
                        HRule(Modifier.padding(start = 80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(
    thread: Thread,
    name: String,
    preview: String,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = space.xl, vertical = space.lg),
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        InitialAvatar(name, size = 44)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                // First name only (iOS row shows the leading token).
                Text(
                    name.substringBefore(' '),
                    style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
                if (thread.bookingId != null) {
                    // Chrome-free kerned micro-label (iOS MessagesView) — accent is
                    // signal, not a filled chip; house rule = no card chrome.
                    Text(
                        "BOOKING",
                        style = AppTheme.type.caption.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                        ),
                        color = colors.accent,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    timeAgo(thread.messages.lastOrNull()?.sentAt),
                    style = AppTheme.type.monoSmall,
                    color = colors.ink3,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Text(
                    preview,
                    style = AppTheme.type.footnote,
                    color = if (thread.unread > 0) colors.ink else colors.ink3,
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                )
                if (thread.unread > 0) {
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(colors.brand),
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(msg: String, onRetry: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = space.xl, vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Text(
            msg,
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.hot,
            modifier = Modifier.weight(1f),
        )
        Text(
            "Retry",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.brand,
            modifier = Modifier.clip(CircleShape).clickable(onClick = onRetry).padding(space.xs),
        )
    }
}

@Composable
private fun EmptyState() {
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().padding(space.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No conversations yet", style = AppTheme.type.displaySmall, color = AppTheme.colors.ink)
        Spacer(Modifier.height(space.sm))
        Text(
            "Once you book or message an artist, threads show up here.",
            style = AppTheme.type.footnote,
            color = AppTheme.colors.ink3,
        )
    }
}

@Composable
private fun ThreadSkeleton() {
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxSize()) {
        repeat(4) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = space.xl, vertical = space.lg),
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                Skeleton(Modifier.size(44.dp), cornerRadius = 24.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    Skeleton(Modifier.fillMaxWidth(0.4f).height(12.dp))
                    Skeleton(Modifier.fillMaxWidth(0.8f).height(10.dp))
                }
            }
            HRule(Modifier.padding(start = 80.dp))
        }
    }
}

/** Compact relative time (now / Xm / Xh / Xd) — iOS `timeAgo`. */
private fun timeAgo(instant: Instant?): String {
    if (instant == null) return ""
    val mins = Duration.between(instant, Instant.now()).toMinutes()
    return when {
        mins < 1 -> "now"
        mins < 60 -> "${mins}m"
        mins < 60 * 24 -> "${mins / 60}h"
        else -> "${mins / (60 * 24)}d"
    }
}
