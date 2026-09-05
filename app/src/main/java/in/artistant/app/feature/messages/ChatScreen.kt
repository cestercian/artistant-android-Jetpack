package `in`.artistant.app.feature.messages

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.repository.ReportOutcome
import `in`.artistant.app.data.model.MessageKind
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.NarratedStep
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SendingNarration
import `in`.artistant.app.designsystem.component.StepState
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.delay
import java.util.Date

/**
 * One conversation (designs 08, 70, 88).
 *
 * The chrome around the transcript answers the questions a bare bubble list
 * leaves open, and the light design moved each answer to where it belongs: the
 * HEADER says who and which gig (a name alone meant bouncing out to Bookings to
 * find out), a centred status pill at the head of the transcript says what state
 * that gig is in, and decisions are OBJECTS in the thread rather than chrome
 * bolted above the keyboard — a quote is a card with Accept and Counter on it,
 * not a number someone typed.
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
    /** Trust & safety (design 131), from the details sheet and the report form. */
    onOpenSafetyCentre: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    // One clock for the whole screen. The transcript's day separators and the
    // quote card's expiry both describe the same day, so they read the same
    // "now" — and one midnight timer / lifecycle observer serves both instead of
    // a pair per screen (see rememberDayClock).
    val now = rememberDayClock()
    val haptics = rememberHaptics()

    ResumeEffect(onResumed = viewModel::onResumed)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ChatEvent.SendFailed -> haptics.error()
                // Nowhere to go. Accepting a quote creates no booking on this
                // backend (see [ChatEvent.QuoteAccepted]), so there is no
                // "Match confirmed" landing to open and no id to open one with.
                // The buzz confirms the write; the frozen card, which says
                // plainly that nothing is booked yet, is the record.
                ChatEvent.QuoteAccepted -> haptics.success()
            }
        }
    }

    // One buzz per outcome, and only ONE of the three is a success.
    //
    // The success buzz used to fire after every report, including the two where
    // nothing had reached the safety team — a physical confirmation of a
    // delivery that had not happened. `Queued` gets no buzz at all rather than a
    // quieter one: it is neither good news nor bad, and the words on the sheet
    // are what carry it.
    LaunchedEffect(state.report.outcome, state.report.failed) {
        when {
            state.report.failed != null -> haptics.warning()
            state.report.outcome == ReportOutcome.Sent -> haptics.success()
            else -> Unit
        }
    }

    // Accepting takes over the whole screen (design 70): it is a decision with a
    // round trip behind it, and narrating the three phases in place — under a
    // transcript that is still scrollable — would invite a second tap on a card
    // whose write is already in flight.
    val accepting = state.quoteAction as? QuoteAction.Accepting
    if (accepting != null) {
        AcceptingQuote(state = state, phase = accepting.phase, modifier = modifier)
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page)
            // The composer has to clear the keyboard, so the inset goes on the
            // whole screen rather than on the input.
            //
            // `exclude(navigationBars)` rather than a bare `imePadding()`: the
            // tab scaffold already pads its content by the system bars, and the
            // IME inset is measured from the screen edge — so padding by the
            // full IME height would count the navigation bar twice and leave the
            // composer floating a bar's height above the keyboard.
            //
            // This is the ONLY thing that moves for the keyboard. The activity
            // declares `windowSoftInputMode="adjustResize"` precisely so the
            // platform does not also pan the window: with the default
            // (UNSPECIFIED → PAN) the screen moved twice and the header slid off
            // the top. Do not remove that attribute.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        ChatHeader(
            title = state.title,
            subtitle = headerSubtitle(state.context, now),
            onBack = onBack,
            onDetails = viewModel::openDetails,
        )
        HRule()
        if (state.safetyBannerVisible) {
            SafetyNotice(onDismiss = viewModel::dismissSafetyBanner)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && state.messages.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = colors.accentInk) }

                state.error != null && state.messages.isEmpty() -> EmptyState(
                    title = "Couldn't load this conversation",
                    body = state.error,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                    modifier = Modifier.align(Alignment.Center),
                )

                state.messages.isEmpty() && state.quote == null -> EmptyState(
                    title = "No messages yet",
                    body = "Say hello — this is the start of the conversation.",
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> Transcript(
                    state = state,
                    nowMs = now,
                    onBookingClick = onBookingClick,
                    onRetry = viewModel::retryFailedMessage,
                    onLoadOlder = viewModel::loadOlder,
                    onAcceptQuote = viewModel::acceptQuote,
                    onCounterQuote = viewModel::openCounter,
                )
            }
        }

        ComposerBar(
            onSend = { body ->
                // Light tap on send, matching iOS. Fired on the tap, not on the
                // delivery: the optimistic bubble is already on screen, and the
                // failure path has its own buzz above.
                haptics.tap()
                viewModel.send(body)
            },
            above = {
                ComposerNotices(
                    state = state,
                    onRetryRefresh = viewModel::refresh,
                    onOpenBooking = onBookingClick,
                    onDismissQuoteError = viewModel::dismissQuoteError,
                )
            },
        )
    }

    state.quote?.let { quote ->
        if (state.countering) {
            CounterQuoteSheet(
                currentAmountInr = quote.amountInr,
                onSubmit = viewModel::counterQuote,
                onDismiss = viewModel::dismissCounter,
            )
        }
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
            blocked = state.blocked,
            canBlock = state.counterpartId != null,
            report = state.report,
            // A mute/block that didn't land is reported HERE, on the sheet the
            // tap came from, rather than in the transcript's own error slot —
            // that one speaks for the conversation failing to load and offers to
            // reload it, which is neither true nor useful for a failed toggle.
            actionError = state.actionError,
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
            // Also stays open: the row flips to "Unblock", which is the only
            // affordance for undoing an accidental block — the conversation is
            // by then out of the inbox, so closing the sheet would strand it.
            onToggleBlock = viewModel::toggleBlocked,
            // Closed first, then pushed: a bottom sheet cannot stay up over a
            // destination, and leaving it in state means finding it still open
            // on the way back from a screen the reader has finished with.
            onOpenSafetyCentre = {
                viewModel.dismissDetails()
                onOpenSafetyCentre()
            },
            onMarkUnread = {
                viewModel.markUnread()
                viewModel.dismissDetails()
            },
            onReport = viewModel::reportConversation,
            onRetryReport = viewModel::retryReport,
            onDiscardReport = viewModel::discardFailedReport,
            onDismiss = viewModel::dismissDetails,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Chrome
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Back · avatar · name over the gig line · Details (design 88).
 *
 * The name is left-aligned against the avatar rather than centred in the bar.
 * Centring is for a screen that is one thing; this header is a person plus what
 * you are talking to them about, and the two have to read as a stack.
 *
 * "Details" is a word, not a pill or a bare avatar. The avatar exposed none of
 * the thread's context and for an artist viewer it was inert — their counterpart
 * is a client with no profile to open, so tapping their own face did nothing.
 */
@Composable
private fun ChatHeader(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    onDetails: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = dimens.component.gutter, vertical = dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        IconCircle(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            size = dimens.component.iconCircleSm,
        )
        Avatar(name = title, size = dimens.component.iconCircleSm)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "Details",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
            color = colors.accentInk,
            modifier = Modifier
                .clip(RoundedCornerShape(dimens.radii.sm))
                .clickable(onClick = onDetails)
                .padding(horizontal = dimens.space.sm, vertical = dimens.space.md)
                .semantics { testTag = "chat.details" },
        )
    }
}

/**
 * "Sangeet · Sat 12 Oct · 12 days out" — the gig, under the name.
 *
 * Null for an inquiry (nothing has been agreed) and null when no part resolves,
 * so it never renders a dangling separator.
 */
private fun headerSubtitle(context: ThreadContext, nowMs: Long): String? {
    if (context.bookingId == null) return null
    val parts = listOfNotNull(
        context.dateLabel,
        context.venue,
        ThreadContext.timeUntil(context.startEpochMs, nowMs),
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(ThreadContext.SEPARATOR)
}

/**
 * The trust notice (design 88).
 *
 * It says exactly two true things: keep the conversation here, and report
 * anything that feels wrong. It deliberately does NOT claim messages are
 * "analysed for safety" — no such job exists, and a safety promise the product
 * doesn't keep is worse than no banner. Quiet surface, not a warning colour:
 * this is reassurance, and dressing it in amber would read as an accusation
 * against whoever you are talking to.
 */
@Composable
private fun SafetyNotice(onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = dimens.component.gutter,
                end = dimens.component.gutter,
                top = dimens.space.md,
            )
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.surface3)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = colors.ink4,
            modifier = Modifier.size(dimens.size.iconLg),
        )
        Text(
            "Keep chats on Artistant. Don't move payments or contact off-platform — " +
                "report anything that feels off.",
            style = AppTheme.type.caption,
            color = colors.ink2,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.Close,
            contentDescription = "Dismiss safety notice",
            tint = colors.ink4,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .size(dimens.size.iconLg)
                .semantics { testTag = "chat.safetyBanner.dismiss" },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Transcript
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun Transcript(
    state: ChatUiState,
    nowMs: Long,
    onBookingClick: (String) -> Unit,
    onRetry: (String) -> Unit,
    onLoadOlder: () -> Unit,
    onAcceptQuote: () -> Unit,
    onCounterQuote: () -> Unit,
) {
    val dimens = AppTheme.dimens
    val messages = state.messages
    val listState = rememberLazyListState()

    // Grouping is pure and only changes when the transcript does, so it is
    // computed once per message-list identity rather than per recomposition.
    val dayStarts = remember(messages) { ChatTimestamps.dayStartIds(messages) }
    val incomingStarts = remember(messages) { ChatTimestamps.incomingRunStartIds(messages) }
    val outgoingEnds = remember(messages) { ChatTimestamps.outgoingRunEndIds(messages) }
    // The platform formatters honour the user's locale AND their 24-hour setting.
    // Never reintroduce a fixed "h:mm a" pattern here.
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val dateFormat = remember(context) { DateFormat.getMediumDateFormat(context) }

    FollowTail(listState = listState, messages = messages)
    LoadOlderAtTop(listState = listState, onLoadOlder = onLoadOlder)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.component.gutter)
            .semantics { testTag = "chat.transcript" },
        state = listState,
        // Bottom-anchored. A conversation grows upward from the composer, so a
        // two-message thread should sit just above where you type, not pinned
        // under the safety banner with a screenful of nothing below it. Once the
        // transcript is taller than the viewport the alignment is a no-op and
        // this behaves like any other list.
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm, Alignment.Bottom),
    ) {
        // The gig's state, centred, at the head of the transcript (design 88).
        // It replaces the old always-visible strip: the same fact, read once
        // where the conversation starts rather than parked over it forever.
        state.context.bookingId?.let { bookingId ->
            item(key = "chat.status") {
                StatusChip(
                    label = listOfNotNull(state.context.statusLabel, state.context.dateLabel)
                        .joinToString(ThreadContext.SEPARATOR),
                    onClick = { onBookingClick(bookingId) },
                )
            }
        }

        items(messages, key = { it.id }) { message ->
            // Every decoration renders inside its message's item so the list's
            // item count stays equal to the message count.
            Column(Modifier.fillMaxWidth()) {
                if (message.id in dayStarts) {
                    DaySeparatorRow(
                        sentAtEpochMs = message.sentAtEpochMs,
                        nowMs = nowMs,
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
                if (message.id in outgoingEnds &&
                    message.delivery == MessageDelivery.Sent &&
                    message.id != state.lastReadOwnMessageId
                ) {
                    TrailingCaption(timeFormat.format(Date(message.sentAtEpochMs)))
                }
                if (message.id == state.lastReadOwnMessageId) {
                    TrailingCaption(
                        text = "Read by ${state.title}",
                        readable = "Read by ${state.title}",
                        tag = "chat.readReceipt",
                    )
                }
            }
        }

        // The quote lives at the tail: it is the live decision, and burying it
        // in date order would put the most actionable thing in the thread behind
        // a scroll.
        state.quote?.let { quote ->
            item(key = "chat.quote") {
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (quote.viewerDecides) {
                        Alignment.CenterStart
                    } else {
                        Alignment.CenterEnd
                    },
                ) {
                    QuoteCard(
                        quote = quote,
                        validUntil = quote.expiresAtEpochMs?.let { expiry ->
                            "${dateFormat.format(Date(expiry))}, ${timeFormat.format(Date(expiry))}"
                        },
                        counterpartName = state.title,
                        onAccept = onAcceptQuote,
                        onCounter = onCounterQuote,
                    )
                }
            }
        }
    }
}

/** The centred state capsule at the head of a thread (design 88). */
@Composable
private fun StatusChip(label: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(Modifier.fillMaxWidth().padding(vertical = dimens.space.sm), Alignment.Center) {
        Text(
            label,
            style = AppTheme.type.badge,
            color = colors.ink2,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.surface2)
                .clickable(onClick = onClick)
                .padding(horizontal = dimens.space.md, vertical = dimens.space.sm)
                .semantics { testTag = "chat.contextStrip" },
        )
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
 * pressing send is an unambiguous statement about where you want to be — but only
 * when the NEWEST message actually changed, so a scroll-back page arriving at the
 * head of the list leaves the reader where they are.
 */
@Composable
private fun FollowTail(
    listState: androidx.compose.foundation.lazy.LazyListState,
    messages: List<Message>,
) {
    // Held in a plain holder, read only inside effects: nothing composes against
    // it, so tracking scroll position costs no recomposition.
    val followTail = remember { mutableStateOf(true) }
    // The newest message the last auto-scroll was for. Scroll-back grows the list
    // at the HEAD, which leaves this unchanged — and prepended history must never
    // move the viewport, least of all when the newest message is the viewer's own
    // and the own-send rule below would otherwise yank them out of the history
    // they scrolled up to read.
    val tailId = remember { mutableStateOf<String?>(null) }

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
        val newest = messages.last().id
        val tailChanged = newest != tailId.value
        tailId.value = newest
        if (!tailChanged) return@LaunchedEffect
        val ownSend = messages.last().isMine
        if (ownSend) followTail.value = true
        if (!followTail.value) return@LaunchedEffect
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex < 0) return@LaunchedEffect
        // First paint lands directly at the bottom; later arrivals animate, so
        // the movement reads as "a message came in" rather than a jump cut.
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(lastIndex)
        } else {
            listState.animateScrollToItem(lastIndex)
        }
    }
}

/**
 * Walk back through history when the reader reaches the top.
 *
 * The transcript only ever loads the newest page, so without this the messages
 * before it are unreachable — scrolling up simply stopped at the 50th-newest.
 * Fires when a scroll SETTLES with the oldest loaded message on screen: the same
 * "only decide when the gesture ends" discipline [FollowTail] uses, and what
 * keeps a fresh open — which lands pinned at the bottom — from fetching a page
 * nobody asked for. A transcript that fits on screen has no scroll-back to do
 * and is skipped. Repeat ticks are cheap: the ViewModel owns the one-at-a-time
 * and reached-the-beginning guards ([ChatViewModel.loadOlder]).
 */
@Composable
private fun LoadOlderAtTop(
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLoadOlder: () -> Unit,
) {
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) return@collect
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.size >= info.totalItemsCount) return@collect
            if (listState.firstVisibleItemIndex == 0) onLoadOlder()
        }
    }
}

/**
 * One message (design 88 — "three message states visible").
 *
 * Sent, read and failed-with-retry are three different objects, not one object
 * with three opacities. A failed bubble is drawn in `surface` behind a danger
 * rim with its text stepped back to `ink2` — it is a draft the server never took
 * — and the retry underneath it is a real tap target with the only `danger`
 * colour in the thread on it. It is deliberately not a toast: a toast for a
 * failed send disappears while the message it belongs to stays on screen looking
 * fine.
 */
@Composable
private fun MessageRow(
    message: Message,
    onBookingClick: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    if (message.kind == MessageKind.System) {
        SystemRow(message = message, onBookingClick = onBookingClick)
        return
    }

    val mine = message.isMine
    val failed = mine && message.delivery == MessageDelivery.Failed
    val sending = mine && message.delivery == MessageDelivery.Sending
    // Asymmetric corners: the corner nearest the speaker is clipped, which is
    // what makes a run of bubbles read as coming from one side.
    val shape = RoundedCornerShape(
        topStart = dimens.radii.lg,
        topEnd = dimens.radii.lg,
        bottomEnd = if (mine) dimens.radii.sm else dimens.radii.lg,
        bottomStart = if (mine) dimens.radii.lg else dimens.radii.sm,
    )

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        Text(
            message.body,
            style = AppTheme.type.body,
            color = when {
                failed -> colors.ink2
                mine -> colors.onAccent
                else -> colors.ink
            },
            modifier = Modifier
                // A gutter on the far side rather than a width fraction: it keeps
                // the bubble hugging its own content while guaranteeing the
                // opposite edge always shows, which is what makes a thread read
                // as two columns.
                .padding(start = if (mine) dimens.space.xxl else dimens.space.xs)
                .padding(end = if (mine) dimens.space.xs else dimens.space.xxl)
                .clip(shape)
                .background(
                    when {
                        failed -> colors.surface
                        // An in-flight bubble is the accent stepped back to its
                        // own tint rather than a dimmed copy: it is a real
                        // state, so it gets a real fill (screen 118's rule).
                        sending -> colors.brandSoft
                        mine -> colors.accent
                        else -> colors.surface3
                    },
                )
                .then(
                    if (failed) {
                        Modifier.border(
                            dimens.component.focusStroke,
                            colors.danger.copy(alpha = FAILED_RIM),
                            shape,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = dimens.space.md, vertical = dimens.space.md),
        )
        if (failed) {
            Row(
                Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = dimens.space.xs, vertical = dimens.space.xs)
                    .semantics {
                        testTag = "chat.retry"
                        contentDescription = "Not sent. Tap to retry."
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
            ) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
                Text(
                    "Not sent · Tap to retry",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.danger,
                )
            }
        }
    }
}

/**
 * A state change, not a person: centred caption text with no bubble chrome.
 *
 * When the row carries an `action_route` it gets exactly one action, routed
 * through the same booking destination the status chip uses — so a system notice
 * and the chip above it can never send you to two different places. An
 * unrecognised or malformed route renders as a plain notice rather than a
 * dangling link.
 */
@Composable
private fun SystemRow(message: Message, onBookingClick: (String) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val bookingId = message.actionRoute
        ?.removePrefix(BOOKING_ROUTE_PREFIX)
        ?.takeIf { it.isNotBlank() && it != message.actionRoute }

    Column(
        Modifier.fillMaxWidth().padding(vertical = dimens.space.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message.body,
            style = AppTheme.type.caption,
            color = colors.ink4,
            textAlign = TextAlign.Center,
        )
        bookingId?.let {
            Text(
                "View booking",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accentInk,
                modifier = Modifier
                    .padding(top = dimens.space.xs)
                    .clip(CircleShape)
                    .clickable { onBookingClick(it) }
                    .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs)
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
    val label = when (ChatTimestamps.daySeparator(sentAtEpochMs, nowMs)) {
        DaySeparator.Today -> "Today"
        DaySeparator.Yesterday -> "Yesterday"
        DaySeparator.Earlier -> dateFormat.format(Date(sentAtEpochMs))
    }
    Text(
        label,
        style = AppTheme.type.caption,
        color = AppTheme.colors.ink4,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.space.sm),
    )
}

/**
 * "Asha Rao · Client · 9:14 am" above the first bubble of an incoming run.
 * Marked decorative for a11y: the bubble itself carries the readable content, so
 * a screen reader shouldn't announce the attribution twice.
 */
@Composable
private fun SenderCaption(name: String, role: String, time: String) {
    Text(
        "$name · $role · $time",
        style = AppTheme.type.caption,
        color = AppTheme.colors.ink4,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppTheme.dimens.space.xs)
            .clearAndSetSemantics {},
    )
}

/**
 * Trailing-aligned caption under an outgoing run — the bare send time, or the
 * read receipt (design 88's "Read by Aarav"). Only the receipt is announced; the
 * time is already implied by the day rule above and would otherwise be read out
 * after every burst.
 */
@Composable
private fun TrailingCaption(text: String, readable: String? = null, tag: String? = null) {
    Text(
        text,
        style = AppTheme.type.caption,
        color = AppTheme.colors.ink4,
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
 * What has to be said between the transcript and the input.
 *
 * All of it rides inside the composer bar so it stays above the keyboard: a
 * failed-refresh strip under the keyboard reports nothing, and a CTA under the
 * keyboard cannot be pressed.
 */
@Composable
private fun ComposerNotices(
    state: ChatUiState,
    onRetryRefresh: () -> Unit,
    onOpenBooking: (String) -> Unit,
    onDismissQuoteError: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    (state.quoteAction as? QuoteAction.Failed)?.let { failure ->
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onDismissQuoteError)
                .padding(horizontal = dimens.component.gutter, vertical = dimens.space.sm)
                .semantics { testTag = "chat.quoteError" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = colors.danger,
                modifier = Modifier.size(dimens.size.iconMd),
            )
            Text(failure.message, style = AppTheme.type.caption, color = colors.danger)
        }
    }

    // A failed SEND already carries its own retry on the bubble, and that is the
    // more precise report — so the strip only speaks for the failures nothing
    // else is reporting.
    val hasFailedSend = state.messages.any { it.delivery == MessageDelivery.Failed }
    if (state.error != null && state.messages.isNotEmpty() && !hasFailedSend) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.component.gutter, vertical = dimens.space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            Text(
                "Couldn't refresh this conversation.",
                style = AppTheme.type.caption,
                color = colors.ink2,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Retry",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.accentInk,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onRetryRefresh)
                    .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs),
            )
        }
    }

    // Shown only when the next move is genuinely the reader's AND the thread has
    // no quote card offering the same decision. It routes to the booking rather
    // than mutating anything here — one place owns the accept/decline decision.
    val bookingId = state.context.bookingId
    if (state.context.awaitingViewer && bookingId != null && state.quote?.actionable != true) {
        PrimaryButton(
            text = "Review request",
            onClick = { onOpenBooking(bookingId) },
            fullWidth = true,
            modifier = Modifier
                .padding(horizontal = dimens.component.gutter, vertical = dimens.space.sm)
                .semantics { testTag = "chat.funnelCta" },
        )
    }
}

/**
 * Accepting a quote, narrated (design 70).
 *
 * Three phases, each waiting on real work — see [ChatViewModel.acceptQuote]. The
 * tail says what happens next without claiming a delivery the app cannot
 * confirm.
 */
@Composable
private fun AcceptingQuote(state: ChatUiState, phase: QuotePhase, modifier: Modifier = Modifier) {
    val amount = state.quote?.amountInr
    SendingNarration(
        modifier = modifier,
        title = "Accepting the quote…",
        body = listOfNotNull(
            amount?.let { "Locking " + `in`.artistant.app.common.util.formatInr(it) },
            state.title.takeIf { it.isNotBlank() }?.let { "with $it" },
        ).joinToString(" ").ifBlank { "Confirming the terms." } + ".",
        steps = listOf(
            NarratedStep("Terms locked", phase.stateFor(QuotePhase.Locking)),
            NarratedStep("Saving your answer", phase.stateFor(QuotePhase.Saving)),
            // Never "Opening the booking": accepting opens none. The last phase
            // is the re-read that turns the card into the record.
            NarratedStep("Freezing the terms", phase.stateFor(QuotePhase.Confirming)),
        ),
        tail = "The agreed terms stay in this conversation. Nothing is booked yet.",
    )
}

/** Where [step] sits relative to the phase currently running. */
private fun QuotePhase.stateFor(step: QuotePhase): StepState = when {
    step.ordinal < ordinal -> StepState.Done
    step.ordinal == ordinal -> StepState.Running
    else -> StepState.Pending
}

// ─────────────────────────────────────────────────────────────────────────────
// Lifecycle helpers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Every ON_RESUME this destination sees, handed to [onResumed] unfiltered.
 *
 * Two things bring a screen back and both matter here: the app returning to the
 * foreground (Android suspends the WebSocket while it is away, so a chat left
 * open goes quiet until something re-subscribes) and a destination pushed on
 * top of it going away (the inbox's rows go stale the moment a chat is read
 * over them — see [MessagesScreen]).
 *
 * Deliberately dumb: it does NOT try to swallow the resume that arrives with
 * the first paint, even though every caller wants that resume ignored because
 * its `init` has already loaded. That latch belongs to the ViewModel
 * ([ChatViewModel.onResumed], [MessagesViewModel.onResumed]) because the
 * ViewModel is what SURVIVES. Navigation composes each destination through an
 * `AnimatedContent`, so pushing on top of this screen disposes its content once
 * the transition ends and re-composes it on the way back; a latch held in this
 * composable would reset there and then swallow the single ON_RESUME the
 * restored entry delivers — the one resume that most needs a re-sync. The
 * retained ViewModel is the only thing on this path that can tell "first ever"
 * from "back again".
 */
@Composable
internal fun ResumeEffect(onResumed: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResumed()
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

/** The danger colour softened to a rim (design 88 draws `rgba(164,64,44,.4)`). */
private const val FAILED_RIM = 0.4f

private const val BOOKING_ROUTE_PREFIX = "booking:"
