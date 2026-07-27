package `in`.artistant.app.feature.artisthome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.calendar.CalendarSyncPlanner
import `in`.artistant.app.platform.calendar.CalendarSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistHomeUiState(
    val pendingRequests: List<Booking> = emptyList(),
    val openQuotes: List<StoredRequest> = emptyList(),
    val upcoming: List<Booking> = emptyList(),
    val earningsSeries: List<Int> = emptyList(),
    val earningsTotal: Int = 0,
    val busyDayKeys: Set<String> = emptySet(),
    val dayStrip: List<Pair<String, String>> = emptyList(),
    val score: Int? = null,
    val gigs: Int = 0,
    val showFinishProfileCta: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArtistHomeViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
    private val requestsRepository: RequestsRepository,
    private val usersRepository: UsersRepository,
    private val artistsRepository: ArtistsRepository,
    private val scoreRepository: ScoreRepository,
    private val calendarSync: CalendarSyncService,
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ArtistHomeUiState())
    val state: StateFlow<ArtistHomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val bookings = bookingsRepository.listForArtist()
                val quotes = runCatching { requestsRepository.listForArtist() }.getOrDefault(emptyList())
                val profile = runCatching { usersRepository.fetchSelfProfile() }.getOrNull()
                val artistId = session.currentUserId
                val artist = artistId?.let { runCatching { artistsRepository.fetchArtist(it) }.getOrNull() }
                val breakdown = runCatching { scoreRepository.breakdownForSelf() }.getOrNull()
                val series = earningsSparkline(bookings)
                // Prefer device-mirror busy days when sync is on; else derive from bookings.
                val busy = calendarSync.busyDays().ifEmpty {
                    CalendarSyncPlanner.busyDays(bookings)
                }
                _state.update {
                    it.copy(
                        pendingRequests = pendingConfirmBookings(bookings),
                        openQuotes = openGigRequests(quotes),
                        upcoming = upcomingConfirmed(bookings).take(5),
                        earningsSeries = series,
                        earningsTotal = series.sum(),
                        busyDayKeys = busy,
                        dayStrip = nextDayLabels(14),
                        score = breakdown?.numericScore ?: artist?.score,
                        gigs = artist?.gigs ?: 0,
                        showFinishProfileCta = profile?.artistSetupComplete != true,
                        isLoading = false,
                    )
                }
            } catch (e: BookingRepositoryError) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
