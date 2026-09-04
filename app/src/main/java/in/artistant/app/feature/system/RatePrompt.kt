package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject

/**
 * May the rating prompt appear right now? (design screen 138)
 *
 * The design's note — *asked at the right moment* — is the entire specification,
 * and it says what NOT to do more clearly than what to do: never on launch. So
 * the rule is stated as three conditions, all of which must hold, and the first
 * one is the trigger rather than a timer:
 *
 *  - [reviewSubmitted]: the user just told us the gig went well. That is the
 *    good outcome the prompt rides on, and nothing else in the app is one — a
 *    completed booking is not (it may have gone badly), and a launch certainly
 *    is not.
 *  - not [asked]: once, ever. "Not now" that returns next week is how an app
 *    earns the one-star review it was fishing for.
 *  - not [rated]: somebody who already went to the store is done.
 *
 * Pure, and separated from the ViewModel, because "when do we ask" is the part
 * of this feature that is worth being able to read and pin in a test.
 */
fun shouldPromptForRating(record: RatePromptRecord): Boolean =
    record.reviewSubmitted && !record.asked && !record.rated

/**
 * Owns the prompt's eligibility, and the record behind it.
 *
 * `@Singleton`-scoped state would be wrong here — the record is persisted — but
 * the ViewModel IS shared by the two callers that matter, because both live in
 * the client tab scaffold: the booking-detail destination that reports a
 * submitted review, and the host that draws the sheet.
 */
@HiltViewModel
class RatePromptViewModel @Inject constructor(
    private val prefs: SystemPreferences,
) : ViewModel() {

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    /**
     * One transition at a time.
     *
     * All three entry points are read-modify-write over the same record, and all
     * three are fired from UI callbacks that can genuinely overlap: the scaffold
     * arms the prompt from a composition effect that re-runs on a configuration
     * change, while the sheet's own buttons dismiss it. Unserialized, an arm
     * whose read happened before a `rated()` write can complete afterwards and
     * put `asked`/`rated` back to false — and the prompt the user has already
     * answered comes back.
     *
     * The lock is also what makes the "terminal state wins" check below sound:
     * reading and writing inside it is the only way `asked || rated` can be
     * trusted between the two.
     */
    private val transitions = Mutex()

    /**
     * A review just landed.
     *
     * Idempotent, and now explicitly so: a record that is already `asked` or
     * `rated` has answered this question for good, so a replayed event neither
     * writes nor re-opens. [shouldPromptForRating] would refuse the prompt
     * anyway; refusing the WRITE is what stops a stale arm regressing the flags.
     */
    fun recordReviewSubmitted() {
        viewModelScope.launch {
            transitions.withLock {
                val record = prefs.ratePrompt()
                if (record.asked || record.rated) return@withLock
                val next = record.copy(reviewSubmitted = true)
                persist(next)
                _visible.value = shouldPromptForRating(next)
            }
        }
    }

    /** "Rate on Google Play" — the store opens, and we never ask again. */
    fun rated() {
        viewModelScope.launch {
            transitions.withLock {
                // Recorded BEFORE the sheet goes, so there is no window in which
                // the prompt is invisible and the record still says it was never
                // answered — which is the state a process death turns into a
                // second ask.
                persist(prefs.ratePrompt().copy(asked = true, rated = true))
                _visible.value = false
            }
        }
    }

    /** "Not now", the close cross, and a scrim tap. All the same answer. */
    fun dismiss() {
        viewModelScope.launch {
            transitions.withLock {
                persist(prefs.ratePrompt().copy(asked = true))
                _visible.value = false
            }
        }
    }

    /**
     * A failed write must not leave the sheet up.
     *
     * The lock cannot make a disk error go away; what it can do is guarantee the
     * attempt happened before the prompt closes. If the store refuses, the user
     * still gets the answer they pressed and the worst case is one repeated ask
     * on a later launch — strictly better than a modal that will not close.
     */
    private suspend fun persist(record: RatePromptRecord) {
        runCatching { prefs.setRatePrompt(record) }
            .onFailure { Timber.w(it, "Couldn't record the rating prompt state") }
    }
}
