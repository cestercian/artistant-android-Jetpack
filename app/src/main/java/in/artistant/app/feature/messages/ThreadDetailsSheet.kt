package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.repository.PendingReport
import `in`.artistant.app.data.repository.ReportOutcome
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.ListRow
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.component.StatusPill
import `in`.artistant.app.designsystem.component.StatusTone
import `in`.artistant.app.designsystem.component.bookingStatusTone
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands

/**
 * Thread details (design 33) — "the deal, not the chatter".
 *
 * It answers *what did we actually agree* without scrolling the transcript, which
 * is why the booking card is the first thing under the name and why the fee is
 * set at the same weight as its own label. Everything below it is the small set
 * of things you can do to a conversation.
 *
 * Report is the last row and it is the only one in `danger`. It is a rare,
 * serious action, not the sheet's primary one, and lime is this system's single
 * "do the positive thing" signal — an accent button here would make reporting the
 * loudest thing on the sheet and read as encouragement. It opens a reason picker
 * rather than filing anything, so it cannot be a one-tap accident.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailsSheet(
    counterpartName: String,
    context: ThreadContext,
    viewerIsArtist: Boolean,
    starred: Boolean,
    archived: Boolean,
    muted: Boolean,
    blocked: Boolean,
    /**
     * False when the counterparty's user id couldn't be resolved, which is the
     * only thing a block can be keyed on. Hides the row entirely rather than
     * offering an action that would have to guess who to block.
     */
    canBlock: Boolean,
    /** Non-null once a report reached the server or this device's log. */
    reportOutcome: ReportOutcome?,
    /** Non-null when a report is held NOWHERE — the sheet owes a retry, not a receipt. */
    failedReport: PendingReport?,
    /**
     * The last mute/block toggle didn't land. Rendered above the rows, because
     * this sheet is where the tap happened and the row it belongs to has already
     * flipped back to the state it was in.
     */
    actionError: String?,
    onBookingClick: (String) -> Unit,
    onToggleStar: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onMarkUnread: () -> Unit,
    onReport: (reason: String, details: String?) -> Unit,
    /** Re-file the report the reader already wrote. */
    onRetryReport: () -> Unit,
    /** Give up on a report nothing is holding. */
    onDiscardReport: () -> Unit,
    /**
     * Trust & safety (design 131).
     *
     * The advice belongs beside the remedies, not only in account settings:
     * this sheet is where someone lands when a conversation has started to feel
     * wrong, and "what do I do about this" is a different question from "file a
     * report" — one it is worse to have to go looking for. The caller closes the
     * sheet before navigating; a bottom sheet cannot stay up over a pushed
     * destination.
     */
    onOpenSafetyCentre: () -> Unit,
    onDismiss: () -> Unit,
    /** Non-null only on the client seat — an artist's counterpart has no profile. */
    artistId: String? = null,
    artistSubtitle: String = "",
    artistScore: Int? = null,
    onArtistClick: (String) -> Unit = {},
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    var reporting by remember { mutableStateOf(false) }
    // Blocking gets a confirm step for the same reason reporting gets a reason
    // picker, plus one of its own: a block takes the conversation out of the
    // inbox, so an accidental one is genuinely awkward to walk back — the way
    // back in is the thread you just hid. Unblocking is one tap, since undoing
    // a mistake should never need a confirmation of its own.
    var blocking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = colors.surface,
    ) {
        SheetScaffold {
            SheetTitle(
                title = when {
                    failedReport != null -> "Report not sent"
                    reportOutcome == ReportOutcome.Sent -> "Report received"
                    reportOutcome == ReportOutcome.Queued -> "Report saved on this device"
                    reporting -> "Report conversation"
                    blocking -> "Block $counterpartName?"
                    else -> "Thread details"
                },
                onClose = onDismiss,
            )
            Column(
                Modifier
                    .heightIn(max = dimens.size.heroTall)
                    .verticalScroll(rememberScrollState())
                    .semantics { testTag = "chat.detailsSheet" },
            ) {
                when {
                    // Ordered so the worst outcome wins: a retry that fails
                    // again must not be covered by the receipt of the attempt
                    // before it.
                    failedReport != null -> ReportFailure(
                        onRetry = onRetryReport,
                        onDiscard = onDiscardReport,
                    )

                    reportOutcome != null -> ReportReceipt(counterpartName, reportOutcome)

                    reporting -> ReportConversationSheet(
                        counterpartName = counterpartName,
                        onSubmit = onReport,
                        onOpenSafetyCentre = onOpenSafetyCentre,
                        onBack = { reporting = false },
                    )

                    blocking -> BlockConfirm(
                        counterpartName = counterpartName,
                        onConfirm = {
                            onToggleBlock()
                            blocking = false
                        },
                        onCancel = { blocking = false },
                    )

                    else -> DetailsBody(
                        counterpartName = counterpartName,
                        context = context,
                        viewerIsArtist = viewerIsArtist,
                        artistId = artistId,
                        artistSubtitle = artistSubtitle,
                        artistScore = artistScore,
                        starred = starred,
                        archived = archived,
                        muted = muted,
                        blocked = blocked,
                        canBlock = canBlock,
                        actionError = actionError,
                        onArtistClick = onArtistClick,
                        onBookingClick = onBookingClick,
                        onToggleStar = onToggleStar,
                        onToggleArchive = onToggleArchive,
                        onToggleMute = onToggleMute,
                        onMarkUnread = onMarkUnread,
                        onStartBlock = { if (blocked) onToggleBlock() else blocking = true },
                        onStartReport = { reporting = true },
                        onOpenSafetyCentre = onOpenSafetyCentre,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailsBody(
    counterpartName: String,
    context: ThreadContext,
    viewerIsArtist: Boolean,
    artistId: String?,
    artistSubtitle: String,
    artistScore: Int?,
    starred: Boolean,
    archived: Boolean,
    muted: Boolean,
    blocked: Boolean,
    canBlock: Boolean,
    actionError: String?,
    onArtistClick: (String) -> Unit,
    onBookingClick: (String) -> Unit,
    onToggleStar: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleMute: () -> Unit,
    onMarkUnread: () -> Unit,
    onStartBlock: () -> Unit,
    onStartReport: () -> Unit,
    onOpenSafetyCentre: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    // Who, with the profile behind it when there is one to open.
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (artistId != null) {
                    Modifier
                        .clip(RoundedCornerShape(dimens.radii.md))
                        .clickable { onArtistClick(artistId) }
                } else {
                    Modifier
                },
            )
            .padding(vertical = dimens.space.sm)
            .semantics(mergeDescendants = true) { testTag = "threadDetails.participant" },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = counterpartName, size = dimens.size.avatarLg)
        Column(Modifier.weight(1f)) {
            Text(
                counterpartName,
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = listOf(
                artistSubtitle,
                ThreadCounterpart.counterpartRole(viewerIsArtist),
            ).firstOrNull { it.isNotBlank() }.orEmpty()
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = AppTheme.type.subtitle, color = colors.ink4, maxLines = 1)
            }
        }
    }

    Spacer(Modifier.height(dimens.space.lg))
    if (context.bookingId != null) {
        BookingCard(context = context, artistScore = artistScore)
    } else {
        // The inquiry variant the design calls for, in place of the card rather
        // than under it: there is nothing agreed, and an empty card with dashes
        // in it would imply there is.
        Banner(
            title = "No booking yet — this is an inquiry.",
            detail = "Nothing is agreed until a request is sent and accepted.",
            tone = BannerTone.Promotion,
            modifier = Modifier.semantics { testTag = "threadDetails.inquiry" },
        )
    }

    Spacer(Modifier.height(dimens.space.lg))
    actionError?.let {
        Text(
            it,
            style = AppTheme.type.caption,
            color = colors.danger,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dimens.space.sm)
                .semantics { testTag = "threadDetails.actionError" },
        )
    }

    context.bookingId?.let { bookingId ->
        ListRow(
            title = "View booking",
            onClick = { onBookingClick(bookingId) },
            modifier = Modifier.semantics { testTag = "threadDetails.viewBooking" },
        )
    }
    ListRow(
        title = if (archived) "Unarchive conversation" else "Archive conversation",
        subtitle = "Removes it from the inbox and the badge",
        onClick = onToggleArchive,
        modifier = Modifier.semantics { testTag = "threadDetails.archive" },
    )
    // Mute is a SERVER column (mig 0091) that `send-push` honours, unlike the
    // two device-local flags around it — so its caption promises something that
    // actually survives a reinstall, and says plainly that it silences only your
    // side.
    ListRow(
        title = if (muted) "Unmute conversation" else "Mute conversation",
        subtitle = if (muted) {
            "You won't get notifications about this. $counterpartName still gets theirs."
        } else {
            "Stop notifications from this conversation on every device"
        },
        onClick = onToggleMute,
        modifier = Modifier.semantics { testTag = "threadDetails.mute" },
    )
    ListRow(
        title = "Mark as unread",
        subtitle = "Saved on this device",
        onClick = onMarkUnread,
        modifier = Modifier.semantics { testTag = "threadDetails.markUnread" },
    )
    ListRow(
        title = if (starred) "Remove star" else "Star this conversation",
        subtitle = "Saved on this device",
        onClick = onToggleStar,
        modifier = Modifier.semantics { testTag = "threadDetails.star" },
    )
    if (canBlock) {
        // Block sits below the housekeeping and above Report, because report is
        // the action that actually gets someone looked at — blocking only
        // changes what THIS person sees (mig 0087 is client-side filtering in
        // v1). The caption says so rather than letting the word "blocked" imply
        // a wall that isn't there yet.
        ListRow(
            title = if (blocked) "Unblock $counterpartName" else "Block $counterpartName",
            subtitle = if (blocked) {
                "Hidden from your inbox. They aren't told, and they can still message you."
            } else {
                "Private — they aren't told"
            },
            onClick = onStartBlock,
            modifier = Modifier.semantics { testTag = "threadDetails.block" },
        )
    }

    // Advice sits beside the remedies and above the report, because it is the
    // step before one: block and report both change something, and this is the
    // row for the person who does not yet know which of them they need. It is a
    // plain ListRow, not `danger` — reading about safety is not a destructive
    // act, and there is exactly one red thing on this sheet.
    ListRow(
        title = "Trust & safety",
        subtitle = "How to keep a booking safe, and what to do when it isn't",
        onClick = onOpenSafetyCentre,
        modifier = Modifier.semantics { testTag = "threadDetails.safetyCentre" },
    )

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.md))
            .clickable(onClick = onStartReport)
            .padding(vertical = dimens.space.lg)
            .semantics(mergeDescendants = true) { testTag = "threadDetails.report" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Icon(
            Icons.Outlined.Flag,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(dimens.size.iconLg),
        )
        Text(
            "Report this conversation",
            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
            color = colors.danger,
        )
    }
}

/**
 * The gig behind the thread, as a card (design 33).
 *
 * The one place card chrome earns its keep in a hairline-first design: this is a
 * distinct object being *referenced* by the conversation, not a row of it. The
 * fee is set at the same weight as its own label because it is the answer to the
 * question the whole sheet exists for.
 */
@Composable
private fun BookingCard(context: ThreadContext, artistScore: Int?) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(colors.surface3)
            .padding(dimens.space.lg)
            .semantics { testTag = "threadDetails.bookingCard" },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Booking behind this thread",
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.size(dimens.space.sm))
            StatusPill(label = context.statusLabel, tone = context.pillTone)
        }
        Spacer(Modifier.height(dimens.space.md))
        context.dateLabel?.let { DetailLine(label = "Date", value = it) }
        context.venue?.let { DetailLine(label = "Venue", value = it) }
        context.fee?.let {
            Spacer(Modifier.height(dimens.space.sm))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Artist fee", style = AppTheme.type.sectionTitle, color = colors.ink)
                Text(formatInr(it), style = AppTheme.type.sectionTitle, color = colors.ink)
            }
        }
        // Only when the score is real. A "Bookability score" row with a dash in
        // it on a thread whose artist hasn't loaded would be the app inventing a
        // reputation.
        artistScore?.let { score ->
            Spacer(Modifier.height(dimens.space.md))
            HRule()
            Spacer(Modifier.height(dimens.space.md))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            ) {
                Box(
                    Modifier
                        .size(dimens.size.iconXl)
                        .clip(CircleShape)
                        .background(colors.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(score.toString(), style = AppTheme.type.badge, color = colors.onAccent)
                }
                Text(
                    // The tier, not a percentile. Design 33 prints "top 8% in
                    // Bengaluru"; nothing in the schema ranks an artist against
                    // a city, and a made-up percentile on a trust surface is the
                    // worst kind of invented number. The band IS real — it is
                    // the same one the profile and the score explainer show.
                    "Bookability score · ${ScoreBands.tier(score).label}",
                    style = AppTheme.type.subtitle,
                    color = colors.ink2,
                )
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.space.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppTheme.type.body, color = colors.ink2)
        Text(
            value,
            style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The sheet's own bar: a centred title and one way out. */
@Composable
private fun SheetTitle(title: String, onClose: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier.fillMaxWidth().padding(bottom = dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(dimens.size.iconXl))
        Text(
            title,
            style = AppTheme.type.sectionTitle,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .size(dimens.size.iconXl)
                .clip(CircleShape)
                .background(colors.surface2)
                .clickable(onClick = onClose)
                .semantics { contentDescription = "Close"; testTag = "threadDetails.close" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
    }
}

/**
 * Confirm a block — and say what one actually is.
 *
 * The wording is load-bearing, not padding. Migration 0087 ships blocking as
 * CLIENT-SIDE FILTERING: the blocked person's conversations stop appearing in
 * your inbox. Server-side contact prevention was deliberately deferred rather
 * than amend the message-insert policy, so nothing here stops them sending, and
 * their pushes are not suppressed either — mute is the control for that. Someone
 * who blocks a person they feel unsafe around and believes it walled them off is
 * worse off than someone who was told the truth and reported them as well, which
 * is why report is named as the next step rather than left implied.
 *
 * Reword this only alongside the migration that changes what a block does.
 */
@Composable
private fun BlockConfirm(
    counterpartName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(Modifier.fillMaxWidth().semantics { testTag = "threadDetails.blockConfirm" }) {
        Text(
            "Your conversations with them stop showing in your inbox, and they aren't told.",
            style = AppTheme.type.body,
            color = colors.ink2,
        )
        Spacer(Modifier.height(dimens.space.sm))
        Text(
            "Blocking doesn't stop them sending messages or notifications — mute the " +
                "conversation for that, and report it if something's wrong.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
        Spacer(Modifier.height(dimens.space.lg))
        ListRow(
            title = "Block $counterpartName",
            destructive = true,
            onClick = onConfirm,
            modifier = Modifier.semantics { testTag = "threadDetails.blockConfirmed" },
        )
        ListRow(
            title = "Cancel",
            onClick = onCancel,
            showHairline = false,
            modifier = Modifier.semantics { testTag = "threadDetails.blockCancel" },
        )
    }
}

/**
 * The receipt.
 *
 * Worded to be true whichever way the write went. The reports repository
 * deliberately never throws — it falls back to an on-device log so a moderation
 * outage can't block a conversation — which means this surface genuinely cannot
 * distinguish delivered from queued, and must not claim to.
 */
@Composable
private fun ReportReceipt(counterpartName: String, outcome: ReportOutcome) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier.fillMaxWidth().semantics { testTag = "threadDetails.reportReceipt" },
    ) {
        Text(
            // Two different facts, and only one of them is a delivery. The
            // insert soft-fails into a local log, and telling a reporter their
            // report reached Artistant while it is sitting in DataStore is the
            // overclaim this whole branch exists to stop.
            when (outcome) {
                ReportOutcome.Sent -> "Thanks — the report is with our safety team."
                ReportOutcome.Queued ->
                    "Saved on this device. It hasn't reached our safety team yet — " +
                        "we'll send it the next time you're online."
                // Unreachable: Failed rides `failedReport` and renders
                // [ReportFailure]. Stated rather than defaulted, so a future
                // outcome cannot silently inherit the delivery sentence.
                ReportOutcome.Failed -> "This report isn't saved anywhere yet."
            },
            style = AppTheme.type.body,
            color = colors.ink,
        )
        Spacer(Modifier.height(dimens.space.sm))
        Text(
            "$counterpartName is never shown this report. If you also want them out of your " +
                "inbox, block them from this sheet.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
}

/**
 * A report nothing is holding.
 *
 * Not a receipt and not a toast. The insert failed and so did the on-device log,
 * so the only true sentence is that the report does not exist — and that is a
 * state with an action attached, not a message that fades. The reader's own
 * words are kept behind [onRetry] so re-filing does not ask them to write, a
 * second time, about something that already upset them enough to report.
 *
 * Discard is a deliberate second control rather than a timeout: the banner must
 * not disappear on its own while what it says is still true.
 */
@Composable
private fun ReportFailure(onRetry: () -> Unit, onDiscard: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier.fillMaxWidth().semantics { testTag = "threadDetails.reportFailure" },
    ) {
        Banner(
            title = "Your report wasn't sent",
            detail = "It didn't reach our safety team, and this device couldn't hold on to " +
                "it either. Nothing about this conversation has been reported yet.",
            tone = BannerTone.Failure,
            actionLabel = "Try again",
            onAction = onRetry,
            modifier = Modifier.semantics { testTag = "threadDetails.reportRetry" },
        )
        Spacer(Modifier.height(dimens.space.md))
        Text(
            "Discard this report",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink3,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .clickable(onClick = onDiscard)
                .padding(vertical = dimens.space.md)
                .semantics { testTag = "threadDetails.reportDiscard" },
        )
    }
}

/**
 * The pill tone for a thread's booking status.
 *
 * Derived from the app's ONE status→tone mapping ([bookingStatusTone]) rather
 * than a second `when` over the same enum, so a thread and its booking can never
 * disagree about what colour "confirmed" is — which is exactly the bug a parallel
 * table invites the first time a status is added.
 */
private val ThreadContext.pillTone: StatusTone
    get() = when (status?.let(::bookingStatusTone)) {
        null, PillTone.Neutral -> StatusTone.Neutral
        PillTone.Brand, PillTone.BrandSolid -> StatusTone.Live
        PillTone.Warm -> StatusTone.Pending
        PillTone.Good -> StatusTone.Done
        PillTone.Hot -> StatusTone.Failed
    }
