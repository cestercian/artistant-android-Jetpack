package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme

/**
 * The light sheet: rounded top corners, a grabber, an optional title, and the
 * page gutter applied once.
 *
 * Only the TOP corners round. A sheet is flush with the bottom of the display,
 * so its lower corners have no edge to soften — rounding them would cut two
 * notches out of the fill and show the page through, which reads as a rendering
 * fault rather than as a rounded card.
 *
 * **Not a `ModalBottomSheet`.** This is the sheet's INSIDE. Material's component
 * carries the scrim, the drag gesture, the predictive-back handling and the
 * dismissal contract — behaviour worth having and not worth reimplementing — but
 * it draws its own container and its own drag handle in M3 colours. So the
 * pattern is: `ModalBottomSheet(dragHandle = null, containerColor = …)` wrapping
 * this, which supplies everything visible. A caller that owns its own container
 * (a bottom-anchored panel that never dismisses) can use this alone.
 *
 * The navigation-bar inset is applied here rather than by every caller, because
 * a sheet is the one surface that is always against the bottom edge and always
 * needs it.
 */
@Composable
fun SheetScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    showGrabber: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = dimens.radii.xl,
                    topEnd = dimens.radii.xl,
                ),
            )
            .background(colors.surface)
            .padding(
                start = dimens.component.gutter,
                end = dimens.component.gutter,
                bottom = dimens.space.xl +
                    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        if (showGrabber) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.space.md),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(dimens.component.grabberW)
                        .height(dimens.component.grabberH)
                        .clip(RoundedCornerShape(dimens.radii.sm))
                        .background(colors.hairline),
                )
            }
        }
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = AppTheme.type.displaySmall,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    top = if (showGrabber) dimens.space.sm else dimens.space.xl,
                    bottom = dimens.space.lg,
                ),
            )
        }
        content()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun SheetScaffoldPreview() {
    ArtistantTheme {
        Box(Modifier.padding(top = AppTheme.dimens.space.xxl)) {
            SheetScaffold(title = "Report this conversation") {
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm)) {
                    ListRow("Spam or a scam", onClick = {})
                    ListRow("Asked me to pay off-platform", onClick = {})
                    ListRow("Something else", onClick = {}, showHairline = false)
                    Box(Modifier.size(AppTheme.dimens.space.lg))
                    PrimaryButton("Send report", {}, fullWidth = true)
                }
            }
        }
    }
}
