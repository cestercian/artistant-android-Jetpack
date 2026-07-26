package `in`.artistant.app.feature.epk

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.platform.auth.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EpkUiState(
    val artist: Artist? = null,
    val setupComplete: Boolean = true,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class EpkViewModel @Inject constructor(
    private val session: SessionManager,
    private val users: UsersRepository,
    private val artists: ArtistsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EpkUiState())
    val state: StateFlow<EpkUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val userId = session.currentUserId
            if (userId == null) {
                _state.update { it.copy(isLoading = false, error = "Sign in to view your EPK.") }
                return@launch
            }
            val profile = runCatching { users.fetchSelfProfile() }.getOrNull()
            val setupComplete = profile?.artistSetupComplete == true
            val artist = runCatching { artists.fetchArtist(userId) }.getOrNull()
            _state.update {
                it.copy(
                    artist = artist,
                    setupComplete = setupComplete,
                    isLoading = false,
                    error = if (artist == null && setupComplete) "Couldn't load your EPK." else null,
                )
            }
        }
    }
}
