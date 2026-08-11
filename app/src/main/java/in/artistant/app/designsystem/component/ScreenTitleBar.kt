package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.designsystem.theme.AppTheme

/**
 * A tab root's title band — a centred title in a bar the height of a row.
 *
 * The iOS app gets this for free: its tab roots sit in a navigation stack and
 * declare an inline navigation title, so the OS draws a 44pt bar under the
 * status bar with the screen's name centred in it. Compose has no equivalent
 * ambient chrome, so the Android tab roots were starting straight at their first
 * piece of content and reading as one line of copy shorter than their iOS
 * counterparts — the screen never said its own name.
 *
 * Deliberately NOT a Material `TopAppBar`: that brings its own container colour,
 * elevation-on-scroll behaviour and title alignment defaults, all of which are
 * Material conventions rather than this app's. What is wanted is the geometry
 * (44dp tall, title optically centred against the whole bar) and nothing else,
 * so the background stays transparent and whatever the screen or the scaffold
 * paints shows through — which is what keeps the ambient role wash continuous
 * behind the title on Profile.
 *
 * Height comes from `size.rowMin`, the same 44dp tap-target floor the rest of the
 * app uses, because that IS the iOS bar height — the two numbers agreeing is not
 * a coincidence worth hiding behind a second token.
 *
 * Only for tab ROOTS. A pushed screen that needs a back control uses
 * `FunnelHeader`, which centres its title the same way but reserves the leading
 * edge for the button.
 */
@Composable
fun ScreenTitleBar(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(AppTheme.dimens.size.rowMin),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = AppTheme.type.headline,
            color = AppTheme.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
