package `in`.artistant.app.feature.epk

import android.net.Uri
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.SampleRow
import `in`.artistant.app.data.model.SelfArtistRow
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.FakeArtistLinksRepository
import `in`.artistant.app.data.repository.FakeArtistMediaRepository
import `in`.artistant.app.data.repository.FakeArtistsRepository
import `in`.artistant.app.data.repository.FakePackagesRepository
import `in`.artistant.app.data.repository.FakeSamplesRepository
import `in`.artistant.app.data.repository.FakeTechRiderRepository
import `in`.artistant.app.platform.media.EpkMediaStager
import `in`.artistant.app.platform.media.PendingAudioRef
import `in`.artistant.app.platform.media.PendingMediaRef
import `in`.artistant.app.platform.upload.MediaUploadEnqueuer
import `in`.artistant.app.platform.upload.UploadBannerSource
import `in`.artistant.app.platform.upload.UploadBannerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Covers the EPK editor VM through the fakes: the parallel load hydrating every section,
 * a persisting save path (links + tech), the debounced-pricing generation guard (a stale
 * debounce can't clobber a newer edit), and the pure link validation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EpkViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val artistId = "a1"

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun selfRow() = SelfArtistRow(
        id = artistId, stageName = "Kaavya Menon", handle = "kaavya", category = "Singer", baseCity = "Goa",
        genre = "Indie", bio = "Soulful sets.", coverGradientIndex = 2, published = true, setupComplete = true,
        instagramHandle = "@kaavya", spotifyArtistUrl = "open.spotify.com/artist/x", youtubeChannelUrl = null,
    )

    // Media staging isn't exercised in these tests (no Context), so a throwing stub is enough.
    private val stager = object : EpkMediaStager {
        override fun writePhoto(source: Uri): PendingMediaRef = error("not used")
        override fun adoptAudio(source: Uri, displayName: String?): PendingAudioRef = error("not used")
        override fun bytesOf(cacheFilename: String): ByteArray = error("not used")
        override fun delete(cacheFilename: String) {}
    }
    private val enqueuer = object : MediaUploadEnqueuer {
        override fun enqueuePhoto(ref: PendingMediaRef, artistId: String, position: Int?): UUID = UUID.randomUUID()
        override fun enqueueVideo(ref: PendingMediaRef, artistId: String): UUID = UUID.randomUUID()
        override fun enqueueAudioSample(ref: PendingAudioRef, artistId: String): UUID = UUID.randomUUID()
    }
    private val idleBanner = object : UploadBannerSource {
        override fun bannerStateFlow(): Flow<UploadBannerState> = flowOf(UploadBannerState())
        override fun clearFinished() {}
    }

    private fun vm(
        artists: ArtistsRepository,
        packages: FakePackagesRepository = FakePackagesRepository(),
        tech: FakeTechRiderRepository = FakeTechRiderRepository(),
        samples: FakeSamplesRepository = FakeSamplesRepository(),
        media: FakeArtistMediaRepository = FakeArtistMediaRepository(),
        links: FakeArtistLinksRepository = FakeArtistLinksRepository(),
    ) = EpkViewModel(artists, packages, tech, samples, media, links, enqueuer, stager, idleBanner)

    @Test
    fun `parallel load hydrates identity, pricing, tech, samples and socials`() = runTest(dispatcher) {
        val artists = FakeArtistsRepository().apply { selfRow = selfRow() }
        val packages = FakePackagesRepository(
            seed = mapOf(artistId to listOf(ArtistPackage("Cafe Set", "60 min", 14_000, emptyList(), popular = true))),
        )
        val tech = FakeTechRiderRepository(seed = mapOf(artistId to listOf("4 vocal mics")))
        val samples = FakeSamplesRepository(
            seed = mapOf(artistId to listOf(SampleRow("s1", artistId, 0, "Demo", "1:20", "http://x/s.mp3", null, null))),
        )
        val vm = vm(artists, packages = packages, tech = tech, samples = samples)
        advanceUntilIdle()

        val s = vm.state.value
        assertNull(s.error)
        assertEquals(artistId, s.artistId)
        assertEquals("Kaavya Menon", s.stageName)
        assertEquals(2, s.coverGradientIndex)
        assertEquals("@kaavya", s.instagram)
        assertEquals(1, s.packages.size)
        assertEquals(14_000, s.packages.first().price)
        assertEquals(setOf("4 vocal mics"), s.tech)
        assertEquals(1, s.samples.size)
    }

    @Test
    fun `a load with no artist row surfaces an error instead of a silent empty editor`() = runTest(dispatcher) {
        val artists = FakeArtistsRepository() // selfRow stays null
        val vm = vm(artists)
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertEquals("", vm.state.value.artistId)
    }

    @Test
    fun `addLink and toggleTech persist through the repositories and back into state`() = runTest(dispatcher) {
        val artists = FakeArtistsRepository().apply { selfRow = selfRow() }
        val links = FakeArtistLinksRepository()
        val tech = FakeTechRiderRepository()
        val vm = vm(artists, tech = tech, links = links)
        advanceUntilIdle()

        vm.addLink("Bandcamp", "https://kaavya.bandcamp.com")
        vm.toggleTech("1 DI box")
        advanceUntilIdle()

        assertEquals(1, links.list(artistId).size)
        assertEquals("Bandcamp", vm.state.value.links.first().label)
        assertEquals(listOf("1 DI box"), tech.list(artistId))
        assertTrue("1 DI box" in vm.state.value.tech)
    }

    @Test
    fun `a stale debounced pricing save cannot overwrite a newer edit`() = runTest(dispatcher) {
        val artists = FakeArtistsRepository().apply { selfRow = selfRow() }
        val packages = FakePackagesRepository(
            seed = mapOf(artistId to listOf(ArtistPackage("Base", "60 min", 10_000, emptyList()))),
        )
        val vm = vm(artists, packages = packages)
        advanceUntilIdle()

        val tier = vm.state.value.packages.first()
        vm.updateTier(tier.copy(price = 11_111)) // schedules a save…
        vm.updateTier(tier.copy(price = 22_222)) // …superseded before the debounce fires
        advanceUntilIdle()

        // Only the newest edit reaches the server + stays in state.
        assertEquals(22_222, packages.list(artistId).first().price)
        assertEquals(22_222, vm.state.value.packages.first().price)
    }

    @Test
    fun `link validation mirrors the iOS rules`() {
        assertNull(EpkViewModel.validateLink("Bandcamp", "https://x.bandcamp.com"))
        assertNotNull(EpkViewModel.validateLink("", "https://x.com"))              // blank label
        assertNotNull(EpkViewModel.validateLink("x".repeat(33), "https://x.com"))  // too-long label
        assertNotNull(EpkViewModel.validateLink("Site", "ftp://x.com"))            // bad scheme
        assertNotNull(EpkViewModel.validateLink("Site", "https://x"))              // too short
    }
}
