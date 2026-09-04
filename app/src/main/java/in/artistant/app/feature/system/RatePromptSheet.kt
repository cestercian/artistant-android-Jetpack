package `in`.artistant.app.feature.system

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.designsystem.component.ButtonVariant
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * Screen 138 — "Enjoying Artistant?", presented from the client tab scaffold
 * once a review has landed.
 *
 * See [shouldPromptForRating] for when. This only draws it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatePromptHost(viewModel: RatePromptViewModel) {
    val visible by viewModel.visible.collectAsStateWithLifecycle()
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        // A scrim tap is "Not now" — the same answer, and it must close the
        // question for good rather than leaving it to be asked again tomorrow.
        onDismissRequest = viewModel::dismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = AppTheme.colors.surface,
    ) {
        RatePromptSheet(
            onRate = {
                viewModel.rated()
                AppStore.openListing(context)
            },
            onDismiss = viewModel::dismiss,
        )
    }
}

/** The sheet's inside — hoisted so a preview can render it without Hilt. */
@Composable
fun RatePromptSheet(
    onRate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens

    SheetScaffold(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconCircle(
                icon = Icons.Filled.Close,
                contentDescription = "Not now",
                onClick = onDismiss,
                size = dimens.component.iconCircleSm,
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = dimens.space.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The launcher mark, in the app's own hand: a dark squircle with a
            // lime "A" in JetBrains Mono. The one dark object on a light sheet,
            // for the same reason the splash is dark — it is the logo, not a
            // surface.
            Box(
                Modifier
                    .size(dimens.funnel.actThumb)
                    .clip(RoundedCornerShape(dimens.radii.lg))
                    .background(colors.darkest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "A",
                    // `monoHero`, not `monoNumber`: the design sets this "A" at
                    // 30 in a 62 box, and at the 18sp numeral step the mark reads
                    // as a letter that happens to be on a square rather than as
                    // the app's icon.
                    style = AppTheme.type.monoHero,
                    color = colors.accent,
                )
            }

            Text(
                text = "Enjoying Artistant?",
                style = AppTheme.type.displaySmall,
                color = colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = dimens.space.lg),
            )
            Text(
                // The design personalises this ("Your sangeet went well — a rating
                // helps other hosts find acts like The Tilt Collective"). That
                // sentence needs the event type and the act's name at the moment
                // the prompt fires, and the review sheet hands neither out — so
                // the copy states the fact it CAN stand behind: a review just
                // landed, and a rating helps other hosts. Naming an act we have
                // not looked up would be the fabrication the section forbids.
                text = "You just reviewed a gig. A rating helps other hosts find acts " +
                    "like that one.",
                style = AppTheme.type.body,
                color = colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = dimens.space.sm)
                    .widthIn(max = dimens.component.readingMeasure),
            )

            // Outlines, not a control. The rating is chosen in the Play sheet,
            // and five stars that look tappable but only open the store would be
            // a rating the user believes they have already given.
            Row(
                Modifier
                    .padding(top = dimens.space.lg)
                    .semantics { contentDescription = "Five stars, rated in Google Play" },
                horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            ) {
                repeat(STAR_COUNT) {
                    Icon(
                        imageVector = Icons.Outlined.StarBorder,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(dimens.component.reviewStar),
                    )
                }
            }

            PrimaryButton(
                text = "Rate on Google Play",
                onClick = onRate,
                fullWidth = true,
                modifier = Modifier.padding(top = dimens.space.xl),
            )
            PrimaryButton(
                text = "Not now",
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                fullWidth = true,
                modifier = Modifier.padding(top = dimens.space.xs),
            )
            Text(
                text = "Asked once, after a review — never on launch.",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Normal),
                color = colors.ink4,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = dimens.space.sm),
            )
        }
    }
}

private const val STAR_COUNT = 5

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun RatePromptPreview() {
    ArtistantTheme {
        RatePromptSheet(onRate = {}, onDismiss = {})
    }
}
