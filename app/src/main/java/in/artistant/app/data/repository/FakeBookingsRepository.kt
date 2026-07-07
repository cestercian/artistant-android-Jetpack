package `in`.artistant.app.data.repository

import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.domain.booking.BookingMath
import `in`.artistant.app.platform.payments.PaymentResult
import java.time.Instant
import java.util.UUID

/**
 * In-memory [BookingsRepository] for tests / previews. [create] runs the SAME fee
 * path as the real repo (resolve the package price via [artists] → [BookingMath]),
 * so a money-math end-to-end test asserts on real numbers. [cancel] flips the row
 * to Cancelled + escrow Refunded (what the real Edge Function does server-side).
 */
class FakeBookingsRepository(
    private val artists: ArtistsRepository = FakeArtistsRepository(),
    seed: List<Booking> = emptyList(),
) : BookingsRepository {

    val bookings: MutableList<Booking> = seed.toMutableList()

    override suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking {
        val fee = draft.fee(artists)
        val c = BookingMath.compute(fee)
        val booking = Booking(
            id = UUID.randomUUID().toString(),
            artistId = draft.artistId.lowercaseUuid(),
            packageIndex = draft.packageIndex,
            dateLabel = draft.date,
            timeLabel = draft.time,
            startDatetime = null,
            endDatetime = null,
            venue = draft.venue.ifEmpty { "TBD" },
            guests = draft.guests,
            fee = fee,
            platformFee = c.platform,
            gst = c.gst,
            total = c.total,
            status = BookingStatus.Confirmed,
            escrowStatus = EscrowStatus.Held,
            paymentMethod = draft.paymentMethod,
            protectionEnabled = true,
            createdAt = Instant.now(),
        )
        bookings.add(0, booking)
        return booking
    }

    override suspend fun listForClient(): List<Booking> = bookings.toList()

    override suspend fun listForArtist(): List<Booking> = bookings.toList()

    override suspend fun cancel(id: String): Booking {
        val idx = bookings.indexOfFirst { it.id == id }
        require(idx >= 0) { "no such booking $id" }
        val updated = bookings[idx].copy(
            status = BookingStatus.Cancelled,
            escrowStatus = EscrowStatus.Refunded,
        )
        bookings[idx] = updated
        return updated
    }
}
