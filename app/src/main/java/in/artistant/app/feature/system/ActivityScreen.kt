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
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.GroupLabel
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
    /**
     * Activity is PUSHED on both graphs, and the tab bar is hidden while it is
     * up — so the header's back circle is the only way out that is not a system
     * gesture. It drew the tab-root `ScreenHeader` before, which has no back
     * control at all: on a gesture-navigation device with no visible system
     * back, the screen was a dead end.
     */
    onBack: () -> Unit,
    onOpenBooking: (bookingId: String) -> Unit,
    onOpenThread: (threadId: String) -> Unit,
    onOpenMessages: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenGigRequest: ((requestId: String) -> Unit)? = null,
    onOpenGigs: (() -> Unit)? = null,
    onOpenHome: (() -> Unit)? = null,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    // A row is tappable only when this graph can actually serve where its
    // notification would have gone — see [openActivity].
    val openFor: (ActivityEntry) -> (() -> Unit)? = { entry ->
        openActivity(
            entry = entry,
            role = role,
            onOpenBooking = onOpenBooking,
            onOpenThread = onOpenThread,
            onOpenMessages = onOpenMessages,
            onOpenGigRequest = onOpenGigRequest,
            onOpenGigs = onOpenGigs,
            onOpenHome = onOpenHome,
        )
    }

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
            BackHeader(
                title = "Activity",
                // Left-aligned with its subtitle under it, the way every other
                // pushed screen that also states a fact about itself is drawn
                // (60, 127, 34) — see `BackHeader`'s own note on `centered`.
                subtitle = "Notifications received on this device",
                onBack = onBack,
                centered = false,
                // Only when there is something to mark. After the screen has
                // been opened that means "something landed while you were
                // reading" — see [ActivityUiState.hasUnread].
                trailing = if (state.hasUnread) {
                    {
                        Text(
                            text = "Mark all read",
                            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accentInk,
                            modifier = Modifier
                                .clickable(role = Role.Button, onClick = viewModel::markAllRead)
                                .padding(dimens.space.xs),
                        )
                    }
                } else {
                    null
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
                        onClick = openFor(entry),
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
                        onClick = openFor(entry),
                    )
                }
            }
        }
    }
}

/**
 * Where one row goes.
 *
 * The same six places [PushPayloadRouter] routes a notification TAP to, named as
 * destinations rather than as router actions so the screen can ask two different
 * questions of one answer: *where does this row go*, and *can this graph take it
 * there*.
 */
internal sealed interface ActivityDestination {
    data class Booking(val id: String) : ActivityDestination
    data class Thread(val id: String) : ActivityDestination

    /** The inbox — a chat push whose thread id never arrived. */
    data object Messages : ActivityDestination
    data class GigRequest(val id: String) : ActivityDestination
    data object Gigs : ActivityDestination

    /** The artist's studio, which is also where the gig-request LIST lives. */
    data object Home : ActivityDestination
}

/**
 * A row's destination, decided by the SAME function the notification tap uses.
 *
 * A row that routed by its own rules would eventually disagree with the
 * notification it is a record of — the user taps the row expecting the place the
 * banner would have taken them, and gets somewhere else.
 *
 * The two null-id fallbacks are [TabRouter.apply]'s own, not new policy: a
 * `message` with no thread id lands on the inbox there, and a `gig_request` with
 * no request id arms the artist's Home tab. Reproducing them here is what stops
 * a valid push rendering as a tappable row that does nothing.
 *
 * Null means the payload has no destination at all — a server event newer than
 * this build. Those rows are drawn without an affordance rather than being made
 * to look tappable.
 */
internal fun activityDestination(entry: ActivityEntry, role: AppRole): ActivityDestination? =
    when (val action = PushPayloadRouter.route(
        event = entry.event,
        bookingId = entry.bookingId,
        threadId = entry.threadId,
        requestId = entry.requestId,
        role = role,
    )) {
        is PushDeepLinkAction.OpenBookingDetail -> ActivityDestination.Booking(action.bookingId)
        is PushDeepLinkAction.OpenThread ->
            action.threadId?.let(ActivityDestination::Thread) ?: ActivityDestination.Messages
        is PushDeepLinkAction.OpenGigRequest ->
            action.requestId?.let(ActivityDestination::GigRequest) ?: ActivityDestination.Home
        PushDeepLinkAction.ArtistGigs -> ActivityDestination.Gigs
        PushDeepLinkAction.ArtistHome -> ActivityDestination.Home
        PushDeepLinkAction.Ignore -> null
    }

/**
 * The tap handler for one row, or null when this graph cannot serve it.
 *
 * The artist-side destinations arrive as nullable lambdas because the client
 * graph has no gig requests, no Gigs tab and no studio — and a row whose
 * destination this graph cannot reach must render as a record, not as a control
 * that swallows a tap.
 */
private fun openActivity(
    entry: ActivityEntry,
    role: AppRole,
    onOpenBooking: (String) -> Unit,
    onOpenThread: (String) -> Unit,
    onOpenMessages: () -> Unit,
    onOpenGigRequest: ((String) -> Unit)?,
    onOpenGigs: (() -> Unit)?,
    onOpenHome: (() -> Unit)?,
): (() -> Unit)? = when (val destination = activityDestination(entry, role)) {
    null -> null
    is ActivityDestination.Booking -> ({ onOpenBooking(destination.id) })
    is ActivityDestination.Thread -> ({ onOpenThread(destination.id) })
    ActivityDestination.Messages -> onOpenMessages
    // A graph that cannot open one request can still open the list it is in.
    is ActivityDestination.GigRequest ->
        onOpenGigRequest?.let { open -> ({ open(destination.id) }) } ?: onOpenHome
    ActivityDestination.Gigs -> onOpenGigs
    ActivityDestination.Home -> onOpenHome
}

/**
 * One row. [onClick] is null when the row has nowhere to go — it then draws
 * identically but carries no ripple, no button semantics and no tap target, so a
 * record that cannot be opened does not advertise itself as one that can.
 */
@Composable
private fun ActivityRow(
    entry: ActivityEntry,
    accented: Boolean,
    nowMs: Long,
    onClick: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick == null) Modifier
                else Modifier.clickable(role = Role.Button, onClick = onClick),
            )
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
