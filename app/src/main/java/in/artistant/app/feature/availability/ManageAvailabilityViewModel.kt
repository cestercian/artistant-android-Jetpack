package `in`.artistant.app.feature.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.common.util.availabilityKicker
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.feature.wizard.WizardWeekdays
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    /**
     * The badge clients actually see, driven by the current selection — the whole
     * job of the "HOW CLIENTS SEE YOU" box, so it delegates to the same helper
     * Discover's hero pill renders rather than deriving a second answer.
     *
     * It used to pick the first weekday in canonical Mon→Sun order and label it
     * "Open Mon". [availabilityKicker] scans forward from [today] instead, so on a
     * Saturday with `days = {Mon, Sat}` Discover said "AVAILABLE TODAY" while this
     * preview said "Open Mon" — wrong day AND wrong copy, on the one screen whose
     * stated purpose is showing the artist that badge before they save. iOS shares
     * the formatter for exactly this reason (`ManageAvailabilityView.previewLabel`).
     *
     * [today] is injected and defaulted the same way the helper does it, so the
     * preview is assertable without touching the system clock.
     */
    fun previewLabel(today: LocalDate = LocalDate.now()): String? =
        availabilityKicker(days, today)
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
