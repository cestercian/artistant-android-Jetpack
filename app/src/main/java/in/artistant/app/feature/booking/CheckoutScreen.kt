package `in`.artistant.app.feature.booking

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.designsystem.component.Avatar
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.InlineBanner
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.designsystem.component.RevealOnAppear
import `in`.artistant.app.designsystem.rememberHaptics
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Confirm request — the last screen before a booking exists.
 *
 * It is a review, not a form: every value on it was decided one screen back, and
 * each row's chevron pops there to change it rather than re-rendering the editor
 * inline. That keeps one source for each input, which is what stops the venue
 * being editable in two places that then disagree.
 *
 * v1 is a matchmaker, so there is exactly one number on this page — the artist's
 * quoted fee. No platform fee, no GST, no total, no payment step: the CTA sends a
 * REQUEST that lands `pending_confirm` for the artist to accept.
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
    val space = AppTheme.dimens.space
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

    Box(modifier.fillMaxSize().background(colors.bg)) {
        Column(Modifier.fillMaxSize()) {
            FunnelHeader(
                title = "Confirm request",
                onBack = { if (!state.isSubmitting) onBack() },
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
                            .padding(horizontal = space.lg)
                            .padding(top = space.md, bottom = space.xl),
                        verticalArrangement = Arrangement.spacedBy(space.lg),
                    ) {
                        state.lastCreateErrorMessage?.let { message ->
                            InlineBanner(
                                title = message,
                                tone = BannerTone.Failure,
                                actionLabel = "Retry",
                                onAction = viewModel::sendRequest,
                            )
                        }

                        IdentityHeader(
                            name = state.artistName.ifBlank { "Artist" },
                            packageName = draft.packageName,
                            score = state.artist?.score,
                        )

                        if (state.artistHasNoPackages) {
                            // Nothing here can be confirmed: a package-less
                            // artist has no tier and no fixed price, so sending
                            // would file a request against a number they never
                            // quoted. Point at the path that does work instead of
                            // leaving a dead button under a blank summary.
                            CustomQuoteNotice()
                        } else {
                            ReviewRows(draft = draft, onEdit = onBack)
                            FeeRow(draft.feeInr)
                        }
                    }

                    CtaBar {
                        Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                            Text(
                                CHECKOUT_EXPECTATION,
                                style = AppTheme.type.footnote,
                                color = colors.ink3,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            FunnelCta(
                                text = if (state.isSubmitting) "Sending request…" else "Send request",
                                onClick = viewModel::sendRequest,
                                fullWidth = true,
                                enabled = !state.blocked,
                            )
                        }
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
 * Who this request is going to, which tier, and how they score.
 *
 * Read-only, unlike everything below it: the artist and their score are not
 * things this screen can change, and giving them a chevron would suggest
 * otherwise.
 */
@Composable
private fun IdentityHeader(name: String, packageName: String, score: Int?) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = name, size = AppTheme.dimens.size.avatarMd)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = AppTheme.type.headline,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                packageName,
                style = AppTheme.type.footnote,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Self-hiding rather than showing a zero: an artist we couldn't hydrate
        // has no score to report, and "0" is a claim about them.
        score?.let {
            Pill(
                it.toString(),
                // Solid, not washed: this is the only chip on the screen and the
                // reference build fills it. A soft chip here read as decoration
                // beside the artist's name rather than as their score.
                tone = PillTone.BrandSolid,
                modifier = Modifier.semantics {
                    contentDescription = "Bookability score $it"
                },
            )
        }
    }
}

/**
 * The decided inputs, one collapsed line each, every one of them a way back to
 * the form that owns it.
 */
@Composable
private fun ReviewRows(draft: BookingDraft, onEdit: () -> Unit) {
    Column {
        val rows = checkoutReviewRows(draft)
        rows.forEachIndexed { index, row ->
            if (index == 0) HRule()
            ReviewRow(row, onEdit)
            HRule()
        }
    }
}

@Composable
private fun ReviewRow(row: CheckoutReviewRow, onEdit: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = "${row.label}: ${row.value}. Edit"
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Muted label left, value hard right — the same row anatomy the booking
        // detail's terms use, so the request reads identically before and after
        // it is sent.
        Text(row.label, style = AppTheme.type.footnote, color = colors.ink3)
        Text(
            row.value,
            style = AppTheme.type.callout,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            // The row's own semantics already announce "Edit"; the chevron is a
            // glyph, not a second thing to read.
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(dimens.size.iconMd),
        )
    }
}

/**
 * The one number on the page. Brand-tinted mono, because it is the figure the
 * client is weighing and the only place this screen asks for a decision about
 * money — v1 takes none of it.
 */
@Composable
private fun FeeRow(feeInr: Int) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().padding(top = space.sm),
        horizontalArrangement = Arrangement.spacedBy(space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Artist fee", style = AppTheme.type.headline, color = colors.ink)
        Spacer(Modifier.weight(1f))
        Text(
            formatInr(feeInr),
            style = AppTheme.type.monoHero,
            color = colors.brand,
            maxLines = 1,
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
            style = AppTheme.type.footnote,
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
            .background(colors.bg)
            // Swallows taps for the duration. An opaque fill hides the CTA but
            // does not consume touches on its own — without this the Send button
            // underneath is still tappable through the overlay, which is the
            // double-submit this whole takeover exists to prevent.
            .clickable(interactionSource = interaction, indication = null) {}
            .padding(space.xxl),
        verticalArrangement = Arrangement.spacedBy(space.xl, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = colors.brand)
        Column(
            verticalArrangement = Arrangement.spacedBy(space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Editorial serif — this is a moment in the flow, not chrome.
            Text(
                copy.title,
                style = AppTheme.type.displaySub,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(
                copy.subtitle,
                style = AppTheme.type.callout,
                color = colors.ink3,
                textAlign = TextAlign.Center,
            )
        }
    }
}
