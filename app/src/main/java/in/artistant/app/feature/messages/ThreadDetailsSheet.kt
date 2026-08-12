package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.LockOpen
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.repository.ReportReasons
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.hairlineBottom
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.tierColor

/**
 * "What is this thread about, and who is in it" — from the chat header's Details
 * pill.
 *
 * It exists because a lot of context was latent: the gig, its date, its venue,
 * its fee, and the counterparty's standing were all one screen away with no way
 * in. The conversation actions below it are device-local and really do persist,
 * which is the bar for including them at all — a Star that forgets itself on
 * relaunch is worse than no Star.
 *
 * Report is the last row and stays in the same neutral style as the others. It
 * is a rare, serious action, not the sheet's primary one, and lime is this
 * system's single "do the positive thing" signal — a filled brand button here
 * would make reporting the loudest thing on the sheet and read as encouragement.
 * It opens a reason picker rather than filing anything, so it cannot be a
 * one-tap accident.
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
    reportSubmitted: Boolean,
    onBookingClick: (String) -> Unit,
    onToggleStar: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onMarkUnread: () -> Unit,
    onReport: (reason: String) -> Unit,
    onDismiss: () -> Unit,
    /** Non-null only on the client seat — an artist's counterpart has no profile. */
    artistId: String? = null,
    artistSubtitle: String = "",
    artistScore: Int? = null,
    onArtistClick: (String) -> Unit = {},
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
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
        containerColor = colors.bg,
    ) {
        // Outside the scrolling Column on purpose: it is the sheet's bar, and a
        // title that scrolls away takes the only dismiss control with it.
        SheetHeader(title = "Details", onDone = onDismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.xl)
                .padding(bottom = space.xxl)
                .semantics { testTag = "chat.detailsSheet" },
        ) {
            SectionHeader("Gig details")
            GigCard(context = context, onBookingClick = onBookingClick)

            SectionHeader("In this conversation")
            if (artistId != null) {
                ParticipantRow(
                    name = counterpartName,
                    role = ThreadCounterpart.counterpartRole(viewerIsArtist),
                    subtitle = artistSubtitle,
                    score = artistScore,
                    onClick = { onArtistClick(artistId) },
                )
            } else {
                ParticipantRow(
                    name = counterpartName,
                    role = ThreadCounterpart.counterpartRole(viewerIsArtist),
                )
            }
            ParticipantRow(name = "You", role = ThreadCounterpart.viewerRole(viewerIsArtist))

            SectionHeader("Conversation")
            when {
                reportSubmitted -> ReportReceipt()
                reporting -> ReportReasonPicker(
                    counterpartName = counterpartName,
                    onPick = onReport,
                )
                blocking -> BlockConfirm(
                    counterpartName = counterpartName,
                    onConfirm = {
                        onToggleBlock()
                        blocking = false
                    },
                    onCancel = { blocking = false },
                )
                else -> {
                    // Labels flip with state so a row always reads as the action
                    // it will perform, not the state it is in.
                    ActionRow(
                        label = if (starred) "Unstar" else "Star",
                        icon = if (starred) Icons.Filled.Star else Icons.Filled.StarBorder,
                        tint = if (starred) colors.warm else colors.ink,
                        tag = "threadDetails.star",
                        onClick = onToggleStar,
                    )
                    // Mute sits second, directly under Star, matching the
                    // reference's order — the two rows that change how loudly a
                    // conversation reaches you belong together, above the two
                    // that change where it sits.
                    //
                    // Labelled "Mute", not "Mute notifications": one word is
                    // what the reference says, and the longer label was doing a
                    // job the caption already does properly. A muted thread
                    // still shows in the inbox and still counts as unread, and
                    // the obvious wrong reading — that the other person has been
                    // silenced too — needs a sentence, not an extra noun.
                    ActionRow(
                        label = if (muted) "Unmute" else "Mute",
                        icon = if (muted) Icons.Filled.NotificationsOff else Icons.Filled.Notifications,
                        tint = if (muted) colors.ink3 else colors.ink,
                        tag = "threadDetails.mute",
                        caption = if (muted) {
                            "You won't get notifications about this conversation. " +
                                "$counterpartName still gets theirs."
                        } else {
                            null
                        },
                        onClick = onToggleMute,
                    )
                    ActionRow(
                        label = "Mark as unread",
                        icon = Icons.Filled.MarkEmailUnread,
                        tag = "threadDetails.markUnread",
                        onClick = onMarkUnread,
                    )
                    ActionRow(
                        label = if (archived) "Unarchive" else "Archive",
                        icon = if (archived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                        tag = "threadDetails.archive",
                        onClick = onToggleArchive,
                    )
                    ActionRow(
                        label = "Report conversation",
                        icon = Icons.Outlined.Flag,
                        tag = "threadDetails.report",
                        onClick = { reporting = true },
                    )
                    // Block sits last, below Report, because report is the action
                    // that actually gets someone looked at — blocking only
                    // changes what THIS person sees (mig 0087 is client-side
                    // filtering in v1). The caption on the blocked state says so
                    // rather than letting the word "blocked" imply a wall that
                    // isn't there yet.
                    if (canBlock) {
                        ActionRow(
                            label = if (blocked) "Unblock $counterpartName" else "Block $counterpartName",
                            icon = if (blocked) Icons.Outlined.LockOpen else Icons.Outlined.Block,
                            tint = if (blocked) colors.ink3 else colors.ink,
                            tag = "threadDetails.block",
                            caption = if (blocked) {
                                "Hidden from your inbox. They aren't told, and they can still " +
                                    "message you."
                            } else {
                                null
                            },
                            onClick = { if (blocked) onToggleBlock() else blocking = true },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The gig, as a card.
 *
 * The one place card chrome earns its keep in a hairline-first design: this is a
 * distinct object being *referenced* by the conversation, not a row of it.
 */
@Composable
private fun GigCard(context: ThreadContext, onBookingClick: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val bookingId = context.bookingId

    if (bookingId == null) {
        // Genuinely nothing agreed yet — say so rather than render an empty card.
        Text(
            "No booking yet — this is an inquiry.",
            style = AppTheme.type.body,
            color = colors.ink3,
            modifier = Modifier.padding(vertical = space.sm),
        )
        return
    }

    val shape = RoundedCornerShape(AppTheme.dimens.radii.md)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.bgCard)
            .border(AppTheme.dimens.size.hairline, colors.line, shape)
            .clickable { onBookingClick(bookingId) }
            .padding(space.md)
            .semantics { testTag = "threadDetails.gig" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(context.statusLabel, style = AppTheme.type.callout, color = colors.ink)
            Text(
                context.detailLine ?: "Details in the booking",
                style = AppTheme.type.monoSmall,
                color = colors.ink3,
            )
            context.fee?.let {
                Spacer(Modifier.height(AppTheme.dimens.space.xs))
                Text(
                    "${formatInr(it)} artist fee",
                    style = AppTheme.type.footnote,
                    color = colors.brand,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(AppTheme.dimens.size.iconMd),
        )
    }
}

/**
 * One participant: avatar, name, and the seat they occupy.
 *
 * The role caption is what makes the two rows unambiguous when both names are
 * unfamiliar, and the roles are always opposites — see [ThreadCounterpart].
 */
@Composable
private fun ParticipantRow(
    name: String,
    role: String,
    subtitle: String = "",
    score: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            // The rule belongs to the row rather than following it, so the row's
            // pitch is its height — see [hairlineBottom].
            .hairlineBottom()
            .padding(vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Avatar(name = name, size = AppTheme.dimens.size.avatarSm)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = AppTheme.type.body,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle.ifBlank { role }.uppercase(),
                style = AppTheme.type.monoSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        score?.let {
            // Tier-coloured so the number carries the same meaning it does on the
            // artist profile and the Discover tiles.
            Text(
                it.toString(),
                style = AppTheme.type.monoMedium,
                color = tierColor(ScoreBands.tier(it), colors),
                modifier = Modifier.semantics { contentDescription = "Bookability score $it" },
            )
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier.size(AppTheme.dimens.size.iconMd),
            )
        }
    }
}

/**
 * One tappable action.
 *
 * [caption] is the slot for a row whose consequence isn't self-evident from its
 * label — currently mute and block, both of which people reliably assume do more
 * than they do. It renders only when the state it describes is actually in
 * effect, so an inert row stays a single line.
 */
@Composable
private fun ActionRow(
    label: String,
    icon: ImageVector,
    tag: String,
    onClick: () -> Unit,
    tint: Color = AppTheme.colors.ink,
    caption: String? = null,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Drawn on the row's bottom edge rather than laid out under it, so a
            // run of these rows has no per-row unit of divider height in it —
            // see [hairlineBottom]. This was the whole of the remaining pitch
            // difference against the reference on this sheet.
            .hairlineBottom()
            .padding(vertical = space.md)
            .semantics { testTag = tag },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(AppTheme.dimens.size.iconLg),
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = AppTheme.type.body, color = colors.ink)
            caption?.let {
                Text(it, style = AppTheme.type.footnote, color = colors.ink3)
            }
        }
    }
}

/** Reason first, then file. Reporting should never be a single mis-tap. */
@Composable
private fun ReportReasonPicker(counterpartName: String, onPick: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Why are you reporting this conversation?",
            style = AppTheme.type.callout,
            color = colors.ink,
        )
        Spacer(Modifier.height(space.sm))
        ReportReasons.forEach { reason ->
            Text(
                reason,
                style = AppTheme.type.body,
                color = colors.ink2,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .clickable { onPick(reason) }
                    .padding(vertical = space.md)
                    .semantics { testTag = "threadDetails.reason" },
            )
            HRule()
        }
        Spacer(Modifier.height(space.sm))
        Text(
            "This won't be shared with $counterpartName.",
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )
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
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxWidth().semantics { testTag = "threadDetails.blockConfirm" }) {
        Text("Block $counterpartName?", style = AppTheme.type.callout, color = colors.ink)
        Spacer(Modifier.height(space.xs))
        Text(
            "Your conversations with them stop showing in your inbox, and they aren't told.",
            style = AppTheme.type.body,
            color = colors.ink2,
        )
        Spacer(Modifier.height(space.xs))
        Text(
            "Blocking doesn't stop them sending messages or notifications — mute the " +
                "conversation for that, and report it if something's wrong.",
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )
        Spacer(Modifier.height(space.md))
        Text(
            "Block $counterpartName",
            style = AppTheme.type.body,
            color = colors.ink,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                .clickable(onClick = onConfirm)
                .padding(vertical = space.md)
                .semantics { testTag = "threadDetails.blockConfirmed" },
        )
        HRule()
        Text(
            "Cancel",
            style = AppTheme.type.body,
            color = colors.ink3,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                .clickable(onClick = onCancel)
                .padding(vertical = space.md)
                .semantics { testTag = "threadDetails.blockCancel" },
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
private fun ReportReceipt() {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxWidth().padding(vertical = space.sm)) {
        Text("Report saved.", style = AppTheme.type.callout, color = colors.ink)
        Text(
            "Our team reviews reports to keep Artistant trustworthy. It's never shared with them.",
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )
    }
}

/**
 * The sheet's bar: title centred, an explicit Done at the trailing edge.
 *
 * The drag handle alone was the only way out of here, and a grabber is a hint
 * about a gesture rather than a control — it is discoverable if you already know
 * the pattern and invisible if you don't. It stays (the reference shows one too);
 * this just adds the affordance that names the action.
 *
 * Centred rather than left-aligned because with something at one end and nothing
 * at the other, a left title makes the bar look like it lost a control. The Done
 * target is padded out to the row minimum — the word is short and the glyph-free
 * hit area would otherwise be about half a thumb.
 */
@Composable
private fun SheetHeader(title: String, onDone: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = space.xl)
            .padding(bottom = space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = AppTheme.type.headline, color = colors.ink)
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .clip(CircleShape)
                .clickable(onClick = onDone)
                .heightIn(min = dimens.size.rowMin)
                .padding(horizontal = space.sm)
                .semantics { contentDescription = "Done" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Done",
                style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = colors.brand,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    val space = AppTheme.dimens.space
    Spacer(Modifier.height(space.lg))
    Text(
        title.uppercase(),
        style = AppTheme.type.caption,
        color = AppTheme.colors.ink3,
    )
    Spacer(Modifier.height(space.sm))
    HRule()
    Spacer(Modifier.height(space.sm))
}
