package `in`.artistant.app.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.platform.upload.UploadBannerState

/**
 * Compact banner surfacing the [UploadBannerState] at the top of ArtistHome (iOS
 * `Components/UploadProgressBanner.swift`). Three states:
 *   - Idle → collapses to nothing (zero chrome).
 *   - Uploading → "Saving X of Y…" + a thin progress bar + spinner.
 *   - Failed → "N uploads stalled" + a Retry-all affordance.
 * Failed takes priority: a stalled batch parks the runner, so surfacing the failure
 * tells the artist to act rather than watching a frozen "Saving…" forever.
 *
 * Stateless — the ViewModel owns the [UploadBannerState] flow so this stays dumb and
 * previewable. [onRetryAll] clears the failed batch (see `UploadQueue.clearFinished`).
 */
@Composable
fun UploadProgressBanner(
    state: UploadBannerState,
    onRetryAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    AnimatedVisibility(
        visible = !state.isIdle,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        if (state.failed > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.hot.copy(alpha = 0.08f))
                    .clickable { onRetryAll() }
                    .padding(horizontal = space.lg, vertical = space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${state.failed} upload${if (state.failed == 1) "" else "s"} stalled",
                        style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                    )
                    Text("Tap to clear and re-add from your profile", style = AppTheme.type.caption, color = colors.ink3)
                }
                Text(
                    "Retry all",
                    style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.hot,
                )
            }
        } else {
            val done = state.completed.coerceAtMost(state.total)
            val title = if (done == 0) "Saving your profile media…" else "Saving $done of ${state.total}…"
            val progress = if (state.total == 0) 0f else done.toFloat() / state.total.toFloat()
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.brand.copy(alpha = 0.08f))
                    .padding(horizontal = space.lg, vertical = space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(space.md),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.brand,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                    )
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.brand,
                        trackColor = colors.bgSoft,
                    )
                }
            }
        }
    }
}
