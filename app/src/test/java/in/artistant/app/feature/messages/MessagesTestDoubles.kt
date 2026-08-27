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
 * per file: a bookings repository that only answers the by-id reads these screens
 * make, and a flags store that keeps its sets in memory instead of DataStore.
 */

/**
 * Bookings, scriptable.
 *
 * The messages surfaces read bookings by id and nothing else: the inbox asks for
 * the ids its rows carry ([fetchMany]), the chat asks for the one behind its
 * thread ([fetchOne]). Everything they never call throws — including the two
 * seat lists, so a surface that goes back to pulling a whole booking history
 * fails loudly instead of passing against a no-op.
 */
open class StubBookings(
    private val bookings: List<Booking> = emptyList(),
    private val failFetch: Boolean = false,
    private val one: Booking? = null,
) : BookingsRepository {
    /** Every id set asked for, in order — one entry per load. */
    val fetchedIds = mutableListOf<List<String>>()

    var feedback: List<String> = emptyList()
        private set

    /** Set to make [submitFeedback] report a failed write. */
    var feedbackDelivers: Boolean = true

    override suspend fun fetchMany(ids: List<String>): List<Booking> {
        fetchedIds += ids
        if (failFetch) throw IllegalStateException("offline")
        val wanted = ids.map { it.lowercase() }.toSet()
        return bookings.filter { it.id.lowercase() in wanted }
    }

    override suspend fun fetchOne(id: String): Booking? = one?.takeIf { it.id == id }

    override suspend fun submitFeedback(body: String, isBug: Boolean): Boolean {
        feedback = feedback + body
        return feedbackDelivers
    }

    override suspend fun listForClient(): List<Booking> =
        error("messages reads bookings by id, never a whole seat list")

    override suspend fun listForArtist(): List<Booking> =
        error("messages reads bookings by id, never a whole seat list")

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
 * the ViewModels: [toggle] ALWAYS flips the set and emits before the write is
 * even attempted (mirroring [ServerBlockedUsersStore]'s optimistic update), and
 * on a failed write flips it back — recomputed from the CURRENT set, not the
 * pre-toggle snapshot, exactly as the real store does — and returns false.
 */
class FakeBlockedUsersStore(initial: Set<String> = emptySet()) : BlockedUsersStore {
    private val state = MutableStateFlow(initial)
    override val blocked: StateFlow<Set<String>> = state

    /** Set to make the next [toggle] report a failed write. */
    var failWrites: Boolean = false

    /**
     * Set to make [refresh] report that the server copy could NOT be read — the
     * offline / signed-out / migration-missing case. The set is left alone, as
     * the real store leaves it.
     */
    var refreshSucceeds: Boolean = true

    var refreshCount = 0
        private set

    override suspend fun refresh(): Boolean {
        refreshCount++
        return refreshSucceeds
    }

    override suspend fun toggle(userId: String): Boolean {
        val id = userId.lowercase()
        val wasBlocked = id in state.value
        state.value = if (wasBlocked) state.value - id else state.value + id
        if (failWrites) {
            // Roll back from wherever the set is NOW, not the pre-toggle
            // snapshot — another id may have flipped while this write was
            // "in flight", same as ServerBlockedUsersStore.toggle.
            state.value = if (wasBlocked) state.value + id else state.value - id
            return false
        }
        return true
    }
}
