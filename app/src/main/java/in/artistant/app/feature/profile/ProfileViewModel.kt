package `in`.artistant.app.feature.profile

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.data.model.SelfProfile
import `in`.artistant.app.data.repository.AccountRepository
import `in`.artistant.app.data.repository.ExportResult
import `in`.artistant.app.data.repository.UsersRepository
import `in`.artistant.app.designsystem.theme.AppRole
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
    val calendarSyncEnabled: Boolean = false,
    val calendarHasPermission: Boolean = false,
    val calendarTitle: String = "Artistant",
    val calendars: List<CalendarSyncService.CalendarOption> = emptyList(),
) {
    val displayName: String
        get() = profile?.fullName?.trim()?.takeIf { it.isNotEmpty() } ?: "You"

    val subtitle: String
        get() {
            val city = profile?.city?.trim().orEmpty()
            val roleNoun = if (role == AppRole.Client) "Host" else "Artist"
            val year = Calendar.getInstance().get(Calendar.YEAR)
            val suffix = "$roleNoun since $year"
            return if (city.isBlank()) suffix else "$city · $suffix"
        }

    val handleLabel: String?
        get() = profile?.handle?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }

    val subscriptionsEnabled: Boolean get() = AppEnvironment.subscriptionsEnabled
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val users: UsersRepository,
    private val account: AccountRepository,
    private val session: SessionManager,
    private val prefs: AppPreferences,
    private val calendarSync: CalendarSyncService,
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
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        val role = prefs.role.first()
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
    }

    fun showSignOutConfirm() = _state.update { it.copy(showSignOutConfirm = true) }
    fun dismissSignOutConfirm() = _state.update { it.copy(showSignOutConfirm = false) }

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
