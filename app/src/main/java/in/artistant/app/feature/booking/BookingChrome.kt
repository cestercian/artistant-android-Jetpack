package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * The shared booking-funnel top bar — back chevron + inline title. Approximates
 * iOS's native inline nav (system back + glass scroll-edge) with a plain hairline
 * header, since Android has no glass analogue. ponytail: reused across
 * Book/Checkout/RequestQuote so their chrome can't drift.
 */
@Composable
fun FunnelHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = space.md, vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Box(
            Modifier
                .size(AppTheme.dimens.size.rowMin)
                .clip(CircleShape)
                .clickable(onClick = onBack),
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
        )
    }
}

/**
 * Bottom CTA scrim (iOS `CTAScrim`) — a top hairline + elevated background so the
 * pinned action reads above the scrolling content.
 */
@Composable
fun CtaBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth()) {
        HRule()
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(AppTheme.dimens.space.lg),
        ) { content() }
    }
}

/** Small-caps section label (iOS `sectionTitle`) shared across the funnel screens. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppTheme.type.caption,
        color = AppTheme.colors.ink3,
        modifier = modifier,
    )
}
