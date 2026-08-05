package `in`.artistant.app.feature.epk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.repository.ArtistLink
import `in`.artistant.app.data.repository.ArtistLinksRepository
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PackageDraft
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.media.UploadQueue
import `in`.artistant.app.platform.media.WizardMediaCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EpkUiState(
    val artist: Artist? = null,
    val links: List<ArtistLink> = emptyList(),
    val photos: List<`in`.artistant.app.data.repository.ArtistMediaItem> = emptyList(),
    val setupComplete: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val editingPackages: Boolean = false,
    val packageName: String = "",
    val packageDuration: String = "2h",
    val packagePrice: String = "",
    val linkLabel: String = "",
    val linkUrl: String = "",
    val techDraft: String = "",
)

@HiltViewModel
class EpkViewModel @Inject constructor(
    private val session: SessionManager,
    private val users: UsersRepository,
    private val artists: ArtistsRepository,
    private val packages: PackagesRepository,
    private val techRider: TechRiderRepository,
    private val samples: SamplesRepository,
    private val links: ArtistLinksRepository,
    private val media: `in`.artistant.app.data.repository.ArtistMediaRepository,
    private val mediaCache: WizardMediaCache,
    private val uploadQueue: UploadQueue,
) : ViewModel() {

    private val _state = MutableStateFlow(EpkUiState())
    val state: StateFlow<EpkUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, message = null) }
            val userId = session.currentUserId
            if (userId == null) {
                _state.update { it.copy(isLoading = false, error = "Sign in to view your EPK.") }
                return@launch
            }
            val profile = runCatching { users.fetchSelfProfile() }.getOrNull()
            val setupComplete = profile?.artistSetupComplete == true
            val artist = runCatching { artists.fetchArtist(userId) }.getOrNull()
            val linkRows = runCatching { links.list(userId) }.getOrDefault(emptyList())
            val photos = runCatching {
                media.list(userId).filter {
                    it.kind == `in`.artistant.app.data.repository.ArtistMediaKind.photo
                }
            }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    artist = artist,
                    links = linkRows,
                    photos = photos,
                    setupComplete = setupComplete,
                    isLoading = false,
                    error = if (artist == null && setupComplete) "Couldn't load your EPK." else null,
                    packageName = artist?.packages?.firstOrNull()?.name.orEmpty(),
                    packageDuration = artist?.packages?.firstOrNull()?.duration ?: "2h",
                    packagePrice = artist?.packages?.firstOrNull()?.price?.toString().orEmpty(),
                )
            }
        }
    }

    fun onPackageName(v: String) = _state.update { it.copy(packageName = v) }
    fun onPackageDuration(v: String) = _state.update { it.copy(packageDuration = v) }
    fun onPackagePrice(v: String) = _state.update { it.copy(packagePrice = v) }
    fun onLinkLabel(v: String) = _state.update { it.copy(linkLabel = v) }
    fun onLinkUrl(v: String) = _state.update { it.copy(linkUrl = v) }
    fun onTechDraft(v: String) = _state.update { it.copy(techDraft = v) }
    fun toggleEditingPackages() = _state.update { it.copy(editingPackages = !it.editingPackages) }

    fun savePackages() {
        val userId = session.currentUserId ?: return
        val snap = _state.value
        val price = snap.packagePrice.toIntOrNull() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            runCatching {
                packages.replaceAll(
                    userId,
                    listOf(
                        PackageDraft(
                            name = snap.packageName.ifBlank { "Set" },
                            durationLabel = snap.packageDuration.ifBlank { "2h" },
                            priceInr = price,
                            // Matches the iOS default and PackagesRepository's own
                            // `popular = false`. The EPK saves a single package, so
                            // `true` here badged every artist's only tier "Popular"
                            // — a comparison against an empty field.
                            popular = false,
                        ),
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(isSaving = false, message = "Packages saved.", editingPackages = false) }
                refresh()
            }.onFailure { e ->
                _state.update { it.copy(isSaving = false, error = e.message ?: "Couldn't save packages.") }
            }
        }
    }

    fun addTechItem() {
        val userId = session.currentUserId ?: return
        val item = _state.value.techDraft.trim()
        if (item.isBlank()) return
        val current = _state.value.artist?.tech.orEmpty()
        viewModelScope.launch {
            runCatching { techRider.replaceAll(userId, current + item) }
                .onSuccess {
                    _state.update { it.copy(techDraft = "", message = "Tech rider updated.") }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Couldn't update tech.") }
                }
        }
    }

    fun removeTechItem(item: String) {
        val userId = session.currentUserId ?: return
        val next = _state.value.artist?.tech.orEmpty().filterNot { it == item }
        viewModelScope.launch {
            runCatching { techRider.replaceAll(userId, next) }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Couldn't update tech.") }
                }
        }
    }

    fun addLink() {
        val userId = session.currentUserId ?: return
        val label = _state.value.linkLabel.trim()
        val url = _state.value.linkUrl.trim()
        if (label.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            runCatching { links.upsert(userId, label, url) }
                .onSuccess {
                    _state.update { it.copy(linkLabel = "", linkUrl = "", message = "Link added.") }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Couldn't add link.") }
                }
        }
    }

    fun deleteLink(id: String) {
        viewModelScope.launch {
            runCatching { links.delete(id) }
                .onSuccess { refresh() }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Couldn't delete link.") }
                }
        }
    }

    fun onSamplePicked(uri: android.net.Uri, displayName: String?) {
        val userId = session.currentUserId ?: return
        viewModelScope.launch {
            runCatching {
                val title = displayName?.substringBeforeLast('.')?.ifBlank { null } ?: "Sample"
                val pending = mediaCache.adoptAudio(uri, title)
                uploadQueue.enqueueAudioSample(
                    artistId = userId,
                    file = pending.file(mediaCache),
                    title = pending.title,
                    durationSeconds = pending.durationSeconds,
                )
            }.onSuccess {
                _state.update { it.copy(message = "Sample queued for upload.") }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Couldn't import sample.") }
            }
        }
    }

    fun onPhotoPicked(uri: android.net.Uri) {
        val userId = session.currentUserId ?: return
        viewModelScope.launch {
            runCatching {
                val pending = mediaCache.adoptPhoto(uri)
                uploadQueue.enqueueCoverPhoto(userId, pending.file(mediaCache))
            }.onSuccess {
                _state.update { it.copy(message = "Photo queued — pull to refresh when done.") }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Couldn't import photo.") }
            }
        }
    }

    fun deletePhoto(item: `in`.artistant.app.data.repository.ArtistMediaItem) {
        viewModelScope.launch {
            runCatching { media.delete(item) }
                .onSuccess {
                    _state.update { it.copy(message = "Photo removed.") }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Couldn't delete photo.") }
                }
        }
    }

    /** Move photo at [from] toward cover (index 0) or later; persists via reorder_artist_media. */
    fun movePhoto(from: Int, to: Int) {
        val photos = _state.value.photos.toMutableList()
        if (from !in photos.indices || to !in photos.indices || from == to) return
        val item = photos.removeAt(from)
        photos.add(to, item)
        _state.update { it.copy(photos = photos) }
        viewModelScope.launch {
            runCatching { media.reorder(photos.map { it.id }) }
                .onSuccess { _state.update { it.copy(message = "Photo order saved.") } }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Couldn't reorder photos.") }
                    refresh()
                }
        }
    }

    fun deleteSample(sample: Sample) {
        viewModelScope.launch {
            runCatching { samples.delete(sample.id, null) }
                .onSuccess {
                    _state.update { it.copy(message = "Sample removed.") }
                    refresh()
                }
                .onFailure { e ->
                    _state.update { it.copy(error = e.message ?: "Couldn't delete sample.") }
                }
        }
    }
}
