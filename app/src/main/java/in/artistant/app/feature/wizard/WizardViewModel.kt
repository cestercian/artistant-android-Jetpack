package `in`.artistant.app.feature.wizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.ArtistGradient
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.HandleAvailability
import `in`.artistant.app.data.model.HandleRules
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.feature.epk.PackageRow
import `in`.artistant.app.feature.epk.packageDrafts
import `in`.artistant.app.feature.epk.previewPackages
import `in`.artistant.app.feature.epk.sanitizePriceInput
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.media.UploadQueue
import `in`.artistant.app.platform.media.WizardMediaCache
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
import kotlinx.coroutines.flow.drop
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
     * Pricing tiers as editor rows, not domain packages. Prices are Strings for
     * the same reason the EPK editor keeps them that way: a live text field has
     * a "cleared, not yet retyped" state that an Int cannot represent, and
     * coercing it to 0 mid-keystroke publishes a free gig.
     */
    val packageRows: List<PackageRow> = emptyList(),
    val techItems: Set<String> = emptySet(),
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
    }

    // ── Restore ──────────────────────────────────────────────────────────────

    /**
     * Seed the form, saved draft first.
     *
     * Order matters. The `users` row is the weaker source — it only knows the
     * signup name/handle/city — so it fills the blanks the draft left rather
     * than overwriting what the artist typed last session. Both are best-effort:
     * a failed read must leave an empty but usable form, never a blocked one.
     */
    private suspend fun restore() {
        val ownerId = session.currentUserId?.lowercase()
        // No session means no perspective to read the slot from, which is the
        // same "I cannot enumerate what is staged" position as a corrupt draft.
        val read = ownerId?.let { runCatching { draftStore.read(it) }.getOrNull() }
            ?: WizardDraftRead.Unclaimable
        val draft = (read as? WizardDraftRead.Mine)?.draft
        val profile = runCatching { users.fetchSelfProfile() }.getOrNull()

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
     */
    private fun sweepOrphanMedia(media: RestoredWizardMedia) {
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
        packageRows = draft.packages.map {
            PackageRow(it.key, it.name, it.duration, it.price, it.popular)
        },
        techItems = draft.techItems.toSet(),
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
     * character. 600ms is long enough that a burst of typing collapses to one
     * write and short enough that a task-kill mid-form loses at most a word.
     * `drop(1)` skips the initial empty state so an immediate kill can't
     * overwrite a good draft with a blank one before [restore] has landed.
     *
     * Done is excluded, and that exclusion is load-bearing rather than tidy.
     * Publish clears the draft and then moves the step, so a writer that still
     * fired on Done would land 600ms later and rewrite the draft it had just
     * deleted — with `step = Done` in it. The next launch would restore that and
     * open the wizard on the celebration screen for a profile the artist had not
     * published. Caught on device; the ordering is invisible from the code alone.
     */
    @OptIn(FlowPreview::class)
    private fun observeDraftWrites() {
        viewModelScope.launch {
            _state
                .filter { !it.isRestoring && it.step != WizardStep.Done }
                .drop(1)
                .debounce(600)
                .collect { snapshot ->
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
        packages = packageRows.map { DraftPackage(it.key, it.name, it.duration, it.price, it.popular) },
        techItems = techItems.toList(),
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

    fun toggleTechItem(item: String) =
        _state.update { it.copy(techItems = toggleInSet(item, it.techItems)) }

    fun onTechDraftChanged(value: String) = _state.update { it.copy(techDraft = value) }

    fun addTechItem() {
        val item = _state.value.techDraft.trim()
        if (item.isBlank()) return
        _state.update { it.copy(techItems = it.techItems + item, techDraft = "") }
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
                        publishError = null,
                    )
                }
            }.onFailure { e ->
                _state.update { it.copy(publishError = e.message ?: "Couldn't import that photo.") }
            }
        }
    }

    fun clearCoverPick() = _state.update { it.copy(pendingCover = null, pendingCoverPath = null) }

    // ── Samples ──────────────────────────────────────────────────────────────

    fun onSamplePicked(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            runCatching {
                val title = displayName?.substringBeforeLast('.')?.ifBlank { null } ?: "Sample"
                val pending = mediaCache.adoptAudio(uri, title)
                _state.update {
                    if (it.pendingSamples.size >= WIZARD_MAX_SAMPLES) it
                    else it.copy(pendingSamples = it.pendingSamples + pending, publishError = null)
                }
            }.onFailure { e ->
                _state.update { it.copy(publishError = e.message ?: "Couldn't import that audio.") }
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
            WizardStep.Done -> finishFromDone()
            else -> advanceWizardStep(current.step)?.let { nextStep ->
                _state.update { it.copy(step = nextStep, publishError = null) }
            }
        }
    }

    fun back() {
        backWizardStep(_state.value.step)?.let { prev ->
            _state.update { it.copy(step = prev, publishError = null) }
        }
    }

    /** Preview's per-section edit jump. Advancing re-walks the flow back here. */
    fun jumpTo(step: WizardStep) = _state.update { it.copy(step = step, publishError = null) }

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
     */
    fun saveAndExit() {
        viewModelScope.launch {
            session.currentUserId?.lowercase()?.let { ownerId ->
                runCatching { draftStore.save(_state.value.toDraft(ownerId)) }
            }
            runCatching { session.signOut() }
        }
    }

    // ── Publish ──────────────────────────────────────────────────────────────

    /**
     * Publish order, mirroring iOS:
     *  1. upsert the artist row (fast, no file transfer)
     *  2. replace packages + tech rider in parallel
     *  3. flip `published` — go-live is never gated on a media upload
     *  4. enqueue cover + samples for the background drain
     *
     * Step 3 sits before step 4 deliberately. Deferring the publish flag behind
     * the uploads means one bad file leaves an artist who tapped Publish, saw
     * the success screen, and is still invisible in Discover.
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
                    val tech = async { techRider.replaceAll(userId, snap.techItems.toList()) }
                    pkgs.await()
                    tech.await()
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
                        pendingCover = null,
                        pendingCoverPath = null,
                        pendingSamples = emptyList(),
                    )
                }
            } catch (e: AppError.UniqueViolation) {
                failPublish("That handle is already taken.")
            } catch (e: AppError) {
                failPublish(e.message ?: "Couldn't publish. Try again.")
            } catch (e: Exception) {
                failPublish(e.message ?: "Couldn't publish. Try again.")
            }
        }
    }

    private fun failPublish(message: String) = _state.update {
        it.copy(isPublishing = false, publishPhase = WizardPublishPhase.Idle, publishError = message)
    }
}
