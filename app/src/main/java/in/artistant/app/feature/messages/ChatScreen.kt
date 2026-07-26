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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageKind
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppTheme

/** M4 chat: verbatim bodies, system notices, and quiet Airbnb-style trust copy. */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onBookingClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var draft by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.sm, vertical = space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
            }
            Text(state.title, style = AppTheme.type.headline, color = colors.ink, modifier = Modifier.weight(1f))
        }
        SafetyBanner()
        HRule()
        when {
            state.isLoading && state.messages.isEmpty() -> Box(
                Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = colors.brand) }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = space.lg),
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageRow(message, onBookingClick)
                }
                if (state.error != null) {
                    item { Text(state.error.orEmpty(), style = AppTheme.type.caption, color = colors.hot) }
                }
            }
        }
        HRule()
        Row(
            Modifier.fillMaxWidth().padding(space.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Message…", style = AppTheme.type.body) },
                modifier = Modifier.weight(1f),
                textStyle = AppTheme.type.body.copy(color = colors.ink),
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    viewModel.send(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank() && !state.isSending,
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = colors.brand)
            }
        }
    }
}

@Composable
private fun SafetyBanner() {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().background(colors.bgElev).padding(horizontal = space.lg, vertical = space.sm),
        horizontalArrangement = Arrangement.spacedBy(space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = colors.ink3)
        Column {
            Text("Messages are analysed for safety and support.", style = AppTheme.type.caption, color = colors.ink2)
            Text("Always communicate through Artistant.", style = AppTheme.type.caption, color = colors.ink3)
        }
    }
}

@Composable
private fun MessageRow(message: Message, onBookingClick: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    if (message.kind == MessageKind.System) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message.body, style = AppTheme.type.caption, color = colors.ink3, textAlign = TextAlign.Center)
            message.actionRoute?.removePrefix("booking:")?.takeIf { it != message.actionRoute }?.let { bookingId ->
                Text(
                    "View booking",
                    style = AppTheme.type.caption,
                    color = colors.ink2,
                    modifier = Modifier
                        .padding(top = space.xs)
                        .clickable { onBookingClick(bookingId) }
                        .padding(horizontal = space.sm, vertical = space.xs),
                )
            }
        }
        return
    }
    Box(
        Modifier.fillMaxWidth().padding(vertical = space.xs),
        contentAlignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            message.body,
            style = AppTheme.type.callout,
            color = if (message.isMine) colors.brandInk else colors.ink,
            modifier = Modifier
                .background(
                    if (message.isMine) colors.brand else colors.bgElev,
                    RoundedCornerShape(AppTheme.dimens.radii.lg),
                )
                .padding(horizontal = space.md, vertical = space.sm),
        )
    }
}
