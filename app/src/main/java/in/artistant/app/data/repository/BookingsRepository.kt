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
import `in`.artistant.app.data.model.resolvedStartEpochMs
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

    /**
     * The `bookings_no_overlap` GiST exclusion (0051) rejected the insert — the
     * artist took that slot between the date pick and Send.
     */
    class DateUnavailable(cause: Throwable) :
        BookingRepositoryError("That date was just taken. Pick another date and try again.", cause)

    /** The `bookings_no_self` CHECK (0033/0081) — you can't book yourself. */
    class SelfBooking(cause: Throwable) :
        BookingRepositoryError("You can't book your own artist profile.", cause)

    /** A server write-rate / squat cap (0074) rejected the insert. */
    class RateLimited(cause: Throwable) :
        BookingRepositoryError("You've reached a booking limit for now. Try again a little later.", cause)

    /**
     * A status write matched ZERO rows — the id is gone, or RLS (0083's
     * artist-only accept) filtered it out. [id] is kept off the copy on purpose:
     * a UUID in a banner tells the user nothing, but logs want it.
     */
    class NotFoundOrUnauthorized(val id: String) : BookingRepositoryError(
        "This request is no longer available — it may have been cancelled or already answered.",
    )

    class Underlying(cause: Throwable) : BookingRepositoryError(cause.message ?: "Booking request failed", cause)
}

interface BookingsRepository {
    suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking
    suspend fun listForClient(): List<Booking>
    suspend fun listForArtist(): List<Booking>
    suspend fun fetchOne(id: String): Booking?

    /**
     * The rows behind a known set of ids, in one round trip.
     *
     * For a surface that references a handful of bookings it never listed — the
     * inbox, where each thread carries at most one. Asking the seat's own list
     * instead would pull every booking the account has ever had (and drag the
     * calendar mirror along with it) to render a status word per row. RLS decides
     * what comes back, so an id the viewer can't read is simply absent.
     */
    suspend fun fetchMany(ids: List<String>): List<Booking>
    suspend fun cancel(id: String, reason: String?): Booking
    suspend fun accept(id: String): Booking
    suspend fun declineByArtist(id: String, reason: String?): Booking
    /**
     * ACCT-12 — insert into `app_feedback` (mig 0073).
     * @return true when the row landed; false on empty body / signed-out / network failure
     * so the Help sheet can show failure copy instead of a false "Thanks".
     */
    suspend fun submitFeedback(body: String, isBug: Boolean): Boolean
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
            // The house-rule guards are server-side, and the client pre-checks
            // neither of them, so this is where they land. Unclassified, the raw
            // PostgREST text went straight to the checkout banner — a client who
            // picked a slot the artist already has booked read
            // `conflicting key value violates exclusion constraint
            // "bookings_no_overlap"` with no hint to pick another date.
            throw classifyCreateError(t)
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

    override suspend fun fetchMany(ids: List<String>): List<Booking> {
        if (ids.isEmpty()) return emptyList()
        return try {
            // Same projection as [fetchOne] — the artist seat needs the client
            // embed, and RLS nulls it for anyone who shouldn't see it — and, like
            // fetchOne, no calendar ingest: mirroring belongs to the surfaces
            // that own the calendar, not to a caller reading rows by id.
            client.from("bookings")
                .select(Columns.raw("*, client:users!client_id(full_name)")) {
                    filter { isIn("id", ids.map { it.lowercase() }) }
                }
                .decodeList<DbBookingWithClient>()
                .map { it.toDomain() }
        } catch (t: Throwable) {
            throw BookingRepositoryError.Underlying(t)
        }
    }

    override suspend fun cancel(id: String, reason: String?): Booking =
        cancelViaEdgeFunction(id, reason, cancelledBy = "client")

    override suspend fun accept(id: String): Booking {
        if (currentUserId() == null) throw BookingRepositoryError.NotSignedIn
        return try {
            // decodeList, not decodeSingle: zero rows is an EXPECTED outcome here
            // — 0083's artist-only policy rejecting the write, a booking another
            // device already answered, a stale id off a push deep link — and
            // decodeSingle turns all three into a kotlinx decode message the
            // artist reads verbatim in the action banner. Same shape as
            // RequestsRepository.updateStatus.
            val rows = client.from("bookings")
                .update(AcceptPayload(status = BookingStatus.Confirmed.dbValue)) {
                    filter { eq("id", id.lowercase()) }
                    select()
                }
                .decodeList<DbBooking>()
            val row = rows.firstOrNull() ?: throw BookingRepositoryError.NotFoundOrUnauthorized(id)
            row.toDomain().also { calendarSync.ingest(listOf(it)) }
        } catch (e: BookingRepositoryError) {
            throw e
        } catch (t: Throwable) {
            throw BookingRepositoryError.Underlying(t)
        }
    }

    override suspend fun declineByArtist(id: String, reason: String?): Booking =
        cancelViaEdgeFunction(id, reason, cancelledBy = "artist")

    /**
     * ACCT-12 — insert into `app_feedback`. Returns false on empty / unsigned /
     * transport failure so the UI never pretends a dropped note was received.
     */
    override suspend fun submitFeedback(body: String, isBug: Boolean): Boolean {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return false
        val userId = currentUserId() ?: return false
        return runCatching {
            client.from("app_feedback").insert(
                FeedbackInsert(userId = userId, body = trimmed, isBug = isBug),
            )
        }.isSuccess
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
         * Classify a failed `create` into a typed error the checkout screen can
         * say something useful about. Pure + in the companion so every branch is
         * unit-testable without a live PostgREST round trip (port of iOS
         * `BookingsRepository.classifyCreateError`).
         *
         * **Order matters.** `bookings_no_self` and the 0074 squat caps both
         * raise SQLSTATE 23514 (`check_violation`), so keying the cap on a bare
         * "23514" first would report a self-booking as "you've hit a booking
         * limit" — the specific constraint has to win.
         */
        fun classifyCreateError(t: Throwable): BookingRepositoryError {
            val desc = t.message.orEmpty().lowercase()
            return when {
                // Its own constraint name, matched before the caps it shares an
                // SQLSTATE with. Covers `bookings_no_self_booking` (0033) too.
                "bookings_no_self" in desc -> BookingRepositoryError.SelfBooking(t)
                // The GiST exclusion (0051) on a slot taken since the date pick.
                "23p01" in desc || "exclusion" in desc || "no_overlap" in desc ->
                    BookingRepositoryError.DateUnavailable(t)
                // Write-rate / squat caps (0074) — after the two above.
                RATE_LIMIT_MARKERS.any { it in desc } -> BookingRepositoryError.RateLimited(t)
                else -> BookingRepositoryError.Underlying(t)
            }
        }

        /** Shared with iOS's `BackendError.isRateLimit` — same guard family. */
        private val RATE_LIMIT_MARKERS =
            listOf("23514", "check_violation", "rate limit", "cap reached", "booking limit")

        /** Gig wall-clock is IST — see [startEndIso]. */
        private val IST: TimeZone get() = TimeZone.getTimeZone("Asia/Kolkata")

        /**
         * Combines draft day + time into ISO start/end. End = start + 2h
         * (same placeholder as iOS until package-duration parsing lands).
         *
         * The clock is read in **IST**, not the device's zone. `time_label`
         * ("8:00 PM") is a wall-clock time in India, and every other place that
         * turns a gig's labels into an instant says so — `BookingDateFormat
         * .parseDateAndTime`, `CalendarSyncPlanner`, `CalendarSyncService`. When
         * this wrote the instant in the device's zone instead, a client booking
         * from, say, Dubai (UTC+4) stored an 8:00 PM gig as 16:00Z — 9:30 PM to
         * the artist in IST: the mirrored calendar event and its −24h/−2h alarms
         * fired at the wrong time, and the server's `bookings_no_overlap`
         * GiST — which compares these instants, not the labels — let two clients
         * in different zones take the same slot.
         *
         * The calendar DAY still comes from the device's zone, because that is the
         * zone `BookingSlots.upcomingDateChips` formatted `date_label` in: day
         * from the chip the client tapped, clock in IST.
         */
        fun startEndIso(draft: BookingDraft): Pair<String, String> {
            val timeParts = parseTimeOfDay(draft.time)
                ?: throw BookingRepositoryError.MalformedTime(draft.time)
            // The chip's day read in IST, matching how it was generated and
            // labelled (BookingSlots.upcomingDateChips) — reading it in the
            // device zone here made the stored day disagree with the label the
            // client tapped whenever the two calendars differed.
            val chosenDay = Calendar.getInstance(IST).apply { timeInMillis = draft.dateRawEpochMs }
            val cal = Calendar.getInstance(IST).apply {
                clear()
                set(
                    chosenDay.get(Calendar.YEAR),
                    chosenDay.get(Calendar.MONTH),
                    chosenDay.get(Calendar.DAY_OF_MONTH),
                    timeParts.first,
                    timeParts.second,
                )
            }
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
    /**
     * The tier name the server stamped at insert (`package_name`, `not null`
     * since 0001). Defaulted to null anyway so a projection that omits the
     * column decodes rather than throwing.
     */
    @SerialName("package_name") val packageName: String? = null,
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
            packageName = packageName?.trim()?.takeIf { it.isNotEmpty() },
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
            createdAtEpochMs = createdAt
                ?.let { `in`.artistant.app.common.util.SupabaseISO8601.parse(it)?.toEpochMilli() }
                ?: System.currentTimeMillis(),
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
    @SerialName("package_name") val packageName: String? = null,
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
            packageName = packageName,
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
    /**
     * The signed-in user's id. Unset by default, so every existing single-role
     * fixture keeps seeing the full seeded set from both [listForClient] and
     * [listForArtist] — exactly as before. Name it to model the ARTIST seat:
     * [listForArtist] then filters to rows where `artistId == viewerId`
     * instead of silently delegating to [listForClient]'s unfiltered list, the
     * way a multi-artist fixture (a harness roster, a future cross-artist
     * test) could otherwise show bookings the viewer does not own.
     */
    private val viewerId: String? = null,
    /**
     * The booking client's display name — migration 0080's denormalized
     * `client_name`, which the server stamps on insert and the real `create`'s
     * `select()` echo carries back for the artist seat to render.
     *
     * Unset by default, so a fixture that says nothing keeps producing the
     * unnamed row every "falls back to 'Client'" test needs; name it to model a
     * create the way the server answers it.
     */
    private val clientName: String? = null,
) : BookingsRepository {
    private val rows = seed.toMutableList()
    var signedIn: Boolean = true
    var failCreate: Boolean = false

    override suspend fun create(draft: BookingDraft, paymentResult: PaymentResult): Booking {
        if (!signedIn) throw BookingRepositoryError.NotSignedIn
        if (failCreate) throw BookingRepositoryError.Underlying(IllegalStateException("fake create fail"))
        val charges = draft.charges
        // Same helper the real insert computes start/end from (BookingsRepository
        // .startEndIso), so a booking this fake creates carries the same
        // startDatetimeIso/endDatetimeIso a real one would — the columns
        // Booking.resolvedStartEpochMs and the "Add to calendar" path key off.
        val (startIso, endIso) = SupabaseBookingsRepository.startEndIso(draft)
        val booking = Booking(
            id = UUID.randomUUID().toString(),
            artistId = draft.artistId.lowercase(),
            packageIndex = draft.packageIndex,
            // Same snapshot the real insert writes (`package_name`), so a test
            // driving this fake sees the tier name a real booking would carry.
            packageName = draft.packageName.ifBlank { "Custom" },
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
            // Trimmed-and-blank-dropped like the decoder does with `client_name`,
            // so a fixture full of spaces can't reach the artist seat as a name.
            clientFullName = clientName?.trim()?.takeIf { it.isNotEmpty() },
            startDatetimeIso = startIso,
            endDatetimeIso = endIso,
            venueNotes = draft.venueNotes.trim().takeIf { it.isNotEmpty() },
        )
        rows.add(0, booking)
        return booking
    }

    override suspend fun listForClient(): List<Booking> {
        if (!signedIn) throw BookingRepositoryError.NotSignedIn
        return rows.startDatetimeDescending()
    }

    override suspend fun listForArtist(): List<Booking> {
        if (!signedIn) throw BookingRepositoryError.NotSignedIn
        val viewer = viewerId
        val mine =
            if (viewer == null) rows else rows.filter { it.artistId.equals(viewer, ignoreCase = true) }
        return mine.startDatetimeDescending()
    }

    override suspend fun fetchOne(id: String): Booking? =
        rows.firstOrNull { it.id.equals(id, ignoreCase = true) }

    /** Like the real seam: ids that match nothing are absent, never null entries. */
    override suspend fun fetchMany(ids: List<String>): List<Booking> {
        val wanted = ids.map { it.lowercase() }.toSet()
        return rows.filter { it.id.lowercase() in wanted }
    }

    override suspend fun cancel(id: String, reason: String?): Booking =
        mutate(id) { it.copy(status = BookingStatus.Cancelled, escrowStatus = EscrowStatus.Refunded) }

    override suspend fun accept(id: String): Booking =
        mutate(id) { it.copy(status = BookingStatus.Confirmed) }

    override suspend fun declineByArtist(id: String, reason: String?): Booking =
        cancel(id, reason)

    override suspend fun submitFeedback(body: String, isBug: Boolean): Boolean {
        if (body.trim().isEmpty() || !signedIn) return false
        return true
    }

    /**
     * `order("start_datetime", Order.DESCENDING)`, done in memory — the clause
     * BOTH real list reads carry, so a list off this fake arrives in the order a
     * screen would really receive it.
     *
     * The key is the start COLUMN, never [resolvedStartEpochMs]'s date/time-label
     * fallback: the server sorts on the column alone, so a row carrying none has
     * no place in that ordering. Those keep the order they were seeded in, ahead
     * of the dated rows — a stable sort, and `DESC` is `NULLS FIRST` in Postgres.
     */
    private fun List<Booking>.startDatetimeDescending(): List<Booking> {
        val byStartDesc = compareBy<Booking, Long?>(nullsFirst(reverseOrder<Long>())) { b ->
            b.startDatetimeIso?.takeIf { it.isNotBlank() }?.let { b.resolvedStartEpochMs() }
        }
        return sortedWith(byStartDesc)
    }

    private fun mutate(id: String, transform: (Booking) -> Booking): Booking {
        if (!signedIn) throw BookingRepositoryError.NotSignedIn
        val idx = rows.indexOfFirst { it.id.equals(id, ignoreCase = true) }
        // Same signal the real seam raises when the status write matches no row.
        if (idx < 0) throw BookingRepositoryError.NotFoundOrUnauthorized(id)
        val updated = transform(rows[idx])
        rows[idx] = updated
        return updated
    }
}
