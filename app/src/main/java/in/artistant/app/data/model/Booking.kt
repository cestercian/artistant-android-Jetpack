package `in`.artistant.app.data.model

import androidx.compose.ui.graphics.Color
import `in`.artistant.app.designsystem.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Booking-funnel domain models — ports of iOS `Models/Booking.swift`. Plain data
 * classes; the DTOs in `data/model/dto` decode Postgres and map INTO them. Money
 * math lives in `domain/booking/BookingMath` (India 5% platform + 18% GST).
 */

/**
 * Lifecycle of a booking. Rawvalues match the `bookings.status` enum verbatim so
 * `fromDb`/`dbValue` round-trip. Participants may only walk
 * pending_confirm→confirmed / →cancelled (API_MAPPING §3); completed/disputed are
 * service-role only. [calendarTint] is the single source for the month-calendar /
 * schedule-row tint — takes [AppColors] so it stays token-driven (no raw hex).
 */
enum class BookingStatus(val dbValue: String, val label: String) {
    PendingConfirm("pending_confirm", "Awaiting confirm"),
    Confirmed("confirmed", "Confirmed"),
    Completed("completed", "Completed"),
    Cancelled("cancelled", "Cancelled"),
    Disputed("disputed", "Disputed");

    fun calendarTint(colors: AppColors): Color = when (this) {
        Confirmed -> colors.good
        PendingConfirm -> colors.warm
        Completed -> colors.ink3
        Cancelled, Disputed -> colors.hot
    }

    companion object {
        /** Unknown/absent → PendingConfirm (iOS decode fallback). */
        fun fromDb(raw: String?): BookingStatus =
            entries.firstOrNull { it.dbValue == raw } ?: PendingConfirm
    }
}

/** Escrow state. Insert-time is clamped to `held` by the DB (API_MAPPING §3). */
enum class EscrowStatus(val dbValue: String, val label: String) {
    Held("held", "Held in escrow"),
    Released("released", "Released to artist"),
    Refunded("refunded", "Refunded");

    companion object {
        fun fromDb(raw: String?): EscrowStatus =
            entries.firstOrNull { it.dbValue == raw } ?: Held
    }
}

/** How the client paid (dormant in v1 matchmaker; the seam stays for M7). */
enum class PaymentMethod(val dbValue: String, val label: String) {
    Upi("upi", "UPI"),
    Card("card", "Card"),
    Split("split", "Split");

    companion object {
        fun fromDb(raw: String?): PaymentMethod =
            entries.firstOrNull { it.dbValue == raw } ?: Upi
    }
}

/**
 * A persisted booking (iOS `Booking`). [clientFullName] is populated only by
 * `listForArtist` via the `client:users!client_id(full_name)` embed — null on the
 * client's own list (their name comes from their profile). [startDatetime] /
 * [endDatetime] are the machine show-window; [resolvedStart]/[resolvedEnd] fall
 * back to parsing the display labels for the rare row that lacks them.
 */
data class Booking(
    val id: String,
    val artistId: String,
    val packageIndex: Int,
    val dateLabel: String,          // "Sat, May 16, 2026"
    val timeLabel: String,          // "8:30 PM"
    val startDatetime: Instant?,
    val endDatetime: Instant?,
    val venue: String,
    val guests: Int,
    val fee: Int,                   // performance fee, INR
    val platformFee: Int,           // 5% of fee
    val gst: Int,                   // 18% of (fee + platform)
    val total: Int,                 // fee + platform + gst
    val status: BookingStatus,
    val escrowStatus: EscrowStatus,
    val paymentMethod: PaymentMethod,
    val protectionEnabled: Boolean,
    val createdAt: Instant?,
    val clientFullName: String? = null,
)

// --- Resolved show window (calendar sync) --------------------------------
// Port of iOS `Booking.resolvedStart/resolvedEnd`. Every server row carries
// start_datetime/end_datetime; the label-parse fallback exists only for a rare
// pre-datetime local snapshot. The calendar mirror keys its fingerprint + the
// "can this become an event?" guard off these.

/** Best-effort concrete start: the machine timestamp when present, else a parse of
 *  the display labels. Null when neither resolves (the mirror then skips the row). */
val Booking.resolvedStart: Instant?
    get() = startDatetime ?: parseBookingLabels(dateLabel, timeLabel)

/** End of the show window; falls back to start + 2h — the same placeholder duration
 *  `SupabaseBookingsRepository.startEnd` writes on create, so both paths agree. */
val Booking.resolvedEnd: Instant?
    get() = endDatetime ?: resolvedStart?.plusSeconds(2 * 3600)

// "EEE, MMM d, yyyy" (the load-bearing date-label contract) + the same 12h/24h time
// pair the repo's startEnd accepts. Locale.US because the tokens are English name
// words; device zone to match iOS `Calendar.current` / the repo's startEnd.
private val LABEL_FORMATS = listOf("EEE, MMM d, yyyy h:mm a", "EEE, MMM d, yyyy HH:mm")
    .map { DateTimeFormatter.ofPattern(it, Locale.US) }

private fun parseBookingLabels(date: String, time: String): Instant? {
    val text = "${date.trim()} ${time.trim()}"
    for (fmt in LABEL_FORMATS) {
        val parsed = runCatching { LocalDateTime.parse(text, fmt) }.getOrNull()
        if (parsed != null) return parsed.atZone(ZoneId.systemDefault()).toInstant()
    }
    return null
}

/**
 * In-flight booking being composed (iOS `BookingDraft`). Holds only inputs; the
 * fee/totals are computed against the artist's packages via `ArtistsRepository`
 * (see the `fee`/`charges` extensions in `BookingsRepository.kt`, kept there to
 * avoid a data.model → data.repository import cycle). [guests] defaults to 100.
 */
data class BookingDraft(
    val artistId: String,
    val packageIndex: Int,
    val date: String,               // display label, e.g. "Sat, May 16, 2026"
    val dateRaw: LocalDate,
    val time: String,               // "8:30 PM"
    val venue: String = "",
    val guests: Int = 100,
    val paymentMethod: PaymentMethod = PaymentMethod.Upi,
)
