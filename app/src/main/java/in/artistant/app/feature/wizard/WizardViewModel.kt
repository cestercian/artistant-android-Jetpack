package `in`.artistant.app.feature.wizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.HandleRules
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.feature.signup.HandleStatus
import `in`.artistant.app.platform.media.PendingAudioRef
import `in`.artistant.app.platform.media.PendingMediaRef
import `in`.artistant.app.platform.media.VideoTrimmer
import `in`.artistant.app.platform.media.WizardMediaCache
import `in`.artistant.app.platform.observability.Analytics
import `in`.artistant.app.platform.upload.MediaUploadEnqueuer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The artist onboarding wizard's observable state (iOS `ArtistOnboardingStore` @Published
 * surface). One immutable snapshot the steps render from; the media fields hold on-disk refs,
 * never bitmaps, so a 12MP pick never enters the state graph.
 */
data class WizardUiState(
    val step: WizardStep = WizardStep.Identity,
    // Identity
    val stageName: String = "",
    val handle: String = "",
    val handleStatus: HandleStatus = HandleStatus.Empty,
    val category: String = "",
    val genre: String = "",
    // Location
    val baseCity: String = "",
    val eventTypes: Set<String> = emptySet(),
    // Pricing
    val packages: List<PricingTier> = emptyList(),
    // Tech
    val tech: Set<String> = emptySet(),
    // Availability
    val daysAvailable: Set<String> = WizardConstants.defaultDays,
    val timeSlots: Set<String> = WizardConstants.defaultTimeSlots,
    // Cover
    val coverGradientIndex: Int = 0,
    val coverPhoto: PendingMediaRef? = null,
    val coverVideo: PendingMediaRef? = null,
    val gallery: List<PendingMediaRef> = emptyList(),
    val isProcessingVideo: Boolean = false,
    // Socials (paste-based)
    val spotify: String = "",
    val instagram: String = "",
    val youtube: String = "",
    // Bio
    val bio: String = "",
    // Samples
    val samples: List<PendingAudioRef> = emptyList(),
    // Publish
    val isPublishing: Boolean = false,
    val publishError: String? = null,
) {
    /** `.Error` counts as available (transient RPC blip must not trap the artist — iOS parity). */
    val handleAvailable: Boolean
        get() = handleStatus == HandleStatus.Available || handleStatus == HandleStatus.Error

    val identityValid: Boolean
        get() = WizardValidation.identity(stageName, handle.trim().length, handleAvailable, category)
    val locationValid: Boolean get() = WizardValidation.location(baseCity)
    val pricingValid: Boolean get() = WizardValidation.pricing(packages)
    val availabilityValid: Boolean get() = WizardValidation.availability(daysAvailable)
    val bioValid: Boolean get() = WizardValidation.bio(bio)
    val samplesValid: Boolean get() = WizardValidation.samples(samples.size)

    /** Whether the current step's CTA is enabled. */
    val canAdvance: Boolean
        get() = when (step) {
            WizardStep.Identity -> identityValid
            WizardStep.Location -> locationValid
            WizardStep.Pricing -> pricingValid
            WizardStep.Tech -> WizardValidation.tech()
            WizardStep.Availability -> availabilityValid
            WizardStep.Cover -> WizardValidation.cover()
            WizardStep.Socials -> WizardValidation.socials()
            WizardStep.Bio -> bioValid
            WizardStep.Samples -> samplesValid
            WizardStep.Preview, WizardStep.Done -> true
        }
}

/**
 * Drives the artist onboarding wizard (iOS `ArtistOnboardingStore`): field edits, live
 * handle availability, on-disk media staging, and the Publish orchestration.
 *
 * TESTABILITY: the load-bearing logic (step nav, validation, publish ordering) lives in the
 * PURE `WizardStep.kt` / `WizardPublish.kt` and is unit-tested there. This VM is just the
 * wiring + the media staging that genuinely needs a Context (cache/trimmer), which is why it's
 * never constructed in a plain-JUnit test.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class WizardViewModel @Inject constructor(
    private val users: UsersRepository,
    private val artists: ArtistsRepository,
    private val packages: PackagesRepository,
    private val techRider: TechRiderRepository,
    private val enqueuer: MediaUploadEnqueuer,
    private val cache: WizardMediaCache,
    private val trimmer: VideoTrimmer,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state

    init {
        // Live handle availability — same debounce idiom as SignupViewModel (350ms, drop invalid
        // formats before the RPC, guard a keep-typing race). Mirrors iOS `onHandleChanged`.
        viewModelScope.launch {
            _state
                .map { it.handle }
                .distinctUntilChanged()
                .debounce(350)
                .filter { HandleRules.isValidFormat(it) }
                .collect { handle ->
                    if (_state.value.handle != handle) return@collect
                    val result = users.handleIsAvailable(HandleRules.normalize(handle))
                    if (_state.value.handle != handle) return@collect
                    _state.update {
                        it.copy(
                            handleStatus = when (result) {
                                HandleAvailability.Available -> HandleStatus.Available
                                HandleAvailability.Unavailable -> HandleStatus.Taken
                                is HandleAvailability.Failure -> HandleStatus.Error
                            },
                        )
                    }
                }
        }
    }

    // --- Step nav -------------------------------------------------------

    fun advance() = _state.update { it.copy(step = advanceWizard(it.step)) }
    fun back() = _state.update { it.copy(step = backWizard(it.step)) }

    // --- Identity -------------------------------------------------------

    fun setStageName(v: String) = _state.update { it.copy(stageName = v) }
    fun setGenre(v: String) = _state.update { it.copy(genre = v) }

    fun setHandle(raw: String) {
        // Live-clean to the handle charset (lowercase alnum + underscore) so the field never
        // holds a value the format regex would reject, then set the synchronous status.
        val cleaned = raw.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        _state.update { it.copy(handle = cleaned, handleStatus = syncHandleStatus(cleaned)) }
    }

    private fun syncHandleStatus(handle: String): HandleStatus = when {
        handle.isEmpty() -> HandleStatus.Empty
        !HandleRules.isValidFormat(handle) -> HandleStatus.Invalid
        else -> HandleStatus.Checking
    }

    /** Choosing a category prefills the starter pricing tiers if none exist yet (iOS
     *  `chooseCategory`). */
    fun chooseCategory(c: String) = _state.update {
        it.copy(
            category = c,
            packages = if (it.packages.isEmpty()) WizardConstants.starterPackages(c) else it.packages,
        )
    }

    // --- Location -------------------------------------------------------

    fun setBaseCity(v: String) = _state.update { it.copy(baseCity = v) }
    fun toggleEventType(e: String) = _state.update { it.copy(eventTypes = it.eventTypes.toggle(e)) }

    // --- Pricing --------------------------------------------------------

    fun addTier() = _state.update {
        it.copy(packages = it.packages + PricingTier(name = "New tier", duration = "60 min", price = 20000, popular = false))
    }
    fun removeTier(id: String) = _state.update { it.copy(packages = it.packages.filterNot { p -> p.id == id }) }
    fun updateTier(tier: PricingTier) = _state.update {
        it.copy(packages = it.packages.map { p -> if (p.id == tier.id) tier else p })
    }

    // --- Tech / Availability -------------------------------------------

    fun toggleTech(item: String) = _state.update { it.copy(tech = it.tech.toggle(item)) }
    fun toggleDay(d: String) = _state.update { it.copy(daysAvailable = it.daysAvailable.toggle(d)) }
    fun toggleTimeSlot(s: String) = _state.update { it.copy(timeSlots = it.timeSlots.toggle(s)) }

    // --- Cover ----------------------------------------------------------

    fun setGradient(index: Int) = _state.update { it.copy(coverGradientIndex = index) }

    /** Normalize a picked/captured image to the wizard cache off the main thread, replacing any
     *  prior cover photo (whose backing file we delete so the cache doesn't accumulate orphans). */
    fun stageCoverPhoto(uri: Uri) = viewModelScope.launch {
        runMedia {
            val ref = withContext(Dispatchers.IO) { cache.writePhoto(uri) }
            _state.update { s ->
                s.coverPhoto?.let { cache.delete(it.cacheFilename) }
                s.copy(coverPhoto = ref)
            }
        }
    }

    /** Trim a picked/captured video to <=10s 1080p, adopt it into the cache, and stage it. */
    fun stageCoverVideo(uri: Uri) = viewModelScope.launch {
        _state.update { it.copy(isProcessingVideo = true) }
        runMedia {
            val trimmed = trimmer.trim(uri)
            val ref = withContext(Dispatchers.IO) { cache.adoptVideo(trimmed) }
            _state.update { s ->
                s.coverVideo?.let { cache.delete(it.cacheFilename) }
                s.copy(coverVideo = ref)
            }
        }
        _state.update { it.copy(isProcessingVideo = false) }
    }

    fun addGalleryPhoto(uri: Uri) = viewModelScope.launch {
        runMedia {
            val ref = withContext(Dispatchers.IO) { cache.writePhoto(uri) }
            _state.update { it.copy(gallery = it.gallery + ref) }
        }
    }

    fun removeCoverPhoto() = _state.update {
        it.coverPhoto?.let { ref -> cache.delete(ref.cacheFilename) }
        it.copy(coverPhoto = null)
    }
    fun removeCoverVideo() = _state.update {
        it.coverVideo?.let { ref -> cache.delete(ref.cacheFilename) }
        it.copy(coverVideo = null)
    }
    fun removeGalleryPhoto(id: String) = _state.update { s ->
        s.gallery.firstOrNull { it.id == id }?.let { cache.delete(it.cacheFilename) }
        s.copy(gallery = s.gallery.filterNot { it.id == id })
    }

    // --- Socials --------------------------------------------------------

    fun setSpotify(v: String) = _state.update { it.copy(spotify = v) }
    fun setInstagram(v: String) = _state.update { it.copy(instagram = v) }
    fun setYoutube(v: String) = _state.update { it.copy(youtube = v) }

    // --- Bio ------------------------------------------------------------

    /** Clamp on the way in so a 1000-char paste truncates immediately (iOS `bioBinding`). */
    fun setBio(v: String) = _state.update { it.copy(bio = if (v.length <= 200) v else v.take(200)) }

    // --- Samples --------------------------------------------------------

    fun addSample(uri: Uri, displayName: String?) = viewModelScope.launch {
        runMedia {
            val ref = withContext(Dispatchers.IO) { cache.adoptAudio(uri, displayName) }
            _state.update { it.copy(samples = it.samples + ref) }
        }
    }

    fun renameSample(id: String, title: String) = _state.update { s ->
        s.copy(samples = s.samples.map { if (it.id == id) it.copy(title = title) else it })
    }

    fun removeSample(id: String) = _state.update { s ->
        s.samples.firstOrNull { it.id == id }?.let { cache.delete(it.cacheFilename) }
        s.copy(samples = s.samples.filterNot { it.id == id })
    }

    // --- Publish --------------------------------------------------------

    /**
     * Save + go live + enqueue media (delegates ordering to [runWizardPublish]). On success:
     * hand the pending refs off to the queue (clear them from state but keep the on-disk files —
     * the queue owns them now) and advance to the Done celebration. On failure, surface the
     * message on the preview screen; the artist is never told they published when the write failed.
     */
    fun publish() {
        val s = _state.value
        if (s.isPublishing) return
        _state.update { it.copy(isPublishing = true, publishError = null) }
        viewModelScope.launch {
            try {
                runWizardPublish(
                    fields = WizardPublishFields(
                        handle = s.handle,
                        stageName = s.stageName,
                        category = s.category,
                        baseCity = s.baseCity,
                        genre = s.genre.nilIfBlank(),
                        bio = s.bio.nilIfBlank(),
                        coverGradientIndex = s.coverGradientIndex,
                        daysAvailable = s.daysAvailable.toList(),
                        timeSlots = WizardConstants.sortedTimeSlots(s.timeSlots),
                        eventTypes = s.eventTypes.toList(),
                        instagramHandle = s.instagram.nilIfBlank(),
                        spotifyArtistUrl = s.spotify.nilIfBlank(),
                        youtubeChannelUrl = s.youtube.nilIfBlank(),
                        packages = s.packages.map { it.toArtistPackage() },
                        tech = s.tech.toList(),
                    ),
                    media = WizardPendingMedia(
                        coverPhoto = s.coverPhoto,
                        coverVideo = s.coverVideo,
                        gallery = s.gallery,
                        samples = s.samples,
                    ),
                    artists = artists,
                    packages = packages,
                    tech = techRider,
                    enqueuer = enqueuer,
                )
                analytics.capture("artist_published")
                // Hand off: clear refs (queue owns the files now) and land on Done.
                _state.update {
                    it.copy(
                        isPublishing = false,
                        coverPhoto = null, coverVideo = null, gallery = emptyList(), samples = emptyList(),
                        step = WizardStep.Done,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(isPublishing = false, publishError = e.message ?: "Couldn't publish. Try again.") }
            }
        }
    }

    // --- Helpers --------------------------------------------------------

    /** Absolute file for a staged media ref, so a preview can render it (Coil accepts a File). */
    fun cacheFile(cacheFilename: String): java.io.File = cache.resolve(cacheFilename)

    /** Run a media-staging block, surfacing any failure as the publish/pick error line. */
    private inline fun runMedia(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            _state.update { it.copy(publishError = e.message ?: "Couldn't process that file. Try another.") }
        }
    }
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

/** Trim + null-out an empty string so a skipped field lands as NULL, not "" (iOS `nilIfEmpty`). */
private fun String.nilIfBlank(): String? = trim().ifBlank { null }
