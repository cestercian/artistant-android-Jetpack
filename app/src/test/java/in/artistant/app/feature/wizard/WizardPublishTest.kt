package `in`.artistant.app.feature.wizard

import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.MediaAspect
import `in`.artistant.app.data.model.MediaKind
import `in`.artistant.app.data.model.SelfArtistRow
import `in`.artistant.app.data.model.SelfAvailability
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.platform.media.PendingAudioRef
import `in`.artistant.app.platform.media.PendingMediaRef
import `in`.artistant.app.platform.upload.MediaUploadEnqueuer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * The publish orchestration's ORDERING contract (M5b DoD): the artist row + packages/tech land
 * BEFORE any media is enqueued, and the row write is the one that flips setup_complete. Proven
 * with fakes recording into a shared log — no ViewModel/Compose/Context.
 */
class WizardPublishTest {

    private val log = mutableListOf<String>()

    @Test
    fun `publish saves the row before enqueuing any media`() = runTest {
        val id = runWizardPublish(
            fields = fields(),
            media = mediaWith(photo = true, video = false, gallery = 0, samples = 1),
            artists = RecordingArtists(log),
            packages = RecordingPackages(log),
            tech = RecordingTech(log),
            enqueuer = RecordingEnqueuer(log),
        )

        assertEquals("artist-1", id)
        // The row write ("save", which sets setup_complete=true) is first.
        assertEquals("save", log.first())
        // packages + tech land before ANY enqueue.
        val firstEnqueue = log.indexOfFirst { it.startsWith("enqueue") }
        assertTrue("nothing enqueued", firstEnqueue >= 0)
        assertTrue("packages before media", log.indexOf("packages") < firstEnqueue)
        assertTrue("tech before media", log.indexOf("tech") < firstEnqueue)
        assertTrue("save before media", log.indexOf("save") < firstEnqueue)
        // go-live flip happens before media too.
        assertTrue("publish before media", log.indexOf("publish") < firstEnqueue)
    }

    @Test
    fun `publish enqueues every pending media item`() = runTest {
        runWizardPublish(
            fields = fields(),
            media = mediaWith(photo = true, video = true, gallery = 2, samples = 3),
            artists = RecordingArtists(log),
            packages = RecordingPackages(log),
            tech = RecordingTech(log),
            enqueuer = RecordingEnqueuer(log),
        )
        assertEquals(1, log.count { it == "enqueue:photo:0" }) // cover pins position 0
        assertEquals(1, log.count { it == "enqueue:video" })
        assertEquals(2, log.count { it == "enqueue:photo:null" }) // gallery appends
        assertEquals(3, log.count { it == "enqueue:audio" })
    }

    // --- Fixtures -------------------------------------------------------

    private fun fields() = WizardPublishFields(
        handle = "kaavya",
        stageName = "Kaavya",
        category = "Indie Band",
        baseCity = "Bangalore",
        genre = "Indie",
        bio = null,
        coverGradientIndex = 0,
        daysAvailable = listOf("Fri", "Sat"),
        timeSlots = listOf("9:00 PM"),
        eventTypes = emptyList(),
        instagramHandle = null,
        spotifyArtistUrl = null,
        youtubeChannelUrl = null,
        packages = listOf(ArtistPackage("Full Band", "60 min", 22000, emptyList(), true)),
        tech = listOf("4 vocal mics"),
    )

    private fun mediaWith(photo: Boolean, video: Boolean, gallery: Int, samples: Int) = WizardPendingMedia(
        coverPhoto = if (photo) photoRef() else null,
        coverVideo = if (video) videoRef() else null,
        gallery = List(gallery) { photoRef() },
        samples = List(samples) { audioRef() },
    )

    private fun photoRef() = PendingMediaRef(
        id = UUID.randomUUID().toString(), kind = MediaKind.Photo, aspect = MediaAspect.Square,
        cacheFilename = "p.jpg", mimeType = "image/jpeg", width = 100, height = 100, durationSeconds = null,
    )

    private fun videoRef() = PendingMediaRef(
        id = UUID.randomUUID().toString(), kind = MediaKind.Video, aspect = MediaAspect.Landscape,
        cacheFilename = "v.mp4", mimeType = "video/mp4", width = 1920, height = 1080, durationSeconds = 8.0,
    )

    private fun audioRef() = PendingAudioRef(
        id = UUID.randomUUID().toString(), cacheFilename = "a.m4a", title = "Clip",
        durationSeconds = 30.0, ext = "m4a", mimeType = "audio/m4a",
    )
}

/** Records the write order; only the two calls Publish makes matter, the rest are no-ops. */
private class RecordingArtists(private val log: MutableList<String>) : ArtistsRepository {
    override suspend fun savePublishedProfile(
        handle: String, stageName: String, category: String, baseCity: String, genre: String?,
        bio: String?, coverGradientIndex: Int, daysAvailable: List<String>, timeSlots: List<String>,
        eventTypes: List<String>, instagramHandle: String?, spotifyArtistUrl: String?, youtubeChannelUrl: String?,
    ): String { log.add("save"); return "artist-1" }

    override suspend fun publish(artistId: String) { log.add("publish") }

    // Unused read/cache surface.
    override fun find(id: String): Artist? = null
    override fun cachedArtists(ids: List<String>): List<Artist> = emptyList()
    override fun cache(partials: List<Artist>) {}
    override suspend fun fetchArtist(id: String): Artist? = null
    override suspend fun ensureFull(id: String): Artist? = null
    override suspend fun fetchArtists(ids: List<String>) {}
    override suspend fun refresh(): List<Artist> = emptyList()
    override suspend fun upsertSelfArtist(
        handle: String, stageName: String, category: String, baseCity: String,
        genre: String?, bio: String?, coverGradientIndex: Int,
    ): String = "artist-1"
    override suspend fun fetchSelfArtistRow(): SelfArtistRow? = null
    override suspend fun fetchSelfAvailability(): SelfAvailability = SelfAvailability(emptyList(), emptyList())
    override suspend fun updateAvailability(days: List<String>, times: List<String>) {}
    override suspend fun updateCoverGradient(index: Int) {}
    override suspend fun updateSocials(instagram: String?, spotify: String?, youtube: String?) {}
    override fun invalidate(id: String) {}
}

private class RecordingPackages(private val log: MutableList<String>) : PackagesRepository {
    override suspend fun list(artistId: String): List<ArtistPackage> = emptyList()
    override suspend fun replaceAll(artistId: String, packages: List<ArtistPackage>) { log.add("packages") }
}

private class RecordingTech(private val log: MutableList<String>) : TechRiderRepository {
    override suspend fun list(artistId: String): List<String> = emptyList()
    override suspend fun replaceAll(artistId: String, items: List<String>) { log.add("tech") }
}

private class RecordingEnqueuer(private val log: MutableList<String>) : MediaUploadEnqueuer {
    override fun enqueuePhoto(ref: PendingMediaRef, artistId: String, position: Int?): UUID {
        log.add("enqueue:photo:${position?.toString() ?: "null"}"); return UUID.randomUUID()
    }
    override fun enqueueVideo(ref: PendingMediaRef, artistId: String): UUID {
        log.add("enqueue:video"); return UUID.randomUUID()
    }
    override fun enqueueAudioSample(ref: PendingAudioRef, artistId: String): UUID {
        log.add("enqueue:audio"); return UUID.randomUUID()
    }
}
