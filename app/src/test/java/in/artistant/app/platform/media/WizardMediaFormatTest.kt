package `in`.artistant.app.platform.media

import `in`.artistant.app.data.repository.SupabaseSamplesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Which picked clips are allowed to become staged samples.
 *
 * `WizardMediaCache` needs a Context, but the decision does not — and the
 * decision is the whole bug. The samples picker offered the wildcard audio MIME while the
 * `artist-samples` bucket accepts only `audio/mpeg, audio/mp4, audio/aac,
 * audio/m4a, audio/x-m4a` under a 10 MiB cap (migration 0010). So a WAV — the
 * format a recorded live take arrives in — was copied into the cache, listed in
 * the samples step, published, and only then rejected three times by Storage, in
 * a failure set no screen in the app reads. The artist was told "You're live"
 * and the clip never existed.
 *
 * Three properties keep that dead: the picker offers exactly what the bucket
 * takes, a rejected format is refused at the pick rather than at the upload, and
 * every extension we stage maps back to a `Content-Type` the bucket accepts.
 */
class WizardMediaFormatTest {

    /** `allowed_mime_types` from 0010_storage_buckets.sql, verbatim. */
    private val bucketMimeTypes = setOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/aac",
        "audio/m4a",
        "audio/x-m4a",
    )

    @Test
    fun `the picker offers exactly what the bucket accepts`() {
        // Not "roughly" — a picker that offers a format Storage rejects is the
        // bug. Anything added here has to be added to 0010 first.
        assertEquals(bucketMimeTypes, WizardMediaCache.ACCEPTED_AUDIO_MIME_TYPES.toSet())
        WizardMediaCache.ACCEPTED_AUDIO_MIME_TYPES.forEach { mime ->
            assertNotNull(
                "$mime is offered by the picker but has no staged extension",
                WizardMediaCache.stagedAudioExtension(mime, null),
            )
        }
    }

    @Test
    fun `the size cap is the bucket's cap`() {
        // 10485760 in 0010. Stated as bytes so a drift in either direction is a
        // failing test rather than three silent retries.
        assertEquals(10_485_760L, WizardMediaCache.MAX_AUDIO_BYTES)
    }

    @Test
    fun `accepted types keep their own format`() {
        // Relabelling everything .m4a would set the wrong Content-Type on the
        // upload and break decoders that sniff by extension.
        assertEquals("mp3", WizardMediaCache.stagedAudioExtension("audio/mpeg", "take.mp3"))
        assertEquals("m4a", WizardMediaCache.stagedAudioExtension("audio/mp4", "take.m4a"))
        assertEquals("m4a", WizardMediaCache.stagedAudioExtension("audio/x-m4a", "take.m4a"))
        assertEquals("aac", WizardMediaCache.stagedAudioExtension("audio/aac", "take.aac"))
    }

    @Test
    fun `a type the bucket rejects is refused at the pick`() {
        assertNull(WizardMediaCache.stagedAudioExtension("audio/wav", "take.wav"))
        assertNull(WizardMediaCache.stagedAudioExtension("audio/x-wav", "take.wav"))
        assertNull(WizardMediaCache.stagedAudioExtension("audio/ogg", "take.ogg"))
        assertNull(WizardMediaCache.stagedAudioExtension("audio/flac", "take.flac"))
        assertNull(WizardMediaCache.stagedAudioExtension("video/mp4", "take.mp4"))
    }

    @Test
    fun `a stated type is never overruled by the file name`() {
        // A WAV saved as "take.mp3" is still a WAV. Falling through to the
        // extension would stage it as an MP3 and upload bytes no decoder can
        // read — worse than the 400, because that one at least fails loudly.
        assertNull(WizardMediaCache.stagedAudioExtension("audio/wav", "take.mp3"))
    }

    @Test
    fun `the reported type is read leniently`() {
        // Providers answer with parameters and mixed case.
        assertEquals("mp3", WizardMediaCache.stagedAudioExtension("Audio/MPEG; charset=utf-8", null))
        assertEquals("mp3", WizardMediaCache.stagedAudioExtension("  audio/mpeg  ", null))
        // Some report the non-standard audio/mp3 for an MP3; the .mp3 extension
        // maps back to audio/mpeg on the way out, so it is safe to take.
        assertEquals("mp3", WizardMediaCache.stagedAudioExtension("audio/mp3", null))
    }

    @Test
    fun `a provider that states no type falls back to the file name`() {
        assertEquals("mp3", WizardMediaCache.stagedAudioExtension(null, "take.MP3"))
        assertEquals("aac", WizardMediaCache.stagedAudioExtension("", "take.aac"))
        assertNull(WizardMediaCache.stagedAudioExtension(null, "take.wav"))
        // A document id, not a file name — no extension to read. The picker's
        // MIME filter already matched, so in practice this is AAC/M4A.
        assertEquals("m4a", WizardMediaCache.stagedAudioExtension(null, "audio:1000000042"))
        assertEquals("m4a", WizardMediaCache.stagedAudioExtension(null, null))
    }

    @Test
    fun `every extension the cache stages uploads as a mime the bucket accepts`() {
        // The other half of the contract: SamplesRepository derives the upload's
        // Content-Type from the staged file's extension, so staging an extension
        // it maps to audio/wav would 400 just as surely as the picker did.
        val staged = (WizardMediaCache.ACCEPTED_AUDIO_MIME_TYPES + "audio/mp3")
            .mapNotNull { WizardMediaCache.stagedAudioExtension(it, null) }
            .toSet()

        assertEquals(setOf("mp3", "m4a", "aac"), staged)
        staged.forEach { ext ->
            val (_, mime) = SupabaseSamplesRepository.audioFormat(File("clip.$ext"))
            assertTrue("a .$ext sample uploads as $mime, which 0010 rejects", mime in bucketMimeTypes)
        }
    }
}
