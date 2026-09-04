package `in`.artistant.app.feature.bookings

import `in`.artistant.app.data.model.BookingDateFormat
import `in`.artistant.app.data.model.BookingStatus
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Every decision the Bookings list makes, as plain Kotlin.
 *
 * Same reasoning as `BookingDetailLogic`: Composables are not unit-testable on
 * the JVM, and the things decided here — which segment a booking belongs to,
 * how many days away it is, whether a failure was the network — are exactly the
 * ones a wrong answer makes visible. A gig filed under "Past" on the afternoon
 * of the show is not a cosmetic bug.
 */

/** The two segments screen 10 splits the list into. */
enum class BookingsTab(val label: String) {
    Upcoming("Upcoming"),
    Past("Past"),
}

/**
 * What a row OFFERS, which is what makes it look different.
 *
 * Screen 10's note is the rule: "Confirmed, pending and played each get a
 * different affordance, not a badge." A confirmed gig is a card with the act's
 * picture and the two things you do on the night; an unanswered request is a
 * quiet row that says what it is waiting for; a played one is a row with the one
 * thing left to do on it. None of them is a coloured chip on an otherwise
 * identical row, which is what the app drew before.
 */
enum class BookingAffordance {
    /** Confirmed and ahead of you: the picture card with Message + Tech rider. */
    Confirmed,

    /** Sent, unanswered. States what it is waiting on, in the accent read as text. */
    Awaiting,

    /** Played. Carries the review invitation, and nothing else. */
    Review,

    /** Cancelled, disputed, or a status this build can't read. A plain record. */
    Ended,
}

fun affordanceFor(status: BookingStatus): BookingAffordance = when (status) {
    BookingStatus.Confirmed -> BookingAffordance.Confirmed
    BookingStatus.PendingConfirm -> BookingAffordance.Awaiting
    BookingStatus.Completed -> BookingAffordance.Review
    BookingStatus.Cancelled, BookingStatus.Disputed, BookingStatus.Unknown ->
        BookingAffordance.Ended
}

/**
 * Placeholder gig length for a row with no `end_datetime` — the same two hours
 * `BookingsRepository.create()` writes, so the two agree about when a gig is
 * over.
 */
private const val DEFAULT_GIG_MS = 2L * 60 * 60 * 1000

/**
 * Is this booking still ahead of the viewer?
 *
 * Terminal statuses are past whatever their date says — a cancelled gig in
 * November is not something to look forward to. Everything else is upcoming
 * until the show ENDS, not until it starts: a gig at 8pm must not drop into
 * "Past" at 8:01pm, which is the hour the client is most likely to be opening
 * this screen. A row whose clock cannot be read at all stays upcoming, because
 * hiding a booking is worse than listing it in the wrong half.
 */
fun isUpcoming(status: BookingStatus, startMs: Long?, endMs: Long?, nowMs: Long): Boolean {
    if (status == BookingStatus.Completed ||
        status == BookingStatus.Cancelled ||
        status == BookingStatus.Disputed
    ) {
        return false
    }
    val ends = endMs ?: startMs?.plus(DEFAULT_GIG_MS) ?: return true
    return ends >= nowMs
}

/**
 * Whole days from today to the gig, counted in CALENDAR days in India — not in
 * elapsed hours.
 *
 * "In 3 days" is a statement about dates, and 71 hours can be three sleeps or
 * two depending on the time of day. The gig calendar is IST (`BookingDateFormat`
 * says so and writes its labels in that zone), so the day boundaries that decide
 * this are India's, whatever the device is set to.
 *
 * Negative for a gig in the past; null when there is no clock to read.
 */
fun daysUntilGig(startMs: Long?, nowMs: Long): Int? {
    if (startMs == null) return null
    val zone = TimeZone.getTimeZone("Asia/Kolkata")
    fun startOfDay(ms: Long): Long = Calendar.getInstance(zone).apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayMs = 24L * 60 * 60 * 1000
    return Math.round((startOfDay(startMs) - startOfDay(nowMs)).toDouble() / dayMs).toInt()
}

/**
 * The badge riding on a confirmed card — "Today", "Tomorrow", "In 3 days".
 *
 * Null beyond [BADGE_HORIZON_DAYS], and null for anything already past. The
 * badge is the card's only accent, and spending it on "In 94 days" would make
 * the accent mean "there is a booking" rather than "this one is close".
 */
fun countdownBadge(startMs: Long?, nowMs: Long): String? {
    val days = daysUntilGig(startMs, nowMs) ?: return null
    return when {
        days < 0 -> null
        days == 0 -> "Today"
        days == 1 -> "Tomorrow"
        days <= BADGE_HORIZON_DAYS -> "In $days days"
        else -> null
    }
}

private const val BADGE_HORIZON_DAYS = 30

/**
 * "Sat 12 Oct" — the date as the list prints it.
 *
 * A label we cannot parse comes back untouched rather than sliced: a wrong date
 * reads as a data bug, an unfamiliar format reads as somebody else's data.
 */
fun compactDate(dateLabel: String): String {
    val cal = BookingDateFormat.parseLabel(dateLabel) ?: return dateLabel.trim()
    return SimpleDateFormat("EEE d MMM", Locale.US).format(cal.time)
}

/** "12 Oct" — the same date without its weekday, for a line that already has one. */
fun bareDate(dateLabel: String): String {
    val cal = BookingDateFormat.parseLabel(dateLabel) ?: return dateLabel.trim()
    return SimpleDateFormat("d MMM", Locale.US).format(cal.time)
}

/**
 * "Sat 12 Oct · 8:00 pm · Indiranagar" — a confirmed card's one line of where
 * and when.
 *
 * Blank parts are dropped rather than joined, so a booking with no venue does
 * not render a dangling separator.
 */
fun whenAndWhereLine(dateLabel: String, timeLabel: String, venue: String): String =
    listOf(compactDate(dateLabel), timeLabel.trim().lowercase(Locale.US), venue.trim())
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

/** "Techno DJ · Fri 25 Oct" — a waiting row's second line. */
fun categoryAndDateLine(category: String, dateLabel: String): String =
    listOf(category.trim(), compactDate(dateLabel))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

/** "Techno DJ · Played 6 Sep" — a played row's second line. */
fun playedLine(category: String, dateLabel: String): String =
    listOf(category.trim(), "Played ${bareDate(dateLabel)}")
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

/**
 * Was this failure the network, rather than the server?
 *
 * The distinction is what lets screen 122 say "You're offline" and mean it. A
 * dropped connection surfaces as an [IOException] somewhere down the cause chain
 * (`UnknownHostException`, `ConnectException`, `SocketTimeoutException` all are
 * one); a 500 or an RLS refusal does not. Claiming the phone is offline when the
 * server is merely unhappy sends the user to fiddle with their Wi-Fi.
 *
 * Walks the chain rather than testing the top throwable, because the repository
 * wraps everything in `BookingRepositoryError.Underlying`. Bounded so a cyclic
 * `initCause` cannot hang the caller.
 */
fun isConnectivityFailure(error: Throwable?): Boolean {
    var t = error
    var hops = 0
    while (t != null && hops < CAUSE_CHAIN_LIMIT) {
        if (t is IOException) return true
        t = t.cause
        hops++
    }
    return false
}

private const val CAUSE_CHAIN_LIMIT = 8

/** "Cached 9:04 am" — when the copy on screen was last true. */
fun cachedAtLabel(cachedAtMs: Long): String =
    "Cached " + SimpleDateFormat("h:mm a", Locale.US)
        .format(java.util.Date(cachedAtMs))
        .lowercase(Locale.US)
