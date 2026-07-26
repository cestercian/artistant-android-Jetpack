package `in`.artistant.app.feature.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.feature.wizard.WizardWeekdays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageAvailabilityUiState(
    val days: Set<String> = emptySet(),
    val times: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val seedFailed: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
) {
    /** Discover-style soonest-day label for the live preview. */
    val previewLabel: String?
        get() {
            if (days.isEmpty()) return null
            val soonest = WizardWeekdays.firstOrNull { it in days } ?: return null
            return "Open $soonest"
        }
}

@HiltViewModel
class ManageAvailabilityViewModel @Inject constructor(
    private val artists: ArtistsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageAvailabilityUiState())
    val state: StateFlow<ManageAvailabilityUiState> = _state.asStateFlow()

    init {
        seed()
    }

    fun seed() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, seedFailed = false, saved = false) }
            runCatching { artists.fetchSelfAvailability() }
                .onSuccess { draft ->
                    if (draft == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                seedFailed = true,
                                error = "Couldn't load availability. Retry before saving.",
                            )
                        }
                        return@launch
                    }
                    _state.update {
                        it.copy(
                            days = draft.daysAvailable.toSet(),
                            times = draft.timeSlots.toSet(),
                            isLoading = false,
                            seedFailed = false,
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            seedFailed = true,
                            error = e.message ?: "Couldn't load availability.",
                        )
                    }
                }
        }
    }

    fun toggleDay(day: String) {
        _state.update {
            val next = if (day in it.days) it.days - day else it.days + day
            it.copy(days = next, saved = false)
        }
    }

    fun toggleTime(slot: String) {
        _state.update {
            val next = if (slot in it.times) it.times - slot else it.times + slot
            it.copy(times = next, saved = false)
        }
    }

    fun save() {
        val snap = _state.value
        if (snap.seedFailed) return
        // Canonical order — never Set iteration order (booking grid depends on it).
        val days = WizardWeekdays.filter { it in snap.days }
        val times = DefaultTimeSlots.filter { it in snap.times }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, saved = false) }
            runCatching { artists.updateAvailability(days, times) }
                .onSuccess {
                    _state.update { it.copy(isSaving = false, saved = true) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isSaving = false, error = e.message ?: "Couldn't save.")
                    }
                }
        }
    }
}
