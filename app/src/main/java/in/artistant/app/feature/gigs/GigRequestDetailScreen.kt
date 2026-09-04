package `in`.artistant.app.feature.gigs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.BackHeader
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.dockSurface
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * A gig request, in whichever of its four states it is in — design screens 35
 * (open), 107 (countered), 108 (declined) and 109 (not found).
 *
 * The organising idea is that **only one of the three answers is irreversible**,
 * so the screen's job is to put everything the artist needs in front of that one
 * answer before they give it. The clash warning is the whole reason 35 exists as
 * a screen rather than as two buttons on the dashboard: accepting a night the
 * artist has already sold costs a real fee and a real reputation, and there is
 * no un-accept in this build.
 *
 * Two of the design's CTAs are not here, and the PR says so: **Withdraw** (107)
 * has no write path — `RequestsRepository` offers accept / decline / counter and
 * nothing else, and there is no agreed status to move a counter back to — and
 * **Message** (107, 108) has no thread, because a thread is only created when a
 * request is ACCEPTED (mig `0047`). A button that cannot do its job is worse
 * than its absence.
 */
@Composable
fun GigRequestDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GigRequestDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val gutter = dimens.component.gutter
    val request = state.request
    var confirmingDecline by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    // Accept is the only outcome the artist gets a buzz for, and it is the
    // server's answer, not the tap. Decline buzzes at the confirm below —
    // there is nothing to celebrate on its way back.
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                GigRequestDetailEvent.Accepted -> haptics.success()
            }
        }
    }

    when {
        state.isLoading && request == null -> {
            Box(
                modifier.fillMaxSize().background(colors.page),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.accentInk)
            }
        }
        // Screen 109. Reached mostly from a push tap on a request that has since
        // gone, so the copy names both causes: a withdrawn request is DELETEd by
        // the client and an expired one is swept (mig 0090), and both leave the
        // same absent row — we genuinely cannot tell which happened, and neither
        // is the artist's fault.
        request == null -> {
            Column(
                modifier
                    .fillMaxSize()
                    .background(colors.page)
                    .semantics { testTag = "screen.gigRequestNotFound" },
            ) {
                BackHeader(
                    title = "Gig request",
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = space.sm, vertical = space.sm),
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = REQUEST_NOT_FOUND_TITLE,
                        // The design's CTA here is "See open gigs". That screen
                        // has no data source (an artist can only read requests
                        // addressed to them), so the way out is the studio,
                        // where the requests they CAN see are listed.
                        body = REQUEST_NOT_FOUND_BODY,
                        actionLabel = "Back to Studio",
                        onAction = onBack,
                    )
                }
            }
        }
        else -> RevealOnAppear {
            Column(
                modifier
                    .fillMaxSize()
                    .background(colors.page)
                    .semantics { testTag = "screen.gigRequest" },
            ) {
                BackHeader(
                    title = "Gig request",
                    subtitle = requestHeaderSubtitle(request),
                    onBack = onBack,
                    modifier = Modifier.padding(horizontal = space.sm, vertical = space.sm),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = gutter),
                    verticalArrangement = Arrangement.spacedBy(space.lg),
                ) {
                    RequesterRow(request)

                    when (request.status) {
                        GigRequestStatus.Open -> OpenBody(request, state)
                        GigRequestStatus.Countered -> CounteredBody(request)
                        GigRequestStatus.Declined -> DeclinedBody(request)
                        else -> ClosedBody(request)
                    }

                    state.actionError?.let {
                        Banner(title = "That didn't go through", detail = it, tone = BannerTone.Failure)
                    }
                    Spacer(Modifier.height(space.md))
                }

                if (viewModel.showActions()) {
                    Column(
                        Modifier.dockSurface().padding(gutter),
                        verticalArrangement = Arrangement.spacedBy(space.sm),
                    ) {
                        // Each button speaks only for ITSELF: the in-flight
                        // label keys off which action is running, while every
                        // control disables on any of them. Read from one
                        // `isActing` boolean, the dock announced "Accepting…"
                        // and "Declining…" at the same time.
                        val acting = state.actingAction
                        PrimaryButton(
                            text = if (acting == GigRequestAction.Accept) "Accepting…" else "Accept",
                            onClick = viewModel::accept,
                            fullWidth = true,
                            enabled = !state.isActing,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                            PrimaryButton(
                                text = if (acting == GigRequestAction.Decline) "Declining…" else "Decline",
                                onClick = { confirmingDecline = true },
                                variant = ButtonVariant.Ghost,
                                fullWidth = true,
                                enabled = !state.isActing,
                                modifier = Modifier.weight(1f),
                            )
                            PrimaryButton(
                                text = "Counter",
                                onClick = viewModel::showCounterSheet,
                                variant = ButtonVariant.Subtle,
                                fullWidth = true,
                                enabled = !state.isActing,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmingDecline) {
        AlertDialog(
            shape = RoundedCornerShape(AppTheme.dimens.radii.xxl),
            onDismissRequest = { confirmingDecline = false },
            title = { Text("Decline this request?") },
            text = { Text("The client is notified and this request closes. It can't be reopened.") },
            confirmButton = {
                TextButton(onClick = {
                    // Warning, not error: declining is a deliberate, legitimate
                    // action with a consequence for the client — the buzz marks
                    // its weight, it is not a failure. Fired here rather than on
                    // the result because the decision is the moment that matters.
                    haptics.warning()
                    confirmingDecline = false
                    viewModel.decline()
                }) {
                    Text("Decline request", color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDecline = false }) {
                    Text("Keep open")
                }
            },
        )
    }

    if (state.showCounterSheet) {
        CounterDialog(
            theirOffer = request?.raw?.amount ?: 0,
            amount = state.counterAmount,
            // This button's own progress, not "something is running". While the
            // sheet is up it is the only action that CAN be running — the dialog
            // is modal and the dock's controls are disabled behind it — so the
            // two agree here; the label is honest either way.
            isSending = state.actingAction == GigRequestAction.Counter,
            onAmountChange = viewModel::setCounterAmount,
            onDismiss = viewModel::dismissCounterSheet,
            onSend = viewModel::sendCounter,
        )
    }
}

/**
 * Who is asking, and for what.
 *
 * The name is usually absent and that is a schema fact, not a bug: `users` is
 * self-only under RLS, so the `client:users!client_id(full_name)` embed comes
 * back null on the artist's side, and `gig_requests` carries no denormalized
 * `client_name` the way `bookings` and `threads` do (mig 0080). "A client" is
 * used rather than a fabricated name — it reads the same on every request,
 * which is exactly what it means.
 */
@Composable
private fun RequesterRow(request: StoredRequest) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val name = request.requesterName
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Avatar(name = name ?: "?", size = dimens.component.rowAvatar)
        Column(Modifier.weight(1f)) {
            Text(
                name ?: "A client",
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
            )
            requestIdentityLine(request).takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        Pill(request.status.label, tone = requestStatusTone(request.status))
    }
}

/** Screen 35 — the live request, with the clash warning above the dock. */
@Composable
private fun OpenBody(request: StoredRequest, state: GigRequestDetailUiState) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.lg)) {
        ProposalCard(label = "Their proposal", amount = request.raw.amount)
        FactRows(requestFacts(request))
        MessageBlock(request)

        // Above the dock, and drawn as a warning rather than as a note: this is
        // the fact that should stop a thumb already moving toward Accept.
        clashWarning(state.clashes)?.let { warning ->
            Banner(title = "Calendar clash", detail = warning, tone = BannerTone.Attention)
        }
        Text(
            "Declining is final — the client is notified and this request closes.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
}

/**
 * Screen 107 — countered. Both numbers, side by side.
 *
 * Theirs struck through and quiet, yours in the accent: the strike is what makes
 * the negotiation state readable without reading a word, and it is the one place
 * on this screen the accent is spent.
 */
@Composable
private fun CounteredBody(request: StoredRequest) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val name = request.requesterName ?: "the client"
    Column(verticalArrangement = Arrangement.spacedBy(space.lg)) {
        AccentNote(
            lead = "Your counter is with $name.",
            text = "They can accept it, come back with another number, or decline.",
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space.md),
        ) {
            AmountCell(
                label = "Their offer",
                amount = formatInr(request.raw.amount),
                struck = true,
                modifier = Modifier.weight(1f),
            )
            AmountCell(
                label = "Your counter",
                amount = request.counterAmount?.let(::formatInr) ?: "—",
                accent = true,
                modifier = Modifier.weight(1f),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            EyebrowLabel("History")
            negotiationHistory(request).forEachIndexed { index, entry ->
                if (index > 0) HRule()
                Row(
                    Modifier.fillMaxWidth().padding(vertical = space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        entry.who,
                        style = AppTheme.type.subtitle,
                        color = colors.ink2,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        listOfNotNull(entry.amount, entry.whenAgo).joinToString(" · "),
                        style = AppTheme.type.footnote,
                        color = colors.ink,
                    )
                }
            }
        }
        FactRows(requestFacts(request))
        Banner(
            title = "The date stays open while you negotiate — it isn't held " +
                "until someone accepts.",
            tone = BannerTone.Note,
        )
        // No Withdraw, no Message: see the note on the screen. Saying which is
        // missing beats a button that does nothing.
        Text(
            "There's no way to withdraw a counter yet, and a conversation only " +
                "opens once a request is accepted.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
    }
}

/** Screen 108 — declined. Terminal, and it names the one route back. */
@Composable
private fun DeclinedBody(request: StoredRequest) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val name = request.requesterName ?: "The client"
    Column(verticalArrangement = Arrangement.spacedBy(space.lg)) {
        Banner(
            title = "You declined this request",
            detail = "$name was notified and the request closed — it can't be " +
                "reopened from either side.",
            tone = BannerTone.Failure,
        )
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            EyebrowLabel("What was asked")
            FactRows(
                listOf("Their offer" to formatInr(request.raw.amount)) + requestFacts(request),
            )
        }
        Text(
            "Declining doesn't cost score points. Leaving a request unanswered " +
                "for over 48 hours does.",
            style = AppTheme.type.caption,
            color = colors.ink4,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimens.radii.card))
                .background(colors.surface3)
                .padding(dimens.component.cardPad),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            EyebrowLabel("If this was a mistake")
            Text(
                "Nothing stops a second request for the same date. Ask $name to " +
                    "send a fresh one — a declined request can't be revived, but a " +
                    "new one behaves exactly like the first.",
                style = AppTheme.type.subtitle,
                color = colors.ink2,
            )
        }
    }
}

/** Accepted, expired, or a status this build doesn't recognise: read-only. */
@Composable
private fun ClosedBody(request: StoredRequest) {
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.lg)) {
        ProposalCard(
            label = if (request.counterAmount != null) "Agreed at" else "Their proposal",
            amount = request.counterAmount ?: request.raw.amount,
        )
        FactRows(requestFacts(request))
        MessageBlock(request)
    }
}

/** The proposal's headline number — one figure, at hero size, on the accent. */
@Composable
private fun ProposalCard(label: String, amount: Int) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.xl))
            .background(colors.surface3)
            .padding(dimens.component.heroPad),
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        EyebrowLabel(label)
        Text(formatInr(amount), style = AppTheme.type.monoHero, color = colors.ink, maxLines = 1)
    }
}

@Composable
private fun AmountCell(
    label: String,
    amount: String,
    modifier: Modifier = Modifier,
    struck: Boolean = false,
    accent: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier
            .clip(RoundedCornerShape(dimens.radii.lg))
            .background(colors.surface3)
            .padding(dimens.component.cardPad),
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Text(label, style = AppTheme.type.caption, color = colors.ink4)
        Text(
            amount,
            style = AppTheme.type.monoMedium.copy(
                textDecoration = if (struck) TextDecoration.LineThrough else null,
                fontWeight = if (accent) FontWeight.Bold else FontWeight.Normal,
            ),
            color = if (accent) colors.accentInk else colors.ink3,
            maxLines = 1,
        )
    }
}

@Composable
private fun FactRows(facts: List<Pair<String, String>>) {
    if (facts.isEmpty()) return
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxWidth()) {
        facts.forEachIndexed { index, (label, value) ->
            if (index > 0) HRule()
            Row(
                Modifier.fillMaxWidth().padding(vertical = space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = AppTheme.type.subtitle,
                    color = colors.ink4,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    value,
                    style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
            }
        }
    }
}

@Composable
private fun MessageBlock(request: StoredRequest) {
    if (request.raw.message.isBlank()) return
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        EyebrowLabel("Their message")
        Text(request.raw.message, style = AppTheme.type.body, color = colors.ink2)
    }
}

@Composable
private fun CounterDialog(
    theirOffer: Int,
    amount: String,
    isSending: Boolean,
    onAmountChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val radii = AppTheme.dimens.radii
    val parsed = amount.toIntOrNull() ?: 0

    AlertDialog(
        shape = RoundedCornerShape(radii.xxl),
        onDismissRequest = onDismiss,
        title = { Text("Counter offer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                Text(
                    "Their offer: ${formatInr(theirOffer)}",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radii.control))
                        .border(
                            AppTheme.dimens.size.hairline,
                            colors.line,
                            RoundedCornerShape(radii.control),
                        )
                        .padding(space.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("₹", style = AppTheme.type.monoLarge, color = colors.ink3)
                    BasicTextField(
                        value = amount,
                        onValueChange = onAmountChange,
                        textStyle = AppTheme.type.monoLarge.copy(color = colors.ink),
                        modifier = Modifier.padding(start = space.xs),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSend, enabled = !isSending && parsed > 0) {
                Text(if (isSending) "Sending…" else "Send counter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
