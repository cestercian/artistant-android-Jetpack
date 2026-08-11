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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Where the scripted support tree currently sits.
 *
 * Deliberately a fixed tree and not an "assistant": nothing here generates text,
 * nothing pretends a person replied, and there is no typing indicator. Every
 * answer below is a sentence a human wrote, so the surface can be honest about
 * being canned while still being the fastest route to the right screen.
 */
enum class SupportStep { Root, Answered, Composing, NoteSent }

/** One turn in the scripted transcript. */
data class SupportLine(
    val id: Long,
    val fromBot: Boolean,
    val text: String,
    /** True when the bot answer offers the one real deep link (open bookings). */
    val offersBookings: Boolean = false,
)

/** A tappable quick reply. */
data class SupportReply(val label: String, val intent: SupportIntent)

/** What the reader can ask for. Closed set — this is a menu, not a parser. */
enum class SupportIntent { Booking, Safety, Other, TypeItOut, BackToMenu }

/**
 * The script.
 *
 * Pure: given a step it returns the replies to offer, and given an intent it
 * returns the two lines to append. Keeping it out of the composable means the
 * tree can be walked in a unit test — the failure mode for a decision tree is a
 * dead end, and a dead end is only visible if you can enumerate the states.
 */
object SupportScript {
    const val GREETING = "Hi — I'm the Artistant assistant. What can I help you with?"

    private const val TYPE_IT_OUT = "I'd rather type it out"
    private const val BACK_TO_MENU = "Back to menu"

    /** Replies for [step]. There is always a way onward — no step returns empty. */
    fun replies(step: SupportStep): List<SupportReply> = when (step) {
        SupportStep.Root -> listOf(
            SupportReply("I need help with a booking", SupportIntent.Booking),
            SupportReply("I want to report a safety issue", SupportIntent.Safety),
            SupportReply("Something else", SupportIntent.Other),
            SupportReply(TYPE_IT_OUT, SupportIntent.TypeItOut),
        )
        // After an answer, and after a note is sent, the only moves are "ask
        // something else" and "say it in your own words". The escape hatch is
        // present at every step except right after a note, where repeating it
        // would invite the same person to file the same note twice.
        SupportStep.Answered -> listOf(
            SupportReply(BACK_TO_MENU, SupportIntent.BackToMenu),
            SupportReply(TYPE_IT_OUT, SupportIntent.TypeItOut),
        )
        SupportStep.Composing -> listOf(SupportReply(BACK_TO_MENU, SupportIntent.BackToMenu))
        SupportStep.NoteSent -> listOf(SupportReply(BACK_TO_MENU, SupportIntent.BackToMenu))
    }

    /**
     * The user's echoed turn for [intent].
     *
     * Null for "back to menu", which is navigation rather than something the
     * reader said — echoing it would put a line in the transcript nobody spoke.
     */
    fun userLine(intent: SupportIntent): String? =
        replies(SupportStep.Root).firstOrNull { it.intent == intent }?.label

    /** The bot's answer, and whether it carries the bookings deep link. */
    fun answer(intent: SupportIntent, bookingsLabel: String): Pair<String, Boolean> = when (intent) {
        SupportIntent.Booking ->
            "Every booking — its status, date and full chat — lives under $bookingsLabel. " +
                "Requests, confirmations and cancellations all happen there." to true
        SupportIntent.Safety ->
            "Your safety comes first. To report someone, open the conversation's Details and tap " +
                "Report — or use Report on their profile. It goes to our team and is never shared " +
                "with them. In an emergency, contact local services." to false
        SupportIntent.Other ->
            "No problem. Tap “$TYPE_IT_OUT” and tell us what's going on — your note goes " +
                "straight to the Artistant team." to false
        SupportIntent.TypeItOut ->
            "Go ahead — type your message below and we'll get it to the team." to false
        SupportIntent.BackToMenu -> "What else can I help with?" to false
    }

    /** Step to move to after [intent]. */
    fun next(intent: SupportIntent): SupportStep = when (intent) {
        SupportIntent.Booking, SupportIntent.Safety, SupportIntent.Other -> SupportStep.Answered
        SupportIntent.TypeItOut -> SupportStep.Composing
        SupportIntent.BackToMenu -> SupportStep.Root
    }

    /**
     * The receipt after a typed note.
     *
     * Two outcomes, not one. The write can fail (offline, signed out, missing
     * table) and `submitFeedback` reports that honestly, so claiming "we've
     * logged your message" either way would be the app inventing a delivery it
     * never made.
     */
    fun noteReceipt(delivered: Boolean): String = if (delivered) {
        "Thanks — we've logged your message. The team follows up by email, usually within a day."
    } else {
        "We couldn't send that just now. Check your connection and try again — nothing was lost, " +
            "your text is still above."
    }
}

data class SupportUiState(
    val lines: List<SupportLine> = emptyList(),
    val step: SupportStep = SupportStep.Root,
    val sending: Boolean = false,
) {
    val replies: List<SupportReply> = SupportScript.replies(step)
    val composing: Boolean = step == SupportStep.Composing
}

/**
 * Drives the scripted transcript and the one real side effect it has: a typed
 * note lands in `app_feedback` through the same repository the Help sheet uses.
 */
@HiltViewModel
class SupportChatViewModel @Inject constructor(
    private val bookings: BookingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SupportUiState())
    val state: StateFlow<SupportUiState> = _state.asStateFlow()

    private var nextId = 0L

    init {
        _state.update { it.copy(lines = listOf(botLine(SupportScript.GREETING))) }
    }

    fun choose(intent: SupportIntent, bookingsLabel: String) {
        val (answer, offersBookings) = SupportScript.answer(intent, bookingsLabel)
        _state.update { current ->
            val echoed = SupportScript.userLine(intent)?.let { listOf(userLine(it)) } ?: emptyList()
            current.copy(
                lines = current.lines + echoed + botLine(answer, offersBookings),
                step = SupportScript.next(intent),
            )
        }
    }

    /**
     * Send a free-text note. The user's own words are echoed immediately —
     * they typed them, so they are true regardless of what the network does —
     * and only the receipt waits on the write.
     */
    fun sendNote(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.sending) return
        _state.update { it.copy(lines = it.lines + userLine(trimmed), sending = true) }
        viewModelScope.launch {
            val delivered = runCatching { bookings.submitFeedback(trimmed, isBug = false) }
                .getOrDefault(false)
            _state.update {
                it.copy(
                    lines = it.lines + botLine(SupportScript.noteReceipt(delivered)),
                    // A failed send stays in the composer step so the retry is one
                    // tap away; a delivered one closes the composer.
                    step = if (delivered) SupportStep.NoteSent else SupportStep.Composing,
                    sending = false,
                )
            }
        }
    }

    private fun botLine(text: String, offersBookings: Boolean = false) =
        SupportLine(nextId++, fromBot = true, text = text, offersBookings = offersBookings)

    private fun userLine(text: String) = SupportLine(nextId++, fromBot = false, text = text)
}

/**
 * The Artistant Support conversation.
 *
 * Rendered as a thread rather than an FAQ because that is what it is replacing —
 * the reader arrived from the inbox expecting to talk to someone, and a list of
 * links would read as a dead end.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportChatSheet(
    onDismiss: () -> Unit,
    bookingsLabel: String = "Bookings",
    onOpenBookings: (() -> Unit)? = null,
    viewModel: SupportChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val listState = rememberLazyListState()

    // Follow the newest turn. The transcript is short and entirely
    // machine-driven, so there is no reader-scrolled-away case to protect.
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.bg,
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = space.lg)) {
            Text(
                "Artistant Support",
                style = AppTheme.type.headline,
                color = colors.ink,
                modifier = Modifier.padding(horizontal = space.xl, vertical = space.sm),
            )
            LazyColumn(
                Modifier.weight(1f, fill = false).fillMaxWidth().padding(horizontal = space.lg),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                items(state.lines, key = { it.id }) { line ->
                    SupportBubble(
                        line = line,
                        bookingsLabel = bookingsLabel,
                        onOpenBookings = onOpenBookings?.let {
                            {
                                it()
                                onDismiss()
                            }
                        },
                    )
                }
            }
            if (state.composing) {
                SupportComposer(sending = state.sending, onSend = viewModel::sendNote)
            }
            SupportReplies(
                replies = state.replies,
                onChoose = { viewModel.choose(it, bookingsLabel) },
            )
        }
    }
}

@Composable
private fun SupportBubble(
    line: SupportLine,
    bookingsLabel: String,
    onOpenBookings: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (line.fromBot) Alignment.Start else Alignment.End,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (line.fromBot) {
                // Brand wash, no face: this is the app talking and it should not
                // borrow a person's shape to say so.
                Box(
                    Modifier
                        .size(AppTheme.dimens.size.avatarSm)
                        .clip(CircleShape)
                        .background(colors.brandSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("A", style = AppTheme.type.monoSmall, color = colors.brand)
                }
            }
            Text(
                line.text,
                style = AppTheme.type.callout,
                color = if (line.fromBot) colors.ink else colors.brandInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.lg))
                    .background(if (line.fromBot) colors.bgCard else colors.brand)
                    .padding(horizontal = space.md, vertical = space.sm),
            )
        }
        if (line.offersBookings && onOpenBookings != null) {
            Text(
                "Go to $bookingsLabel",
                style = AppTheme.type.footnote,
                color = colors.brand,
                modifier = Modifier
                    .padding(top = space.xs, start = AppTheme.dimens.size.avatarSm)
                    .clickable(onClick = onOpenBookings)
                    .padding(space.xs),
            )
        }
    }
}

/** Quick replies, right-aligned so they read as things the reader is about to say. */
@Composable
private fun SupportReplies(replies: List<SupportReply>, onChoose: (SupportIntent) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier.fillMaxWidth().padding(horizontal = space.lg, vertical = space.sm),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        replies.forEach { reply ->
            Text(
                reply.label,
                style = AppTheme.type.footnote,
                color = colors.ink,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(AppTheme.dimens.size.hairline, colors.line, CircleShape)
                    .clickable { onChoose(reply.intent) }
                    .padding(horizontal = space.lg, vertical = space.sm),
            )
        }
    }
}

/** Free-text escape hatch. Owns its own draft so typing doesn't redraw the transcript. */
@Composable
private fun SupportComposer(sending: Boolean, onSend: (String) -> Unit) {
    val space = AppTheme.dimens.space
    Spacer(Modifier.height(space.xs))
    MessageComposer(
        placeholder = "Type your message…",
        enabled = !sending,
        onSend = onSend,
        testTag = "support.composer",
    )
}
