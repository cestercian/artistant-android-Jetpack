package `in`.artistant.app.feature.bookings

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.AccentNoteCard
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.MediaSlot
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.ScreenHeader
import `in`.artistant.app.designsystem.component.SegmentedControl
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.component.bookingStatusTone
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.booking.TechRiderSheet
import `in`.artistant.app.feature.messages.ChatOpenViewModel

/**
 * The client's Bookings tab — screens 10 (the list), 89 (empty plus a nudge) and
 * 122 (offline, from the cached essentials).
 *
 * One list, two segments, three affordances. Screen 10's note is the whole
 * design: "Confirmed, pending and played each get a different affordance, not a
 * badge." So a confirmed gig is a card carrying the act's picture and the two
 * things you do on the night; an unanswered request is a quiet row that names
 * what it is waiting on; a played one is a row with the review invitation on it.
 * The previous version drew all three as identical rows with a coloured chip,
 * which meant the list told you nothing until you read every word of it.
 */
@Composable
fun BookingsScreen(
    onBookingClick: (bookingId: String) -> Unit,
    modifier: Modifier = Modifier,
    onFindArtist: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onOpenCalendar: () -> Unit = {},
    onOpenChat: (threadId: String) -> Unit = {},
    viewModel: BookingsViewModel = hiltViewModel(),
    chatOpen: ChatOpenViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openingChat by chatOpen.opening.collectAsStateWithLifecycle()
    val chatError by chatOpen.error.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    var techRiderFor by remember { mutableStateOf<BookingsListItem?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        Column(Modifier.padding(horizontal = dimens.component.gutter)) {
            ScreenHeader(
                title = "Bookings",
                // The subtitle is not decoration and is not always there: it
                // appears only when the list on screen is a cached one, which is
                // the one time the page is showing something other than the truth.
                subtitle = if (state.showsCached) "Showing your last sync" else null,
                trailing = {
                    if (state.showsCached) {
                        IconCircle(
                            icon = Icons.Filled.Refresh,
                            contentDescription = "Try again",
                            onClick = viewModel::refresh,
                            size = dimens.component.iconCircleSm,
                        )
                    } else {
                        IconCircle(
                            icon = Icons.Filled.CalendarMonth,
                            contentDescription = "Month calendar",
                            onClick = onOpenCalendar,
                            size = dimens.component.iconCircleSm,
                        )
                    }
                },
            )
        }

        when {
            state.isLoading && state.items.isEmpty() -> BookingsSkeleton()

            state.showsCached -> CachedBookings(
                snapshot = requireNotNull(state.cached),
                offline = state.offline,
                onRetry = viewModel::refresh,
            )

            state.error != null && state.items.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(dimens.space.xxl))
                EmptyState(
                    title = if (state.offline) "You're offline" else "Couldn't load bookings",
                    body = if (state.offline) {
                        "Nothing is cached on this device yet, so there is nothing to show " +
                            "until you reconnect."
                    } else {
                        state.error
                    },
                    icon = Icons.Filled.CalendarMonth,
                    actionLabel = "Try again",
                    onAction = viewModel::refresh,
                )
            }

            state.items.isEmpty() -> EmptyBookings(
                showNudge = state.showNameNudge,
                onDismissNudge = viewModel::dismissNameNudge,
                onEditProfile = onEditProfile,
                onFindArtist = onFindArtist,
            )

            else -> BookingsList(
                state = state,
                openingChat = openingChat,
                chatError = chatError,
                onDismissChatError = chatOpen::dismissError,
                onSelectTab = viewModel::selectTab,
                onBookingClick = onBookingClick,
                onMessage = { item ->
                    chatOpen.open(item.booking.artistId, item.booking.id, onOpenChat)
                },
                onTechRider = { techRiderFor = it },
                onFindArtist = onFindArtist,
            )
        }
    }

    techRiderFor?.let { item ->
        TechRiderSheet(
            artistName = item.artistName,
            items = item.techRider,
            onDismiss = { techRiderFor = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The list
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookingsList(
    state: BookingsUiState,
    openingChat: Boolean,
    chatError: String?,
    onDismissChatError: () -> Unit,
    onSelectTab: (BookingsTab) -> Unit,
    onBookingClick: (String) -> Unit,
    onMessage: (BookingsListItem) -> Unit,
    onTechRider: (BookingsListItem) -> Unit,
    onFindArtist: () -> Unit,
) {
    val dimens = AppTheme.dimens
    val rows = state.visible
    RevealOnAppear {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Spacer(Modifier.height(dimens.space.xs))
            // The shared switch, not chips: every booking is in exactly one half
            // and there is no state where neither is chosen. Its selected fill is
            // a raised hairline rather than lime, which leaves the screen's one
            // accent for the countdown badge — the thing worth finding.
            SegmentedControl(
                options = BookingsTab.entries,
                selected = state.tab,
                onSelect = onSelectTab,
                label = { it.label },
            )
            // A thread that would not open is still true next time this screen is
            // opened, so it states itself here rather than as a snackbar that
            // scrolls away with the tap that caused it.
            chatError?.let {
                Banner(
                    title = it,
                    tone = BannerTone.Failure,
                    actionLabel = "Dismiss",
                    onAction = onDismissChatError,
                )
            }
            Spacer(Modifier.height(dimens.space.xs))

            if (rows.isEmpty()) {
                // A segment with nothing in it is not the empty STATE — the other
                // segment has bookings in it — so it says which half is empty and
                // offers the way out of that half specifically.
                EmptyState(
                    title = when (state.tab) {
                        BookingsTab.Upcoming -> "Nothing coming up"
                        BookingsTab.Past -> "Nothing played yet"
                    },
                    body = when (state.tab) {
                        BookingsTab.Upcoming ->
                            "Your past bookings are under Past. Book someone and they land here."
                        BookingsTab.Past ->
                            "Bookings move here once the night is over."
                    },
                    icon = Icons.Filled.CalendarMonth,
                    actionLabel = if (state.tab == BookingsTab.Upcoming) "Find an artist" else null,
                    onAction = if (state.tab == BookingsTab.Upcoming) onFindArtist else null,
                )
            } else {
                rows.forEach { item ->
                    when (affordanceFor(item.booking.status)) {
                        BookingAffordance.Confirmed -> ConfirmedBookingCard(
                            item = item,
                            asOfMs = state.asOfMs,
                            openingChat = openingChat,
                            onClick = { onBookingClick(item.booking.id) },
                            onMessage = { onMessage(item) },
                            onTechRider = { onTechRider(item) },
                        )

                        else -> BookingRow(
                            item = item,
                            affordance = affordanceFor(item.booking.status),
                            onClick = { onBookingClick(item.booking.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(dimens.chrome.contentTailroom))
        }
    }
}

/**
 * A confirmed gig: the act's picture, when and where, and the two things anyone
 * does about a booking before the night — talk to them, and check what they need
 * on stage.
 */
@Composable
private fun ConfirmedBookingCard(
    item: BookingsListItem,
    asOfMs: Long,
    openingChat: Boolean,
    onClick: () -> Unit,
    onMessage: () -> Unit,
    onTechRider: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val badge = countdownBadge(item.startMs, asOfMs)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.xl))
            .background(colors.surface3)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.component.bookingCardImage)
                .background(colors.placeholder),
        ) {
            item.coverUrl?.takeIf { it.isNotBlank() }?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // The card's one accent, and it is spent on the only thing here that
            // changes by itself: how close the night is.
            badge?.let {
                Text(
                    it,
                    style = AppTheme.type.badge,
                    color = colors.onAccent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(dimens.space.md)
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = dimens.space.md, vertical = dimens.space.xs + dimens.space.xs / 2),
                )
            }
        }
        Column(Modifier.padding(dimens.space.lg)) {
            Text(
                item.artistName,
                style = AppTheme.type.cardTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                whenAndWhereLine(item.booking.date, item.booking.time, item.booking.venue),
                style = AppTheme.type.subtitle,
                color = colors.ink4,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = dimens.space.xs),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.space.md),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                CardAction(
                    label = if (openingChat) "Opening…" else "Message",
                    onClick = onMessage,
                    enabled = !openingChat,
                    modifier = Modifier.weight(1f),
                )
                CardAction(
                    label = "Tech rider",
                    onClick = onTechRider,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** One of the two quiet buttons inside a booking card. */
@Composable
private fun CardAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        modifier
            .height(dimens.size.rowMin)
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.surface2)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = AppTheme.type.rowTitle,
            color = if (enabled) colors.ink else colors.ink4,
            maxLines = 1,
        )
    }
}

/**
 * A request, a played gig, or a record — a thumbnail, two lines, and whatever
 * this state is actually offering.
 */
@Composable
private fun BookingRow(
    item: BookingsListItem,
    affordance: BookingAffordance,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val ended = affordance == BookingAffordance.Ended
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .clickable(onClick = onClick)
            .padding(dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaSlot(
            modifier = Modifier
                .size(dimens.size.avatarLg)
                // A cancelled booking's picture is faded, not hidden: the record
                // stays legible (screen 83's "terminal but not dead"), it simply
                // stops competing with the live rows above it.
                .alpha(if (ended) ENDED_ALPHA else 1f),
            radius = dimens.radii.md,
        ) {
            item.coverUrl?.takeIf { it.isNotBlank() }?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.artistName,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = if (ended) colors.ink2 else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when (affordance) {
                    BookingAffordance.Review ->
                        playedLine(item.category, item.booking.date)
                    else -> categoryAndDateLine(item.category, item.booking.date)
                },
                style = AppTheme.type.caption,
                color = colors.ink4,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
            Spacer(Modifier.height(dimens.space.xs + dimens.space.xs / 2))
            when (affordance) {
                // Not a pill: what a request is waiting on is a sentence, and a
                // capsule around it would rank it beside a status badge instead of
                // reading as the row's own line.
                BookingAffordance.Awaiting -> Text(
                    "Awaiting artist confirmation",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accentInk,
                )
                // The one control on a played row, and the reason it is a solid
                // accent pill rather than a link: it is a call to action, not a
                // status. The row opens the booking, where the sheet lives.
                BookingAffordance.Review -> Pill("Leave a review", tone = PillTone.BrandSolid)
                BookingAffordance.Ended -> Pill(
                    item.booking.status.label,
                    tone = bookingStatusTone(item.booking.status),
                )
                BookingAffordance.Confirmed -> Unit
            }
        }
    }
}

/** How far a terminal row's picture and name step back. */
private const val ENDED_ALPHA = 0.55f

// ─────────────────────────────────────────────────────────────────────────────
// Empty, loading, offline
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Screen 89: nothing booked yet, with the profile prompt ABOVE the empty state
 * rather than replacing it.
 *
 * The order is the design's note, and it matters: the empty state is the answer
 * to "why is this page blank", and a nudge in its place would answer a question
 * the user did not ask.
 */
@Composable
private fun EmptyBookings(
    showNudge: Boolean,
    onDismissNudge: () -> Unit,
    onEditProfile: () -> Unit,
    onFindArtist: () -> Unit,
) {
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.component.gutter),
    ) {
        if (showNudge) {
            Spacer(Modifier.height(dimens.space.md))
            NameNudge(onGo = onEditProfile, onDismiss = onDismissNudge)
        }
        Spacer(Modifier.height(dimens.space.xxl))
        EmptyState(
            title = "No bookings yet",
            body = "When you book an artist it lands here, with the run of show and their contact.",
            icon = Icons.Filled.CalendarMonth,
            actionLabel = "Find an artist",
            onAction = onFindArtist,
        )
    }
}

/**
 * "Add your name — so artists know who's asking."
 *
 * Shown only when `users.full_name` is really blank (see
 * `BookingsViewModel.refreshNudge`), so it is never a nag at somebody who has
 * already done it. Dismissing persists: the prompt is a suggestion, and a
 * suggestion that returns after being refused is a nag.
 */
@Composable
private fun NameNudge(onGo: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    AccentNoteCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Add your name",
                    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                )
                Text(
                    "So artists know who's asking",
                    style = AppTheme.type.caption,
                    color = colors.ink2,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
            Text(
                "Go",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.onDark,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .background(colors.ink)
                    .clickable(role = Role.Button, onClick = onGo)
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.sm),
            )
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss",
                tint = colors.ink2,
                modifier = Modifier
                    .size(dimens.size.iconLg)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss),
            )
        }
    }
}

/**
 * Screen 122 — the night the venue's Wi-Fi is a basement.
 *
 * Everything here comes off the snapshot, and the snapshot is deliberately small
 * (see [CachedBooking]). A cached CONFIRMED booking is shown in full, because
 * confirmed is the one status that cannot silently become something better; every
 * other cached status renders as Unknown and says why, because a request may
 * well have been answered since this list was written and showing the old word
 * would be a claim we cannot make.
 */
@Composable
private fun CachedBookings(
    snapshot: BookingsSnapshot,
    offline: Boolean,
    onRetry: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val clipboard = LocalClipboardManager.current
    val haptics = rememberHaptics()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.component.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Spacer(Modifier.height(dimens.space.xs))
        Banner(
            // Two different facts, and the user's next move differs: one is their
            // Wi-Fi, the other is ours.
            title = if (offline) "You're offline" else "Can't reach Artistant",
            detail = cachedAtLabel(snapshot.cachedAtMs) + " · this is the last list we read",
            tone = BannerTone.Attention,
            actionLabel = "Retry",
            onAction = onRetry,
        )
        snapshot.items.forEach { cached ->
            val confirmed = BookingStatus.fromDb(cached.status) == BookingStatus.Confirmed
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.card))
                    .background(colors.surface3)
                    .alpha(if (confirmed) 1f else ENDED_ALPHA)
                    .padding(dimens.space.lg),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        cached.artistName,
                        style = AppTheme.type.cardTitle,
                        color = if (confirmed) colors.ink else colors.ink2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Pill(
                        if (confirmed) "Confirmed" else "Unknown",
                        tone = if (confirmed) PillTone.BrandSolid else PillTone.Warm,
                    )
                }
                if (confirmed) {
                    Text(
                        whenAndWhereLine(cached.date, cached.time, cached.venue),
                        style = AppTheme.type.subtitle,
                        color = colors.ink4,
                        modifier = Modifier.padding(top = dimens.space.sm),
                    )
                    cached.venue.takeIf { it.isNotBlank() }?.let { venue ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = dimens.space.md)
                                .clickable(role = Role.Button) {
                                    clipboard.setText(AnnotatedString(venue))
                                    haptics.success()
                                }
                                .semantics { contentDescription = "Copy address: $venue" },
                            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Place,
                                contentDescription = null,
                                tint = colors.ink3,
                                modifier = Modifier.size(dimens.size.iconMd),
                            )
                            Text(
                                "$venue — cached, tap to copy",
                                style = AppTheme.type.subtitle,
                                color = colors.ink2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    cached.venueNotes?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = AppTheme.type.caption,
                            color = colors.ink4,
                            modifier = Modifier.padding(top = dimens.space.sm),
                        )
                    }
                } else {
                    Text(
                        "Status needs a connection to confirm",
                        style = AppTheme.type.subtitle,
                        color = colors.ink4,
                        modifier = Modifier.padding(top = dimens.space.sm),
                    )
                }
            }
        }
        AccentNote(
            "Venue, load-in notes and show times stay readable offline — that is the " +
                "data you need on the night.",
        )
        Spacer(Modifier.height(dimens.chrome.contentTailroom))
    }
}

/**
 * Loading, narrated by shape rather than by a spinner: three blocks the size of
 * the rows that are coming, so the fill-in doesn't reflow what the eye already
 * parsed.
 */
@Composable
private fun BookingsSkeleton() {
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.component.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Spacer(Modifier.height(dimens.space.xs))
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.size.controlMin),
            radius = dimens.radii.control,
        )
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.component.skeletonTile),
            radius = dimens.radii.xl,
        )
        repeat(2) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.size.avatarLg + dimens.space.xl),
                radius = dimens.radii.card,
            )
        }
    }
}
