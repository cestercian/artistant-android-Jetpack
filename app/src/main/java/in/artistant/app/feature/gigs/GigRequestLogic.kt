package `in`.artistant.app.feature.gigs

import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.designsystem.component.PillTone
import `in`.artistant.app.platform.calendar.CalendarSyncPlanner
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The sentences design screens 35 / 107 / 108 put on a gig request.
 *
 * Pure and separate from the composable because every one of them is a claim
 * about money or about a date the artist is committing to, and a claim is worth
 * a test.
 */

private val IST: TimeZone get() = TimeZone.getTimeZone("Asia/Kolkata")

/**
 * The line under "Gig request" — what last HAPPENED, not what the row is.
 *
 * Three different sentences off one field, which is the whole reason
 * `updated_at` is decoded ([GigRequest.updatedAgo]): "Sent 15 minutes ago"
 * (screen 35) and "You countered 2 hours ago" (screen 107) are the same row at
 * two moments, and the second one is the only one that says whose turn it is.
 *
 * Falls back to the sent time when `updated_at` was unreadable, and to null when
 * neither is known — a header with no timestamp is better than one that says
 * "Sent " and stops.
 */
fun requestHeaderSubtitle(request: StoredRequest): String? {
    val changed = request.raw.updatedAgo.takeIf { it.isNotBlank() }
    val sent = request.raw.timeAgo.takeIf { it.isNotBlank() }
    return when (request.status) {
        GigRequestStatus.Countered -> changed?.let { "You countered $it" }
        GigRequestStatus.Declined -> changed?.let { "Declined $it" }
        GigRequestStatus.Accepted -> changed?.let { "Accepted $it" }
        GigRequestStatus.Expired -> "Expired without a reply"
        // Open and Unknown both describe the row's arrival, because neither has
        // an action of the artist's to name.
        else -> sent?.let { "Sent $it" }
    }
}

/**
 * The pill beside the requester's name.
 *
 * Speaks [PillTone] rather than `StatusTone` so it sits beside
 * `bookingStatusTone` — same component, same vocabulary. A request's states are
 * not a booking's, which is why this is a second function and not a second
 * meaning for the first one.
 */
fun requestStatusTone(status: GigRequestStatus): PillTone = when (status) {
    GigRequestStatus.Open -> PillTone.Warm
    GigRequestStatus.Countered -> PillTone.Brand
    GigRequestStatus.Accepted -> PillTone.Good
    GigRequestStatus.Declined, GigRequestStatus.Expired -> PillTone.Hot
    // Not Hot: an unrecognised status is not a bad outcome, it is an absent one,
    // and colouring it red invents a verdict this build cannot read.
    GigRequestStatus.Unknown -> PillTone.Neutral
}

/**
 * "You already have Rooftop brand night, 7:00 pm on 12 Oct." (+ "1 more").
 *
 * The one warning on screen 35 that has to land BEFORE the artist commits — an
 * accept is irreversible from the UI, and double-booking a night is the mistake
 * that costs a real fee and a real reputation.
 *
 * Names the FIRST clash in full and counts the rest: three titles stacked in a
 * warning box stop being read, and the first one is enough to make the artist go
 * and look.
 */
fun clashWarning(clashes: List<CalendarSyncPlanner.Clash>): String? {
    val first = clashes.firstOrNull() ?: return null
    val fmt = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = IST }
    val dayFmt = SimpleDateFormat("d MMM", Locale.US).apply { timeZone = IST }
    val time = fmt.format(first.startEpochMs)
    val day = dayFmt.format(first.startEpochMs)
    val extra = clashes.size - 1
    val tail = if (extra > 0) " +$extra more." else ""
    return "You already have ${first.title}, $time on $day.$tail"
}

/** One line of screen 107's History block. */
data class NegotiationEntry(val who: String, val amount: String, val whenAgo: String?)

/**
 * The negotiation, oldest first — screen 107's History block.
 *
 * Only ever two entries, because `gig_requests` holds exactly two numbers
 * (`proposed_amount_inr` and `counter_amount_inr`) and no ledger. A third round
 * overwrites the counter in place, so the history is honest about the two
 * figures that currently exist rather than pretending to be an audit trail.
 */
fun negotiationHistory(request: StoredRequest): List<NegotiationEntry> {
    val entries = mutableListOf(
        NegotiationEntry(
            who = "They requested",
            amount = formatInr(request.raw.amount),
            whenAgo = request.raw.timeAgo.takeIf { it.isNotBlank() },
        ),
    )
    request.counterAmount?.let { counter ->
        entries += NegotiationEntry(
            who = "You countered",
            amount = formatInr(counter),
            whenAgo = request.raw.updatedAgo.takeIf { it.isNotBlank() },
        )
    }
    return entries
}

/**
 * Date / Venue / Guests — the proposal's facts, in the design's order.
 *
 * A missing venue or headcount is DROPPED rather than printed as a dash: the
 * client's quote form leaves both blank often, and a row of em-dashes reads as
 * information that failed to load rather than as a question nobody asked.
 */
fun requestFacts(request: StoredRequest): List<Pair<String, String>> = buildList {
    request.raw.date.takeIf { it.isNotBlank() }?.let { add("Date" to it) }
    request.raw.venue?.takeIf { it.isNotBlank() }?.let { add("Venue" to it) }
    request.raw.crowdSize?.takeIf { it > 0 }?.let { add("Guests" to it.toString()) }
}

/**
 * What screen 109 says when the id resolves to nothing.
 *
 * Two causes, and the screen names both because they are genuinely different and
 * neither is the artist's fault. We cannot tell them apart — a withdrawn request
 * is DELETEd by the client (`gig_requests_delete_client_open`) and an expired one
 * is swept (mig `0090`), and both leave the same absent row — so the copy says
 * "may have", which is the true statement.
 */
const val REQUEST_NOT_FOUND_TITLE = "This request isn't here"
const val REQUEST_NOT_FOUND_BODY =
    "It may have expired or been withdrawn by the client. Requests expire after " +
        "7 days without a reply."

/**
 * The gig date as "Sat 12 Oct", for the identity row under the requester's name.
 *
 * Returns the raw label when it cannot be parsed rather than an empty string:
 * a date we cannot reformat is still a date the artist can read.
 */
fun shortGigDate(dateLabel: String): String {
    if (dateLabel.isBlank()) return dateLabel
    val parser = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).apply { timeZone = IST }
    val parsed = runCatching { parser.parse(dateLabel) }.getOrNull() ?: return dateLabel
    val out = SimpleDateFormat("EEE d MMM", Locale.US).apply { timeZone = IST }
    return out.format(parsed)
}

/**
 * "Sangeet · 200 guests · Sat 12 Oct" — who and what, on one line.
 *
 * The package label leads because that is what the artist is being asked to
 * play. Empty parts fall out, so a bare request still produces a clean line.
 */
fun requestIdentityLine(request: StoredRequest): String = listOfNotNull(
    request.raw.packageLabel.takeIf { it.isNotBlank() && it != "Custom" },
    request.raw.crowdSize?.takeIf { it > 0 }?.let { "$it guests" },
    shortGigDate(request.raw.date).takeIf { it.isNotBlank() },
).joinToString(" · ")

/**
 * Screen 36's masthead line: "4 this month · ₹1,42,000 booked".
 *
 * Counts and sums only what is on the DISPLAYED month, so stepping the calendar
 * moves the subtitle with it — a total that never changed while the grid did
 * would read as an app-wide figure and be wrong about both halves.
 */
fun gigsMonthSummary(gigs: List<ArtistGigListItem>, year: Int, month: Int): String {
    val inMonth = gigs.filter { it.dayOfMonth != null && it.year == year && it.month == month }
    val fee = inMonth.sumOf { it.booking.fee }
    val count = when (inMonth.size) {
        0 -> "Nothing"
        1 -> "1 gig"
        else -> "${inMonth.size} gigs"
    }
    return if (fee > 0) "$count this month · ${formatInr(fee)} booked" else "$count this month"
}

/** "Sat 12 October" plus "N gigs" — screen 36's day heading. */
fun dayHeadingCount(gigs: Int): String = when (gigs) {
    0 -> "nothing on"
    1 -> "1 gig"
    else -> "$gigs gigs"
}
