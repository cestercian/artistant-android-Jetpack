package `in`.artistant.app.feature.messages

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.MessageKind
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.bookingStatusTone
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.booking.CircleIconButton
import kotlinx.coroutines.delay
import java.util.Date

/**
 * One conversation.
 *
 * Three things wrap the transcript, and each answers a question the bare bubble
 * list left open: the strip at the top says *which gig* this is (a name alone
 * meant bouncing out to Bookings to find out), the caption above the composer
 * says *how soon*, and the CTA below it offers the one move that is actually the
 * reader's to make. The safety notice sits between them and says only what is
 * true — chat is not analysed, but the report flow behind it is real.
 *
 * The transcript is a single lazy list and stays one: it is realtime and
 * unbounded, and every decoration (day rules, sender captions, read receipt)
 * renders inside its message's own item so the list's item count always equals
 * the message count — which is what makes "scroll to the newest" a one-liner.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onBookingClick: (String) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    RefreshOnResume(onResumed = viewModel::onResumed)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            // The composer and the funnel CTA both have to clear the keyboard, so
            // the inset goes on the whole screen rather than on the input.
            //
            // `exclude(navigationBars)` rather than a bare `imePadding()`: the tab
            // scaffold already pads its content by the system bars, and the IME
            // inset is measured from the screen edge — so padding by the full IME
            // height would count the navigation bar twice and leave the composer
            // floating a bar's height above the keyboard.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        ChatTopBar(title = state.title, onBack = onBack, onDetails = viewModel::openDetails)
        ThreadContextStrip(context = state.context, onOpenBooking = onBookingClick)
        if (state.safetyBannerVisible) {
            SafetyBanner(onDismiss = viewModel::dismissSafetyBanner)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.messages.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = colors.brand) }

                state.error != null && state.messages.isEmpty() -> EmptyState(
                    title = "Couldn't load this conversation",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.messages.isEmpty() -> EmptyState(
                    title = "No messages yet",
                    body = "Say hello — this is the start of the conversation.",
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> Transcript(
                    state = state,
                    onBookingClick = onBookingClick,
                    onRetry = viewModel::retryFailedMessage,
                )
            }
        }

        ComposerStack(
            state = state,
            onSend = viewModel::send,
            onRetryRefresh = viewModel::refresh,
            onOpenBooking = onBookingClick,
        )
    }

    if (state.showDetails) {
        ThreadDetailsSheet(
            counterpartName = state.title,
            context = state.context,
            viewerIsArtist = state.viewerIsArtist,
            artistId = state.artistId,
            artistSubtitle = state.artistSubtitle,
            artistScore = state.artistScore,
            starred = state.starred,
            archived = state.archived,
            muted = state.muted,
            reportSubmitted = state.reportSubmitted,
            onBookingClick = onBookingClick,
            onArtistClick = onArtistClick,
            onToggleStar = viewModel::toggleStarred,
            onToggleArchive = {
                viewModel.toggleArchived()
                viewModel.dismissDetails()
            },
            // Stays open, unlike archive: the row's own label and caption ARE
            // the confirmation that the mute landed, so closing the sheet would
            // hide the only feedback the action has.
            onToggleMute = viewModel::toggleMuted,
            onMarkUnread = {
                viewModel.markUnread()
                viewModel.dismissDetails()
            },
            onReport = viewModel::reportConversation,
            onDismiss = viewModel::dismissDetails,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chrome
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatTopBar(title: String, onBack: () -> Unit, onDetails: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val dimens = AppTheme.dimens
    // Box, not Row: the counterpart's name is centred against the BAR, the way
    // the funnel header and the reference's inline nav bar centre theirs. Laid
    // out as a Row child it would centre in the space left over beside the back
    // button and visibly sit off-axis. The reservations either side are the two
    // controls' own widths, so a long name ellipsises before it reaches either.
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = space.xs, end = space.lg, top = space.xs)
            .height(dimens.size.rowMin),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = AppTheme.type.headline,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = dimens.size.barTitleReserve),
        )
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        // A labelled pill rather than a bare avatar: the avatar exposed none of
        // the thread's context, and for an artist viewer it was inert — their
        // counterpart is a client with no profile to open, so tapping their own
        // face did nothing.
        Text(
            "Details",
            style = AppTheme.type.footnote,
            color = colors.ink,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(CircleShape)
                .background(colors.bgElev)
                .border(dimens.size.hairline, colors.line, CircleShape)
                .clickable(onClick = onDetails)
                .padding(horizontal = space.md, vertical = space.sm)
                .semantics { testTag = "chat.details" },
        )
    }
}

/**
 * The always-on gig line: status, date, venue, fee.
 *
 * Tappable straight through to the booking when there is one, because the most
 * common reason to want this information is to go and act on it.
 */
@Composable
private fun ThreadContextStrip(context: ThreadContext, onOpenBooking: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val tone = context.status?.let(::bookingStatusTone) ?: PillTone.Neutral
    val bookingId = context.bookingId

    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgElev)
            .then(
                if (bookingId != null) Modifier.clickable { onOpenBooking(bookingId) } else Modifier,
            )
            .padding(horizontal = space.lg, vertical = space.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = context.summary
                testTag = "chat.contextStrip"
            },
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
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f))
        context.fee?.let {
            Text(formatInr(it), style = AppTheme.type.monoSmall, color = colors.brand)
        }
    }
    HRule()
}

/**
 * The trust notice.
 *
 * It says exactly two true things: keep the conversation here, and report
 * anything that feels wrong. It deliberately does NOT claim messages are
 * "analysed for safety" — no such job exists, and a safety promise the product
 * doesn't keep is worse than no banner. Quiet surface, not a warning colour:
 * this is reassurance, and dressing it in amber would read as an accusation
 * against whoever you are talking to.
 */
@Composable
private fun SafetyBanner(onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bgElev)
            .padding(start = space.lg, end = space.xs, top = space.xs, bottom = space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Icon(
            Icons.Filled.Shield,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(AppTheme.dimens.size.iconMd),
        )
        Text(
            "Keep chats on Artistant. Don't move payments or contact off-platform — " +
                "report anything that feels off.",
            style = AppTheme.type.caption,
            color = colors.ink2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { testTag = "chat.safetyBanner.dismiss" },
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Dismiss safety notice",
                tint = colors.ink3,
                modifier = Modifier.size(AppTheme.dimens.size.iconMd),
            )
        }
    }
    HRule()
}

// ─────────────────────────────────────────────────────────────────────────────
// Transcript
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Transcript(
    state: ChatUiState,
    onBookingClick: (String) -> Unit,
    onRetry: (String) -> Unit,
) {
    val space = AppTheme.dimens.space
    val messages = state.messages
    val listState = rememberLazyListState()

    // Grouping is pure and only changes when the transcript does, so it is
    // computed once per message-list identity rather than per recomposition.
    val dayStarts = remember(messages) { ChatTimestamps.dayStartIds(messages) }
    val incomingStarts = remember(messages) { ChatTimestamps.incomingRunStartIds(messages) }
    val outgoingEnds = remember(messages) { ChatTimestamps.outgoingRunEndIds(messages) }
    // One consistent "now" for the whole transcript, so two separators can't
    // disagree about which day is Today mid-scroll — but one that re-reads the
    // clock when the day actually turns over (see rememberDayClock).
    val now = rememberDayClock()
    // The platform formatters honour the user's locale AND their 24-hour setting.
    // Never reintroduce a fixed "h:mm a" pattern here.
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val dateFormat = remember(context) { DateFormat.getMediumDateFormat(context) }

    FollowTail(listState = listState, messages = messages)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = space.lg),
        state = listState,
        // Bottom-anchored. A conversation grows upward from the composer, so a
        // two-message thread should sit just above where you type, not pinned
        // under the safety banner with a screenful of nothing below it. Once the
        // transcript is taller than the viewport the alignment is a no-op and
        // this behaves like any other list.
        verticalArrangement = Arrangement.spacedBy(space.xs, Alignment.Bottom),
    ) {
        items(messages, key = { it.id }) { message ->
            // Every decoration renders inside its message's item so the list's
            // item count stays equal to the message count.
            Column(Modifier.fillMaxWidth()) {
                if (message.id in dayStarts) {
                    DaySeparatorRow(
                        sentAtEpochMs = message.sentAtEpochMs,
                        nowMs = now,
                        dateFormat = dateFormat,
                    )
                }
                if (message.id in incomingStarts) {
                    SenderCaption(
                        name = state.title,
                        role = ThreadCounterpart.counterpartRole(state.viewerIsArtist),
                        time = timeFormat.format(Date(message.sentAtEpochMs)),
                    )
                }
                MessageRow(
                    message = message,
                    onBookingClick = onBookingClick,
                    onRetry = { onRetry(message.id) },
                )
                if (message.id in outgoingEnds) {
                    TrailingCaption(timeFormat.format(Date(message.sentAtEpochMs)))
                }
                if (message.id == state.lastReadOwnMessageId) {
                    TrailingCaption(
                        text = state.counterpartLastReadAt
                            ?.let { "Read · ${timeFormat.format(Date(it))}" }
                            ?: "Read",
                        readable = "Read by ${state.title}",
                        tag = "chat.readReceipt",
                    )
                }
            }
        }
    }
}

/**
 * Keep the newest message in view without yanking someone reading history.
 *
 * The "am I at the bottom" flag is recomputed only when a **user scroll gesture
 * ends** — never when the list grows. That distinction is the whole trick: if
 * arriving messages could update it, a new message would flip the flag false
 * between the list growing and this effect reading it, and the thread would stop
 * following exactly when it matters. Sending always re-arms the follow, because
 * pressing send is an unambiguous statement about where you want to be.
 */
@Composable
private fun FollowTail(
    listState: androidx.compose.foundation.lazy.LazyListState,
    messages: List<Message>,
) {
    // Held in a plain holder, read only inside effects: nothing composes against
    // it, so tracking scroll position costs no recomposition.
    val followTail = remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) return@collect
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            followTail.value = last == null || last.index >= info.totalItemsCount - 1
        }
    }

    LaunchedEffect(messages.lastOrNull()?.id, messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val ownSend = messages.last().isMine
        if (ownSend) followTail.value = true
        if (!followTail.value) return@LaunchedEffect
        // First paint lands directly at the bottom; later arrivals animate, so
        // the movement reads as "a message came in" rather than a jump cut.
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(messages.lastIndex)
        } else {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
}

@Composable
private fun MessageRow(
    message: Message,
    onBookingClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    if (message.kind == MessageKind.System) {
        SystemRow(message = message, onBookingClick = onBookingClick)
        return
    }

    val mine = message.isMine
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            message.body,
            style = AppTheme.type.callout,
            color = if (mine) colors.brandInk else colors.ink,
            modifier = Modifier
                // A gutter on the far side rather than a width fraction: it keeps
                // the bubble hugging its own content while guaranteeing the
                // opposite edge always shows, which is what makes a thread read
                // as two columns.
                .padding(start = if (mine) space.sm * BUBBLE_GUTTER_STEPS else space.xs)
                .padding(end = if (mine) space.xs else space.sm * BUBBLE_GUTTER_STEPS)
                // In-flight sends read as dimmed until the server confirms; a
                // failed one dims too, with the retry chip below carrying the
                // actionable signal.
                .alpha(if (message.delivery == MessageDelivery.Sent) 1f else IN_FLIGHT_ALPHA)
                .clip(RoundedCornerShape(AppTheme.dimens.radii.lg))
                .background(if (mine) colors.brand else colors.bgCard)
                .padding(horizontal = space.md, vertical = space.sm),
        )
        if (mine && message.delivery == MessageDelivery.Failed) {
            // The only place `hot` appears in a thread, so a failed send is
            // unmistakable against a wall of lime and card grey.
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = space.xs, vertical = space.xs)
                    .semantics { testTag = "chat.retry" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.xs),
            ) {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = colors.hot,
                    modifier = Modifier.size(AppTheme.dimens.size.iconSm),
                )
                Text("Not sent · Tap to retry", style = AppTheme.type.caption, color = colors.hot)
            }
        }
    }
}

/**
 * A state change, not a person: centred caption text with no bubble chrome.
 *
 * When the row carries an `action_route` it gets exactly one action, routed
 * through the same booking destination the funnel CTA uses — so a system notice
 * and the CTA above it can never send you to two different places. An
 * unrecognised or malformed route renders as a plain notice rather than a
 * dangling link.
 */
@Composable
private fun SystemRow(message: Message, onBookingClick: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val bookingId = message.actionRoute
        ?.removePrefix(BOOKING_ROUTE_PREFIX)
        ?.takeIf { it.isNotBlank() && it != message.actionRoute }

    Column(
        Modifier.fillMaxWidth().padding(vertical = space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message.body,
            style = AppTheme.type.caption,
            color = colors.ink3,
            textAlign = TextAlign.Center,
        )
        bookingId?.let {
            Text(
                "View booking",
                style = AppTheme.type.caption,
                color = colors.ink2,
                modifier = Modifier
                    .padding(top = space.xs)
                    .clip(CircleShape)
                    .clickable { onBookingClick(it) }
                    .padding(horizontal = space.sm, vertical = space.xs)
                    .semantics { testTag = "chat.systemAction" },
            )
        }
    }
}

/**
 * Centered "Today / Yesterday / 4 Aug 2026" rule between days. Relative labels
 * for the two recent buckets because that's how people actually read a
 * transcript; anything older gets the locale's medium date.
 */
@Composable
private fun DaySeparatorRow(sentAtEpochMs: Long, nowMs: Long, dateFormat: java.text.DateFormat) {
    val colors = AppTheme.colors
    val label = when (ChatTimestamps.daySeparator(sentAtEpochMs, nowMs)) {
        DaySeparator.Today -> "Today"
        DaySeparator.Yesterday -> "Yesterday"
        DaySeparator.Earlier -> dateFormat.format(Date(sentAtEpochMs))
    }
    Text(
        label,
        style = AppTheme.type.monoSmall,
        color = colors.ink3,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.space.sm),
    )
}

/**
 * "Asha Rao · CLIENT · 9:14 am" above the first bubble of an incoming run.
 * Marked decorative for a11y: the bubble itself carries the readable content, so
 * a screen reader shouldn't announce the attribution twice.
 */
@Composable
private fun SenderCaption(name: String, role: String, time: String) {
    Text(
        "$name · ${role.uppercase()} · $time",
        style = AppTheme.type.monoSmall,
        color = AppTheme.colors.ink3,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.dimens.space.xs)
            .clearAndSetSemantics {},
    )
}

/**
 * Trailing-aligned caption under an outgoing run — the bare send time, or the
 * read receipt. Only the receipt is announced; the time is already implied by
 * the day rule above and would otherwise be read out after every burst.
 */
@Composable
private fun TrailingCaption(text: String, readable: String? = null, tag: String? = null) {
    Text(
        text,
        style = AppTheme.type.monoSmall,
        color = AppTheme.colors.ink3,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.dimens.space.xs)
            .then(
                if (readable == null) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier.semantics {
                        contentDescription = readable
                        if (tag != null) testTag = tag
                    }
                },
            ),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Composer stack
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Everything pinned below the transcript: the how-soon caption, the input, and
 * the funnel CTA. All three sit inside the keyboard inset so the action stays
 * reachable while typing — the reason it is here rather than in the header.
 */
@Composable
private fun ComposerStack(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onRetryRefresh: () -> Unit,
    onOpenBooking: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val now = rememberDayClock()

    Column(Modifier.fillMaxWidth()) {
        // A failed SEND already carries its own retry on the bubble, and that is
        // the more precise report — so the strip only speaks for the failures
        // nothing else is reporting.
        val hasFailedSend = state.messages.any { it.delivery == MessageDelivery.Failed }
        if (state.error != null && state.messages.isNotEmpty() && !hasFailedSend) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bgElev)
                    .padding(horizontal = space.lg, vertical = space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                Text(
                    "Couldn't refresh this conversation.",
                    style = AppTheme.type.caption,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "RETRY",
                    style = AppTheme.type.caption,
                    color = colors.brand,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onRetryRefresh)
                        .padding(horizontal = space.sm, vertical = space.xs),
                )
            }
        }

        gigCaption(state.context, now)?.let {
            Text(
                it,
                style = AppTheme.type.monoSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.lg)
                    .semantics { testTag = "chat.gigCaption" },
            )
        }

        MessageComposer(onSend = onSend)

        // Shown only when the next move is genuinely the reader's. It routes to
        // the booking rather than mutating anything here — one place owns the
        // accept/decline decision.
        val bookingId = state.context.bookingId
        if (state.context.awaitingViewer && bookingId != null) {
            Text(
                "Review request",
                style = AppTheme.type.ctaLabel,
                color = colors.brandInk,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = space.lg)
                    .padding(bottom = space.sm)
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
                    .background(colors.brand)
                    .clickable { onOpenBooking(bookingId) }
                    .padding(vertical = space.md)
                    .semantics { testTag = "chat.funnelCta" },
            )
        }
    }
}

/**
 * "Sat, May 16 · Rooftop · 2 weeks out" — one line of gig context right above
 * the input, where the thing being negotiated is most likely to be needed.
 *
 * Null for an inquiry (nothing has been agreed) and null when no part resolves,
 * so it never renders a dangling separator.
 */
private fun gigCaption(context: ThreadContext, nowMs: Long): String? {
    if (context.bookingId == null) return null
    val parts = listOfNotNull(
        context.dateLabel,
        context.venue,
        ThreadContext.timeUntil(context.startEpochMs, nowMs),
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(ThreadContext.SEPARATOR)
}

// ─────────────────────────────────────────────────────────────────────────────
// Lifecycle helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Re-sync when the app comes back to the foreground.
 *
 * Android suspends the WebSocket while backgrounded, so a chat left open goes
 * quiet until something re-subscribes. The very first ON_RESUME is skipped —
 * the ViewModel's `init` already did that load, and refreshing again on entry
 * would double every open.
 */
@Composable
private fun RefreshOnResume(onResumed: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var seenFirstResume = false
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            if (!seenFirstResume) {
                seenFirstResume = true
                return@LifecycleEventObserver
            }
            onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * A "now" for the relative day labels that stays honest while the screen is open.
 *
 * Keying this to the message list was wrong: a thread sitting on screen across
 * local midnight with no new message never re-read the clock, so yesterday's
 * messages kept rendering as "Today" until something else forced recomposition.
 *
 * Two triggers, because neither alone is sufficient:
 *
 *  - **Midnight timer.** Sleeps exactly until the next local midnight, then
 *    re-reads and sleeps again. The loop wakes once per day, not per frame —
 *    the delay is computed by [ChatTimestamps.millisUntilNextDay], never a poll
 *    interval, so an idle thread costs nothing. `LaunchedEffect` scopes the
 *    coroutine to this composable, so it is cancelled on dispose and cannot
 *    outlive the screen.
 *  - **Resume.** `delay` is not guaranteed to run down while the device dozes, so
 *    a screen left open overnight can come back before its timer fired. Re-read
 *    on ON_RESUME to cover that. `DisposableEffect` removes the observer on
 *    dispose, so the observer never leaks the composable to the Lifecycle.
 *
 * Both write the same state, and re-reading the clock is idempotent, so the two
 * firing together is harmless.
 */
@Composable
private fun rememberDayClock(): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(ChatTimestamps.millisUntilNextDay(System.currentTimeMillis()))
            now = System.currentTimeMillis()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) now = System.currentTimeMillis()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return now
}

/** Multiples of `space.sm` reserved on a bubble's far side (5 × 8dp = 40dp). */
private const val BUBBLE_GUTTER_STEPS = 5

private const val IN_FLIGHT_ALPHA = 0.55f
private const val BOOKING_ROUTE_PREFIX = "booking:"
