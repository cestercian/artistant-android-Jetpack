package `in`.artistant.app.feature.artisthome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingRepositoryError
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.ScoreRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.platform.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistHomeUiState(
    val pendingRequests: List<Booking> = emptyList(),
    val score: Int? = null,
    val gigs: Int = 0,
    val showFinishProfileCta: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArtistHomeViewModel @Inject constructor(
    private val bookingsRepository: BookingsRepository,
    private val usersRepository: UsersRepository,
    private val artistsRepository: ArtistsRepository,
    private val scoreRepository: ScoreRepository,
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
                val profile = runCatching { usersRepository.fetchSelfProfile() }.getOrNull()
                val artistId = session.currentUserId
                val artist = artistId?.let { runCatching { artistsRepository.fetchArtist(it) }.getOrNull() }
                val breakdown = runCatching { scoreRepository.breakdownForSelf() }.getOrNull()
                _state.update {
                    it.copy(
                        pendingRequests = pendingConfirmBookings(bookings),
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
