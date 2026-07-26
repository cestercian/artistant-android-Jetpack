package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus

/** Bookings awaiting artist Accept/Decline — mirrors iOS `newRequestsSection` filter. */
internal fun pendingConfirmBookings(bookings: List<Booking>): List<Booking> =
    bookings.filter { it.status == BookingStatus.PendingConfirm }

/** Display label for artist-side rows — never fall back to venue (pre-0080 "TBD" trap). */
internal fun artistClientDisplayName(booking: Booking): String =
    booking.clientFullName?.takeIf { it.isNotBlank() } ?: "Client"
