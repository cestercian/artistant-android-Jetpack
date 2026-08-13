package `in`.artistant.app.domain.sample

import `in`.artistant.app.data.model.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Playing an artist's samples.
 *
 * The player itself is not covered here — it needs a Context, a decoder and a
 * main looper. What is covered is the logic that fails *quietly* around it:
 * tapping the wrong row restarting a clip from zero, a sample with no file
 * behind it showing a play button that does nothing, and the duration that gets
 * shown next to the title.
 */
class SamplePlaybackTest {

    private fun sample(
        id: String = "s1",
        title: String = "Opening set",
        duration: String = "",
        url: String? = "https://cdn.example/artist-samples/a1/s1.m4a",
    ) = Sample(id = id, title = title, duration = duration, audioUrl = url)

    // ── What a tap means ─────────────────────────────────────────────────────

    @Test
    fun `tapping an idle row starts it`() {
        val tap = sampleTapAction(sample(), SamplePlayback())
        assertEquals(SampleTap.Start("s1", "https://cdn.example/artist-samples/a1/s1.m4a"), tap)
    }

    @Test
    fun `tapping the row that is playing pauses it`() {
        val tap = sampleTapAction(
            sample(),
            SamplePlayback(sampleId = "s1", isPlaying = true),
        )
        assertEquals(SampleTap.Pause, tap)
    }

    @Test
    fun `tapping the paused row resumes instead of restarting`() {
        // Restarting would throw away the prepared media and the position, and
        // drop the listener back to 0:00 — the one thing a pause/play toggle
        // must never do.
        val tap = sampleTapAction(
            sample(),
            SamplePlayback(sampleId = "s1", isPlaying = false, positionMs = 42_000),
        )
        assertEquals(SampleTap.Resume, tap)
    }

    @Test
    fun `tapping a different row while one plays switches to it`() {
        val tap = sampleTapAction(
            sample(id = "s2", url = "https://cdn.example/s2.m4a"),
            SamplePlayback(sampleId = "s1", isPlaying = true),
        )
        assertEquals(SampleTap.Start("s2", "https://cdn.example/s2.m4a"), tap)
    }

    @Test
    fun `a sample with no file behind it is unplayable, not silently broken`() {
        // audio_url is nullable and rows predating the upload path (or whose
        // upload never drained) carry a title with no file.
        assertEquals(SampleTap.Unplayable, sampleTapAction(sample(url = null), SamplePlayback()))
        assertEquals(SampleTap.Unplayable, sampleTapAction(sample(url = "  "), SamplePlayback()))
        assertFalse(sampleIsPlayable(sample(url = null)))
        assertTrue(sampleIsPlayable(sample()))
    }

    // ── Exclusivity ──────────────────────────────────────────────────────────

    @Test
    fun `only the loaded sample reads as playing`() {
        val state = SamplePlayback(sampleId = "s1", isPlaying = true)
        assertTrue(state.isPlaying("s1"))
        assertFalse(state.isPlaying("s2"))
        assertFalse(SamplePlayback(sampleId = "s1", isPlaying = false).isPlaying("s1"))
    }

    // ── Clock and progress ───────────────────────────────────────────────────

    @Test
    fun `the clock pads seconds and rolls into minutes`() {
        assertEquals("0:00", formatPlaybackClock(0))
        assertEquals("0:07", formatPlaybackClock(7_400))
        assertEquals("1:00", formatPlaybackClock(60_000))
        assertEquals("2:05", formatPlaybackClock(125_999))
        assertEquals("12:30", formatPlaybackClock(750_000))
    }

    @Test
    fun `an unprepared duration clamps instead of rendering negative time`() {
        // ExoPlayer reports C.TIME_UNSET — a large negative — for media it has
        // not prepared, and that value reaches the UI on the frame after a tap.
        assertEquals("0:00", formatPlaybackClock(Long.MIN_VALUE + 1))
        assertEquals("0:00", formatPlaybackClock(-1))
    }

    @Test
    fun `progress is a fraction, and a zero duration does not divide by zero`() {
        assertEquals(0f, playbackProgress(0, 100_000), 0.0001f)
        assertEquals(0.5f, playbackProgress(50_000, 100_000), 0.0001f)
        assertEquals(1f, playbackProgress(100_000, 100_000), 0.0001f)
        // Between the tap and the player reporting a length, duration is 0.
        assertEquals(0f, playbackProgress(5_000, 0), 0.0001f)
        assertEquals(0f, playbackProgress(5_000, -1), 0.0001f)
        // A position past the end (seek rounding) stays inside the bar.
        assertEquals(1f, playbackProgress(101_000, 100_000), 0.0001f)
    }

    // ── Which duration to believe ────────────────────────────────────────────

    @Test
    fun `the player's measured duration beats the stored label`() {
        // Every sample uploaded from this app records zero seconds, so the
        // server label says "0:00" for clips that are minutes long. Once the
        // player has a real number, that is the one worth showing.
        val label = sampleDurationLabel(
            sample(duration = "0:00"),
            SamplePlayback(sampleId = "s1", durationMs = 212_000),
        )
        assertEquals("3:32", label)
    }

    @Test
    fun `a stored 0 colon 00 renders as nothing rather than as a confident lie`() {
        assertNull(sampleDurationLabel(sample(duration = "0:00"), SamplePlayback()))
        assertNull(sampleDurationLabel(sample(duration = ""), SamplePlayback()))
    }

    @Test
    fun `an unplayed row keeps a real stored label`() {
        assertEquals("3:20", sampleDurationLabel(sample(duration = "3:20"), SamplePlayback()))
    }

    @Test
    fun `another row playing does not relabel this one`() {
        val label = sampleDurationLabel(
            sample(id = "s2", duration = "1:45"),
            SamplePlayback(sampleId = "s1", durationMs = 212_000),
        )
        assertEquals("1:45", label)
    }
}
