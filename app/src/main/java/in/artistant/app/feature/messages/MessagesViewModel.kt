package `in`.artistant.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.Thread
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.state.BookingStore
import `in`.artistant.app.state.DeepLinkRouter
import `in`.artistant.app.state.MessageStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the shared `MessagesScreen` (port of iOS `MessagesView`). Owns the
 * two-stage hydrate — pull the thread set + each thread's messages ([MessageStore.
 * refreshFromServer]), THEN hydrate the client-viewer's artist names by id so each
 * row resolves a counterpart. Counterpart resolution + preview redaction are pure
 * functions the composable calls per row, so both are directly unit-testable.
 *
 * Role isn't held here: the scaffold knows it statically (client vs artist tab) and
 * passes it into [counterpartName]. That keeps the VM free of the DataStore/Context
 * role source and trivially constructible in a plain JVM test.
 */
@HiltViewModel
class MessagesViewModel @Inject constructor(
    private val store: MessageStore,
    private val artists: ArtistsRepository,
    private val bookings: BookingStore,
    private val deepLink: DeepLinkRouter,
) : ViewModel() {

    val threads: StateFlow<List<Thread>> = store.threads

    /** A parked push thread id → the screen pushes Chat then [consumePendingThread]. */
    val pendingThreadId: StateFlow<String?> = deepLink.pendingThreadId

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    // Surfaced as a banner (kept honest — a failed refresh must not read as "no
    // conversations"). MessageStore swallows load failures into its `errors`
    // SharedFlow, so without this collector a failure would render an empty list.
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // First load hasn't finished yet — drives the skeleton (distinct from an
    // empty-but-loaded list, which shows the empty state).
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch { store.errors.collect { _error.value = it } }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _error.value = null
            if (!initial) _refreshing.value = true
            store.refreshFromServer()
            // Stage 2: hydrate the client-viewer's thread artists by id so each row
            // resolves a name (the app pages the roster — artists aren't eager-loaded).
            // A no-op for the artist viewer (its counterpart is the denormalized
            // thread.client_name, already on the thread — no fetch).
            artists.fetchArtists(store.threads.value.map { it.artistId })
            _refreshing.value = false
            _loading.value = false
        }
    }

    fun consumePendingThread() = deepLink.consumePendingThread()

    /**
     * The OTHER party's display name from the viewer's POV. Artist viewer → the
     * client (via the denormalized `threads.client_name`); client viewer → the
     * artist (resolved from the by-id [ArtistsRepository] cache). Pure over [role]
     * + the caches so it's unit-testable.
     */
    fun counterpartName(thread: Thread, role: AppRole): String =
        if (role == AppRole.Artist) {
            thread.clientName?.takeIf { it.isNotBlank() } ?: "Client"
        } else {
            artists.find(thread.artistId)?.name ?: "Artist"
        }

    /**
     * The row preview, redaction applied. The last body is already server-redacted
     * off the wire; this re-applies the client mirror so an un-confirmed thread's
     * snippet can't leak contact info even in an optimistic/echoed body. Redaction
     * LIFTS only once the thread's booking is confirmed/completed (resolved from
     * [BookingStore]); unknown/absent booking → redact (the safe direction).
     */
    fun preview(thread: Thread): String {
        val body = thread.messages.lastOrNull()?.body ?: return ""
        return store.displayBody(body, thread, bookingConfirmed(thread))
    }

    private fun bookingConfirmed(thread: Thread): Boolean {
        val status = thread.bookingId?.let { bookings.booking(it)?.status } ?: return false
        return status == BookingStatus.Confirmed || status == BookingStatus.Completed
    }
}
