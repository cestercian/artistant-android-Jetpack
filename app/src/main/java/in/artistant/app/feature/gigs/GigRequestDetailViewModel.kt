package `in`.artistant.app.feature.gigs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.RequestsRepositoryError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GigRequestDetailUiState(
    val request: StoredRequest? = null,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val isActing: Boolean = false,
    val actionError: String? = null,
    val showCounterSheet: Boolean = false,
    val counterAmount: String = "",
)

@HiltViewModel
class GigRequestDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val requestsRepository: RequestsRepository,
) : ViewModel() {

    private val requestId: String = checkNotNull(savedStateHandle["requestId"])

    private val _state = MutableStateFlow(GigRequestDetailUiState())
    val state: StateFlow<GigRequestDetailUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            try {
                val found = requestsRepository.listForArtist()
                    .firstOrNull { it.id.equals(requestId, ignoreCase = true) }
                _state.update {
                    it.copy(
                        request = found,
                        isLoading = false,
                        loadError = if (found == null) "Request not found." else null,
                        counterAmount = found?.raw?.amount?.toString().orEmpty(),
                    )
                }
            } catch (e: RequestsRepositoryError) {
                _state.update { it.copy(isLoading = false, loadError = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, loadError = e.message) }
            }
        }
    }

    fun showCounterSheet() {
        val amount = _state.value.request?.raw?.amount?.toString().orEmpty()
        _state.update { it.copy(showCounterSheet = true, counterAmount = amount, actionError = null) }
    }

    fun dismissCounterSheet() {
        _state.update { it.copy(showCounterSheet = false) }
    }

    fun setCounterAmount(value: String) {
        _state.update { it.copy(counterAmount = value.filter { ch -> ch.isDigit() }) }
    }

    fun accept() = mutate { requestsRepository.accept(requestId) }

    fun decline() = mutate { requestsRepository.decline(requestId) }

    fun sendCounter() {
        val amount = _state.value.counterAmount.toIntOrNull() ?: 0
        if (amount <= 0) {
            _state.update { it.copy(actionError = "Enter a counter amount above ₹0.") }
            return
        }
        mutate {
            requestsRepository.counter(requestId, amount)
            _state.update { it.copy(showCounterSheet = false) }
        }
    }

    fun showActions(): Boolean =
        _state.value.request?.status == GigRequestStatus.Open

    private fun mutate(after: (() -> Unit)? = null, block: suspend () -> Unit) {
        if (_state.value.isActing) return
        viewModelScope.launch {
            _state.update { it.copy(isActing = true, actionError = null) }
            try {
                block()
                after?.invoke()
                refresh()
                _state.update { it.copy(isActing = false) }
            } catch (e: RequestsRepositoryError) {
                _state.update { it.copy(isActing = false, actionError = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isActing = false, actionError = e.message ?: "Action failed.")
                }
            }
        }
    }
}
