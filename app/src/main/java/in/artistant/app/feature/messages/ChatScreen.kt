package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import `in`.artistant.app.data.model.Message
import `in`.artistant.app.data.model.MessageDelivery
import `in`.artistant.app.data.model.MessageSender
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.booking.InitialAvatar

/**
 * Shared chat (port of iOS `ChatView`), both roles. A reverse [LazyColumn] pins the
 * newest bubble to the bottom and auto-scrolls on send/receive; bubbles are
 * system(centered)/me(brand)/other(card) with PER-BUBBLE redaction; a failed send
 * grows a Tap-to-retry chip + fires an error haptic; the composer floats in a
 * glass-ish bar. The open lifecycle (ensure/refresh/mark-read + Realtime subscribe)
 * is driven here so it ties to the same threadId/userId key as the paint.
 */
@Composable
fun ChatScreen(
    role: AppRole,
    onBack: () -> Unit,
    onArtist: ((String) -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val thread by viewModel.thread.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val colors = AppTheme.colors

    // Counterpart title: artist viewer → the client (denormalized name); client
    // viewer → the artist (resolved by-id, "Chat" until it hydrates).
    val title = if (role == AppRole.Artist) {
        thread?.clientName?.takeIf { it.isNotBlank() } ?: "Client"
    } else {
        viewModel.artistName() ?: "Chat"
    }

    // Open effect — ensures the thread, hydrates messages + header artist, marks
    // read. Keyed on threadId (a sign-out/account-swap tears down the whole tab graph
    // on Android, recreating this screen — so no separate userId key is needed, unlike
    // iOS where the persistent view instance re-keys on userID).
    LaunchedEffect(viewModel.threadId) { viewModel.load() }

    // Realtime lifecycle. iOS suspends the socket on background, so a chat left open
    // goes silent until re-subscribe. Subscribe (+ fill the missed gap) on RESUME,
    // tear the channel down on PAUSE and on dispose. The VM holds the Job + bumps a
    // generation on teardown so a superseded subscribe can't leak a WebSocket.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel.threadId) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.onForeground()
                Lifecycle.Event.ON_PAUSE -> viewModel.teardownRealtime()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.teardownRealtime()
        }
    }

    // Auto-scroll to newest. reverseLayout renders item[0] at the bottom, and we feed
    // the list newest-first, so item[0] IS the newest — scroll it into view on open,
    // send, and realtime receive.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    // Error haptic when a send flips to .Failed, so the failure is felt even if the
    // user scrolled away from the bubble (iOS audit P3).
    var lastFailed by remember { mutableIntStateOf(0) }
    val failedNow = messages.count { it.delivery == MessageDelivery.Failed }
    LaunchedEffect(failedNow) {
        if (failedNow > lastFailed) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        lastFailed = failedNow
    }

    Scaffold(
        containerColor = colors.bg,
        topBar = {
            ChatTopBar(
                title = title,
                onBack = onBack,
                // Client viewer only: the avatar pushes the artist profile.
                onAvatar = if (role != AppRole.Artist) onArtist?.let { push ->
                    { viewModel.artistId()?.let(push) }
                } else null,
            )
        },
        bottomBar = {
            Composer(onSend = { text ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.send(text)
            })
        },
    ) { inner ->
        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(AppTheme.dimens.space.lg),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        ) {
            items(messages.asReversed(), key = { it.id }) { m ->
                Bubble(
                    message = m,
                    body = viewModel.displayBody(m.body),
                    onRetry = { viewModel.retry(m.id) },
                )
            }
        }
    }
}

@Composable
private fun ChatTopBar(title: String, onBack: () -> Unit, onAvatar: (() -> Unit)?) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = space.md, vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Box(
            Modifier.size(AppTheme.dimens.size.rowMin).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
        }
        Text(
            title,
            style = AppTheme.type.headline.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onAvatar != null) {
            Box(Modifier.clip(CircleShape).clickable(onClick = onAvatar)) {
                InitialAvatar(title, size = 28)
            }
        }
    }
}

@Composable
private fun Bubble(message: Message, body: String, onRetry: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // System rows are centered chrome (join/booking notices).
    if (message.sender == MessageSender.System) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text(
                body,
                style = AppTheme.type.caption,
                color = colors.ink3,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.bgSoft)
                    .padding(horizontal = space.md, vertical = space.sm),
            )
        }
        return
    }

    val mine = message.sender == MessageSender.Me
    // In-flight / failed sends read dimmed until the server confirms; the retry chip
    // carries the actionable signal for a failed one.
    val bubbleAlpha = if (message.delivery == MessageDelivery.Sent) 1f else 0.55f
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(space.xs),
    ) {
        Text(
            body,
            style = AppTheme.type.callout,
            color = if (mine) colors.brandInk else colors.ink,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    (if (mine) colors.brand else colors.bgCard).copy(alpha = bubbleAlpha),
                )
                .padding(horizontal = space.md, vertical = space.md),
        )
        if (mine && message.delivery == MessageDelivery.Failed) {
            // Hot-tinted retry — the only place `hot` appears in chat, so a failed
            // send is unmistakable. Re-runs the write under the same optimistic id.
            Row(
                Modifier.clip(CircleShape).clickable(onClick = onRetry).padding(space.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.xs),
            ) {
                Text(
                    "Not sent · Tap to retry",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.hot,
                )
            }
        }
    }
}

/**
 * The floating composer (iOS's Liquid Glass capsule). Android has no glass blur, so
 * this approximates it with a translucent elevated capsule that the conversation
 * scrolls under. ponytail: swap the flat translucent fill for a real blur/Haze
 * effect if/when a blur dependency lands — the shape + layout stay.
 */
@Composable
private fun Composer(onSend: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var draft by remember { mutableStateOf("") }
    val trimmed = draft.trim()
    val canSend = trimmed.isNotEmpty()

    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = space.lg, vertical = space.sm),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(colors.bgElev.copy(alpha = 0.92f))
                .border(1.dp, colors.line, RoundedCornerShape(26.dp))
                .padding(horizontal = space.lg, vertical = space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) {
                    Text("Message…", style = AppTheme.type.callout, color = colors.ink3)
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(4000) }, // guard a pathological paste (store also caps)
                    textStyle = AppTheme.type.callout.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.brand),
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // Send — brand circle, dimmed + disabled while empty.
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) colors.brand else colors.bgSoft)
                .clickable(enabled = canSend) {
                    onSend(trimmed)
                    draft = ""
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.ArrowUpward,
                contentDescription = "Send",
                tint = if (canSend) colors.brandInk else colors.ink3,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
