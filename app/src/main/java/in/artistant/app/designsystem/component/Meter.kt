package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A labelled progress bar: name on the left, the arithmetic on the right, a
 * 6dp accent bar under both (screens 16 / 80 / 99).
 *
 * The Bookability screens are the design's argument that the score is auditable,
 * and this is the row that carries it — so the trailing text is a FRACTION
 * ("28 / 30"), not a percentage. A percentage restates the bar; the fraction
 * says how many of the available points this factor earned, which is the only
 * form in which four rows add up to a number the reader can check.
 *
 * [fraction] null is the *unavailable* row, and it is a different thing from
 * zero: the track renders empty on [AppColors.placeholder] rather than
 * [AppColors.hairline], the label steps back to `ink3`, and the value reads "—".
 * Screen 80 is nothing but four of those, and the headline above it exists
 * because a zeroed bar would be read as a penalty.
 */
@Composable
fun Meter(
    label: String,
    fraction: Float?,
    modifier: Modifier = Modifier,
    value: String? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val available = fraction != null
    val shown = value ?: EM_DASH

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = if (available) "$label: $shown" else "$label: unavailable"
            },
        verticalArrangement = Arrangement.spacedBy(dimens.space.sm),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = AppTheme.type.rowTitle,
                color = if (available) colors.ink else colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = shown,
                // Mono, because these are the numbers the reader is being invited
                // to add up and a proportional set does not column-align.
                style = AppTheme.type.monoPill.copy(fontWeight = FontWeight.Medium),
                color = if (available) colors.ink2 else colors.ink3,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(dimens.size.dot)
                .clip(RoundedCornerShape(dimens.radii.sm))
                .background(if (available) colors.hairline else colors.placeholder),
        ) {
            if (fraction != null && fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(colors.accent),
                )
            }
        }
    }
}

/**
 * The row shape screen 99 uses under "NOT LOADED": a name and a dash on a quiet
 * fill, with no bar at all.
 *
 * A [Meter] with a null fraction still draws an empty track, which on a list of
 * three reads as three factors scoring zero. These are factors the client could
 * not FETCH, so they get no track to misread.
 */
@Composable
fun UnavailableRow(label: String, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(colors.surface3)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.md)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label: not loaded"
            },
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = AppTheme.type.body, color = colors.ink3, maxLines = 1)
        Text(EM_DASH, style = AppTheme.type.monoPill, color = colors.ink3)
    }
}

/** The glyph an unavailable figure renders as. Not a hyphen, not a zero. */
internal const val EM_DASH = "—"

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
private fun MeterPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.lg),
        ) {
            Meter("Showed up on time", fraction = 0.93f, value = "28 / 30")
            Meter("Repeat bookings", fraction = 0.65f, value = "13 / 20")
            Meter("Reply speed", fraction = null)
            UnavailableRow("Host reviews")
        }
    }
}
