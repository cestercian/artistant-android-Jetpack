package `in`.artistant.app.feature.profile

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.data.repository.ExportResult
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
import `in`.artistant.app.feature.saved.SavedStore
import `in`.artistant.app.platform.auth.SessionManager
import `in`.artistant.app.platform.calendar.CalendarSyncService
import `in`.artistant.app.platform.storage.AppPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ProfileUiState(
    val profile: SelfProfile? = null,
    val role: AppRole = AppRole.Client,
    val isLoading: Boolean = true,
    val error: String? = null,
    val actionMessage: String? = null,
    val actionError: String? = null,
    val isDeleting: Boolean = false,
    val isExporting: Boolean = false,
    val pendingExport: ExportResult? = null,
    val showSignOutConfirm: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showHelp: Boolean = false,
    val calendarSyncEnabled: Boolean = false,
    val calendarHasPermission: Boolean = false,
    val calendarTitle: String = "Artistant",
    val calendars: List<CalendarSyncService.CalendarOption> = emptyList(),
    /**
     * The signed-in account's email, straight off the cached auth session (the
     * `public.users` row does not carry one). Read-only identity, rendered
     * MASKED — see [maskedEmail]. Null for a session without an email address,
     * which is legitimate: OAuth providers can withhold it, and the row is
     * simply omitted then rather than showing a blank value.
     */
    val email: String? = null,
    /**
     * The calendar year this account was created, off the same cached auth
     * session as [email]. Null when there is no session to read — a provider can
     * withhold the date, and a signed-out state has none at all.
     */
    val joinedYear: Int? = null,
    /** Profile stats — Bookings = still live (see [liveBookingsCount]), Completed = finished. */
    val bookingsCount: Int = 0,
    val savedCount: Int = 0,
    val completedCount: Int = 0,
    val feedbackSending: Boolean = false,
    val feedbackStatus: String? = null,
    val feedbackOk: Boolean = false,
) {
    val displayName: String
        get() = profile?.fullName?.trim()?.takeIf { it.isNotEmpty() } ?: "You"

    /**
     * The year the identity strip and [subtitle] print. [joinedYear] when the
     * session answered, otherwise today — the least-surprising fallback, and the
     * one iOS uses, since a made-up vintage is worse than a current one.
     */
    val vintageYear: Int
        get() = joinedYear ?: Calendar.getInstance().get(Calendar.YEAR)

    val subtitle: String
        get() {
            val city = profile?.city?.trim().orEmpty()
            val roleNoun = if (role == AppRole.Client) "Host" else "Artist"
            val suffix = "$roleNoun since $vintageYear"
            return if (city.isBlank()) suffix else "$city · $suffix"
        }

    val handleLabel: String?
        get() = profile?.handle?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }

    /** The email row's rendered value, or null when there is no email to show. */
    val maskedEmail: String?
        get() = email?.trim()?.takeIf { it.isNotEmpty() }?.let(::maskEmail)

    val subscriptionsEnabled: Boolean get() = AppEnvironment.subscriptionsEnabled
}

/**
 * Render-time PII mask for the account email: `yashafaid@gmail.com` →
 * `y•••d@gmail.com`.
 *
 * Masking happens at RENDER, never in storage — the stored value is untouched,
 * we just refuse to put the whole address on a screen that a shoulder-surfer or
 * a screenshot can read. Keeping the first and last character of the local part
 * is what lets the owner still recognise WHICH of their addresses is signed in,
 * which is the entire point of showing the row.
 *
 * Anything that is not a single-`@` address is returned verbatim rather than
 * mangled: a value we cannot parse is a value we cannot safely shorten, and a
 * half-masked string would be worse than either extreme. A one-character local
 * part has no distinct last character, so it degrades to `x•••@domain`.
 */
internal fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    // Needs a local part, an @, and a domain — reject 0 matches, a leading @,
    // a trailing @, and (via lastIndexOf) any address carrying a second @.
    if (at <= 0 || at == email.length - 1 || at != email.lastIndexOf('@')) return email
    val local = email.substring(0, at)
    val domain = email.substring(at)
    val head = local.first()
    val tail = local.drop(1).takeLast(1)
    return "$head•••$tail$domain"
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UsersRepository,
    private val account: AccountRepository,
    private val session: SessionManager,
    private val prefs: AppPreferences,
    private val calendarSync: CalendarSyncService,
    private val savedStore: SavedStore,
    val bookingsRepository: BookingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            calendarSync.ui.collect { cal ->
                _state.update {
                    it.copy(
                        calendarSyncEnabled = cal.enabled,
                        calendarHasPermission = cal.hasPermission,
                        calendarTitle = cal.calendarTitle,
                        calendars = cal.calendars,
                    )
                }
            }
        }
        viewModelScope.launch {
            savedStore.ids.collect { ids ->
                _state.update { it.copy(savedCount = ids.size) }
            }
        }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        val role = prefs.role.first()
        // Off the cached session, not a network call — safe to read on every
        // refresh, and it is the only place the account email and the signup
        // date exist (the `public.users` row carries neither). Published BEFORE
        // the profile fetch so the identity row survives a failed refresh: both
        // are already known locally, and blanking them because an unrelated
        // request 500'd would be inventing a gap.
        val user = session.currentUser
        _state.update {
            it.copy(
                email = user?.email,
                joinedYear = user?.createdAt?.toEpochMilliseconds()?.let { ms ->
                    Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)
                },
            )
        }
        runCatching { users.fetchSelfProfile() }
            .onSuccess { profile ->
                _state.update {
                    it.copy(
                        profile = profile,
                        role = profile?.role ?: role,
                        isLoading = false,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Couldn't load profile")
                }
            }
        // Best-effort stats — don't blank the profile if bookings fail.
        runCatching { bookingsRepository.listForClient() }
            .onSuccess { bookings ->
                _state.update {
                    it.copy(
                        bookingsCount = liveBookingsCount(bookings),
                        completedCount = bookings.count { b -> b.status == BookingStatus.Completed },
                        savedCount = savedStore.ids.value.size,
                    )
                }
            }
        runCatching { savedStore.refreshFromServer() }
    }

    fun showSignOutConfirm() = _state.update { it.copy(showSignOutConfirm = true) }
    fun dismissSignOutConfirm() = _state.update { it.copy(showSignOutConfirm = false) }

    fun showHelp() = _state.update {
        it.copy(showHelp = true, feedbackStatus = null, feedbackOk = false, feedbackSending = false)
    }
    fun dismissHelp() = _state.update {
        it.copy(showHelp = false, feedbackStatus = null, feedbackOk = false, feedbackSending = false)
    }

    fun submitFeedback(body: String, isBug: Boolean) = viewModelScope.launch {
        if (_state.value.feedbackSending) return@launch
        _state.update { it.copy(feedbackSending = true, feedbackStatus = null) }
        val ok = bookingsRepository.submitFeedback(body, isBug)
        _state.update {
            it.copy(
                feedbackSending = false,
                feedbackOk = ok,
                feedbackStatus = if (ok) {
                    "Thanks — we got it."
                } else {
                    "Couldn't send — check your connection and try again."
                },
            )
        }
    }

    fun signOut() = viewModelScope.launch {
        _state.update { it.copy(showSignOutConfirm = false) }
        calendarSync.clearSessionState()
        runCatching { session.signOut() }
            .onFailure { e ->
                _state.update { it.copy(actionError = e.message ?: "Sign out failed") }
            }
    }

    fun showDeleteConfirm() = _state.update { it.copy(showDeleteConfirm = true, actionError = null) }
    fun dismissDeleteConfirm() = _state.update { it.copy(showDeleteConfirm = false) }

    /** Server delete FIRST — local wipe only after success (DPDP §11 / PR #60). */
    fun deleteAccount() = viewModelScope.launch {
        _state.update { it.copy(isDeleting = true, actionError = null) }
        runCatching { account.deleteAccount() }
            .onSuccess {
                calendarSync.wipeForAccountDelete()
                _state.update { it.copy(isDeleting = false, showDeleteConfirm = false) }
                session.signOut()
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        isDeleting = false,
                        actionError = e.message ?: "Account deletion failed",
                    )
                }
            }
    }

    fun exportData() = viewModelScope.launch {
        _state.update { it.copy(isExporting = true, actionError = null, actionMessage = null) }
        runCatching { account.requestDataExport() }
            .onSuccess { result ->
                _state.update { it.copy(isExporting = false, pendingExport = result) }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(isExporting = false, actionError = e.message ?: "Export failed")
                }
            }
    }

    fun clearPendingExport() = _state.update { it.copy(pendingExport = null) }

    fun clearActionFeedback() = _state.update { it.copy(actionMessage = null, actionError = null) }

    fun manageAvailabilityMissingNav() {
        _state.update {
            it.copy(actionMessage = "Open Profile from the artist Home tab to manage availability.")
        }
    }

    fun setCalendarSyncEnabled(on: Boolean) = viewModelScope.launch {
        val ok = calendarSync.setEnabled(on)
        if (!ok) {
            _state.update {
                it.copy(actionMessage = "Calendar permission is required to sync gigs.")
            }
        }
    }

    fun selectCalendar(id: Long) = viewModelScope.launch {
        calendarSync.selectCalendar(id)
    }

    fun onCalendarPermissionResult(granted: Boolean) = viewModelScope.launch {
        if (granted) {
            calendarSync.setEnabled(true)
        } else {
            _state.update {
                it.copy(actionMessage = "Calendar permission denied — enable it in system Settings.")
            }
        }
    }
}

/**
 * How many of [bookings] are still LIVE — the number under the profile header's
 * "Bookings" column, read beside "Completed".
 *
 * The shipped rule was `status != Completed`, i.e. "everything that hasn't
 * finished". That put cancelled and disputed bookings — and `Unknown`, the
 * decode fallback for a status this build can't interpret — in the same column a
 * user reads as "work I have on". Cancel all three of your bookings and the
 * header still said 3, next to a Completed column reading 0, which describes a
 * user with three live bookings rather than one with none.
 *
 * So the stat means *in flight*: awaiting the artist's answer, or accepted and
 * not yet played. Cancelled and disputed are terminal — the booking is over,
 * it just didn't happen — and `Unknown` is by construction uninterpretable, so
 * none of them can be asserted to be live.
 *
 * Written as an exhaustive `when` over an allow-list rather than a `!=` chain,
 * for two reasons. The compiler fails the build when a case is added to
 * [BookingStatus], so a new server status has to be classified here instead of
 * being silently swept into "live" — which is exactly how `Unknown` ended up
 * counted. And the safe default when someone does classify it is the one this
 * shape encourages: not-live.
 *
 * A confirmed booking whose date has already passed still counts as live. The
 * server flips it to `completed` on its own schedule, and the Completed column
 * is keyed strictly on that status — so excluding it here would drop the booking
 * out of BOTH columns until the server caught up. Better slightly stale than
 * briefly invisible.
 *
 * Note this is deliberately narrower than the drill-down list the stat opens,
 * which still shows cancelled bookings behind a red pill: the column answers
 * "how much do I have going", the list is where a cancelled booking stays
 * visible at all (the Bookings tab filters it out entirely).
 */
fun liveBookingsCount(bookings: List<Booking>): Int = bookings.count { it.status.isLive() }

private fun BookingStatus.isLive(): Boolean = when (this) {
    BookingStatus.PendingConfirm, BookingStatus.Confirmed -> true
    BookingStatus.Completed,
    BookingStatus.Cancelled,
    BookingStatus.Disputed,
    BookingStatus.Unknown,
    -> false
}

/** Build a share intent for an inline JSON export. */
fun exportShareIntent(json: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_SUBJECT, "Artistant data export")
        putExtra(Intent.EXTRA_TEXT, json)
    }

/** Open a signed export URL in the browser. */
fun exportViewIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
