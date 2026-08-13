package `in`.artistant.app.platform.media

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.domain.sample.SamplePlayback
import `in`.artistant.app.domain.sample.SampleTap
import `in`.artistant.app.domain.sample.sampleTapAction
import kotlinx.coroutines.delay

/**
 * Audio-sample playback for the EPK and the artist profile.
 *
 * ## Why a Composable-scoped player rather than a singleton
 *
 * A sample is something you listen to *on a screen*: leave the screen and the
 * clip is done. Scoping the player to the composition means leaving disposes it,
 * which is exactly the desired behaviour and needs no teardown call that a
 * future screen could forget to make. A singleton would have to be told when to
 * stop by every consumer, and the failure mode of forgetting is an artist's clip
 * still playing over the next screen.
 *
 * ## The three things this gets right that are easy to miss
 *
 * 1. **Backgrounding pauses.** Without the lifecycle observer, navigating away
 *    or locking the phone leaves audio playing from a screen that is gone. This
 *    is not a music app — nothing here justifies background playback, and it has
 *    no notification or lockscreen control to stop it with.
 * 2. **Audio focus is requested.** `handleAudioFocus = true` makes the platform
 *    pause us for a phone call and duck us for a navigation prompt, and stops us
 *    from playing over whatever the user already had going.
 * 3. **Position polling only runs while playing.** A permanent ticker would keep
 *    the composition recomposing at 10Hz forever on a screen where most visits
 *    never press play.
 */
@Composable
fun rememberSamplePlayer(): SamplePlayerHandle {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playback = remember { mutableStateOf(SamplePlayback()) }

    val player = remember {
        buildPlayer(context.applicationContext).also { exo ->
            exo.addListener(playerListener(exo, playback))
        }
    }

    // Release with the composition. Without this the decoder, the audio track
    // and the loading thread all outlive the screen.
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    // Backgrounding stops playback — see the KDoc. ON_STOP rather than ON_PAUSE
    // so a transient overlay (a permission dialog, the share sheet) doesn't cut
    // the clip off mid-listen.
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && player.isPlaying) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Position ticker, alive only while something is actually playing. The
    // listener flips `isPlaying`, which cancels and restarts this effect.
    LaunchedEffect(playback.value.isPlaying) {
        while (playback.value.isPlaying) {
            playback.value = playback.value.copy(
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            )
            delay(POSITION_POLL_MS)
        }
    }

    return remember(player) { SamplePlayerHandle(player, playback) }
}

/** Controls a [rememberSamplePlayer] instance and exposes what it is doing. */
class SamplePlayerHandle internal constructor(
    private val player: ExoPlayer,
    private val playbackState: MutableState<SamplePlayback>,
) {
    val playback: State<SamplePlayback> get() = playbackState

    /**
     * Handle a tap on [sample], doing whatever [sampleTapAction] says it means.
     *
     * The decision is delegated rather than re-derived here so the "resume
     * instead of restart" and "no file behind it" rules stay in one covered
     * place; this method is only the effect.
     */
    fun onTap(sample: Sample) {
        when (val action = sampleTapAction(sample, playbackState.value)) {
            is SampleTap.Start -> start(action.sampleId, action.url)
            SampleTap.Pause -> player.pause()
            SampleTap.Resume -> player.play()
            // Nothing to do — the row renders without a control, so this is only
            // reachable if a caller wires a tap onto an unplayable row.
            SampleTap.Unplayable -> Unit
        }
    }

    private fun start(sampleId: String, url: String) {
        // Clear the previous error so switching away from a failed clip doesn't
        // leave its message showing under a different row.
        playbackState.value = SamplePlayback(
            sampleId = sampleId,
            isBuffering = true,
            errorSampleId = null,
        )
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }
}

private const val POSITION_POLL_MS = 200L

@OptIn(UnstableApi::class)
private fun buildPlayer(context: android.content.Context): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            // Let the platform pause us for calls and duck us for prompts.
            /* handleAudioFocus = */ true,
        )
        // A sample is a listen, not a loop.
        repeatMode = Player.REPEAT_MODE_OFF
    }

private fun playerListener(
    player: ExoPlayer,
    playback: MutableState<SamplePlayback>,
): Player.Listener = object : Player.Listener {

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        playback.value = playback.value.copy(isPlaying = isPlaying)
    }

    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_BUFFERING ->
                playback.value = playback.value.copy(isBuffering = true)

            Player.STATE_READY -> playback.value = playback.value.copy(
                isBuffering = false,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            )

            // Reaching the end returns the row to its resting state rather than
            // leaving a full progress bar and a pause icon on a clip that has
            // stopped. Tapping again replays from the start.
            Player.STATE_ENDED -> playback.value = SamplePlayback()

            Player.STATE_IDLE -> Unit
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        // Attribute the failure to the sample that was loading, so exactly one
        // row can say so. A dead CDN URL is the common case and it must not read
        // as "nothing happened when I tapped".
        playback.value = SamplePlayback(errorSampleId = playback.value.sampleId)
    }
}
