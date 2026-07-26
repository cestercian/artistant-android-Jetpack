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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppTheme

/** M4 inbox: server-backed thread list with verbatim last-message previews. */
@Composable
fun MessagesScreen(
    onThreadClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MessagesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
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
        else -> Column(modifier.fillMaxSize().background(colors.bg)) {
            Text(
                "Messages",
                style = AppTheme.type.displaySub,
                color = colors.ink,
                modifier = Modifier.padding(space.lg),
            )
            state.threads.forEach { item ->
                val thread = item.thread
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThreadClick(thread.id) }
                        .padding(horizontal = space.lg, vertical = space.md),
                    horizontalArrangement = Arrangement.spacedBy(space.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier
                            .size(AppTheme.dimens.size.avatarMd)
                            .background(colors.bgElev, androidx.compose.foundation.shape.RoundedCornerShape(AppTheme.dimens.radii.sm)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.ChatBubbleOutline,
                            contentDescription = null,
                            tint = colors.ink3,
                        )
                    }
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
                                        androidx.compose.foundation.shape.RoundedCornerShape(AppTheme.dimens.radii.xl),
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
