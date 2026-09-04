package `in`.artistant.app.feature.booking

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.EyebrowLabel
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.PrimaryButton
import `in`.artistant.app.designsystem.component.bookingStatusTone
import `in`.artistant.app.designsystem.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InvoiceUiState(
    val booking: Booking? = null,
    val artistName: String = "",
    val isLoading: Boolean = true,
    val loadError: String? = null,
)

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookingsRepository: BookingsRepository,
    private val artistsRepository: ArtistsRepository,
) : ViewModel() {
    private val bookingId: String = checkNotNull(savedStateHandle["bookingId"])

    private val _state = MutableStateFlow(InvoiceUiState())
    val state: StateFlow<InvoiceUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null) }
            // Guarded the way every other booking read is: `fetchOne` wraps every
            // transport failure in a BookingRepositoryError and rethrows, and
            // viewModelScope carries no handler, so an unguarded throw here walks
            // out to the thread's uncaught handler. Unlike the confirmation
            // screen, this one has NOTHING to fall back on — a record with no row
            // behind it is not a record — so a failure is a stated failure with a
            // retry, never an empty document.
            val booking = runCatching { bookingsRepository.fetchOne(bookingId) }
            val row = booking.getOrNull()
            val artist = row?.let { artistsRepository.find(it.artistId) }
            _state.update {
                it.copy(
                    booking = row,
                    artistName = artist?.name.orEmpty(),
                    isLoading = false,
                    loadError = when {
                        row != null -> null
                        booking.isFailure -> "Couldn't load this record."
                        else -> "This booking isn't available any more."
                    },
                )
            }
        }
    }
}

/**
 * Screen 132 — the booking record.
 *
 * The design's note is the whole brief: **"a record, not a tax invoice"**.
 * Because v1 moves no money, this document has to be careful about what it is,
 * and Indian GST rules are why. So it states the artist fee, states Artistant's
 * own fee as the zero it is, totals to the fee, and closes with the paragraph
 * that says who settled what with whom.
 *
 * Three things the design draws are not here, and each is a column we do not
 * have: the host's company and city, their GSTIN, and a "Travel — ₹0, within
 * city" line. A zero we cannot source is a number we made up.
 *
 * The CTA shares plain text rather than a PDF. A PDF of a document that is
 * careful not to be a tax invoice gets forwarded as one; the format carries an
 * implication the copy then has to spend a paragraph undoing.
 */
@Composable
fun InvoiceScreen(
    bookingId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InvoiceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val context = LocalContext.current
    val reference = bookingReference(bookingId)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        val booking = state.booking
        FunnelBar(
            title = "Invoice",
            subtitle = invoiceSubtitle(reference, booking?.date.orEmpty()),
            onLeading = onBack,
            trailing = if (booking != null) {
                {
                    IconCircle(
                        icon = Icons.Filled.IosShare,
                        contentDescription = "Share this record",
                        onClick = { shareRecord(context, booking, state.artistName, reference) },
                        size = dimens.component.iconCircleSm,
                    )
                }
            } else {
                null
            },
        )

        if (booking == null) {
            if (state.isLoading) {
                // Loading is not empty and not failed — the third state gets its
                // own words, per the design's own rule.
                Text(
                    "Reading the booking…",
                    style = AppTheme.type.body,
                    color = colors.ink3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimens.component.gutter),
                )
            } else {
                EmptyState(
                    title = "No record to show",
                    body = state.loadError,
                    actionLabel = "Try again",
                    onAction = viewModel::refresh,
                )
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.component.gutter)
                .padding(top = space.lg, bottom = space.xl),
            verticalArrangement = Arrangement.spacedBy(space.lg),
        ) {
            FunnelCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space.md)) {
                    Column(Modifier.weight(1f)) {
                        EyebrowLabel("Billed to")
                        Text(
                            // `bookings.client_name` (mig 0080), denormalized so
                            // it reads under RLS. Null only for rows written
                            // before the backfill — "You" is then the one true
                            // thing this screen can say about its own viewer.
                            booking.clientFullName?.trim()?.takeIf { it.isNotEmpty() } ?: "You",
                            style = AppTheme.type.rowTitle,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = space.sm),
                        )
                    }
                    // The design draws a lime "Settled" badge. There is no
                    // payment state on the row to settle, so the badge carries
                    // the booking's OWN status through the app's one
                    // status→tone map — a lime capsule over a cancelled gig
                    // would be the same lie in a different colour.
                    Pill(booking.status.label, tone = bookingStatusTone(booking.status))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                EyebrowLabel("The booking")
                invoiceBookingRows(booking, state.artistName).forEach { row ->
                    TermRow(label = row.label, value = row.amount)
                }
            }

            HRule()

            Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
                invoiceLines(booking).forEach { line ->
                    if (line.emphasis) HRule(Modifier.padding(vertical = space.xs))
                    TermRow(label = line.label, value = line.amount, emphasis = line.emphasis)
                }
            }

            NoteBlock(INVOICE_DISCLAIMER)
        }

        CtaBar {
            PrimaryButton(
                // NOT "Share PDF": this shares the record as text, and a button
                // that promises a file it does not produce is a support ticket.
                text = "Share this record",
                onClick = { shareRecord(context, booking, state.artistName, reference) },
                fullWidth = true,
            )
        }
    }
}

/** "#AR-3F9A2C · Sat, 12 Oct 2026" — whichever halves exist. */
internal fun invoiceSubtitle(reference: String, dateLabel: String): String? {
    val parts = listOf(
        reference.trim().takeIf { it.isNotEmpty() }?.let { "#$it" }.orEmpty(),
        dateLabel.trim(),
    ).filter { it.isNotEmpty() }
    return parts.joinToString(" · ").ifEmpty { null }
}

private fun shareRecord(
    context: Context,
    booking: Booking,
    artistName: String,
    reference: String,
) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, invoiceShareText(booking, artistName, reference))
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
