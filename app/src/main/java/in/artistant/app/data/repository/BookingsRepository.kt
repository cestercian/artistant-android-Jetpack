package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.PaymentMethod
import `in`.artistant.app.data.payments.PaymentResult
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bookings seam — port of iOS `BookingsRepository`.
 *
 * Create inserts directly (status = pending_confirm). Accept is a status-only
 * PATCH → confirmed (0083 guards artist-only). Decline/cancel route through
 * the `cancel-booking` Edge Function so escrow flips as service_role (0034).
 */

sealed class BookingRepositoryError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object NotSignedIn : BookingRepositoryError("You're not signed in. Sign in and try booking again.")
    class MalformedTime(raw: String) : BookingRepositoryError("Couldn't parse the show time \"$raw\".")
    class Underlying(cause: Throwable) : BookingRepositoryError(cause.message ?: "Booking request failed", cause)
}

interface BookingsRepository {
    suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking
    suspend fun listForClient(): List<Booking>
    suspend fun listForArtist(): List<Booking>
    suspend fun fetchOne(id: String): Booking?
    suspend fun cancel(id: String, reason: String?): Booking
    suspend fun accept(id: String): Booking
    suspend fun declineByArtist(id: String, reason: String?): Booking
    /** ACCT-12 — non-throwing insert into `app_feedback` (mig 0073). */
    suspend fun submitFeedback(body: String, isBug: Boolean)
}

@Singleton
class SupabaseBookingsRepository @Inject constructor(
    private val client: SupabaseClient,
    private val calendarSync: `in`.artistant.app.platform.calendar.CalendarSyncService,
) : BookingsRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking {
        val clientId = currentUserId() ?: throw BookingRepositoryError.NotSignedIn
        val artistId = draft.artistId.lowercase()
        if (runCatching { UUID.fromString(artistId) }.isFailure) {
            throw BookingRepositoryError.Underlying(
                IllegalArgumentException("Internal: artist_id is not a UUID ($artistId)."),
            )
        }
        val (startIso, endIso) = startEndIso(draft)
        val charges = draft.charges
        val row = DbBookingInsert(
            clientId = clientId,
            artistId = artistId,
            packageId = null,
            packageName = draft.packageName.ifBlank { "Custom" },
            packageDurationLabel = draft.packageDuration.ifBlank { "Custom" },
            packageIndex = draft.packageIndex,
            dateLabel = draft.date,
            timeLabel = draft.time,
            startDatetime = startIso,
            endDatetime = endIso,
            venue = draft.venue.ifBlank { "TBD" },
            guests = draft.guests,
            feeInr = draft.feeInr,
            platformFeeInr = charges.platform,
            gstInr = charges.gst,
            totalInr = charges.total,
            status = BookingStatus.PendingConfirm.dbValue,
            escrowStatus = EscrowStatus.Held.dbValue,
            paymentMethod = draft.paymentMethod.dbValue,
            protectionEnabled = true,
            razorpayOrderId = paymentResult.orderId,
            razorpayPaymentId = paymentResult.paymentId,
        )
        return try {
            var booking = client.from("bookings")
                .insert(row) { select() }
                .decodeSingle<DbBooking>()
                .toDomain()
            val notes = draft.venueNotes.trim()
            if (notes.isNotEmpty()) {
                try {
                    client.from("bookings").update(VenueNotesPatch(notes)) {
                        filter { eq("id", booking.id.lowercase()) }
                    }
                    booking = booking.copy(venueNotes = notes)
                } catch (_: Throwable) {
                    // Pre-0072 column missing — booking already landed; drop notes.
                }
            }
            calendarSync.ingest(listOf(booking))
            booking
        } catch (t: Throwable) {
            throw BookingRepositoryError.Underlying(t)
        }
    }

    override suspend fun listForClient(): List<Booking> {
        val clientId = currentUserId() ?: throw BookingRepositoryError.NotSignedIn
        return try {
            client.from("bookings")
                .select {
                    filter { eq("client_id", clientId) }
                    order("start_datetime", Order.DESCENDING)
                }
                .decodeList<DbBooking>()
                .map { it.toDomain() }
                .also { calendarSync.ingest(it) }
        } catch (t: Throwable) {
            throw BookingRepositoryError.Underlying(t)
        }
    }

    override suspend fun listForArtist(): List<Booking> {
        val userId = currentUserId() ?: throw BookingRepositoryError.NotSignedIn
        return try {
            // Prefer live embed; fall back to 0080 client_name (RLS nulls embed for artists).
            client.from("bookings")
                .select(Columns.raw("*, client:users!client_id(full_name)")) {
                    filter { eq("artist_id", userId) }
                    order("start_datetime", Order.DESCENDING)
                }
                .decodeList<DbBookingWithClient>()
                .map { it.toDomain() }
                .also { calendarSync.ingest(it) }
        } catch (t: Throwable) {
            throw BookingRepositoryError.Underlying(t)
        }
    }

    override suspend fun fetchOne(id: String): Booking? {
        return try {
            client.from("bookings")
                .select(Columns.raw("*, client:users!client_id(full_name)")) {
                    filter { eq("id", id.lowercase()) }
                    limit(1)
                }
                .decodeList<DbBookingWithClient>()
                .firstOrNull()
                ?.toDomain()
        } catch (t: Throwable) {
            throw BookingRepositoryError.Underlying(t)
        }
    }

    override suspend fun cancel(id: String, reason: String?): Booking =
        cancelViaEdgeFunction(id, reason, cancelledBy = "client")

    override suspend fun accept(id: String): Booking {
        if (currentUserId() == null) throw BookingRepositoryError.NotSignedIn
        return try {
            client.from("bookings")
                .update(AcceptPayload(status = BookingStatus.Confirmed.dbValue)) {
                    filter { eq("id", id.lowercase()) }
                    select()
                }
                .decodeSingle<DbBooking>()
                .toDomain()
                .also { calendarSync.ingest(listOf(it)) }
        } catch (t: Throwable) {
            throw BookingRepositoryError.Underlying(t)
        }
    }

    override suspend fun declineByArtist(id: String, reason: String?): Booking =
        cancelViaEdgeFunction(id, reason, cancelledBy = "artist")

    /**
     * ACCT-12 — insert into `app_feedback`. Non-throwing: feedback must never
     * dead-end the UI (pre-0073 / offline / signed-out → silent no-op).
     */
    override suspend fun submitFeedback(body: String, isBug: Boolean) {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return
        val userId = currentUserId() ?: return
        runCatching {
            client.from("app_feedback").insert(
                FeedbackInsert(userId = userId, body = trimmed, isBug = isBug),
            )
        }
    }

    private suspend fun cancelViaEdgeFunction(
        id: String,
        reason: String?,
        cancelledBy: String,
    ): Booking {
        if (currentUserId() == null) throw BookingRepositoryError.NotSignedIn
        try {
            val response = client.functions.invoke(
                function = "cancel-booking",
                body = CancelBody(
                    bookingId = id.lowercase(),
                    cancelledBy = cancelledBy,
                    reason = reason,
                ),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
            val parsed = json.decodeFromString<CancelEdgeResponse>(response.bodyAsText())
            if (!parsed.cancelled) {
                throw BookingRepositoryError.Underlying(
                    IllegalStateException("cancel-booking returned cancelled=false"),
                )
            }
            return client.from("bookings")
                .select {
                    filter { eq("id", id.lowercase()) }
                    limit(1)
                }
                .decodeSingle<DbBooking>()
                .toDomain()
                .also { calendarSync.ingest(listOf(it)) }
        } catch (e: BookingRepositoryError) {
            throw e
        } catch (t: Throwable) {
            throw BookingRepositoryError.Underlying(t)
        }
    }

    private fun currentUserId(): String? =
        client.auth.currentSessionOrNull()?.user?.id?.lowercase()

    companion object {
        /** Pinned cancel-booking body keys — never include escrow_status (0034). */
        val cancelPayloadKeys: Set<String> = setOf("booking_id", "cancelled_by", "reason")

        /**
         * Combines draft day + time into ISO start/end. End = start + 2h
         * (same placeholder as iOS until package-duration parsing lands).
         */
        fun startEndIso(draft: BookingDraft): Pair<String, String> {
            val cal = Calendar.getInstance()
            cal.timeInMillis = draft.dateRawEpochMs
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val timeParts = parseTimeOfDay(draft.time)
                ?: throw BookingRepositoryError.MalformedTime(draft.time)
            cal.set(Calendar.HOUR_OF_DAY, timeParts.first)
            cal.set(Calendar.MINUTE, timeParts.second)
            val start = cal.time
            cal.add(Calendar.HOUR_OF_DAY, 2)
            val end = cal.time
            return isoUtc(start) to isoUtc(end)
        }

        private fun parseTimeOfDay(raw: String): Pair<Int, Int>? {
            val formats = listOf("h:mm a", "HH:mm")
            for (fmt in formats) {
                val f = SimpleDateFormat(fmt, Locale.US)
                f.isLenient = false
                val parsed = runCatching { f.parse(raw) }.getOrNull() ?: continue
                val c = Calendar.getInstance().apply { time = parsed }
                return c.get(Calendar.HOUR_OF_DAY) to c.get(Calendar.MINUTE)
            }
            return null
        }

        private fun isoUtc(date: Date): String {
            val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            f.timeZone = TimeZone.getTimeZone("UTC")
            return f.format(date)
        }
    }
}

@Serializable
private data class AcceptPayload(val status: String)

@Serializable
private data class VenueNotesPatch(@SerialName("venue_notes") val venueNotes: String)

@Serializable
private data class CancelBody(
    @SerialName("booking_id") val bookingId: String,
    @SerialName("cancelled_by") val cancelledBy: String,
    val reason: String?,
)

@Serializable
private data class FeedbackInsert(
    @SerialName("user_id") val userId: String,
    val body: String,
    @SerialName("is_bug") val isBug: Boolean,
)

@Serializable
private data class CancelEdgeResponse(val cancelled: Boolean)

@Serializable
private data class DbBookingInsert(
    @SerialName("client_id") val clientId: String,
    @SerialName("artist_id") val artistId: String,
    @SerialName("package_id") val packageId: String?,
    @SerialName("package_name") val packageName: String,
    @SerialName("package_duration_label") val packageDurationLabel: String,
    @SerialName("package_index") val packageIndex: Int,
    @SerialName("date_label") val dateLabel: String,
    @SerialName("time_label") val timeLabel: String,
    @SerialName("start_datetime") val startDatetime: String,
    @SerialName("end_datetime") val endDatetime: String,
    val venue: String,
    val guests: Int,
    @SerialName("fee_inr") val feeInr: Int,
    @SerialName("platform_fee_inr") val platformFeeInr: Int,
    @SerialName("gst_inr") val gstInr: Int,
    @SerialName("total_inr") val totalInr: Int,
    val status: String,
    @SerialName("escrow_status") val escrowStatus: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("protection_enabled") val protectionEnabled: Boolean,
    @SerialName("razorpay_order_id") val razorpayOrderId: String?,
    @SerialName("razorpay_payment_id") val razorpayPaymentId: String?,
)

@Serializable
private data class DbBooking(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    @SerialName("package_index") val packageIndex: Int = 0,
    @SerialName("date_label") val dateLabel: String = "",
    @SerialName("time_label") val timeLabel: String = "",
    val venue: String = "TBD",
    val guests: Int = 0,
    @SerialName("fee_inr") val feeInr: Int = 0,
    @SerialName("platform_fee_inr") val platformFeeInr: Int = 0,
    @SerialName("gst_inr") val gstInr: Int = 0,
    @SerialName("total_inr") val totalInr: Int = 0,
    val status: String = BookingStatus.PendingConfirm.dbValue,
    @SerialName("escrow_status") val escrowStatus: String = EscrowStatus.Held.dbValue,
    @SerialName("payment_method") val paymentMethod: String = PaymentMethod.Upi.dbValue,
    @SerialName("protection_enabled") val protectionEnabled: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("start_datetime") val startDatetime: String? = null,
    @SerialName("end_datetime") val endDatetime: String? = null,
    @SerialName("venue_notes") val venueNotes: String? = null,
    @SerialName("client_name") val clientName: String? = null,
) {
    fun toDomain(clientFullName: String? = null): Booking {
        val stamped = clientName?.trim()?.takeIf { it.isNotEmpty() }
        return Booking(
            id = id,
            artistId = artistId,
            packageIndex = packageIndex,
            date = dateLabel,
            time = timeLabel,
            venue = venue,
            guests = guests,
            fee = feeInr,
            platformFee = platformFeeInr,
            gst = gstInr,
            total = totalInr,
            status = BookingStatus.fromDb(status),
            escrowStatus = EscrowStatus.fromDb(escrowStatus),
            paymentMethod = PaymentMethod.fromDb(paymentMethod),
            protectionEnabled = protectionEnabled,
            createdAtEpochMs = System.currentTimeMillis(),
            clientFullName = clientFullName ?: stamped,
            startDatetimeIso = startDatetime,
            endDatetimeIso = endDatetime,
            venueNotes = venueNotes,
        )
    }
}

@Serializable
private data class DbBookingWithClient(
    val id: String,
    @SerialName("artist_id") val artistId: String,
    @SerialName("package_index") val packageIndex: Int = 0,
    @SerialName("date_label") val dateLabel: String = "",
    @SerialName("time_label") val timeLabel: String = "",
    val venue: String = "TBD",
    val guests: Int = 0,
    @SerialName("fee_inr") val feeInr: Int = 0,
    @SerialName("platform_fee_inr") val platformFeeInr: Int = 0,
    @SerialName("gst_inr") val gstInr: Int = 0,
    @SerialName("total_inr") val totalInr: Int = 0,
    val status: String = BookingStatus.PendingConfirm.dbValue,
    @SerialName("escrow_status") val escrowStatus: String = EscrowStatus.Held.dbValue,
    @SerialName("payment_method") val paymentMethod: String = PaymentMethod.Upi.dbValue,
    @SerialName("protection_enabled") val protectionEnabled: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("start_datetime") val startDatetime: String? = null,
    @SerialName("end_datetime") val endDatetime: String? = null,
    @SerialName("venue_notes") val venueNotes: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    val client: ClientEmbed? = null,
) {
    @Serializable
    data class ClientEmbed(@SerialName("full_name") val fullName: String? = null)

    fun toDomain(): Booking {
        val embed = client?.fullName?.trim()?.takeIf { it.isNotEmpty() }
        val stamped = clientName?.trim()?.takeIf { it.isNotEmpty() }
        return DbBooking(
            id = id,
            artistId = artistId,
            packageIndex = packageIndex,
            dateLabel = dateLabel,
            timeLabel = timeLabel,
            venue = venue,
            guests = guests,
            feeInr = feeInr,
            platformFeeInr = platformFeeInr,
            gstInr = gstInr,
            totalInr = totalInr,
            status = status,
            escrowStatus = escrowStatus,
            paymentMethod = paymentMethod,
            protectionEnabled = protectionEnabled,
            createdAt = createdAt,
            startDatetime = startDatetime,
            endDatetime = endDatetime,
            venueNotes = venueNotes,
            clientName = clientName,
        ).toDomain(clientFullName = embed ?: stamped)
    }
}

/**
 * In-memory [BookingsRepository] for unit tests. Accept/decline/cancel mutate
 * status in place; create stamps a UUID + pending_confirm.
 */
class FakeBookingsRepository(
    seed: List<Booking> = emptyList(),
) : BookingsRepository {
    private val rows = seed.toMutableList()
    var signedIn: Boolean = true
    var failCreate: Boolean = false

    override suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking {
        if (!signedIn) throw BookingRepositoryError.NotSignedIn
        if (failCreate) throw BookingRepositoryError.Underlying(IllegalStateException("fake create fail"))
        val charges = draft.charges
        val booking = Booking(
            id = UUID.randomUUID().toString(),
            artistId = draft.artistId.lowercase(),
            packageIndex = draft.packageIndex,
            date = draft.date,
            time = draft.time,
            venue = draft.venue.ifBlank { "TBD" },
            guests = draft.guests,
            fee = draft.feeInr,
            platformFee = charges.platform,
            gst = charges.gst,
            total = charges.total,
            status = BookingStatus.PendingConfirm,
            escrowStatus = EscrowStatus.Held,
            paymentMethod = draft.paymentMethod,
            protectionEnabled = true,
            createdAtEpochMs = System.currentTimeMillis(),
            venueNotes = draft.venueNotes.trim().takeIf { it.isNotEmpty() },
        )
        rows.add(0, booking)
        return booking
    }

    override suspend fun listForClient(): List<Booking> {
        if (!signedIn) throw BookingRepositoryError.NotSignedIn
        return rows.toList()
    }

    override suspend fun listForArtist(): List<Booking> = listForClient()

    override suspend fun fetchOne(id: String): Booking? =
        rows.firstOrNull { it.id.equals(id, ignoreCase = true) }

    override suspend fun cancel(id: String, reason: String?): Booking =
        mutate(id) { it.copy(status = BookingStatus.Cancelled, escrowStatus = EscrowStatus.Refunded) }

    override suspend fun accept(id: String): Booking =
        mutate(id) { it.copy(status = BookingStatus.Confirmed) }

    override suspend fun declineByArtist(id: String, reason: String?): Booking =
        cancel(id, reason)

    override suspend fun submitFeedback(body: String, isBug: Boolean) {
        // Fake: no-op (tests don't assert feedback persistence).
    }

    private fun mutate(id: String, transform: (Booking) -> Booking): Booking {
        if (!signedIn) throw BookingRepositoryError.NotSignedIn
        val idx = rows.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        if (idx < 0) throw BookingRepositoryError.Underlying(NoSuchElementException(id))
        val updated = transform(rows[idx])
        rows[idx] = updated
        return updated
    }
}
