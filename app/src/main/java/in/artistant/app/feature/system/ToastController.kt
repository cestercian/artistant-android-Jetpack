package `in`.artistant.app.feature.system

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One transient confirmation, on its way to the app's single toast host
 * (design screen 77) — the Android port of iOS `ToastCenter`.
 *
 * A process-wide singleton rather than per-screen state, because the fact a
 * toast confirms often outlives the screen that caused it: "Request sent." fires
 * as the funnel pops back to a profile, and a toast owned by the popped screen
 * dies with it. Features call [show]; exactly one host renders whatever is
 * current.
 *
 * The HOST owns the display window and calls [dismiss] when it elapses. That
 * split matters: the delay has to be cancelled and restarted when a second toast
 * replaces the first, and a `LaunchedEffect` keyed on [ToastMessage.id] does
 * that for free where a timer in here would have to be hand-managed.
 */
@Singleton
class ToastController @Inject constructor() {

    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    private var nextId = 0L

    /**
     * Raise a toast. The copy states the FACT — "Venue address copied", not
     * "Success!" — which is the house rule the design's own examples follow.
     *
     * A second call replaces the first immediately rather than queueing. Two
     * stacked toasts are unreadable, and the newer fact is the one the user just
     * caused.
     */
    fun show(text: String, icon: ToastIcon = ToastIcon.Confirm) {
        if (text.isBlank()) return
        nextId += 1
        _current.value = ToastMessage(id = nextId, text = text, icon = icon)
    }

    /**
     * Clear [id], but only if it is still the one showing.
     *
     * Unconditional clearing is the classic bug (iOS `ToastCenter` carries the
     * same guard): a toast raised inside the previous one's display window would
     * be cut short by the first one's timer, because that timer has no idea it
     * has been superseded.
     */
    fun dismiss(id: Long?) {
        if (id == null || _current.value?.id == id) _current.value = null
    }
}

/**
 * The glyph in the toast's accent disc.
 *
 * An enum rather than an `ImageVector` so [ToastController] — and every feature
 * that calls it — stays free of a Compose dependency. The host maps it.
 */
enum class ToastIcon { Confirm, Flag, Info }

/**
 * One toast.
 *
 * [id] is what makes a repeat of the same string a NEW toast: the host keys its
 * display timer on it, and two identical texts in a row would otherwise share a
 * key, leaving the second one to be dismissed by the first one's already-running
 * delay.
 */
data class ToastMessage(
    val id: Long,
    val text: String,
    val icon: ToastIcon = ToastIcon.Confirm,
)
