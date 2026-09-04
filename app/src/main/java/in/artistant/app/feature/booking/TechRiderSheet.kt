package `in`.artistant.app.feature.booking

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * What the act needs on stage — the artist's `tech_rider` rows, read off the
 * already-hydrated [in.artistant.app.data.model.Artist].
 *
 * A sheet rather than a screen because it is a reference: you open it while
 * talking to a venue, read four lines and put it away. It is opened from two
 * places — the confirmed card on the Bookings list and the booking detail's
 * action row — which is why it lives here rather than inside either of them.
 *
 * The empty case is a real state and says which: a rider is optional on the
 * artist's profile, so "nothing listed" is a fact about that artist, not a
 * failure of this sheet. It does not offer a retry, because there is nothing to
 * retry — the rider travels with the artist we already loaded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechRiderSheet(
    artistName: String,
    items: List<String>,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        dragHandle = null,
    ) {
        SheetScaffold(title = "Tech rider") {
            if (items.isEmpty()) {
                Text(
                    "$artistName hasn't listed a tech rider. Ask them in the thread what " +
                        "they need on stage.",
                    style = AppTheme.type.body,
                    color = colors.ink3,
                    modifier = Modifier.padding(bottom = dimens.space.lg),
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.space.md),
                    modifier = Modifier.padding(bottom = dimens.space.lg),
                ) {
                    items.forEach { line ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
                            verticalAlignment = androidx.compose.ui.Alignment.Top,
                        ) {
                            Box(
                                Modifier
                                    .padding(top = dimens.space.sm)
                                    .size(dimens.size.dot)
                                    .clip(CircleShape)
                                    .background(colors.accentInk),
                            )
                            Text(line, style = AppTheme.type.body, color = colors.ink2)
                        }
                    }
                }
            }
        }
    }
}
