package `in`.artistant.app.feature.epk

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.ArtistLink
import `in`.artistant.app.data.model.ArtistMediaItem
import `in`.artistant.app.data.model.MediaKind
import `in`.artistant.app.data.model.SampleRow
import `in`.artistant.app.data.repository.ArtistLinksRepository
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.feature.wizard.PricingTier
import `in`.artistant.app.platform.media.EpkMediaStager
import `in`.artistant.app.platform.upload.MediaUploadEnqueuer
import `in`.artistant.app.platform.upload.UploadBannerSource
import `in`.artistant.app.platform.upload.UploadBannerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Six-photo / six-sample caps — mirror the wizard + the server-side triggers. */
private const val MAX_PHOTOS = 6
private const val MAX_SAMPLES = 6

/**
 * Everything the EPK editor renders in one snapshot. Real-data-only: an empty section
 * renders its truthful empty state; [error] surfaces any failed load/save behind a
 * banner so a dropped write (especially a MONEY field like pricing) is never silent.
 */
data class EpkUiState(
    val loading: Boolean = true,
    /** The signed-in artist's own id — scopes every child read/write. Blank = not loaded. */
    val artistId: String = "",
    val stageName: String = "",
    val category: String = "",
    val genre: String? = null,
    val baseCity: String = "",
    val handle: String = "",
    val bio: String = "",
    val coverGradientIndex: Int = 0,
    val media: List<ArtistMediaItem> = emptyList(),
    val samples: List<SampleRow> = emptyList(),
    /** Editable pricing tiers (reuses the wizard's [PricingTier]; debounced save). */
    val packages: List<PricingTier> = emptyList(),
    val tech: Set<String> = emptySet(),
    val links: List<ArtistLink> = emptyList(),
    val spotify: String = "",
    val instagram: String = "",
    val youtube: String = "",
    /** True while a picked sample is adopting + uploading (swaps Add for a spinner). */
    val uploadingSample: Boolean = false,
    val error: String? = null,
) {
    // Photo grouping mirrors iOS `ArtistMediaItem` array extension: photos position-
    // ordered, cover = position 0, gallery = the rest, plus the single cover video.
    private val photos: List<ArtistMediaItem>
        get() = media.filter { it.kind == MediaKind.Photo }.sortedBy { it.position }
    val coverPhoto: ArtistMediaItem? get() = photos.firstOrNull()
    val galleryPhotos: List<ArtistMediaItem> get() = photos.drop(1)
    val coverVideo: ArtistMediaItem? get() = media.firstOrNull { it.kind == MediaKind.Video }
    val photoCount: Int get() = photos.size
    val atPhotoCap: Boolean get() = photoCount >= MAX_PHOTOS
    val atSampleCap: Boolean get() = samples.size >= MAX_SAMPLES
    val shareUrl: String get() = if (handle.isNotBlank()) "artistant.in/$handle" else "artistant.in/yourhandle"
}

/**
 * Drives `EpkScreen` (port of iOS `EPKView` + `EPKStore`). Loads the signed-in artist's
 * row + all five child sections in parallel, then persists each section INDEPENDENTLY:
 *  - pricing + socials are DEBOUNCED (~1.2s) and generation-guarded so a stale in-flight
 *    save can never clobber a newer edit (the iOS audit-3 fix),
 *  - tech / gradient / links / media / samples save immediately on the discrete action,
 *  - photos enqueue onto the upload queue (batch), samples upload immediately.
 * Every failure lands in [EpkUiState.error] — no silent `try?`.
 *
 * TESTABILITY: the two media-add paths need a Context (bitmap/audio staging) so they go
 * through the fakeable [EpkMediaStager]/[MediaUploadEnqueuer] seams; every other dep is a
 * repo interface with a Fake twin, so the ViewModel is plain-JVM constructible.
 */
@HiltViewModel
class EpkViewModel @Inject constructor(
    private val artists: ArtistsRepository,
    private val packagesRepo: PackagesRepository,
    private val techRepo: TechRiderRepository,
    private val samplesRepo: SamplesRepository,
    private val mediaRepo: ArtistMediaRepository,
    private val linksRepo: ArtistLinksRepository,
    private val enqueuer: MediaUploadEnqueuer,
    private val stager: EpkMediaStager,
    private val uploadBannerSource: UploadBannerSource,
) : ViewModel() {

    private val _state = MutableStateFlow(EpkUiState())
    val state: StateFlow<EpkUiState> = _state.asStateFlow()

    /** Live upload-queue banner (photos drain here). */
    val uploadBanner: StateFlow<UploadBannerState> = uploadBannerSource.bannerStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UploadBannerState())

    /** Banner "retry all" — prune the finished/stalled batch (see UploadQueue.clearFinished). */
    fun clearFinishedUploads() = uploadBannerSource.clearFinished()

    // Debounce bookkeeping — one job + one monotonically-rising generation per debounced
    // section. The generation guard is the load-bearing bit: even if a delayed save survives
    // its cancel (a race the TestDispatcher can surface), it bails when a newer edit has
    // bumped the generation, so the LAST edit always wins the persisted value.
    private var pricingJob: Job? = null
    private var pricingGen = 0
    private var socialsJob: Job? = null
    private var socialsGen = 0

    init {
        load()
        // Refetch media when the queue completes a batch so newly-uploaded photos appear
        // without a manual refresh (iOS `onChange(of: uploadQueue.batchCompleted)`).
        viewModelScope.launch {
            var lastCompleted = 0
            uploadBanner.collect { banner ->
                val completed = banner.completed
                when {
                    // A new success landed → refetch so the photo appears.
                    completed > lastCompleted -> { lastCompleted = completed; reloadMedia() }
                    // The count DROPPED — WorkManager pruned finished infos (Retry-all /
                    // clearFinished calls pruneWork, resetting the live SUCCEEDED count to 0).
                    // Rebaseline so the NEXT success climbs past the mark and re-fires;
                    // otherwise `completed > lastCompleted` would stay false forever and a
                    // re-added photo would never reload (the banner's own recovery flow).
                    completed < lastCompleted -> lastCompleted = completed
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            // The self row carries the identity strings + the artist id every child read
            // needs. Without it there's nothing to scope to, so bail with a surfaced error
            // rather than silently rendering an empty editor.
            val row = runCatching { artists.fetchSelfArtistRow() }.getOrNull()
            if (row == null || row.id.isBlank()) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = "Couldn't load your profile — check your connection and try again.",
                )
                return@launch
            }
            _state.value = _state.value.copy(
                artistId = row.id,
                stageName = row.stageName,
                category = row.category,
                genre = row.genre,
                baseCity = row.baseCity,
                handle = row.handle,
                bio = row.bio.orEmpty(),
                coverGradientIndex = row.coverGradientIndex,
                spotify = row.spotifyArtistUrl.orEmpty(),
                instagram = row.instagramHandle.orEmpty(),
                youtube = row.youtubeChannelUrl.orEmpty(),
            )
            val id = row.id
            var error: String? = null
            coroutineScope {
                val pkgsD = async { runCatching { packagesRepo.list(id) } }
                val techD = async { runCatching { techRepo.list(id) } }
                val samplesD = async { runCatching { samplesRepo.list(id) } }
                val mediaD = async { runCatching { mediaRepo.list(id) } }
                val linksD = async { runCatching { linksRepo.list(id) } }

                pkgsD.await().fold(
                    onSuccess = { list -> _state.value = _state.value.copy(packages = list.map { it.toTier() }) },
                    onFailure = { error = it.message ?: "Couldn't load your pricing." },
                )
                techD.await().fold(
                    onSuccess = { _state.value = _state.value.copy(tech = it.toSet()) },
                    onFailure = { error = it.message ?: "Couldn't load your tech rider." },
                )
                samplesD.await().fold(
                    onSuccess = { _state.value = _state.value.copy(samples = it) },
                    onFailure = { error = it.message ?: "Couldn't load your samples." },
                )
                mediaD.await().fold(
                    onSuccess = { _state.value = _state.value.copy(media = it) },
                    onFailure = { error = it.message ?: "Couldn't load your photos." },
                )
                linksD.await().fold(
                    onSuccess = { _state.value = _state.value.copy(links = it) },
                    onFailure = { error = it.message ?: "Couldn't load your links." },
                )
            }
            _state.value = _state.value.copy(loading = false, error = error)
        }
    }

    fun dismissError() { _state.value = _state.value.copy(error = null) }

    private fun artistId(): String? = _state.value.artistId.takeIf { it.isNotBlank() }
    private fun setError(msg: String) { _state.value = _state.value.copy(error = msg) }
    private fun clearError() { if (_state.value.error != null) _state.value = _state.value.copy(error = null) }

    // --- Cover gradient (immediate) -------------------------------------

    fun setGradient(index: Int) {
        _state.value = _state.value.copy(coverGradientIndex = index)
        viewModelScope.launch {
            runCatching { artists.updateCoverGradient(index) }
                .onSuccess { clearError() }
                .onFailure { setError("Couldn't save your cover — try again.") }
        }
    }

    // --- Photos (staged → enqueued; drains via the worker) ---------------

    fun addPhoto(uri: Uri) {
        val id = artistId() ?: return
        // The Add affordance is hidden at the cap; this guards a double-pick race. The
        // server trigger is the true backstop (surfaced as an upload failure on the banner).
        if (_state.value.atPhotoCap) {
            setError("You can have up to $MAX_PHOTOS photos — remove one first.")
            return
        }
        viewModelScope.launch {
            try {
                val ref = withContext(Dispatchers.IO) { stager.writePhoto(uri) }
                enqueuer.enqueuePhoto(ref, id, null) // reload happens on batch-complete
                clearError()
            } catch (e: Throwable) {
                setError(e.message ?: "Couldn't add that photo — try another.")
            }
        }
    }

    fun deletePhoto(item: ArtistMediaItem) {
        viewModelScope.launch {
            runCatching { mediaRepo.delete(item) }
                .onSuccess { clearError(); reloadMedia() }
                .onFailure { setError("Couldn't remove that photo — try again.") }
        }
    }

    // --- Samples (immediate upload) --------------------------------------

    fun addSample(uri: Uri, displayName: String?) {
        val id = artistId() ?: return
        if (_state.value.atSampleCap) {
            setError("You can keep up to $MAX_SAMPLES samples — delete one to add another.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(uploadingSample = true)
            try {
                val ref = withContext(Dispatchers.IO) { stager.adoptAudio(uri, displayName) }
                val bytes = withContext(Dispatchers.IO) { stager.bytesOf(ref.cacheFilename) }
                samplesRepo.upload(bytes, ref.ext, ref.mimeType, ref.title, ref.durationSeconds, id)
                stager.delete(ref.cacheFilename) // uploaded — the local copy is dead weight
                clearError()
                reloadSamples()
            } catch (e: Throwable) {
                setError(e.message ?: "Couldn't add that sample — check your connection and try again.")
            } finally {
                _state.value = _state.value.copy(uploadingSample = false)
            }
        }
    }

    fun deleteSample(row: SampleRow) {
        val id = artistId() ?: return
        viewModelScope.launch {
            runCatching { samplesRepo.delete(row, id) }
                .onSuccess { clearError(); reloadSamples() }
                .onFailure { setError("Couldn't remove that sample — check your connection and try again.") }
        }
    }

    // --- Pricing (debounced + generation-guarded) ------------------------

    fun addTier() = setPackages(
        _state.value.packages + PricingTier(name = "New tier", duration = "60 min", price = 20000, popular = false),
    )
    fun removeTier(tierId: String) = setPackages(_state.value.packages.filterNot { it.id == tierId })
    fun updateTier(tier: PricingTier) = setPackages(_state.value.packages.map { if (it.id == tier.id) tier else it })

    private fun setPackages(list: List<PricingTier>) {
        _state.value = _state.value.copy(packages = list)
        val id = artistId() ?: return
        val gen = ++pricingGen
        pricingJob?.cancel()
        pricingJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            if (gen != pricingGen) return@launch // a newer edit superseded this one
            runCatching { packagesRepo.replaceAll(id, list.map { it.toArtistPackage() }) }
                .onSuccess { clearError() }
                .onFailure {
                    if (gen != pricingGen) return@onFailure
                    setError("Couldn't save your pricing — check your connection and edit again to retry.")
                }
        }
    }

    // --- Tech rider (immediate replace-all on each toggle) ---------------

    fun toggleTech(item: String) {
        val next = _state.value.tech.toMutableSet().apply { if (!add(item)) remove(item) }
        _state.value = _state.value.copy(tech = next)
        val id = artistId() ?: return
        viewModelScope.launch {
            runCatching { techRepo.replaceAll(id, next.toList()) }
                .onSuccess { clearError() }
                .onFailure { setError("Couldn't save your tech rider — try again.") }
        }
    }

    // --- Socials (debounced + generation-guarded) ------------------------

    fun setSpotify(v: String) { _state.value = _state.value.copy(spotify = v); scheduleSocialsSave() }
    fun setInstagram(v: String) { _state.value = _state.value.copy(instagram = v); scheduleSocialsSave() }
    fun setYoutube(v: String) { _state.value = _state.value.copy(youtube = v); scheduleSocialsSave() }

    private fun scheduleSocialsSave() {
        if (artistId() == null) return
        val gen = ++socialsGen
        socialsJob?.cancel()
        socialsJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            if (gen != socialsGen) return@launch
            val s = _state.value
            runCatching {
                artists.updateSocials(
                    instagram = s.instagram.nilIfBlank(),
                    spotify = s.spotify.nilIfBlank(),
                    youtube = s.youtube.nilIfBlank(),
                )
            }
                .onSuccess { clearError() }
                .onFailure { if (gen == socialsGen) setError("Couldn't save your socials — try again.") }
        }
    }

    // --- Bio (read-only in v1; parity with iOS — edit via the wizard) ----
    // No setter: iOS renders bio read-only here and defers in-place editing.

    // --- Links (immediate CRUD) ------------------------------------------

    fun addLink(label: String, url: String) {
        val id = artistId() ?: return
        viewModelScope.launch {
            runCatching { linksRepo.add(label, url, id) }
                .onSuccess { clearError(); reloadLinks() }
                .onFailure { setError("Couldn't save your link — check your connection and try again.") }
        }
    }

    fun updateLink(link: ArtistLink, label: String, url: String) {
        viewModelScope.launch {
            runCatching { linksRepo.update(link, label, url) }
                .onSuccess { clearError(); reloadLinks() }
                .onFailure { setError("Couldn't save your link — check your connection and try again.") }
        }
    }

    fun deleteLink(link: ArtistLink) {
        viewModelScope.launch {
            runCatching { linksRepo.delete(link) }
                .onSuccess { clearError(); reloadLinks() }
                .onFailure { setError("Couldn't remove your link — check your connection and try again.") }
        }
    }

    // --- Reloads (also used after a mutation) ---------------------------

    private suspend fun reloadMedia() {
        val id = artistId() ?: return
        runCatching { mediaRepo.list(id) }.onSuccess { _state.value = _state.value.copy(media = it) }
    }
    private suspend fun reloadSamples() {
        val id = artistId() ?: return
        runCatching { samplesRepo.list(id) }.onSuccess { _state.value = _state.value.copy(samples = it) }
    }
    private suspend fun reloadLinks() {
        val id = artistId() ?: return
        runCatching { linksRepo.list(id) }.onSuccess { _state.value = _state.value.copy(links = it) }
    }

    companion object {
        const val SAVE_DEBOUNCE_MS = 1_200L

        /**
         * Validates a custom link the way iOS `EditArtistLinkSheet.validate` does; returns
         * the error message to show, or null when the input is good. Pure so the sheet + the
         * unit test share one rule set.
         */
        fun validateLink(label: String, url: String): String? {
            val l = label.trim()
            val u = url.trim().lowercase()
            return when {
                l.isEmpty() -> "Label can't be blank."
                l.length > 32 -> "Label is too long (32 characters max)."
                !u.startsWith("https://") && !u.startsWith("http://") -> "URL needs to start with https:// or http://"
                u.length < 10 -> "That URL looks too short."
                else -> null
            }
        }
    }
}

/** Map a stored [ArtistPackage] into the editable [PricingTier] the pricing UI binds to. */
private fun `in`.artistant.app.data.model.ArtistPackage.toTier() = PricingTier(
    name = name, duration = duration, price = price, popular = popular,
)

private fun String.nilIfBlank(): String? = trim().ifBlank { null }
