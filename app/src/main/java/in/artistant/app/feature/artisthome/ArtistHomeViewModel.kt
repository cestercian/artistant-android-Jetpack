package `in`.artistant.app.feature.artisthome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.feature.bookings.BookingsViewModel
import `in`.artistant.app.platform.upload.UploadBannerSource
import `in`.artistant.app.platform.upload.UploadBannerState
import `in`.artistant.app.state.DeepLinkRouter
import `in`.artistant.app.state.RequestStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Earnings-hero window toggle (iOS `EarningsRange`). */
enum class EarningsRange(val label: String) { SevenDays("7D"), ThirtyDays("30D"), All("ALL") }

/**
 * Everything the dashboard renders in one snapshot. Real-data-only: an empty
 * [bookings] list renders truthful zeros, and a failed load surfaces via [error]
 * so "no data yet" can never masquerade as a swallowed fetch failure (the exact
 * silent-`try?` trap the iOS screen documents).
 */
data class ArtistHomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val greetingName: String = "there",
    val bookings: List<Booking> = emptyList(),
    val breakdown: ScoreBreakdown = ScoreBreakdown.newArtist,
    val error: String? = null,
)

/** The earnings delta pill: a magnitude + a direction (iOS `deltaPill`). */
data class EarningsDelta(val percent: Int, val up: Boolean)

/**
 * Drives `ArtistHomeScreen` (port of iOS `ArtistHomeView`). Parallel-loads the
 * signed-in artist's row (greeting), bookings (earnings + availability strip),
 * Bookability breakdown, and gig requests (via the shared [RequestStore]). Each
 * load is independent so one failure doesn't silently drop the others; any failure
 * sets [ArtistHomeUiState.error] behind a Retry.
 *
 * The earnings maths are pure companion functions so they're unit-testable without
 * a ViewModel — the same v1-matchmaker framing as iOS: this counts BOOKINGS, not ₹
 * (no money moves through the app, so a rupee total would be fictitious).
 */
@HiltViewModel
class ArtistHomeViewModel @Inject constructor(
    private val artists: ArtistsRepository,
    private val bookingsRepo: BookingsRepository,
    private val scoreRepo: ScoreRepository,
    private val requestStore: RequestStore,
    private val uploadBannerSource: UploadBannerSource,
    private val deepLink: DeepLinkRouter,
) : ViewModel() {

    /** A parked `gig_request` push id → the screen pushes its detail then [consumePendingRequest]. */
    val pendingRequestId: StateFlow<String?> = deepLink.pendingRequestId

    fun consumePendingRequest() = deepLink.consumePendingRequest()

    private val _state = MutableStateFlow(ArtistHomeUiState())
    val state: StateFlow<ArtistHomeUiState> = _state.asStateFlow()

    private val _range = MutableStateFlow(EarningsRange.ThirtyDays)
    val range: StateFlow<EarningsRange> = _range.asStateFlow()

    /** Gig-request inbox rows — read straight off the shared store. */
    val requests: StateFlow<List<StoredRequest>> = requestStore.requests

    /** Live upload-queue banner state (idle collapses to nothing in the UI). */
    val uploadBanner: StateFlow<UploadBannerState> = uploadBannerSource.bannerStateFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UploadBannerState())

    init { load(includeRequests = true) }

    fun setRange(range: EarningsRange) { _range.value = range }

    /** Pull-to-refresh entry — same parallel loads plus a forced requests re-pull. */
    fun refresh() = load(includeRequests = true, refreshing = true)

    private fun load(includeRequests: Boolean, refreshing: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = _state.value.bookings.isEmpty() && !refreshing,
                refreshing = refreshing,
                error = null,
            )
            coroutineScope {
                val selfD = async { runCatching { artists.fetchSelfArtistRow() } }
                val bookingsD = async { runCatching { bookingsRepo.listForArtist() } }
                val scoreD = async { runCatching { scoreRepo.breakdownForSelf() } }
                if (includeRequests) launch { requestStore.refreshFromServer() }

                var error: String? = null
                selfD.await().fold(
                    onSuccess = { row ->
                        row?.let { _state.value = _state.value.copy(greetingName = firstName(it.stageName)) }
                    },
                    onFailure = { error = it.message ?: "Couldn't load your dashboard." },
                )
                bookingsD.await().fold(
                    onSuccess = { _state.value = _state.value.copy(bookings = it) },
                    onFailure = { error = it.message ?: "Couldn't load your bookings." },
                )
                scoreD.await().fold(
                    onSuccess = { _state.value = _state.value.copy(breakdown = it) },
                    onFailure = { error = it.message ?: "Couldn't load your score." },
                )
                _state.value = _state.value.copy(loading = false, refreshing = false, error = error)
            }
        }
    }

    /** Failed-banner "Retry all" — clears the stalled batch (see UploadQueue.clearFinished). */
    fun clearFinishedUploads() = uploadBannerSource.clearFinished()

    // The range-dependent derivations (earnings series, delta, availability strip,
    // upcoming gigs) are pure companion functions below — the screen calls them with
    // the collected bookings + range so recomposition tracks both cleanly, and the
    // tests exercise them without spinning up a ViewModel.

    companion object {
        private val ZONE: ZoneId = ZoneId.systemDefault()

        /** Greeting first name: first whitespace-delimited word of the stage name, else "there". */
        fun firstName(stageName: String?): String =
            stageName?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: "there"

        /** Daily buckets shown for a range: 7 / 30, capped at 90 for ALL so bars stay legible. */
        fun bucketCount(range: EarningsRange): Int = when (range) {
            EarningsRange.SevenDays -> 7
            EarningsRange.ThirtyDays -> 30
            EarningsRange.All -> 90
        }

        /**
         * Daily booking counts by `createdAt`, oldest→newest, always length [count]
         * (zeros when empty) so the caller decides chart-vs-empty from the data, not
         * the array shape. Bookings outside the window are ignored.
         */
        fun dailyBuckets(bookings: List<Booking>, count: Int, today: LocalDate): List<Double> {
            val buckets = DoubleArray(count)
            for (b in bookings) {
                val day = b.createdAt?.atZone(ZONE)?.toLocalDate() ?: continue
                val daysAgo = java.time.temporal.ChronoUnit.DAYS.between(day, today).toInt()
                if (daysAgo in 0 until count) buckets[count - 1 - daysAgo] += 1.0
            }
            return buckets.toList()
        }

        /**
         * (headline, prior) booking counts for a range's rolling window:
         *   7D  → last 7 days vs the prior 7,  30D → last 30 vs prior 30,
         *   ALL → all-time headline, prior 0 (no comparable period; the pill hides).
         * Windows are inclusive of today, matching the sparkline's bucket span.
         */
        fun windowCounts(bookings: List<Booking>, range: EarningsRange, today: LocalDate): Pair<Int, Int> {
            if (range == EarningsRange.All) return bookings.size to 0
            val days = bucketCount(range).toLong()
            val currentStart = today.minusDays(days - 1)      // today-(N-1) .. today
            val priorStart = today.minusDays(2 * days - 1)    // the N days before that
            var headline = 0
            var prior = 0
            for (b in bookings) {
                val day = b.createdAt?.atZone(ZONE)?.toLocalDate() ?: continue
                when {
                    !day.isBefore(currentStart) && !day.isAfter(today) -> headline++
                    !day.isBefore(priorStart) && day.isBefore(currentStart) -> prior++
                }
            }
            return headline to prior
        }

        /** Percent change headline-vs-prior; new-window / all-time collapses to +100% or 0%. */
        fun delta(headline: Int, prior: Int): EarningsDelta = when {
            prior > 0 -> {
                val raw = Math.round((headline - prior).toDouble() / prior * 100).toInt()
                EarningsDelta(kotlin.math.abs(raw), raw >= 0)
            }
            headline > 0 -> EarningsDelta(100, true)
            else -> EarningsDelta(0, true)
        }

        /** Day-anchored dates within the next 14 days that carry a confirmed booking. */
        fun bookedDatesNext14Days(bookings: List<Booking>, start: LocalDate): Set<LocalDate> {
            val end = start.plusDays(14)
            val out = HashSet<LocalDate>()
            for (b in bookings) {
                if (b.status != BookingStatus.Confirmed) continue
                val day = BookingsViewModel.parseDay(b.dateLabel) ?: continue
                if (!day.isBefore(start) && day.isBefore(end)) out.add(day)
            }
            return out
        }

        /** Confirmed bookings sorted by gig date ascending; unparseable dates fall last. */
        fun upcomingConfirmed(bookings: List<Booking>): List<Booking> =
            bookings.filter { it.status == BookingStatus.Confirmed }
                .sortedBy { BookingsViewModel.parseDay(it.dateLabel) ?: LocalDate.MAX }

        /**
         * Dynamic UPCOMING sub-copy for the stat card, mirroring iOS `escrowSnapshot.daysCopy`:
         *   none → "No upcoming gigs";  today → "Next gig today";
         *   single / same-day cluster → "Next gig in N days";  spread → "Spread over N days".
         * Derives from the soonest/latest confirmed gig date; unparseable dates fall back to a
         * generic "Upcoming gig" rather than inventing a day count.
         */
        fun upcomingCopy(bookings: List<Booking>, today: LocalDate): String {
            val gigs = upcomingConfirmed(bookings)
            if (gigs.isEmpty()) return "No upcoming gigs"
            val dates = gigs.mapNotNull { BookingsViewModel.parseDay(it.dateLabel) }
            val earliest = dates.minOrNull() ?: return "Upcoming gig"
            val latest = dates.maxOrNull() ?: earliest
            val earliestDays = maxOf(0, java.time.temporal.ChronoUnit.DAYS.between(today, earliest).toInt())
            val latestDays = maxOf(0, java.time.temporal.ChronoUnit.DAYS.between(today, latest).toInt())
            fun days(n: Int) = "Next gig in $n day${if (n == 1) "" else "s"}"
            return when {
                earliestDays == 0 && latestDays == 0 -> "Next gig today"
                gigs.size == 1 -> days(earliestDays)
                earliestDays == latestDays -> days(earliestDays)
                else -> "Spread over $latestDays days"
            }
        }
    }
}
