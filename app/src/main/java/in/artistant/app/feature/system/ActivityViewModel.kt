package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ActivityUiState(
    /** Everything in the log, newest first — before the chip is applied. */
    val all: List<ActivityEntry> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.All,
) {
    /** The rows the chosen chip admits. */
    val visible: List<ActivityEntry> get() = all.filter { matchesFilter(it, filter) }

    val hasUnread: Boolean get() = unreadActivityCount(all) > 0

    /**
     * The one row that gets the accent disc — the newest unread.
     *
     * One accent per screen (REDESIGN_2026-09 §2), and the newest unread thing
     * is what the eye should land on. The design draws exactly this: two unread
     * rows, one accent circle, on the topmost.
     */
    val accentedId: String? get() = all.firstOrNull { !it.read }?.id
}

/**
 * Screen 123.
 *
 * Reads only. Everything on this screen was written by the FCM receive path
 * ([in.artistant.app.platform.push.ArtistantMessagingService]); the only write
 * from here is "Mark all read", which is a local flag and never a server call —
 * there is no server-side read state to sync with, and inventing one that dies
 * on reinstall would be worse than saying nothing.
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val log: ActivityLog,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.All)

    val state: StateFlow<ActivityUiState> =
        combine(log.entries, filter) { entries, chosen ->
            ActivityUiState(all = entries, filter = chosen)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS),
            initialValue = ActivityUiState(),
        )

    fun select(next: ActivityFilter) {
        filter.value = next
    }

    fun markAllRead() {
        viewModelScope.launch { log.markAllRead() }
    }

    private companion object {
        const val SUBSCRIPTION_GRACE_MS = 5_000L
    }
}

/**
 * Midnight this morning, in the device's own zone.
 *
 * Read at the call site rather than held in state: it is a fact about the clock,
 * not about the screen, and a value captured when the ViewModel was built is
 * wrong for anybody who leaves the app open across midnight. [groupActivity]
 * takes it as a parameter for the same reason — so the split itself stays pure.
 */
fun startOfToday(zone: ZoneId = ZoneId.systemDefault()): Long =
    LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
