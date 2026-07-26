package `in`.artistant.app.feature.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.core.config.AppEnvironment
import androidx.compose.foundation.shape.RoundedCornerShape
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.designsystem.theme.AppTheme
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * ₹99/mo subscription paywall — port of iOS `PaywallView`.
 * Purchase/restore call Play Billing when [AppEnvironment.subscriptionsEnabled].
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
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isArtist = state.isArtist

    LaunchedEffect(role) { viewModel.bindRole(role) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.lg, vertical = space.lg),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.ink3)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.xl),
            verticalArrangement = Arrangement.spacedBy(space.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                Text(
                    if (isArtist) "ARTIST MEMBERSHIP" else "CLIENT MEMBERSHIP",
                    style = AppTheme.type.caption,
                    color = colors.ink3,
                )
                Row {
                    Text(
                        if (isArtist) "Stay " else "Keep ",
                        style = AppTheme.type.displaySub,
                        color = colors.ink,
                    )
                    Text(
                        if (isArtist) "listed" else "booking",
                        style = AppTheme.type.displaySub.copy(fontStyle = FontStyle.Italic),
                        color = colors.brand,
                    )
                    Text(".", style = AppTheme.type.displaySub, color = colors.ink)
                }
                Text(
                    if (isArtist) {
                        "Keep your profile discoverable and keep receiving client leads."
                    } else {
                        "Keep matching with artists after your free intro."
                    },
                    style = AppTheme.type.callout,
                    color = colors.ink3,
                )
            }

            val perks = if (isArtist) {
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
                perks.forEach { perk ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(space.md),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brand)
                        Text(perk, style = AppTheme.type.body, color = colors.ink)
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.bgCard, RoundedCornerShape(AppTheme.dimens.radii.md))
                    .padding(space.lg),
                verticalArrangement = Arrangement.spacedBy(space.sm),
            ) {
                if (state.productPrice != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(state.productPrice!!, style = AppTheme.type.title, color = colors.ink)
                        Text(" / month", style = AppTheme.type.callout, color = colors.ink3)
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
                    TextButton(onClick = { uriHandler.openUri(AppEnvironment.termsOfServiceUrl) }) {
                        Text("Terms", style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold))
                    }
                    Text("·", color = colors.ink3)
                    TextButton(onClick = { uriHandler.openUri(AppEnvironment.privacyPolicyUrl) }) {
                        Text("Privacy", style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = space.xl, vertical = space.lg),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            HRule()
            Spacer(Modifier.height(space.md))
            state.error?.let { msg ->
                Text(msg, style = AppTheme.type.footnote, color = colors.warm)
            }
            PrimaryButton(
                text = if (state.productPrice != null) "Subscribe · ${state.productPrice}/month" else "Subscribe",
                onClick = { viewModel.subscribe(context.findActivity(), onComplete) },
                fullWidth = true,
                enabled = !state.working && AppEnvironment.subscriptionsEnabled,
            )
            TextButton(
                onClick = viewModel::restore,
                enabled = !state.working && AppEnvironment.subscriptionsEnabled,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text("Restore purchases", style = AppTheme.type.caption, color = colors.ink2)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
