package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * A section title with an optional trailing action — "Available Sat night" /
 * "See all" (screen 02), "Live clips" / "View all" (screen 04).
 *
 * The action is `accentInk`, not `accent`. Lime as TEXT on off-white measures
 * about 1.3:1; the leaf green is the same signal at 5.4:1. This is the single
 * most-repeated place that distinction shows up, which is why it is baked into
 * the component instead of left to each call site.
 *
 * Baseline-aligned, not centre-aligned: the title is 17 and the action 13.5, and
 * centring two different sizes leaves the smaller one visibly floating.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = AppTheme.type.sectionTitle,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accentInk,
                maxLines = 1,
                modifier = Modifier
                    // The label stays 13.5; the tap node grows to the 44dp floor
                    // around it. `wrapContentSize` re-centres the word inside the
                    // grown box so it does not sit in a corner of its own target.
                    .sizeIn(minWidth = dimens.size.rowMin, minHeight = dimens.size.rowMin)
                    .clip(RoundedCornerShape(dimens.radii.sm))
                    .clickable(role = Role.Button, onClick = onAction)
                    .wrapContentSize()
                    .padding(horizontal = dimens.space.sm),
            )
        }
    }
}

/**
 * The eyebrow above a block: mono, small, wide-tracked, uppercased here so no
 * call site has to remember to.
 *
 * The design uses this where a section is a LABEL rather than a headline —
 * "QUOTE" over a quote card, "NOT ARRIVING?" over the OTP help copy. It is a
 * different job from [SectionHeader], which names a list you can see.
 */
@Composable
fun EyebrowLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = AppTheme.colors.ink3,
) {
    Text(
        text = text.uppercase(),
        style = AppTheme.type.monoLabel,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The label that divides a list into runs — "TODAY" / "EARLIER" on screen 123,
 * "ALSO FIXED" on 137.
 *
 * Not [EyebrowLabel], and the difference is in the markup rather than in taste:
 * the design sets an eyebrow in JetBrains Mono and a group divider in the SANS
 * at 12.5/700 with a hair of tracking. The two do different jobs — an eyebrow
 * names the block under it, a group label says where you are in a list you are
 * already reading — and setting a run of them in mono makes a scrolling list
 * look like a log file.
 */
@Composable
fun GroupLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = AppTheme.colors.ink3,
) {
    Text(
        text = text.uppercase(),
        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun SectionHeaderPreview() {
    ArtistantTheme {
        Column(
            Modifier.padding(AppTheme.dimens.component.gutter),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.lg),
        ) {
            SectionHeader("Available Sat night", actionLabel = "See all", onAction = {})
            SectionHeader("Packages")
            EyebrowLabel("Quote", color = AppTheme.colors.accentInk)
        }
    }
}
