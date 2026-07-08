package `in`.artistant.app.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import `in`.artistant.app.common.util.SupabaseISO8601
import `in`.artistant.app.common.util.lowercaseUuid
import `in`.artistant.app.core.result.AppError
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingDraft
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.EscrowStatus
import `in`.artistant.app.data.model.dto.DBBooking
import `in`.artistant.app.data.model.dto.DBBookingInsert
import `in`.artistant.app.data.model.dto.DBBookingWithClient
import `in`.artistant.app.domain.booking.BookingCharges
import `in`.artistant.app.domain.booking.BookingMath
import `in`.artistant.app.platform.calendar.CalendarSync
import `in`.artistant.app.platform.payments.PaymentResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Booking funnel data boundary (port of iOS `BookingsRepository`). v1 matchmaker:
 * [create] is a DIRECT insert (no `create-booking` Edge Function / payments) — fees
 * are computed client-side via [BookingMath] and the row lands `confirmed` /
 * escrow `held`, honouring the DB guards (self-booking CHECK, no-overlap, insert
 * status ∈ {pending_confirm, confirmed}, escrow clamped to held — API_MAPPING §3).
 * [cancel] must route through the `cancel-booking` Edge Function (a direct
 * escrow_status PATCH is rejected by the guard trigger). Every fetch/create/cancel
 * feeds [CalendarSync.ingest] — the single seam both roles route through, so the
 * calendar mirror sees client + artist gigs without any per-screen wiring (iOS parity).
 */
interface BookingsRepository {
    /** Insert a booking from [draft] + the (mock) [paymentResult]. Returns the row. */
    suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking

    /** All bookings where the signed-in user is the client, newest-first. */
    suspend fun listForClient(): List<Booking>

    /** All bookings where the signed-in user owns the artist row, newest-first.
     *  Uses the `client:users!client_id(full_name)` embed for the client's name. */
    suspend fun listForArtist(): List<Booking>

    /** Cancel via the `cancel-booking` function, then refetch the updated row. */
    suspend fun cancel(id: String): Booking
}

@Singleton
class SupabaseBookingsRepository @Inject constructor(
    private val client: SupabaseClient,
    // Injected to resolve the draft's fee + package snapshot (iOS reads the shared
    // ArtistsRepository singleton). find() hits the by-id cache the funnel warmed.
    private val artists: ArtistsRepository,
    // The calendar mirror's ingest seam — fed after every create/fetch/cancel below.
    private val calendar: CalendarSync,
) : BookingsRepository {

    override suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking {
        val clientId = currentUserId()
        val artistUuid = draft.artistId.lowercaseUuid()

        // Fee comes from the artist's package price; totals from BookingMath. The
        // package name/duration are SNAPSHOTTED onto the row so a later package edit
        // doesn't rewrite this booking's history (schema requires them non-null).
        val pkg = artists.find(artistUuid)?.packages?.getOrNull(draft.packageIndex)
        val fee = draft.fee(artists)
        val charges = BookingMath.compute(fee)
        val (start, end) = startEnd(draft)

        val row = DBBookingInsert(
            client_id = clientId,
            artist_id = artistUuid,
            package_id = null,
            package_name = pkg?.name ?: "Custom",
            package_duration_label = pkg?.duration ?: "Custom",
            package_index = draft.packageIndex,
            date_label = draft.date,
            time_label = draft.time,
            start_datetime = SupabaseISO8601.format(start),
            end_datetime = SupabaseISO8601.format(end),
            venue = draft.venue.ifEmpty { "TBD" },
            guests = draft.guests,
            fee_inr = fee,
            platform_fee_inr = charges.platform,
            gst_inr = charges.gst,
            total_inr = charges.total,
            // v1 matchmaker: confirmed on insert, escrow held (the DB clamps it too).
            status = BookingStatus.Confirmed.dbValue,
            escrow_status = EscrowStatus.Held.dbValue,
            payment_method = draft.paymentMethod.dbValue,
            protection_enabled = true,
            razorpay_order_id = paymentResult.orderId,
            razorpay_payment_id = paymentResult.paymentId,
        )

        return client.postgrest.from("bookings")
            .insert(row) { select(BOOKING_COLUMNS) }
            .decodeSingle<DBBooking>()
            .toBooking()
            .also { calendar.ingest(listOf(it)) }
    }

    override suspend fun listForClient(): List<Booking> {
        val clientId = currentUserId()
        return client.postgrest.from("bookings")
            .select(BOOKING_COLUMNS) {
                filter { eq("client_id", clientId) }
                order("start_datetime", Order.DESCENDING)
            }
            .decodeList<DBBooking>()
            .map { it.toBooking() }
            .also { calendar.ingest(it) }
    }

    override suspend fun listForArtist(): List<Booking> {
        val artistId = currentUserId()
        return client.postgrest.from("bookings")
            .select(Columns.raw("$BOOKING_COLS, client:users!client_id(full_name)")) {
                filter { eq("artist_id", artistId) }
                order("start_datetime", Order.DESCENDING)
            }
            .decodeList<DBBookingWithClient>()
            .map { it.toBooking() }
            .also { calendar.ingest(it) }
    }

    override suspend fun cancel(id: String): Booking {
        currentUserId() // ensure signed in (throws NotFoundOrUnauthorized otherwise)
        // Cancel is service-role work (escrow flip is guard-blocked for participants),
        // so route through the Edge Function; cancelled_by='client' — the only cancel
        // surface is the client's booking detail (don't penalize the artist's metric).
        val resp = client.functions.invoke(
            function = "cancel-booking",
            body = CancelBody(booking_id = id.lowercaseUuid(), cancelled_by = "client", reason = null),
        )
        val decoded = LENIENT_JSON.decodeFromString<CancelResponse>(resp.bodyAsText())
        require(decoded.cancelled) { "cancel-booking returned cancelled=false" }

        // Refetch so the local model reflects the server's status/escrow flip.
        return client.postgrest.from("bookings")
            .select(BOOKING_COLUMNS) {
                filter { eq("id", id.lowercaseUuid()) }
                single()
            }
            .decodeSingle<DBBooking>()
            .toBooking()
            .also { calendar.ingest(listOf(it)) } // cancelled → the reconcile removes its event
    }

    // --- helpers ---------------------------------------------------------

    private fun currentUserId(): String =
        client.auth.currentSessionOrNull()?.user?.id?.lowercaseUuid()
            ?: throw AppError.NotFoundOrUnauthorized

    @Serializable
    private data class CancelBody(val booking_id: String, val cancelled_by: String, val reason: String?)

    @Serializable
    private data class CancelResponse(val cancelled: Boolean = false)

    companion object {
        private const val BOOKING_COLS =
            "id, client_id, artist_id, package_index, date_label, time_label, venue, guests, " +
                "fee_inr, platform_fee_inr, gst_inr, total_inr, status, escrow_status, " +
                "payment_method, protection_enabled, created_at, start_datetime, end_datetime"
        private val BOOKING_COLUMNS = Columns.raw(BOOKING_COLS)

        private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

        // 12-hour ("8:30 PM") first, then 24-hour ("20:30"). US locale: the labels use
        // English AM/PM tokens, so a device-locale formatter could fail to parse them.
        private val TIME_FORMATS = listOf("h:mm a", "HH:mm").map {
            DateTimeFormatter.ofPattern(it, Locale.US)
        }

        /**
         * Combine the draft's day + time into a concrete start Instant; end defaults to
         * start + 2h (the same placeholder the cron jobs assume until a duration parser
         * lands). Uses the device zone — matches iOS `Calendar.current`.
         */
        internal fun startEnd(draft: BookingDraft): Pair<java.time.Instant, java.time.Instant> {
            val time = TIME_FORMATS.firstNotNullOfOrNull { fmt ->
                runCatching { LocalTime.parse(draft.time.trim(), fmt) }.getOrNull()
            } ?: LocalTime.of(20, 0) // benign fallback if free-text time is unparseable
            val zone = ZoneId.systemDefault()
            val start = draft.dateRaw.atTime(time).atZone(zone).toInstant()
            return start to start.plusSeconds(2 * 3600)
        }
    }
}

// --- BookingDraft fee resolution (kept here to avoid a data.model → repository cycle) ---

/** The draft's resolved artist package (or null if the artist/index isn't cached). */
fun BookingDraft.resolvedPackage(artists: ArtistsRepository) =
    artists.find(artistId.lowercaseUuid())?.packages?.getOrNull(packageIndex)

/** Performance fee for the draft, 0 when the package can't be resolved. */
fun BookingDraft.fee(artists: ArtistsRepository): Int = resolvedPackage(artists)?.price ?: 0

/** Platform/GST/total for the draft's fee (iOS `BookingDraft.totals`). */
fun BookingDraft.charges(artists: ArtistsRepository): BookingCharges = BookingMath.compute(fee(artists))
