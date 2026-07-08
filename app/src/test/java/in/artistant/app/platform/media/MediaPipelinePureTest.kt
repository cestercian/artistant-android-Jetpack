package `in`.artistant.app.platform.media

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure bits of the media pipeline: duration clamp + audio format + labels. */
class MediaPipelinePureTest {

    @Test
    fun `clampDuration pins into the DB-legal 1 to 10 window`() {
        assertEquals(1.0, VideoTrimmer.clampDuration(0.4), 0.0001)   // sub-1s clip
        assertEquals(10.0, VideoTrimmer.clampDuration(10.6), 0.0001) // rounding overshoot
        assertEquals(6.3, VideoTrimmer.clampDuration(6.3), 0.0001)   // in range untouched
    }

    @Test
    fun `audio ext + mime preserve the source format`() {
        assertEquals("mp3", WizardMediaFormats.normalizedAudioExt("MP3"))
        assertEquals("audio/mpeg", WizardMediaFormats.audioMime("mp3"))
        assertEquals("aiff", WizardMediaFormats.normalizedAudioExt("aif"))
        assertEquals("audio/aiff", WizardMediaFormats.audioMime("aiff"))
        // Empty/unknown extension defaults to m4a (the picker's AAC fallback).
        assertEquals("m4a", WizardMediaFormats.normalizedAudioExt(""))
        assertEquals("audio/m4a", WizardMediaFormats.audioMime(""))
    }

    @Test
    fun `durationLabel formats seconds and minutes`() {
        assertEquals("30s", WizardMediaFormats.durationLabel(30.0))
        assertEquals("1:24", WizardMediaFormats.durationLabel(84.0))
        assertEquals("2:05", WizardMediaFormats.durationLabel(125.0))
    }
}
