package `in`.artistant.app.feature.availability

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.common.util.availabilityKicker
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.feature.booking.DefaultTimeSlots
import `in`.artistant.app.feature.wizard.WizardWeekdays
import `in`.artistant.app.platform.auth.SessionManager
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
    private val session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageAvailabilityUiState())
    val state: StateFlow<ManageAvailabilityUiState> = _state.asStateFlow()

    /**
     * The account the loaded availability was READ for, captured when [seed]
     * succeeds. [save] passes it as the write's `expectedOwner` so the edit is
     * checked against the identity it was composed under — the same guard the
     * press-kit editor's writes carry. `fetchSelfAvailability` filters on the
     * session and returns the columns WITHOUT the id, so the owner is the session
     * id at the moment of that read rather than a value the draft carries or a
     * fresh session read at save time. Null until a seed lands, which (together
     * with [ManageAvailabilityUiState.seedFailed]) is what stops [save] writing
     * before the screen ever read the row.
     */
    private var seededOwner: String? = null

    init {
        seed()
    }

    fun seed() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, seedFailed = false, saved = false) }
            // Captured here, alongside the read: `fetchSelfAvailability` filters on
            // this same session id, so it names the account the loaded days/times
            // belong to. A null session makes the read return null below and lands
            // in the seed-failed path without arming [seededOwner].
            val owner = session.currentUserId
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
                    // Armed only now the read has landed: the days/times on screen
                    // came from this owner's row, so this owner is who a save may
                    // target.
                    seededOwner = owner
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
        // No owner means no successful seed to compose against — the same reason
        // [ManageAvailabilityUiState.seedFailed] blocks a save. Refuse rather than
        // fall back to a fresh session read, which is the very substitution this
        // guard exists to remove.
        val owner = seededOwner ?: return
        // Canonical order — never Set iteration order (booking grid depends on it).
        val days = WizardWeekdays.filter { it in snap.days }
        val times = DefaultTimeSlots.filter { it in snap.times }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null, saved = false) }
            runCatching { artists.updateAvailability(owner, days, times) }
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
