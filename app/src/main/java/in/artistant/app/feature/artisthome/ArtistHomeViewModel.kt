package `in`.artistant.app.feature.artisthome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.feature.messages.ViewerIdentity
import `in`.artistant.app.feature.paywall.EntitlementStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything the dashboard renders.
 *
 * The derived fields ([heroCounts], [series], [strip], …) are recomputed on
 * [setRange] as well as on refresh, so the range picker never has to reach back
 * to the network — the whole screen is a projection of one bookings list.
 */
data class ArtistHomeUiState(
    // Identity
    val greetingName: String = "there",
    val avatarName: String = "Artist",
    val todayLabel: String = "",

    // Rails
    val pendingRequests: List<Booking> = emptyList(),
    val openQuotes: List<StoredRequest> = emptyList(),
    val upcoming: List<Booking> = emptyList(),

    // Hero
    val range: EarningsRange = EarningsRange.ThirtyDays,
    val hero: HeroCounts = HeroCounts(0, 0, 0, true),
    val series: List<Int> = emptyList(),

    // Stat row
    val upcomingSnapshot: UpcomingSnapshot = UpcomingSnapshot(0, "No upcoming gigs"),
    val bookings7d: List<Int> = emptyList(),

    // Bookability
    val breakdown: ScoreBreakdown = ScoreBreakdown.NewArtist,

    // 14-day strip
    val strip: List<StripDay> = emptyList(),
    val bookedDayKeys: Set<String> = emptySet(),

    // Banners
    val profileGaps: ProfileGaps? = null,
    val showSubscribeBanner: Boolean = false,

    val isLoading: Boolean = true,
    /**
     * True once a refresh has landed. Distinct from `!isLoading`: it is what
     * separates "nothing to show yet" (spinner, then a full-screen failure) from
     * "a dashboard is on screen and a later refresh failed" (inline banner over
     * the data that's already there). Without it, a dropped connection on a
     * background refresh would wipe a working screen.
     */
    val hasLoaded: Boolean = false,
    val error: String? = null,
) {
    /** Bars only draw when at least one bucket landed — see [EarningsRange.All]. */
    val hasChartData: Boolean get() = series.any { it > 0 }

    /** True when the artist has bookings the chart window can't reach. */
    val hasCountsOutsideChart: Boolean
        get() = hero.headline > 0 && !hasChartData && range == EarningsRange.All

    val bookings7dCount: Int get() = bookings7d.sum()
}

@HiltViewModel
class ArtistHomeViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
    private val requestsRepository: RequestsRepository,
    private val usersRepository: UsersRepository,
    private val artistsRepository: ArtistsRepository,
    private val scoreRepository: ScoreRepository,
    private val entitlements: EntitlementStore,
    // The uid only, not the whole SessionManager: that class drags in a Context, the
    // Supabase client, analytics and push, none of which a JVM unit test can build —
    // and the refresh ordering below is only assertable from a test that can construct
    // this ViewModel. Reuses the existing app-wide ViewerIdentity binding rather than
    // adding a second seam for the same fact.
    private val viewer: ViewerIdentity,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistHomeUiState())
    val state: StateFlow<ArtistHomeUiState> = _state.asStateFlow()

    /** The bookings list every derived field projects from. */
    private var bookings: List<Booking> = emptyList()

    /**
     * Newest refresh wins, however the network resolves.
     *
     * Refreshes overlap routinely here — pull-to-refresh, a resume and the failure
     * banner's Retry can all fire within a second — and the whole screen is a
     * projection of ONE bookings list written in a single shot at the end of
     * [refresh]. Without a guard, the request that finishes LAST wins even when it
     * is the older one, and the counts, the request rail, the upcoming gigs and the
     * booked days on the availability strip all snap back to a stale snapshot.
     *
     * Same shape SearchViewModel uses for its paged loads: cancel the in-flight job
     * AND stamp each load with a generation. The cancel is the cheap part — it stops
     * work nobody wants — but it is not sufficient on its own: a job that has already
     * passed its last suspension point runs on to its state write regardless, and the
     * four reads below can all be complete before the newer refresh even starts. The
     * stamp is what actually orders the writes.
     */
    private var refreshJob: Job? = null
    private var generation = 0

    init {
        refresh()
    }

    /**
     * Range switch is local — the list is already in memory, so re-deriving is
     * cheaper than a round trip and doesn't flash a spinner over the screen.
     */
    fun setRange(range: EarningsRange) {
        _state.update { it.copy(range = range).withDerived(bookings) }
    }

    fun refresh() {
        refreshJob?.cancel()
        val gen = ++generation
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // The four reads are independent, so one being slow shouldn't
                // hold the others. Only the bookings read is allowed to fail the
                // whole screen: an empty list rendered after a failed fetch is
                // indistinguishable from a genuinely empty dashboard, and would
                // paint booked days as open. The quote read degrades to its own
                // truthful default; the profile and score reads carry their
                // outcome forward instead, because "the read failed" and "the
                // field is empty" are different answers and both the banners and
                // the standing card below depend on which.
                val loaded = coroutineScope {
                    val bookingsJob = async { bookingsRepository.listForArtist() }
                    val quotesJob = async { runCatching { requestsRepository.listForArtist() }.getOrDefault(emptyList()) }
                    // Kept as a Result, not flattened to null: `fetchSelfProfile`
                    // returns null for a genuinely-absent row and THROWS on a
                    // network/RLS failure, and the completeness banner below means
                    // something different in each case.
                    val profileJob = async { runCatching { usersRepository.fetchSelfProfile() } }
                    // Same treatment, same reason. Flattened to null this read was
                    // indistinguishable from a real zero-gig artist, and the write
                    // below substituted `ScoreBreakdown.NewArtist` for it — so one
                    // blipped read repainted an Elite 92 as "—" / "NEW" with all
                    // four metric rows collapsed to em-dashes, silently, because
                    // nothing folded it into the failure banner either.
                    val breakdownJob = async { runCatching { scoreRepository.breakdownForSelf() } }
                    LoadedDashboard(
                        bookings = bookingsJob.await(),
                        quotes = quotesJob.await(),
                        profile = profileJob.await(),
                        breakdown = breakdownJob.await(),
                    )
                }

                // Depends on the auth id, so it can't join the fan-out above.
                val artistId = viewer.currentUserId()
                val artistResult = artistId?.let { runCatching { artistsRepository.fetchArtist(it) } }
                val artist = artistResult?.getOrNull()
                // "We could not read the artist row" — a thrown fetch, or no session
                // to read it for. NOT the same as "we read it and the fields are
                // empty", which is what a bare `getOrNull()` would collapse it into.
                val detailUnknown = artistResult == null || artistResult.isFailure

                // A read that failed is the artist's business: it's why half the
                // screen looks thinner than it should. The existing refresh-failure
                // banner already carries a Retry, so reuse it rather than inventing
                // a second failure surface — but name the half that didn't land,
                // because the detail line is the only thing telling the artist why
                // their standing (or their name, or the completeness flag) looks
                // wrong. Profile first when both fail: it's the wider outage.
                val partialFailure = when {
                    loaded.profile.isFailure || artistResult?.isFailure == true ->
                        "Couldn't load your profile details. Check your connection and try again."
                    loaded.breakdown.isFailure ->
                        "Couldn't load your Bookability Score. Check your connection and try again."
                    else -> null
                }

                // Inert unless the operator has flipped subscriptions on; the
                // store short-circuits to "not entitled" in that case, so the
                // banner self-hides without any billing traffic.
                runCatching { entitlements.refresh() }
                val showSubscribe = entitlements.subscriptionsActive && !entitlements.isEntitled.value

                // Everything below WRITES. A load that has been superseded stops here:
                // the shared `bookings` field is guarded too, not just the state, or
                // the next range switch would re-derive the screen from the stale list.
                if (gen != generation) return@launch

                bookings = loaded.bookings
                _state.update { prev ->
                    // Null = "nothing can be said about completeness this time
                    // round" (the artist row didn't arrive, or there's no users row
                    // to check against). Fall back to what the last good refresh
                    // decided rather than to a banner derived from absent fields.
                    val gaps = loaded.profile.getOrNull()?.let {
                        profileGaps(
                            setupComplete = it.artistSetupComplete == true,
                            genre = artist?.genre,
                            bio = artist?.bio,
                            detailUnknown = detailUnknown,
                        )
                    }
                    prev.copy(
                        // Same reasoning for the identity: a blipped read must not
                        // reset a hydrated screen to the pre-hydration placeholders.
                        greetingName = if (detailUnknown) prev.greetingName else greetingName(artist?.name),
                        avatarName = if (detailUnknown) {
                            prev.avatarName
                        } else {
                            artist?.name?.takeIf { it.isNotBlank() } ?: "Artist"
                        },
                        todayLabel = todayLabel(),
                        openQuotes = openGigRequests(loaded.quotes),
                        // Same rule as the identity above: a blipped read is not
                        // news that the artist is new. `NewArtist` is the
                        // pre-hydration default only — never a failure fallback.
                        breakdown = loaded.breakdown.getOrNull() ?: prev.breakdown,
                        profileGaps = gaps ?: prev.profileGaps,
                        showSubscribeBanner = showSubscribe,
                        isLoading = false,
                        hasLoaded = true,
                        error = partialFailure,
                    ).withDerived(loaded.bookings)
                }
            } catch (e: Exception) {
                // Guarded for the same reason as the success path, plus one more: a
                // cancelled job lands here (CancellationException is an Exception), and
                // an unguarded write would paint the failure banner over the very data
                // the newer refresh just fetched.
                if (gen != generation) return@launch

                // Keep whatever was already on screen and surface a retry banner
                // above it — a refresh failure shouldn't blank a working
                // dashboard. The screen only falls back to a full-screen error
                // when it has nothing to show at all.
                _state.update { it.copy(isLoading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }

    /** Everything computed from the bookings list, in one place so it can't drift. */
    private fun ArtistHomeUiState.withDerived(source: List<Booking>): ArtistHomeUiState = copy(
        pendingRequests = pendingConfirmBookings(source),
        upcoming = upcomingConfirmed(source),
        hero = heroCounts(source, range),
        series = bookingCountSeries(source, range.bucketCount),
        upcomingSnapshot = upcomingSnapshot(source),
        // Fixed 7-day window regardless of the range picker: this cell is a
        // "recent momentum" snapshot, not a windowed metric.
        bookings7d = bookingCountSeries(source, buckets = 7),
        strip = calendarStripDays(STRIP_DAYS),
        bookedDayKeys = bookedDayKeys(source, STRIP_DAYS),
    )

    private data class LoadedDashboard(
        val bookings: List<Booking>,
        val quotes: List<StoredRequest>,
        /** success(null) = no users row; failure = the read threw. Not interchangeable. */
        val profile: Result<SelfProfile?>,
        /** success = the row was read (zeros included); failure = the read threw. */
        val breakdown: Result<ScoreBreakdown>,
    )

    private companion object {
        const val STRIP_DAYS = 14
    }
}
