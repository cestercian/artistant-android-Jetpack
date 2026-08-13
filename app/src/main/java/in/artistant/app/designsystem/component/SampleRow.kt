package `in`.artistant.app.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.sample.SamplePlayback
import `in`.artistant.app.domain.sample.formatPlaybackClock
import `in`.artistant.app.domain.sample.playbackProgress
import `in`.artistant.app.domain.sample.sampleDurationLabel
import `in`.artistant.app.domain.sample.sampleIsPlayable

/**
 * One playable audio sample.
 *
 * Shared by the EPK (where the artist manages their own clips) and the artist
 * profile (where a client listens to them), because the two were drifting into
 * different renderings of the same row and only one of them could play anything.
 *
 * The progress bar only exists for the active row. A permanent zero-width bar
 * under every sample reads as chrome; a bar that appears when you press play
 * reads as the thing you pressed responding to you.
 */
@Composable
fun SampleRow(
    sample: Sample,
    playback: SamplePlayback,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val playable = sampleIsPlayable(sample)
    val active = playback.isActive(sample.id)
    val playing = playback.isPlaying(sample.id)
    val failed = playback.errorSampleId == sample.id
    val duration = sampleDurationLabel(sample, playback)

    Column(modifier.fillMaxWidth().padding(vertical = space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (playable) {
                PlayControl(
                    playing = playing,
                    buffering = active && playback.isBuffering,
                    title = sample.title,
                    onTap = onTap,
                )
                Spacer(Modifier.width(space.md))
            }

            Column(Modifier.weight(1f)) {
                Text(
                    sample.title,
                    style = AppTheme.type.callout,
                    // The playing row takes the accent; everything else stays
                    // ink, so "which one is playing" is answered by the list
                    // rather than only by the icon.
                    color = if (playing) colors.brand else colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SampleMeta(
                    active = active,
                    failed = failed,
                    playable = playable,
                    positionMs = playback.positionMs,
                    duration = duration,
                )
            }

            trailing?.invoke()
        }

        if (active && !failed) {
            Spacer(Modifier.height(space.xs))
            PlaybackBar(
                progress = playbackProgress(playback.positionMs, playback.durationMs),
            )
        }
    }
}

/**
 * The line under the title: elapsed/total while active, the clip length
 * otherwise, and the failure in place of both when the media would not load.
 */
@Composable
private fun SampleMeta(
    active: Boolean,
    failed: Boolean,
    playable: Boolean,
    positionMs: Long,
    duration: String?,
) {
    val colors = AppTheme.colors
    when {
        failed -> Text(
            "Couldn't play this clip",
            style = AppTheme.type.footnote,
            color = colors.hot,
        )

        active -> Text(
            // Total is omitted until the player has measured one, rather than
            // showing "0:07 / 0:00" for the first frames after a tap.
            if (duration != null) "${formatPlaybackClock(positionMs)} / $duration"
            else formatPlaybackClock(positionMs),
            style = AppTheme.type.monoSmall,
            color = colors.ink3,
        )

        // Said plainly rather than left as a row that just doesn't respond.
        !playable -> Text(
            "No audio uploaded",
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )

        duration != null -> Text(duration, style = AppTheme.type.monoSmall, color = colors.ink3)
    }
}

@Composable
private fun PlayControl(
    playing: Boolean,
    buffering: Boolean,
    title: String,
    onTap: () -> Unit,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .size(36.dp)
            .pressScale(interaction)
            .clip(CircleShape)
            .background(if (playing) colors.brand else colors.ink.copy(alpha = 0.08f))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = if (playing) colors.bg else colors.ink),
                onClick = onTap,
            )
            // Named for a screen reader, since the icon alone says "play" but
            // not "play what".
            .semantics {
                contentDescription = if (playing) "Pause $title" else "Play $title"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (buffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = if (playing) colors.bg else colors.ink2,
            )
        } else {
            Icon(
                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (playing) colors.bg else colors.ink,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Hairline progress, animated so a 200ms poll interval doesn't read as the bar
 * stepping. Two stacked rules rather than a Material slider: this is a readout,
 * not a control — the app has no scrub gesture and a slider would invite one.
 */
@Composable
private fun PlaybackBar(progress: Float) {
    val colors = AppTheme.colors
    val animated by animateFloatAsState(targetValue = progress, label = "sampleProgress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.ink.copy(alpha = 0.12f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(2.dp)
                .background(colors.brand),
        )
    }
}
