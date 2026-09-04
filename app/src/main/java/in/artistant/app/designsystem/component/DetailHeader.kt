package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A pushed screen's header when the title is a RECORD rather than a place —
 * "Booking #AR-40712 / Confirmed · Sat 12 Oct" (screens 18 / 78 / 83 / 84 / 95 /
 * 96 / 97 / 117).
 *
 * The difference from [BackHeader] is the alignment, and it is not decoration.
 * [BackHeader] centres its title because that title names a destination ("Artist
 * profile") and a centred label reads as the bar's own caption. This one carries
 * an identifier plus a status line that changes on every one of these screens —
 * a two-line block that is longer than the bar's centre can hold, and whose
 * second line has to start where the first does or the pair stops reading as one
 * statement. So it sets left, ragged right, and the trailing slot reserves the
 * back circle's width whether or not it has a control in it.
 *
 * The circles are [Components.iconCircleSm] (40) rather than the 42 [BackHeader]
 * takes: with two lines of text between them the taller pair pushed the block off
 * the design's 56dp bar.
 */
@Composable
fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backLabel: String = "Back",
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = dimens.size.controlMin),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        IconCircle(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = backLabel,
            onClick = onBack,
            size = dimens.component.iconCircleSm,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        // The mirror of the back circle. Without it a title with no trailing
        // control sits half a circle to the right of where its subtitle starts.
        if (trailing != null) {
            Box(contentAlignment = Alignment.Center) { trailing() }
        } else {
            Spacer(Modifier.size(dimens.component.iconCircleSm))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun DetailHeaderPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.lg),
        ) {
            DetailHeader(
                title = "Booking #AR-40712",
                subtitle = "Confirmed · Sat 12 Oct",
                onBack = {},
                trailing = {
                    IconCircle(
                        icon = Icons.AutoMirrored.Outlined.Message,
                        contentDescription = "Message",
                        onClick = {},
                        size = AppTheme.dimens.component.iconCircleSm,
                    )
                },
            )
            DetailHeader(title = "Cancel booking", subtitle = "Step 1 of 2", onBack = {})
        }
    }
}
