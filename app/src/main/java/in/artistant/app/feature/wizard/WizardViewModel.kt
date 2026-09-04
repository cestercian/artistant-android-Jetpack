package `in`.artistant.app.feature.wizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.data.model.HandleRules
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.ArtistGradient
import `in`.artistant.app.domain.artist.ServiceTags
import `in`.artistant.app.feature.epk.PackageRow
import `in`.artistant.app.feature.epk.addTechItem
import `in`.artistant.app.feature.epk.packageDrafts
import `in`.artistant.app.feature.epk.previewPackages
import `in`.artistant.app.feature.epk.sampleTitleFrom
import `in`.artistant.app.feature.epk.sanitizePriceInput
import `in`.artistant.app.feature.epk.toggleTechItem
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.media.UploadQueue
import `in`.artistant.app.platform.media.WizardMediaCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class WizardUiState(
    val step: WizardStep = WizardStep.Identity,
    val stageName: String = "",
    val handle: String = "",
    val handleStatus: WizardHandleStatus = WizardHandleStatus.Empty,
    val category: String = "",
    val genre: String = "",
    val baseCity: String = "",
    /**
     * How far the artist will travel, and which occasions they take.
     *
     * Draft-only. `artists` has no radius column and this client has no writer
     * for `event_types`, so neither reaches the public profile — the location
     * step says so on screen rather than letting the artist believe otherwise.
     */
    val travelRadiusKm: Int = 0,
    val eventTypes: Set<String> = emptySet(),
    /** `artists.service_tags` slugs. Published, via `updateServiceTags`. */
    val serviceTags: List<String> = emptyList(),
    /**
     * Whether [serviceTags] STARTED as a copy of the published column rather
     * than as an empty list.
     *
     * `updateServiceTags` is whole-set — what it sends replaces what is stored —
     * and the wizard is re-enterable, so an artist can arrive here with services
     * already published from the press-kit editor or another client. Without
     * this flag the picker's list is a guess at that column, and publishing it
     * deletes every tag the wizard never showed. With it, an untick is an untick
     * and the list can be sent as it stands. See [wizardServiceTagsToPublish].
     */
    val serviceTagsHydrated: Boolean = false,
    /**
     * Pricing tiers as editor rows, not domain packages. Prices are Strings for
     * the same reason the EPK editor keeps them that way: a live text field has
     * a "cleared, not yet retyped" state that an Int cannot represent, and
     * coercing it to 0 mid-keystroke publishes a free gig.
     */
    val packageRows: List<PackageRow> = emptyList(),
    /**
     * The rider, in the order the artist built it — a List rather than a Set for
     * the same reason the EPK editor holds one: `tech_rider` stores a `position`
     * and a sound engineer reads the sheet top to bottom. Membership is
     * case-insensitive, which a Set cannot express, so add/toggle go through the
     * EPK's rules rather than through plain set arithmetic.
     */
    val techItems: List<String> = emptyList(),
    val techDraft: String = "",
    val daysAvailable: Set<String> = setOf("Fri", "Sat"),
    val timeSlots: Set<String> = setOf("7:30 PM", "9:00 PM"),
    val coverGradientIndex: Int = 0,
    /** Pending cover from a gallery/camera pick — uploaded after go-live. */
    val pendingCover: WizardMediaCache.PendingPhoto? = null,
    /**
     * Absolute path of [pendingCover] on disk, resolved here rather than in the
     * composable: the cache is a Hilt singleton the UI has no handle on, and
     * constructing a second one to answer "where is this file" would sever the
     * preview from the instance the upload queue actually drains.
     */
    val pendingCoverPath: String? = null,
    val pendingSamples: List<WizardMediaCache.PendingAudio> = emptyList(),
    val instagramHandle: String = "",
    val spotifyArtistUrl: String = "",
    val youtubeChannelUrl: String = "",
    val bio: String = "",
    val isPublishing: Boolean = false,
    val publishPhase: WizardPublishPhase = WizardPublishPhase.Idle,
    val publishError: String? = null,
    /**
     * A media step's own error line: an import that failed, or a camera the OS
     * refused to open.
     *
     * Separate from [publishError] because only the Preview step renders that
     * one, and every step change clears it — so a failed pick on the Cover or
     * Samples step wrote a message nothing would ever show, and the artist got
     * an empty preview with no explanation at all.
     */
    val mediaError: String? = null,
    /**
     * What the background upload queue is doing right now.
     *
     * Surfaced on the Samples step because the queue outlives the wizard: an
     * artist who published, had a sample fail, and was sent back in by a dropped
     * `setup_complete` write arrives at a step that already has work behind it.
     * The design's note is that the banner "reports state instead of hiding it",
     * and the only way to do that honestly is to read the queue rather than to
     * animate a bar of our own.
     */
    val uploads: UploadQueue.State = UploadQueue.State(),
    /** Set while the draft is being restored, so the form doesn't flash empty. */
    val isRestoring: Boolean = true,
) {
    val canAdvance: Boolean get() = wizardCanAdvance(this)

    /**
     * Read-only derivation for the preview step. Unsavable rows are excluded, so
     * a half-typed tier never flashes into the preview at a price the artist has
     * not finished typing.
     */
    val previewPackages: List<ArtistPackage> get() = previewPackages(packageRows)
}

sealed interface WizardEvent {
    data object Finished : WizardEvent

    /** The profile went live — every write landed. */
    data object Published : WizardEvent

    /** Publish failed; the Preview step is showing `publishError`. */
    data object PublishFailed : WizardEvent
}

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val session: SessionManager,
    private val users: UsersRepository,
    private val artists: ArtistsRepository,
    private val packages: PackagesRepository,
    private val techRider: TechRiderRepository,
    private val mediaCache: WizardMediaCache,
    private val uploadQueue: UploadQueue,
    private val draftStore: WizardDraftStore,
) : ViewModel() {

    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    private val _events = Channel<WizardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { restore() }
        observeHandle()
        observeDraftWrites()
        observeUploads()
    }

    /**
     * Mirror the upload queue into the form state.
     *
     * A mirror rather than a `collectAsState` in the composable, so the Samples
     * step reads one state object like every other step and the queue stays a
     * ViewModel-side dependency — the seam rule applies to a singleton with a
     * StateFlow exactly as it does to a repository.
     */
    private fun observeUploads() {
        viewModelScope.launch {
            uploadQueue.state.collect { snapshot -> _state.update { it.copy(uploads = snapshot) } }
        }
    }

    /** Re-arm everything the runner gave up on. Surfaced by the samples banner. */
    fun retryFailedUploads() = uploadQueue.retryFailed()

    // ── Restore ──────────────────────────────────────────────────────────────

    /**
     * Seed the form, saved draft first.
     *
     * Order matters. The `users` row is the weaker source — it only knows the
     * signup name/handle/city — so it fills the blanks the draft left rather
     * than overwriting what the artist typed last session. Both are best-effort:
     * a failed read must leave an empty but usable form, never a blocked one.
     *
     * The `artists` row is read for one reason: `service_tags` is a whole-set
     * column with an existing writer (the press-kit editor's chip group, which
     * reaches it through this same [ArtistsRepository.fetchArtist] path), so the
     * picker has to open on what is published or publishing it deletes the rest.
     * Concurrent with the profile read because they answer different questions
     * and the wizard is behind a spinner until both land.
     */
    private suspend fun restore() {
        val ownerId = session.currentUserId?.lowercase()
        // No session means no perspective to read the slot from, which is the
        // same "I cannot enumerate what is staged" position as a corrupt draft.
        val read = ownerId?.let { runCatching { draftStore.read(it) }.getOrNull() }
            ?: WizardDraftRead.Unclaimable
        val draft = (read as? WizardDraftRead.Mine)?.draft
        val (profile, publishedTags) = coroutineScope {
            val profileRead = async { runCatching { users.fetchSelfProfile() }.getOrNull() }
            // Success carrying null — no row yet — is the ordinary first-run
            // answer and still counts as "read": there is nothing to preserve.
            // Only a THROWN read leaves the published set unknown.
            val tagsRead = async {
                ownerId?.let {
                    runCatching { artists.fetchArtist(it)?.serviceTags.orEmpty() }.getOrNull()
                }
            }
            profileRead.await() to tagsRead.await()
        }

        // Staged media is resolved off the main thread: this stats one file per
        // reference, and the sweep below lists a directory.
        val media = withContext(Dispatchers.IO) {
            val resolved = draft?.let {
                restoredWizardMedia(
                    coverFileName = it.coverFileName,
                    samples = it.samples,
                    isOnDisk = mediaCache::exists,
                )
            } ?: RestoredWizardMedia(coverFileName = null, samples = emptyList())
            // Sweep only when the reference set is positively known. The cache is
            // a singleton whose files deliberately outlive sign-out, so it can
            // hold another artist's only copy of a photo; an Unclaimable read
            // means someone has media here that this session cannot enumerate,
            // and an empty reference set would read as "delete all of it".
            if (read !is WizardDraftRead.Unclaimable) runCatching { sweepOrphanMedia(resolved) }
            resolved
        }

        _state.update { current ->
            val restored = draft?.let { current.applyDraft(it, media) } ?: current
            val seeded = restored.copy(
                stageName = restored.stageName.ifBlank { profile?.fullName.orEmpty() },
                handle = restored.handle.ifBlank { profile?.handle.orEmpty() },
                baseCity = restored.baseCity.ifBlank { profile?.city.orEmpty() },
                // A draft saved before the artist reached pricing has a category
                // but no tiers. Seeding here as well as in `onCategorySelected`
                // means resuming never lands on an empty, gated pricing step with
                // no explanation of what it wants.
                packageRows = restored.packageRows.ifEmpty {
                    if (restored.category.isBlank()) emptyList() else starterPackageRows(restored.category)
                },
                // Seeded like the fields above rather than merged: a draft that
                // carries tags is one this artist already edited, and re-adding
                // a tag they unticked last session would undo a real decision.
                serviceTags = restored.serviceTags.ifEmpty {
                    ServiceTags.normalizeForDisplay(publishedTags.orEmpty())
                },
                serviceTagsHydrated = publishedTags != null,
                isRestoring = false,
            )
            seeded.copy(handleStatus = wizardHandleSyncStatus(seeded.handle))
        }
    }

    /**
     * Delete staged files nothing points at any more.
     *
     * The reference set is the restored draft **plus every file the upload queue
     * still holds**, and that second half is not optional: publish enqueues the
     * staged cover and samples and then clears the draft, so between publish and
     * the queue draining there is a window where the files are referenced only by
     * the queue. Sweeping on the draft alone would delete the artist's cover out
     * from under an upload that had already been told to send it.
     *
     * Failed tasks count as referenced too — they are retryable, and a retry
     * needs its file.
     *
     * Waits for the queue's snapshot read before reading it. That read is off the main
     * thread now, so `state.value` answers "nothing is queued" while it is still in
     * flight — indistinguishable here from a genuinely drained queue, and this is the
     * one caller for which the two have opposite consequences.
     */
    private suspend fun sweepOrphanMedia(media: RestoredWizardMedia) {
        uploadQueue.awaitRestore()
        val queued = uploadQueue.state.value.let { it.pending + it.failed }
            .mapNotNull { task ->
                when (task) {
                    is UploadQueue.Task.CoverPhoto -> task.filePath
                    is UploadQueue.Task.AudioSample -> task.filePath
                }
            }
            .map { it.substringAfterLast('/') }

        val referenced = buildSet {
            media.coverFileName?.let(::add)
            media.samples.forEach { add(it.fileName) }
            addAll(queued)
        }
        val orphans = orphanWizardMediaFiles(mediaCache.stagedFileNames(), referenced)
        if (orphans.isNotEmpty()) mediaCache.delete(orphans)
    }

    private fun WizardUiState.applyDraft(
        draft: WizardDraft,
        media: RestoredWizardMedia,
    ): WizardUiState = copy(
        // Never resume into Done. A draft is a form in progress; if one ever
        // names the celebration step — an older build, a write that raced the
        // publish — restoring it would show "You're live" to an artist who is
        // not. Preview is the honest place to land: everything they typed is
        // there and one tap publishes it for real.
        step = wizardResumeStep(draft.step),
        stageName = draft.stageName,
        handle = draft.handle,
        category = draft.category,
        genre = draft.genre,
        baseCity = draft.baseCity,
        travelRadiusKm = draft.travelRadiusKm,
        eventTypes = draft.eventTypes.toSet(),
        serviceTags = draft.serviceTags,
        packageRows = draft.packages.map {
            PackageRow(it.key, it.name, it.duration, it.price, it.popular)
        },
        techItems = draft.techItems,
        daysAvailable = draft.daysAvailable.toSet().ifEmpty { daysAvailable },
        timeSlots = draft.timeSlots.toSet().ifEmpty { timeSlots },
        coverGradientIndex = ArtistGradient.clampIndex(draft.coverGradientIndex),
        // Only files the sweep above confirmed are still on disk. The path is
        // re-resolved from the cache rather than stored, so it survives the app
        // moving between installs.
        pendingCover = media.coverFileName?.let { WizardMediaCache.PendingPhoto(it) },
        pendingCoverPath = media.coverFileName
            ?.let { WizardMediaCache.PendingPhoto(it).file(mediaCache).absolutePath },
        pendingSamples = media.samples.map {
            WizardMediaCache.PendingAudio(it.fileName, it.title, it.durationSeconds)
        },
        instagramHandle = draft.instagramHandle,
        spotifyArtistUrl = draft.spotifyArtistUrl,
        youtubeChannelUrl = draft.youtubeChannelUrl,
        bio = draft.bio,
    )

    // ── Draft persistence ────────────────────────────────────────────────────

    /**
     * Mirror the form into the draft store, debounced.
     *
     * Debounced rather than written per keystroke because a DataStore edit is a
     * file write plus a JSON encode, and a text field emits one state per typed
     * character.
     *
     * The pipeline itself lives in [wizardDraftWrites] — which snapshots are
     * worth writing, and in which order the filters sit relative to the debounce,
     * are decisions that were only ever visible on a device. They are covered by
     * a unit test now.
     */
    private fun observeDraftWrites() {
        viewModelScope.launch {
            wizardDraftWrites(_state).collect { snapshot ->
                val ownerId = session.currentUserId?.lowercase() ?: return@collect
                runCatching { draftStore.save(snapshot.toDraft(ownerId)) }
            }
        }
    }

    private fun WizardUiState.toDraft(ownerId: String) = WizardDraft(
        ownerId = ownerId,
        step = step.name,
        stageName = stageName,
        handle = handle,
        category = category,
        genre = genre,
        baseCity = baseCity,
        travelRadiusKm = travelRadiusKm,
        eventTypes = eventTypes.toList(),
        serviceTags = serviceTags,
        packages = packageRows.map { DraftPackage(it.key, it.name, it.duration, it.price, it.popular) },
        techItems = techItems,
        daysAvailable = daysAvailable.toList(),
        timeSlots = timeSlots.toList(),
        coverGradientIndex = coverGradientIndex,
        // Media refs travel in the draft too. The bytes were always safe — the
        // cache writes them to disk — but the *references* lived only here, so a
        // process kill left the photo on disk with nothing pointing at it and the
        // artist looking at an empty Cover step.
        coverFileName = pendingCover?.fileName.orEmpty(),
        samples = pendingSamples.map {
            DraftSample(it.fileName, it.title, it.durationSeconds)
        },
        instagramHandle = instagramHandle,
        spotifyArtistUrl = spotifyArtistUrl,
        youtubeChannelUrl = youtubeChannelUrl,
        bio = bio,
    )

    // ── Handle availability ──────────────────────────────────────────────────

    /**
     * Live handle check, mirroring the signup screen's contract.
     *
     * Format failures are rejected synchronously and never reach the RPC; only
     * well-formed handles are worth a round-trip. Both the pre- and post-call
     * guards compare against the live field because the artist keeps typing
     * while the request is in flight, and answering for a handle they have
     * already changed is how a stale "taken" pins a perfectly free name.
     */
    @OptIn(FlowPreview::class)
    private fun observeHandle() {
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
                                HandleAvailability.Available -> WizardHandleStatus.Available
                                HandleAvailability.Unavailable -> WizardHandleStatus.Taken
                                is HandleAvailability.Failure -> WizardHandleStatus.Error
                            },
                        )
                    }
                }
        }
    }

    // ── Identity ─────────────────────────────────────────────────────────────

    fun onStageNameChanged(value: String) = _state.update { it.copy(stageName = value) }

    fun onHandleChanged(raw: String) {
        val cleaned = sanitizeHandleInput(raw)
        _state.update { it.copy(handle = cleaned, handleStatus = wizardHandleSyncStatus(cleaned)) }
    }

    /**
     * Picking a category seeds the pricing tiers, but only while they are
     * untouched. Re-seeding over rows the artist has edited would silently
     * discard their prices the moment they went back to fix a typo in the
     * category chip.
     */
    fun onCategorySelected(value: String) = _state.update {
        val seeded = it.packageRows.isEmpty() || it.packageRows == starterPackageRows(it.category)
        it.copy(
            category = value,
            packageRows = if (seeded) starterPackageRows(value) else it.packageRows,
        )
    }

    fun onGenreChanged(value: String) = _state.update { it.copy(genre = value) }

    // ── Location ─────────────────────────────────────────────────────────────

    fun onBaseCitySelected(value: String) = _state.update { it.copy(baseCity = value) }

    fun onTravelRadiusSelected(km: Int) = _state.update { it.copy(travelRadiusKm = km) }

    fun toggleEventType(value: String) =
        _state.update { it.copy(eventTypes = toggleInSet(value, it.eventTypes)) }

    /**
     * Service tags go through [ServiceTags.toggle], not plain list arithmetic.
     *
     * That is where the six-tag cap lives, and where the refusal-at-the-boundary
     * rule lives with it: an over-cap tick returns the list unchanged, so what
     * the artist sees selected is exactly what publish writes.
     */
    fun toggleServiceTag(slug: String) =
        _state.update { it.copy(serviceTags = ServiceTags.toggle(it.serviceTags, slug)) }

    // ── Pricing ──────────────────────────────────────────────────────────────

    fun onPackageNameChanged(key: String, value: String) =
        updateRow(key) { it.copy(name = value) }

    fun onPackageDurationChanged(key: String, value: String) =
        updateRow(key) { it.copy(duration = value) }

    /** Digits only, capped — the field can never hold a value the save would reject. */
    fun onPackagePriceChanged(key: String, value: String) =
        updateRow(key) { it.copy(price = sanitizePriceInput(value)) }

    fun onPackagePopularToggled(key: String) = updateRow(key) { it.copy(popular = !it.popular) }

    fun addPackageRow() = _state.update {
        if (it.packageRows.size >= WIZARD_MAX_PACKAGES) {
            it
        } else {
            // `popular = false`. A new tier has not earned the badge, and
            // defaulting it true is what made the badge a constant app-wide.
            it.copy(packageRows = it.packageRows + PackageRow(key = "row-${UUID.randomUUID()}"))
        }
    }

    fun removePackageRow(key: String) =
        _state.update { it.copy(packageRows = it.packageRows.filterNot { row -> row.key == key }) }

    private fun updateRow(key: String, transform: (PackageRow) -> PackageRow) = _state.update { state ->
        state.copy(packageRows = state.packageRows.map { if (it.key == key) transform(it) else it })
    }

    // ── Tech rider ───────────────────────────────────────────────────────────

    /**
     * Both halves defer to the EPK's rider rules, which are case-insensitive.
     *
     * The wizard used to do exact-match set arithmetic, so an artist who typed
     * "4 Vocal Mics" with the "4 vocal mics" preset chip already selected
     * published the same line twice — on a document a venue has to act on. The
     * rule belongs in one place; the wizard and the EPK edit the same rider.
     */
    fun toggleTechItem(item: String) =
        _state.update { it.copy(techItems = toggleTechItem(it.techItems, item)) }

    fun onTechDraftChanged(value: String) = _state.update { it.copy(techDraft = value) }

    fun addTechItem() {
        val draft = _state.value.techDraft
        // The field clears either way: a refused duplicate means the line the
        // artist typed is already on the rider, so leaving it in the box to be
        // "fixed" would be asking for a change that has nothing to change.
        _state.update { it.copy(techItems = addTechItem(it.techItems, draft), techDraft = "") }
    }

    // ── Availability ─────────────────────────────────────────────────────────

    fun toggleDay(day: String) =
        _state.update { it.copy(daysAvailable = toggleInSet(day, it.daysAvailable)) }

    fun toggleTimeSlot(slot: String) =
        _state.update { it.copy(timeSlots = toggleInSet(slot, it.timeSlots)) }

    // ── Cover ────────────────────────────────────────────────────────────────

    fun onCoverGradientSelected(index: Int) =
        _state.update { it.copy(coverGradientIndex = ArtistGradient.clampIndex(index)) }

    fun onCoverPicked(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val pending = mediaCache.adoptPhoto(uri)
                _state.update {
                    it.copy(
                        pendingCover = pending,
                        pendingCoverPath = pending.file(mediaCache).absolutePath,
                        mediaError = null,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(mediaError = e.message ?: "Couldn't import that photo.") }
            }
        }
    }

    /**
     * The camera can't be opened — the permission was refused, and on Android 11+
     * a second refusal stops the system prompting at all.
     *
     * Said out loud because the alternative is what shipped: "Take photo" looking
     * identical to the working "Choose photo" beside it and doing nothing, for
     * good. The gallery is the recovery worth naming; Settings is the other one.
     */
    fun onCameraUnavailable() = _state.update {
        it.copy(mediaError = "Camera access is off — turn it on in Settings, or choose a photo instead.")
    }

    fun clearCoverPick() = _state.update { it.copy(pendingCover = null, pendingCoverPath = null) }

    // ── Samples ──────────────────────────────────────────────────────────────

    /**
     * The title comes from the provider's `DISPLAY_NAME`, never from
     * `Uri.lastPathSegment`.
     *
     * A SAF pick hands back a document URI whose last segment is a
     * provider-defined id — `audio:1000000042`, `primary:Music/demo.mp3` — and
     * that string was going straight into `samples.title` on the artist's public
     * profile. Reading the real name is a content-provider query, which is why
     * this runs on IO (`adoptAudio` makes the same hop for its file copy).
     */
    fun onSamplePicked(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    mediaCache.adoptAudio(uri, sampleTitleFrom(mediaCache.displayName(uri)))
                }
            }.onSuccess { pending ->
                _state.update {
                    if (it.pendingSamples.size >= WIZARD_MAX_SAMPLES) it
                    else it.copy(pendingSamples = it.pendingSamples + pending, mediaError = null)
                }
            }.onFailure { e ->
                _state.update { it.copy(mediaError = e.message ?: "Couldn't import that audio.") }
            }
        }
    }

    fun onSampleTitleChanged(fileName: String, title: String) = _state.update { state ->
        state.copy(
            pendingSamples = state.pendingSamples.map {
                if (it.fileName == fileName) it.copy(title = title) else it
            },
        )
    }

    fun removeSample(fileName: String) =
        _state.update { it.copy(pendingSamples = it.pendingSamples.filterNot { s -> s.fileName == fileName }) }

    // ── Socials / bio ────────────────────────────────────────────────────────

    fun onInstagramChanged(value: String) = _state.update { it.copy(instagramHandle = value) }
    fun onSpotifyChanged(value: String) = _state.update { it.copy(spotifyArtistUrl = value) }
    fun onYoutubeChanged(value: String) = _state.update { it.copy(youtubeChannelUrl = value) }
    fun onBioChanged(value: String) = _state.update { it.copy(bio = clampBio(value)) }

    // ── Navigation ───────────────────────────────────────────────────────────

    fun next() {
        val current = _state.value
        if (!current.canAdvance) return
        when (current.step) {
            WizardStep.Preview -> publish()
            // No Done arm: `wizardCanAdvance` refuses Done, so the guard above has
            // already returned. Done leaves through its own CTA, which calls
            // `finishFromDone` directly.
            else -> advanceWizardStep(current.step)?.let { nextStep ->
                _state.update { it.copy(step = nextStep, publishError = null, mediaError = null) }
            }
        }
    }

    fun back() {
        val current = _state.value
        if (!wizardMayChangeStep(current)) return
        backWizardStep(current.step)?.let { prev ->
            _state.update { it.copy(step = prev, publishError = null, mediaError = null) }
        }
    }

    /**
     * Preview's per-section edit jump. Advancing re-walks the flow back here.
     *
     * Refused while a publish is in flight — see [wizardMayChangeStep]. The
     * EDIT chips sit on the same screen as the "Publishing…" CTA and stayed
     * tappable through three round trips.
     */
    fun jumpTo(step: WizardStep) = _state.update {
        if (!wizardMayChangeStep(it)) it else it.copy(step = step, publishError = null, mediaError = null)
    }

    fun finishFromDone() {
        viewModelScope.launch { _events.send(WizardEvent.Finished) }
    }

    /**
     * Save & exit.
     *
     * The wizard is a mandatory gate — there is no screen behind it to return
     * to — so the only honest exit is to end the session. The draft is flushed
     * synchronously first, and it survives because it lives in its own store
     * (see [WizardDraftStore]); sign-out's `wipeAll` clears the shared one.
     * Signing back in as the same artist resumes at the same step.
     *
     * The write is skipped while the form is still restoring — see
     * [wizardExitMaySaveDraft]. The control is live during that window (the top
     * bar is not behind the spinner), and the state behind it is the blank
     * default, so writing it would destroy the very draft this button exists to
     * protect. Exiting still signs out: the artist asked to leave, and the saved
     * draft is already what they would come back to.
     */
    fun saveAndExit() {
        val snapshot = _state.value
        viewModelScope.launch {
            if (wizardExitMaySaveDraft(snapshot)) {
                session.currentUserId?.lowercase()?.let { ownerId ->
                    runCatching { draftStore.save(snapshot.toDraft(ownerId)) }
                }
            }
            runCatching { session.signOut() }
        }
    }

    // ── Publish ──────────────────────────────────────────────────────────────

    /**
     * Publish order, mirroring iOS:
     *  1. upsert the artist row (fast, no file transfer)
     *  2. replace packages + tech rider in parallel
     *  3. flip `published` + `setup_complete` — go-live is never gated on a media upload
     *  4. enqueue cover + samples for the background drain
     *
     * Step 3 sits before step 4 deliberately. Deferring the publish flag behind
     * the uploads means one bad file leaves an artist who tapped Publish, saw
     * the success screen, and is still invisible in Discover.
     *
     * The three calls are not atomic, so `setup_complete` is written by step 3
     * alone and never by step 1 (see [ArtistsRepository.setPublished]). A drop
     * between round trips then just sends the artist back into the wizard with
     * their draft intact — the alternative was a row marked finished but never
     * published, which nothing in the app can flip live afterwards.
     */
    private fun publish() {
        val userId = session.currentUserId?.lowercase() ?: run {
            _state.update { it.copy(publishError = "Sign in again to publish.") }
            return
        }
        val snap = _state.value
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isPublishing = true,
                    publishError = null,
                    publishPhase = WizardPublishPhase.SavingProfile,
                )
            }
            try {
                artists.publishWizardProfile(buildWizardProfileDraft(snap, userId))

                _state.update { it.copy(publishPhase = WizardPublishPhase.SavingDetails) }
                coroutineScope {
                    val pkgs = async { packages.replaceAll(userId, packageDrafts(snap.packageRows)) }
                    val tech = async { techRider.replaceAll(userId, snap.techItems) }
                    // Service tags are one column on the row the upsert above
                    // just wrote, but they are NOT part of that upsert: the
                    // wizard publish row is a fixed shape shared with the resume
                    // path, and widening it to carry an optional array would make
                    // "the artist skipped the bio step" and "the artist cleared
                    // their services" the same write. The dedicated setter is
                    // owner-guarded and whole-set, which is what this needs.
                    val tags = async { publishServiceTags(userId, snap) }
                    pkgs.await()
                    tech.await()
                    tags.await()
                }

                _state.update { it.copy(publishPhase = WizardPublishPhase.GoingLive) }
                artists.setPublished(userId, published = true)

                // Media backfill — best-effort, the artist is already live. The
                // queue owns the staged files from here.
                snap.pendingCover?.let { uploadQueue.enqueueCoverPhoto(userId, it.file(mediaCache)) }
                snap.pendingSamples.forEach { sample ->
                    uploadQueue.enqueueAudioSample(
                        artistId = userId,
                        file = sample.file(mediaCache),
                        title = sample.title,
                        durationSeconds = sample.durationSeconds,
                    )
                }

                // The draft has served its purpose; leaving it would resurrect a
                // stale form if the artist ever re-entered the wizard.
                runCatching { draftStore.clear() }

                _state.update {
                    it.copy(
                        isPublishing = false,
                        publishPhase = WizardPublishPhase.Idle,
                        step = WizardStep.Done,
                        publishError = null,
                        mediaError = null,
                        pendingCover = null,
                        pendingCoverPath = null,
                        pendingSamples = emptyList(),
                    )
                }
                _events.send(WizardEvent.Published)
            } catch (e: CancellationException) {
                // Structured concurrency: never swallow. A CancellationException
                // is an Exception, so without this arm clearing the wizard
                // mid-publish would land in the catch-all below and report the
                // cancellation to the artist as a publish failure.
                throw e
            } catch (e: AppError.UniqueViolation) {
                failPublish("That handle is already taken.")
            } catch (e: AppError) {
                failPublish(e.message ?: "Couldn't publish. Try again.")
            } catch (e: Exception) {
                failPublish(e.message ?: "Couldn't publish. Try again.")
            }
        }
    }

    /**
     * Write `artists.service_tags`, or decline to.
     *
     * The column is whole-set, so this is only ever safe from a set we have
     * seen. [WizardUiState.serviceTagsHydrated] says the picker opened on the
     * published array, which makes the local list a true edit of it. Without it
     * the picker opened empty, every tick is an ADDITION to a set nobody read,
     * and the row is re-read here for one more chance to merge rather than
     * replace — the upsert two steps up leaves `service_tags` alone, so what
     * comes back is still the artist's own array. If that read fails too,
     * nothing is written: losing this session's ticks is recoverable from the
     * press-kit editor, and deleting published services is not.
     */
    private suspend fun publishServiceTags(userId: String, snap: WizardUiState) {
        if (snap.serviceTags.isEmpty()) return
        // Re-read only in the case that needs it — a hydrated picker already
        // knows what it is replacing.
        val published = if (snap.serviceTagsHydrated) {
            null
        } else {
            runCatching { artists.fetchArtist(userId)?.serviceTags.orEmpty() }.getOrNull()
        }
        val tags = wizardServiceTagsToPublish(
            picked = snap.serviceTags,
            published = published,
            seeded = snap.serviceTagsHydrated,
        ) ?: return
        artists.updateServiceTags(userId, tags)
    }

    private fun failPublish(message: String) {
        _state.update {
            it.copy(isPublishing = false, publishPhase = WizardPublishPhase.Idle, publishError = message)
        }
        // An event, not a read off `publishError`: two consecutive failures with
        // the same message leave that field unchanged, and the second attempt is
        // the one the artist most needs acknowledged.
        viewModelScope.launch { _events.send(WizardEvent.PublishFailed) }
    }
}
