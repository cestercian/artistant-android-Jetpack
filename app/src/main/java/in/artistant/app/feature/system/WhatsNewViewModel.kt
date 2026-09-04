package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides once, at the root, whether screen 137 appears.
 *
 * The version it compares against is [BuildConfig.VERSION_NAME] — the binary's
 * own name, read at the one place that can honestly know it. Nothing else in the
 * app needs to; that is why it is read here rather than plumbed through
 * [ReleaseNotes], which stays a plain table a unit test can read.
 */
@HiltViewModel
class WhatsNewViewModel @Inject constructor(
    private val prefs: SystemPreferences,
) : ViewModel() {

    private val _visibleNote = MutableStateFlow<ReleaseNote?>(null)

    /** The note to present, or null for "nothing to say on this launch". */
    val visibleNote: StateFlow<ReleaseNote?> = _visibleNote.asStateFlow()

    private val currentVersion = BuildConfig.VERSION_NAME

    init {
        viewModelScope.launch {
            val note = ReleaseNotes.forVersion(currentVersion)
            when (decideWhatsNew(prefs.whatsNewSeenVersion(), currentVersion, note != null)) {
                WhatsNewDecision.Show -> _visibleNote.value = note
                // Recorded now rather than on dismissal, because there is no
                // dismissal to hang it on: nothing is shown, and an unrecorded
                // version re-runs this decision on every launch of this build.
                WhatsNewDecision.RecordSilently -> prefs.setWhatsNewSeenVersion(currentVersion)
                WhatsNewDecision.Nothing -> Unit
            }
        }
    }

    /**
     * The account list's "What's new" row — the way back to the sheet.
     *
     * The launch trigger fires once per version and is right to; this is what
     * makes the notes readable afterwards, so it deliberately ignores the
     * seen-version record instead of consulting [decideWhatsNew].
     *
     * It presents THIS build's notes and falls back to the most recent release
     * that has any, so a patch release with no entry of its own still opens
     * something true. The sheet stamps the version it describes, so the fallback
     * cannot pass an older release off as this one.
     */
    fun showOnDemand() {
        _visibleNote.value = ReleaseNotes.forVersion(currentVersion) ?: ReleaseNotes.mostRecent()
    }

    /**
     * Close and remember. Both the cross and "Got it" land here — neither is
     * more of an acknowledgement than the other, so treating them differently
     * would only produce a way to see the same sheet twice.
     *
     * The flow is cleared FIRST so the sheet leaves immediately; the write is a
     * DataStore edit and the user should not watch it happen.
     */
    fun acknowledge() {
        if (_visibleNote.value == null) return
        _visibleNote.value = null
        viewModelScope.launch { prefs.setWhatsNewSeenVersion(currentVersion) }
    }
}
