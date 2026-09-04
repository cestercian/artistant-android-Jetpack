package `in`.artistant.app.feature.system

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.GroupLabel
import `in`.artistant.app.designsystem.component.ScreenHeader
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.navigation.PushDeepLinkAction
import `in`.artistant.app.navigation.PushPayloadRouter

/**
 * Screen 123 — Activity.
 *
 * The design's note is the requirement: *push needs a home — otherwise a missed
 * alert is gone forever*. What that turns into on Android is a local log written
 * on RECEIPT (not on tap), because the notification the user never tapped is
 * exactly the one they come here looking for.
 *
 * **It says what it is.** The subtitle — "Notifications received on this device"
 * — is not decoration. The shared schema has no notifications table (it has
 * `device_tokens`, which is where to send, and push triggers, which are when),
 * so this list cannot include anything that arrived on the user's other phone or
 * before they reinstalled. An "Activity" screen that silently omitted those
 * would be worse than no screen at all, so the screen states its own boundary
 * and the log is honest inside it.
 *
 * Rows route through [PushPayloadRouter] — the same function the notification
 * tap uses — so a row and its notification can never disagree about where they
 * go.
 */
@Composable
fun ActivityScreen(
    role: AppRole,
    onOpenBooking: (bookingId: String) -> Unit,
    onOpenThread: (threadId: String) -> Unit,
    onOpenGigRequest: (requestId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    // Read once per composition rather than held in state: it is a fact about
    // the clock, and both the group split and the stamps must use the same one.
    val nowMs = remember(state.all) { System.currentTimeMillis() }
    val todayStart = remember(state.all) { startOfToday() }
    val (today, earlier) = groupActivity(state.visible, todayStart)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        Column(Modifier.padding(horizontal = dimens.component.gutter)) {
            ScreenHeader(
                title = "Activity",
                subtitle = "Notifications received on this device",
                trailing = {
                    if (state.hasUnread) {
                        Text(
                            text = "Mark all read",
                            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentInk,
                            modifier = Modifier
                                .clickable(role = Role.Button, onClick = viewModel::markAllRead)
                                .padding(dimens.space.xs),
                        )
                    }
                },
            )
            Row(
                Modifier.padding(top = dimens.space.lg),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                ActivityFilter.entries.forEach { option ->
                    Chip(
                        label = option.label,
                        selected = option == state.filter,
                        onClick = { viewModel.select(option) },
                    )
                }
            }
        }

        if (state.visible.isEmpty()) {
            EmptyState(
                // Two different empty states, because they mean different
                // things: an untouched log is a new device, a filtered one is a
                // chip the user can undo.
                title = if (state.all.isEmpty()) "Nothing yet" else "Nothing in ${state.filter.label}",
                body = if (state.all.isEmpty()) {
                    "Notifications land here as they arrive, so a missed one is still " +
                        "here later."
                } else {
                    "This device has received nothing in this category."
                },
                icon = Icons.Outlined.Notifications,
                actionLabel = if (state.all.isEmpty()) null else "Show all",
                onAction = if (state.all.isEmpty()) null else { { viewModel.select(ActivityFilter.All) } },
                modifier = Modifier.padding(top = dimens.space.xxl),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = dimens.component.gutter,
                end = dimens.component.gutter,
                top = dimens.space.md,
                bottom = dimens.chrome.contentTailroom,
            ),
        ) {
            if (today.isNotEmpty()) {
                item(key = "today-label") {
                    GroupLabel("Today", modifier = Modifier.padding(vertical = dimens.space.sm))
                }
                items(today, key = { it.id }) { entry ->
                    ActivityRow(
                        entry = entry,
                        accented = entry.id == state.accentedId,
                        nowMs = nowMs,
                        onClick = {
                            openActivity(entry, role, onOpenBooking, onOpenThread, onOpenGigRequest)
                        },
                    )
                }
            }
            if (earlier.isNotEmpty()) {
                item(key = "earlier-label") {
                    GroupLabel(
                        "Earlier",
                        modifier = Modifier.padding(
                            top = dimens.space.lg,
                            bottom = dimens.space.sm,
                        ),
                    )
                }
                items(earlier, key = { it.id }) { entry ->
                    ActivityRow(
                        entry = entry,
                        accented = entry.id == state.accentedId,
                        nowMs = nowMs,
                        onClick = {
                            openActivity(entry, role, onOpenBooking, onOpenThread, onOpenGigRequest)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Where a row goes, decided by the SAME function the notification tap uses.
 *
 * A row that routed by its own rules would eventually disagree with the
 * notification it is a record of — the user taps the row expecting the place the
 * banner would have taken them, and gets somewhere else. Actions this graph
 * cannot serve (an artist-tab jump from the client's Activity screen) simply do
 * nothing rather than navigating somewhere arbitrary.
 */
private fun openActivity(
    entry: ActivityEntry,
    role: AppRole,
    onOpenBooking: (String) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenGigRequest: (String) -> Unit,
) {
    val action = PushPayloadRouter.route(
        event = entry.event,
        bookingId = entry.bookingId,
        threadId = entry.threadId,
        requestId = entry.requestId,
        role = role,
    )
    when (action) {
        is PushDeepLinkAction.OpenBookingDetail -> onOpenBooking(action.bookingId)
        is PushDeepLinkAction.OpenThread -> action.threadId?.let(onOpenThread)
        is PushDeepLinkAction.OpenGigRequest -> action.requestId?.let(onOpenGigRequest)
        // Tab-only actions and unroutable payloads: the row is a record, not a
        // promise, and moving the user to a tab they are already looking at is
        // indistinguishable from the tap doing nothing.
        PushDeepLinkAction.ArtistGigs,
        PushDeepLinkAction.ArtistHome,
        PushDeepLinkAction.Ignore,
        -> Unit
    }
}

@Composable
private fun ActivityRow(
    entry: ActivityEntry,
    accented: Boolean,
    nowMs: Long,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .hairlineBottom()
            .padding(vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = listOf(
                    entry.title,
                    entry.body,
                    relativeStamp(entry.receivedAtMs, nowMs),
                    if (entry.read) "" else "unread",
                ).filter { it.isNotBlank() }.joinToString(". ")
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Box(
            Modifier
                .size(dimens.size.avatarSm)
                .clip(CircleShape)
                // One accent per screen: the newest unread row gets the lime
                // disc, everything else the quiet one.
                .background(if (accented) colors.accent else colors.hairline),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = activityIcon(entry.event),
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.title,
                // Weight IS the unread state here, alongside the dot. The design
                // sets an unread row at 700 and a read one at 600, which survives
                // a glance where a 8dp dot on the far edge does not.
                style = AppTheme.type.rowTitle.copy(
                    fontWeight = if (entry.read) FontWeight.SemiBold else FontWeight.Bold,
                ),
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.body.isNotBlank()) {
                Text(
                    text = entry.body,
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                    color = colors.ink4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
        ) {
            Text(
                text = relativeStamp(entry.receivedAtMs, nowMs),
                style = AppTheme.type.monoPill,
                color = colors.ink3,
                maxLines = 1,
            )
            if (!entry.read) {
                Box(
                    Modifier
                        .size(dimens.dashboard.bannerDot)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
            }
        }
    }
}

/**
 * The glyph for one event.
 *
 * By EVENT rather than by [ActivityCategory], because the design draws four
 * different marks inside the booking family alone — a tick for a confirmation, a
 * calendar for a reminder, a shield for a moderation outcome. The category is
 * what the chips filter on; this is what the row looks like.
 */
private fun activityIcon(event: String?): ImageVector = when (event?.trim()) {
    "booking_confirmed_client", "booking_confirmed_artist" -> Icons.Filled.Check
    "booking_reminder_24h" -> Icons.Filled.CalendarMonth
    "gig_request", "booking_request" -> Icons.AutoMirrored.Filled.ArrowForward
    "booking_review_request" -> Icons.Outlined.StarBorder
    "message" -> Icons.AutoMirrored.Outlined.Chat
    // A server event this build has never heard of still gets a row and a mark.
    else -> Icons.Outlined.Shield
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun ActivityRowPreview() {
    ArtistantTheme {
        Column(Modifier.padding(AppTheme.dimens.component.gutter)) {
            ActivityRow(
                entry = ActivityEntry(
                    id = "1",
                    event = "booking_confirmed_client",
                    title = "Saanjh accepted your request",
                    body = "Fri 25 Oct is held",
                    receivedAtMs = 0L,
                ),
                accented = true,
                nowMs = 120_000L,
                onClick = {},
            )
            Spacer(Modifier.height(AppTheme.dimens.space.sm))
            ActivityRow(
                entry = ActivityEntry(
                    id = "2",
                    event = "booking_review_request",
                    title = "How was the set?",
                    body = "Leave a review",
                    receivedAtMs = 0L,
                    read = true,
                ),
                accented = false,
                nowMs = 172_800_000L,
                onClick = {},
            )
        }
    }
}
