package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.Chip
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.ScreenHeader
import `in`.artistant.app.designsystem.component.SearchBar
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.component.SkeletonCircle
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * The inbox (designs 19 and 110).
 *
 * **Threads carry deal state.** The second line of a row is not "the last thing
 * somebody typed" when there is something better to say: a live quote replaces it
 * with the amount and when it lapses, so the list reads as a pipeline rather than
 * as a contact list. That is the whole of screen 19.
 *
 * **Support survives the empty.** The Artistant Support row sits above the list
 * in every segment and in every state — loading, loaded, failed, empty. It is the
 * one surface here that does not depend on the thread list, which makes it most
 * valuable exactly when the thread list is what is broken (screen 110).
 */
@Composable
fun MessagesScreen(
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBookingClick: (String) -> Unit = {},
    /** The archive (design 60). */
    onOpenArchive: () -> Unit = {},
    /** The scripted assistant (design 34). */
    onOpenSupport: () -> Unit = {},
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    // Chat is a sibling destination, so opening one leaves this ViewModel alive
    // with the payload it loaded BEFORE the conversation was read: come back and
    // the row still carries the unread rail, the old preview, and the old place
    // in `last_message_at` order. Nothing else can correct it — the flags store
    // only clears a device-local "mark as unread", never the server's count — so
    // returning to the inbox re-reads it. Every resume is delivered; the
    // ViewModel decides which ones to act on, because it is the half of this
    // screen that survives the push — see MessagesViewModel.onResumed.
    ResumeEffect(onResumed = viewModel::onResumed)

    Column(modifier.fillMaxSize().background(colors.page)) {
        ScreenHeader(
            title = "Messages",
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
            trailing = {
                // The archive has to be reachable from the inbox or archiving is
                // a one-way door. The dot says there is something in there
                // without spending a number on chrome.
                IconCircle(
                    icon = Icons.Filled.Archive,
                    contentDescription = if (state.archivedThreads.isNotEmpty()) {
                        "Archived, ${state.archivedThreads.size} conversations"
                    } else {
                        "Archived"
                    },
                    onClick = onOpenArchive,
                    size = dimens.component.iconCircleSm,
                    dot = state.archivedThreads.isNotEmpty(),
                    modifier = Modifier.semantics { testTag = "messages.archiveEntry" },
                )
            },
        )
        Spacer(Modifier.height(dimens.space.md))
        SearchBar(
            value = state.query,
            onValueChange = viewModel::setQuery,
            hint = "Search conversations",
            onClear = { viewModel.setQuery("") },
            modifier = Modifier
                .padding(horizontal = dimens.component.gutter)
                .semantics { testTag = "messages.search" },
        )
        Spacer(Modifier.height(dimens.space.md))

        // Chips only make sense once the first load has settled — on the very
        // first paint they would be four chips over a skeleton. A FAILED load
        // still counts as settled, so the segments stay usable when the thread
        // list is the thing that's broken.
        if (state.hasLoaded || state.error != null) {
            FilterChips(
                filter = state.filter,
                counts = state.counts,
                onFilter = viewModel::setFilter,
            )
            Spacer(Modifier.height(dimens.space.md))
        }

        SupportRow(onOpen = onOpenSupport)

        // A refresh that failed while rows are already on screen is a strip, not
        // an empty state — the stale list is still the best thing to show.
        //
        // Gated on `activeThreads`, not `threads`: archived conversations are on
        // another screen, so an inbox whose every thread is archived has nothing
        // on screen for the strip to sit over, and would print "couldn't refresh"
        // directly above "you don't have any messages".
        if (state.error != null && state.activeThreads.isNotEmpty()) {
            Banner(
                title = "Couldn't refresh messages",
                detail = state.error,
                tone = BannerTone.Failure,
                actionLabel = "Retry",
                onAction = viewModel::refresh,
                modifier = Modifier
                    .padding(horizontal = dimens.component.gutter)
                    .padding(top = dimens.space.md),
            )
        }

        PullToRefreshBox(
            // Never two progress signals at once: the skeleton owns the first
            // load, the pull indicator owns every refresh after it.
            isRefreshing = state.isLoading && state.hasLoaded,
            onRefresh = viewModel::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                state.isLoading && !state.hasLoaded -> InboxSkeleton()

                // Same seam as the strip above, from the other side: with nothing
                // visible to show, a failed load owns the whole area rather than
                // ceding it to the "no messages yet" copy.
                state.error != null && state.activeThreads.isEmpty() -> EmptyState(
                    title = "Couldn't load messages",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    icon = Icons.Outlined.ChatBubbleOutline,
                )

                // Distinguish "no conversations at all" from "none in this
                // segment": the first is about the app being new to you, the
                // second is about the chip you just tapped.
                state.activeThreads.isEmpty() -> EmptyState(
                    title = "You don't have any messages",
                    body = "Message an artist from their profile and the thread lands here. " +
                        "Support is always available above.",
                    actionLabel = "Refresh",
                    onAction = viewModel::refresh,
                    icon = Icons.Outlined.ChatBubbleOutline,
                    modifier = Modifier.semantics { testTag = "messages.empty" },
                )

                state.visibleThreads.isEmpty() -> EmptyState(
                    title = "No conversations here",
                    body = if (state.query.isBlank()) {
                        "Nothing in ${state.filter.label.lowercase()} yet. Try another filter."
                    } else {
                        "No conversations match “${state.query}”."
                    },
                    icon = Icons.Outlined.ChatBubbleOutline,
                )

                else -> ThreadList(
                    items = state.visibleThreads,
                    onOpen = onThreadClick,
                    onOpenBooking = onBookingClick,
                    onArchive = viewModel::toggleArchived,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header furniture
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Segment chips with live counts (designs 19 and 110).
 *
 * Only the selected chip is filled. Lime is the app's single "this is the thing"
 * signal, so spending it on four chips at once would spend it on nothing; one
 * filled chip is what makes the current segment legible at a glance.
 */
@Composable
private fun FilterChips(
    filter: MessagesFilter,
    counts: Map<MessagesFilter, Int>,
    onFilter: (MessagesFilter) -> Unit,
) {
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.component.gutter),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        MessagesFilter.entries.forEach { entry ->
            val count = counts[entry] ?: 0
            // Support is synthetic — it holds no threads, so a "0" beside it
            // would read as an empty inbox rather than as an assistant.
            val label = if (count > 0 && entry != MessagesFilter.Support) {
                "${entry.label} · $count"
            } else {
                entry.label
            }
            Chip(
                label = label,
                selected = filter == entry,
                onClick = { onFilter(entry) },
                modifier = Modifier.semantics { testTag = "messages.filter.${entry.name}" },
            )
        }
    }
}

/**
 * The permanent Artistant Support row (design 110).
 *
 * Above the list rather than inside it, and present in every state — including
 * the two where the thread list has nothing to say. It is the only route to a
 * human on this screen, so it cannot be a row that a failed load takes away.
 */
@Composable
private fun SupportRow(onOpen: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.component.gutter)
            .clip(RoundedCornerShape(dimens.radii.lg))
            .background(colors.surface3)
            .clickable(onClick = onOpen)
            .padding(dimens.space.md)
            .semantics(mergeDescendants = true) { testTag = "messages.supportEntry" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The one dark disc on a light screen, carrying the lime "A". The app
        // talking about itself is the only thing that gets to wear the mark.
        Box(
            Modifier
                .size(dimens.size.avatarMd)
                .clip(CircleShape)
                .background(colors.darkest),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", style = AppTheme.type.monoNumber, color = colors.accent)
        }
        Column(Modifier.weight(1f)) {
            Text(
                "Artistant Support",
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
            Text(
                "Booking help, safety and feedback",
                style = AppTheme.type.caption,
                color = colors.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(dimens.size.iconLg),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThreadList(
    items: List<ThreadListItem>,
    onOpen: (String) -> Unit,
    onOpenBooking: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    // Relative stamps go stale on a tab that sits open for an hour. One shared
    // minute tick keeps every row honest without each row owning a timer.
    val now = rememberMinuteClock()
    LazyColumn(
        Modifier.fillMaxSize().padding(top = AppTheme.dimens.space.sm),
    ) {
        items(items, key = { it.thread.id }) { item ->
            SwipeableThreadRow(
                item = item,
                nowMs = now,
                onOpen = onOpen,
                onOpenBooking = onOpenBooking,
                onArchive = onArchive,
            )
        }
    }
}

/**
 * Swipe a row to archive it.
 *
 * `confirmValueChange` fires the action and then returns false, so the row
 * performs it and springs back rather than dismissing. Deliberate: archiving
 * deletes nothing, and a row that vanishes under your finger implies it did. The
 * list re-projects a moment later and the row leaves on its own — because it is
 * genuinely archived now, not because the gesture animated it away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableThreadRow(
    item: ThreadListItem,
    nowMs: Long,
    onOpen: (String) -> Unit,
    onOpenBooking: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val id = item.thread.id
    val swipeState = rememberSwipeToDismissBoxState()

    // Reacting to the SETTLED value rather than vetoing inside
    // `confirmValueChange`: that callback is deprecated, and a state change is
    // the honest trigger — the gesture has finished by the time it fires.
    androidx.compose.runtime.LaunchedEffect(swipeState.currentValue) {
        if (swipeState.currentValue != SwipeToDismissBoxValue.Settled) {
            onArchive(id)
            swipeState.reset()
        }
    }

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(colors.surface2)
                    .padding(horizontal = dimens.component.gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Filled.Archive, contentDescription = null, tint = colors.ink2)
                Icon(Icons.Filled.Archive, contentDescription = null, tint = colors.ink2)
            }
        },
    ) {
        ThreadRow(
            item = item,
            nowMs = nowMs,
            onOpen = { onOpen(id) },
            onOpenBooking = onOpenBooking,
        )
    }
}

/**
 * One conversation (design 19).
 *
 * Avatar · name · stamp on the first line; the DEAL on the second when there is
 * one, the last message when there isn't; an accent count badge when it is
 * unread. Hairline under each row, inset to the text so the rules start under the
 * words rather than under the artwork.
 */
@Composable
private fun ThreadRow(
    item: ThreadListItem,
    nowMs: Long,
    onOpen: () -> Unit,
    onOpenBooking: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val context = item.context
    val preview = item.thread.lastPreview.ifBlank { "No messages yet" }
    val unreadCount = item.thread.unreadCount

    Column(Modifier.fillMaxWidth().background(colors.page)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interaction)
                .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
                .padding(horizontal = dimens.component.gutter, vertical = dimens.space.md)
                // Merge only the tappable bar, never the whole Column: the
                // "Review request" accelerator below is a separate control and
                // merging would swallow it into the row's single a11y node.
                .semantics(mergeDescendants = true) {
                    // The unread badge is a number in a circle, which a screen
                    // reader would read as a bare digit; unread-ness has to be
                    // spelled out here or it is carried by colour alone.
                    contentDescription = buildString {
                        append(item.counterpartName)
                        if (item.unread) append(", unread")
                        append(". ")
                        append(item.quote?.let { "Quote ${formatInr(it.amountInr)}. " }.orEmpty())
                        append(context.summary)
                        append(". ")
                        append(preview)
                    }
                    testTag = "messages.threadRow"
                },
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = item.counterpartName, size = dimens.size.avatarMd)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.counterpartName,
                        style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(dimens.space.sm))
                    Text(
                        InboxProjection.timeAgo(item.thread.lastMessageAtEpochMs, nowMs),
                        style = AppTheme.type.caption,
                        color = colors.ink4,
                    )
                }
                Spacer(Modifier.height(dimens.space.xs))
                // Deal state beats chatter. A quote says what is on the table and
                // until when; without one the row falls back to the last message.
                if (item.quote != null) {
                    QuoteLine(quote = item.quote, nowMs = nowMs)
                } else {
                    Text(
                        preview,
                        style = AppTheme.type.subtitle,
                        // Unread lifts the preview to full ink; read rows step
                        // down so an unread row wins the column at a glance.
                        color = if (item.unread) colors.ink2 else colors.ink4,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (unreadCount > 0) {
                Box(
                    Modifier
                        .size(dimens.size.iconLg)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        unreadCount.toString(),
                        style = AppTheme.type.badge,
                        color = colors.onAccent,
                    )
                }
            }
        }

        // The accelerator into the funnel, not a second place that mutates the
        // booking: it routes to the detail screen where accept/decline lives.
        if (context.awaitingViewer && context.bookingId != null) {
            Text(
                "Review request",
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
                color = colors.accentInk,
                modifier = Modifier
                    .padding(
                        start = dimens.component.gutter + dimens.size.avatarMd + dimens.space.md,
                        bottom = dimens.space.md,
                    )
                    .clip(CircleShape)
                    .clickable { onOpenBooking(context.bookingId) }
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.sm)
                    .semantics { testTag = "messages.reviewRequest" },
            )
        }
        HRule(
            modifier = Modifier.padding(
                start = dimens.component.gutter + dimens.size.avatarMd + dimens.space.md,
                end = dimens.component.gutter,
            ),
        )
    }
}

/**
 * "QUOTE ₹48,000 · holds till Fri" (design 19).
 *
 * The amount is an accent-tinted pill because it is the fact the row exists to
 * carry; the deadline beside it is plain meta. A lapsed offer says so instead —
 * "holds till Fri" under a quote that expired on Thursday is the one thing this
 * line must never do.
 */
@Composable
private fun QuoteLine(quote: ThreadQuote, nowMs: Long) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        Text(
            "QUOTE ${formatInr(quote.amountInr)}",
            style = AppTheme.type.badge,
            color = colors.accentDeep,
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.radii.sm))
                .background(colors.brandSoft)
                .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs),
        )
        quoteHoldLabel(quote, nowMs)?.let {
            Text(
                it,
                style = AppTheme.type.caption,
                color = if (quote.expired) colors.warm else colors.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * "holds till Fri" / "offer lapsed" / "agreed", or null when the row carried no
 * deadline to report.
 *
 * The weekday is the platform's, so it follows the reader's locale.
 */
@Composable
private fun quoteHoldLabel(quote: ThreadQuote, nowMs: Long): String? = when {
    quote.frozen -> "agreed"
    quote.expired -> "offer lapsed"
    quote.expiresAtEpochMs == null -> null
    else -> {
        val locale = androidx.compose.ui.platform.LocalConfiguration.current
            .locales.get(0) ?: java.util.Locale.getDefault()
        val weekday = remember(quote.expiresAtEpochMs, locale) {
            java.text.SimpleDateFormat("EEE", locale)
                .format(java.util.Date(quote.expiresAtEpochMs))
        }
        // Inside a day the weekday stops being useful — "holds till Fri" on
        // Friday morning says nothing about the hours left.
        if (quote.expiresAtEpochMs - nowMs < DAY_MS) "holds till today" else "holds till $weekday"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Grey rows while the first page lands.
 *
 * A skeleton rather than a spinner because the inbox has a shape worth
 * promising: it tells the user rows are coming and roughly how they will sit,
 * which a centred spinner does not. Its geometry matches the real row's, so the
 * fill-in doesn't reflow what the eye already parsed.
 */
@Composable
private fun InboxSkeleton() {
    val dimens = AppTheme.dimens
    Column(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        repeat(SKELETON_ROWS) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.component.gutter, vertical = dimens.space.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SkeletonCircle(size = dimens.size.avatarMd)
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
                ) {
                    SkeletonBlock(
                        Modifier
                            .width(dimens.component.skeletonSectionWidth)
                            .height(dimens.component.skeletonLineHeight),
                    )
                    SkeletonBlock(
                        Modifier
                            .width(dimens.component.skeletonTitleWidth)
                            .height(dimens.component.skeletonLineHeight),
                    )
                }
            }
            HRule(
                modifier = Modifier.padding(
                    start = dimens.component.gutter + dimens.size.avatarMd + dimens.space.md,
                    end = dimens.component.gutter,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A clock that re-reads once a minute.
 *
 * The row stamps are relative ("12m"), so a tab left open would otherwise keep
 * insisting a two-hour-old message arrived twelve minutes ago. One shared tick
 * for the whole list; the coroutine is scoped to the composition, so it stops
 * the moment the inbox leaves the screen.
 */
@Composable
internal fun rememberMinuteClock(): Long =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }.value

private const val SKELETON_ROWS = 5
private const val CLOCK_TICK_MS = 60_000L
private const val DAY_MS = 86_400_000L
