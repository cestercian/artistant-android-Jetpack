package `in`.artistant.app.domain.sample

import `in`.artistant.app.data.model.Sample

/**
 * The decisions behind playing an artist's audio samples, kept out of the
 * player and the Composables so they can be covered.
 *
 * The player itself (ExoPlayer) is untestable here by construction — it needs a
 * `Context`, a real decoder and a main looper. What is worth testing is not
 * "does audio come out" but the surrounding logic that gets it wrong quietly:
 * what a tap means given what is already playing, which duration to trust, and
 * how far along the bar should be. Those live here as free functions over plain
 * data and the controller becomes a shell that obeys them.
 */

/**
 * What is playing right now, if anything.
 *
 * One sample at a time, deliberately. Two clips of the same artist overlapping
 * is never what anyone wanted, and modelling it as "the current one" rather than
 * a per-row `isPlaying` makes the exclusivity structural instead of something
 * every call site has to remember to enforce.
 */
data class SamplePlayback(
    /** Id of the sample the player is loaded with, or null when idle. */
    val sampleId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    /**
     * Duration as the *player* measured it. Zero until the media is prepared —
     * see [sampleDurationLabel] for why this is preferred over the stored one.
     */
    val durationMs: Long = 0L,
    val isBuffering: Boolean = false,
    /** Id of the sample that failed, so exactly one row can show the error. */
    val errorSampleId: String? = null,
) {
    fun isActive(sampleId: String): Boolean = this.sampleId == sampleId

    fun isPlaying(sampleId: String): Boolean = isActive(sampleId) && isPlaying
}

/** What tapping a sample row should do, given what is already loaded. */
sealed interface SampleTap {
    /** Load and play a different sample (or the first one). */
    data class Start(val sampleId: String, val url: String) : SampleTap

    /** The tapped sample is the one playing — pause it. */
    data object Pause : SampleTap

    /** The tapped sample is loaded but paused — resume without reloading. */
    data object Resume : SampleTap

    /**
     * The sample has no audio behind it.
     *
     * Real rather than defensive: `samples.audio_url` is nullable, and rows
     * predating the upload path (or whose upload never drained) carry a title
     * with no file. Those must render unplayable rather than showing a play
     * button that does nothing when tapped.
     */
    data object Unplayable : SampleTap
}

/**
 * Resolve a tap.
 *
 * Re-tapping the active-but-paused sample resumes rather than restarting: the
 * player still holds the prepared media and the position, and reloading would
 * throw away both and jump the listener back to zero.
 */
fun sampleTapAction(sample: Sample, current: SamplePlayback): SampleTap {
    val url = sample.audioUrl?.takeIf { it.isNotBlank() } ?: return SampleTap.Unplayable
    return when {
        !current.isActive(sample.id) -> SampleTap.Start(sample.id, url)
        current.isPlaying -> SampleTap.Pause
        else -> SampleTap.Resume
    }
}

/** Whether a row can be played at all — drives the disabled state of its button. */
fun sampleIsPlayable(sample: Sample): Boolean = !sample.audioUrl.isNullOrBlank()

/**
 * `m:ss`, the shape the rest of the app uses for clip lengths.
 *
 * Negative and unknown values clamp to zero rather than rendering `-1:-1`:
 * ExoPlayer reports `C.TIME_UNSET` (a large negative) for media it has not
 * prepared yet, and that value reaches here on the first frame after a tap.
 */
fun formatPlaybackClock(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}

/**
 * Fraction of the clip elapsed, 0f..1f.
 *
 * Guards the unprepared case explicitly: duration is 0 between the tap and the
 * player reporting a length, and `position / 0` is the difference between a
 * progress bar and a crash.
 */
fun playbackProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * The duration to show for a row.
 *
 * The player's measurement wins whenever it has one. The stored `duration_label`
 * is only as good as whatever measured it at upload: this app now probes the
 * staged file, but every clip it published before that recorded zero seconds, so
 * the server label still reads "0:00" for plenty of rows that are minutes long.
 * Once a row is playing we have a real number and should use it; rows that have
 * never been played fall back to the stored label, and a blank/zero label renders
 * as nothing rather than as a confident lie about the length.
 */
fun sampleDurationLabel(sample: Sample, playback: SamplePlayback): String? {
    if (playback.isActive(sample.id) && playback.durationMs > 0L) {
        return formatPlaybackClock(playback.durationMs)
    }
    return sample.duration.takeIf { it.isNotBlank() && it != "0:00" }
}

/**
 * Whether the player is loaded with a sample that is no longer in the list.
 *
 * Deleting the playing clip in the EPK removes its row — and with it the only
 * pause control on screen — while the audio carries on. So the player follows
 * the list, and this is the rule it follows: something is loaded, and nothing in
 * the list is it.
 *
 * Idle is not orphaned. A null id means nothing is loaded, which is true of an
 * empty list too, and stopping a player that is already stopped on every list
 * change would fight the ticker for no reason.
 */
fun playbackIsOrphaned(activeSampleId: String?, sampleIds: List<String>): Boolean =
    activeSampleId != null && activeSampleId !in sampleIds
