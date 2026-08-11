package `in`.artistant.app.feature.booking

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.BookingStatusTimeline
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.InlineBanner
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.bookingStatusTone
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.messages.ChatOpenViewModel
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Booking detail — the whole gig on one page: where it stands, what was agreed,
 * how to get there, and the actions this side of the row is allowed to take.
 *
 * The layout is a cover hero over a scrolling body, with the fee and the CTAs
 * pinned in a dock. That is the flat translation of the reference build's
 * draggable glass sheet: blur is out by the owner's call, and a fake-frosted
 * panel that can be dragged to two heights is a lot of machinery to reproduce a
 * scroll. The information order is preserved exactly — progress, terms, getting
 * there, manage — because that order is the argument the screen makes.
 *
 * Every fork on this page keys off [isArtistViewer], which is which SIDE of the
 * booking the viewer is on, not their account role. See [BookingViewer].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailScreen(
    isArtistViewer: Boolean,
    onBack: () -> Unit,
    onOpenChat: (threadId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BookingDetailViewModel = hiltViewModel(),
    chatOpen: ChatOpenViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val openingChat by chatOpen.opening.collectAsStateWithLifecycle()
    val chatError by chatOpen.error.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val booking = state.booking
    val viewer = viewerOf(isArtistViewer)

    var showReview by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }
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

    when {
        state.isLoading && booking == null -> {
            Box(modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
        }
        booking == null -> {
            Column(modifier.fillMaxSize().background(colors.bg)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                }
                EmptyState(
                    title = "Booking not found",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }
        }
        else -> {
            val counterparty = viewModel.counterpartyName(isArtistViewer)
            val city = state.artist?.city
            val address = venueAddress(booking.venue, city)

            RevealOnAppear {
                Column(modifier.fillMaxSize().background(colors.bg)) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Hero(
                            booking = booking,
                            counterparty = counterparty,
                            // The artist gets a neutral hero, never their own
                            // cover. Their photo here made the page read as "my
                            // profile" instead of "my gig" — the same confusion
                            // that had them looking at the client's screen.
                            coverUrl = if (isArtistViewer) null else state.artist?.coverUrl,
                            gradient = state.artist?.gradient,
                            onBack = onBack,
                        )
                        Column(
                            Modifier.padding(horizontal = space.lg),
                            verticalArrangement = Arrangement.spacedBy(space.xl),
                        ) {
                            ProgressSection(booking.status)
                            TermsSection(bookingTerms(booking, viewModel.packageName()))
                            if (BookingActions.showsGettingThere(booking.status)) {
                                GettingThereSection(
                                    address = address,
                                    notes = booking.venueNotes,
                                    copied = copied,
                                    onOpenMaps = {
                                        openMaps(context, address)
                                            ?.let(viewModel::reportActionError)
                                    },
                                    onCopy = {
                                        clipboard.setText(AnnotatedString(address))
                                        copied = true
                                    },
                                )
                            }
                            ManageSection(
                                // Rows only — the states whose single secondary
                                // action belongs beside the primary hand it to
                                // the dock instead. The two halves partition the
                                // manage set, so nothing renders twice.
                                actions = BookingActions.manageRows(viewer, booking.status),
                                viewer = viewer,
                                onShare = {
                                    shareGig(context, shareGigText(counterparty, booking, city))
                                        ?.let(viewModel::reportActionError)
                                },
                                onAddToCalendar = {
                                    launchAddToCalendar(context, booking)
                                        ?.let(viewModel::reportActionError)
                                },
                                onCancel = { showCancel = true },
                            )
                            Spacer(Modifier.height(space.sm))
                        }
                    }

                    Dock(
                        booking = booking,
                        viewer = viewer,
                        isActing = state.isActing,
                        openingChat = openingChat,
                        error = state.actionError ?: chatError,
                        onDismissError = {
                            viewModel.dismissActionError()
                            chatOpen.dismissError()
                        },
                        onAccept = viewModel::acceptRequest,
                        onDecline = { showCancel = true },
                        onMessage = { chatOpen.open(booking.artistId, booking.id, onOpenChat) },
                        onCancel = { showCancel = true },
                        onReview = { showReview = true },
                    )
                }
            }

            if (showCancel) {
                // Decline and Cancel share this sheet on purpose: both end the
                // booking, both want a reason on the row, and both route by side
                // (the artist's lands as `cancelled_by = "artist"`). The copy is
                // what differs, and it is driven by the two arguments below.
                CancelFlowSheet(
                    viewer = viewer,
                    isDecline = viewer == BookingViewer.Artist &&
                        booking.status == BookingStatus.PendingConfirm,
                    isActing = state.isActing,
                    onDismiss = { showCancel = false },
                    onConfirm = { reason ->
                        showCancel = false
                        if (viewer == BookingViewer.Artist &&
                            booking.status == BookingStatus.PendingConfirm
                        ) {
                            viewModel.declineRequest(reason)
                        } else {
                            viewModel.cancelBooking(viewer, reason)
                        }
                    },
                )
            }

            if (showReview) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { showReview = false },
                    sheetState = sheetState,
                    containerColor = colors.bgElev,
                ) {
                    ReviewSheet(
                        bookingId = booking.id,
                        reviewsRepository = viewModel.reviewsRepository,
                        onDismiss = { showReview = false },
                        onSubmitted = { showReview = false },
                    )
                }
            }
        }
    }
}

/** How long the Copy row wears its "Copied" label before reverting. */
private const val COPIED_LABEL_MS = 1_600L

// ─────────────────────────────────────────────────────────────────────────────
// Hero
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Cover, scrim, status, who and where.
 *
 * Shorter than the artist profile's hero ([Size.heroShort] rather than a screen
 * fraction) because the photo is context here, not the product: the thing being
 * read is the status and the terms below it, and a half-screen of cover pushes
 * both under the fold.
 */
@Composable
private fun Hero(
    booking: Booking,
    counterparty: String,
    coverUrl: String?,
    gradient: List<Color>?,
    onBack: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space

    Box(
        Modifier
            .fillMaxWidth()
            .height(dimens.size.heroShort),
    ) {
        // Floor first, so a slow or failed cover never shows a hole. The artist's
        // side gets the neutral surface ladder rather than the artist's own brand
        // gradient — this is their gig, not their profile, and a caller that
        // suppresses the cover means it.
        val floor = if (coverUrl.isNullOrBlank() || gradient.isNullOrEmpty()) {
            listOf(colors.bgElev, colors.bgSoft, colors.bg)
        } else {
            gradient
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(floor)))
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        1f - dimens.fraction.heroFade to Color.Transparent,
                        1f to colors.bg,
                    ),
                ),
        )

        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            tint = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = space.md, vertical = space.xs),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = space.lg)
                .padding(bottom = space.lg),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            // The one status→tone mapping in the app. Every surface that painted
            // its own disagreed with the others; this one is shared with the
            // bookings list and the artist drill-down.
            Pill(booking.status.label, tone = bookingStatusTone(booking.status))
            Text(
                counterparty,
                style = AppTheme.type.profileHeroName,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                heroWhereLine(booking),
                style = AppTheme.type.callout,
                color = colors.inkOnMedia,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sections
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Where the booking actually is in its life.
 *
 * This is the shared [BookingStatusTimeline] the post-request screen already
 * uses, not a lookalike: the two screens answer the same question ("what happens
 * next") and had no business drawing two different pictures of it. A status word
 * alone doesn't answer it — "Awaiting confirm" says nothing about what comes
 * after, and the timeline shows the whole arc with the current step lit.
 */
@Composable
private fun ProgressSection(status: BookingStatus) {
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionLabel("Progress")
        BookingStatusTimeline(status = status)
    }
}

/** The agreed terms, one per hairline-separated row. */
@Composable
private fun TermsSection(terms: List<BookingTerm>) {
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionLabel("Details")
        Column {
            terms.forEachIndexed { index, term ->
                TermRow(term)
                if (index != terms.lastIndex) HRule()
            }
        }
    }
}

@Composable
private fun TermRow(term: BookingTerm) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = space.md),
        horizontalArrangement = Arrangement.spacedBy(space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(term.label, style = AppTheme.type.footnote, color = colors.ink3)
        Spacer(Modifier.weight(1f))
        Text(
            term.value,
            // The id is a machine value and reads as one; everything else is
            // prose the client typed or picked.
            style = if (term.mono) AppTheme.type.monoSmall else AppTheme.type.callout,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Address, load-in notes, and the two things anyone does with an address.
 *
 * Confirmed-only: a pending booking has nothing to travel to yet, and a
 * completed one is in the past.
 */
@Composable
private fun GettingThereSection(
    address: String,
    notes: String?,
    copied: Boolean,
    onOpenMaps: () -> Unit,
    onCopy: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionLabel("Getting there")
        Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
            Text(address, style = AppTheme.type.callout, color = colors.ink)
            notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, style = AppTheme.type.footnote, color = colors.ink3)
            }
        }
        Column {
            HRule()
            ActionRow(Icons.Filled.Place, "Open in Maps", onClick = onOpenMaps)
            HRule()
            ActionRow(
                Icons.Filled.ContentCopy,
                if (copied) "Copied" else "Copy address",
                tint = if (copied) colors.good else colors.ink,
                onClick = onCopy,
            )
        }
    }
}

/**
 * The post-confirmation action list — hairline rows, no card chrome.
 *
 * Message is deliberately absent: it is the pinned primary below, and the
 * always-visible CTA duplicated as a row is one more thing to read past.
 */
@Composable
private fun ManageSection(
    actions: List<BookingAction>,
    viewer: BookingViewer,
    onShare: () -> Unit,
    onAddToCalendar: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // Maps + Copy already rendered above under their own heading.
    val rows = actions.filter { it != BookingAction.OpenMaps && it != BookingAction.CopyAddress }
    if (rows.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        SectionLabel("Manage")
        Column {
            rows.forEachIndexed { index, action ->
                if (index == 0) HRule()
                when (action) {
                    BookingAction.Share ->
                        ActionRow(Icons.Filled.Share, "Share gig details", onClick = onShare)
                    BookingAction.AddToCalendar ->
                        ActionRow(Icons.Filled.CalendarMonth, "Add to calendar", onClick = onAddToCalendar)
                    BookingAction.Cancel -> ActionRow(
                        Icons.Outlined.Cancel,
                        // The label differs because the consequences do: an
                        // artist pulling out of a gig they accepted is a
                        // different act from a client withdrawing a request.
                        if (viewer == BookingViewer.Artist) "Cancel this gig" else "Cancel booking",
                        tint = colors.warm,
                        onClick = onCancel,
                    )
                    else -> Unit
                }
                HRule()
            }
        }
    }
}

/** Icon, label, chevron; the whole row is the target. */
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = AppTheme.colors.ink,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimens.space.md),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(dimens.size.iconLg))
        Text(label, style = AppTheme.type.callout, color = tint, modifier = Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            // The label names the control; the chevron is a glyph, not a second
            // thing for a screen reader to announce.
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(dimens.size.iconMd),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Dock
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fee and actions, pinned.
 *
 * The fee sits here rather than in the scroll for the same reason the CTAs do:
 * it is the number both sides came to check, and having to scroll for it on a
 * page whose top half is a photo is a bad trade. Only the artist's fee appears —
 * v1 moves no money, so there is no platform fee, no GST and no total to show.
 */
@Composable
private fun Dock(
    booking: Booking,
    viewer: BookingViewer,
    isActing: Boolean,
    openingChat: Boolean,
    error: String?,
    onDismissError: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit,
    onCancel: () -> Unit,
    onReview: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val primary = BookingActions.primary(viewer, booking.status)
    // Only the secondary actions the in-page list did NOT take — see the
    // manageRows/dockSecondary partition.
    val secondary = BookingActions.dockSecondary(viewer, booking.status)

    Column(
        Modifier
            .dockSurface()
            .padding(space.lg),
        verticalArrangement = Arrangement.spacedBy(space.md),
    ) {
        // A failed accept or a chat that wouldn't open surfaces where the tap
        // happened, not as a transient snackbar the user can miss — both are
        // still true next time the screen is opened.
        error?.let {
            InlineBanner(
                title = it,
                tone = BannerTone.Failure,
                actionLabel = "Dismiss",
                onAction = onDismissError,
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ARTIST FEE", style = AppTheme.type.caption, color = colors.ink3)
            Spacer(Modifier.weight(1f))
            Text(formatInr(booking.fee), style = AppTheme.type.monoDock, color = colors.brand)
        }

        primary.forEach { action ->
            when (action) {
                BookingAction.Accept -> PrimaryButton(
                    text = if (isActing) "Accepting…" else "Accept request",
                    onClick = onAccept,
                    fullWidth = true,
                    enabled = !isActing,
                )
                BookingAction.Decline -> PrimaryButton(
                    text = if (isActing) "Declining…" else "Decline",
                    onClick = onDecline,
                    variant = ButtonVariant.Ghost,
                    fullWidth = true,
                    enabled = !isActing,
                )
                BookingAction.Message -> PrimaryButton(
                    text = when {
                        openingChat -> "Opening…"
                        viewer == BookingViewer.Artist -> "Message client"
                        else -> "Message artist"
                    },
                    onClick = onMessage,
                    fullWidth = true,
                    enabled = !openingChat,
                )
                else -> Unit
            }
        }

        // Pending has no Manage list to hold its Cancel, and a completed booking's
        // review opens a sheet rather than a row — so both pin here under the
        // primary rather than floating a one-item section above the dock.
        secondary.forEach { action ->
            when (action) {
                BookingAction.Cancel -> PrimaryButton(
                    text = if (isActing) "Cancelling…" else "Cancel request",
                    onClick = onCancel,
                    variant = ButtonVariant.Ghost,
                    fullWidth = true,
                    enabled = !isActing,
                )
                BookingAction.LeaveReview -> PrimaryButton(
                    text = "Leave a review",
                    onClick = onReview,
                    variant = ButtonVariant.Ghost,
                    fullWidth = true,
                )
                else -> Unit
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cancel flow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reason, then consequences, then the destructive tap.
 *
 * Two stages rather than one confirm dialog because the two questions are
 * different: the reason is for us (it lands on the row and feeds matching), the
 * consequences are for them. Collapsing them into a single "Are you sure?" gets
 * a reflexive yes and no reason at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CancelFlowSheet(
    viewer: BookingViewer,
    isDecline: Boolean,
    isActing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (reason: String?) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by remember { mutableStateOf<CancelReason?>(null) }
    var stage by remember { mutableStateOf(CancelStage.Reason) }
    val title = if (isDecline) "Decline request" else "Cancel booking"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElev,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.lg)
                .padding(bottom = space.xxl),
            verticalArrangement = Arrangement.spacedBy(space.xl),
        ) {
            when (stage) {
                CancelStage.Reason -> {
                    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                        Text(title, style = AppTheme.type.title, color = colors.ink)
                        Text(
                            if (isDecline) {
                                "Why are you declining? It helps us match you with better-fitting requests."
                            } else {
                                "Why are you cancelling? This helps us match you better next time."
                            },
                            style = AppTheme.type.footnote,
                            color = colors.ink3,
                        )
                    }
                    Column {
                        cancelReasons(viewer).forEachIndexed { index, option ->
                            ReasonRow(
                                label = option.label,
                                selected = reason == option,
                                onClick = { reason = option },
                            )
                            if (index != cancelReasons(viewer).lastIndex) HRule()
                        }
                    }
                    PrimaryButton(
                        text = "Continue",
                        onClick = { stage = CancelStage.Consequences },
                        fullWidth = true,
                        // Disabled until a reason is picked — the whole point of
                        // this stage is that it produces one.
                        enabled = reason != null,
                    )
                    PrimaryButton(
                        text = if (isDecline) "Keep request" else "Keep booking",
                        onClick = onDismiss,
                        variant = ButtonVariant.Ghost,
                        fullWidth = true,
                    )
                }

                CancelStage.Consequences -> {
                    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                        Text("Here's what happens", style = AppTheme.type.title, color = colors.ink)
                        Text(
                            "This can't be undone.",
                            style = AppTheme.type.footnote,
                            color = colors.ink3,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                        cancelConsequences(viewer).forEach {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(space.md),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    Modifier
                                        .padding(top = space.sm)
                                        .size(AppTheme.dimens.size.dot)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(colors.ink3),
                                )
                                Text(it, style = AppTheme.type.callout, color = colors.ink2)
                            }
                        }
                    }
                    PrimaryButton(
                        text = title,
                        onClick = { onConfirm(reason?.label) },
                        fullWidth = true,
                        enabled = !isActing,
                    )
                    PrimaryButton(
                        text = if (isDecline) "Keep request" else "Keep booking",
                        onClick = onDismiss,
                        variant = ButtonVariant.Ghost,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

private enum class CancelStage { Reason, Consequences }

/**
 * One reason, with the same radio idiom the package picker uses — a filled core
 * inside a ring, not a checkbox. The rows are a single choice, and the radio is
 * what says so before anything is tapped.
 */
@Composable
private fun ReasonRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = dimens.space.lg),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = AppTheme.type.callout, color = colors.ink, modifier = Modifier.weight(1f))
        Box(
            Modifier
                .size(dimens.size.radio)
                .border(
                    dimens.size.stroke,
                    if (selected) colors.brand else colors.line,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(dimens.size.radioCore)
                        .clip(CircleShape)
                        .background(colors.brand),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// System handoffs
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Zero-permission calendar handoff. Prefills title/location/window; the system
 * Calendar app owns the compose UI (we never read the store).
 */
private fun launchAddToCalendar(context: Context, booking: Booking): String? {
    val startMs = parseIsoEpochMs(booking.startDatetimeIso)
        ?: return "Couldn't add to calendar — missing show time."
    val endMs = parseIsoEpochMs(booking.endDatetimeIso) ?: (startMs + DEFAULT_GIG_MS)
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

private fun parseIsoEpochMs(iso: String?): Long? =
    iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
