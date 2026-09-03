package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A tab ROOT's header: a large left-aligned title, an optional second line, and
 * one action circle on the trailing edge (screens 02 / 10 / 19 / 26).
 *
 * The title is 26/700 with tight tracking — the largest thing on the page, and
 * the reason these screens no longer need a centred navigation bar to say their
 * own name.
 *
 * [subtitle] is not decoration. On Discover it carries the city and the date the
 * prices below are quoted for, which is the whole premise of that screen; if a
 * caller has nothing that specific to say, it should pass null rather than
 * inventing a tagline.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.type.screenTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AppTheme.type.subtitle,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * A PUSHED screen's header: a back circle, a centred 17/700 title, and an
 * optional trailing slot (screens 04 / 12 / 18 / 119).
 *
 * The title is centred against the whole bar, not against the space left over
 * between the controls — so the leading and trailing edges reserve the SAME
 * width even when only one of them has a control in it. An asymmetric
 * reservation centres the title in the bar's remainder, which reads as almost-
 * centred, which is worse than either extreme.
 */
@Composable
fun BackHeader(
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
    ) {
        IconCircle(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = backLabel,
            onClick = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimens.space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // The mirror of the back circle. Without it a title with no trailing
        // control sits half a circle to the right of centre.
        if (trailing != null) trailing() else Spacer(Modifier.width(dimens.size.controlMin))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun HeadersPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.xl),
        ) {
            ScreenHeader(
                title = "Discover",
                subtitle = "Bengaluru · Sat 12 Oct",
                trailing = {
                    IconCircle(Icons.Filled.Notifications, "Notifications", onClick = {}, dot = true)
                },
            )
            Box { BackHeader(title = "Artist profile", onBack = {}) }
            BackHeader(title = "Booking #AR-40712", subtitle = "Confirmed · Sat 12 Oct", onBack = {})
        }
    }
}
