package `in`.artistant.app.feature.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.feature.wizard.WizardConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Working copy of the availability editor. Local `@State`-equivalent so Cancel
 * discards cleanly and the "how clients see you" preview reacts instantly; nothing
 * reaches the server until [save].
 */
data class ManageAvailabilityUiState(
    val days: Set<String> = emptySet(),
    val times: Set<String> = emptySet(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

/**
 * Post-onboarding availability editor VM (port of iOS `ManageAvailabilityView`).
 * Seeds from the artist's own row and PATCHes only availability on save, persisting
 * in the canonical chip order (not Set iteration order) so the client's booking grid
 * stays deterministic. Seed failure is non-fatal (opens on a clean slate).
 */
@HiltViewModel
class ManageAvailabilityViewModel @Inject constructor(
    private val artists: ArtistsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageAvailabilityUiState())
    val state: StateFlow<ManageAvailabilityUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val current = runCatching { artists.fetchSelfAvailability() }.getOrNull()
            if (current != null) {
                _state.update { it.copy(days = current.days.toSet(), times = current.times.toSet()) }
            }
        }
    }

    fun toggleDay(day: String) = _state.update { it.copy(days = it.days.toggle(day)) }
    fun toggleTime(time: String) = _state.update { it.copy(times = it.times.toggle(time)) }

    fun save() {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            // Persist in the wizard's canonical chip order so the client's booking grid
            // doesn't jump (mirrors the wizard's sortedTimeSlots; days sorted the same way).
            val days = WizardConstants.allDays.filter { it in _state.value.days }
            val times = WizardConstants.allTimeSlots.filter { it in _state.value.times }
            try {
                artists.updateAvailability(days, times)
                _state.update { it.copy(saving = false, saved = true) }
            } catch (_: Throwable) {
                _state.update {
                    it.copy(saving = false, error = "Couldn't save your availability. Check your connection and try again.")
                }
            }
        }
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (contains(value)) this - value else this + value
}
