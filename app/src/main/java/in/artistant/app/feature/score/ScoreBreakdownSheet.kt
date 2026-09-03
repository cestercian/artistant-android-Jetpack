package `in`.artistant.app.feature.score

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.designsystem.component.Banner
import `in`.artistant.app.designsystem.component.BannerTone
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.Meter
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.component.SheetScaffold
import `in`.artistant.app.designsystem.component.UnavailableRow
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import java.util.Locale

/**
 * The client-facing Bookability sheet — design screen 99 and its healthy twin.
 *
 * **Renders only what it can back.** The artist row itself carries a `score`, a
 * `total_gigs` and an `on_time_rate`, and those arrive with the profile; the
 * five weighted `metric_*` columns arrive in a second read that can fail on its
 * own. When it does, this sheet keeps the number (it is the server's, and it did
 * not change) and itemises only the factors the row can vouch for — the rest go
 * under NOT LOADED with a dash and no bar, because an empty bar in a list of
 * full ones reads as a factor that scored zero.
 *
 * That is the difference between screen 99 and an error screen, and it is why
 * [breakdownFailed] is threaded in rather than inferred from a null
 * [breakdown]: null is also what "the fetch has not returned yet" looks like,
 * and claiming a partial view during the first frame of every profile would
 * make the honest state meaningless.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreBreakdownSheet(
    artist: Artist,
    breakdown: ScoreBreakdown?,
    breakdownFailed: Boolean,
    reviews: List<Review>,
    reviewsFailed: Boolean,
    onRetry: () -> Unit,
    onSeeBookability: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val tier = breakdown?.tier ?: ScoreBands.tier(artist.score, artist.gigs)
    val isNew = tier == ScoreTier.New

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = colors.surface,
    ) {
        SheetScaffold {
            Row(
                Modifier.fillMaxWidth().padding(bottom = space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Score details",
                    style = AppTheme.type.sectionTitle,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconCircle(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    onClick = onDismiss,
                    size = dimens.component.iconCircleSm,
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScoreDisc(
                    score = if (isNew) null else artist.score,
                    size = dimens.size.ringMd,
                )
                Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
                    Text(
                        artist.name,
                        style = AppTheme.type.rowTitle,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        // The tier, not a percentile: nothing in the schema
                        // ranks an artist against their city, and "top 18% in
                        // Bengaluru" would be a number this client invented.
                        if (isNew) "New on Artistant" else "${tier.label} tier",
                        style = AppTheme.type.subtitle,
                        color = colors.ink4,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(space.lg))

            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(space.md),
            ) {
                if (breakdownFailed) {
                    Banner(
                        title = "Couldn't itemise this score",
                        detail = "Showing what this artist's record can back on its " +
                            "own. The rest needs a fetch we couldn't complete.",
                        tone = BannerTone.Attention,
                    )
                }
                if (isNew) {
                    Text(
                        "Under ${ScoreBands.MIN_GIGS_FOR_RANK} completed gigs, so " +
                            "there is no ranked score yet. That is not a low score — " +
                            "it is no score.",
                        style = AppTheme.type.body,
                        color = colors.ink2,
                    )
                }

                if (breakdown != null) {
                    EyebrowLabel("What moves it")
                    ScoreFactors.of(breakdown).forEach { factor ->
                        Meter(
                            label = factor.label,
                            fraction = factor.fraction,
                            value = factor.display,
                        )
                    }
                } else {
                    // Degraded (99): only the facts the artist row itself carries.
                    EyebrowLabel("What we can show")
                    Meter(
                        label = "Shows completed",
                        // No denominator exists for a career total, so the bar is
                        // full and the figure carries the meaning.
                        fraction = if (artist.gigs > 0) 1f else 0f,
                        value = "${artist.gigs}",
                    )
                    if (artist.onTime > 0) {
                        Meter(
                            label = ScoreFactors.SHOW_UP,
                            fraction = artist.onTime.coerceIn(0, PERCENT) / PERCENT.toFloat(),
                            value = "${artist.onTime}%",
                        )
                    }
                    reviewAverage(reviews)?.let { average ->
                        Meter(
                            label = ScoreFactors.REVIEWS,
                            fraction = (average / MAX_RATING).toFloat(),
                            value = String.format(Locale.US, "%.1f / %d", average, MAX_RATING),
                        )
                    }
                    Spacer(Modifier.height(space.xs))
                    EyebrowLabel("Not loaded")
                    ScoreFactors.labels
                        .filterNot { it == ScoreFactors.SHOW_UP && artist.onTime > 0 }
                        .filterNot { it == ScoreFactors.REVIEWS && reviewAverage(reviews) != null }
                        .forEach { UnavailableRow(it) }
                }

                if (reviewsFailed) {
                    Text(
                        "Reviews couldn't be loaded, so they aren't counted in what " +
                            "you see here.",
                        style = AppTheme.type.caption,
                        color = colors.ink4,
                    )
                }
                Text(
                    if (breakdown != null) {
                        "Computed from completed bookings, verified reviews and " +
                            "response times on Artistant. Nothing here can be bought."
                    } else {
                        "The total is still the server's number. We just can't " +
                            "itemise all of it right now."
                    },
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                )
                Spacer(Modifier.height(space.sm))
                if (breakdownFailed) {
                    PrimaryButton(text = "Retry", onClick = onRetry, fullWidth = true)
                    Spacer(Modifier.height(space.sm))
                    SecondaryButton(
                        text = "See the full breakdown",
                        onClick = onSeeBookability,
                        fullWidth = true,
                    )
                } else {
                    PrimaryButton(
                        text = "See the full breakdown",
                        onClick = onSeeBookability,
                        fullWidth = true,
                    )
                }
            }
        }
    }
}

/**
 * The accent disc a score rides in on the light design — a filled circle with
 * the number in it, not a progress ring.
 *
 * The ring survives on the self-facing explainer, where the arc is the subject.
 * Here the number is one fact among several and a ring beside a name reads as a
 * loading spinner that stopped.
 */
@Composable
internal fun ScoreDisc(score: Int?, size: Dp, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (score == null) colors.surface2 else colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = score?.toString() ?: "New",
            style = AppTheme.type.monoNumber,
            color = if (score == null) colors.ink3 else colors.onAccent,
            maxLines = 1,
        )
    }
}

private const val PERCENT = 100
private const val MAX_RATING = 5

private fun reviewAverage(reviews: List<Review>): Double? =
    if (reviews.isEmpty()) null else reviews.sumOf { it.rating }.toDouble() / reviews.size
