package `in`.artistant.app.feature.paywall

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.platform.billing.SubscriptionProduct

/**
 * The ₹99/mo subscription paywall (port of iOS `PaywallView`) — role-aware by the [productId]
 * suffix (`.artist.monthly` sells "Stay listed"; the client sells "Keep booking"). Only ever
 * reached when `subscriptionsEnabled` (the banner + the checkout gate are flag-guarded), so in
 * v1 this screen is dormant. Editorial-dark treatment, design tokens throughout.
 *
 * Play Billing Guideline parity: price + billing period + auto-renew terms, an explicit
 * "Restore purchases" control, and Terms + Privacy links are all present.
 *
 * @param onComplete called on a successful purchase, before [onClose] — lets a gate resume the
 *   action it interrupted (e.g. confirm the booking it blocked).
 * @param onClose dismiss the paywall (the sheet's close affordance + post-purchase unwind).
 */
@Composable
fun PaywallScreen(
    productId: String,
    onClose: () -> Unit,
    onComplete: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val products by viewModel.products.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val pending by viewModel.purchasePending.collectAsStateWithLifecycle()

    val isArtist = productId.endsWith(".artist.monthly")
    val product = products.firstOrNull { it.id == productId }

    // Success: resume the interrupted gate, then dismiss.
    val onPurchased: () -> Unit = { onComplete(); onClose() }

    Column(Modifier.fillMaxSize().background(colors.bg)) {
        // Header — close affordance.
        Row(
            Modifier.fillMaxWidth().padding(space.lg),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(colors.bgCard).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.ink3, modifier = Modifier.size(16.dp))
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(space.xl),
            verticalArrangement = Arrangement.spacedBy(space.xl),
        ) {
            Hero(isArtist)
            Perks(isArtist)
            PriceCard(product)
        }

        CtaBar(
            product = product,
            working = working,
            pending = pending,
            onSubscribe = { viewModel.subscribe(productId) { onPurchased() } },
            onRestore = { viewModel.restore(productId) { onPurchased() } },
        )
    }
}

// MARK: - Hero

@Composable
private fun Hero(isArtist: Boolean) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text(
            if (isArtist) "ARTIST MEMBERSHIP" else "CLIENT MEMBERSHIP",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.ink3,
        )
        // Editorial signature — the second word in italic brand (iOS `editorialHeadline`).
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (isArtist) "Stay" else "Keep",
                style = AppTheme.type.displayMedium,
                color = colors.ink,
            )
            Text(
                if (isArtist) "listed." else "booking.",
                style = AppTheme.type.displayMedium.copy(fontStyle = FontStyle.Italic),
                color = colors.brand,
            )
        }
        Text(
            if (isArtist)
                "Keep your profile discoverable and keep receiving client leads."
            else
                "Keep matching with artists after your free intro.",
            style = AppTheme.type.callout,
            color = colors.ink3,
        )
    }
}

// MARK: - Perks

@Composable
private fun Perks(isArtist: Boolean) {
    val space = AppTheme.dimens.space
    val items = if (isArtist) {
        listOf(
            "Stay listed across Discover and search",
            "Receive and reply to client requests",
            "Your Bookability Score keeps compounding",
        )
    } else {
        listOf(
            "Message and book any artist",
            "All your bookings in one place",
            "Help when you need it",
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        items.forEach { PerkRow(it) }
    }
}

@Composable
private fun PerkRow(text: String) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(horizontalArrangement = Arrangement.spacedBy(space.md), verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
        )
        Text(text, style = AppTheme.type.body, color = colors.ink, modifier = Modifier.weight(1f))
    }
}

// MARK: - Price + terms

@Composable
private fun PriceCard(product: SubscriptionProduct?) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val uriHandler = LocalUriHandler.current

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(colors.bgCard)
            .padding(space.lg),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        if (product != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(product.displayPrice, style = AppTheme.type.monoLarge, color = colors.ink)
                Text("/ ${product.periodLabel}", style = AppTheme.type.callout, color = colors.ink3)
            }
            product.introOffer?.let { intro ->
                Text(intro, style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.brand)
            }
        } else {
            Text(
                "Subscription unavailable right now. Pull back and try again.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        }
        Text(
            "Auto-renews every month until cancelled. Manage or cancel anytime in Google Play → Subscriptions.",
            style = AppTheme.type.caption,
            color = colors.ink3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(space.md)) {
            Text(
                "Terms",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink2,
                modifier = Modifier.clickable { uriHandler.openUri(AppEnvironment.TERMS_URL) },
            )
            Text("·", style = AppTheme.type.caption, color = colors.ink3)
            Text(
                "Privacy",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink2,
                modifier = Modifier.clickable { uriHandler.openUri(AppEnvironment.PRIVACY_URL) },
            )
        }
    }
}

// MARK: - CTA bar (subscribe + restore)

@Composable
private fun CtaBar(
    product: SubscriptionProduct?,
    working: Boolean,
    pending: Boolean,
    onSubscribe: () -> Unit,
    onRestore: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val enabled = product != null && !working

    Column(Modifier.fillMaxWidth().background(colors.bg)) {
        HRule()
        Column(
            Modifier.fillMaxWidth().padding(horizontal = space.xl, vertical = space.md),
            verticalArrangement = Arrangement.spacedBy(space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (pending) {
                Text(
                    "Waiting for approval…",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                )
            }
            // Custom brand capsule (PrimaryButton has no spinner slot) — spinner while working.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(if (enabled) colors.brand else colors.bgSoft)
                    .clickable(enabled = enabled) { onSubscribe() },
                contentAlignment = Alignment.Center,
            ) {
                if (working) {
                    CircularProgressIndicator(color = colors.brandInk, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        ctaTitle(product),
                        style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
                        color = if (enabled) colors.brandInk else colors.ink3,
                    )
                }
            }
            Text(
                "Restore purchases",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink2,
                modifier = Modifier.clickable(enabled = !working) { onRestore() }.padding(space.sm),
            )
        }
        Spacer(Modifier.height(space.sm))
    }
}

/** "Start free trial" when an intro offer is live (artist's 3 months), else the explicit price. */
private fun ctaTitle(product: SubscriptionProduct?): String = when {
    product == null -> "Subscribe"
    !product.introOffer.isNullOrEmpty() -> "Start free trial"
    else -> "Subscribe · ${product.displayPrice}/${product.periodLabel}"
}
