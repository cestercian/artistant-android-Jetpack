package `in`.artistant.app.feature.epk

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
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
import `in`.artistant.app.domain.artist.ServiceTags
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.media.UploadQueue
import `in`.artistant.app.platform.media.WizardMediaCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val savingPackages: Boolean = false,
    val savingTech: Boolean = false,
    val savingBio: Boolean = false,
    val savingSocials: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val busyLinks: Boolean = false,

    /** Initial hydrate failed — the section list is empty because of the network. */
    val loadError: String? = null,
    /** A WRITE failed. Dismissible, and never conflated with [loadError]. */
    val saveError: String? = null,
    /** Transient confirmation ("Pricing saved.") for writes with no visible result. */
    val statusNote: String? = null,
) {
    val anySaveInFlight: Boolean get() = savingPackages || savingTech || savingBio || savingSocials
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

    init {
        refresh()
        observeUploadQueue()
    }

    /**
     * Audio samples still go through [UploadQueue] (see [onSamplePicked]), which
     * drains in the background. Watching its completion counter is what makes a
     * finished upload appear without the artist pulling to refresh.
     *
     * `drop(1)` skips the value that is already there at collection time —
     * otherwise every construction would fire a redundant reload on top of the
     * one `init` already started.
     */
    private fun observeUploadQueue() {
        viewModelScope.launch {
            uploadQueue.state
                .map { it.batchCompleted }
                .distinctUntilChanged()
                .drop(1)
                .collect { loadSamples() }
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
                        bioDraft = if (pendingBio) it.bioDraft else artist?.bio.orEmpty(),
                        socialDraft = if (pendingSocials) it.socialDraft else savedSocials(artist),
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
        _state.update { it.copy(coverGradientIndex = clamped, saveError = null) }
        viewModelScope.launch {
            runCatching { artists.updateCoverGradient(clamped) }
                .onSuccess { _state.update { it.copy(statusNote = "Cover saved.") } }
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
        _state.update { it.copy(newArtistDiscountPct = target, saveError = null) }
        viewModelScope.launch {
            runCatching { artists.updateNewArtistDiscount(target) }
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
        val shown = shownServiceTags(current.serviceTags, current.artist?.serviceTags.orEmpty())
        val next = ServiceTags.toggle(shown, slug)
        // At the cap, toggling ON is refused rather than silently truncated. Say
        // so — a chip that does not light up on tap reads as a broken control.
        if (next == shown) {
            _state.update {
                it.copy(saveError = "You can list up to ${ServiceTags.MAX_TAGS} services — untick one to add another.")
            }
            return
        }
        _state.update { it.copy(serviceTags = next, saveError = null) }
        viewModelScope.launch {
            runCatching { artists.updateServiceTags(next) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            statusNote = "Services saved.",
                            artist = it.artist?.copy(serviceTags = next),
                        )
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
    private fun scheduleBioSave() {
        if (!_state.value.identityHydrated || session.currentUserId == null) return
        bioSaveJob?.cancel()
        bioSaveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persistBio()
        }
    }

    private suspend fun persistBio() {
        val draft = _state.value.bioDraft
        // Nothing to say to the server: the debounce fires on any keystroke,
        // including the ones that type a character and delete it again.
        if (!bioNeedsSave(draft, _state.value.artist?.bio.orEmpty())) return
        _state.update { it.copy(savingBio = true) }
        runCatching { artists.updateBio(draft) }
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
    private fun scheduleSocialsSave() {
        if (!_state.value.identityHydrated || session.currentUserId == null) return
        socialsSaveJob?.cancel()
        socialsSaveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            persistSocials()
        }
    }

    private suspend fun persistSocials() {
        val draft = _state.value.socialDraft
        if (!socialsNeedSave(draft, savedSocials(_state.value.artist))) return
        _state.update { it.copy(savingSocials = true) }
        runCatching {
            // Named, not positional. The parameter order here is
            // (instagram, spotify, youtube) while the draft and the count helper
            // read (spotify, instagram, youtube) — positionally this compiles
            // fine and silently files an artist's Spotify URL as their Instagram
            // handle.
            artists.updateSocialLinks(
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
        packagesSaveJob = viewModelScope.launch {
            if (!immediate) delay(SAVE_DEBOUNCE_MS)
            persistPackages()
        }
    }

    private suspend fun persistPackages() {
        val userId = session.currentUserId ?: return
        val drafts = packageDrafts(_state.value.packageRows)
        _state.update { it.copy(savingPackages = true) }
        runCatching { packages.replaceAll(userId, drafts) }
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

    fun removeTechItem(item: String) {
        _state.update { it.copy(techItems = it.techItems.filterNot { existing -> existing == item }) }
        scheduleTechSave(immediate = true)
    }

    private fun scheduleTechSave(immediate: Boolean = false) {
        if (!canReplaceWholeSet(_state.value.techHydrated, session.currentUserId != null)) return
        techSaveJob?.cancel()
        techSaveJob = viewModelScope.launch {
            if (!immediate) delay(SAVE_DEBOUNCE_MS)
            persistTech()
        }
    }

    private suspend fun persistTech() {
        val userId = session.currentUserId ?: return
        val items = _state.value.techItems
        _state.update { it.copy(savingTech = true) }
        runCatching { techRider.replaceAll(userId, items) }
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
     */
    fun onPhotoPicked(uri: Uri) {
        val userId = session.currentUserId ?: return
        if (!canAddPhoto(_state.value.photos.size, _state.value.uploadingPhoto)) return
        viewModelScope.launch {
            _state.update { it.copy(uploadingPhoto = true, saveError = null) }
            val result = runCatching {
                val pending = mediaCache.adoptPhoto(uri)
                val file = pending.file(mediaCache)
                try {
                    media.uploadPhoto(file, userId, position = null)
                } finally {
                    // The cache copy exists to survive the wizard's
                    // pick-now-publish-later gap. Here the upload IS the commit,
                    // so the copy is dead weight the moment it returns.
                    file.delete()
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
     * refreshes the list when the drain lands.
     */
    fun onSamplePicked(uri: Uri, displayName: String?) {
        val userId = session.currentUserId ?: return
        if (!canAddSample(_state.value.samples.size, uploadInFlight = false)) {
            _state.update {
                it.copy(saveError = "You can keep up to $MAX_SAMPLES samples — remove one to add another.")
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                val title = displayName?.substringBeforeLast('.')?.trim()?.ifBlank { null } ?: "Sample"
                val pending = mediaCache.adoptAudio(uri, title)
                uploadQueue.enqueueAudioSample(
                    artistId = userId,
                    file = pending.file(mediaCache),
                    title = pending.title,
                    durationSeconds = pending.durationSeconds,
                )
            }
                .onSuccess {
                    _state.update { it.copy(saveError = null, statusNote = "Sample uploading…") }
                }
                .onFailure {
                    _state.update { it.copy(saveError = "Couldn't add that sample — try a different file.") }
                }
        }
    }

    fun deleteSample(sample: Sample) {
        viewModelScope.launch {
            // Targeted single-row delete, never a whole-set replace: a replace
            // rebuilt from a possibly-stale local list prunes rows this device
            // has not seen, taking another device's newer clip with it.
            runCatching { samples.delete(sample.id, null) }
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

    private companion object {
        /** Matches iOS. Long enough to swallow a typed number, short enough to feel saved. */
        const val SAVE_DEBOUNCE_MS = 1_200L
    }
}
