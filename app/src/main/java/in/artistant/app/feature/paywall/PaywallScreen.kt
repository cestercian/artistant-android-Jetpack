package `in`.artistant.app.feature.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.AccentNote
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.CheckRow
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.MarkState
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.component.hairlineTop
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Design screens 25 / 91 / 92 / 93 — **Artistant Pro, in its four honest states**.
 *
 * One destination, four states, and [proStateFor] is the only thing that picks between them —
 * so the screen cannot claim an entitlement Play Billing has not confirmed, and an outage can
 * never present as a lost plan (screen 92's whole note).
 *
 * **What ships today is screen 92**, because `AppEnvironment.subscriptionsEnabled` is false:
 * there is no product to query and no price to print. That is not a placeholder — it is the
 * correct state for a dormant seam, and its copy is written for exactly that ("your current
 * plan is unchanged").
 *
 * **The design's two-up plan chooser is one card here.** Play carries a single product
 * (`in.artistant.app.subscription.monthly`); there is no yearly SKU, so a "Yearly −33%" card
 * would be an offer nobody could accept. The price printed is whatever Play formats, never a
 * hard-coded ₹499.
 *
 * **The active screen drops the design's fee arithmetic.** Screen 93 sells "5% platform fee,
 * not 9%", "Payout in 24 hours" and "₹18,400 saved in fees" — all three describe a product that
 * moves money, and v1 is a no-payments matchmaker with no fee, no payout and nothing to save.
 * The perks listed are the ones from screen 25, which are about reach and proof, and the
 * "saved so far" block is replaced by the renewal fact — the thing a subscriber actually needs
 * from this screen.
 */
@Composable
fun PaywallScreen(
    role: AppRole,
    onClose: () -> Unit,
    onComplete: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(role) { viewModel.bindRole(role) }

    PaywallContent(
        state = state,
        onClose = onClose,
        onSubscribe = { viewModel.subscribe(context.findActivity(), onComplete) },
        onRetry = viewModel::load,
        onRestore = viewModel::restore,
        onDismissPending = viewModel::dismissPending,
        modifier = modifier,
    )
}

@Composable
private fun PaywallContent(
    state: PaywallUiState,
    onClose: () -> Unit,
    onSubscribe: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onDismissPending: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val gutter = dimens.component.gutter

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface)
            .semantics { testTag = "screen.paywall" },
    ) {
        // The offer, pending and unavailable screens are dismissible sheets with a close
        // control; the active screen is a settings destination reached from the account list,
        // so it takes the same close control rather than a second, different back affordance.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = dimens.space.sm),
            horizontalArrangement = Arrangement.End,
        ) {
            IconCircle(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                modifier = Modifier.semantics { testTag = "paywall.close" },
            )
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = gutter),
        ) {
            when {
                state.loading -> PaywallLoading()
                else -> when (state.proState) {
                    ProState.Offer -> PaywallOffer(state)
                    ProState.Pending -> PaywallPending(state)
                    ProState.Unavailable -> PaywallUnavailable()
                    ProState.Active -> PaywallActive(state)
                }
            }
            Spacer(Modifier.height(dimens.size.listTailroom))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .hairlineTop()
                .padding(horizontal = gutter)
                .padding(top = dimens.space.lg, bottom = dimens.space.xl),
            verticalArrangement = Arrangement.spacedBy(dimens.space.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            state.error?.let { message ->
                Text(
                    message,
                    style = AppTheme.type.caption,
                    color = colors.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { testTag = "paywall.error" },
                )
            }
            PaywallFooter(
                state = state,
                onSubscribe = onSubscribe,
                onRetry = onRetry,
                onRestore = onRestore,
                onDismissPending = onDismissPending,
            )
        }
    }
}

@Composable
private fun ColumnScope.PaywallFooter(
    state: PaywallUiState,
    onSubscribe: () -> Unit,
    onRetry: () -> Unit,
    onRestore: () -> Unit,
    onDismissPending: () -> Unit,
) {
    val colors = AppTheme.colors
    if (state.loading) {
        SecondaryButton(text = "Loading plans…", onClick = {}, fullWidth = true, enabled = false)
        return
    }
    when (state.proState) {
        ProState.Offer -> {
            PrimaryButton(
                text = "Subscribe · ${state.price} a month",
                onClick = onSubscribe,
                fullWidth = true,
                enabled = !state.working,
                modifier = Modifier.semantics { testTag = "paywall.subscribe" },
            )
            Text(
                "Auto-renews monthly. Cancel anytime in Google Play → Subscriptions.",
                style = AppTheme.type.caption,
                color = colors.ink4,
                textAlign = TextAlign.Center,
            )
        }
        // Disabled rather than absent, and it says what it is waiting for. The design's note:
        // "the CTA disables rather than lying."
        ProState.Pending -> {
            SecondaryButton(
                text = "Confirming…",
                onClick = {},
                fullWidth = true,
                enabled = false,
                modifier = Modifier.semantics { testTag = "paywall.confirming" },
            )
            Text(
                "Safe to close — we'll notify you either way.",
                style = AppTheme.type.caption,
                color = colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .semantics { testTag = "paywall.safeToClose" },
            )
            SecondaryButton(text = "Check again", onClick = onDismissPending, fullWidth = true)
        }
        ProState.Unavailable -> {
            PrimaryButton(
                text = "Try again",
                onClick = onRetry,
                fullWidth = true,
                enabled = !state.working,
                modifier = Modifier.semantics { testTag = "paywall.retry" },
            )
            SecondaryButton(
                text = "Restore purchases",
                onClick = onRestore,
                fullWidth = true,
                enabled = !state.working,
                modifier = Modifier.semantics { testTag = "paywall.restore" },
            )
        }
        ProState.Active -> SecondaryButton(
            text = "Manage in Google Play",
            onClick = onRestore,
            fullWidth = true,
            enabled = !state.working,
            modifier = Modifier.semantics { testTag = "paywall.manage" },
        )
    }
}

/** The first store query. Narrated, and it does not pretend to be a plan list. */
@Composable
private fun PaywallLoading() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier.fillMaxWidth().padding(vertical = dimens.space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
    ) {
        CircularProgressIndicator(
            Modifier.size(dimens.size.avatarSm),
            strokeWidth = dimens.size.stroke,
            color = colors.accent,
            trackColor = colors.hairline,
        )
        Text("Checking your plan…", style = AppTheme.type.sectionTitle, color = colors.ink)
    }
}

/** 25 — the offer. */
@Composable
private fun PaywallOffer(state: PaywallUiState) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    AppMark()
    Spacer(Modifier.height(dimens.space.lg))
    Text("Get seen first.", style = AppTheme.type.displayHero, color = colors.ink)
    Text("Get believed faster.", style = AppTheme.type.displayHero, color = colors.ink)
    Spacer(Modifier.height(dimens.space.sm))
    Text(
        if (state.isArtist) {
            "For artists booking more than two shows a month."
        } else {
            "For hosts booking more than two shows a month."
        },
        style = AppTheme.type.body,
        color = colors.ink4,
    )
    Spacer(Modifier.height(dimens.space.xl))
    PRO_PERKS.forEach { perk -> CheckRow(perk.title, MarkState.Done, subtitle = perk.detail) }
    Spacer(Modifier.height(dimens.space.xl))
    PlanCard(price = state.price.orEmpty())
}

/** 91 — a deferred payment settling. */
@Composable
private fun PaywallPending(state: PaywallUiState) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier.fillMaxWidth().padding(top = dimens.space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
    ) {
        Box(
            Modifier
                .size(dimens.size.avatarMd)
                .background(colors.surface3, RoundedCornerShape(dimens.radii.lg)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                Modifier.size(dimens.size.iconXl),
                strokeWidth = dimens.size.stroke,
                color = colors.accent,
                trackColor = colors.hairline,
            )
        }
        Text(
            "Waiting for approval…",
            style = AppTheme.type.displaySub,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            "Your bank or Google Play is confirming the payment. This can take a minute.",
            style = AppTheme.type.body,
            color = colors.ink4,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.surface3, RoundedCornerShape(dimens.radii.lg))
                .padding(dimens.space.lg),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Artistant Pro · monthly", style = AppTheme.type.rowTitle, color = colors.ink)
                Text(
                    "Nothing is charged until this clears.",
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
            state.price?.let {
                Text(it, style = AppTheme.type.monoPrice, color = colors.ink)
            }
        }
    }
}

/** 92 — the store could not be reached. Never implies a lost plan. */
@Composable
private fun PaywallUnavailable() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Banner(
        title = "Subscription unavailable",
        tone = BannerTone.Failure,
        detail = "We couldn't reach the store.",
        modifier = Modifier.semantics { testTag = "paywall.unavailableBanner" },
    )
    Column(
        Modifier.fillMaxWidth().padding(top = dimens.space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space.lg),
    ) {
        Box(
            Modifier
                .size(dimens.component.emptyGlyphCircle)
                .background(colors.surface3, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = colors.ink4,
                modifier = Modifier.size(dimens.component.emptyGlyph),
            )
        }
        Text(
            "Can't load plans right now",
            style = AppTheme.type.displaySub,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Text(
            "Pull back and try again in a moment. Your current plan is unchanged.",
            style = AppTheme.type.body,
            color = colors.ink4,
            textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.height(dimens.space.xl))
    AccentNote(
        text = "Already subscribed on another device? Restore purchases brings it across " +
            "without paying twice.",
    )
}

/** 93 — entitled. Its own screen, not a badge on the offer. */
@Composable
private fun PaywallActive(state: PaywallUiState) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.accent, RoundedCornerShape(dimens.radii.xl))
            .padding(dimens.space.xl)
            .semantics { testTag = "paywall.activeCard" },
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ACTIVE",
                style = AppTheme.type.monoLabel,
                color = colors.onAccent,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = colors.onAccent,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
        Text("Artistant Pro", style = AppTheme.type.displaySub, color = colors.onAccent)
        state.price?.let {
            Text("$it a month", style = AppTheme.type.body, color = colors.onAccent)
        }
    }
    Spacer(Modifier.height(dimens.space.xl))
    EyebrowLabel("What you're getting", color = colors.ink4)
    Spacer(Modifier.height(dimens.space.sm))
    PRO_PERKS.forEach { perk -> CheckRow(perk.title, MarkState.Done, subtitle = perk.detail) }
    Spacer(Modifier.height(dimens.space.lg))
    AccentNote(
        text = "Auto-renews until cancelled. Manage or cancel anytime in Google Play → " +
            "Subscriptions — Artistant never charges you directly.",
    )
}

/** The one plan Play actually carries. */
@Composable
private fun PlanCard(price: String) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.lg)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.brandSoft, shape)
            .border(dimens.component.focusStroke, colors.accent, shape)
            .padding(dimens.space.lg)
            .semantics { testTag = "paywall.plan" },
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Text("Monthly", style = AppTheme.type.rowTitle, color = colors.ink)
        Text(price, style = AppTheme.type.displaySub, color = colors.ink)
        Text("Billed by Google Play", style = AppTheme.type.caption, color = colors.ink3)
    }
}

/**
 * The app mark — a dark rounded square carrying a lime "A" (design 25's header).
 *
 * A local copy rather than a call into `feature/signup`'s `AppMark`: this package must not
 * depend on the signup flow for a 64dp square, and the rule it follows (mono face, Black
 * weight, `darkest` ground) is one line either way.
 */
@Composable
private fun AppMark() {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .size(dimens.size.avatarLg)
            .background(colors.darkest, RoundedCornerShape(dimens.radii.card)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "A",
            style = AppTheme.type.monoNumber.copy(fontSize = AppTheme.type.displayHero.fontSize),
            color = colors.accent,
        )
    }
}

/** One perk bullet. */
private data class ProPerk(val title: String, val detail: String)

/**
 * What Pro sells, in the design's own words (screen 25).
 *
 * Deliberately the SAME list on the offer and the active screen. Screen 93 sells a different
 * four — a lower platform fee, faster payouts — and all of those describe a product that takes
 * money. This one does not: there is no platform fee charged to an artist, no payout path and
 * nothing to save, so those lines would be false on the one screen a paying subscriber reads
 * most carefully.
 */
private val PRO_PERKS = listOf(
    ProPerk("Verified badge on your profile", "ID and past shows checked by us"),
    ProPerk("Priority in search", "Above free acts at the same score"),
    ProPerk("Unlimited proposals", "Free stops at eight a month"),
    ProPerk("Score history and trends", "See every event that moved your score"),
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun PaywallOfferPreview() {
    ArtistantTheme {
        PaywallContent(
            state = PaywallUiState(loading = false, price = "₹499"),
            onClose = {},
            onSubscribe = {},
            onRetry = {},
            onRestore = {},
            onDismissPending = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun PaywallUnavailablePreview() {
    ArtistantTheme {
        PaywallContent(
            state = PaywallUiState(loading = false, price = null),
            onClose = {},
            onSubscribe = {},
            onRetry = {},
            onRestore = {},
            onDismissPending = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, heightDp = 900)
@Composable
private fun PaywallActivePreview() {
    ArtistantTheme {
        PaywallContent(
            state = PaywallUiState(loading = false, entitled = true, price = "₹499"),
            onClose = {},
            onSubscribe = {},
            onRetry = {},
            onRestore = {},
            onDismissPending = {},
        )
    }
}
