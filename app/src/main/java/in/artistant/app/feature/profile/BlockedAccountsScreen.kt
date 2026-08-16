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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.booking.FunnelHeader

/**
 * Blocked accounts — reached from the account settings list on both roles.
 *
 * THE DEAD END THIS CLOSES. Blocking (migration 0087) filters the blocked
 * person's conversations out of the inbox at the point rows are built, and the
 * only Unblock control shipped inside that conversation. So a block put the undo
 * behind the very door it had just locked: block by mistake, leave the chat, and
 * nothing in the app could reverse it. This screen is the exit.
 *
 * COPY RULES. Every line here matches what a block actually does in v1 — client-
 * side filtering, nothing more. It does not stop them sending, does not suppress
 * their notifications, and does not tell them. The chat's confirm sheet says the
 * same thing in the same words on purpose; if a future migration changes what a
 * block does, both surfaces change together.
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
    viewModel: BlockedAccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val size = AppTheme.dimens.size

    Column(modifier.fillMaxSize().background(colors.bg)) {
        FunnelHeader(title = "Blocked accounts", onBack = onBack)

        when (state.status) {
            BlockedAccountsStatus.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.brand)
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
                        actionLabel = "Retry",
                        onAction = viewModel::refresh,
                    )
                }

            BlockedAccountsStatus.Ready, BlockedAccountsStatus.Stale ->
                RevealOnAppear {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = space.xl),
                    ) {
                        Text(
                            "WHAT BLOCKING DOES",
                            style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.ink3,
                        )
                        Spacer(Modifier.height(space.sm))
                        Text(
                            "Blocked people don't show up in your inbox. They aren't told, and " +
                                "they can still send you messages and notifications — mute a " +
                                "conversation for that, and report it if something's wrong.",
                            style = AppTheme.type.footnote,
                            color = colors.ink2,
                        )
                        Spacer(Modifier.height(space.xl))

                        // A list that is on screen but might be out of date. It
                        // is still worth showing — these rows came off this
                        // device's own mirror — but it must not present itself
                        // as the whole truth.
                        if (state.status == BlockedAccountsStatus.Stale) {
                            StaleNotice(onRetry = viewModel::refresh)
                        }

                        state.actionError?.let { message ->
                            Text(
                                message,
                                style = AppTheme.type.footnote,
                                color = colors.hot,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = viewModel::clearActionError)
                                    .padding(bottom = space.md)
                                    .semantics { testTag = "blockedAccounts.actionError" },
                            )
                        }

                        if (state.rows.isEmpty()) {
                            // Reached only when the server answered, so this can
                            // safely claim the list is empty.
                            Box(Modifier.semantics { testTag = "blockedAccounts.empty" }) {
                                EmptyState(
                                    title = "No one is blocked",
                                    body = "When you block someone from a conversation, they'll " +
                                        "appear here so you can undo it.",
                                )
                            }
                        } else {
                            HRule()
                            state.rows.forEach { row ->
                                BlockedAccountRowUi(
                                    row = row,
                                    onUnblock = { viewModel.unblock(row.userId) },
                                )
                                HRule()
                            }
                            if (state.hasUnnamedRows) {
                                Spacer(Modifier.height(space.md))
                                // Says what is missing instead of inventing a
                                // name for it. A block stores an account, not a
                                // profile — see BlockedAccountRow.
                                Text(
                                    "Some names aren't available. Blocking records the account, " +
                                        "not their profile, so a name only shows when we can " +
                                        "still read your conversation with them.",
                                    style = AppTheme.type.footnote,
                                    color = colors.ink3,
                                )
                            }
                        }

                        Spacer(Modifier.height(size.listTailroom))
                    }
                }
        }
    }
}

/**
 * One blocked person plus the way to release them.
 *
 * Unblock is a plain text action rather than a filled button: lime is this
 * system's single positive-action signal, and a screen with five lime buttons on
 * it would make "unblock everyone" look like the recommended move. One tap, no
 * confirmation — undoing a mistake shouldn't need a second ceremony, and the
 * block itself already had one.
 */
@Composable
private fun BlockedAccountRowUi(
    row: BlockedAccountRow,
    onUnblock: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val name = row.displayName
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = space.md)
            .semantics { testTag = "blockedAccounts.row" },
        horizontalArrangement = Arrangement.spacedBy(space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar falls back to its own initials-from-name rendering; with no
        // name it draws the neutral placeholder, which is the correct look for
        // a person we can't identify.
        Avatar(name = name.orEmpty(), size = AppTheme.dimens.size.avatarSm)
        Column(Modifier.weight(1f)) {
            Text(
                name ?: "Blocked account",
                style = AppTheme.type.body,
                color = if (name != null) colors.ink else colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Second line: the role when we know it, and the id fragment only
            // when there's no name — two nameless rows have to be tellable
            // apart, a named row doesn't need a uuid next to it.
            val subtitle = listOfNotNull(
                row.role?.uppercase(),
                if (name == null) "ID ${row.shortId}" else null,
            ).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = AppTheme.type.monoSmall,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            "Unblock",
            style = AppTheme.type.callout,
            color = colors.brand,
            modifier = Modifier
                // A word is not a button, so nothing gives it a touch target for
                // free: the word keeps its size and the tap node around it is
                // grown to the floor. This is the only action on a safety screen.
                .heightIn(min = AppTheme.dimens.size.rowMin)
                .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                .clickable(onClick = onUnblock)
                .wrapContentHeight()
                .padding(horizontal = space.md, vertical = space.sm)
                .semantics { testTag = "blockedAccounts.unblock" },
        )
    }
}

/** The list is showing, but it's this device's copy and the server didn't answer. */
@Composable
private fun StaleNotice(onRetry: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = space.lg)
            .semantics { testTag = "blockedAccounts.stale" },
        verticalArrangement = Arrangement.spacedBy(space.xs),
    ) {
        Text(
            "Showing this device's copy — we couldn't refresh your list, so it may be out of date.",
            style = AppTheme.type.footnote,
            color = colors.warm,
        )
        Text(
            "Retry",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
            color = colors.brand,
            modifier = Modifier
                // Same reason as Unblock: a footnote-sized word needs the tap
                // node grown around it to clear the touch floor.
                .heightIn(min = AppTheme.dimens.size.rowMin)
                .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                .clickable(onClick = onRetry)
                .wrapContentHeight()
                .padding(vertical = space.xs)
                .semantics { testTag = "blockedAccounts.retry" },
        )
    }
}
