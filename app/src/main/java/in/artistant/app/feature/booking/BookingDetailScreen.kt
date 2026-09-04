package `in`.artistant.app.feature.booking

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedEndEpochMs
import `in`.artistant.app.data.model.resolvedStartEpochMs
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.AccentNoteCard
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.BottomActionBar
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.DetailHeader
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.EventStep
import `in`.artistant.app.designsystem.component.EventStepState
import `in`.artistant.app.designsystem.component.EventTimeline
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.MediaSlot
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.component.SkeletonBlock
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.component.hairlineTop
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.bookings.compactDate
import `in`.artistant.app.feature.bookings.daysUntilGig
import `in`.artistant.app.feature.messages.ChatOpenViewModel
import kotlinx.coroutines.delay

/**
 * Booking detail — five pages behind one route (screens 18, 83, 95, 96, 97) plus
 * the two the flow can end on: not found (84) and the cancel flow (117 → 52).
 *
 * The variant is not a tint on a shared layout, it is a different page, because
 * each state answers a different question — see [BookingDetailVariant]. What all
 * of them share is the top: a record header naming the booking and where it
 * stands, and an identity card saying who the other side is.
 *
 * Every fork keys off [isArtistViewer], which is which SIDE of the booking the
 * viewer is on, not their account role. See [BookingViewer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    isArtistViewer: Boolean,
    onBack: () -> Unit,
    onOpenChat: (threadId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    /** "Book again" on a cancelled booking — back to the artist's profile. */
    onBookAgain: (artistId: String) -> Unit = {},
    /** Where support lives. The disputed page's only live action. */
    onOpenSupport: () -> Unit = {},
    viewModel: BookingDetailViewModel = hiltViewModel(),
    chatOpen: ChatOpenViewModel = hiltViewModel(),
    // Resolved HERE, not inside the sheet, and it is the same instance the sheet
    // gets: both are `hiltViewModel()` against this destination. The host has to
    // own it because the write outlives the sheet.
    reviewVm: ReviewSheetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val review by reviewVm.state.collectAsStateWithLifecycle()
    val openingChat by chatOpen.opening.collectAsStateWithLifecycle()
    val chatError by chatOpen.error.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val booking = state.booking
    val viewer = viewerOf(isArtistViewer)
    val haptics = rememberHaptics()

    var showReview by remember { mutableStateOf(false) }
    var showTechRider by remember { mutableStateOf(false) }
    var cancelStage by remember { mutableStateOf<CancelStage?>(null) }
    var cancelReason by remember { mutableStateOf<CancelReason?>(null) }

    // Outside the sheet on purpose. The insert runs on the ViewModel's scope so a
    // scrim tap mid-submit no longer cancels it — which means it can also land
    // when the sheet is already gone. An effect inside the sheet would not be in
    // composition to hear it: the row would keep offering "Leave a review", and
    // reopening would flash shut on the leftover flag.
    LaunchedEffect(review.submitted) {
        if (review.submitted) {
            haptics.success()
            reviewVm.consumeSubmitted()
            showReview = false
            viewModel.refresh()
        }
    }
    // The clipboard row acknowledges itself by swapping its own label rather than
    // raising a toast: from Android 13 the system already shows a copy
    // confirmation, and stacking ours on top of it reads as a double-fire.
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_LABEL_MS)
            copied = false
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.page),
    ) {
        when {
            state.isLoading && booking == null -> DetailSkeleton(onBack)

            booking == null -> BookingNotFound(
                failure = state.loadFailure ?: BookingLoadFailure.NotFound,
                onBack = onBack,
                onRetry = viewModel::refresh,
            )

            cancelStage != null -> CancelFlow(
                stage = requireNotNull(cancelStage),
                viewer = viewer,
                counterparty = viewModel.counterpartyName(isArtistViewer),
                reason = cancelReason,
                daysBefore = daysUntilGig(booking.resolvedStartEpochMs(), System.currentTimeMillis()),
                isDecline = viewer == BookingViewer.Artist &&
                    booking.status == BookingStatus.PendingConfirm,
                onPickReason = { cancelReason = it },
                onContinue = { cancelStage = CancelStage.Consequences },
                onBack = {
                    cancelStage =
                        if (cancelStage == CancelStage.Consequences) CancelStage.Reason else null
                },
                onKeep = { cancelStage = null },
                onConfirm = {
                    // Warning at the confirm: cancelling is a deliberate act with
                    // a cost to the other party, not a failure. The result gets no
                    // buzz of its own.
                    haptics.warning()
                    cancelStage = null
                    viewModel.cancelBooking(viewer, cancelReason?.label)
                },
            )

            else -> {
                val counterparty = viewModel.counterpartyName(isArtistViewer)
                val city = state.artist?.city
                val address = venueAddress(booking.venue, city)
                val variant = variantFor(booking.status)

                RevealOnAppear {
                    BookingDetailBody(
                        booking = booking,
                        variant = variant,
                        viewer = viewer,
                        counterparty = counterparty,
                        packageName = viewModel.packageName(),
                        coverUrl = if (isArtistViewer) null else state.artist?.coverUrl,
                        address = address,
                        copied = copied,
                        acting = state.actingAction,
                        openingChat = openingChat,
                        error = state.actionError ?: chatError,
                        onDismissError = {
                            viewModel.dismissActionError()
                            chatOpen.dismissError()
                        },
                        onBack = onBack,
                        onMessage = { chatOpen.open(booking.artistId, booking.id, onOpenChat) },
                        onAccept = viewModel::acceptRequest,
                        onCancel = {
                            cancelReason = null
                            cancelStage = CancelStage.Reason
                        },
                        onReview = { showReview = true },
                        onTechRider = { showTechRider = true },
                        onBookAgain = { onBookAgain(booking.artistId) },
                        onOpenSupport = onOpenSupport,
                        onAddToCalendar = {
                            launchAddToCalendar(context, booking)?.let(viewModel::reportActionError)
                        },
                        onOpenMaps = {
                            openMaps(context, address)?.let(viewModel::reportActionError)
                        },
                        onCopyAddress = {
                            clipboard.setText(AnnotatedString(address))
                            haptics.success()
                            copied = true
                        },
                        onShare = {
                            shareGig(context, shareGigText(counterparty, booking, city))
                                ?.let(viewModel::reportActionError)
                        },
                        onUpdateApp = {
                            openPlayStore(context)?.let(viewModel::reportActionError)
                        },
                    )
                }

                if (showTechRider) {
                    TechRiderSheet(
                        artistName = counterparty,
                        items = state.artist?.tech.orEmpty(),
                        onDismiss = { showTechRider = false },
                    )
                }

                if (showReview) {
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { showReview = false },
                        sheetState = sheetState,
                        containerColor = Color.Transparent,
                        dragHandle = null,
                    ) {
                        // No repository handed down: the sheet owns a ViewModel of
                        // its own, resolved against THIS destination, so a scrim
                        // tap mid-submit no longer cancels the write.
                        ReviewSheet(
                            bookingId = booking.id,
                            // Screen 98: a name we could not load never blocks the
                            // review — the booking reference carries the identity
                            // instead, and the sheet says so.
                            artistName = state.artistName.takeIf { it.isNotBlank() },
                            subtitle = reviewSubtitleFor(booking, state.artist?.category),
                            onDismiss = { showReview = false },
                            viewModel = reviewVm,
                        )
                    }
                }
            }
        }
    }
}

/** How long the Copy row wears its "Copied" label before reverting. */
private const val COPIED_LABEL_MS = 1_600L

/** How far a terminal page's picture and name step back. */
private const val DIMMED = 0.6f

// ─────────────────────────────────────────────────────────────────────────────
// The page
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BookingDetailBody(
    booking: Booking,
    variant: BookingDetailVariant,
    viewer: BookingViewer,
    counterparty: String,
    packageName: String?,
    coverUrl: String?,
    address: String,
    copied: Boolean,
    acting: BookingAction?,
    openingChat: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
    onMessage: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onReview: () -> Unit,
    onTechRider: () -> Unit,
    onBookAgain: () -> Unit,
    onOpenSupport: () -> Unit,
    onAddToCalendar: () -> Unit,
    onOpenMaps: () -> Unit,
    onCopyAddress: () -> Unit,
    onShare: () -> Unit,
    onUpdateApp: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val nowMs = remember(booking.id, booking.status) { System.currentTimeMillis() }

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
        ) {
            DetailHeader(
                title = bookingReference(booking.id),
                subtitle = headerStatusLine(booking, variant, nowMs),
                onBack = onBack,
                trailing = if (variant == BookingDetailVariant.Confirmed) {
                    {
                        IconCircle(
                            icon = Icons.AutoMirrored.Outlined.Message,
                            contentDescription = if (openingChat) "Opening chat" else "Message",
                            onClick = onMessage,
                            size = dimens.component.iconCircleSm,
                        )
                    }
                } else {
                    null
                },
            )

            // A failed accept, or a thread that would not open, is still true next
            // time this screen is opened — so it states itself here rather than as
            // a transient the user can miss.
            error?.let {
                Banner(
                    title = it,
                    tone = BannerTone.Failure,
                    actionLabel = "Dismiss",
                    onAction = onDismissError,
                )
            }

            // The page's own explanation, above everything it explains.
            when (variant) {
                BookingDetailVariant.Awaiting -> Banner(
                    title = "Waiting on $counterparty",
                    // No response-time promise: nothing in the backend enforces
                    // one, and checkout one screen earlier deliberately refuses to
                    // make it. The notification IS real — a push fires when a
                    // booking is confirmed — so that is what is promised.
                    detail = "We'll let you know the moment they accept.",
                    tone = BannerTone.Attention,
                )

                BookingDetailVariant.Cancelled -> Banner(
                    title = cancelledByLine(booking, viewer, counterparty),
                    detail = cancelDetailLine(booking),
                    tone = BannerTone.Failure,
                )

                BookingDetailVariant.Disputed -> Banner(
                    title = "Under review by Artistant",
                    detail = "Both sides have been asked for their account. " +
                        "Reviews and scoring are paused until it closes.",
                    tone = BannerTone.Failure,
                )

                BookingDetailVariant.ReadOnly -> Banner(
                    title = "This app version doesn't know this status",
                    detail = "It is shown read-only. Nothing here can be actioned — " +
                        "updating the app will restore the controls.",
                    tone = BannerTone.Attention,
                )

                BookingDetailVariant.Confirmed -> Unit
            }

            IdentityCard(
                name = counterparty,
                line = identityLine(booking, variant, packageName),
                statusLabel = booking.status.label,
                statusTone = bookingPillTone(booking.status),
                coverUrl = coverUrl,
                dimmed = variant == BookingDetailVariant.Cancelled ||
                    variant == BookingDetailVariant.ReadOnly,
            )

            when (variant) {
                BookingDetailVariant.Confirmed -> ConfirmedBody(
                    booking = booking,
                    nowMs = nowMs,
                    address = address,
                    copied = copied,
                    viewer = viewer,
                    onOpenMaps = onOpenMaps,
                    onCopyAddress = onCopyAddress,
                    onAddToCalendar = onAddToCalendar,
                    onTechRider = onTechRider,
                    onShare = onShare,
                    onCancel = onCancel,
                )

                BookingDetailVariant.Awaiting -> AwaitingBody(
                    booking = booking,
                    nowMs = nowMs,
                    packageName = packageName,
                )

                BookingDetailVariant.Cancelled -> CancelledBody(
                    booking = booking,
                    packageName = packageName,
                    viewer = viewer,
                    openingChat = openingChat,
                    onMessage = onMessage,
                    onBookAgain = onBookAgain,
                )

                BookingDetailVariant.Disputed -> DisputedBody()

                BookingDetailVariant.ReadOnly -> ReadOnlyBody(
                    booking = booking,
                    packageName = packageName,
                )
            }

            Spacer(Modifier.height(dimens.space.lg))
        }

        BookingDock(
            variant = variant,
            booking = booking,
            viewer = viewer,
            counterparty = counterparty,
            acting = acting,
            openingChat = openingChat,
            onAccept = onAccept,
            onCancel = onCancel,
            onMessage = onMessage,
            onReview = onReview,
            onOpenSupport = onOpenSupport,
            onUpdateApp = onUpdateApp,
        )
    }
}

/** The header's second line — a different fact per variant. */
private fun headerStatusLine(
    booking: Booking,
    variant: BookingDetailVariant,
    nowMs: Long,
): String? = when (variant) {
    BookingDetailVariant.Awaiting ->
        relativeSince(booking.createdAtEpochMs, nowMs)?.let { "Sent $it" } ?: "Sent"
    BookingDetailVariant.Confirmed ->
        listOf(booking.status.label, compactDate(booking.date))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    BookingDetailVariant.Cancelled -> cancelledOnLabel(booking)
    BookingDetailVariant.Disputed -> "Disputed"
    BookingDetailVariant.ReadOnly -> "Status not recognised"
}

/** The identity card's second line — the tier, or where and when. */
private fun identityLine(
    booking: Booking,
    variant: BookingDetailVariant,
    packageName: String?,
): String = when (variant) {
    BookingDetailVariant.Confirmed, BookingDetailVariant.Cancelled ->
        packageName?.takeIf { it.isNotBlank() }
            ?: listOf(compactDate(booking.date), booking.venue).filter { it.isNotBlank() }
                .joinToString(" · ")
    else -> listOf(compactDate(booking.date), booking.venue)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/**
 * "2 Aug · reason" under the cancelled banner.
 *
 * The reason is printed because it is on the row and BOTH parties can read it —
 * `bookings_select_participants` grants the whole row to client and artist alike.
 * The design's line here ("the artist only sees that you cancelled") describes a
 * privacy this schema does not implement, and printing it would be a promise the
 * database breaks.
 */
private fun cancelDetailLine(booking: Booking): String? =
    booking.cancelReason?.takeIf { it.isNotBlank() }?.let { "Reason given: $it" }

private fun bookingPillTone(status: BookingStatus): PillTone = when (status) {
    BookingStatus.Confirmed -> PillTone.BrandSolid
    BookingStatus.Completed -> PillTone.Good
    BookingStatus.PendingConfirm -> PillTone.Neutral
    BookingStatus.Cancelled, BookingStatus.Disputed -> PillTone.Hot
    BookingStatus.Unknown -> PillTone.Warm
}

/** Who the other side is, on every variant. */
@Composable
private fun IdentityCard(
    name: String,
    line: String,
    statusLabel: String,
    statusTone: PillTone,
    coverUrl: String?,
    dimmed: Boolean,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .padding(dimens.space.lg)
            .alpha(if (dimmed) DIMMED else 1f),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (coverUrl.isNullOrBlank()) {
            // A flat initial disc, not the hashed-gradient Avatar: this card sits
            // on a page whose one accent is spent elsewhere, and a saturated
            // gradient disc would out-rank the status pill beside it.
            Box(
                Modifier
                    .size(dimens.size.avatarLg)
                    .clip(CircleShape)
                    .background(colors.hairline),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().split(Regex("\\s+")).take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .joinToString("")
                        .ifEmpty { "?" },
                    style = AppTheme.type.sectionTitle,
                    color = colors.ink2,
                )
            }
        } else {
            MediaSlot(
                modifier = Modifier.size(dimens.size.avatarLg),
                radius = dimens.radii.lg,
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = AppTheme.type.cardTitle,
                color = if (dimmed) colors.ink2 else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.isNotBlank()) {
                Text(
                    line,
                    style = AppTheme.type.subtitle,
                    color = colors.ink4,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        Pill(statusLabel, tone = statusTone)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Variant bodies
// ─────────────────────────────────────────────────────────────────────────────

/** Screen 18 — the day, hour by hour, then the fee, then what you can do. */
@Composable
private fun ConfirmedBody(
    booking: Booking,
    nowMs: Long,
    address: String,
    copied: Boolean,
    viewer: BookingViewer,
    onOpenMaps: () -> Unit,
    onCopyAddress: () -> Unit,
    onAddToCalendar: () -> Unit,
    onTechRider: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val moments = remember(booking.id, nowMs) { runOfShow(booking, nowMs) }

    if (moments.isNotEmpty()) {
        SectionTitle("Run of show")
        EventTimeline(steps = moments.map { it.toStep() })
    }

    if (BookingActions.showsGettingThere(booking.status)) {
        SectionTitle("Getting there")
        Column(verticalArrangement = Arrangement.spacedBy(dimens.space.xs)) {
            Text(address, style = AppTheme.type.body, color = colors.ink)
            booking.venueNotes?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = AppTheme.type.caption, color = colors.ink4)
            }
        }
        Column {
            HRule()
            ActionRow(Icons.Outlined.Place, "Open in Maps", onClick = onOpenMaps)
            HRule()
            ActionRow(
                Icons.Filled.ContentCopy,
                if (copied) "Copied" else "Copy address",
                tint = if (copied) colors.accentInk else colors.ink,
                onClick = onCopyAddress,
            )
            HRule()
            ActionRow(Icons.Filled.CalendarMonth, "Add to calendar", onClick = onAddToCalendar)
            HRule()
        }
    }

    SectionTitle("The fee")
    AccentNoteCard {
        FeeRow("Agreed fee", formatInr(booking.fee))
        Spacer(Modifier.height(dimens.space.sm))
        FeeRow("Settled", "Directly, after the set")
        Text(
            "Artistant holds no money in this version — this is the number you and the " +
                "artist agreed, and the record of it.",
            style = AppTheme.type.caption,
            color = colors.ink2,
            modifier = Modifier.padding(top = dimens.space.md),
        )
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        FlatAction("Tech rider", onTechRider, Modifier.weight(1f))
        FlatAction("Share", onShare, Modifier.weight(1f))
        // Only while the booking is still actionable: a completed gig has nothing
        // to withdraw from, and offering it would be a button the server refuses.
        if (BookingAction.Cancel in BookingActions.manage(viewer, booking.status)) {
            FlatAction(
                if (viewer == BookingViewer.Artist) "Cancel gig" else "Cancel",
                onCancel,
                Modifier.weight(1f),
            )
        }
    }
}

/** Screen 95 — the only editable state, and what it is waiting for. */
@Composable
private fun AwaitingBody(booking: Booking, nowMs: Long, packageName: String?) {
    val dimens = AppTheme.dimens
    SectionTitle("Progress")
    EventTimeline(steps = requestProgress(booking, nowMs).map { it.toStep() })

    SectionTitle("What you asked for")
    TermsList(bookingTerms(booking, packageName))

    Spacer(Modifier.height(dimens.space.xs))
    AccentNote(
        "You can still withdraw this while it's awaiting — once it's accepted, the " +
            "terms are fixed.",
    )
}

/** Screen 83 — terminal, but the relationship may survive. */
@Composable
private fun CancelledBody(
    booking: Booking,
    packageName: String?,
    viewer: BookingViewer,
    openingChat: Boolean,
    onMessage: () -> Unit,
    onBookAgain: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    SectionTitle("What was agreed")
    Column(Modifier.alpha(DIMMED)) {
        TermsList(bookingTerms(booking, packageName))
    }

    AccentNote(
        "Terms are frozen and read-only now. The thread stays open so you can rebook " +
            "or explain.",
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        PrimaryButton(
            text = if (openingChat) "Opening…" else "Message",
            onClick = onMessage,
            enabled = !openingChat,
            modifier = Modifier.weight(1f),
        )
        // Only the client can start a booking, so only the client is offered one.
        if (viewer == BookingViewer.Client) {
            SecondaryButton("Book again", onBookAgain, modifier = Modifier.weight(1f))
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = dimens.space.md)
            .hairlineTop()
            .padding(top = dimens.space.md),
    ) {
        Text(
            "No money moved — Artistant holds none, so there is nothing to refund.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
}

/**
 * Screen 96 — an escalated state, not an error.
 *
 * The design lists what each side reported and when. The schema has no such
 * record: `bookings.status` carries 'disputed' and nothing else, `reports` does
 * not reference a booking at all, and there is no dispute-events table. So the
 * account of what happened is stated as UNAVAILABLE rather than invented — and
 * the half of the screen that IS true, the policy while a dispute is open, is
 * printed in full, because saying reviews and scoring are suspended is what
 * stops retaliatory ratings.
 */
@Composable
private fun DisputedBody() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    SectionTitle("What happened")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.surface3)
            .padding(dimens.space.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(dimens.size.iconLg),
        )
        Text(
            "The account of this dispute isn't in the app. Artistant Support holds both " +
                "sides' messages from the night.",
            style = AppTheme.type.subtitle,
            color = colors.ink2,
        )
    }

    SectionTitle("While this is open")
    Column {
        HRule()
        FactRow("Reviews are paused", "Neither side publishes until it closes")
        HRule()
        FactRow("The score is untouched", "No points move on a disputed booking")
        HRule()
        FactRow("The thread stays open", "You can still message each other")
        HRule()
    }
}

/** Screen 97 — forward compatibility, visible. */
@Composable
private fun ReadOnlyBody(booking: Booking, packageName: String?) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    SectionTitle("What we can still show")
    Column(Modifier.alpha(0.85f)) {
        TermsList(bookingTerms(booking, packageName))
    }

    Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
        // Disabled rather than hidden, per the design: you can see what WOULD be
        // available, which is what makes "update the app" an obvious next step
        // instead of a mystery.
        DisabledAction("Cancel booking")
        DisabledAction("Leave a review")
        DisabledAction("Add to calendar")
    }
    Text(
        "Actions are disabled rather than hidden, so you can see what would be available.",
        style = AppTheme.type.caption,
        color = colors.ink4,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// The dock
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The pinned bar, per variant.
 *
 * Screen 18 has none: a confirmed booking's one conversational action is the
 * message circle in its header, and everything else is a row in the page. The
 * other four each end in a bar because each has exactly one thing the page is
 * asking you to decide.
 */
@Composable
private fun BookingDock(
    variant: BookingDetailVariant,
    booking: Booking,
    viewer: BookingViewer,
    counterparty: String,
    acting: BookingAction?,
    openingChat: Boolean,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    onMessage: () -> Unit,
    onReview: () -> Unit,
    onOpenSupport: () -> Unit,
    onUpdateApp: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val isActing = acting != null

    when (variant) {
        BookingDetailVariant.Confirmed -> {
            // A completed booking's one remaining act is the review — and only the
            // client's: an artist reviewing their own gig is not a thing the
            // reviews table models.
            if (BookingAction.LeaveReview in BookingActions.manage(viewer, booking.status)) {
                BottomActionBar { PrimaryButton("Leave a review", onReview, fullWidth = true) }
            }
        }

        BookingDetailVariant.Awaiting -> BottomActionBar {
            if (viewer == BookingViewer.Artist) {
                // The artist's pending dock is the answer to the request. There is
                // no Message here on purpose: no thread exists until the booking
                // confirms, and `findOrCreateThread` refuses artist-side creation
                // on a pending booking.
                PrimaryButton(
                    text = if (acting == BookingAction.Accept) "Accepting…" else "Accept request",
                    onClick = onAccept,
                    fullWidth = true,
                    enabled = !isActing,
                )
                SecondaryButton(
                    text = if (acting == BookingAction.Decline) "Declining…" else "Decline",
                    onClick = onCancel,
                    fullWidth = true,
                    enabled = !isActing,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
                    SecondaryButton(
                        text = if (acting == BookingAction.Cancel) "Withdrawing…" else "Withdraw",
                        onClick = onCancel,
                        enabled = !isActing,
                        modifier = Modifier.weight(1f),
                    )
                    PrimaryButton(
                        text = if (openingChat) "Opening…" else "Message $counterparty",
                        onClick = onMessage,
                        enabled = !openingChat,
                        modifier = Modifier.weight(WIDE_ACTION_WEIGHT),
                    )
                }
            }
        }

        // The cancelled page's actions are in the body, beside the record they
        // act on, and its bar would have nothing left to hold.
        BookingDetailVariant.Cancelled -> Unit

        BookingDetailVariant.Disputed -> BottomActionBar {
            // Disabled, with the reason under it. There is no evidence endpoint —
            // no dispute table, no attachment path — and a button that silently
            // does nothing is worse than one that says why it can't.
            PrimaryButton("Add evidence", onClick = {}, fullWidth = true, enabled = false)
            Text(
                "Evidence goes through Support in this version.",
                style = AppTheme.type.caption,
                color = colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // The counterparty thread, beside Support and not instead of it.
            //
            // The page says three lines up that "the thread stays open", and
            // `BookingActions.primary` really does allow Message on a disputed
            // booking — but the dock offered only Support, so the one screen that
            // PROMISES the conversation survives was the one screen you could not
            // reach it from. A dispute is exactly when the two sides most need to
            // talk, and the design's 96 keeps the thread reachable.
            if (BookingAction.Message in BookingActions.primary(viewer, booking.status)) {
                SecondaryButton(
                    text = if (openingChat) "Opening…" else "Message $counterparty",
                    onClick = onMessage,
                    fullWidth = true,
                    enabled = !openingChat,
                )
            }
            Text(
                "Message Artistant Support",
                style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accentInk,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .clickable(role = Role.Button, onClick = onOpenSupport)
                    .padding(vertical = dimens.space.md),
            )
        }

        BookingDetailVariant.ReadOnly -> BottomActionBar {
            PrimaryButton("Update Artistant", onUpdateApp, fullWidth = true)
        }
    }
}

/**
 * The client's awaiting dock is not two equal halves: Message is the useful act
 * while you wait, Withdraw is the exit. The design draws 1 : 1.3, and the ratio
 * is what says which is which without colouring the smaller one red.
 */
private const val WIDE_ACTION_WEIGHT = 1.3f

// ─────────────────────────────────────────────────────────────────────────────
// Not found (84)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Two causes, no guess.
 *
 * A push tap can outrun the sync that would have fetched the row, so "we can't
 * find this booking" offers BOTH explanations rather than implying it is gone.
 * A read that FAILED is a different sentence with a retry on it — see
 * [BookingLoadFailure].
 */
@Composable
private fun BookingNotFound(
    failure: BookingLoadFailure,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val missing = failure == BookingLoadFailure.NotFound
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.component.gutter),
    ) {
        DetailHeader(title = "", onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EmptyState(
                title = failure.title,
                body = failure.body,
                icon = Icons.Filled.CalendarMonth,
                actionLabel = if (missing) "Back to Bookings" else "Try again",
                onAction = if (missing) onBack else onRetry,
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .hairlineTop()
                .padding(vertical = dimens.space.lg),
        ) {
            Text(
                if (missing) {
                    "Opened from a notification on a device that hasn't synced yet? " +
                        "Pull Bookings to refresh."
                } else {
                    "Nothing was changed — this is a read that didn't complete."
                },
                style = AppTheme.type.caption,
                color = colors.ink4,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cancel flow (117 → 52)
// ─────────────────────────────────────────────────────────────────────────────

/** Which half of the cancel flow is on screen. */
enum class CancelStage { Reason, Consequences }

/**
 * Reason, then consequences.
 *
 * Two stages rather than one confirm dialog because the two questions are
 * different: the reason is for us (it lands on the row and feeds matching), the
 * consequences are for them. Collapsing them into a single "Are you sure?" gets
 * a reflexive yes and no reason at all — and, per screen 117's note, asking why
 * first "creates the moment where a reschedule can replace a cancellation",
 * which is what the aside under the reason list is for.
 *
 * A full page rather than a sheet, as the design draws it: both stages carry a
 * header, a list and a pinned bar, and a sheet tall enough for that is a page
 * with a grabber on it.
 */
@Composable
private fun CancelFlow(
    stage: CancelStage,
    viewer: BookingViewer,
    counterparty: String,
    reason: CancelReason?,
    daysBefore: Int?,
    isDecline: Boolean,
    onPickReason: (CancelReason) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onKeep: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val title = if (isDecline) "Decline request" else "Cancel booking"
    val keepLabel = if (isDecline) "Keep request" else "Keep booking"

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
        ) {
            DetailHeader(
                title = title,
                subtitle = if (stage == CancelStage.Reason) "Step 1 of 2" else "Step 2 of 2",
                onBack = onBack,
            )

            when (stage) {
                CancelStage.Reason -> {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
                        Text(
                            if (isDecline) "Why are you declining?" else "Why are you cancelling?",
                            style = AppTheme.type.screenTitle,
                            color = colors.ink,
                        )
                        Text(
                            "This helps us match you better next time. It is saved on the " +
                                "booking, so $counterparty can see it too.",
                            style = AppTheme.type.subtitle,
                            color = colors.ink4,
                        )
                    }
                    Column {
                        cancelReasons(viewer).forEach { option ->
                            ReasonRow(
                                label = option.label,
                                selected = reason == option,
                                onClick = { onPickReason(option) },
                            )
                        }
                    }
                    // The moment a reschedule can replace a cancellation. Only for
                    // the reason that has one — a client whose event moved has a
                    // date to offer, a client whose event is off does not.
                    if (reason == CancelReason.DateChanged) {
                        AccentNote(
                            lead = "Date moved?",
                            text = "Message $counterparty first — shifting a date is usually " +
                                "easier than starting over.",
                        )
                    }
                }

                CancelStage.Consequences -> {
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
                        Text(
                            "Here's what happens",
                            style = AppTheme.type.screenTitle,
                            color = colors.ink,
                        )
                        Text(
                            "This can't be undone.",
                            style = AppTheme.type.subtitle,
                            color = colors.ink4,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(dimens.space.md)) {
                        cancelConsequences(viewer, counterparty, daysBefore).forEach {
                            ConsequenceCard(it)
                        }
                    }
                    reason?.let {
                        AccentNote(
                            lead = "You said: ${it.label.lowercase()}.",
                            // Not "the artist only sees that you cancelled" —
                            // `bookings_select_participants` grants the whole row
                            // to both sides, `cancel_reason` included.
                            text = "We use it to match you better next time. It is saved " +
                                "on the booking, so $counterparty can see it too.",
                        )
                    }
                }
            }
            Spacer(Modifier.height(dimens.space.lg))
        }

        BottomActionBar {
            when (stage) {
                CancelStage.Reason -> PrimaryButton(
                    text = "Continue",
                    onClick = onContinue,
                    fullWidth = true,
                    // The whole point of this stage is that it produces a reason.
                    enabled = reason != null,
                )

                CancelStage.Consequences -> DestructiveButton(
                    text = if (isDecline) "Decline this request" else "Cancel this booking",
                    onClick = onConfirm,
                )
            }
            Text(
                keepLabel,
                style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink2,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .clickable(role = Role.Button, onClick = onKeep)
                    .padding(vertical = dimens.space.sm),
            )
        }
    }
}

/**
 * The one destructive control in the app: a `danger` fill with white on it.
 *
 * Not a [ButtonVariant]: the accent rule says a screen has one signal, and this
 * button deliberately breaks it — on the second stage of a cancellation the
 * destructive act IS the signal. Keeping it local to that flow is what stops it
 * becoming a general-purpose red button.
 */
@Composable
private fun DestructiveButton(text: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .fillMaxWidth()
            .height(dimens.component.cta)
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.danger)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppTheme.type.cta, color = colors.onDark)
    }
}

/**
 * One reason. A circle that FILLS when chosen, not a ring with a dot: the list is
 * a single choice made once, and the design draws the accent disc with a tick in
 * it — which is also the only thing on the page saying an answer has been given.
 */
@Composable
private fun ReasonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .hairlineBottom()
            .padding(vertical = dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.radio)
                .clip(CircleShape)
                .background(if (selected) colors.accent else Color.Transparent)
                .border(
                    if (selected) dimens.size.hairline else dimens.size.strokeEmphasis,
                    if (selected) colors.accent else colors.lineStrong,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
            }
        }
        Text(
            label,
            style = AppTheme.type.rowTitle,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
    }
}

/** One consequence, as its own quiet card. */
@Composable
private fun ConsequenceCard(consequence: CancelConsequence) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.buttonLg))
            .background(colors.surface3)
            .padding(dimens.space.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = colors.ink4,
            modifier = Modifier.size(dimens.size.iconLg),
        )
        Column {
            Text(consequence.title, style = AppTheme.type.rowTitle, color = colors.ink)
            Text(
                consequence.detail,
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier.padding(top = dimens.space.xs / 2),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small parts
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = AppTheme.type.sectionTitle,
        color = AppTheme.colors.ink,
        modifier = Modifier.padding(top = AppTheme.dimens.space.xs),
    )
}

/** The agreed terms, label left and value right, one per row. */
@Composable
private fun TermsList(terms: List<BookingTerm>) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.space.md)) {
        terms.forEach { term ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            ) {
                Text(term.label, style = AppTheme.type.body, color = colors.ink2)
                Spacer(Modifier.weight(1f))
                Text(
                    term.value,
                    // The id is a machine value and reads as one; everything else
                    // is prose the client typed or picked.
                    style = if (term.mono) {
                        AppTheme.type.monoSmall
                    } else {
                        AppTheme.type.rowTitle.copy(fontWeight = FontWeight.SemiBold)
                    },
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun FeeRow(label: String, value: String) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = AppTheme.type.body, color = colors.ink2)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
        )
    }
}

/** A stated fact with a line under it — the dispute page's policy list. */
@Composable
private fun FactRow(title: String, detail: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(Modifier.padding(vertical = dimens.space.md)) {
        Text(title, style = AppTheme.type.rowTitle, color = colors.ink)
        Text(
            detail,
            style = AppTheme.type.caption,
            color = colors.ink4,
            modifier = Modifier.padding(top = dimens.space.xs / 2),
        )
    }
}

/** Icon, label, chevron; the whole row is the target. */
@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AppTheme.colors.ink,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimens.size.iconLg))
        Text(label, style = AppTheme.type.rowTitle, color = tint, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            // The label names the control; the chevron is a glyph, not a second
            // thing for a screen reader to announce.
            contentDescription = null,
            tint = colors.ink4,
            modifier = Modifier.size(dimens.size.iconMd),
        )
    }
}

/** One of the flat buttons under a confirmed booking. */
@Composable
private fun FlatAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        modifier
            .height(dimens.size.controlMin)
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.surface2)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppTheme.type.rowTitle, color = colors.ink, maxLines = 1)
    }
}

/** The same shape, visibly inert — screen 97's disabled list. */
@Composable
private fun DisabledAction(label: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .fillMaxWidth()
            .height(dimens.size.controlMin)
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.lineSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = AppTheme.type.rowTitle, color = colors.ink3)
    }
}

/** Loading: the shape of the page that is coming, not a spinner. */
@Composable
private fun DetailSkeleton(onBack: () -> Unit) {
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = dimens.component.gutter),
        verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
    ) {
        DetailHeader(title = "Booking", onBack = onBack)
        SkeletonBlock(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.size.avatarLg + dimens.space.xl),
            radius = dimens.radii.card,
        )
        repeat(3) {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.component.skeletonChipHeight),
                radius = dimens.radii.md,
            )
        }
    }
}

/** [BookingMoment] → the timeline's own step. */
private fun BookingMoment.toStep(): EventStep = EventStep(
    title = title,
    detail = detail,
    state = if (done) EventStepState.Done else EventStepState.Current,
)

// ─────────────────────────────────────────────────────────────────────────────
// System handoffs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zero-permission calendar handoff. Prefills title/location/window; the system
 * Calendar app owns the compose UI (we never read the store).
 */
private fun launchAddToCalendar(context: Context, booking: Booking): String? {
    // The canonical resolver, not a local ISO parse: `Instant.parse` is
    // ISO_INSTANT, which rejects the numeric-offset form PostgREST emits for a
    // `timestamptz`, and it has nothing to fall back on when the column is
    // missing from a projection. `resolvedStartEpochMs` tries the offset patterns
    // and then the date+time labels this very screen is displaying two sections
    // up — so "Add to calendar" stops refusing a gig whose show time is plainly
    // on screen.
    val startMs = booking.resolvedStartEpochMs()
        ?: return "Couldn't add to calendar — missing show time."
    val endMs = booking.resolvedEndEpochMs() ?: (startMs + DEFAULT_GIG_MS)
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "Artistant · ${booking.venue}")
        .putExtra(CalendarContract.Events.EVENT_LOCATION, booking.venue)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
    booking.venueNotes?.takeIf { it.isNotBlank() }?.let {
        intent.putExtra(CalendarContract.Events.DESCRIPTION, it)
    }
    return runCatching { context.startActivity(intent); null }
        .getOrElse { "Couldn't open a calendar app on this device." }
}

/** Placeholder gig length when the row carries no end time — matches create(). */
private const val DEFAULT_GIG_MS = 2L * 60 * 60 * 1000

/**
 * A `geo:` search rather than coordinates: free text is all either side has
 * (there is no lat/long on the row), and a search pin at the right name beats a
 * dropped pin at the wrong point.
 */
private fun openMaps(context: Context, address: String): String? {
    if (address.isBlank()) return "No venue address on this booking yet."
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
    return runCatching { context.startActivity(intent); null }
        .getOrElse { "Couldn't open a maps app on this device." }
}

private fun shareGig(context: Context, text: String): String? {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    return runCatching { context.startActivity(Intent.createChooser(intent, null)); null }
        .getOrElse { "Couldn't open the share sheet." }
}

/**
 * Screen 97's only live control: the store listing for THIS package.
 *
 * `market://` first because it opens the Play app directly; the https form is the
 * fallback for a device without it (an emulator, a sideloaded build), which is
 * exactly where a read-only booking is most likely to be seen during
 * development.
 */
private fun openPlayStore(context: Context): String? {
    val id = context.packageName
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$id")))
        null
    } catch (_: ActivityNotFoundException) {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$id"),
                ),
            )
            null
        }.getOrElse { "Couldn't open the Play Store on this device." }
    }
}
