package `in`.artistant.app.feature.profile

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.platform.account.AccountService
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.calendar.CalendarSync
import `in`.artistant.app.state.BookingStore
import `in`.artistant.app.state.MessageStore
import `in`.artistant.app.state.RequestStore
import `in`.artistant.app.state.SavedStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

/** The three profile-header stats (iOS ProfileView statsRow). Extracted + pure so the
 *  count logic is unit-testable without constructing the (Context-bound) ViewModel. */
data class ProfileStats(val bookings: Int = 0, val saved: Int = 0, val completed: Int = 0)

/**
 * Bookings = total, Saved = size of the saved-id set (NOT the hydrated-card count,
 * which drops unknown ids), Completed = bookings whose status is Completed. The
 * "Completed" label is honest: it counts finished bookings, not reviews written.
 */
fun profileStats(bookings: List<Booking>, savedCount: Int): ProfileStats = ProfileStats(
    bookings = bookings.size,
    saved = savedCount,
    completed = bookings.count { it.status == BookingStatus.Completed },
)

/**
 * Client own-profile tab (port of iOS `ProfileView`). Surfaces the header
 * (name + "City · Host since YEAR"), the 3-col stats, the saved-artists carousel,
 * and drives the destructive account actions.
 *
 * Holds [SessionManager] for sign-out/delete + the member-since year, so — like
 * [in.artistant.app.ui.auth.AuthViewModel] — this VM isn't itself plain-JVM
 * constructible; the testable logic ([profileStats]) is extracted above and the
 * store-reset leakage invariant is proven at the store level.
 *
 * Sign-out / delete don't navigate: clearing the Supabase session flips
 * RootViewModel's gate to NotSignedIn, which tears down the client tabs and shows
 * the welcome flow. This VM's job is only to clear the session + wipe the
 * process-lived stores so the next account signed in on this device sees nothing.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val session: SessionManager,
    private val users: UsersRepository,
    private val account: AccountService,
    private val artistsRepo: ArtistsRepository,
    private val bookingStore: BookingStore,
    private val savedStore: SavedStore,
    private val messageStore: MessageStore,
    private val requestStore: RequestStore,
    private val calendarSync: CalendarSync,
) : ViewModel() {

    /** A hydrated saved-artist tile for the carousel (id + name + resolved gradient). */
    data class SavedArtistCard(val id: String, val name: String, val gradient: List<Color>)

    data class ProfileUiState(
        val name: String = "You",
        val subtitle: String = "",
        val stats: ProfileStats = ProfileStats(),
        val saved: List<SavedArtistCard> = emptyList(),
        /** Non-null flips the delete sheet to an error state instead of faking success. */
        val deleteError: String? = null,
    )

    private data class Header(val name: String = "You", val subtitle: String = "")

    private val _header = MutableStateFlow(Header())
    private val _saved = MutableStateFlow<List<SavedArtistCard>>(emptyList())
    private val _deleteError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        _header, bookingStore.bookingsFlow, savedStore.ids, _saved, _deleteError,
    ) { header, bookings, savedIds, saved, deleteError ->
        ProfileUiState(
            name = header.name,
            subtitle = header.subtitle,
            stats = profileStats(bookings, savedIds.size),
            saved = saved,
            deleteError = deleteError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    init {
        // Hydrate saved-artist cards whenever the id set changes (cold launch has ids
        // but no cached rows yet — the app pages the roster, so fetch-on-observe).
        viewModelScope.launch {
            savedStore.ids.collect { ids -> hydrateSaved(ids.toList()) }
        }
        refresh()
    }

    /** Pull server truth for the header + stores; the carousel re-hydrates off ids. */
    fun refresh() {
        viewModelScope.launch { bookingStore.refreshFromServer() }
        viewModelScope.launch { savedStore.refreshFromServer() }
        viewModelScope.launch { loadHeader() }
    }

    private suspend fun loadHeader() {
        // fetchSelfProfile throws on a network/RLS blip; degrade to the "You" default
        // rather than surfacing an error on a passive header.
        val profile = runCatching { users.fetchSelfProfile() }.getOrNull()
        val role = profile?.role ?: AppRole.Client
        // "Host" reads naturally for clients (they host the event); artists are "Artist".
        val roleNoun = if (role == AppRole.Client) "Host" else "Artist"
        // Member-since year from the auth user's createdAt (ISO string prefix); current
        // year is the least-surprising fallback when there's no live session.
        val year = session.currentUser?.createdAt?.toString()?.take(4)?.toIntOrNull()
            ?: Year.now().value
        val city = profile?.city?.trim().orEmpty()
        val name = profile?.fullName?.trim()?.ifBlank { null } ?: "You"
        val suffix = "$roleNoun since $year"
        _header.value = Header(
            name = name,
            subtitle = if (city.isBlank()) suffix else "$city · $suffix",
        )
    }

    private suspend fun hydrateSaved(ids: List<String>) {
        if (ids.isEmpty()) {
            _saved.value = emptyList()
            return
        }
        // Best-effort batched full hydrate; then read whatever's cached (unknown/RLS-hidden
        // ids are simply skipped, matching iOS savedArtists).
        runCatching { artistsRepo.fetchArtists(ids) }
        _saved.value = artistsRepo.cachedArtists(ids)
            .map { SavedArtistCard(it.id, it.name, it.gradient) }
    }

    /** Sign out: clear the Supabase session + wipe local per-user state. */
    fun signOut() {
        viewModelScope.launch {
            // signOut() also wipes prefs (role → Client default) + cancels wizard uploads.
            session.signOut()
            wipeLocalUserState()
        }
    }

    /**
     * DPDP §11(1)(b) erasure. The local wipe runs ONLY after the server delete
     * succeeds — wiping unconditionally would make a transient failure look like a
     * successful deletion while the backend account (and its data) stayed alive and
     * resurrected on the next sign-in. On failure we surface the error + keep the
     * user signed in to retry (iOS ProfileView delete flow).
     */
    fun deleteAccount() {
        viewModelScope.launch {
            _deleteError.value = null
            try {
                account.deleteAccount()
            } catch (t: Throwable) {
                _deleteError.value =
                    "Couldn't delete your account: ${t.message ?: "please try again"}."
                return@launch
            }
            // Remove the mirrored calendar events + wipe the sync state BEFORE the session
            // clears — sign-out deliberately keeps calendar state, so delete-account is the
            // only path that erases it (else the user's gigs would orphan on their calendar
            // with no handle to clean them up). iOS parity: setEnabled(false) before wipeAll.
            calendarSync.wipeForAccountDeletion()
            // Clear the cached session too — the server bans the user, but the local token
            // stays valid (~1h) and would keep authorizing calls: a DPDP-erasure gap.
            session.signOut()
            wipeLocalUserState()
        }
    }

    /**
     * Clears every process-lived per-user store. These @Singletons outlive a sign-out,
     * so without an explicit reset the prior user's bookings/saved/chats/requests leak
     * into the next account signed in on this device (the iOS wipeLocalUserState invariant).
     */
    private fun wipeLocalUserState() {
        bookingStore.reset()
        savedStore.reset()
        messageStore.reset()
        requestStore.reset()
    }
}
