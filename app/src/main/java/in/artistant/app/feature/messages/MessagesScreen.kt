package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.theme.AppTheme

/** M4 inbox: server-backed thread list + All / Bookings / Inquiries chips. */
@Composable
fun MessagesScreen(
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val visible = state.visibleThreads
    when {
        state.isLoading && state.threads.isEmpty() -> Box(
            modifier.fillMaxSize().background(colors.bg),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = colors.brand) }
        state.error != null && state.threads.isEmpty() -> EmptyState(
            title = "Couldn't load messages",
            body = state.error,
            actionLabel = "Retry",
            onAction = viewModel::refresh,
        )
        state.threads.isEmpty() -> EmptyState(
            title = "You don't have any messages",
            body = "New conversations with artists show up here.",
            actionLabel = "Refresh",
            onAction = viewModel::refresh,
        )
        else -> RevealOnAppear {
            Column(modifier.fillMaxSize().background(colors.bg)) {
                Text(
                    "Messages",
                    style = AppTheme.type.displaySub,
                    color = colors.ink,
                    modifier = Modifier.padding(space.lg),
                )
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = space.lg),
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                ) {
                    MessagesFilter.entries.forEach { filter ->
                        val selected = state.filter == filter
                        val shape = RoundedCornerShape(AppTheme.dimens.radii.xl)
                        Text(
                            text = filter.name,
                            style = AppTheme.type.caption,
                            color = if (selected) colors.brandInk else colors.ink2,
                            modifier = Modifier
                                .background(if (selected) colors.brand else colors.bgSoft, shape)
                                .border(1.dp, if (selected) colors.brand else colors.lineSoft, shape)
                                .clickable { viewModel.setFilter(filter) }
                                .padding(horizontal = space.md, vertical = space.sm),
                        )
                    }
                }
                Spacer(Modifier.height(space.md))
                if (visible.isEmpty()) {
                    EmptyState(
                        title = "Nothing in ${state.filter.name.lowercase()}",
                        body = "Try another filter.",
                        actionLabel = "Show all",
                        onAction = { viewModel.setFilter(MessagesFilter.All) },
                    )
                } else {
                    visible.forEach { item ->
                        val thread = item.thread
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onThreadClick(thread.id) }
                                .padding(horizontal = space.lg, vertical = space.md),
                            horizontalArrangement = Arrangement.spacedBy(space.md),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Avatar(
                                name = item.counterpartName,
                                size = AppTheme.dimens.size.avatarMd,
                            )
                            Column(Modifier.weight(1f)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(item.counterpartName, style = AppTheme.type.headline, color = colors.ink)
                                    if (thread.unreadCount > 0) {
                                        Text(
                                            thread.unreadCount.toString(),
                                            style = AppTheme.type.monoSmall,
                                            color = colors.brandInk,
                                            modifier = Modifier.background(
                                                colors.brand,
                                                RoundedCornerShape(AppTheme.dimens.radii.xl),
                                            ).padding(horizontal = space.sm),
                                        )
                                    }
                                }
                                Spacer(Modifier.height(space.xs))
                                Text(
                                    thread.lastPreview.ifBlank { "Start a conversation" },
                                    style = AppTheme.type.footnote,
                                    color = colors.ink3,
                                    maxLines = 1,
                                )
                            }
                        }
                        HRule(modifier = Modifier.padding(start = space.lg + AppTheme.dimens.size.avatarMd + space.md))
                    }
                }
            }
        }
    }
}
