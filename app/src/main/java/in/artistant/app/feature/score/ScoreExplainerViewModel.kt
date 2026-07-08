package `in`.artistant.app.feature.score

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.repository.ScoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The explainer's load states. [Error] is DISTINCT from a real New-artist zero:
 * a failed fetch must not render as "0 / New" (the silent-`try?` trap the iOS
 * screen calls out) — it shows an explicit, retryable error instead.
 */
sealed interface ScoreExplainerUiState {
    data object Loading : ScoreExplainerUiState
    data object Error : ScoreExplainerUiState
    data class Loaded(val breakdown: ScoreBreakdown) : ScoreExplainerUiState
}

/** Loads the signed-in artist's Bookability breakdown for `ScoreExplainerScreen`. */
@HiltViewModel
class ScoreExplainerViewModel @Inject constructor(
    private val scoreRepo: ScoreRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScoreExplainerUiState>(ScoreExplainerUiState.Loading)
    val state: StateFlow<ScoreExplainerUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = ScoreExplainerUiState.Loading
            _state.value = try {
                ScoreExplainerUiState.Loaded(scoreRepo.breakdownForSelf())
            } catch (_: Throwable) {
                ScoreExplainerUiState.Error
            }
        }
    }
}
