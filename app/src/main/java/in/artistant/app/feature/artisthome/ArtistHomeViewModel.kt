package `in`.artistant.app.feature.artisthome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.feature.paywall.EntitlementStore
import `in`.artistant.app.platform.auth.SessionManager
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
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistHomeUiState())
    val state: StateFlow<ArtistHomeUiState> = _state.asStateFlow()

    /** The bookings list every derived field projects from. */
    private var bookings: List<Booking> = emptyList()

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
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                // The four reads are independent, so one being slow shouldn't
                // hold the others. Only the bookings read is allowed to fail the
                // whole screen: an empty list rendered after a failed fetch is
                // indistinguishable from a genuinely empty dashboard, and would
                // paint booked days as open. The rest degrade to their own
                // truthful defaults.
                val loaded = coroutineScope {
                    val bookingsJob = async { bookingsRepository.listForArtist() }
                    val quotesJob = async { runCatching { requestsRepository.listForArtist() }.getOrDefault(emptyList()) }
                    val profileJob = async { runCatching { usersRepository.fetchSelfProfile() }.getOrNull() }
                    val breakdownJob = async { runCatching { scoreRepository.breakdownForSelf() }.getOrNull() }
                    LoadedDashboard(
                        bookings = bookingsJob.await(),
                        quotes = quotesJob.await(),
                        profile = profileJob.await(),
                        breakdown = breakdownJob.await(),
                    )
                }

                // Depends on the auth id, so it can't join the fan-out above.
                val artistId = session.currentUserId
                val artist = artistId?.let { runCatching { artistsRepository.fetchArtist(it) }.getOrNull() }

                // Inert unless the operator has flipped subscriptions on; the
                // store short-circuits to "not entitled" in that case, so the
                // banner self-hides without any billing traffic.
                runCatching { entitlements.refresh() }
                val showSubscribe = entitlements.subscriptionsActive && !entitlements.isEntitled.value

                bookings = loaded.bookings
                _state.update { prev ->
                    prev.copy(
                        greetingName = greetingName(artist?.name),
                        avatarName = artist?.name?.takeIf { it.isNotBlank() } ?: "Artist",
                        todayLabel = todayLabel(),
                        openQuotes = openGigRequests(loaded.quotes),
                        breakdown = loaded.breakdown ?: ScoreBreakdown.NewArtist,
                        profileGaps = loaded.profile?.let {
                            profileGaps(
                                setupComplete = it.artistSetupComplete == true,
                                genre = artist?.genre,
                                bio = artist?.bio,
                            )
                        },
                        showSubscribeBanner = showSubscribe,
                        isLoading = false,
                        hasLoaded = true,
                        error = null,
                    ).withDerived(loaded.bookings)
                }
            } catch (e: Exception) {
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
        val profile: `in`.artistant.app.data.model.SelfProfile?,
        val breakdown: ScoreBreakdown?,
    )

    private companion object {
        const val STRIP_DAYS = 14
    }
}
