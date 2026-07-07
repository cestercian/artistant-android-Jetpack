package `in`.artistant.app.state

import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.repository.RequestsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide gig-request state (port of iOS `RequestStore`). The artist's
 * accept / decline / counter actions are OPTIMISTIC: the local status flips
 * instantly, then a background write follows. On the server's purpose-built
 * [AppError.NotFoundOrUnauthorized] signal (the row was withdrawn / RLS-filtered)
 * the flip is ROLLED BACK and an error event is emitted; other (transient) failures
 * keep the optimistic state for the next [refreshFromServer] to reconcile.
 *
 * accept/decline/counter return the write [Job] so tests can `join()` it
 * deterministically; the write runs on a long-lived SupervisorJob scope.
 */
@Singleton
class RequestStore @Inject constructor(
    private val repository: RequestsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _requests = MutableStateFlow<List<StoredRequest>>(emptyList())
    val requests: StateFlow<List<StoredRequest>> = _requests.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    fun reset() {
        _requests.value = emptyList()
    }

    val openCount: Int get() = _requests.value.count { it.status == GigRequestStatus.Open }

    fun accept(id: String): Job =
        writeOptimistically(id, { it.copy(status = GigRequestStatus.Accepted) }) { repository.accept(id) }

    fun decline(id: String): Job =
        writeOptimistically(id, { it.copy(status = GigRequestStatus.Declined) }) { repository.decline(id) }

    fun counter(id: String, amount: Int): Job =
        writeOptimistically(
            id,
            { it.copy(status = GigRequestStatus.Countered, counterAmount = amount) },
        ) { repository.counter(id, amount) }

    /** Optimistic mutate + background write; rollback on NotFoundOrUnauthorized. */
    private fun writeOptimistically(
        id: String,
        mutate: (StoredRequest) -> StoredRequest,
        write: suspend () -> Unit,
    ): Job {
        val prior = _requests.value.firstOrNull { it.id == id } ?: return Job().apply { complete() }
        _requests.update { list -> list.map { if (it.id == id) mutate(it) else it } }
        return scope.launch {
            try {
                write()
            } catch (e: AppError.NotFoundOrUnauthorized) {
                _requests.update { list -> list.map { if (it.id == id) prior else it } }
                _errors.tryEmit("That request was withdrawn or already handled.")
            } catch (_: Throwable) {
                // Transient failure: keep the optimistic state for refresh to reconcile.
            }
        }
    }

    /** Pulls the artist's server requests and replaces the local list. */
    suspend fun refreshFromServer() {
        try {
            _requests.value = repository.listForArtist()
        } catch (t: Throwable) {
            _errors.tryEmit(t.message ?: "Couldn't load requests.")
        }
    }

    /** Seed the list (used by the client outbox / a just-created request). */
    fun setRequests(requests: List<StoredRequest>) {
        _requests.value = requests
    }
}
