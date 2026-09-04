package `in`.artistant.app.feature.booking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.component.isTrusted
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Screen 06 — "Confirm request", the last screen before a booking exists.
 *
 * The design's note is the product's whole v1 position: **no money in v1.**
 * Checkout confirms terms and sets an expectation; it never collects a payment.
 * So there is exactly one number on the page — the artist's quoted fee — and the
 * accent-washed note under it says in words what the absent card field says by
 * omission.
 *
 * It is a review, not a form: every value on it was decided one screen back. The
 * rows lost their per-row chevrons in the redesign, which is deliberate — the
 * light design draws them as plain terms, and with the funnel now explicitly
 * "Step 1 of 2" the back control IS the way to change them. Six tappable rows
 * that all did the same thing were six affordances for one action.
 */
@Composable
fun CheckoutScreen(
    onBack: () -> Unit,
    onConfirmed: (bookingId: String) -> Unit,
    onPaywall: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CheckoutViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val gutter = dimens.component.gutter
    val haptics = rememberHaptics()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                CheckoutEvent.Sent -> haptics.success()
                CheckoutEvent.Failed -> haptics.error()
            }
        }
    }

    LaunchedEffect(state.confirmedBookingId) {
        state.confirmedBookingId?.let { id ->
            onConfirmed(id)
            viewModel.clearNavigation()
        }
    }
    LaunchedEffect(state.needsPaywall) {
        if (state.needsPaywall) {
            onPaywall()
            viewModel.consumePaywall()
        }
    }

    // Leaving mid-write let the create finish invisibly: the booking landed, the
    // confirmation screen never showed, and the client's natural next move was to
    // send it again. Both exits are held shut until the write resolves.
    BackHandler(enabled = state.isSubmitting) { /* swallow */ }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(Modifier.fillMaxSize()) {
            FunnelBar(
                title = "Confirm request",
                subtitle = "Nothing is charged — v1 takes no payment",
                onLeading = { if (!state.isSubmitting) onBack() },
            )

            val draft = state.draft
            if (draft == null) {
                EmptyState(
                    title = "Nothing to confirm",
                    body = state.lastCreateErrorMessage
                        ?: "This request expired or was already sent. Go back and pick a date again.",
                    actionLabel = "Back",
                    onAction = onBack,
                )
                return@Column
            }

            RevealOnAppear {
                Column(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = gutter)
                            .padding(top = space.lg, bottom = space.xl),
                        verticalArrangement = Arrangement.spacedBy(space.lg),
                    ) {
                        state.lastCreateErrorMessage?.let { message ->
                            Banner(
                                title = message,
                                tone = BannerTone.Failure,
                                actionLabel = "Retry",
                                onAction = viewModel::sendRequest,
                            )
                        }

                        FunnelCard {
                            ActRow(
                                name = state.artistName.ifBlank { "Artist" },
                                coverUrl = state.artist?.coverUrl,
                                trusted = state.artist?.let { isTrusted(it.score, it.gigs) } == true,
                                lines = checkoutActMeta(draft),
                            )
                        }

                        if (state.artistHasNoPackages) {
                            // Nothing here can be confirmed: a package-less artist
                            // has no tier and no fixed price, so sending would file
                            // a request against a number they never quoted. Point
                            // at the path that does work instead of leaving a dead
                            // button under a blank summary.
                            CustomQuoteNotice()
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                                Text(
                                    "Your request",
                                    style = AppTheme.type.sectionTitle,
                                    color = colors.ink,
                                )
                                checkoutReviewRows(draft).forEach { row ->
                                    TermRow(label = row.label, value = row.value)
                                }
                                HRule(Modifier.padding(vertical = space.xs))
                                TermRow(
                                    label = "Artist fee",
                                    value = formatInr(draft.feeInr),
                                    emphasis = true,
                                )
                            }
                            NoteBlock(
                                "No card, no deposit. The fee is what you agree with the artist " +
                                    "and settle directly — Artistant takes nothing in this version.",
                            )
                            WhatHappensNext()
                        }
                    }

                    CtaBar {
                        PrimaryButton(
                            text = if (state.isSubmitting) "Sending request…" else "Send request",
                            onClick = viewModel::sendRequest,
                            fullWidth = true,
                            enabled = !state.blocked,
                        )
                        CtaCaption("We recommend asking at least 3 artists")
                    }
                }
            }
        }

        // Full-screen narrated takeover during the submit — see NarratedWait.
        state.waitPhase?.let { phase ->
            NarratedWait(checkoutWaitCopy(phase, state.artistName))
        }
    }
}

/**
 * What the client gets for tapping Send — the design's closing block.
 *
 * A card rather than another note: it is not a term of the deal, it is what
 * happens next, and the two blocks have to be told apart at a glance on a page
 * whose one accent has already been spent on the fee note above.
 */
@Composable
private fun WhatHappensNext() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    FunnelCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        ) {
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(dimens.size.iconLg),
            )
            Text(
                "What happens next",
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
        }
        Text(
            // No response-window promise: nothing on this path enforces one
            // (`expires_at` belongs to the gig-request flow, not to a booking),
            // so "usually within an hour" is the artist's own published reply
            // speed being repeated, not a guarantee we can keep. It is left out.
            "$CHECKOUT_EXPECTATION Accepting opens a chat thread with them.",
            style = AppTheme.type.subtitle,
            color = colors.ink2,
            modifier = Modifier.padding(top = dimens.space.sm),
        )
    }
}

@Composable
private fun CustomQuoteNotice() {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
        Text(
            "This artist takes custom requests.",
            style = AppTheme.type.body,
            color = colors.ink,
        )
        Text(
            "They don't list fixed packages — go back and use \"Request a quote\" to tell them " +
                "about your event and get a price.",
            style = AppTheme.type.subtitle,
            color = colors.ink3,
        )
    }
}

/**
 * The narrated wait.
 *
 * A bare spinner past a couple of seconds stops meaning "working" and starts
 * meaning "stuck", and this submit has two server hops in it. Naming the step —
 * and the artist — turns the same wait into progress. It covers the content
 * rather than sitting inside the CTA so there is nothing left to tap while the
 * write is in flight.
 */
@Composable
private fun NarratedWait(copy: CheckoutWaitCopy) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val interaction = remember { MutableInteractionSource() }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surface)
            // Swallows taps for the duration. An opaque fill hides the CTA but
            // does not consume touches on its own — without this the Send button
            // underneath is still tappable through the overlay, which is the
            // double-submit this whole takeover exists to prevent.
            .clickable(interactionSource = interaction, indication = null) {}
            .padding(space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.xl, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = colors.accentInk)
        Column(
            verticalArrangement = Arrangement.spacedBy(space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                copy.title,
                style = AppTheme.type.displaySmall,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                copy.subtitle,
                style = AppTheme.type.body,
                color = colors.ink3,
                textAlign = TextAlign.Center,
            )
        }
    }
}
