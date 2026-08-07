package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * Tone of an [InlineBanner] — which is really a statement about *urgency*, and
 * the fill follows from it.
 *
 * [Attention] and [Failure] sit on the card surface with a coloured dot, because
 * they are the page telling you something about itself. [Promotion] is filled in
 * the brand accent because it is the page asking for something — and the app
 * reserves a solid accent fill for exactly that, so a filled banner is
 * self-evidently an offer rather than a warning.
 */
enum class BannerTone { Attention, Failure, Promotion }

/**
 * A full-width notice that lives inline in a scroll, not over it.
 *
 * The alternative — a snackbar or a dialog — is wrong for everything this is
 * used for: a profile that isn't finished, a refresh that failed, a subscription
 * that hasn't started. None of those are momentary, and all of them should still
 * be true when the user comes back to the screen tomorrow. So this stays in the
 * flow and stays until the underlying fact changes.
 *
 * Flat fills only, no gradient or scrim: at this size a gradient is invisible
 * and still costs a shader.
 *
 * @param onClick makes the whole banner the target (press-scale + ripple).
 * @param actionLabel renders a trailing text action INSTEAD of the chevron —
 *   for the retry case, where the banner itself isn't a destination.
 */
@Composable
fun InlineBanner(
    title: String,
    tone: BannerTone,
    modifier: Modifier = Modifier,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val dashboard = AppTheme.dimens.dashboard
    val interaction = remember { MutableInteractionSource() }

    val promotion = tone == BannerTone.Promotion
    val fill = if (promotion) colors.brand else colors.bgCard
    val titleColor = if (promotion) colors.brandInk else colors.ink
    val detailColor = if (promotion) colors.brandInk.copy(alpha = DETAIL_ON_BRAND) else colors.ink3
    val dot = when (tone) {
        BannerTone.Attention -> colors.warm
        BannerTone.Failure -> colors.hot
        BannerTone.Promotion -> null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(fill)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = ripple(),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = space.lg, vertical = space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = listOfNotNull(title, detail).joinToString(". ")
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        dot?.let {
            Box(
                Modifier
                    .size(dashboard.bannerDot)
                    .clip(CircleShape)
                    .background(it),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.callout,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    style = AppTheme.type.footnote,
                    color = detailColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            actionLabel != null && onAction != null -> Text(
                actionLabel.uppercase(),
                style = AppTheme.type.caption,
                color = colors.brand,
                modifier = Modifier
                    .clip(RoundedCornerShape(AppTheme.dimens.radii.sm))
                    .clickable(onClick = onAction)
                    .padding(horizontal = space.sm, vertical = space.xs),
            )
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (promotion) colors.brandInk else colors.ink3,
                modifier = Modifier.size(AppTheme.dimens.size.iconMd),
            )
        }
    }
}

/**
 * Supporting copy on a brand fill. Full-strength ink under a bold title reads as
 * two headlines; this is the same step down the ladder that `ink3` gives on a
 * dark surface, expressed as opacity because the ink tokens are tuned for `bg`
 * and would be unreadable on lime or violet.
 */
private const val DETAIL_ON_BRAND = 0.75f
