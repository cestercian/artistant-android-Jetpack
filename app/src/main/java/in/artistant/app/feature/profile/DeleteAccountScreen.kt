package `in`.artistant.app.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.AppTextField
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.CheckRow
import `in`.artistant.app.designsystem.component.MarkState
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.feature.booking.BookingDraftStore
import `in`.artistant.app.feature.saved.SavedStore
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.calendar.CalendarSyncService
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The three stages the design draws — 115 → 48 → 116. */
enum class DeleteStage { Reason, Consequences, Receipt }

/** One option on stage 1. Optional, and it is not sent anywhere — see [DeleteAccountViewModel]. */
enum class DeleteReason(val label: String) {
    NoArtist("I didn't find the right artist"),
    TooExpensive("Too expensive"),
    OneOff("I only needed it once"),
    BadExperience("I had a bad experience"),
    Privacy("Privacy concerns"),
    Other("Something else"),
}

/**
 * Whether the mirrored gigs came off this device's calendar.
 *
 * Three states because there are three, and the receipt on stage 3 must not flatten them: the
 * wipe runs AFTER that screen is already up (the server row is gone, and holding a spinner
 * through a 30-second logout would read as a delete that never happened), so [Pending] is what
 * the row shows for the moment it takes, and [Failed] is what it shows when the calendar
 * provider refused. A tick over a wipe that threw is the one screen in the app whose whole job
 * is honesty claiming an erasure that did not happen.
 */
sealed interface CalendarOutcome {
    /** The wipe has not answered yet. */
    data object Pending : CalendarOutcome

    /** The mirrored events are off this device. */
    data object Cleaned : CalendarOutcome

    /** They are not, and [reason] is why. */
    data class Failed(val reason: String) : CalendarOutcome
}

data class DeleteAccountUiState(
    val stage: DeleteStage = DeleteStage.Reason,
    val reason: DeleteReason? = null,
    val confirmation: String = "",
    val working: Boolean = false,
    val failure: String? = null,
    /** What the account is about to lose. Null counts render without a number, never as zero. */
    val consequences: DeleteConsequences = DeleteConsequences(),
    /** What the device-calendar wipe actually managed. Stage 3's third row reads this. */
    val calendar: CalendarOutcome = CalendarOutcome.Pending,
) {
    /** The typed confirmation has to match exactly, case included. */
    val canDelete: Boolean get() = !working && confirmation.trim() == DELETE_KEYWORD
}

/**
 * The facts stage 2 itemises.
 *
 * Every field is nullable or blank-able on purpose: this screen is the last one someone sees
 * before an irreversible action, and a fabricated "128 bookings" would be the worst possible
 * place to guess. A count we could not read is simply left out of the sentence.
 */
data class DeleteConsequences(
    val handle: String? = null,
    val bookings: Int? = null,
    val upcoming: Int? = null,
    val reviews: Int? = null,
    val score: Int? = null,
    val isArtist: Boolean = false,
)

const val DELETE_KEYWORD = "DELETE"

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val account: AccountRepository,
    private val users: UsersRepository,
    private val bookings: BookingsRepository,
    private val session: SessionManager,
    private val prefs: AppPreferences,
    private val calendarSync: CalendarSyncService,
    private val savedStore: SavedStore,
    private val dataExport: DataExportStore,
    private val draftStore: BookingDraftStore,
) : ViewModel() {
    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state

    init {
        loadConsequences()
    }

    /**
     * Read what this account actually holds, so stage 2 can name it.
     *
     * Best-effort and field-by-field: a bookings read that fails must not blank the handle, and
     * neither failure may block the delete — someone exercising their DPDP §11 erasure right
     * cannot be stopped by a stat query. Every field stays null on failure, and
     * [deleteConsequences] renders the generic sentence for a null rather than "0".
     */
    private fun loadConsequences() = viewModelScope.launch {
        runCatching { users.fetchSelfProfile() }.onSuccess { profile ->
            _state.update {
                it.copy(
                    consequences = it.consequences.copy(
                        handle = profile?.handle?.trim()?.takeIf { h -> h.isNotEmpty() },
                        isArtist = profile?.role == AppRole.Artist,
                    ),
                )
            }
        }
        val isArtist = _state.value.consequences.isArtist
        runCatching {
            if (isArtist) bookings.listForArtist() else bookings.listForClient()
        }.onSuccess { list ->
            _state.update {
                it.copy(
                    consequences = it.consequences.copy(
                        bookings = list.size,
                        upcoming = liveBookingsCount(list),
                    ),
                )
            }
        }
    }

    fun pick(reason: DeleteReason) = _state.update { it.copy(reason = reason) }

    fun continueToConsequences() =
        _state.update { it.copy(stage = DeleteStage.Consequences, failure = null) }

    fun backToReason() =
        _state.update { it.copy(stage = DeleteStage.Reason, confirmation = "", failure = null) }

    fun setConfirmation(text: String) = _state.update { it.copy(confirmation = text) }

    /**
     * Server delete FIRST — local wipe only after success (DPDP §11 / PR #60).
     *
     * The reason picked on stage 1 goes nowhere, deliberately. `account_deletions` exists in
     * the schema but the `delete-account` Edge Function owns that row and takes no reason
     * argument; posting one from the client would need a server change, which starts in the iOS
     * repo. So the question is asked because the design's off-ramp needs it asked — the answer
     * is what decides whether Support is offered — and the screen never claims it was sent.
     */
    fun deleteAccount(onDeleted: () -> Unit) = viewModelScope.launch {
        if (!_state.value.canDelete) return@launch
        _state.update { it.copy(working = true, failure = null) }
        runCatching { account.deleteAccount() }
            .onSuccess {
                // Stage 3 goes up BEFORE the cleanup: the row is already erased server-side,
                // nothing below can change that outcome, and holding a spinner through a 30s
                // logout timeout would read as a delete that never happened.
                _state.update { it.copy(working = false, stage = DeleteStage.Receipt) }
                draftStore.clear()
                val cleanup = cleanUpAfterAccountDelete(
                    wipeCalendar = { calendarSync.wipeForAccountDelete() },
                    signOut = { session.signOut() },
                    wipeLocalState = { prefs.wipeAll(); savedStore.reset(); dataExport.reset() },
                )
                _state.update {
                    it.copy(
                        calendar = cleanup.calendarFailure
                            ?.let(CalendarOutcome::Failed)
                            ?: CalendarOutcome.Cleaned,
                        failure = cleanup.message ?: it.failure,
                    )
                }
                onDeleted()
            }
            .onFailure { e ->
                _state.update {
                    it.copy(working = false, failure = e.message ?: "Account deletion failed")
                }
            }
    }
}

/**
 * Design screens 115 → 48 → 116 — **"Three stages, plainly stated"**.
 *
 * Reason, then consequences, then a receipt. One destination rather than three, because they
 * are three steps of one decision and a back stack that lets someone re-enter stage 2 after
 * the account is gone is a back stack pointed at nothing.
 *
 * **Stage 1's job is the off-ramp** (the design's note: "the off-ramp is the point"). Continue
 * is a SECONDARY button, not the primary one — the primary action on a screen about leaving is
 * the one that keeps you — and picking "I had a bad experience" surfaces Support above it.
 *
 * **Stage 2 names every loss**, and only the ones it can actually count. The design writes
 * "128 bookings · including the 2 upcoming ones"; this reads those two numbers off the same
 * bookings list the profile band uses and falls back to the sentence without a number when the
 * read failed. It is the last screen before an irreversible action; a plausible invented count
 * here would be the worst fabrication in the app.
 *
 * **Stage 3 is a receipt, not a goodbye**, including the 30-day backup window — which is the
 * honest part, and the part every "your account is gone" screen leaves out.
 */
@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    /** The scripted support assistant (design 34) — stage 1's off-ramp. */
    onContactSupport: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeleteAccountContent(
        state = state,
        onBack = { if (state.stage == DeleteStage.Consequences) viewModel.backToReason() else onBack() },
        onPick = viewModel::pick,
        onContinue = viewModel::continueToConsequences,
        onConfirmationChange = viewModel::setConfirmation,
        // The receipt replaces this screen; navigating away is the host's job, and it happens
        // anyway the moment the cleared session propagates to the root gate.
        onDelete = { viewModel.deleteAccount(onFinished) },
        onKeep = onBack,
        onContactSupport = onContactSupport,
        onClose = onFinished,
        modifier = modifier,
    )
}

@Composable
private fun DeleteAccountContent(
    state: DeleteAccountUiState,
    onBack: () -> Unit,
    onPick: (DeleteReason) -> Unit,
    onContinue: () -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
    onContactSupport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    when (state.stage) {
        DeleteStage.Reason -> DeleteReasonStage(
            state = state,
            onBack = onBack,
            onPick = onPick,
            onContinue = onContinue,
            onContactSupport = onContactSupport,
            modifier = modifier,
        )
        DeleteStage.Consequences -> DeleteConsequencesStage(
            state = state,
            onBack = onBack,
            onConfirmationChange = onConfirmationChange,
            onDelete = onDelete,
            onKeep = onKeep,
            modifier = modifier,
        )
        DeleteStage.Receipt -> DeleteReceiptStage(
            state = state,
            onClose = onClose,
            modifier = modifier,
        )
    }
    Spacer(Modifier.height(dimens.space.xs))
}

/** 115 — "Before you go". */
@Composable
private fun DeleteReasonStage(
    state: DeleteAccountUiState,
    onBack: () -> Unit,
    onPick: (DeleteReason) -> Unit,
    onContinue: () -> Unit,
    onContactSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = AppTheme.dimens
    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.deleteReason" },
        header = { BackHeader(title = "Delete account", onBack = onBack, subtitle = "Step 1 of 3") },
        footer = {
            // Continue is SECONDARY and Support is the accent. On a screen whose job is the
            // off-ramp, the destructive path does not get the page's one accent.
            SecondaryButton(
                text = "Continue",
                onClick = onContinue,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "delete.continue" },
            )
            PrimaryButton(
                text = "Talk to Support instead",
                onClick = onContactSupport,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "delete.support" },
            )
        },
    ) {
        AccountGap()
        AccountPageTitle(
            "Before you go",
            body = "What's prompting this? It helps us do better — and it's optional.",
        )
        AccountGap()
        DeleteReason.entries.forEach { reason ->
            CheckRow(
                title = reason.label,
                state = if (state.reason == reason) MarkState.Done else MarkState.Pending,
                onClick = { onPick(reason) },
                showHairline = reason != DeleteReason.entries.last(),
                modifier = Modifier.semantics { testTag = "delete.reason.${reason.name}" },
            )
        }
        if (state.reason == DeleteReason.BadExperience) {
            AccountGap()
            AccentNote(
                lead = "A bad experience? Support can often fix it",
                text = supportOfframpLine(state.consequences),
                modifier = Modifier.semantics { testTag = "delete.offramp" },
            )
        }
        AccountGap()
        Text(
            "Your answer stays on this screen — this version of Artistant has no way to send " +
                "it, so nothing here is reported anywhere.",
            style = AppTheme.type.caption,
            color = AppTheme.colors.ink4,
        )
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/** 48 — "This can't be undone". */
@Composable
private fun DeleteConsequencesStage(
    state: DeleteAccountUiState,
    onBack: () -> Unit,
    onConfirmationChange: (String) -> Unit,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.deleteConsequences" },
        header = { BackHeader(title = "Delete account", onBack = onBack, subtitle = "Step 2 of 3") },
        footer = {
            state.failure?.let { message ->
                Text(
                    message,
                    style = AppTheme.type.caption,
                    color = colors.danger,
                    modifier = Modifier.semantics { testTag = "delete.failure" },
                )
            }
            DestructiveButton(
                text = if (state.working) "Deleting…" else "Delete my account",
                enabled = state.canDelete,
                onClick = onDelete,
            )
            SecondaryButton(
                text = "Keep my account",
                onClick = onKeep,
                fullWidth = true,
                enabled = !state.working,
                modifier = Modifier.semantics { testTag = "delete.keep" },
            )
        },
    ) {
        AccountGap()
        AccountPageTitle(
            "This can't be undone",
            body = "Type $DELETE_KEYWORD to confirm you understand what goes.",
        )
        AccountGap()
        deleteConsequences(state.consequences).forEach { item ->
            ConsequenceCard(item.title, item.detail)
            Spacer(Modifier.height(dimens.space.sm))
        }
        AccountGap()
        AppTextField(
            value = state.confirmation,
            onValueChange = onConfirmationChange,
            label = "Type $DELETE_KEYWORD",
            hint = DELETE_KEYWORD,
            enabled = !state.working,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.semantics { testTag = "delete.confirmField" },
        )
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/** 116 — "Account deleted". A receipt, itemised, including the backup window. */
@Composable
private fun DeleteReceiptStage(
    state: DeleteAccountUiState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    AccountScaffold(
        modifier = modifier.semantics { testTag = "screen.deleteReceipt" },
        footer = {
            PrimaryButton(
                text = "Close Artistant",
                onClick = onClose,
                fullWidth = true,
                modifier = Modifier.semantics { testTag = "delete.close" },
            )
        },
    ) {
        AccountGap(2)
        Box(
            Modifier.size(dimens.size.avatarMd).background(colors.surface2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(dimens.size.iconXl),
            )
        }
        AccountGap()
        AccountPageTitle("Account deleted", body = "Here's exactly what that means.")
        AccountGap()
        deleteReceipt(state.consequences, state.calendar).forEachIndexed { index, item ->
            CheckRow(
                title = item.title,
                subtitle = item.detail,
                // Each row carries its own mark, because two of the four are not "done": the
                // backup window has not happened yet, and the calendar row reports what the
                // wipe actually managed.
                state = item.mark,
                showHairline = false,
                modifier = Modifier.semantics { testTag = "delete.receipt.$index" },
            )
        }
        AccountGap()
        AccentNote(
            text = "Nothing was owed and nothing was charged — this version of Artistant takes " +
                "no payment.",
        )
        state.failure?.let { message ->
            AccountGap()
            Banner(title = "One last step on this device", tone = BannerTone.Attention, detail = message)
        }
        Spacer(Modifier.height(dimens.size.listTailroom))
    }
}

/** One itemised card on stage 2 — a danger disc, a title, and the specific loss. */
@Composable
private fun ConsequenceCard(title: String, detail: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.surface3, RoundedCornerShape(dimens.radii.lg))
            .padding(horizontal = dimens.space.lg)
            .semantics { testTag = "delete.consequence" },
    ) {
        CheckRow(
            title = title,
            subtitle = detail,
            state = MarkState.Failed,
            showHairline = false,
            // The mark is the alarm; the sentence beside it is a fact, and the design sets it
            // in ink4. See CheckRow.subtitleColor.
            subtitleColor = colors.ink4,
        )
    }
}

/**
 * The destructive CTA — the one control in the app painted in [danger] rather than the accent.
 *
 * Not `PrimaryButton`: the accent means "the thing to do", and on this screen the thing to do
 * is the button underneath. A lime "Delete my account" would be the single worst-coloured
 * control in the product.
 */
@Composable
private fun DestructiveButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    Box(
        Modifier
            .fillMaxWidth()
            .height(dimens.component.cta)
            .background(if (enabled) colors.danger else colors.hairline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { testTag = "delete.confirm" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = AppTheme.type.cta,
            color = if (enabled) colors.onDark else colors.ink3,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** One line of the itemised list on stage 2 or stage 3. */
data class DeleteItem(val title: String, val detail: String)

/**
 * What stage 2 itemises — every loss, named.
 *
 * Counts are only stated when they were read. "128 bookings · including the 2 upcoming ones,
 * which are cancelled" becomes "Your bookings · every one of them, including anything upcoming"
 * when the read failed — the same fact, without a number nobody verified. The artist row is
 * dropped entirely for a host: a client has no reviews about them and no score, and listing
 * losses that cannot happen makes the ones that can look equally uncertain.
 */
fun deleteConsequences(facts: DeleteConsequences): List<DeleteItem> = buildList {
    add(
        DeleteItem(
            title = "Your profile and handle",
            detail = facts.handle
                ?.let { "@$it is released and can be taken by someone else" }
                ?: "Your username is released and can be taken by someone else",
        ),
    )
    add(
        DeleteItem(
            title = facts.bookings?.let { bookingsLabel(it) } ?: "Your bookings",
            detail = when {
                facts.upcoming == null -> "Every one of them, including anything still upcoming"
                facts.upcoming == 0 -> "Nothing is upcoming, so nothing gets cancelled"
                facts.upcoming == 1 -> "Including the 1 upcoming one, which is cancelled"
                else -> "Including the ${facts.upcoming} upcoming ones, which are cancelled"
            },
        ),
    )
    add(
        DeleteItem(
            title = "Every message",
            detail = "Both sides lose the thread history",
        ),
    )
    if (facts.isArtist) {
        add(
            DeleteItem(
                title = "Your reviews and score",
                detail = "Every review written about you, and your Bookability Score, stop existing",
            ),
        )
    }
}

/** "128 bookings" / "1 booking" / "Your bookings" for a zero, which reads better than "0". */
private fun bookingsLabel(count: Int): String = when (count) {
    0 -> "Your bookings"
    1 -> "1 booking"
    else -> "$count bookings"
}

/** One line of the stage-3 receipt, with the mark that says how true it is. */
data class DeleteReceiptItem(val title: String, val detail: String, val mark: MarkState)

/**
 * What stage 3 itemises — the receipt.
 *
 * Four lines, and only two of them are unqualified claims about the past. The backup window is
 * a claim about the next 30 days and is deliberately NOT ticked. The calendar line reports what
 * the wipe actually managed ([calendar]): those events are on the DEVICE, not on the server,
 * and the delete cannot remove them if the calendar provider says no — so a tick there is a
 * claim this function is often not entitled to make. When it can't, the row says so and tells
 * the user what is left to do, because nobody else is going to.
 */
fun deleteReceipt(
    facts: DeleteConsequences,
    calendar: CalendarOutcome,
): List<DeleteReceiptItem> = listOf(
    DeleteReceiptItem(
        title = "Gone now",
        detail = "Your profile, bookings, messages, saved acts and reviews",
        mark = MarkState.Done,
    ),
    DeleteReceiptItem(
        title = "Handle released",
        detail = facts.handle
            ?.let { "@$it is free for someone else to take" }
            ?: "Your username is free for someone else to take",
        mark = MarkState.Done,
    ),
    when (calendar) {
        CalendarOutcome.Pending -> DeleteReceiptItem(
            title = "Clearing your calendar",
            detail = "Removing the mirrored events from this device",
            mark = MarkState.Active,
        )
        CalendarOutcome.Cleaned -> DeleteReceiptItem(
            title = "Calendar cleaned",
            detail = "Mirrored events removed from your device calendar",
            mark = MarkState.Done,
        )
        is CalendarOutcome.Failed -> DeleteReceiptItem(
            // Not "Calendar not cleaned": a negation inside a title is read as its opposite by
            // anyone scanning, and a screen reader gives it no more emphasis than the rest.
            title = "Couldn't clear your calendar",
            detail = "The mirrored events are still in your device calendar — " +
                "${calendar.reason}. Delete them there when you get a chance.",
            mark = MarkState.Failed,
        )
    },
    DeleteReceiptItem(
        title = "Backups purge in 30 days",
        detail = "Standard retention window, then it's unrecoverable",
        mark = MarkState.Pending,
    ),
)

/** The off-ramp's second clause — what Support could save, when we know what that is. */
internal fun supportOfframpLine(facts: DeleteConsequences): String {
    val bookings = facts.bookings
    return if (bookings == null || bookings == 0) {
        "without you losing your account, your history and everyone you've matched with."
    } else {
        "without you losing your ${bookingsLabel(bookings).lowercase()} and everyone " +
            "you've matched with."
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun DeleteReasonPreview() {
    ArtistantTheme {
        DeleteAccountContent(
            state = DeleteAccountUiState(reason = DeleteReason.BadExperience),
            onBack = {},
            onPick = {},
            onContinue = {},
            onConfirmationChange = {},
            onDelete = {},
            onKeep = {},
            onContactSupport = {},
            onClose = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun DeleteConsequencesPreview() {
    ArtistantTheme {
        DeleteAccountContent(
            state = DeleteAccountUiState(
                stage = DeleteStage.Consequences,
                confirmation = DELETE_KEYWORD,
                consequences = DeleteConsequences(
                    handle = "tiltcollective",
                    bookings = 128,
                    upcoming = 2,
                    isArtist = true,
                ),
            ),
            onBack = {},
            onPick = {},
            onContinue = {},
            onConfirmationChange = {},
            onDelete = {},
            onKeep = {},
            onContactSupport = {},
            onClose = {},
        )
    }
}
