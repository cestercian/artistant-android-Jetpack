package `in`.artistant.app.feature.epk

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPrompt
import `in`.artistant.app.data.model.Sample
import `in`.artistant.app.data.repository.ArtistLink
import `in`.artistant.app.data.repository.ArtistLinksRepository
import `in`.artistant.app.data.repository.ArtistMediaItem
import `in`.artistant.app.data.repository.ArtistMediaKind
import `in`.artistant.app.data.repository.ArtistMediaRepository
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PackagesRepository
import `in`.artistant.app.data.repository.SamplesRepository
import `in`.artistant.app.data.repository.TechRiderRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.ArtistGradient
import `in`.artistant.app.domain.artist.ArtistPrompts
import `in`.artistant.app.domain.artist.ServiceTags
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.media.UploadQueue
import `in`.artistant.app.platform.media.WizardMediaCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** The link sheet's in-flight edit. `id == null` means "new link". */
data class LinkEditorState(
    val id: String? = null,
    val label: String = "",
    val url: String = "",
) {
    val isExisting: Boolean get() = id != null
}

data class EpkUiState(
    /**
     * Identity + the read-only halves of the profile (bio, socials, gradient).
     * Sourced from the artist row; see [loadIdentity] for why the mutable sets
     * deliberately do NOT come from here.
     *
     * Its **id is the owner every self-row write on this screen passes down**, and
     * it is the honest one to pass: [loadIdentity] read this row for the signed-in
     * artist and seeded the drafts from it, so it names the account each edit was
     * composed FOR. `session.currentUserId` re-read at save time would name
     * whoever is signed in when the write happens — which is the same thing right
     * up until it isn't, and [onCleared] flushes owed saves from a scope that
     * outlives both the screen and, potentially, the session.
     */
    val artist: Artist? = null,
    val setupComplete: Boolean = true,

    val photos: List<ArtistMediaItem> = emptyList(),
    val samples: List<Sample> = emptyList(),
    val packageRows: List<PackageRow> = emptyList(),
    val techItems: List<String> = emptyList(),
    val links: List<ArtistLink> = emptyList(),

    /**
     * One gate per whole-set-replace section. False until the section's own
     * server read succeeds — see [canReplaceWholeSet]. A section whose read
     * failed renders read-only rather than risking a replace from state that
     * never came from the server.
     */
    val packagesHydrated: Boolean = false,
    val techHydrated: Boolean = false,
    val photosHydrated: Boolean = false,
    /**
     * The artist row itself was read. Gates the edits that write ITS columns —
     * bio, cover palette, social links — for the same reason the three gates
     * above exist: those writes are only safe when the values on screen came from
     * the server. The social write is the sharp one, since it sends all three
     * links every time and an un-hydrated caller would unlink two of them.
     */
    val identityHydrated: Boolean = false,

    /**
     * The palette the artist just picked, before the write has been confirmed.
     * Null means "nothing picked this session, show what the row says". Kept
     * apart from the artist row rather than folded into it so a failed write can
     * fall back to the truth by clearing one field.
     */
    val coverGradientIndex: Int? = null,

    /**
     * The new-artist offer the artist just toggled, before the write confirms.
     * Null means "show what the row says". Held apart from the artist row for the
     * same reason [coverGradientIndex] is — one field to clear on failure.
     */
    val newArtistDiscountPct: Int? = null,

    /**
     * The services the artist has ticked, pending confirmation. Null means "show
     * what the row says" — the same one-field-to-clear shape the two pricing
     * modifiers use.
     *
     * Held as the whole set rather than as a diff because that is what the write
     * sends, so a failed write has exactly one thing to discard.
     */
    val serviceTags: List<String>? = null,

    /**
     * The weekend surcharge just picked, before the write confirms. Same
     * one-field-to-clear shape as [newArtistDiscountPct] — they are two halves of
     * the same "what modifies my price" idea and behave identically.
     */
    val weekendPremiumPct: Int? = null,

    /**
     * The prompt deck as it is being typed, held apart from `artist.prompts` —
     * which stays the last SAVED deck — so the debounce has something to diff
     * against, exactly like [bioDraft].
     *
     * Seeded only from a successful read; see [identityHydrated]. The write
     * replaces the whole array, so a save fired against a default-constructed
     * empty deck would erase every answer the artist wrote elsewhere.
     */
    val promptDrafts: List<ArtistPrompt> = emptyList(),

    /**
     * The bio as it is being typed. Held apart from `artist.bio` — which stays
     * the last value known to have been SAVED — so the debounce has something to
     * compare against and a re-entry to the screen can tell an unsaved edit from
     * a published one.
     */
    val bioDraft: String = "",

    /**
     * The three account fields as they are being typed, held apart from the
     * artist row for the same reason [bioDraft] is — the row stays the last
     * SAVED state, so the debounce has something to diff against.
     *
     * Whole-set by construction: the write sends all three, so the draft has to
     * carry all three, and it may only be seeded from a successful read. See
     * [identityHydrated] — that gate is what stops a save firing against the
     * default-constructed value and unlinking every account at once.
     */
    val socialDraft: SocialDraft = SocialDraft(),

    val techDraft: String = "",
    val linkEditor: LinkEditorState? = null,

    /**
     * Clips staged in [UploadQueue] that have no `samples` row yet.
     *
     * A count rather than a flag because it is counted toward [MAX_SAMPLES]:
     * [samples] only grows when the queue drains, seconds after the pick, so two
     * picks inside one upload both used to see room under the cap. See
     * [canAddSample] for why staged clips are counted rather than the Add
     * affordance being switched off while the queue works.
     */
    val samplesUploading: Int = 0,

    /**
     * Clips being copied into the media cache that have not reached the queue yet.
     *
     * [samplesUploading] can only see a clip once `enqueueAudioSample` has run,
     * and the copy before it — a whole file, plus a duration probe, plus a
     * provider name query, all off a SAF pick that may be cloud-backed — takes
     * seconds. In that window the clip was in neither number, so a second pick
     * saw the same room the first one did and the pair landed the artist over
     * [MAX_SAMPLES]. Counted separately from the queue's number because the
     * queue observer rewrites that one wholesale on every emission.
     */
    val samplesStaging: Int = 0,

    /**
     * What the upload queue is doing, in the press kit's words — design screens
     * 76 (working) and 66 (stalled). Null when the queue has nothing to report,
     * which is the absence of a banner rather than a banner saying so.
     *
     * Derived in [observeUploadQueue] rather than in the Composable so the
     * mapping is a pure function over queue state with a test, and so the screen
     * never has to hold a reference to the queue itself.
     */
    val uploadBanner: EpkUploadBanner? = null,

    /**
     * The burned tasks, one row each, for the stalled sheet (66).
     *
     * Carries the staged file's SIZE, which is a `stat` — measured on the IO
     * dispatcher in the queue observer, never in composition.
     */
    val stalledUploads: List<StalledUpload> = emptyList(),

    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val savingPackages: Boolean = false,
    val savingTech: Boolean = false,
    val savingBio: Boolean = false,
    val savingSocials: Boolean = false,
    val savingPrompts: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val busyLinks: Boolean = false,

    /** Initial hydrate failed — the section list is empty because of the network. */
    val loadError: String? = null,
    /** A WRITE failed. Dismissible, and never conflated with [loadError]. */
    val saveError: String? = null,
    /**
     * A staged upload — a clip, or the wizard's cover photo — exhausted the queue's
     * attempt budget. Carries the message rather than a flag, because which kind
     * stalled decides which section the artist should look at.
     *
     * Its own field rather than a [saveError], because it is the one failure on
     * this screen with somewhere to go: the file is still staged and the queue
     * can be told to drain it again, so the banner carries a Retry that
     * [saveError]'s dismiss-only banner has no room for.
     */
    val uploadFailedMessage: String? = null,
    /** Transient confirmation ("Pricing saved.") for writes with no visible result. */
    val statusNote: String? = null,
) {
    val anySaveInFlight: Boolean
        get() = savingPackages || savingTech || savingBio || savingSocials || savingPrompts
}

/**
 * The EPK editor's state machine.
 *
 * Three things shape this class and are worth stating up front, because each
 * one replaced something that was silently wrong:
 *
 * **1. Every mutable section reads from its OWN repository, not from the artist
 * row.** `ArtistsRepository.fetchArtist` is an id-keyed *cache* that returns the
 * hydrated entry without re-fetching, and nothing in the packages / tech / media
 * write paths invalidates it. Hydrating the editor from it meant every save was
 * followed by a refresh that handed back the pre-save list, so a saved change
 * appeared to revert until the process restarted. The artist row is now used
 * only for the parts that are not whole-set replaces — identity, plus the narrow
 * single-column writes (bio, socials, cover palette), which are safe from the
 * cache-staleness trap precisely because they send one field rather than
 * rebuilding a set from a possibly-stale read. Those still gate on
 * [EpkUiState.identityHydrated] so they never write a row nobody read.
 *
 * **2. Whole-set replaces are gated on a successful read.** Packages, the tech
 * rider and the photo order all persist by sending the complete list. The
 * previous editor sent `listOf(oneDraft)` from a single text field, which did
 * not "add a package" — it replaced every published tier with that one. The
 * gates ([EpkUiState.packagesHydrated] and friends) plus [canReplaceWholeSet]
 * make that shape impossible: nothing can be replaced until the real set has
 * been seen.
 *
 * **3. Failures are surfaced, never swallowed.** A load failure and a save
 * failure are different states with different affordances — one offers Retry,
 * the other says which edit did not land — so they are separate fields rather
 * than one `error: String?`.
 */
@HiltViewModel
class EpkViewModel @Inject constructor(
    private val session: SessionManager,
    private val users: UsersRepository,
    private val artists: ArtistsRepository,
    private val packages: PackagesRepository,
    private val techRider: TechRiderRepository,
    private val samples: SamplesRepository,
    private val links: ArtistLinksRepository,
    private val media: ArtistMediaRepository,
    private val mediaCache: WizardMediaCache,
    private val uploadQueue: UploadQueue,
) : ViewModel() {

    private val _state = MutableStateFlow(EpkUiState())
    val state: StateFlow<EpkUiState> = _state.asStateFlow()

    private var packagesSaveJob: Job? = null
    private var techSaveJob: Job? = null
    private var bioSaveJob: Job? = null
    private var socialsSaveJob: Job? = null
    private var promptsSaveJob: Job? = null

    /**
     * Which writes are armed: scheduled, and not yet run all the way through.
     *
     * Kept beside the jobs rather than derived from them because by the time
     * [onCleared] is asked, the framework has already cancelled `viewModelScope`
     * and every one of those jobs reads as inactive — a flush that trusted
     * `job.isActive` would always find nothing to do. Main-thread only, like the
     * jobs themselves.
     */
    private val armedSaves = mutableSetOf<EpkSave>()

    init {
        refresh()
        observeUploadQueue()
    }

    /**
     * Audio samples still go through [UploadQueue] (see [onSamplePicked]), which
     * drains in the background. This watches it for BOTH of the things it can do.
     *
     * **Completion** is what makes a finished upload appear without the artist
     * pulling to refresh. The counter is compared against the value already there
     * at collection time, so construction does not fire a redundant reload on top
     * of the one `init` already started.
     *
     * **Failure** is the half this used to miss entirely. A task that burns its
     * three attempts is moved to `failed` and deliberately does NOT bump
     * `batchCompleted`, so watching the counter alone meant a clip that died
     * against a dead connection produced no reload, no banner and no retry — the
     * artist's only signal was a sample that never appeared, and
     * `retryFailed()` had no caller anywhere in the app.
     *
     * It watches the whole `failed` list, not just the samples. The queue is shared
     * with the wizard, which puts the cover photo through it on publish: a cover
     * that burned its attempts on a flaky connection used to ship a live profile
     * with no cover, no error, and a staged file kept on disk for a retry nobody
     * could ask for. [failedUploadMessage] is what keeps the banner honest about
     * which of the two stalled.
     *
     * The message is acted on at its TRANSITIONS rather than read on every
     * emission: a dismissed banner then stays dismissed while the queue churns on
     * with `isRunning` flips, and a retry that succeeds clears it without anyone
     * having to remember to. It is seeded at null rather than at the current
     * message because a task that burned its budget in a PREVIOUS session is
     * restored straight into `failed` — that upload is precisely the one nobody has
     * ever been told about.
     */
    private fun observeUploadQueue() {
        viewModelScope.launch {
            var lastCompleted = uploadQueue.state.value.batchCompleted
            var lastFailed: String? = null
            uploadQueue.state.collect { queue ->
                if (queue.batchCompleted != lastCompleted) {
                    lastCompleted = queue.batchCompleted
                    loadSamples()
                }
                // What the cap counts on top of the stored rows. A task sits at the
                // head of `pending` while it uploads and only leaves once its row
                // exists, and this is updated AFTER the reload above, so a clip is
                // never missing from both numbers at once — which is the window the
                // cap used to be picked through.
                val staged = queue.pending.count { task -> task is UploadQueue.Task.AudioSample }
                // The banner the press kit draws, and the rows the stalled sheet
                // lists. Both are recomputed on every emission — unlike the
                // dismissible message below they are not something the artist can
                // wave away, they are a report of what the queue holds right now.
                //
                // The sizes are a `stat` per burned task, so the whole mapping
                // goes to IO: this collector runs on `viewModelScope`, i.e. the
                // main dispatcher.
                val stalled = withContext(Dispatchers.IO) { stalledRowsFor(queue) }
                _state.update {
                    it.copy(
                        samplesUploading = staged,
                        uploadBanner = uploadBannerFor(queue),
                        stalledUploads = stalled,
                    )
                }
                val failed = failedUploadMessage(queue.failed)
                if (failed != lastFailed) {
                    lastFailed = failed
                    _state.update { it.copy(uploadFailedMessage = failed) }
                }
            }
        }
    }

    // ── Loading ──────────────────────────────────────────────────────────────

    fun refresh() {
        viewModelScope.launch {
            val userId = session.currentUserId
            if (userId == null) {
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, loadError = "Sign in to edit your profile.")
                }
                return@launch
            }
            _state.update {
                it.copy(
                    isLoading = it.artist == null,
                    isRefreshing = it.artist != null,
                    loadError = null,
                )
            }
            // Concurrent because they are independent reads against different
            // tables: a slow samples query should not hold up the cover.
            listOf(
                async { loadIdentity(userId) },
                async { loadPackages(userId) },
                async { loadTech(userId) },
                async { loadSamples() },
                async { loadLinks(userId) },
                async { loadMedia(userId) },
            ).awaitAll()
            _state.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    private suspend fun loadIdentity(userId: String) {
        val profile = runCatching { users.fetchSelfProfile() }.getOrNull()
        runCatching { artists.fetchArtist(userId) }
            .onSuccess { artist ->
                _state.update {
                    // Refuse to overwrite typing. A pull-to-refresh landing between
                    // a keystroke and its debounced write would otherwise replace
                    // the half-written bio with the published one, which reads as
                    // the field erasing itself. Only adopt the server's copy when
                    // the artist has nothing outstanding — which is the common
                    // case, including every first load.
                    val pendingBio = bioNeedsSave(it.bioDraft, it.artist?.bio.orEmpty())
                    // Same refusal for the accounts, asked of all three at once
                    // because they save as one. On a first load both sides are
                    // empty, so this is false and the server's values are adopted —
                    // which is exactly what has to happen before the gate below
                    // lets anything write them back.
                    val pendingSocials = socialsNeedSave(it.socialDraft, savedSocials(it.artist))
                    // And the same for the deck, which is also typed prose.
                    val pendingPrompts = ArtistPrompts.needsSave(
                        it.promptDrafts,
                        it.artist?.prompts.orEmpty(),
                    )
                    it.copy(
                        artist = artist,
                        setupComplete = profile?.artistSetupComplete == true,
                        // A successful read is what opens the identity-column
                        // edits, and it also drops any optimistic palette from a
                        // previous session — the row is now the truth.
                        identityHydrated = artist != null,
                        coverGradientIndex = null,
                        newArtistDiscountPct = null,
                        serviceTags = null,
                        weekendPremiumPct = null,
                        bioDraft = if (pendingBio) it.bioDraft else artist?.bio.orEmpty(),
                        socialDraft = if (pendingSocials) it.socialDraft else savedSocials(artist),
                        promptDrafts =
                            if (pendingPrompts) it.promptDrafts else artist?.prompts.orEmpty(),
                    )
                }
            }
            .onFailure { failLoad() }
    }

    /**
     * Hydrate the pricing tiers and open their write gate.
     *
     * The gate opens on success ONLY. A failed read leaves the section
     * read-only, which is the correct trade: an artist who cannot edit their
     * pricing for one session has lost nothing, while an artist whose empty
     * local list replaced their published tiers has lost their whole price list
     * from a device that never saw it.
     */
    private suspend fun loadPackages(userId: String) {
        runCatching { packages.list(userId) }
            .onSuccess { rows ->
                _state.update { it.copy(packageRows = packageRows(rows), packagesHydrated = true) }
            }
            .onFailure { failLoad() }
    }

    private suspend fun loadTech(userId: String) {
        runCatching { techRider.list(userId) }
            .onSuccess { items -> _state.update { it.copy(techItems = items, techHydrated = true) } }
            .onFailure { failLoad() }
    }

    private suspend fun loadSamples() {
        val userId = session.currentUserId ?: return
        runCatching { samples.list(userId) }
            .onSuccess { rows -> _state.update { it.copy(samples = rows) } }
            .onFailure { failLoad() }
    }

    private suspend fun loadLinks(userId: String) {
        runCatching { links.list(userId) }
            .onSuccess { rows -> _state.update { it.copy(links = rows) } }
            .onFailure { failLoad() }
    }

    private suspend fun loadMedia(userId: String) {
        runCatching { media.list(userId).filter { it.kind == ArtistMediaKind.photo } }
            .onSuccess { rows -> _state.update { it.copy(photos = rows, photosHydrated = true) } }
            .onFailure { failLoad() }
    }

    /**
     * One shared message for every failed loader.
     *
     * Deliberately not per-section: six simultaneous reads failing on one dead
     * connection would otherwise stack six identical banners, and the artist's
     * action is the same in every case — retry when the network is back.
     */
    private fun failLoad() {
        _state.update {
            it.copy(loadError = "Couldn't load your profile — check your connection and retry.")
        }
    }

    fun dismissSaveError() = _state.update { it.copy(saveError = null) }

    fun consumeStatusNote() = _state.update { it.copy(statusNote = null) }

    /**
     * Send everything the queue gave up on back round.
     *
     * The queue parks a burned task with its attempt budget spent and waits to be
     * asked; until this existed, nothing in the app ever asked, so an upload that
     * failed three times was stranded for good with the staged file still on
     * disk. It retries the whole `failed` list — a clip and the wizard's cover
     * photo stall for the same reason, and the artist tapping Retry means "send
     * what didn't send", not "send the audio only". The banner is cleared here
     * rather than waiting for the queue to report `failed` empty, so the tap has a
     * visible effect even while the drain is still starting.
     */
    fun retryFailedUploads() {
        _state.update { it.copy(uploadFailedMessage = null, statusNote = "Retrying upload…") }
        uploadQueue.retryFailed()
    }

    fun dismissUploadError() = _state.update { it.copy(uploadFailedMessage = null) }

    /**
     * Send ONE burned upload back round — the stalled sheet's per-item Retry (66).
     *
     * Per-item first, bulk second, because two uploads rarely stall for the same
     * reason: an oversized clip beside a cover that hit a dead cell. "Retry all"
     * on its own spends the drain on the one that is going to fail again.
     */
    fun retryStalledUpload(taskId: String) {
        _state.update { it.copy(statusNote = "Retrying upload…") }
        uploadQueue.retryFailed(taskId)
    }

    /**
     * Forget one burned upload and its staged bytes (66's Discard).
     *
     * No confirmation dialog. The thing being discarded is a copy the app made of
     * a file the artist still has, of an upload that has already failed three
     * times — the cost of a mis-tap is picking it again, and a modal in front of
     * every row would make clearing a stuck queue a six-tap job.
     */
    fun discardStalledUpload(taskId: String) {
        uploadQueue.discardFailed(taskId)
        _state.update { it.copy(statusNote = "Upload discarded.") }
    }

    // ── Cover palette ────────────────────────────────────────────────────────

    /**
     * Pick a fallback palette, optimistically.
     *
     * The preview switches on the tap and the write follows, because the tap's
     * entire purpose is seeing the new colour — waiting for a round-trip would
     * make the control feel broken on a slow connection. A failure clears the
     * optimistic value so the preview snaps back to what is actually published
     * rather than lying about a choice that did not land.
     *
     * Not debounced: this is one tap producing one decision, not a text field
     * producing a keystroke per character, and the six-swatch row is small enough
     * that a burst is a handful of writes at most.
     */
    fun onCoverGradientPicked(index: Int) {
        val current = _state.value
        val clamped = coverGradientPickToWrite(
            hydrated = current.identityHydrated,
            pending = current.coverGradientIndex,
            published = current.artist?.coverGradientIndex ?: 0,
            requested = index,
        ) ?: return
        val owner = current.artist?.id ?: return
        _state.update { it.copy(coverGradientIndex = clamped, saveError = null) }
        viewModelScope.launch {
            runCatching { artists.updateCoverGradient(owner, clamped) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            statusNote = "Cover saved.",
                            // Folded into the row like every other optimistic
                            // control here. Without it the saved palette lived
                            // ONLY in the pending index, which [loadIdentity]
                            // clears on every successful read — so a refresh
                            // landing before the artist cache is invalidated
                            // snapped the ring back to the old colour and left
                            // "Cover saved." sitting over the palette that was
                            // not saved. The resolved colours travel with the
                            // index because [Artist] carries both.
                            artist = it.artist?.copy(
                                coverGradientIndex = clamped,
                                gradient = ArtistGradient.palette(clamped),
                            ),
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            coverGradientIndex = null,
                            saveError = "Couldn't save your cover — check your connection and try again.",
                        )
                    }
                }
        }
    }

    // ── New-artist offer ─────────────────────────────────────────────────────

    /**
     * Switch the public "N% off first bookings" line on or off.
     *
     * Optimistic and undebounced, like the palette: one tap is one decision, and
     * the artist is entitled to see a promise made on their own profile change
     * the moment they change it. A failure clears the optimistic value so the
     * control snaps back to what clients are actually being shown — the one thing
     * that must never be misreported here, since the artist honours this in their
     * quote.
     */
    fun onNewArtistOfferToggled() {
        val current = _state.value
        if (!current.identityHydrated) return
        val shown = shownNewArtistDiscount(
            current.newArtistDiscountPct,
            current.artist?.newArtistDiscountPct ?: 0,
        )
        val target = newArtistDiscountToggleTarget(shown)
        val owner = current.artist?.id ?: return
        _state.update { it.copy(newArtistDiscountPct = target, saveError = null) }
        viewModelScope.launch {
            runCatching { artists.updateNewArtistDiscount(owner, target) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            statusNote = if (target > 0) "Offer on." else "Offer off.",
                            artist = it.artist?.copy(newArtistDiscountPct = target),
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            newArtistDiscountPct = null,
                            saveError = "Couldn't change your new-artist offer — check your connection and try again.",
                        )
                    }
                }
        }
    }

    // ── Weekend premium ──────────────────────────────────────────────────────

    /**
     * Step the Fri–Sun surcharge.
     *
     * Deliberately the SAME shape as [onNewArtistOfferToggled] — optimistic,
     * undebounced, gated on hydration, failure clears the pending value — because
     * they are the two modifiers a client sees applied to the same price, and a
     * pair of controls that behaved differently would imply a difference that is
     * not there.
     *
     * Where they differ is the range, and the column is why: a discount is a
     * switch (on, or withdrawn), while a premium is a judgement about how much
     * more a Saturday is worth. So this cycles the steps in [WEEKEND_PREMIUM_STEPS]
     * rather than flipping. One tap is still one decision, which keeps it inside
     * the same optimistic contract.
     */
    fun onWeekendPremiumStepped() {
        val current = _state.value
        if (!current.identityHydrated) return
        val shown = shownWeekendPremium(
            current.weekendPremiumPct,
            current.artist?.weekendPremiumPct ?: 0,
        )
        val target = weekendPremiumStepTarget(shown)
        val owner = current.artist?.id ?: return
        _state.update { it.copy(weekendPremiumPct = target, saveError = null) }
        viewModelScope.launch {
            runCatching { artists.updateWeekendPremium(owner, target) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            statusNote = if (target > 0) "Weekend premium on." else "Weekend premium off.",
                            artist = it.artist?.copy(weekendPremiumPct = target),
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            weekendPremiumPct = null,
                            saveError = "Couldn't change your weekend premium — check your connection and try again.",
                        )
                    }
                }
        }
    }

    // ── Prompt deck ──────────────────────────────────────────────────────────

    /**
     * Type an answer. Clamped on the way in so a paste truncates visibly rather
     * than being bounced by the column.
     */
    fun onPromptAnswerChanged(question: String, value: String) {
        _state.update {
            it.copy(
                promptDrafts = ArtistPrompts.upsert(
                    it.promptDrafts,
                    question,
                    ArtistPrompts.clampAnswerInput(value),
                ),
            )
        }
        schedulePromptsSave()
    }

    /**
     * Debounced whole-deck write.
     *
     * Debounced because these are prose fields — one write per character would be
     * wasteful and lets an out-of-order response land an older prefix last, the
     * same reasoning as the bio.
     *
     * Gated on [EpkUiState.identityHydrated] because `updatePrompts` replaces the
     * whole array: a save fired before the read seeded [EpkUiState.promptDrafts]
     * would send a one-entry deck over an artist who had answered all four
     * elsewhere. The UI disables the fields until the same flag is true, so this
     * is the second of two locks — kept because the flag can flip on a background
     * refresh and a guard that lives only in a Composable is not a guard the next
     * caller of this method inherits.
     */
    private fun schedulePromptsSave(immediate: Boolean = false) {
        if (!_state.value.identityHydrated || session.currentUserId == null) return
        promptsSaveJob?.cancel()
        promptsSaveJob = arm(EpkSave.Prompts, debounce = !immediate) { persistPrompts() }
    }

    private suspend fun persistPrompts() {
        val draft = _state.value.promptDrafts
        if (!ArtistPrompts.needsSave(draft, _state.value.artist?.prompts.orEmpty())) {
            // Also where "the edit that cancelled an in-flight save turned out to
            // be a no-op" lands. That save rethrew its cancellation without
            // touching the flag (it belongs to whoever cancelled it), so this is
            // what puts "Saving…" away when nobody takes it over.
            _state.update { it.copy(savingPrompts = false) }
            return
        }
        // The row these answers were typed against — see [EpkUiState.artist]. Read
        // before the flag goes up so a persist with nothing to aim at cannot leave
        // "Saving…" on screen.
        val owner = _state.value.artist?.id ?: return
        _state.update { it.copy(savingPrompts = true) }
        saveCatching { artists.updatePrompts(owner, draft) }
            .onSuccess {
                _state.update {
                    it.copy(
                        savingPrompts = false,
                        saveError = null,
                        statusNote = "Answers saved.",
                        // Folded in exactly as the server stores it — blanks
                        // dropped, answers clamped — so the next diff compares
                        // like with like. Fold the raw draft instead and every
                        // later keystroke re-sends the whole deck.
                        artist = it.artist?.copy(
                            prompts = ArtistPrompts.decode(ArtistPrompts.encode(draft)),
                        ),
                    )
                }
            }
            .onFailure {
                _state.update {
                    it.copy(
                        savingPrompts = false,
                        // Draft left alone, like the bio's: these are the artist's
                        // own words and a failed write must not be resolved by
                        // discarding them.
                        saveError = "Couldn't save your answers — check your connection and edit again to retry.",
                    )
                }
            }
    }

    // ── Services offered ─────────────────────────────────────────────────────

    /**
     * Tick or untick one service, optimistically.
     *
     * **Gated on [EpkUiState.identityHydrated] for the same reason the accounts
     * write is.** `updateServiceTags` sends the complete array, so a tap that
     * fired before [loadIdentity] returned would send a one-element list built on
     * top of an empty local set — publishing "I only do DJ sets" over an artist
     * who had ticked five services on another device. The gate is what makes the
     * local set a copy of the server's rather than a guess at it.
     *
     * Undebounced like the palette and the offer: a chip tap is one decision, and
     * the nine-chip group cannot produce a burst worth coalescing. A failure
     * clears the pending set so the chips snap back to what is actually
     * published — which here also decides whether clients can find this artist at
     * all, since the same slugs back Discover's services filter.
     */
    fun onServiceTagToggled(slug: String) {
        val current = _state.value
        if (!current.identityHydrated) return
        val shown = effectiveServiceTags(current)
        val next = ServiceTags.toggle(shown, slug)
        // At the cap, toggling ON is refused rather than silently truncated. Say
        // so — a chip that does not light up on tap reads as a broken control.
        if (next == shown) {
            _state.update {
                it.copy(saveError = "You can list up to ${ServiceTags.MAX_TAGS} services — untick one to add another.")
            }
            return
        }
        val owner = current.artist?.id ?: return
        _state.update { it.copy(serviceTags = next, saveError = null) }
        persistServiceTags(owner, next, note = "Services saved.")
    }

    /** What the chips currently show: the local edit if there is one, else the row. */
    private fun effectiveServiceTags(state: EpkUiState): List<String> =
        shownServiceTags(state.serviceTags, state.artist?.serviceTags.orEmpty())

    /** Publish a service set and reconcile the cached row with the result. */
    private fun persistServiceTags(owner: String, tags: List<String>, note: String) {
        viewModelScope.launch {
            runCatching { artists.updateServiceTags(owner, tags) }
                .onSuccess {
                    _state.update {
                        it.copy(statusNote = note, artist = it.artist?.copy(serviceTags = tags))
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            serviceTags = null,
                            saveError = "Couldn't save what you offer — check your connection and try again.",
                        )
                    }
                }
        }
    }

    // ── Sheet edits are transactions ─────────────────────────────────────────

    // The bio and personality sheets (design 67, 68) each carry a Cancel/Skip,
    // and a Cancel that only closes the sheet is a lie on this screen: every
    // field in them edits shared draft state that autosaves 1.2s later, and the
    // service chips beside the bio wrote on the tap. Typing a new bio and
    // tapping Cancel published the new bio. See [epkEditRevert].
    private var editSnapshot: EpkEditSnapshot? = null

    /** Open a sheet: remember what Cancel has to be able to restore. */
    fun beginSheetEdit() {
        val current = _state.value
        editSnapshot = EpkEditSnapshot(
            bio = current.bioDraft,
            services = effectiveServiceTags(current),
            prompts = current.promptDrafts,
        )
    }

    /**
     * Save: the edits stand, so the snapshot is dropped and everything owed goes
     * out now rather than waiting out a debounce the artist has stopped watching.
     */
    fun commitSheetEdit() {
        editSnapshot = null
        flushPendingSaves()
    }

    /**
     * Cancel / Skip: put the values back and undo whatever already left.
     *
     * [epkEditRevert] decides what changed and therefore what has to be written
     * back; this applies it. Disarming comes first — nothing still waiting may
     * fire the values being discarded — then the local restore, then the
     * write-backs for the fields that differ.
     */
    fun cancelSheetEdit() {
        val snap = editSnapshot ?: return
        editSnapshot = null
        val current = _state.value
        val revert = epkEditRevert(
            snapshot = snap,
            bio = current.bioDraft,
            services = effectiveServiceTags(current),
            prompts = current.promptDrafts,
        )
        if (revert.isEmpty) return

        bioSaveJob?.cancel()
        promptsSaveJob?.cancel()
        armedSaves -= EpkSave.Bio
        armedSaves -= EpkSave.Prompts

        _state.update {
            it.copy(
                bioDraft = snap.bio,
                promptDrafts = snap.prompts,
                serviceTags = snap.services,
                saveError = null,
            )
        }

        revert.bio?.let { scheduleBioSave(immediate = true) }
        revert.prompts?.let { schedulePromptsSave(immediate = true) }
        // Service chips never had a debounce to cancel — they wrote on the tap —
        // so undoing them is only ever the write-back.
        val owner = current.artist?.id
        if (revert.services != null && owner != null) {
            persistServiceTags(owner, snap.services, note = "Changes discarded.")
        }
    }

    // ── Bio ──────────────────────────────────────────────────────────────────

    /**
     * Type into the bio.
     *
     * Clamped on the way in so a pasted essay truncates where the artist can see
     * it, rather than being accepted, sent, and rejected by the column.
     */
    fun onBioChanged(value: String) {
        _state.update { it.copy(bioDraft = clampBioInput(value)) }
        scheduleBioSave()
    }

    /**
     * Debounced single-column write.
     *
     * Debounced for the reason the pricing set is: a bio typed at speed is one
     * state change per character, and a PATCH per character is both wasteful and
     * a way for an out-of-order response to land an older prefix last.
     *
     * Unlike pricing this is NOT a whole-set replace, so it gates on
     * [EpkUiState.identityHydrated] rather than [canReplaceWholeSet] — there is no
     * set to wipe, but there is still a row we must have read before writing it.
     */
    private fun scheduleBioSave(immediate: Boolean = false) {
        if (!_state.value.identityHydrated || session.currentUserId == null) return
        bioSaveJob?.cancel()
        bioSaveJob = arm(EpkSave.Bio, debounce = !immediate) { persistBio() }
    }

    private suspend fun persistBio() {
        val draft = _state.value.bioDraft
        // Nothing to say to the server: the debounce fires on any keystroke,
        // including the ones that type a character and delete it again. Clearing
        // the flag here is what stops "Saving…" sticking when the keystroke that
        // cancelled an in-flight save was one of those — see [persistPrompts].
        if (!bioNeedsSave(draft, _state.value.artist?.bio.orEmpty())) {
            _state.update { it.copy(savingBio = false) }
            return
        }
        // The row this bio was typed against — see [EpkUiState.artist].
        val owner = _state.value.artist?.id ?: return
        _state.update { it.copy(savingBio = true) }
        saveCatching { artists.updateBio(owner, draft) }
            .onSuccess {
                _state.update {
                    it.copy(
                        savingBio = false,
                        saveError = null,
                        statusNote = "Bio saved.",
                        // Fold the saved value into the artist row so the next
                        // debounce has an accurate "already saved" to compare
                        // against. Without this every later keystroke re-sends the
                        // whole bio, because the row still holds the pre-save copy.
                        artist = it.artist?.copy(bio = draft.trim()),
                    )
                }
            }
            .onFailure {
                _state.update {
                    it.copy(
                        savingBio = false,
                        // The draft is deliberately left alone. The artist's words
                        // are the one thing here that cannot be re-derived, so a
                        // failed write must never be resolved by discarding them.
                        saveError = "Couldn't save your bio — check your connection and edit again to retry.",
                    )
                }
            }
    }

    // ── Connected accounts ───────────────────────────────────────────────────

    /**
     * The row's social columns as a draft. One place that knows the mapping, so
     * the seeding, the "has it changed" diff and the post-save fold cannot drift
     * apart into three subtly different ideas of what is currently published.
     */
    private fun savedSocials(artist: Artist?): SocialDraft = socialDraftOf(
        spotify = artist?.spotifyArtistUrl,
        instagram = artist?.instagramHandle,
        youtube = artist?.youtubeChannelUrl,
    )

    fun onSocialChanged(platform: SocialPlatform, value: String) {
        _state.update { it.copy(socialDraft = it.socialDraft.with(platform, value)) }
        scheduleSocialsSave()
    }

    /**
     * Debounced write of all three account fields.
     *
     * **The hydration gate is load-bearing here in a way it is nowhere else on
     * this screen.** `updateSocialLinks` sends every one of the three columns on
     * every call, so it behaves like a whole-set replace even though it is a
     * single-row PATCH: a save that fired before [loadIdentity] seeded the draft
     * would send three empty strings, and the repository turns blank into NULL —
     * unlinking the artist's Spotify, Instagram and YouTube in one request, from
     * a device that never read them.
     *
     * Editing is disabled in the UI until the same flag is true, so this guard is
     * the second of two locks rather than the only one. It stays anyway: the flag
     * flips on a background refresh, and a guard that exists only in a Composable
     * is a guard the next caller of this method will not have.
     */
    private fun scheduleSocialsSave(immediate: Boolean = false) {
        if (!_state.value.identityHydrated || session.currentUserId == null) return
        socialsSaveJob?.cancel()
        socialsSaveJob = arm(EpkSave.Socials, debounce = !immediate) { persistSocials() }
    }

    private suspend fun persistSocials() {
        val draft = _state.value.socialDraft
        if (!socialsNeedSave(draft, savedSocials(_state.value.artist))) {
            // Clears a flag a cancelled save left behind — see [persistPrompts].
            _state.update { it.copy(savingSocials = false) }
            return
        }
        // The row these three links were read from and edited against — see
        // [EpkUiState.artist]. Sharpest of the three here: this write replaces all
        // three columns, so landing it on the wrong account both overwrites their
        // links and publishes this artist's.
        val owner = _state.value.artist?.id ?: return
        _state.update { it.copy(savingSocials = true) }
        saveCatching {
            // Named, not positional. The parameter order here is
            // (instagram, spotify, youtube) while the draft and the count helper
            // read (spotify, instagram, youtube) — positionally this compiles
            // fine and silently files an artist's Spotify URL as their Instagram
            // handle.
            artists.updateSocialLinks(
                expectedOwner = owner,
                instagram = draft.instagram,
                spotify = draft.spotify,
                youtube = draft.youtube,
            )
        }
            .onSuccess {
                _state.update {
                    it.copy(
                        savingSocials = false,
                        saveError = null,
                        statusNote = "Accounts saved.",
                        // Folded in exactly as the repository stores it — trimmed,
                        // blank as NULL — so the next diff compares like with like.
                        // Fold it any other way and every later keystroke re-sends
                        // all three because the row never matches the draft.
                        artist = it.artist?.copy(
                            spotifyArtistUrl = draft.spotify.trim().ifBlank { null },
                            instagramHandle = draft.instagram.trim().ifBlank { null },
                            youtubeChannelUrl = draft.youtube.trim().ifBlank { null },
                        ),
                    )
                }
            }
            .onFailure {
                _state.update {
                    it.copy(
                        savingSocials = false,
                        // Draft left alone, like the bio's: the artist pasted these
                        // from three other apps, and discarding them on a failed
                        // write means going and fetching them again.
                        saveError = "Couldn't save your accounts — check your connection and edit again to retry.",
                    )
                }
            }
    }

    // ── Pricing tiers ────────────────────────────────────────────────────────

    fun addPackageRow() {
        // The key is generated here rather than inside `PackageRow` so the model
        // stays a plain value type and the logic layer stays deterministic.
        _state.update { it.copy(packageRows = it.packageRows + PackageRow(key = UUID.randomUUID().toString())) }
        // No save yet: an empty row is not savable, and firing the debounce now
        // would just schedule a write that drops the row it was scheduled for.
    }

    fun onPackageName(key: String, value: String) = editPackage(key) { it.copy(name = value) }

    fun onPackageDuration(key: String, value: String) = editPackage(key) { it.copy(duration = value) }

    fun onPackagePrice(key: String, value: String) =
        editPackage(key) { it.copy(price = sanitizePriceInput(value)) }

    fun onPackagePopular(key: String, value: Boolean) = editPackage(key) { it.copy(popular = value) }

    fun removePackageRow(key: String) {
        _state.update { it.copy(packageRows = it.packageRows.filterNot { row -> row.key == key }) }
        // A deletion is a decision, not a keystroke — persist it now rather than
        // leaving a removed tier alive on the server for another second and a
        // half, which is long enough to background the app.
        schedulePackagesSave(immediate = true)
    }

    private fun editPackage(key: String, transform: (PackageRow) -> PackageRow) {
        _state.update { st ->
            st.copy(packageRows = st.packageRows.map { if (it.key == key) transform(it) else it })
        }
        schedulePackagesSave()
    }

    /**
     * Debounced whole-set write.
     *
     * Debounced because the inputs are text fields: a five-digit price typed at
     * speed is five state changes, and five `replace_packages` round-trips for
     * one edit is both slow and a way to have the server briefly hold ₹5 while
     * the artist is typing ₹50,000.
     *
     * [immediate] skips the wait for structural edits (add / remove / toggle),
     * which arrive one at a time and carry no half-typed intermediate states.
     */
    private fun schedulePackagesSave(immediate: Boolean = false) {
        if (!canReplaceWholeSet(_state.value.packagesHydrated, session.currentUserId != null)) return
        packagesSaveJob?.cancel()
        packagesSaveJob = arm(EpkSave.Packages, debounce = !immediate) { persistPackages() }
    }

    private suspend fun persistPackages() {
        // The row these tiers were composed against — see [EpkUiState.artist] — is
        // the owner this replace is aimed at, the same compose-time identity the
        // narrow patch writes pass. Read before the flag goes up so a persist with
        // nothing to aim at cannot leave "Saving…" on screen, and passed to the
        // seam so a replace flushed from [onCleared] after an account switch is
        // refused there rather than landing on the new user's row.
        val owner = _state.value.artist?.id ?: return
        val drafts = packageDrafts(_state.value.packageRows)
        _state.update { it.copy(savingPackages = true) }
        saveCatching { packages.replaceAll(owner, drafts) }
            .onSuccess {
                _state.update {
                    it.copy(savingPackages = false, saveError = null, statusNote = "Pricing saved.")
                }
            }
            .onFailure {
                _state.update {
                    it.copy(
                        savingPackages = false,
                        // Pricing is a money field. A swallowed failure leaves the
                        // artist looking at a price clients cannot book at.
                        saveError = "Couldn't save your pricing — check your connection and edit again to retry.",
                    )
                }
            }
    }

    // ── Tech rider ───────────────────────────────────────────────────────────

    fun onTechDraft(value: String) = _state.update { it.copy(techDraft = value) }

    fun toggleTechPreset(item: String) {
        _state.update { it.copy(techItems = toggleTechItem(it.techItems, item)) }
        scheduleTechSave(immediate = true)
    }

    fun addTechDraft() {
        val draft = _state.value.techDraft
        _state.update { it.copy(techItems = addTechItem(it.techItems, draft), techDraft = "") }
        scheduleTechSave(immediate = true)
    }

    private fun scheduleTechSave(immediate: Boolean = false) {
        if (!canReplaceWholeSet(_state.value.techHydrated, session.currentUserId != null)) return
        techSaveJob?.cancel()
        techSaveJob = arm(EpkSave.Tech, debounce = !immediate) { persistTech() }
    }

    private suspend fun persistTech() {
        // The row this rider was composed against — see [EpkUiState.artist] — and
        // the owner the replace is aimed at, matching [persistPackages] and the
        // narrow patch writes. Read before the flag so a persist with nothing to
        // aim at cannot strand "Saving…".
        val owner = _state.value.artist?.id ?: return
        val items = _state.value.techItems
        _state.update { it.copy(savingTech = true) }
        saveCatching { techRider.replaceAll(owner, items) }
            .onSuccess {
                _state.update { it.copy(savingTech = false, saveError = null, statusNote = "Tech rider saved.") }
            }
            .onFailure {
                _state.update {
                    it.copy(
                        savingTech = false,
                        saveError = "Couldn't save your tech rider — check your connection and try again.",
                    )
                }
            }
    }

    // ── Links ────────────────────────────────────────────────────────────────

    fun openLinkEditor(link: ArtistLink? = null) {
        _state.update {
            it.copy(
                linkEditor = if (link == null) {
                    LinkEditorState()
                } else {
                    LinkEditorState(id = link.id, label = link.label, url = link.url)
                },
            )
        }
    }

    fun dismissLinkEditor() = _state.update { it.copy(linkEditor = null) }

    fun onLinkEditorLabel(value: String) =
        _state.update { it.copy(linkEditor = it.linkEditor?.copy(label = value)) }

    fun onLinkEditorUrl(value: String) =
        _state.update { it.copy(linkEditor = it.linkEditor?.copy(url = value)) }

    /**
     * Add or update, through the same sheet.
     *
     * `upsert` carries the row id when there is one, so editing a link is an
     * update rather than a delete-then-insert — the latter would move the link
     * to the end of the list every time a typo was fixed.
     */
    fun saveLinkEditor() {
        val editor = _state.value.linkEditor ?: return
        val userId = session.currentUserId ?: return
        val label = editor.label.trim()
        val url = normalizeLinkUrl(editor.url)
        if (!linkIsSavable(label, url)) return
        viewModelScope.launch {
            _state.update { it.copy(busyLinks = true) }
            runCatching { links.upsert(userId, label, url, id = editor.id) }
                .onSuccess {
                    _state.update { it.copy(busyLinks = false, linkEditor = null, saveError = null) }
                    loadLinks(userId)
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            busyLinks = false,
                            saveError = "Couldn't save your link — check your connection and try again.",
                        )
                    }
                }
        }
    }

    fun deleteLink(id: String) {
        val userId = session.currentUserId ?: return
        viewModelScope.launch {
            _state.update { it.copy(busyLinks = true) }
            runCatching { links.delete(id) }
                .onSuccess {
                    _state.update { it.copy(busyLinks = false, linkEditor = null, saveError = null) }
                    loadLinks(userId)
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            busyLinks = false,
                            saveError = "Couldn't remove your link — check your connection and try again.",
                        )
                    }
                }
        }
    }

    // ── Photos ───────────────────────────────────────────────────────────────

    /**
     * Upload a gallery photo directly rather than through [UploadQueue].
     *
     * The queue's photo task is a *cover* task — it uploads at `position = 0`
     * unconditionally, which is right for the wizard (one cover, no gallery) and
     * wrong here: every photo added from the editor would claim the cover slot
     * and collide with the photo already holding it. Uploading directly lets the
     * repository pick the next free position, which is what appending to a
     * gallery means.
     *
     * The trade is that a killed process loses an in-flight photo, where a queued
     * task would resume. Acceptable for a single image the artist can see did not
     * arrive, and the alternative is a gallery that cannot hold a second photo.
     *
     * **Off the main thread, explicitly.** Giving up the queue also gave up its
     * IO scope, and everything this path does before its first suspension point
     * is blocking work on whatever dispatcher it was launched from — which for
     * `viewModelScope` is `Main.immediate`. `adoptPhoto` streams a multi-megabyte
     * file through `copyTo`, and `uploadPhoto` decodes, scales to 2048px and
     * re-compresses the JPEG. On a 12MP pick that is hundreds of milliseconds of
     * frozen UI, so the whole body moves to IO rather than trusting a repository
     * two layers down to switch for us.
     */
    fun onPhotoPicked(uri: Uri) {
        val userId = session.currentUserId ?: return
        if (!canAddPhoto(_state.value.photos.size, _state.value.uploadingPhoto)) return
        viewModelScope.launch {
            _state.update { it.copy(uploadingPhoto = true, saveError = null) }
            val result = saveCatching {
                withContext(Dispatchers.IO) {
                    val pending = mediaCache.adoptPhoto(uri)
                    val file = pending.file(mediaCache)
                    try {
                        media.uploadPhoto(file, userId, position = null)
                    } finally {
                        // The cache copy exists to survive the wizard's
                        // pick-now-publish-later gap. Here the upload IS the
                        // commit, so the copy is dead weight the moment it
                        // returns.
                        file.delete()
                    }
                }
            }
            result
                .onSuccess {
                    _state.update { it.copy(uploadingPhoto = false, statusNote = "Photo added.") }
                    loadMedia(userId)
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            uploadingPhoto = false,
                            saveError = "Couldn't add that photo — check your connection and try again.",
                        )
                    }
                }
        }
    }

    /**
     * The camera permission was refused — screen 65's "Take a photo" row.
     *
     * A refusal has to say something. After the second one Android stops showing
     * the dialog at all, so without this the row is simply dead and looks
     * identical to "Choose from library" beside it. A `saveError` rather than a
     * toast because it is a state the artist has to fix in Settings, not a thing
     * that happened and passed.
     */
    fun onCameraUnavailable() {
        _state.update {
            it.copy(
                saveError = "Artistant can't open the camera without permission — " +
                    "you can still choose a photo from your library.",
            )
        }
    }

    fun deletePhoto(item: ArtistMediaItem) {
        val userId = session.currentUserId ?: return
        viewModelScope.launch {
            runCatching { media.delete(item) }
                .onSuccess {
                    _state.update { it.copy(saveError = null, statusNote = "Photo removed.") }
                    loadMedia(userId)
                }
                .onFailure {
                    _state.update {
                        it.copy(saveError = "Couldn't remove that photo — check your connection and try again.")
                    }
                }
        }
    }

    /**
     * Reorder, optimistically.
     *
     * The list moves under the artist's finger immediately because the whole
     * point of the control is seeing the new cover; the server call follows. On
     * failure the optimistic order is discarded by re-reading the truth rather
     * than by inverting the move locally — an inverse is only correct if nothing
     * else changed, and a failed write is exactly when that assumption is weak.
     */
    fun movePhoto(from: Int, to: Int) {
        if (!canReplaceWholeSet(_state.value.photosHydrated, session.currentUserId != null)) return
        val reordered = moveItem(_state.value.photos, from, to)
        if (reordered === _state.value.photos) return
        _state.update { it.copy(photos = reordered) }
        viewModelScope.launch {
            runCatching { media.reorder(reordered.map { it.id }) }
                .onSuccess { _state.update { it.copy(saveError = null, statusNote = "Photo order saved.") } }
                .onFailure {
                    _state.update {
                        it.copy(saveError = "Couldn't save the new photo order — check your connection.")
                    }
                    session.currentUserId?.let { id -> loadMedia(id) }
                }
        }
    }

    // ── Samples ──────────────────────────────────────────────────────────────

    /**
     * Samples DO stay on [UploadQueue]: its audio task appends correctly, and an
     * audio file is large enough that crash-resume is worth the indirection the
     * photo path just gave up. The completion watcher in [observeUploadQueue]
     * refreshes the list when the drain lands, and surfaces a drain that gave up.
     *
     * The title is resolved HERE from the picked document rather than handed in
     * by the screen. A picker callback only has the `Uri`, and its last path
     * segment is a provider-defined document id — the EPK used to publish
     * `audio:1000000042` as a clip title on the artist's public page, with no
     * rename anywhere on this screen to fix it. Reading `DISPLAY_NAME` is a
     * content-provider query, which together with `adoptAudio`'s whole-file copy
     * is why the body runs on IO rather than on `Main.immediate`.
     */
    fun onSamplePicked(uri: Uri) {
        val userId = session.currentUserId ?: return
        val current = _state.value
        if (!canAddSample(
                stored = current.samples.size,
                uploading = current.samplesUploading + current.samplesStaging,
            )
        ) {
            _state.update {
                it.copy(saveError = "You can keep up to $MAX_SAMPLES samples — remove one to add another.")
            }
            return
        }
        // Claimed before the first suspension point, so a second pick arriving
        // while this one is still copying sees the seat taken. Both picks land on
        // Main, so the check above and this increment cannot interleave.
        _state.update { it.copy(samplesStaging = it.samplesStaging + 1) }
        viewModelScope.launch {
            saveCatching {
                withContext(Dispatchers.IO) {
                    val title = sampleTitleFrom(mediaCache.displayName(uri))
                    val pending = mediaCache.adoptAudio(uri, title)
                    uploadQueue.enqueueAudioSample(
                        artistId = userId,
                        file = pending.file(mediaCache),
                        title = pending.title,
                        durationSeconds = pending.durationSeconds,
                    )
                }
            }
                .onSuccess {
                    // Deliberately does NOT clear [EpkUiState.uploadFailedMessage]:
                    // adding a second clip is not a retry of the first, and an
                    // earlier one still stranded in the queue is still stranded.
                    _state.update { it.copy(saveError = null, statusNote = "Sample uploading…") }
                }
                .onFailure {
                    _state.update { it.copy(saveError = "Couldn't add that sample — try a different file.") }
                }
            // Released only once the queue can see the clip (or the staging died),
            // so the seat is never free while the clip is invisible to both counts.
            _state.update { it.copy(samplesStaging = (it.samplesStaging - 1).coerceAtLeast(0)) }
        }
    }

    fun deleteSample(sample: Sample) {
        viewModelScope.launch {
            // Targeted single-row delete, never a whole-set replace: a replace
            // rebuilt from a possibly-stale local list prunes rows this device
            // has not seen, taking another device's newer clip with it.
            //
            // The clip's public URL goes with it. The repository only deletes the
            // bucket object when it is given something to derive a path from, so
            // passing null (as this did) removed the row and left the audio
            // sitting in a PUBLIC bucket, still playable by anyone holding the
            // link the artist thinks they just took down.
            runCatching { samples.delete(sample.id, sample.audioUrl) }
                .onSuccess {
                    _state.update { it.copy(saveError = null, statusNote = "Sample removed.") }
                    loadSamples()
                }
                .onFailure {
                    _state.update {
                        it.copy(saveError = "Couldn't remove that sample — check your connection and try again.")
                    }
                }
        }
    }

    // ── Saving ───────────────────────────────────────────────────────────────

    /**
     * Schedule one debounced write and remember that it owes the server something.
     *
     * The [save] stays armed from here until [persist] has run all the way
     * through — deliberately including the network call, so a write that was
     * cancelled mid-flight is still owed. A superseding edit re-arms the same
     * entry, which is why this is a set.
     */
    private fun arm(save: EpkSave, debounce: Boolean, persist: suspend () -> Unit): Job {
        armedSaves += save
        return viewModelScope.launch {
            runOwedSave(save, armedSaves, if (debounce) SAVE_DEBOUNCE_MS else 0L, persist)
        }
    }

    /**
     * Stop waiting: send everything that is owed, now.
     *
     * The debounce is only ever safe while the app is in front of the artist. Go
     * to the home screen mid-window and the 1.2s wait is being counted by a
     * process the OS may reclaim at any point, with the edit living nowhere but
     * that process's heap — [onCleared] does not run on a kill, so the write
     * simply never happens and nothing says so. [removePackageRow] already
     * refuses the debounce for exactly this reason ("long enough to background
     * the app"); this generalises it to every edit rather than to the one that
     * thought of it first.
     *
     * Re-scheduling as `immediate` is deliberate over cancelling the wait in
     * place: it reuses each section's own gate (a signed-out or never-hydrated
     * section still refuses to write) and it re-arms through the same path an
     * ordinary edit uses. A save already past the wait and out on the wire gets
     * cancelled and re-issued, which is a wasted request in a narrow window and
     * costs nothing else — every persist here either diffs first or replaces a
     * whole set, so re-running one is a no-op or the same write again.
     *
     * The drafts themselves are deliberately NOT mirrored to disk to survive a
     * kill. Packages and the tech rider persist by whole-set replace, and this
     * file's first rule is that a replace may only be built from a list that came
     * from a successful server read — replaying a set from a previous process
     * would send a stale list back over whatever the artist has since changed
     * elsewhere, which is a bigger hole than the one it closes.
     */
    fun flushPendingSaves() {
        armedSaves.toList().forEach { save ->
            when (save) {
                EpkSave.Packages -> schedulePackagesSave(immediate = true)
                EpkSave.Tech -> scheduleTechSave(immediate = true)
                EpkSave.Bio -> scheduleBioSave(immediate = true)
                EpkSave.Socials -> scheduleSocialsSave(immediate = true)
                EpkSave.Prompts -> schedulePromptsSave(immediate = true)
            }
        }
    }

    /**
     * Let an armed write finish after the editor is gone.
     *
     * Every save on this screen is debounced onto `viewModelScope`, which the
     * framework cancels when the artist leaves — so typing the last digit of a
     * price and immediately going Back destroyed the edit inside the 1.2s window,
     * silently, while the completeness counter at the top of the screen had
     * already counted it (it reads the drafts). The reference client's saves are
     * unstructured tasks and survive leaving the screen; these now do too.
     *
     * The scope is created here rather than held as a field because it exists for
     * one thing — owning these last writes until they land — and nothing
     * references it afterwards. Re-running is safe: the three diffing persists
     * (bio, accounts, prompts) ask `needsSave` first and no-op when the write
     * already landed, and the two whole-set replaces are idempotent.
     *
     * This covers *leaving*. It cannot cover a process the OS reclaims, which
     * calls nothing at all — [flushPendingSaves] is the half that handles going
     * to the background, before there is anything to reclaim.
     */
    override fun onCleared() {
        super.onCleared()
        val owed = armedSaves.toList()
        if (owed.isEmpty()) return
        // The owner these drafts were composed for. Detaching the write from
        // `viewModelScope` also detaches it from the SESSION, so an owed save
        // still queued when the artist signs out and someone else signs in on the
        // same device would otherwise run under the new user — landing THIS
        // artist's bio, pricing, rider, socials or prompts on the new account's
        // public row, which RLS permits because the JWT is theirs and the row is
        // theirs.
        //
        // Kept even though all three seams this loop drives now refuse a write
        // composed for another account — `require(userId == expectedOwner)` in
        // `patchSelf`, and the same guard on `PackagesRepository` /
        // `TechRiderRepository` `replaceAll`. The belt still earns its place:
        // stopping BEFORE the first write is better than throwing at it — a
        // refused write would be caught by the `runCatching` below and the flush
        // would carry on to the next owed save, one by one, all the way to the
        // end. This checks once up front and again per save so the flush simply
        // does nothing after a session change, rather than firing five guards
        // that each throw.
        val owner = session.currentUserId ?: return
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            owed.forEach { save ->
                // Re-read per save, immediately before issuing it: sign-out is a
                // multi-second user action, so a session that changed mid-flush
                // is caught here rather than after the first write has gone.
                if (session.currentUserId != owner) return@launch
                runCatching {
                    when (save) {
                        EpkSave.Packages -> persistPackages()
                        EpkSave.Tech -> persistTech()
                        EpkSave.Bio -> persistBio()
                        EpkSave.Socials -> persistSocials()
                        EpkSave.Prompts -> persistPrompts()
                    }
                }
            }
        }
    }

    private companion object {
        /** Matches iOS. Long enough to swallow a typed number, short enough to feel saved. */
        const val SAVE_DEBOUNCE_MS = 1_200L
    }
}
