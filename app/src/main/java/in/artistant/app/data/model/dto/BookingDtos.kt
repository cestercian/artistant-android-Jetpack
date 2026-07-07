package `in`.artistant.app.data.model.dto

import `in`.artistant.app.common.util.SupabaseISO8601
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.GigRequest
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.model.StoredRequest
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant

/**
 * Postgres wire rows for the booking funnel + gig requests (ports of the iOS `DB*`
 * structs). EXACT snake_case column names; repositories select these explicitly.
 * Enum columns decode via the model `fromDb` fallbacks so an unexpected value
 * degrades to a benign default rather than throwing the whole list.
 */

/** Read shape for `select <cols> from public.bookings` (iOS `DBBooking`). */
@Serializable
data class DBBooking(
    val id: String,
    val client_id: String,
    val artist_id: String,
    val package_index: Int,
    val date_label: String,
    val time_label: String,
    val venue: String,
    val guests: Int,
    val fee_inr: Int,
    val platform_fee_inr: Int,
    val gst_inr: Int,
    val total_inr: Int,
    val status: String,
    val escrow_status: String,
    val payment_method: String,
    val protection_enabled: Boolean,
    val created_at: String? = null,
    val start_datetime: String? = null,
    val end_datetime: String? = null,
) {
    fun toBooking(clientFullName: String? = null): Booking = Booking(
        id = id,
        artistId = artist_id,
        packageIndex = package_index,
        dateLabel = date_label,
        timeLabel = time_label,
        startDatetime = start_datetime?.let { SupabaseISO8601.parse(it) },
        endDatetime = end_datetime?.let { SupabaseISO8601.parse(it) },
        venue = venue,
        guests = guests,
        fee = fee_inr,
        platformFee = platform_fee_inr,
        gst = gst_inr,
        total = total_inr,
        status = BookingStatus.fromDb(status),
        escrowStatus = EscrowStatus.fromDb(escrow_status),
        paymentMethod = PaymentMethod.fromDb(payment_method),
        protectionEnabled = protection_enabled,
        createdAt = created_at?.let { SupabaseISO8601.parse(it) },
        clientFullName = clientFullName,
    )
}

/**
 * Read shape for `select *, client:users!client_id(full_name)` — the artist's Gigs
 * list. The `client` alias is a nested object (or null). Threads the embed's
 * `full_name` into `Booking.clientFullName`, trimmed + nil-empty.
 */
@Serializable
data class DBBookingWithClient(
    val id: String,
    val client_id: String,
    val artist_id: String,
    val package_index: Int,
    val date_label: String,
    val time_label: String,
    val venue: String,
    val guests: Int,
    val fee_inr: Int,
    val platform_fee_inr: Int,
    val gst_inr: Int,
    val total_inr: Int,
    val status: String,
    val escrow_status: String,
    val payment_method: String,
    val protection_enabled: Boolean,
    val created_at: String? = null,
    val start_datetime: String? = null,
    val end_datetime: String? = null,
    val client: ClientEmbed? = null,
) {
    @Serializable
    data class ClientEmbed(val full_name: String? = null)

    fun toBooking(): Booking {
        val name = client?.full_name?.trim()?.takeIf { it.isNotEmpty() }
        return DBBooking(
            id, client_id, artist_id, package_index, date_label, time_label, venue,
            guests, fee_inr, platform_fee_inr, gst_inr, total_inr, status, escrow_status,
            payment_method, protection_enabled, created_at, start_datetime, end_datetime,
        ).toBooking(clientFullName = name)
    }
}

/**
 * Insert shape for `public.bookings` — mirrors the columns 1:1 except DB-defaulted
 * `id`/`created_at`/`updated_at`. Snake_case keys bind to columns by name.
 * `package_id` stays null (the in-memory package id isn't the DB row id); the
 * snapshot `package_name`/`package_duration_label` carry enough for display.
 */
@Serializable
data class DBBookingInsert(
    val client_id: String,
    val artist_id: String,
    val package_id: String? = null,
    val package_name: String,
    val package_duration_label: String,
    val package_index: Int,
    val date_label: String,
    val time_label: String,
    val start_datetime: String,
    val end_datetime: String,
    val venue: String,
    val guests: Int,
    val fee_inr: Int,
    val platform_fee_inr: Int,
    val gst_inr: Int,
    val total_inr: Int,
    val status: String,
    val escrow_status: String,
    val payment_method: String,
    val protection_enabled: Boolean,
    val razorpay_order_id: String? = null,
    val razorpay_payment_id: String? = null,
)

/**
 * Read shape for `select *, client:users!client_id(full_name)` from gig_requests
 * (iOS `DBGigRequestWithClient`). `package` is NOT embedded — the domain model
 * carries a free-text label, so "Custom" is the placeholder.
 */
@Serializable
data class DBGigRequestWithClient(
    val id: String,
    val artist_id: String,
    val client_id: String,
    val message: String? = null,
    val proposed_amount_inr: Int,
    val counter_amount_inr: Int? = null,
    val date_label: String,
    val venue: String? = null,
    val crowd_size: Int? = null,
    val status: String,
    val created_at: String? = null,
    val client: ClientEmbed? = null,
) {
    @Serializable
    data class ClientEmbed(val full_name: String? = null)

    fun toStoredRequest(): StoredRequest {
        val clientName = client?.full_name?.trim()?.takeIf { it.isNotEmpty() } ?: "Client"
        return StoredRequest(
            raw = GigRequest(
                id = id,
                client = clientName,
                message = message ?: "",
                date = date_label,
                amount = proposed_amount_inr,
                `package` = "Custom",
                timeAgo = relativeTimeAgo(created_at),
            ),
            status = GigRequestStatus.fromDb(status),
            counterAmount = counter_amount_inr,
        )
    }

    companion object {
        /**
         * "now" / "5m ago" / "2h ago" / "3d ago" from an ISO timestamp. Pure +
         * framework-free (no `android.text.format.DateUtils`) so it unit-tests and
         * matches the iOS short relative style closely enough.
         */
        internal fun relativeTimeAgo(raw: String?, now: Instant = Instant.now()): String {
            val then = raw?.let { SupabaseISO8601.parse(it) } ?: return "now"
            val secs = Duration.between(then, now).seconds.coerceAtLeast(0)
            return when {
                secs < 60 -> "now"
                secs < 3_600 -> "${secs / 60}m ago"
                secs < 86_400 -> "${secs / 3_600}h ago"
                else -> "${secs / 86_400}d ago"
            }
        }
    }
}
