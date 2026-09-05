package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class ActivityUiState(
    /** Everything in the log, newest first — before the chip is applied. */
    val all: List<ActivityEntry> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.All,
    /**
     * The rows that were still unread when this visit began — or null while
     * that question is unanswered.
     *
     * Opening the screen IS reading it, so [ActivityViewModel.markSeen] marks
     * the log read as soon as it is on screen; without that the bell on
     * Discover could only be cleared by finding and pressing "Mark all read",
     * so a user who read every notification here still carried a dot.
     *
     * But the design draws unread rows on this very screen — bold titles and
     * one accent disc — and flipping them the instant they appear would take
     * away the one thing the screen answers: *which of these had I not seen*.
     * So the ids are frozen here for the length of the visit while the STORE
     * goes read underneath. Leaving the screen ends the visit (a new
     * back-stack entry means a new ViewModel), and the next one starts clean.
     *
     * Null rather than empty because the two mean opposite things: null is
     * "not resolved yet" and renders each row on its own stored flag, empty is
     * "resolved, and nothing was unread".
     */
    val unreadOnArrival: Set<String>? = null,
) {
    /** The rows the chosen chip admits. */
    val visible: List<ActivityEntry> get() = all.filter { matchesFilter(it, filter) }

    /**
     * Does this row still draw as unread?
     *
     * Stored flag OR the arrival snapshot — see [unreadOnArrival].
     */
    fun showsUnread(entry: ActivityEntry): Boolean =
        !entry.read || entry.id in unreadOnArrival.orEmpty()

    /**
     * Is there anything left for "Mark all read" to do?
     *
     * Deliberately the STORED flag and not [showsUnread]: once the visit has
     * marked the log seen, the only way this goes true again is a push landing
     * while the screen is open, which is exactly the case the button still
     * exists for. Before that resolves it stays false, so the action does not
     * flash up and vanish on entry.
     */
    val hasUnread: Boolean get() = unreadOnArrival != null && unreadActivityCount(all) > 0

    /**
     * The one row that gets the accent disc — the newest unread.
     *
     * One accent per screen (REDESIGN_2026-09 §2), and the newest unread thing
     * is what the eye should land on. The design draws exactly this: two unread
     * rows, one accent circle, on the topmost.
     */
    val accentedId: String? get() = all.firstOrNull { showsUnread(it) }?.id
}

/**
 * Screen 123.
 *
 * Reads, plus the local read flag. Everything on this screen was written by the
 * FCM receive path ([in.artistant.app.platform.push.ArtistantMessagingService]);
 * the only writes from here are [markSeen] and "Mark all read", both of which
 * are a local flag and never a server call — there is no server-side read state
 * to sync with, and inventing one that dies on reinstall would be worse than
 * saying nothing.
 */
@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val log: ActivityLog,
) : ViewModel() {

    private val filter = MutableStateFlow(ActivityFilter.All)

    /** @see ActivityUiState.unreadOnArrival */
    private val unreadOnArrival = MutableStateFlow<Set<String>?>(null)

    /**
     * One snapshot per visit.
     *
     * [markSeen] runs from a composition effect, which re-runs after a
     * configuration change while this ViewModel survives it. A second snapshot
     * would be taken AFTER the first one marked everything read — it would find
     * nothing unread, and every row the user is looking at would lose its
     * unread treatment mid-visit.
     */
    private var seen = false

    val state: StateFlow<ActivityUiState> =
        combine(log.entries, filter, unreadOnArrival) { entries, chosen, arrival ->
            ActivityUiState(all = entries, filter = chosen, unreadOnArrival = arrival)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS),
            initialValue = ActivityUiState(),
        )

    fun select(next: ActivityFilter) {
        filter.value = next
    }

    /**
     * The screen is up: everything already in the log has now been seen.
     *
     * Unread meant "never marked" before this, and the only thing that could
     * mark it was a button in this screen's own header — so the bell on
     * Discover kept its dot after the user had read every row it stood for.
     * Seen is what unread should have meant all along.
     *
     * The rows keep their unread look for the rest of the visit; only the store
     * changes. See [ActivityUiState.unreadOnArrival].
     */
    fun markSeen() {
        if (seen) return
        seen = true
        viewModelScope.launch {
            val unread = log.entries.first().filter { !it.read }.map { it.id }.toSet()
            unreadOnArrival.value = unread
            // Exactly the rows this snapshot speaks for — `markAllRead()` spoke for rows it
            // had never seen. The read is a suspending DataStore collect and the write is
            // another, so a push CAN land between them; it is not in the snapshot, so the
            // screen never draws it unread, and marking it read anyway retired a notification
            // the account had demonstrably not seen. The header's own "Mark all read" is
            // still the control that speaks for everything, including that push.
            if (unread.isNotEmpty()) log.markRead(unread)
        }
    }

    /**
     * The header action, for the log that grew while the screen was open.
     *
     * Not folded into [markSeen]: this one is a deliberate press about every
     * row on screen, so it also drops the arrival snapshot and the rows lose
     * their unread look on the spot. [markSeen] keeps that look precisely
     * because the user has not said anything yet.
     */
    fun markAllRead() {
        viewModelScope.launch {
            unreadOnArrival.value = emptySet()
            log.markAllRead()
        }
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
