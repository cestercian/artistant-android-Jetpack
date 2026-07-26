package `in`.artistant.app.feature.wizard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.platform.auth.SessionManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WizardUiState(
    val step: WizardStep = WizardStep.Identity,
    val stageName: String = "",
    val handle: String = "",
    val category: String = "",
    val genre: String = "",
    val baseCity: String = "",
    val packageName: String = "Evening set",
    val packageDuration: String = "2h",
    val packagePrice: String = "25000",
    val techItems: Set<String> = emptySet(),
    val techDraft: String = "",
    val daysAvailable: Set<String> = setOf("Fri", "Sat"),
    val timeSlots: Set<String> = setOf("7:30 PM", "9:00 PM"),
    val coverGradientIndex: Int = 0,
    val instagramHandle: String = "",
    val spotifyArtistUrl: String = "",
    val youtubeChannelUrl: String = "",
    val bio: String = "",
    val isPublishing: Boolean = false,
    val publishError: String? = null,
) {
    val canAdvance: Boolean
        get() = when (step) {
            WizardStep.Identity ->
                stageName.isNotBlank() && handle.isNotBlank() && category.isNotBlank()
            WizardStep.Location -> baseCity.isNotBlank()
            WizardStep.Pricing ->
                packageName.isNotBlank() && packageDuration.isNotBlank() &&
                    packagePrice.toIntOrNull()?.let { it > 0 } == true
            WizardStep.Tech, WizardStep.Availability, WizardStep.Cover,
            WizardStep.Socials, WizardStep.Samples, WizardStep.Preview,
            -> true
            WizardStep.Bio -> bio.length <= 200
            WizardStep.Done -> false
        }

    val previewPackages: List<ArtistPackage>
        get() = listOf(
            ArtistPackage(
                id = "draft-0",
                name = packageName,
                duration = packageDuration,
                price = packagePrice.toIntOrNull() ?: 0,
                includes = emptyList(),
                popular = true,
            ),
        )
}

sealed interface WizardEvent {
    data object Finished : WizardEvent
}

@HiltViewModel
class WizardViewModel @Inject constructor(
    private val session: SessionManager,
    private val users: UsersRepository,
    private val artists: ArtistsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WizardUiState())
    val state: StateFlow<WizardUiState> = _state.asStateFlow()

    private val _events = Channel<WizardEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val profile = runCatching { users.fetchSelfProfile() }.getOrNull() ?: return@launch
            _state.update {
                it.copy(
                    stageName = profile.fullName.orEmpty(),
                    handle = profile.handle.orEmpty(),
                    baseCity = profile.city.orEmpty(),
                )
            }
        }
    }

    fun onStageNameChanged(value: String) = _state.update { it.copy(stageName = value) }
    fun onHandleChanged(value: String) = _state.update { it.copy(handle = value) }
    fun onCategorySelected(value: String) = _state.update { it.copy(category = value) }
    fun onGenreChanged(value: String) = _state.update { it.copy(genre = value) }
    fun onBaseCityChanged(value: String) = _state.update { it.copy(baseCity = value) }
    fun onPackageNameChanged(value: String) = _state.update { it.copy(packageName = value) }
    fun onPackageDurationChanged(value: String) = _state.update { it.copy(packageDuration = value) }
    fun onPackagePriceChanged(value: String) = _state.update { it.copy(packagePrice = value) }
    fun onTechDraftChanged(value: String) = _state.update { it.copy(techDraft = value) }
    fun addTechItem() {
        val item = _state.value.techDraft.trim()
        if (item.isBlank()) return
        _state.update {
            it.copy(techItems = it.techItems + item, techDraft = "")
        }
    }
    fun removeTechItem(item: String) =
        _state.update { it.copy(techItems = it.techItems - item) }

    fun toggleDay(day: String) =
        _state.update { it.copy(daysAvailable = toggleInSet(day, it.daysAvailable)) }

    fun toggleTimeSlot(slot: String) =
        _state.update { it.copy(timeSlots = toggleInSet(slot, it.timeSlots)) }

    fun onCoverGradientSelected(index: Int) =
        _state.update { it.copy(coverGradientIndex = index.coerceIn(0, 5)) }

    fun onInstagramChanged(value: String) = _state.update { it.copy(instagramHandle = value) }
    fun onSpotifyChanged(value: String) = _state.update { it.copy(spotifyArtistUrl = value) }
    fun onYoutubeChanged(value: String) = _state.update { it.copy(youtubeChannelUrl = value) }
    fun onBioChanged(value: String) = _state.update { it.copy(bio = value.take(200)) }

    fun next() {
        val current = _state.value
        if (!current.canAdvance) return
        when (current.step) {
            WizardStep.Preview -> publish()
            WizardStep.Done -> viewModelScope.launch { _events.send(WizardEvent.Finished) }
            else -> advanceWizardStep(current.step)?.let { nextStep ->
                _state.update { it.copy(step = nextStep, publishError = null) }
            }
        }
    }

    fun back() {
        backWizardStep(_state.value.step)?.let { prev ->
            _state.update { it.copy(step = prev, publishError = null) }
        }
    }

    fun finishFromDone() {
        viewModelScope.launch { _events.send(WizardEvent.Finished) }
    }

    private fun publish() {
        val userId = session.currentUserId ?: run {
            _state.update { it.copy(publishError = "Sign in again to publish.") }
            return
        }
        val snap = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isPublishing = true, publishError = null) }
            try {
                artists.publishWizardProfile(buildWizardProfileDraft(snap, userId))
                _state.update {
                    it.copy(isPublishing = false, step = WizardStep.Done, publishError = null)
                }
            } catch (e: AppError.UniqueViolation) {
                _state.update {
                    it.copy(isPublishing = false, publishError = "That handle is already taken.")
                }
            } catch (e: AppError) {
                _state.update {
                    it.copy(isPublishing = false, publishError = e.message ?: "Couldn't publish.")
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isPublishing = false, publishError = e.message ?: "Couldn't publish.")
                }
            }
        }
    }
}
