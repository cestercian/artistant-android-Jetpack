package `in`.artistant.app.feature.booking

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedEndEpochMs
import `in`.artistant.app.data.model.resolvedStartEpochMs
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.SecondaryButton
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Everything on this screen has a defensible default.
 *
 * That is deliberate, and it is what lets the read below fail quietly: with no
 * booking loaded the screen still reads "Request sent." over the terms the funnel
 * just filed, which is exactly what happened. There is no `isLoading` here
 * because there is nothing to withhold while the read is in flight.
 */
data class ConfirmedUiState(
    val artistName: String = "",
    val booking: Booking? = null,
    val status: BookingStatus = BookingStatus.PendingConfirm,
)

@HiltViewModel
class ConfirmedViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingsRepository: BookingsRepository,
    private val artistsRepository: ArtistsRepository,
) : ViewModel() {
    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"])
    private val _state = MutableStateFlow(ConfirmedUiState())
    val state: StateFlow<ConfirmedUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Guarded the same way BookingDetailViewModel.refresh guards it, and
            // for a sharper reason. `fetchOne` wraps EVERY transport failure in
            // BookingRepositoryError.Underlying and rethrows — it returns null
            // only for a genuine zero-row read — and viewModelScope carries no
            // CoroutineExceptionHandler, so an unguarded throw here walks out to
            // the thread's uncaught handler and kills the process. Losing
            // connectivity a second after the request lands would crash the one
            // screen whose entire job is to tell the client their request went
            // through, over a booking the server already has.
            val booking = runCatching { bookingsRepository.fetchOne(bookingId) }.getOrNull()
            val artist = booking?.let { artistsRepository.find(it.artistId) }
            _state.update {
                it.copy(
                    artistName = artist?.name.orEmpty(),
                    booking = booking,
                    status = booking?.status ?: BookingStatus.PendingConfirm,
                )
            }
        }
    }
}

/**
 * Screen 07 — the funnel's last page.
 *
 * The design's note: **say the outcome, not "success"**. The headline is the
 * sentence the host will repeat out loud — "You've got a band on Saturday." —
 * over a card carrying the reference, the when, the set, the where and the
 * agreed fee, and two actions at the bottom.
 *
 * Two branches, because the funnel files a **request**, not a confirmation. A
 * booking lands `pending_confirm` for the artist to accept (mig 0098), so on the
 * ordinary path the outcome is "Your request is with them" and the actions are
 * the record and the way out. The design's own headline and its "Message the
 * band" CTA belong to the confirmed case — threads are created on booking
 * confirm (mig 0015), so before that there is no conversation to open. Drawing
 * the confirmed page over a pending row would promise a band that has not said
 * yes.
 *
 * The design's "confirmed in 4 minutes" clause is absent: nothing on the row
 * records when the artist answered, and a duration we cannot measure is one we
 * would be making up.
 */
@Composable
fun ConfirmedScreen(
    onViewBooking: (bookingId: String) -> Unit,
    onBackToDiscover: () -> Unit,
    bookingId: String,
    modifier: Modifier = Modifier,
    onOpenInvoice: (bookingId: String) -> Unit = {},
    onMessageArtist: (artistId: String) -> Unit = {},
    viewModel: ConfirmedViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val context = LocalContext.current
    val confirmed = ui.status == BookingStatus.Confirmed
    val who = ui.artistName.ifBlank { "The artist" }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter)
                .padding(top = space.xxl, bottom = space.xl),
        ) {
            OutcomeMark()
            Text(
                confirmedHeadline(confirmed, ui.booking?.date.orEmpty()),
                style = AppTheme.type.displayHero,
                color = colors.ink,
                modifier = Modifier.padding(top = space.xl),
            )
            Text(
                if (confirmed) {
                    "$who confirmed. Contact details are unlocked in your thread."
                } else {
                    "Your request is with $who — we'll notify you the moment they answer."
                },
                style = AppTheme.type.body,
                color = colors.ink4,
                modifier = Modifier.padding(top = space.md),
            )

            val booking = ui.booking
            if (booking != null) {
                FunnelCard(Modifier.padding(top = space.xl)) {
                    ActRow(
                        name = who,
                        thumbSize = dimens.size.avatarLg,
                        lines = listOfNotNull(
                            bookingReference(booking.id)
                                .takeIf { it.isNotBlank() }
                                ?.let { "Booking #$it" },
                        ),
                    )
                    HRule(Modifier.padding(vertical = space.lg))
                    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                        confirmedTerms(booking).forEach { row ->
                            TermRow(label = row.label, value = row.amount)
                        }
                    }
                }
            }
        }

        CtaBar {
            if (confirmed && ui.booking != null) {
                PrimaryButton(
                    text = "Message ${ui.artistName.ifBlank { "the artist" }}",
                    onClick = { onMessageArtist(ui.booking!!.artistId) },
                    fullWidth = true,
                )
                SecondaryButton(
                    text = "Add to calendar",
                    onClick = { addToCalendar(context, ui.booking!!) },
                    fullWidth = true,
                    modifier = Modifier.padding(top = space.md),
                    leading = {
                        Icon(
                            Icons.Filled.CalendarMonth,
                            contentDescription = null,
                            tint = colors.ink,
                            modifier = Modifier.size(dimens.size.iconLg),
                        )
                    },
                )
            } else {
                PrimaryButton(
                    text = "View booking",
                    onClick = { onViewBooking(bookingId) },
                    fullWidth = true,
                )
                SecondaryButton(
                    text = "Back to discover",
                    onClick = onBackToDiscover,
                    fullWidth = true,
                    modifier = Modifier.padding(top = space.md),
                )
            }
            if (ui.booking != null) {
                // The record is one tap from here, which is where the design puts
                // the invoice's other entrance (screen 132 is reachable "from
                // Confirmed and from Booking detail").
                Text(
                    "See the record",
                    style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accentInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInvoice(bookingId) }
                        .padding(top = space.lg),
                )
            }
        }
    }
}

/**
 * The sentence the host repeats out loud.
 *
 * Names the DAY rather than the date when the label carries a weekday, because
 * "You've got a band on Saturday" is how anyone actually says this and
 * "…on Sat, Oct 12, 2026" is how nobody does. The pending branch says what is
 * true instead — the request is filed and the artist has not answered.
 */
internal fun confirmedHeadline(confirmed: Boolean, dateLabel: String): String {
    if (!confirmed) return "Request sent."
    val weekday = weekdayWord(dateLabel)
    return if (weekday == null) "You've got your act." else "You've got a band on $weekday."
}

/**
 * "Sat, Oct 12, 2026" → "Saturday". Null when the label is not one we wrote.
 *
 * A label this app did not write survives as null rather than as a guess: the
 * headline drops the day clause instead of naming the wrong one.
 */
private fun weekdayWord(dateLabel: String): String? {
    val abbrev = dateLabel.trim().takeWhile { it.isLetter() }.lowercase()
    return WEEKDAY_WORDS[abbrev]
}

private val WEEKDAY_WORDS = mapOf(
    "mon" to "Monday",
    "tue" to "Tuesday",
    "wed" to "Wednesday",
    "thu" to "Thursday",
    "fri" to "Friday",
    "sat" to "Saturday",
    "sun" to "Sunday",
)

/**
 * The four facts the card states: when, what, where, and what was agreed.
 *
 * Reuses [InvoiceLine] because it is the same shape — a label and a value — and
 * the two screens have to agree about the booking they are both describing.
 * Blank fields are dropped rather than rendered empty.
 */
internal fun confirmedTerms(booking: Booking): List<InvoiceLine> = buildList {
    val whenLine = listOf(booking.date, booking.time)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" · ")
    if (whenLine.isNotEmpty()) add(InvoiceLine("When", whenLine))
    booking.packageName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(InvoiceLine("Set", it)) }
    booking.venue.trim()
        .takeIf { it.isNotEmpty() && !it.equals("TBD", ignoreCase = true) }
        ?.let { add(InvoiceLine("Where", it)) }
    // "· direct" is not decoration: it is the one word that says nobody is
    // holding this money, which is the whole of v1's position.
    add(InvoiceLine("Agreed fee", "${formatInr(booking.fee)} · direct"))
}

/**
 * Zero-permission calendar handoff — the system Calendar app owns the compose UI
 * and we never read the store.
 *
 * `resolvedStartEpochMs` rather than a local ISO parse: `Instant.parse` is
 * ISO_INSTANT, which rejects the numeric-offset form PostgREST emits for a
 * `timestamptz`, and it has nothing to fall back on when the column is missing
 * from a projection. The resolver tries the offset patterns and then the
 * date+time labels this very screen is displaying.
 */
private fun addToCalendar(context: Context, booking: Booking) {
    val startMs = booking.resolvedStartEpochMs() ?: return
    val endMs = booking.resolvedEndEpochMs() ?: (startMs + DEFAULT_GIG_MS)
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "Artistant · ${booking.venue}")
        .putExtra(CalendarContract.Events.EVENT_LOCATION, booking.venue)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMs)
    booking.venueNotes?.takeIf { it.isNotBlank() }?.let {
        intent.putExtra(CalendarContract.Events.DESCRIPTION, it)
    }
    runCatching { context.startActivity(intent) }
}

/** Placeholder gig length when the row carries no end time — matches create(). */
private const val DEFAULT_GIG_MS = 2L * 60 * 60 * 1000
