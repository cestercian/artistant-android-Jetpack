package `in`.artistant.app.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `FakeSamplesRepository.upload` used to store the raw title and leave
 * `audioUrl` null — the app's "unplayable" signal (see the doc on
 * `Sample.audioUrl`) — so a clip uploaded through this double could never
 * exercise the playback control the real upload enables.
 */
class SamplesRepositoryLogicTest {

    @Test
    fun upload_blankTitle_fallsBackToTheSamePlaceholderTheRealUploadUses() = runTest {
        val repo = FakeSamplesRepository()

        val sample = repo.upload(File("clip.mp3"), title = "   ", durationSeconds = 5.0, artistId = ARTIST)

        assertEquals("Sample", sample.title)
    }

    @Test
    fun upload_trimsASurroundedTitle() = runTest {
        val repo = FakeSamplesRepository()

        val sample = repo.upload(File("clip.mp3"), title = "  Rooftop set  ", durationSeconds = 5.0, artistId = ARTIST)

        assertEquals("Rooftop set", sample.title)
    }

    @Test
    fun upload_stampsAResolvableAudioUrl_soTheClipIsPlayable() = runTest {
        val repo = FakeSamplesRepository()

        val sample = repo.upload(File("clip.wav"), title = "Set", durationSeconds = 5.0, artistId = ARTIST)

        assertNotNull("Sample.audioUrl == null is the app's unplayable signal", sample.audioUrl)
        assertTrue(sample.audioUrl!!.endsWith("${sample.id}.wav"))
    }

    private companion object {
        const val ARTIST = "11111111-1111-1111-1111-111111111111"
    }
}
