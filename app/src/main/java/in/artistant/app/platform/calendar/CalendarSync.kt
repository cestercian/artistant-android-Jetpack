package `in`.artistant.app.platform.calendar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calendar-sync seam (port of iOS `CalendarSyncService`, toggle surface only).
 * M6 PART 1 wires the Profile toggle against this interface; PART 2 supplies the
 * real Android Calendar-Provider-backed impl behind the SAME seam, so the screen
 * doesn't change. [isEnabled] drives the toggle; [setEnabled] returns whether the
 * flip succeeded (a permission denial returns false so the UI can snap the toggle
 * back + show the Settings hint).
 */
interface CalendarSync {
    val isEnabled: StateFlow<Boolean>

    /** Flip sync on/off. Returns false when turning ON but permission was denied. */
    suspend fun setEnabled(on: Boolean): Boolean
}

/**
 * Inert default bound until PART 2 lands the real impl. Persists nothing and always
 * reports "not enabled" — the toggle renders but does no calendar work yet. Kept a
 * @Singleton so its state survives across Profile recompositions like the real one
 * will. ponytail: this exists purely as the PART 2 seam; delete if PART 2 replaces
 * the binding rather than swapping this class.
 */
@Singleton
class NoopCalendarSync @Inject constructor() : CalendarSync {
    private val _enabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _enabled

    // PART 2: request WRITE_CALENDAR, mirror confirmed gigs, persist the map. For now
    // the flip is a pure UI state change with no side effects — never fails.
    override suspend fun setEnabled(on: Boolean): Boolean {
        _enabled.value = on
        return true
    }
}
