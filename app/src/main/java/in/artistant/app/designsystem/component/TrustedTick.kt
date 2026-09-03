package `in`.artistant.app.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.ArtistantTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier

/**
 * The lime check that rides beside an artist's name on Discover, in search
 * results and in the saved list (screens 02 / 03 / 14 / 32).
 *
 * **It is a Bookability claim, not a KYC badge.** The design draws a "verified"
 * tick, but the shared schema has no verified flag on `artists` — the only
 * standing the backend actually publishes is the score, and its bands
 * ([ScoreBands]) are what the whole product is built on. So the tick means
 * *Trusted or better*: a real score of 75+ over at least five completed gigs.
 * [isTrusted] is the single predicate every surface asks, so no screen can
 * invent a softer rule for the same mark.
 *
 * Drawn as a filled disc with an inked glyph rather than as Material's `Verified`
 * vector, which carries its check as a HOLE in the shape: over a cover photo that
 * hole shows the photograph through the tick, so the mark's meaning would depend
 * on what the artist happened to upload.
 */
@Composable
fun TrustedTick(
    modifier: Modifier = Modifier,
    size: Dp = AppTheme.dimens.size.iconMd,
) {
    val colors = AppTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = "Trusted artist",
            tint = colors.onAccent,
            modifier = Modifier.size(size * GLYPH_FRACTION),
        )
    }
}

/** The glyph's share of the disc — enough ring to read as a badge, not a button. */
private const val GLYPH_FRACTION = 0.72f

/**
 * Does this artist earn a [TrustedTick]?
 *
 * Takes the two raw fields rather than an `Artist` so the rule can be asked of a
 * tile projection, a full profile or a test row without any of them having to
 * agree on a model first — and so it stays a pure function the JVM suite can pin.
 */
fun isTrusted(score: Int, gigs: Int): Boolean =
    when (ScoreBands.tier(score, gigs)) {
        ScoreTier.Trusted, ScoreTier.Elite -> true
        ScoreTier.New, ScoreTier.Rising -> false
    }

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF6)
@Composable
private fun TrustedTickPreview() {
    ArtistantTheme {
        Row(Modifier.padding(AppTheme.dimens.component.gutter)) {
            TrustedTick()
        }
    }
}
