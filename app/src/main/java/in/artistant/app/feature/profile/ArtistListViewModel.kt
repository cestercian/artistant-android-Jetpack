package `in`.artistant.app.feature.profile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedStartEpochMs
import `in`.artistant.app.data.repository.ArtistsRepository
import `in`.artistant.app.data.repository.BookingsRepository
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.feature.saved.SavedStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistListRow(
    val id: String,
    val artistId: String?,
    val bookingId: String?,
    val artist: Artist?,
    val fallbackTitle: String,
    val pills: List<Pair<String, PillTone>>,
)

data class ArtistListUiState(
    val kind: ArtistListKind = ArtistListKind.Saved,
    val rows: List<ArtistListRow> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ArtistListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookings: BookingsRepository,
    private val artists: ArtistsRepository,
    private val savedStore: SavedStore,
) : ViewModel() {

    private val kind = ArtistListKind.fromRaw(savedStateHandle.get<String>("kind").orEmpty())

    private val _state = MutableStateFlow(ArtistListUiState(kind = kind))
    val state: StateFlow<ArtistListUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching {
            when (kind) {
                ArtistListKind.Saved -> loadSaved()
                ArtistListKind.Bookings, ArtistListKind.Completed -> loadBookings()
            }
        }.onSuccess { rows ->
            _state.update { it.copy(rows = rows, isLoading = false) }
        }.onFailure { e ->
            _state.update {
                it.copy(isLoading = false, error = e.message ?: "Couldn't load list")
            }
        }
    }

    private suspend fun loadSaved(): List<ArtistListRow> {
        savedStore.refreshFromServer()
        val ids = savedStore.ids.value.toList()
        return ids.map { id ->
            val artist = artists.ensureFull(id) ?: artists.find(id)
            ArtistListRow(
                id = id,
                artistId = id,
                bookingId = null,
                artist = artist,
                fallbackTitle = artist?.name ?: "Artist",
                pills = emptyList(),
            )
        }
    }

    private suspend fun loadBookings(): List<ArtistListRow> {
        val all = bookings.listForClient()
        val source = if (kind == ArtistListKind.Completed) {
            all.filter { it.status == BookingStatus.Completed }
        } else {
            all.filter { it.status != BookingStatus.Completed }
        }
        val sorted = source.sortedWith { lhs, rhs ->
            val l = lhs.resolvedStartEpochMs()
            val r = rhs.resolvedStartEpochMs()
            when {
                l != null && r != null ->
                    if (kind == ArtistListKind.Completed) r.compareTo(l) else l.compareTo(r)
                l != null -> -1
                r != null -> 1
                else -> lhs.id.compareTo(rhs.id)
            }
        }
        return sorted.map { booking ->
            val artistId = booking.artistId.lowercase()
            val artist = artists.ensureFull(artistId) ?: artists.find(artistId)
            ArtistListRow(
                id = booking.id.lowercase(),
                artistId = artistId,
                bookingId = booking.id.lowercase(),
                artist = artist,
                fallbackTitle = booking.venue,
                pills = pillsFor(booking),
            )
        }
    }

    private fun pillsFor(booking: Booking): List<Pair<String, PillTone>> {
        val tone = when (booking.status) {
            BookingStatus.Confirmed -> PillTone.Brand
            BookingStatus.PendingConfirm -> PillTone.Warm
            BookingStatus.Completed -> PillTone.Good
            BookingStatus.Cancelled, BookingStatus.Disputed -> PillTone.Hot
        }
        val out = mutableListOf(booking.status.label.uppercase() to tone)
        booking.date.trim().takeIf { it.isNotEmpty() }?.let {
            out += it.uppercase() to PillTone.Neutral
        }
        return out
    }
}
