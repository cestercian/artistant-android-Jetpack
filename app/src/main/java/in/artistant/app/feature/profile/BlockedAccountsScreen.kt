package `in`.artistant.app.feature.profile

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Blocked accounts (design 127) — "block ≠ report".
 *
 * THE DEAD END THIS CLOSES. Blocking (migration 0087) filters the blocked
 * person's conversations out of the inbox at the point rows are built, and the
 * only Unblock control shipped inside that conversation. So a block put the undo
 * behind the very door it had just locked: block by mistake, leave the chat, and
 * nothing in the app could reverse it. This screen is the exit.
 *
 * COPY RULES. Every line here matches what a block actually does in v1 — client-
 * side filtering, nothing more. It does not stop them sending, does not suppress
 * their notifications, and does not tell them. **The design's own banner claims
 * more than that** ("can't message you, see your profile, or appear in your
 * search results"), and that claim is not shipped: someone who believes it and
 * then receives a message from the person they blocked has been told something
 * false about their own safety. The banner below says what is true and the
 * design's wording becomes correct the day a migration makes it so. The chat's
 * confirm sheet says the same thing in the same words on purpose; if that
 * migration lands, both surfaces change together.
 *
 * EMPTY IS NOT THE SAME AS UNKNOWN. Most people will open this and see nothing,
 * so the empty state has to name itself ("No one is blocked") — an empty screen
 * with no words reads as broken. But a failed load is ALSO nothing, and saying
 * "no one is blocked" to someone whose list we simply couldn't fetch is a lie
 * that would leave them thinking a block they made had evaporated. The two are
 * separate states with separate copy, and the failure offers a retry.
 */
@Composable
fun BlockedAccountsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Straight to the conversation list, where a report can actually be filed. */
    onReportConversation: (() -> Unit)? = null,
    viewModel: BlockedAccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    Column(modifier.fillMaxSize().background(colors.page)) {
        BackHeader(
            title = "Blocked accounts",
            subtitle = when (state.status) {
                BlockedAccountsStatus.Loading -> null
                BlockedAccountsStatus.Unavailable -> "Couldn't load"
                else -> if (state.rows.size == 1) "1 blocked" else "${state.rows.size} blocked"
            },
            onBack = onBack,
            centered = false,
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
        )

        when (state.status) {
            BlockedAccountsStatus.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accentInk)
                }

            // The list could not be read. Deliberately worded as an absence of
            // ANSWER, not an absence of blocked people, and it carries the only
            // Retry on the screen that is worth pressing.
            BlockedAccountsStatus.Unavailable ->
                Box(
                    Modifier.fillMaxSize().semantics { testTag = "blockedAccounts.unavailable" },
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = "Couldn't load your blocked accounts",
                        body = "This isn't the same as nobody being blocked — we couldn't reach " +
                            "your list to find out. Anyone you've blocked stays blocked.",
                        // A retry that fails resolves to the screen it started
                        // on, so with no in-flight label there is nothing to
                        // tell a working button from a dead one — and offline is
                        // the state this control exists for.
                        actionLabel = if (state.isRefreshing) "Retrying…" else "Retry",
                        onAction = { if (!state.isRefreshing) viewModel.refresh() },
                        icon = Icons.Outlined.Block,
                    )
                }

            BlockedAccountsStatus.Ready, BlockedAccountsStatus.Stale ->
                RevealOnAppear {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = dimens.component.gutter),
                    ) {
                        Spacer(Modifier.height(dimens.space.md))
                        Banner(
                            title = "A blocked account stops appearing in your inbox, and they " +
                                "aren't told.",
                            detail = "In this version they can still send you messages and " +
                                "notifications — mute a conversation for that.",
                            tone = BannerTone.Promotion,
                        )
                        Spacer(Modifier.height(dimens.space.lg))

                        // A list that is on screen but might be out of date. It
                        // is still worth showing — these rows came off this
                        // device's own mirror — but it must not present itself
                        // as the whole truth.
                        if (state.status == BlockedAccountsStatus.Stale) {
                            StaleNotice(
                                isRetrying = state.isRefreshing,
                                onRetry = viewModel::refresh,
                            )
                        }

                        state.actionError?.let { message ->
                            Text(
                                message,
                                style = AppTheme.type.caption,
                                color = colors.danger,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = viewModel::clearActionError)
                                    .padding(bottom = dimens.space.md)
                                    .semantics { testTag = "blockedAccounts.actionError" },
                            )
                        }

                        if (state.rows.isEmpty()) {
                            // Either the server answered and there is nobody, or
                            // this device's copy has been emptied by the Unblock
                            // above while the server was unreachable. The claim
                            // is safe in both: the stale case carries the notice
                            // that qualifies it, one line up.
                            Box(Modifier.semantics { testTag = "blockedAccounts.empty" }) {
                                EmptyState(
                                    title = "No one is blocked",
                                    body = "When you block someone from a conversation, they'll " +
                                        "appear here so you can undo it.",
                                    icon = Icons.Outlined.Block,
                                )
                            }
                        } else {
                            state.rows.forEach { row ->
                                BlockedAccountRowUi(
                                    row = row,
                                    onUnblock = { viewModel.unblock(row.userId) },
                                )
                            }
                            if (state.hasUnnamedRows) {
                                Spacer(Modifier.height(dimens.space.md))
                                // Says what is missing instead of inventing a
                                // name for it. A block stores an account, not a
                                // profile — see BlockedAccountRow.
                                Text(
                                    "Some names aren't available. Blocking records the account, " +
                                        "not their profile, so a name only shows when we can " +
                                        "still read your conversation with them.",
                                    style = AppTheme.type.caption,
                                    color = colors.ink4,
                                )
                            }
                        }

                        // "Block ≠ report", stated. People block and assume
                        // someone was told; this is the sentence that corrects
                        // that, and it carries the remedy rather than describing
                        // one.
                        Spacer(Modifier.height(dimens.space.xl))
                        EyebrowLabel("IF SOMETHING SERIOUS HAPPENED")
                        Spacer(Modifier.height(dimens.space.sm))
                        Text(
                            "Blocking is private housekeeping. It doesn't alert our safety team.",
                            style = AppTheme.type.subtitle,
                            color = colors.ink2,
                        )
                        onReportConversation?.let { report ->
                            Spacer(Modifier.height(dimens.space.sm))
                            Text(
                                "Report the conversation",
                                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
                                color = colors.accentInk,
                                modifier = Modifier
                                    .heightIn(min = dimens.size.rowMin)
                                    .clip(CircleShape)
                                    .clickable(onClick = report)
                                    .wrapContentHeight()
                                    .padding(vertical = dimens.space.sm)
                                    .semantics { testTag = "blockedAccounts.report" },
                            )
                        }

                        Spacer(Modifier.height(dimens.space.xl))
                        HRule()
                        Spacer(Modifier.height(dimens.space.md))
                        Text(
                            "Blocked threads are excluded from your unread badge, same as " +
                                "archived ones.",
                            style = AppTheme.type.caption,
                            color = colors.ink4,
                        )
                        Spacer(Modifier.height(dimens.size.listTailroom))
                    }
                }
        }
    }
}

/**
 * One blocked person plus the way to release them.
 *
 * Unblock is a labelled `surface2` pill, not the accent: lime is this system's
 * single positive-action signal, and a screen with five lime buttons on it would
 * make "unblock everyone" look like the recommended move. One tap, no
 * confirmation — undoing a mistake shouldn't need a second ceremony, and the
 * block itself already had one.
 */
@Composable
private fun BlockedAccountRowUi(
    row: BlockedAccountRow,
    onUnblock: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val name = row.displayName
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.space.md)
                .semantics { testTag = "blockedAccounts.row" },
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar falls back to its own initials-from-name rendering; with no
            // name it draws the neutral placeholder, which is the correct look for
            // a person we can't identify.
            Avatar(name = name.orEmpty(), size = dimens.size.avatarMd)
            Column(Modifier.weight(1f)) {
                Text(
                    name ?: "Unnamed account",
                    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                    color = if (name != null) colors.ink else colors.ink2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Second line: the role when we know it, and the id fragment only
                // when there's no name — two nameless rows have to be tellable
                // apart, a named row doesn't need a uuid next to it. There is
                // deliberately NO "Blocked 12 Jul" here, unlike the design:
                // `blocked_users` carries no timestamp this client reads, and a
                // date is not something to guess at on a safety screen.
                val subtitle = listOfNotNull(
                    row.role,
                    if (name == null) "ID ${row.shortId}" else null,
                ).joinToString(" · ")
                if (subtitle.isNotEmpty()) {
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
                "Unblock",
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                modifier = Modifier
                    // A word is not a button, so nothing gives it a touch target
                    // for free: the word keeps its size and the tap node around
                    // it is grown to the floor. This is the only action on a
                    // safety screen.
                    .heightIn(min = dimens.size.rowMin)
                    .clip(CircleShape)
                    .background(colors.surface2)
                    .clickable(onClick = onUnblock)
                    .wrapContentHeight()
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.sm)
                    .semantics { testTag = "blockedAccounts.unblock" },
            )
        }
        HRule()
    }
}

/** The list is showing, but it's this device's copy and the server didn't answer. */
@Composable
private fun StaleNotice(isRetrying: Boolean, onRetry: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = dimens.space.lg)
            .semantics { testTag = "blockedAccounts.stale" },
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Text(
            "Showing this device's copy — we couldn't refresh your list, so it may be out of date.",
            style = AppTheme.type.caption,
            color = colors.warm,
        )
        Text(
            // The retry says so while it runs. Offline it resolves back to this
            // same notice, and a control that never acknowledges the tap reads
            // as broken — which is the wrong impression to leave on the only way
            // out of an accidental block.
            if (isRetrying) "Retrying…" else "Retry",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
            color = if (isRetrying) colors.ink4 else colors.accentInk,
            modifier = Modifier
                // Same reason as Unblock: a footnote-sized word needs the tap
                // node grown around it to clear the touch floor.
                .heightIn(min = dimens.size.rowMin)
                .clip(CircleShape)
                .clickable(enabled = !isRetrying, onClick = onRetry)
                .wrapContentHeight()
                .padding(vertical = dimens.space.xs)
                .semantics { testTag = "blockedAccounts.retry" },
        )
    }
}
