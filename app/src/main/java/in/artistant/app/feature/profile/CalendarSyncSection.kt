package `in`.artistant.app.feature.profile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.platform.calendar.CalendarInfo
import `in`.artistant.app.platform.calendar.CalendarSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Profile calendar-sync row. Thin over [CalendarSync]: re-exposes the toggle /
 * target flows and loads the writable-calendar list on demand. The just-in-time permission
 * REQUEST lives in the composable (launchers need an Activity); this VM only records the
 * grant result and surfaces the denied hint.
 */
@HiltViewModel
class CalendarSyncViewModel @Inject constructor(
    private val calendar: CalendarSync,
) : ViewModel() {

    val isEnabled: StateFlow<Boolean> = calendar.isEnabled
    val targetCalendarId: StateFlow<Long?> = calendar.targetCalendarId

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    private val _permissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = _permissionDenied.asStateFlow()

    /**
     * Flip the toggle. [permissionGranted] is the launcher's just-in-time result (true when
     * already granted). Enabling with the grant denied snaps the toggle back (setEnabled
     * returns false because the service re-checks the live permission) and shows the hint.
     */
    fun setEnabled(on: Boolean, permissionGranted: Boolean) {
        viewModelScope.launch {
            if (on && !permissionGranted) {
                _permissionDenied.value = true
                return@launch
            }
            val ok = calendar.setEnabled(on)
            _permissionDenied.value = on && !ok
            if (ok && on) _calendars.value = calendar.writableCalendars()
        }
    }

    fun refreshCalendars() {
        viewModelScope.launch { _calendars.value = calendar.writableCalendars() }
    }

    fun setTarget(id: Long) {
        viewModelScope.launch { calendar.setTarget(id) }
    }
}

/**
 * "Sync gigs to calendar" — the permissioned auto-mirror toggle (iOS Profile row). Requests
 * READ_CALENDAR + WRITE_CALENDAR just-in-time on enable; shows a target-calendar picker while
 * on, and a Settings hint if the grant was denied. Complements the always-available
 * zero-permission `AddToCalendarButton` — this is the opt-in ongoing mirror.
 */
@Composable
fun CalendarSyncSection(
    modifier: Modifier = Modifier,
    vm: CalendarSyncViewModel = hiltViewModel(),
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val context = LocalContext.current

    val enabled by vm.isEnabled.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val targetId by vm.targetCalendarId.collectAsStateWithLifecycle()
    val denied by vm.permissionDenied.collectAsStateWithLifecycle()

    // Just-in-time permission request. Both must be granted to write the mirror.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result[Manifest.permission.READ_CALENDAR] == true &&
            result[Manifest.permission.WRITE_CALENDAR] == true
        vm.setEnabled(on = true, permissionGranted = granted)
    }

    // Populate the picker when the toggle is already on at first render.
    LaunchedEffect(enabled) { if (enabled) vm.refreshCalendars() }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Sync gigs to calendar",
                    style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                Text(
                    "Mirror confirmed gigs to your device calendar.",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (!on) {
                        vm.setEnabled(on = false, permissionGranted = true)
                    } else if (hasCalendarPermission(context)) {
                        vm.setEnabled(on = true, permissionGranted = true)
                    } else {
                        permissionLauncher.launch(
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                        )
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.brandInk,
                    checkedTrackColor = colors.brand,
                    uncheckedThumbColor = colors.ink3,
                    uncheckedTrackColor = colors.bgSoft,
                ),
            )
        }

        // Target-calendar picker — only meaningful while the mirror is on.
        if (enabled && calendars.isNotEmpty()) {
            Spacer(Modifier.height(space.md))
            TargetCalendarPicker(
                calendars = calendars,
                selectedId = targetId,
                onSelect = vm::setTarget,
            )
        }

        if (denied) {
            Spacer(Modifier.height(space.sm))
            Text(
                "Calendar access is off. Enable it for Artistant in Settings, then try again.",
                style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
                color = colors.warm,
            )
        }
    }
}

/** A hairline row that opens a dropdown of the writable calendars (iOS `setTarget` picker). */
@Composable
private fun TargetCalendarPicker(
    calendars: List<CalendarInfo>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var open by remember { mutableStateOf(false) }
    // Fall back to the first writable calendar's name until the auto-pick resolves an id.
    val selectedName = calendars.firstOrNull { it.id == selectedId }?.displayName
        ?: calendars.firstOrNull()?.displayName
        ?: "Calendar"

    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = space.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            Text("Calendar", style = AppTheme.type.footnote, color = colors.ink3)
            Spacer(Modifier.weight(1f))
            Text(
                selectedName,
                style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                color = colors.ink,
            )
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = "Choose calendar",
                tint = colors.ink3,
                modifier = Modifier.height(AppTheme.dimens.size.iconMd),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            calendars.forEach { cal ->
                DropdownMenuItem(
                    text = { Text(cal.displayName, color = colors.ink) },
                    trailingIcon = {
                        if (cal.id == selectedId) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brand)
                        }
                    },
                    onClick = { onSelect(cal.id); open = false },
                )
            }
        }
    }
}

private fun hasCalendarPermission(context: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
