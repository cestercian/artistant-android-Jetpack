package `in`.artistant.app.data.model

import `in`.artistant.app.domain.booking.BookingMath
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Port of iOS `Models/Booking.swift` + draft shape from `BookingStore`.
 * Status enum matches the DB check constraint; create always lands
 * `pending_confirm` (request→accept). Money columns still persist
 * platform/GST even though matchmaker UI shows artist fee only.
 */

enum class BookingStatus(val dbValue: String) {
    PendingConfirm("pending_confirm"),
    Confirmed("confirmed"),
    Completed("completed"),
    Cancelled("cancelled"),
    Disputed("disputed");

    val label: String
        get() = when (this) {
            PendingConfirm -> "Awaiting confirm"
            Confirmed -> "Confirmed"
            Completed -> "Completed"
            Cancelled -> "Cancelled"
            Disputed -> "Disputed"
        }

    companion object {
        fun fromDb(raw: String?): BookingStatus =
            entries.firstOrNull { it.dbValue == raw } ?: PendingConfirm
    }
}

enum class EscrowStatus(val dbValue: String) {
    Held("held"),
    Released("released"),
    Refunded("refunded");

    companion object {
        fun fromDb(raw: String?): EscrowStatus =
            entries.firstOrNull { it.dbValue == raw } ?: Held
    }
}

enum class PaymentMethod(val dbValue: String) {
    Upi("upi"),
    Card("card"),
    Split("split");

    val label: String
        get() = when (this) {
            Upi -> "UPI"
            Card -> "Card"
            Split -> "Split"
        }

    companion object {
        fun fromDb(raw: String?): PaymentMethod =
            entries.firstOrNull { it.dbValue == raw } ?: Upi
    }
}

data class Booking(
    val id: String,
    val artistId: String,
    val packageIndex: Int,
    val date: String,
    val time: String,
    val venue: String,
    val guests: Int,
    val fee: Int,
    val platformFee: Int,
    val gst: Int,
    val total: Int,
    val status: BookingStatus,
    val escrowStatus: EscrowStatus,
    val paymentMethod: PaymentMethod,
    val protectionEnabled: Boolean,
    val createdAtEpochMs: Long,
    /** Artist-side display name — prefer embed, else 0080 `client_name`. */
    val clientFullName: String? = null,
    val startDatetimeIso: String? = null,
    val endDatetimeIso: String? = null,
    val venueNotes: String? = null,
)

/**
 * In-memory compose draft. Fee/package snapshot fields are filled by the
 * ViewModel from [Artist] so [BookingsRepository.create] never needs the
 * artist cache (keeps Fake tests offline).
 */
data class BookingDraft(
    val artistId: String,
    val packageIndex: Int = 0,
    val packageName: String = "Custom",
    val packageDuration: String = "Custom",
    val feeInr: Int = 0,
    val date: String,
    val dateRawEpochMs: Long,
    val time: String,
    val venue: String = "",
    val guests: Int = 100,
    val paymentMethod: PaymentMethod = PaymentMethod.Upi,
    val venueNotes: String = "",
) {
    val charges get() = BookingMath.compute(feeInr)
}

object BookingDateFormat {
    /** Writer/reader contract — same as iOS `Booking.dateFormat`. */
    const val PATTERN = "EEE, MMM d, yyyy"

    private val posix = Locale.US

    fun weekdayString(epochMs: Long): String {
        val f = SimpleDateFormat(PATTERN, posix)
        f.timeZone = TimeZone.getDefault()
        return f.format(Date(epochMs))
    }

    /** Parse a stored date label into a Calendar (local wall clock). */
    fun parseLabel(dateLabel: String): java.util.Calendar? {
        val formats = listOf(PATTERN, "MMM d, yyyy", "yyyy-MM-dd")
        for (p in formats) {
            val d = runCatching { SimpleDateFormat(p, posix).parse(dateLabel) }.getOrNull() ?: continue
            return java.util.Calendar.getInstance().apply { time = d }
        }
        return null
    }
}

/**
 * Gig start clock — prefer `start_datetime`, else date+time labels (IST),
 * else start-of-day from the date label. Mirrors iOS `Booking.resolvedStart`.
 */
fun Booking.resolvedStartEpochMs(): Long? {
    startDatetimeIso?.let { raw ->
        `in`.artistant.app.common.util.SupabaseISO8601.parse(raw)?.toEpochMilli()?.let { return it }
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
        )
        for (pattern in formats) {
            runCatching {
                val f = SimpleDateFormat(pattern, Locale.US)
                f.timeZone = TimeZone.getTimeZone("UTC")
                f.parse(raw)?.time
            }.getOrNull()?.let { return it }
        }
    }
    if (date.isNotBlank() && time.isNotBlank()) {
        val f = SimpleDateFormat("EEE, MMM d, yyyy h:mm a", Locale.US)
        f.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        runCatching { f.parse("$date $time")?.time }.getOrNull()?.let { return it }
    }
    return BookingDateFormat.parseLabel(date)?.apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }?.timeInMillis
}
