package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Bookings awaiting artist Accept/Decline — mirrors iOS `newRequestsSection` filter. */
internal fun pendingConfirmBookings(bookings: List<Booking>): List<Booking> =
    bookings.filter { it.status == BookingStatus.PendingConfirm }

/** Confirmed upcoming shows for the "Up next" rail (not pending, not past cancel). */
internal fun upcomingConfirmed(bookings: List<Booking>): List<Booking> =
    bookings.filter { it.status == BookingStatus.Confirmed }

/** Open + countered quote requests — iOS RequestStore open rail. */
internal fun openGigRequests(requests: List<StoredRequest>): List<StoredRequest> =
    requests.filter { it.status == GigRequestStatus.Open || it.status == GigRequestStatus.Countered }

/** Display label for artist-side rows — never fall back to venue (pre-0080 "TBD" trap). */
internal fun artistClientDisplayName(booking: Booking): String =
    booking.clientFullName?.takeIf { it.isNotBlank() } ?: "Client"

/**
 * Daily fee totals for the last [days] IST calendar days (oldest → newest).
 * Confirmed + completed only — pending money isn't "earned" yet (matchmaker posture).
 */
internal fun earningsSparkline(bookings: List<Booking>, days: Int = 7): List<Int> {
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    val today = Calendar.getInstance(ist).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val totals = IntArray(days)
    for (b in bookings) {
        if (b.status != BookingStatus.Confirmed && b.status != BookingStatus.Completed) continue
        val epoch = b.createdAtEpochMs
        val dayCal = Calendar.getInstance(ist).apply {
            timeInMillis = epoch
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((today.timeInMillis - dayCal.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
        if (diffDays in 0 until days) {
            totals[days - 1 - diffDays] += b.fee
        }
    }
    return totals.toList()
}

/** Next [count] IST day labels ("Mon 26") for the busy strip, starting today. */
internal fun nextDayLabels(count: Int = 14): List<Pair<String, String>> {
    val ist = TimeZone.getTimeZone("Asia/Kolkata")
    val cal = Calendar.getInstance(ist)
    val dayFmt = java.text.SimpleDateFormat("EEE", Locale.US)
    val keyFmt = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ist }
    return (0 until count).map {
        val key = keyFmt.format(cal.time)
        val label = dayFmt.format(cal.time)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        key to label
    }
}
