package `in`.artistant.app.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
     * A review just landed.
     *
     * Idempotent by construction: the flag is already true after the first call,
     * so a recomposition that replays the event cannot re-arm a prompt the user
     * has since dismissed — [shouldPromptForRating] reads `asked` too.
     */
    fun recordReviewSubmitted() {
        viewModelScope.launch {
            val record = prefs.ratePrompt().copy(reviewSubmitted = true)
            prefs.setRatePrompt(record)
            _visible.value = shouldPromptForRating(record)
        }
    }

    /** "Rate on Google Play" — the store opens, and we never ask again. */
    fun rated() {
        _visible.value = false
        viewModelScope.launch {
            prefs.setRatePrompt(prefs.ratePrompt().copy(asked = true, rated = true))
        }
    }

    /** "Not now", the close cross, and a scrim tap. All the same answer. */
    fun dismiss() {
        _visible.value = false
        viewModelScope.launch { prefs.setRatePrompt(prefs.ratePrompt().copy(asked = true)) }
    }
}
