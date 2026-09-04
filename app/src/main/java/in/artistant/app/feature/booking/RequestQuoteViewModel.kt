package `in`.artistant.app.feature.booking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.RequestsRepository
import `in`.artistant.app.data.repository.RequestsRepositoryError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * The occasions screen 17 offers, in the design's own order.
 *
 * A short closed list rather than a free-text field: it is the first thing an
 * artist reads and the thing that most changes the price, and four words are
 * easier to answer than an empty box. "Other" is the escape hatch, and the
 * free-text brief underneath carries whatever it turns out to be.
 */
val QuoteOccasions = listOf("Sangeet", "Brand event", "House show", "Other")

/** Cap on the free-text brief, matching the counter the design draws under it. */
const val QUOTE_NOTE_MAX = 500

/**
 * Digit caps on the two numeric fields.
 *
 * Not cosmetic. Both fields filtered to digits and nothing else, so a long paste
 * or a leaning finger produced a string `toIntOrNull()` cannot parse — and the
 * guest count then went out as `crowd_size = null`, i.e. the request reached the
 * artist with the head count silently missing. `Int.MAX_VALUE` is ten digits;
 * five holds 99,999 guests and nine holds ₹999,999,999, which are both an order
 * of magnitude past any real event.
 */
const val QUOTE_GUESTS_MAX_DIGITS = 5
const val QUOTE_BUDGET_MAX_DIGITS = 9

data class RequestQuoteUiState(
    val artistName: String = "",
    /**
     * The artist's published reply speed (`artists.response_label`, e.g. "< 24h"),
     * or blank when we could not load them. Blank hides the subtitle rather than
     * inventing a number — "usually replies in 2 hours" is a claim about a person.
     */
    val replyLabel: String = "",
    val occasion: String? = null,
    val dateEpochMs: Long = 0L,
    val dateLabel: String = "",
    val startTime: String = "",
    val timeSlots: List<String> = DefaultTimeSlots,
    val guests: String = "",
    val venue: String = "",
    val budgetInr: String = "",
    val note: String = "",
    // The date sheet's own month, kept here so it survives the sheet closing.
    val visibleYear: Int = 0,
    val visibleMonth: Int = 0,
    val monthDays: List<FunnelDay> = emptyList(),
    val selectableDays: Set<Int> = emptySet(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false,
) {
    val monthLabel: String get() = funnelMonthLabel(visibleYear, visibleMonth)
    val selectedDay: Int? get() = dayOfMonthIfIn(dateEpochMs, visibleYear, visibleMonth)
    val canStepBack: Boolean get() = isAfterCurrentMonth(visibleYear, visibleMonth)

    /**
     * The brief as the artist will read it — the same string [submit] sends.
     *
     * Derived rather than stored so the screen and the wire can never disagree
     * about what was asked for.
     */
    val briefMessage: String get() = quoteBriefMessage(occasion, startTime, note)
}

/**
 * Custom quote request — screen 17, "the brief, prefilled".
 *
 * The design's six fields are occasion, date, start, guests, venue and budget,
 * over a free-text brief. Four of them have a column on `gig_requests`
 * (`date_label`, `crowd_size`, `venue`, `proposed_amount_inr`) and the message.
 * **Occasion and start time do not**, so rather than collecting them and
 * dropping them on the floor they are composed into the message — see
 * [quoteBriefMessage]. That is what the column is for: it is the brief the
 * artist reads to price the night.
 *
 * Budget is a single amount, not the design's range. `proposed_amount_inr` is
 * one `not null` integer and the artist's accept/counter loop answers it; a
 * range would have to be flattened to a number somewhere, and doing that
 * silently is worse than asking for the number.
 */
@HiltViewModel
class RequestQuoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistsRepository: ArtistsRepository,
    private val requestsRepository: RequestsRepository,
) : ViewModel() {

    private val artistId: String = checkNotNull(savedStateHandle["artistId"])

    private val _state = MutableStateFlow(RequestQuoteUiState())
    val state: StateFlow<RequestQuoteUiState> = _state.asStateFlow()

    init {
        // The month opens on today with every future day live: this screen asks
        // for a quote on ANY date, so it carries no per-artist availability —
        // unlike the booking funnel, where the artist's calendar is the source of
        // truth. Seeded before the artist read so the picker is usable while it
        // is still in flight.
        val opening = firstOpenMonth()
        _state.update {
            it.copy(
                visibleYear = opening.year,
                visibleMonth = opening.month,
                monthDays = funnelMonthDays(opening.year, opening.month),
                selectableDays = opening.selectableDays,
            )
        }
        viewModelScope.launch {
            val artist = artistsRepository.find(artistId) ?: artistsRepository.ensureFull(artistId)
            _state.update {
                it.copy(
                    artistName = artist?.name.orEmpty(),
                    replyLabel = artist?.response.orEmpty(),
                    timeSlots = resolveTimeSlots(artist?.timeSlots.orEmpty()),
                )
            }
        }
    }

    fun selectOccasion(value: String) {
        // Tapping the chosen chip again clears it — the whole field is optional,
        // and a closed list with no way back is a trap.
        _state.update { it.copy(occasion = if (it.occasion == value) null else value) }
    }

    fun stepMonth(delta: Int) {
        _state.update { s ->
            if (delta < 0 && !s.canStepBack) return@update s
            val (year, month) = steppedMonth(s.visibleYear, s.visibleMonth, delta)
            s.copy(
                visibleYear = year,
                visibleMonth = month,
                monthDays = funnelMonthDays(year, month),
                selectableDays = monthSelectableDays(year, month),
            )
        }
    }

    fun selectDay(day: Int) {
        _state.update { s ->
            if (day !in s.selectableDays) return@update s
            val epoch = funnelDayEpochMs(s.visibleYear, s.visibleMonth, day)
            s.copy(dateEpochMs = epoch, dateLabel = BookingDateFormat.weekdayString(epoch))
        }
    }

    fun selectStartTime(slot: String) {
        _state.update { it.copy(startTime = if (it.startTime == slot) "" else slot) }
    }

    /**
     * Digits only, and no more of them than an `int` column can hold.
     *
     * The length cap lives HERE rather than at the field, so the bound belongs to
     * the draft instead of to whichever composable happens to be typing into it —
     * the same reason [setNote] is bounded in its setter. [quoteGuestsError] is
     * still checked at submit: a cap in a setter is not a guarantee about a value
     * that reached the state some other way.
     */
    fun setGuests(value: String) {
        _state.update {
            it.copy(
                guests = value.filter { c -> c.isDigit() }.take(QUOTE_GUESTS_MAX_DIGITS),
                errorMessage = null,
            )
        }
    }

    fun setVenue(value: String) {
        _state.update { it.copy(venue = value) }
    }

    fun setBudget(value: String) {
        _state.update {
            it.copy(
                budgetInr = value.filter { c -> c.isDigit() }.take(QUOTE_BUDGET_MAX_DIGITS),
                errorMessage = null,
            )
        }
    }

    /**
     * Bounded HERE rather than at the text field, so the cap belongs to the draft
     * instead of to one caller — a paste handler or a restore writing through
     * another path would otherwise slip past it.
     */
    fun setNote(value: String) {
        _state.update { it.copy(note = value.take(QUOTE_NOTE_MAX)) }
    }

    fun submit() {
        val s = _state.value
        val amount = s.budgetInr.toIntOrNull() ?: 0
        if (amount <= 0) {
            _state.update { it.copy(errorMessage = "Enter a budget above ₹0.") }
            return
        }
        if (s.dateLabel.isBlank()) {
            _state.update { it.copy(errorMessage = "Pick a date.") }
            return
        }
        // Checked rather than coerced. `toIntOrNull()` answers null for a value
        // this app cannot represent, and the write below turns that null into an
        // omitted `crowd_size` — so an unreadable guest count used to send a
        // brief that simply did not mention how many people were coming, with
        // nothing on screen saying so.
        quoteGuestsError(s.guests)?.let { message ->
            _state.update { it.copy(errorMessage = message) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, errorMessage = null) }
            try {
                requestsRepository.create(
                    artistId = artistId,
                    proposedAmountInr = amount,
                    dateLabel = s.dateLabel,
                    message = s.briefMessage.ifBlank { null },
                    venue = s.venue.ifBlank { null },
                    crowdSize = s.guests.toIntOrNull(),
                    expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7),
                )
                _state.update { it.copy(isSubmitting = false, success = true) }
            } catch (e: RequestsRepositoryError) {
                _state.update { it.copy(isSubmitting = false, errorMessage = e.message) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSubmitting = false, errorMessage = e.message ?: "Request failed.")
                }
            }
        }
    }
}

/**
 * The brief the artist actually receives, composed from the parts screen 17
 * collects.
 *
 * `gig_requests` has columns for the date, the venue, the head count and the
 * offer — and none for the occasion or the start time. Both change what an
 * artist charges, so the design asks for them; dropping them after the host
 * typed them would be worse than never asking. They lead the message as one fact
 * line, with the host's own words kept whole underneath as their own paragraph.
 *
 * Order is fixed and the separator is the app's own " · ", so an artist reading
 * their inbox sees the same shape every time. Nothing is invented: a request with
 * no occasion and no start time is exactly the note the host wrote, and a request
 * with neither is the empty string, which [RequestQuoteViewModel.submit] sends as
 * a null message rather than as a blank one.
 */
fun quoteBriefMessage(occasion: String?, startTime: String, note: String): String {
    val facts = listOf(
        occasion?.trim().orEmpty(),
        startTime.trim().let { if (it.isEmpty()) "" else "$it start" },
    ).filter { it.isNotEmpty() }.joinToString(" · ")
    val body = note.trim()
    return listOf(facts, body).filter { it.isNotEmpty() }.joinToString("\n")
}

/**
 * Why this guest count cannot be sent, or null when it can.
 *
 * Blank is fine — `gig_requests.crowd_size` is nullable and the design treats the
 * head count as optional. What is NOT fine is a string the column cannot hold:
 * `submit` maps it through `toIntOrNull()`, and a null there is indistinguishable
 * from "the host left it blank", so an eleven-digit paste reached the artist as a
 * request with no guest count at all rather than as an error the host could fix.
 *
 * Pure and separate from the setter's length cap on purpose: the cap stops the
 * common case at the keyboard, this stops every case at the write.
 */
fun quoteGuestsError(guests: String): String? {
    val digits = guests.trim()
    if (digits.isEmpty()) return null
    val value = digits.toIntOrNull()
    if (value == null || value <= 0 || value > QUOTE_GUESTS_MAX) {
        return "Enter a guest count between 1 and $QUOTE_GUESTS_MAX_LABEL."
    }
    return null
}

/** The largest head count [QUOTE_GUESTS_MAX_DIGITS] can express. */
const val QUOTE_GUESTS_MAX = 99_999
private const val QUOTE_GUESTS_MAX_LABEL = "99,999"
