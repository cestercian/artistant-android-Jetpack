package `in`.artistant.app.feature.messages

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The archive (designs 60 and 111).
 *
 * **Archiving has no server column.** `threads` carries `client_muted` and
 * `artist_muted` and nothing else a reader can set, so the flag is device-local
 * ([ThreadFlagsStore], DataStore). That is stated on the screen rather than
 * hidden: an archive that silently fails to follow you to another device is
 * worse than one that says it won't.
 *
 * **The badge rule.** Screen 60 documents it on the screen that could break it:
 * archived threads are excluded from the Messages badge, so the count can never
 * exceed what the inbox shows. It holds by construction, not by discipline —
 * everything the inbox counts is derived from `MessagesUiState.activeThreads`,
 * which is `threads` minus the archived ones, and there is no second path that
 * counts the raw list. `MessagesInboxViewModelTest` pins it.
 */
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val items = state.archivedThreads

    Column(modifier.fillMaxSize().background(colors.page)) {
        BackHeader(
            title = "Archived",
            subtitle = when {
                !state.hasLoaded -> null
                items.isEmpty() -> "Nothing archived"
                items.size == 1 -> "1 conversation"
                else -> "${items.size} conversations"
            },
            onBack = onBack,
            centered = false,
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && !state.hasLoaded -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = colors.accentInk) }

                items.isEmpty() -> EmptyState(
                    title = "No archived threads",
                    // The only place archiving can be explained, so it is
                    // explained here (design 111's note).
                    body = "Swipe any conversation in your inbox to archive it. Archived " +
                        "threads never count toward your unread badge.",
                    actionLabel = "Back to Messages",
                    onAction = onBack,
                    icon = Icons.Filled.Inventory2,
                    modifier = Modifier.semantics { testTag = "archived.empty" },
                )

                else -> ArchivedList(
                    items = items,
                    onOpen = onThreadClick,
                    onUnarchive = viewModel::toggleArchived,
                )
            }
        }

        // The rule, on the screen that could break it (design 60).
        if (items.isNotEmpty()) {
            HRule()
            Text(
                "Archived threads are excluded from the Messages badge, so the count can never " +
                    "exceed what the inbox shows. Archiving is saved on this device only.",
                style = AppTheme.type.caption,
                color = colors.ink4,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimens.component.gutter)
                    .semantics { testTag = "archived.badgeRule" },
            )
        }
    }
}

@Composable
private fun ArchivedList(
    items: List<ThreadListItem>,
    onOpen: (String) -> Unit,
    onUnarchive: (String) -> Unit,
) {
    val dimens = AppTheme.dimens
    LazyColumn(Modifier.fillMaxSize()) {
        items(items, key = { it.thread.id }) { item ->
            ArchivedRow(
                item = item,
                onOpen = { onOpen(item.thread.id) },
                onUnarchive = { onUnarchive(item.thread.id) },
            )
        }
        item(key = "archived.tailroom") {
            Spacer(Modifier.height(dimens.size.listTailroom))
        }
    }
}

/**
 * One archived conversation, with the way back out on the row itself.
 *
 * Unarchive is a word rather than an icon button: the design draws a labelled
 * pill, and an icon here would be the second unlabelled glyph on a screen whose
 * entire job is undoing something. It is `surface2`, not accent — putting the
 * app's one signal on "unarchive everything" would make the archive read as a
 * mistake to be reversed rather than a place things live.
 */
@Composable
private fun ArchivedRow(
    item: ThreadListItem,
    onOpen: () -> Unit,
    onUnarchive: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val locale = LocalConfiguration.current.locales.get(0) ?: Locale.getDefault()
    val stamp = remember(item.thread.lastMessageAtEpochMs, locale) {
        item.thread.lastMessageAtEpochMs
            ?.let { SimpleDateFormat("d MMM", locale).format(Date(it)) }
            .orEmpty()
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = dimens.component.gutter, vertical = dimens.space.md)
                .semantics { testTag = "archived.row" },
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = item.counterpartName, size = dimens.size.avatarMd)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.counterpartName,
                        style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (stamp.isNotEmpty()) {
                        Spacer(Modifier.width(dimens.space.sm))
                        Text(stamp, style = AppTheme.type.caption, color = colors.ink4)
                    }
                }
                Spacer(Modifier.height(dimens.space.xs))
                Text(
                    item.thread.lastPreview.ifBlank { "No messages yet" },
                    style = AppTheme.type.subtitle,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                "Unarchive",
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
                modifier = Modifier
                    // A word is not a button, so nothing gives it a touch target
                    // for free: the word keeps its size and the tap node around
                    // it is grown to the floor.
                    .heightIn(min = dimens.size.rowMin)
                    .clip(CircleShape)
                    .background(colors.surface2)
                    .clickable(onClick = onUnarchive)
                    .wrapContentHeight()
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.sm)
                    .semantics { testTag = "archived.unarchive" },
            )
        }
        HRule(
            modifier = Modifier.padding(
                start = dimens.component.gutter + dimens.size.avatarMd + dimens.space.md,
                end = dimens.component.gutter,
            ),
        )
    }
}
