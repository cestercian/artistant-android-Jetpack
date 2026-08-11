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
    Disputed("disputed"),

    /**
     * A status this build doesn't recognise — i.e. the server has moved ahead of
     * the client (some future `refunded` / `expired` / …).
     *
     * Decode-only. [dbValue] is a sentinel that is NOT in the `bookings.status`
     * check constraint, and no write path serializes a variable status: create
     * sends `PendingConfirm.dbValue`, accept sends `Confirmed.dbValue`, and
     * cancel/decline go through the `cancel-booking` Edge Function without a
     * status string at all. So this case can never round-trip to the server.
     *
     * It exists because the decoder previously fell back to [PendingConfirm],
     * which is the opposite of neutral: it is the one state that renders the
     * artist's Accept/Decline CTAs, the client's cancel affordance and the
     * artist Home "New requests" bucket. An unrecognised status therefore
     * showed up as a live, actionable request, and Accept fired a status PATCH
     * against a booking the client cannot reason about. Everything keyed on
     * this case renders read-only and neutrally tinted (iOS PR #111 parity).
     */
    Unknown("unknown");

    val label: String
        get() = when (this) {
            PendingConfirm -> "Awaiting confirm"
            Confirmed -> "Confirmed"
            Completed -> "Completed"
            Cancelled -> "Cancelled"
            Disputed -> "Disputed"
            // Deliberately NOT "Unknown": iOS (Booking.swift) shows "Unavailable"
            // for the same case, and a booking that reads differently on the two
            // clients is a support call. The case name stays `Unknown` because it
            // describes the decode outcome; this string is the user-facing copy.
            Unknown -> "Unavailable"
        }

    companion object {
        /**
         * Null / unrecognised → [Unknown]. Never [PendingConfirm]: a booking the
         * client can't interpret must not be handed an Accept button.
         */
        fun fromDb(raw: String?): BookingStatus =
            entries.firstOrNull { it.dbValue == raw } ?: Unknown
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
    /**
     * Position of the booked tier in the artist's package list AT THE TIME OF
     * BOOKING — a pointer, not a fact about the gig.
     *
     * Kept only as the fallback behind [packageName]: an artist who reorders,
     * renames or drops a tier leaves every past booking's index pointing at
     * somebody else's package (or off the end of the list). See [packageName].
     */
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
    /**
     * The booked tier's name, snapshotted by the server at insert time
     * (`bookings.package_name`, present since migration 0001 and written on every
     * create by both clients).
     *
     * This is the tier's name as it was WHEN THE GIG WAS BOOKED, which is the
     * only honest thing to display: resolving `packageIndex` into the artist's
     * *current* package list means an artist reordering their tiers silently
     * relabels every booking they have ever taken. Nullable purely so decoding a
     * projection that didn't ask for the column can't fail — the column itself is
     * `not null` on the server, so no real row is missing it.
     */
    val packageName: String? = null,
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
    private val datePatterns = listOf(PATTERN, "MMM d, yyyy", "yyyy-MM-dd")

    fun weekdayString(epochMs: Long): String {
        val f = SimpleDateFormat(PATTERN, posix)
        f.timeZone = TimeZone.getDefault()
        return f.format(Date(epochMs))
    }

    /** Strict full-string parse of a stored date label into a Calendar. */
    fun parseLabel(dateLabel: String): java.util.Calendar? {
        val trimmed = dateLabel.trim()
        if (trimmed.isEmpty()) return null
        for (p in datePatterns) {
            parseStrict(trimmed, p, TimeZone.getDefault())?.let { d ->
                return java.util.Calendar.getInstance().apply { time = d }
            }
        }
        return null
    }

    /** Strict parse of date + wall-clock time (IST), trying every date pattern. */
    fun parseDateAndTime(dateLabel: String, timeLabel: String): Long? {
        val combined = "${dateLabel.trim()} ${timeLabel.trim()}"
        if (dateLabel.isBlank() || timeLabel.isBlank()) return null
        val ist = TimeZone.getTimeZone("Asia/Kolkata")
        for (p in datePatterns) {
            parseStrict(combined, "$p h:mm a", ist)?.time?.let { return it }
        }
        return null
    }

    private fun parseStrict(input: String, pattern: String, zone: TimeZone): Date? {
        val f = SimpleDateFormat(pattern, posix).apply {
            isLenient = false
            timeZone = zone
        }
        val pos = java.text.ParsePosition(0)
        val d = f.parse(input, pos) ?: return null
        if (pos.index != input.length) return null
        return d
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
            val f = SimpleDateFormat(pattern, Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val pos = java.text.ParsePosition(0)
            val d = f.parse(raw, pos)
            if (d != null && pos.index == raw.length) return d.time
        }
    }
    BookingDateFormat.parseDateAndTime(date, time)?.let { return it }
    return BookingDateFormat.parseLabel(date)?.apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }?.timeInMillis
}
