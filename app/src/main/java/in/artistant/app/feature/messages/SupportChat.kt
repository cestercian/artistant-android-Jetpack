package `in`.artistant.app.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.rememberHaptics
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
 * being canned while still being the fastest route to the right screen — which
 * is exactly what design 34 asks for ("honest about what it is").
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

/**
 * A tappable branch of the tree.
 *
 * [detail] is the second line the design draws under each option ("Change a
 * date, chase a reply, cancel"). It is what makes a menu of three words usable:
 * the label says the topic, the detail says what is actually behind it.
 */
data class SupportReply(val label: String, val intent: SupportIntent, val detail: String? = null)

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
    /** What it is, said first (design 34's whole point). */
    const val GREETING = "I'm a guided assistant, not a person — I can answer common " +
        "questions right here and hand you to the team when it needs one."

    /** The second bubble: the question the options below answer. */
    const val PROMPT = "What do you need?"

    private const val TYPE_IT_OUT = "I'd rather type it out"
    private const val BACK_TO_MENU = "Back to menu"

    /** Replies for [step]. There is always a way onward — no step returns empty. */
    fun replies(step: SupportStep): List<SupportReply> = when (step) {
        SupportStep.Root -> listOf(
            SupportReply("A booking", SupportIntent.Booking, "Change a date, chase a reply, cancel"),
            SupportReply(
                "Safety",
                SupportIntent.Safety,
                "Someone asked me to pay off-platform",
            ),
            SupportReply("Something else", SupportIntent.Other, "Bugs, billing, account"),
            SupportReply(TYPE_IT_OUT, SupportIntent.TypeItOut, "Send a note to the team"),
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

    /**
     * Line ids, minted outside every `update` block.
     *
     * `MutableStateFlow.update` re-runs its lambda when the compare-and-set
     * loses, so the lambda has to be side-effect free — and `nextId++` is a
     * read-modify-write. Building the lines first keeps every lambda a plain
     * `copy()`.
     */
    private var nextId = 0L

    init {
        val opening = listOf(botLine(SupportScript.GREETING), botLine(SupportScript.PROMPT))
        _state.update { it.copy(lines = opening) }
    }

    fun choose(intent: SupportIntent, bookingsLabel: String) {
        val (answer, offersBookings) = SupportScript.answer(intent, bookingsLabel)
        val echoed = SupportScript.userLine(intent)?.let { listOf(userLine(it)) } ?: emptyList()
        val reply = botLine(answer, offersBookings)
        val step = SupportScript.next(intent)
        _state.update { current ->
            current.copy(lines = current.lines + echoed + reply, step = step)
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
        val echo = userLine(trimmed)
        _state.update { it.copy(lines = it.lines + echo, sending = true) }
        viewModelScope.launch {
            val delivered = runCatching { bookings.submitFeedback(trimmed, isBug = false) }
                .getOrDefault(false)
            val receipt = botLine(SupportScript.noteReceipt(delivered))
            _state.update {
                it.copy(
                    lines = it.lines + receipt,
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
 * Artistant Support (design 34).
 *
 * A full screen rather than a sheet: the reader arrived from the inbox's
 * permanent row expecting a conversation, and a half-height sheet with a
 * transcript in it reads as a preview of one. It says what it is in its first
 * sentence, offers three branches as cards, and its one real deep link hands off
 * to the bookings tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bookingsLabel: String = "Bookings",
    onOpenBookings: (() -> Unit)? = null,
    viewModel: SupportChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val listState = rememberLazyListState()
    val haptics = rememberHaptics()

    // iOS buzzes success as it appends the receipt; here the receipt is only
    // honest once the write landed, and `NoteSent` is the step it lands on — a
    // failed send stays in `Composing`, so a failure can't buzz success.
    LaunchedEffect(state.step) {
        if (state.step == SupportStep.NoteSent) haptics.success()
    }

    // Follow the newest turn. The transcript is short and entirely
    // machine-driven, so there is no reader-scrolled-away case to protect.
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.lastIndex)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.page)
            // Same single source of keyboard movement as the chat — see
            // ChatScreen and the activity's `adjustResize`.
            .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars)),
    ) {
        BackHeader(
            title = "Artistant Support",
            subtitle = "Booking help, safety and feedback",
            onBack = onBack,
            centered = false,
            modifier = Modifier.padding(horizontal = dimens.component.gutter),
        )
        HRule()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = dimens.component.gutter)
                .semantics { testTag = "support.transcript" },
            state = listState,
            verticalArrangement = Arrangement.spacedBy(dimens.space.md, Alignment.Bottom),
        ) {
            items(state.lines, key = { it.id }) { line ->
                SupportBubble(
                    line = line,
                    bookingsLabel = bookingsLabel,
                    onOpenBookings = onOpenBookings,
                )
            }
            item(key = "support.replies") {
                SupportOptions(
                    replies = state.replies,
                    onChoose = { viewModel.choose(it, bookingsLabel) },
                )
            }
        }

        ComposerBar(
            onSend = viewModel::sendNote,
            placeholder = "Type a message…",
            enabled = !state.sending,
            testTag = "support.composer",
        )
    }
}

@Composable
private fun SupportBubble(
    line: SupportLine,
    bookingsLabel: String,
    onOpenBookings: (() -> Unit)?,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (line.fromBot) Alignment.Start else Alignment.End,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (line.fromBot) {
                // The mark, not a face: this is the app talking and it should not
                // borrow a person's shape to say so.
                Box(
                    Modifier.size(dimens.size.avatarSm),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(colors.darkest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("A", style = AppTheme.type.monoPill, color = colors.accent)
                    }
                }
            }
            Text(
                line.text,
                style = AppTheme.type.body,
                color = if (line.fromBot) colors.ink else colors.onAccent,
                modifier = Modifier
                    // A gutter on the FAR side only, so the bubble hugs its own
                    // content while the opposite edge always shows — that is
                    // what makes the transcript read as two columns.
                    .then(
                        if (line.fromBot) {
                            Modifier.padding(end = dimens.space.xxl)
                        } else {
                            Modifier.padding(start = dimens.space.xxl)
                        },
                    )
                    .clip(
                        RoundedCornerShape(
                            topStart = dimens.radii.lg,
                            topEnd = dimens.radii.lg,
                            bottomEnd = if (line.fromBot) dimens.radii.lg else dimens.radii.sm,
                            bottomStart = if (line.fromBot) dimens.radii.sm else dimens.radii.lg,
                        ),
                    )
                    .background(if (line.fromBot) colors.surface3 else colors.accent)
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.md),
            )
        }
        if (line.offersBookings && onOpenBookings != null) {
            Text(
                "Go to $bookingsLabel",
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
                color = colors.accentInk,
                modifier = Modifier
                    .padding(top = dimens.space.xs, start = dimens.size.avatarSm)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenBookings)
                    .padding(horizontal = dimens.space.sm, vertical = dimens.space.xs)
                    .semantics { testTag = "support.bookingsLink" },
            )
        }
    }
}

/**
 * The branch options, as cards (design 34).
 *
 * Outlined rather than filled: they are a menu, not the screen's one action, and
 * four accent buttons stacked would spend the app's single signal four times.
 * The detail line under each label is what turns three words into a choice.
 */
@Composable
private fun SupportOptions(replies: List<SupportReply>, onChoose: (SupportIntent) -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier.fillMaxWidth().padding(top = dimens.space.xs),
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        replies.forEach { reply ->
            val shape = RoundedCornerShape(dimens.radii.buttonLg)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(dimens.component.focusStroke, colors.hairline, shape)
                    .clickable { onChoose(reply.intent) }
                    .padding(horizontal = dimens.space.md, vertical = dimens.space.md)
                    .semantics { testTag = "support.option.${reply.intent.name}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        reply.label,
                        style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                        color = colors.ink,
                    )
                    reply.detail?.let {
                        Spacer(Modifier.height(dimens.space.xs))
                        Text(it, style = AppTheme.type.caption, color = colors.ink4)
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.lineStrong,
                    modifier = Modifier.size(dimens.size.iconLg),
                )
            }
        }
    }
}
