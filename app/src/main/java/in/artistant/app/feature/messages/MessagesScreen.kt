package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.avatarGradient
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.InlineBanner
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.bookingStatusTone
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * The inbox.
 *
 * The rebuild's one idea: a row should say what the conversation is *about*. A
 * name and a snippet describe a contact; a name, a gig, a state and a date
 * describe a piece of work in progress, which is what these actually are. The
 * context line under each preview is that change, and it is why the segment chips
 * and the archive are worth having at all — they only matter once rows carry
 * enough meaning to be worth sorting.
 *
 * Structure: a fixed header (title, search, chips) over a single lazy list. The
 * header stays out of the list on purpose — a search field that scrolls out of
 * composition loses focus mid-typing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBookingClick: (String) -> Unit = {},
    /** Support's one real deep link. Null hides it rather than rendering a dead tap. */
    onOpenBookings: (() -> Unit)? = null,
    /** What the destination tab is actually called for this role — "Gigs" for artists. */
    bookingsLabel: String = "Bookings",
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var showArchive by remember { mutableStateOf(false) }
    var showSupport by remember { mutableStateOf(false) }

    // Chat is a sibling destination, so opening one leaves this ViewModel alive
    // with the payload it loaded BEFORE the conversation was read: come back and
    // the row still carries the unread rail, the old preview, and the old place
    // in `last_message_at` order. Nothing else can correct it — the flags store
    // only clears a device-local "mark as unread", never the server's count — so
    // returning to the inbox re-reads it. Every resume is delivered; the
    // ViewModel decides which ones to act on, because it is the half of this
    // screen that survives the push — see MessagesViewModel.onResumed.
    ResumeEffect(onResumed = viewModel::onResumed)

    Column(modifier.fillMaxSize().background(colors.bg)) {
        InboxHeader(
            query = state.query,
            onQueryChange = viewModel::setQuery,
            filter = state.filter,
            counts = state.counts,
            archivedCount = state.archivedThreads.size,
            onFilter = viewModel::setFilter,
            onOpenArchive = { showArchive = true },
            // Chips only make sense once the first load has settled — on the very
            // first paint they would be four chips over a spinner. A FAILED load
            // still counts as settled, so Support stays reachable when the thread
            // list is the thing that's broken.
            showChips = state.hasLoaded || state.error != null,
        )

        // A refresh that failed while rows are already on screen is a strip, not
        // an empty state — the stale list is still the best thing to show.
        if (state.error != null && state.threads.isNotEmpty()) {
            InlineBanner(
                title = "Couldn't refresh messages",
                detail = state.error,
                tone = BannerTone.Failure,
                actionLabel = "Retry",
                onAction = viewModel::refresh,
                modifier = Modifier
                    .padding(horizontal = space.lg)
                    .padding(bottom = space.sm),
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

                // Ahead of the error branch: Support is the one surface that
                // doesn't depend on the thread list, so a failed load is exactly
                // when someone is most likely to want it.
                state.filter == MessagesFilter.Support -> SupportSegment(onOpen = { showSupport = true })

                state.error != null && state.threads.isEmpty() -> EmptyState(
                    title = "Couldn't load messages",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )

                // Distinguish "no conversations at all" from "none in this
                // segment": the first is about the app being new to you, the
                // second is about the chip you just tapped.
                state.activeThreads.isEmpty() -> InboxEmpty(onRefresh = viewModel::refresh)

                state.visibleThreads.isEmpty() -> EmptyState(
                    title = "No conversations here",
                    body = if (state.query.isBlank()) {
                        "Nothing in ${state.filter.label.lowercase()} yet. Try another filter."
                    } else {
                        "No conversations match “${state.query}”."
                    },
                )

                else -> ThreadList(
                    items = state.visibleThreads,
                    onOpen = onThreadClick,
                    onOpenBooking = onBookingClick,
                    onStar = viewModel::toggleStarred,
                    onArchive = viewModel::toggleArchived,
                )
            }
        }
    }

    if (showArchive) {
        ArchivedSheet(
            items = state.archivedThreads,
            onOpen = { id ->
                showArchive = false
                onThreadClick(id)
            },
            onUnarchive = viewModel::toggleArchived,
            onDismiss = { showArchive = false },
        )
    }

    if (showSupport) {
        SupportChatSheet(
            onDismiss = { showSupport = false },
            bookingsLabel = bookingsLabel,
            onOpenBookings = onOpenBookings,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InboxHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: MessagesFilter,
    counts: Map<MessagesFilter, Int>,
    archivedCount: Int,
    onFilter: (MessagesFilter) -> Unit,
    onOpenArchive: () -> Unit,
    showChips: Boolean,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = space.lg, end = space.sm, top = space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Messages", style = AppTheme.type.displaySub, color = colors.ink, modifier = Modifier.weight(1f))
        // The archive has to be reachable from the inbox or archiving is a
        // one-way door. Count in the label so an empty archive is obvious
        // before you open it.
        IconButton(onClick = onOpenArchive) {
            Icon(
                Icons.Filled.Archive,
                contentDescription = if (archivedCount > 0) {
                    "Archived, $archivedCount conversations"
                } else {
                    "Archived"
                },
                tint = if (archivedCount > 0) colors.ink2 else colors.ink3,
                modifier = Modifier.semantics { testTag = "messages.archiveEntry" },
            )
        }
    }

    SearchField(query = query, onQueryChange = onQueryChange)

    if (showChips) {
        FilterChips(filter = filter, counts = counts, onFilter = onFilter)
        Spacer(Modifier.height(space.sm))
    }
    HRule()
}

/**
 * The inbox's search field: a filled capsule under the title.
 *
 * NOT the hairline row the Search tab uses, even though the two used to share a
 * shape here. The two surfaces are genuinely different on the reference build:
 * Search is a bare field over a rule because it IS the screen — the field is
 * what you came for — whereas the inbox's search is a docked control under a
 * large title, and the reference renders that as a filled capsule. Matching the
 * bare version made the inbox's field disappear into the page: no edge, no
 * height of its own, and a magnifier floating in space above the chips.
 *
 * Measured off the reference: 44 tall, 16 side margins, a filled capsule at the
 * `bgSoft` step (its fill samples to within two levels of that token).
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.lg, vertical = space.sm)
            .height(AppTheme.dimens.size.rowMin)
            .clip(CircleShape)
            .background(colors.bgSoft)
            .padding(horizontal = space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(AppTheme.dimens.size.iconLg),
        )
        Spacer(Modifier.width(space.sm))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = AppTheme.type.callout.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.brand),
            modifier = Modifier
                .weight(1f)
                .semantics { testTag = "messages.search" },
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text("Search conversations", style = AppTheme.type.callout, color = colors.ink3)
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear search", tint = colors.ink3)
            }
        }
    }
}

/**
 * Segment chips with live counts.
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
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = space.lg),
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        MessagesFilter.entries.forEach { entry ->
            val selected = filter == entry
            val count = counts[entry] ?: 0
            val shape = RoundedCornerShape(AppTheme.dimens.radii.xl)
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(if (selected) colors.brand else Color.Transparent)
                    .border(AppTheme.dimens.size.hairline, if (selected) colors.brand else colors.line, shape)
                    .clickable { onFilter(entry) }
                    .padding(horizontal = space.md, vertical = space.sm)
                    .semantics {
                        testTag = "messages.filter.${entry.name}"
                        contentDescription =
                            "${entry.label}${if (count > 0) ", $count" else ""}${if (selected) ", selected" else ""}"
                    },
                horizontalArrangement = Arrangement.spacedBy(space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.label,
                    style = AppTheme.type.footnote,
                    color = if (selected) colors.brandInk else colors.ink2,
                )
                // Support is synthetic — it holds no threads, so a "0" beside it
                // would read as an empty inbox rather than as an assistant.
                if (count > 0 && entry != MessagesFilter.Support) {
                    Text(
                        count.toString(),
                        style = AppTheme.type.monoSmall,
                        color = if (selected) colors.brandInk else colors.ink3,
                    )
                }
            }
        }
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
    onStar: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    // Relative stamps go stale on a tab that sits open for an hour. One shared
    // minute tick keeps every row honest without each row owning a timer.
    val now = rememberMinuteClock()
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.thread.id }) { item ->
            SwipeableThreadRow(
                item = item,
                nowMs = now,
                onOpen = onOpen,
                onOpenBooking = onOpenBooking,
                onStar = onStar,
                onArchive = onArchive,
            )
        }
    }
}

/**
 * Swipe either way on a row: left for archive, right for star.
 *
 * Both `confirmValueChange` handlers fire their action and then return false, so
 * the row performs the action and springs back rather than dismissing. That is
 * deliberate — neither action deletes anything, and a row that vanishes implies
 * it did.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableThreadRow(
    item: ThreadListItem,
    nowMs: Long,
    onOpen: (String) -> Unit,
    onOpenBooking: (String) -> Unit,
    onStar: (String) -> Unit,
    onArchive: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val id = item.thread.id
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onStar(id)
                SwipeToDismissBoxValue.EndToStart -> onArchive(id)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        },
    )
    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val starring = swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier
                    .fillMaxSize()
                    .background(colors.bgElev)
                    .padding(horizontal = space.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (starring) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (starring) Icons.Filled.Star else Icons.Filled.Archive,
                    contentDescription = null,
                    tint = if (starring) colors.warm else colors.ink2,
                )
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

@Composable
private fun ThreadRow(
    item: ThreadListItem,
    nowMs: Long,
    onOpen: () -> Unit,
    onOpenBooking: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    val context = item.context
    val preview = item.thread.lastPreview.ifBlank { "No messages yet" }
    val thumbColumn = thumbColumnWidth()

    Column(Modifier.fillMaxWidth().background(colors.bg)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Leading rail rather than a trailing dot: it survives truncation,
            // reads straight down a scrolling list, and leaves the row's trailing
            // edge free for the timestamp.
            Box(
                Modifier
                    .width(AppTheme.dimens.size.stroke)
                    .fillMaxHeight()
                    .background(if (item.unread) colors.brand else Color.Transparent),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .pressScale(interaction)
                    .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
                    .padding(horizontal = space.lg, vertical = space.md)
                    // Merge only the tappable bar, never the whole Column: the
                    // "Review request" accelerator below is a separate control and
                    // merging would swallow it into the row's single a11y node.
                    .semantics(mergeDescendants = true) {
                        // The unread rail and the thumbnail are decorative, so
                        // unread-ness has to be spelled out here or it is carried
                        // by colour alone.
                        contentDescription = buildString {
                            append(item.counterpartName)
                            if (item.unread) append(", unread")
                            append(". ")
                            append(context.summary)
                            append(". ")
                            append(preview)
                        }
                        testTag = "messages.threadRow"
                    },
                horizontalArrangement = Arrangement.spacedBy(space.md),
                verticalAlignment = Alignment.Top,
            ) {
                ThreadThumb(name = item.counterpartName, coverUrl = item.coverUrl)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Name and star share ONE weighted run so the name can use
                        // the whole column when the star is absent. Weighting the
                        // name directly against a spacer would cap it at half the
                        // width and truncate act names that would otherwise fit.
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                item.counterpartName,
                                style = AppTheme.type.headline,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // Starring is otherwise silent state — a swipe that
                            // leaves no visible trace reads as a no-op.
                            if (item.starred) {
                                Spacer(Modifier.width(space.xs))
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = colors.warm,
                                    modifier = Modifier.size(AppTheme.dimens.size.iconSm),
                                )
                            }
                        }
                        Spacer(Modifier.width(space.sm))
                        Text(
                            InboxProjection.timeAgo(item.thread.lastMessageAtEpochMs, nowMs),
                            style = AppTheme.type.monoSmall,
                            color = colors.ink3,
                        )
                    }
                    Spacer(Modifier.height(AppTheme.dimens.space.xs))
                    Text(
                        preview,
                        style = AppTheme.type.footnote,
                        // Unread lifts the preview to full ink; read rows step
                        // down so an unread row wins the column at a glance.
                        color = if (item.unread) colors.ink else colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(AppTheme.dimens.space.xs))
                    ContextLine(context)
                }
            }
        }

        // The accelerator into the funnel, not a second place that mutates the
        // booking: it routes to the detail screen where accept/decline lives.
        if (context.awaitingViewer && context.bookingId != null) {
            Text(
                "Review request",
                style = AppTheme.type.footnote,
                color = colors.brand,
                modifier = Modifier
                    .padding(start = space.lg + thumbColumn + space.md, bottom = space.md)
                    .clip(CircleShape)
                    .border(AppTheme.dimens.size.hairline, colors.brand, CircleShape)
                    .clickable { onOpenBooking(context.bookingId) }
                    .padding(horizontal = space.lg, vertical = space.sm)
                    .semantics { testTag = "messages.reviewRequest" },
            )
        }
        HRule(modifier = Modifier.padding(start = space.lg + thumbColumn + space.md))
    }
}

/**
 * The gig's cover with the counterparty's face on it.
 *
 * This is what makes a row read as a *booking* rather than a contact: the work is
 * the subject, the person is the attribution.
 *
 * The tile keeps its rounded-SQUARE silhouette whether or not a cover has landed.
 * It used to collapse to a plain circle when the cache had no cover, which is the
 * common case on a cold inbox — so the list changed shape as images arrived, and
 * a row mid-hydration looked like a different kind of row. The empty tile is not
 * empty: it takes the same name-derived gradient the initials badge does, so an
 * un-hydrated row reads as "cover still loading", never as a broken image. Same
 * fallback the reference uses.
 */
@Composable
private fun ThreadThumb(name: String, coverUrl: String?) {
    val size = AppTheme.dimens.size
    Box(Modifier.size(thumbColumnWidth()).clearAndSetSemantics {}) {
        Box(
            Modifier
                .size(size.avatarMd)
                .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                .background(avatarGradient(name)),
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Avatar(
            name = name,
            size = size.iconXl,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .clip(CircleShape)
                .background(AppTheme.colors.bg)
                .padding(AppTheme.dimens.size.hairline),
        )
    }
}

/**
 * Status dot + state + date + venue.
 *
 * The dot's colour comes from [bookingStatusTone] — the app's single
 * status→tone mapping — so a thread and its booking can never disagree about
 * what colour "confirmed" is. A thread with no booking gets the neutral tone
 * because there is no state to assert.
 */
@Composable
private fun ContextLine(context: ThreadContext) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val tone = context.status?.let(::bookingStatusTone) ?: PillTone.Neutral
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.xs),
    ) {
        Box(
            Modifier
                .size(AppTheme.dimens.size.dot)
                .clip(CircleShape)
                .background(toneColor(tone)),
        )
        Text(context.statusLabel, style = AppTheme.type.monoSmall, color = colors.ink2)
        context.detailLine?.let {
            Text(
                "· $it",
                style = AppTheme.type.monoSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The tone→colour half of [bookingStatusTone], for the places that need a bare
 * dot rather than a filled capsule. Kept next to its only callers rather than
 * pushed into the shared Pill so the capsule stays the one public shape.
 */
@Composable
internal fun toneColor(tone: PillTone): Color {
    val colors = AppTheme.colors
    return when (tone) {
        PillTone.Neutral -> colors.ink3
        // Both brand tones resolve to the same dot: the solid/washed split is
        // about the capsule's fill, and a bare dot has no fill to differ in.
        PillTone.Brand, PillTone.BrandSolid -> colors.brand
        PillTone.Accent -> colors.accent
        PillTone.Good -> colors.good
        PillTone.Warm -> colors.warm
        PillTone.Hot -> colors.hot
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty / loading / archive / support
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Four grey rows while the first page lands.
 *
 * A skeleton rather than a spinner because the inbox has a shape worth
 * promising: it tells the user rows are coming and roughly how they will sit,
 * which a centred spinner does not.
 */
@Composable
private fun InboxSkeleton() {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val size = AppTheme.dimens.size
    val thumbColumn = thumbColumnWidth()
    Column(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        repeat(SKELETON_ROWS) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = space.lg, vertical = space.md),
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                Box(
                    Modifier
                        .size(size.avatarMd)
                        .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                        .background(colors.bgCard),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.sm)) {
                    Box(
                        Modifier
                            .fillMaxWidth(SKELETON_TITLE_FRACTION)
                            .height(size.iconSm)
                            .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                            .background(colors.bgCard),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(SKELETON_BODY_FRACTION)
                            .height(size.iconSm)
                            .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                            .background(colors.bgElev),
                    )
                }
            }
            HRule(modifier = Modifier.padding(start = space.lg + thumbColumn + space.md))
        }
    }
}

/**
 * An empty inbox is a state, not a call to action, so the one action is outlined
 * rather than filled: there is no universal "go start a conversation" here (a
 * client discovers artists, an artist waits to be found), and refreshing is the
 * only honest thing to offer both.
 */
@Composable
private fun InboxEmpty(onRefresh: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxSize().padding(space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "You don't have any messages",
            style = AppTheme.type.headline,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(space.sm))
        Text(
            "New conversations with artists and clients show up here.",
            style = AppTheme.type.body,
            color = colors.ink3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(space.lg))
        PrimaryButton(text = "Refresh", onClick = onRefresh, variant = ButtonVariant.Ghost)
    }
}

/** The synthetic Support segment: one entry row into the scripted assistant. */
@Composable
private fun SupportSegment(onOpen: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val thumbColumn = thumbColumnWidth()
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = space.lg, vertical = space.md)
                .semantics { testTag = "messages.supportEntry" },
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand wash, not a brand fill: this is the app talking, and lime
            // stays reserved for the thing you are meant to do next.
            Box(
                Modifier
                    .size(AppTheme.dimens.size.avatarMd)
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .background(colors.brandSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text("A", style = AppTheme.type.monoMedium, color = colors.brand)
            }
            Column(Modifier.weight(1f)) {
                Text("Artistant Support", style = AppTheme.type.headline, color = colors.ink)
                Text(
                    "Booking help, safety and feedback — right here.",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HRule(modifier = Modifier.padding(start = space.lg + thumbColumn + space.md))
    }
}

/** The archive. Rows open as normal; the trailing action puts one back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArchivedSheet(
    items: List<ThreadListItem>,
    onOpen: (String) -> Unit,
    onUnarchive: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = space.xxl)) {
            Text(
                "Archived",
                style = AppTheme.type.headline,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = space.xl, vertical = space.md),
            )
            HRule()
            if (items.isEmpty()) {
                EmptyState(
                    title = "Nothing archived",
                    body = "Swipe a conversation left in Messages to archive it — it'll wait for you here.",
                )
                return@Column
            }
            LazyColumn(Modifier.fillMaxWidth()) {
                items(items, key = { it.thread.id }) { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item.thread.id) }
                            .padding(horizontal = space.xl, vertical = space.md),
                        horizontalArrangement = Arrangement.spacedBy(space.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(name = item.counterpartName, size = AppTheme.dimens.size.avatarSm)
                        Column(Modifier.weight(1f)) {
                            Text(item.counterpartName, style = AppTheme.type.callout, color = colors.ink, maxLines = 1)
                            Text(
                                item.thread.lastPreview,
                                style = AppTheme.type.footnote,
                                color = colors.ink3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onUnarchive(item.thread.id) }) {
                            Icon(Icons.Filled.Unarchive, contentDescription = "Unarchive", tint = colors.warm)
                        }
                    }
                    HRule()
                }
            }
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
private fun rememberMinuteClock(): Long =
    produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(CLOCK_TICK_MS)
            value = System.currentTimeMillis()
        }
    }.value

/**
 * The leading column width — the thumbnail plus the overlap the counterparty
 * avatar needs, and the inset every hairline in the list aligns to so the rules
 * start under the text rather than under the artwork.
 *
 * Composed from tokens rather than written as a number so it moves if the avatar
 * scale ever does.
 */
@Composable
private fun thumbColumnWidth(): Dp = AppTheme.dimens.size.avatarMd + AppTheme.dimens.space.sm

private const val SKELETON_ROWS = 4
private const val SKELETON_TITLE_FRACTION = 0.45f
private const val SKELETON_BODY_FRACTION = 0.7f
private const val CLOCK_TICK_MS = 60_000L
