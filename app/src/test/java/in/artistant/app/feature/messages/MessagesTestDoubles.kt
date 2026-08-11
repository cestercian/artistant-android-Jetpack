package `in`.artistant.app.feature.messages

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.payments.PaymentResult
import `in`.artistant.app.data.repository.BookingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Doubles shared by the messages ViewModel tests.
 *
 * Both seams the rebuilt inbox and chat depend on are here rather than repeated
 * per file: a bookings repository that only answers the two list calls the inbox
 * makes, and a flags store that keeps its sets in memory instead of DataStore.
 */

/**
 * Bookings, scriptable per seat.
 *
 * Everything the messages surfaces never call throws, so a test that
 * accidentally reaches for a write path fails loudly instead of silently
 * passing against a no-op.
 */
open class StubBookings(
    private val clientBookings: List<Booking> = emptyList(),
    private val artistBookings: List<Booking> = emptyList(),
    private val failLists: Boolean = false,
    private val one: Booking? = null,
) : BookingsRepository {
    var clientCalls = 0
        private set
    var artistCalls = 0
        private set
    var feedback: List<String> = emptyList()
        private set

    /** Set to make [submitFeedback] report a failed write. */
    var feedbackDelivers: Boolean = true

    override suspend fun listForClient(): List<Booking> {
        clientCalls++
        if (failLists) throw IllegalStateException("offline")
        return clientBookings
    }

    override suspend fun listForArtist(): List<Booking> {
        artistCalls++
        if (failLists) throw IllegalStateException("offline")
        return artistBookings
    }

    override suspend fun fetchOne(id: String): Booking? = one?.takeIf { it.id == id }

    override suspend fun submitFeedback(body: String, isBug: Boolean): Boolean {
        feedback = feedback + body
        return feedbackDelivers
    }

    override suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking =
        error("messages never creates a booking")

    override suspend fun cancel(id: String, reason: String?): Booking =
        error("messages never cancels a booking")

    override suspend fun accept(id: String): Booking = error("messages never accepts a booking")

    override suspend fun declineByArtist(id: String, reason: String?): Booking =
        error("messages never declines a booking")
}

/** In-memory [ThreadFlagsStore] — same semantics as the DataStore one, no Context. */
class FakeThreadFlagsStore(initial: ThreadFlags = ThreadFlags()) : ThreadFlagsStore {
    private val state = MutableStateFlow(initial)
    override val flags: Flow<ThreadFlags> = state

    override suspend fun toggleStarred(threadId: String) {
        state.value = state.value.copy(starred = state.value.starred.toggle(threadId))
    }

    override suspend fun toggleArchived(threadId: String) {
        state.value = state.value.copy(archived = state.value.archived.toggle(threadId))
    }

    override suspend fun markUnread(threadId: String) {
        state.value = state.value.copy(markedUnread = state.value.markedUnread + threadId)
    }

    override suspend fun clearMarkedUnread(threadId: String) {
        state.value = state.value.copy(markedUnread = state.value.markedUnread - threadId)
    }

    override suspend fun dismissSafetyBanner(threadId: String) {
        state.value = state.value.copy(safetyDismissed = state.value.safetyDismissed + threadId)
    }

    private fun Set<String>.toggle(id: String) = if (id in this) this - id else this + id
}

/**
 * In-memory [BlockedUsersStore] — the real one needs DataStore for its offline
 * mirror, which a JVM test can't build. Keeps the same contract that matters to
 * the ViewModels: the set flips immediately, and [toggle] returns false without
 * having changed anything when the write is set to fail.
 */
class FakeBlockedUsersStore(initial: Set<String> = emptySet()) : BlockedUsersStore {
    private val state = MutableStateFlow(initial)
    override val blocked: StateFlow<Set<String>> = state

    /** Set to make the next [toggle] report a failed write. */
    var failWrites: Boolean = false

    var refreshCount = 0
        private set

    override suspend fun refresh() {
        refreshCount++
    }

    override suspend fun toggle(userId: String): Boolean {
        if (failWrites) return false
        val id = userId.lowercase()
        state.value = if (id in state.value) state.value - id else state.value + id
        return true
    }
}
