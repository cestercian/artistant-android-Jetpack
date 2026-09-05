package `in`.artistant.app.feature.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.PendingReport
import `in`.artistant.app.data.repository.ReportOutcome
import `in`.artistant.app.data.repository.ReportSubmission
import `in`.artistant.app.data.repository.ReportsRepository
import `in`.artistant.app.data.repository.ReviewsRepository
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.feature.booking.BookingDraftStore
import `in`.artistant.app.feature.messages.ViewerIdentity
import `in`.artistant.app.feature.saved.SavedStore
import `in`.artistant.app.feature.system.ToastController
import `in`.artistant.app.feature.system.ToastIcon
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistProfileUiState(
    val artist: Artist? = null,
    /**
     * True once a full stitch has landed, i.e. once an empty
     * [Artist.packages] is a FACT about the artist rather than a mid-load
     * state.
     *
     * [artist] goes non-null the moment the cached Discover/Search tile is
     * found — before the profile round-trip returns — and that projection
     * carries no packages beside a real `min_price`. Without this flag the dock
     * asked `PackagePricing.dockPrice` with `packagesLoaded = true` against
     * that tile and quoted "On request / PRICING" for an artist with tiers,
     * then snapped to a price; on a failed stitch it stayed wrong for good.
     *
     * Never reset by a later failed refresh: the packages already on screen
     * came from a stitch, and a dropped packet does not un-load them.
     */
    val packagesLoaded: Boolean = false,
    val reviews: List<Review> = emptyList(),
    val reviewsFailed: Boolean = false,
    val scoreBreakdown: ScoreBreakdown? = null,
    /**
     * The breakdown read THREW. Screen 99's whole subject.
     *
     * Distinct from a null [scoreBreakdown], which is also what "we never asked"
     * looks like: the sheet renders the artist row's own score plus the factors
     * it can still back, and says the rest could not be itemised — but only when
     * a fetch actually failed. Without the flag it would say that during the
     * first frame of every profile, before the fetch has returned.
     */
    val scoreFailed: Boolean = false,
    val showScoreSheet: Boolean = false,
    /** Screen 04's "···" — the sheet carrying Save / Share / Report. */
    val showActionSheet: Boolean = false,
    /** Screen 56. Opened from the action sheet, which closes as it opens. */
    val showReportSheet: Boolean = false,
    /**
     * Filing a report: the in-flight guard, the loss that needs a retry, and the
     * stamp that orders overlapping attempts. Shared with the chat details sheet
     * — see [ReportSubmission], which owns every rule about it.
     *
     * `ReportSubmission.outcome` stays null on this surface: `Sent` and `Queued`
     * are momentary here, so they leave nothing behind but a toast raised on the
     * app's single host through
     * [in.artistant.app.feature.system.ToastController]. Only the loss is durable,
     * because "your safety report was lost" is the wrong shape for a message that
     * fades after three seconds.
     */
    val report: ReportSubmission = ReportSubmission(),
    /**
     * The signed-in user IS this artist (screen 103).
     *
     * Booking controls come off rather than being left to fail against the
     * server's self-booking guard, and the page says which side it is showing.
     */
    val isSelf: Boolean = false,
    val isLoading: Boolean = true,
    /**
     * Non-null means **what is on screen is not a loaded profile** — either
     * nothing at all, or the Discover/Search tile projection that arrived in the
     * cache. Never null just because [artist] is non-null: see
     * [artistProfileLoadError].
     */
    val loadError: String? = null,
    val selectedPackageIndex: Int = 0,
    val isSaved: Boolean = false,
)

/** The read failed in transport / RLS — we do not know what this artist offers. */
internal const val PROFILE_LOAD_FAILED = "Couldn't load this profile."

/** The server answered, and there is no such artist to show. */
internal const val ARTIST_NOT_FOUND = "Artist not found."

/**
 * What the profile says when a hydration attempt did **not** produce a full artist.
 *
 * Called only on that path, so it never returns null: a page drawn from anything
 * less than a hydrated row has something to admit. That is the fix. `ensureFull`
 * folds every transport failure into the same null the server returns for "no
 * such row", and the screen used to read that null as "nothing to report" as soon
 * as a cached artist existed — which it does for every client arriving from
 * Discover or Search, because both `cache()` the tile projection first. A dropped
 * packet therefore rendered the whole profile from a tile that by construction
 * carries no packages, bio, samples or reviews, and every section stated its own
 * emptiness as fact: "Pricing on request", a blank About, "No reviews yet." On a
 * booking marketplace that is misquoting an artist after a network blip.
 *
 * [hasCachedArtist] takes [PROFILE_LOAD_FAILED] whatever the cause, because
 * "Artist not found." next to a rendered artist name reads as a contradiction
 * rather than as a warning — and from this client's seat a row it cannot read is
 * a row it cannot show, whether the network dropped it or RLS did.
 */
internal fun artistProfileLoadError(fetchFailed: Boolean, hasCachedArtist: Boolean): String =
    if (fetchFailed || hasCachedArtist) PROFILE_LOAD_FAILED else ARTIST_NOT_FOUND

@HiltViewModel
class ArtistProfileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistsRepository: ArtistsRepository,
    private val reviewsRepository: ReviewsRepository,
    private val scoreRepository: ScoreRepository,
    private val reportsRepository: ReportsRepository,
    private val savedStore: SavedStore,
    private val draftStore: BookingDraftStore,
    private val toasts: ToastController,
    viewer: ViewerIdentity,
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _state = MutableStateFlow(
        // Resolved once, in the constructor: an artist's own id does not change
        // while the page is open, and a signed-out reader is simply not self.
        ArtistProfileUiState(
            isSelf = ArtistProfileFacts.isSelfProfile(viewer.currentUserId(), artistId),
        ),
    )
    val state: StateFlow<ArtistProfileUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            savedStore.ids.collect { ids ->
                _state.update { it.copy(isSaved = artistId.lowercase() in ids) }
            }
        }
    }

    /**
     * The in-flight load, and the stamp that decides whether it may still
     * commit.
     *
     * Retry is a button, so two loads can be alive at once and they can return
     * in either order — an older, slower read finishing last would overwrite the
     * fresher one it was supposed to replace. Cancelling is most of the fix;
     * the stamp closes the rest of the window, because a coroutine cancelled
     * after its last suspension point can still reach the `update` below.
     */
    private var loadJob: Job? = null
    private var loadGeneration = 0

    fun refresh() {
        loadJob?.cancel()
        val generation = ++loadGeneration
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            val cached = artistsRepository.find(artistId)
            if (cached != null) {
                _state.update { it.copy(artist = cached) }
            }
            // `fetchArtist`, not `ensureFull`: the convenience wrapper swallows the
            // throw, and "the read failed" has to stay distinguishable from "there
            // is no such artist" — the two say opposite things about the tile this
            // page may already be drawn from. See [artistProfileLoadError].
            val fetched = runCatching { artistsRepository.fetchArtist(artistId) }
            val full = fetched.getOrNull()
            if (generation != loadGeneration) return@launch
            if (full == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        loadError = artistProfileLoadError(
                            fetchFailed = fetched.isFailure,
                            hasCachedArtist = it.artist != null,
                        ),
                    )
                }
                return@launch
            }
            val popularIdx = full.packages.indexOfFirst { it.popular }.takeIf { it >= 0 } ?: 0
            var reviewsFailed = false
            val reviews = runCatching { reviewsRepository.listForArtist(artistId) }
                .onFailure { reviewsFailed = true }
                .getOrDefault(emptyList())
            val breakdownRead = runCatching { scoreRepository.breakdown(artistId) }
            if (generation != loadGeneration) return@launch
            _state.update {
                it.copy(
                    artist = full,
                    packagesLoaded = true,
                    reviews = reviews,
                    reviewsFailed = reviewsFailed,
                    scoreBreakdown = breakdownRead.getOrNull(),
                    scoreFailed = breakdownRead.isFailure,
                    selectedPackageIndex = popularIdx,
                    isLoading = false,
                    loadError = null,
                    isSaved = savedStore.contains(artistId),
                )
            }
        }
    }

    fun openScoreSheet() = _state.update { it.copy(showScoreSheet = true) }
    fun dismissScoreSheet() = _state.update { it.copy(showScoreSheet = false) }

    fun openActionSheet() = _state.update { it.copy(showActionSheet = true) }
    fun dismissActionSheet() = _state.update { it.copy(showActionSheet = false) }

    /**
     * The action sheet closes as the report sheet opens.
     *
     * Two `ModalBottomSheet`s alive at once stack two scrims, and dismissing the
     * report would drop the reader back onto the menu they left — so the menu is
     * gone before the form arrives.
     */
    fun openReportSheet() =
        _state.update { it.copy(showActionSheet = false, showReportSheet = true) }

    fun dismissReportSheet() = _state.update { it.copy(showReportSheet = false) }

    /**
     * File the report and remember what became of it.
     *
     * The sheet closes immediately, before the round-trip: the report is
     * fire-and-forget by contract (`ReportsRepository` never throws), the reader
     * has nothing to decide while it is in flight, and holding a spinner over a
     * form they have finished with is the pattern the design's "narrated, not a
     * spinner" note is written against.
     *
     * Sent and Queued are momentary facts and get a toast. **Failed is not** —
     * nothing holds the report, so it becomes durable state
     * ([ReportSubmission.failed])
     * that survives the toast window and carries the reader's own words back
     * into a retry. A safety report that vanished while a toast said "queued"
     * is the failure this branch exists to prevent.
     */
    fun submitReport(reason: String, details: String?) {
        // One report per tap. The sheet closes on submit so its own CTA cannot be
        // doubled, but [retryReport]'s banner stays up for the whole round trip.
        // `starting()` returns null when one is already out — that is the guard, and
        // it lives on [ReportSubmission] because this class cannot be built in a JVM
        // test (it reaches SavedStore, which reaches DataStore).
        val started = _state.value.report.starting() ?: return
        val myGeneration = started.generation
        // Applied through `update`, not assigned from the snapshot read above: a
        // refresh landing in between writes artist/reviews/score into this same
        // state, and a stale copy would put them back.
        _state.update { it.copy(showReportSheet = false, report = it.report.starting() ?: it.report) }
        viewModelScope.launch {
            val outcome = runCatching { reportsRepository.reportArtist(artistId, reason, details) }
                // A throw here is a contract violation (the interface promises
                // not to), so it is the WORST of the three claims, not the
                // middle one: we know nothing about where the report went.
                .getOrDefault(ReportOutcome.Failed)
            _state.update { state ->
                state.copy(
                    report = state.report.settling(
                        outcome = outcome,
                        pending = PendingReport(reason, details),
                        generation = myGeneration,
                    ),
                )
            }
            // Read AFTER the settle, not inside it: `update`'s lambda can run more than
            // once under contention, so it is no place for a side effect. `settling`
            // leaves the generation alone, so this is the same comparison it made.
            val superseded = myGeneration != _state.value.report.generation
            // Raised on the app's ONE host (screen 77), not on a host of this
            // screen's own: the sheet that filed the report has already closed
            // and the reader can leave the profile before the round trip
            // finishes, and a toast owned by a screen that is gone goes with it.
            // Read off the OUTCOME rather than off the settled state — a
            // superseded attempt claims nothing, and the state it leaves behind
            // is the PREVIOUS report's, which would toast twice.
            if (!superseded) {
                ArtistProfileFacts.reportToast(outcome)?.let { toasts.show(it, ToastIcon.Flag) }
            }
        }
    }

    /** Re-file the report the reader already wrote, from the failure banner. */
    fun retryReport() {
        val pending = _state.value.report.failed ?: return
        submitReport(pending.reason, pending.details)
    }

    /**
     * Give up on a lost report.
     *
     * Deliberately a separate control from [retryReport] and not a timeout: the
     * banner states that nothing holds the report, and it must not disappear on
     * its own while that is still true.
     *
     * `dismissing()` retires whatever is in flight AND releases the in-flight flag —
     * both halves, because the retired attempt's own completion is ignored by the
     * stamp, so nothing else would ever clear it. Without that, discarding a retry
     * mid-flight left a guard nobody was waiting for, and the NEXT report the reader
     * filed from the sheet — about someone else — was swallowed in silence.
     */
    fun dismissReportFailure() {
        _state.update { it.copy(report = it.report.dismissing()) }
    }

    fun selectPackage(index: Int) {
        _state.update { it.copy(selectedPackageIndex = index) }
    }

    /**
     * Hand the tapped tier to the booking screen, called as the client leaves via
     * "Check availability". The booking route carries only the artist id (same as
     * iOS `Route.booking(artist.id)`), so the selection travels through the shared
     * [BookingDraftStore] instead — iOS seeds its booking store in a
     * `simultaneousGesture` on the same CTA for exactly this reason. Seeding
     * unconditionally is safe: [ArtistProfileUiState.selectedPackageIndex] is
     * initialised with the same popular-first rule the booking screen falls back
     * to, so an untouched profile hands over the value booking would have picked.
     */
    fun startBooking() {
        draftStore.seedPackageIndex(artistId, _state.value.selectedPackageIndex)
    }

    fun toggleSaved() {
        savedStore.toggle(artistId)
    }

    override fun onCleared() {
        // Retire any attempt still in flight: `viewModelScope` is cancelled here, but a
        // pass already past its last suspension point runs on to its writes regardless,
        // and those writes belong to a screen that is gone.
        _state.update { it.copy(report = it.report.retired()) }
        super.onCleared()
    }
}
