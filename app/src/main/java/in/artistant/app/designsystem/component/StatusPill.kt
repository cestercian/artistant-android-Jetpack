package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * What a [StatusPill] is saying. The dot colour follows; the fill does not.
 *
 * Note that [Live] does NOT paint a lime dot. A `#d6f84b` disc measures about
 * 1.3:1 against `surface2` — at 7dp that is not a signal, it is a smudge. The
 * dot takes `accentInk`, the same hue at a legible weight, which is the same
 * substitution `SectionHeader`'s "See all" makes and for the same reason.
 */
enum class StatusTone { Live, Pending, Failed, Done, Neutral }

/**
 * A dot and a mono label: "● AVAILABLE FRI", "● CONFIRMED", "● AWAITING".
 *
 * Mono because these are machine states rather than prose — the same reason the
 * design sets prices and scores in JetBrains Mono. Uppercased here so no call
 * site has to remember to, and tracked wide by [AppType.monoPill] so the caps do
 * not read as a serial number.
 *
 * The fill is quiet on purpose. The status is carried by the dot and the word;
 * a coloured capsule for every state turns a list of bookings into a row of
 * traffic lights, which is what the design's own note warns against ("Confirmed,
 * pending and played each get a different affordance, not a badge").
 */
@Composable
fun StatusPill(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    val dot = when (tone) {
        StatusTone.Live -> colors.accentInk
        StatusTone.Pending -> colors.warm
        StatusTone.Failed -> colors.danger
        StatusTone.Done -> colors.accentDeep
        StatusTone.Neutral -> colors.ink4
    }
    val fill: Color
    val ink: Color
    if (onMedia) {
        fill = colors.glassScrim
        ink = colors.onDark
    } else {
        fill = colors.surface2
        ink = colors.ink2
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(fill)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.xs + dimens.space.xs / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm - dimens.space.xs / 2),
    ) {
        Box(
            Modifier
                .size(dimens.component.statusDot)
                .clip(CircleShape)
                .background(if (onMedia) colors.accent else dot),
        )
        Text(
            text = label.uppercase(),
            style = AppTheme.type.monoPill,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun StatusPillPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
        ) {
            StatusPill("Available Fri", StatusTone.Live)
            StatusPill("Awaiting artist", StatusTone.Pending)
            StatusPill("Declined", StatusTone.Failed)
            StatusPill("Played 6 Sep", StatusTone.Done)
        }
    }
}
