package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.data.model.resolvedStartEpochMs
import `in`.artistant.app.data.repository.ScoreBreakdown
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Every derivation the artist dashboard renders, as pure functions.
 *
 * The screen is a projection of three server reads (bookings, quote requests,
 * score breakdown) plus the wall clock, and *all* of the interesting behaviour
 * lives in how those get bucketed, windowed and worded. Keeping that out of the
 * composables means it can be tested against a FIXED clock — the one thing a
 * Compose test cannot do cheaply — which matters here because almost every
 * function below has a midnight-boundary edge case.
 *
 * Everything is anchored to **IST**, not the device zone. The product is
 * India-only and the server writes gig dates as IST wall-clock labels, so a
 * device travelling abroad must still bucket a gig into the day it actually
 * happens on locally, not the day it happens on for the traveller.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Shared day math
// ─────────────────────────────────────────────────────────────────────────────

private val IST: TimeZone get() = TimeZone.getTimeZone("Asia/Kolkata")

private const val MS_PER_DAY = 24L * 60 * 60 * 1000

/** Midnight-in-IST for the instant [epochMs] falls in. */
private fun istStartOfDay(epochMs: Long): Long = Calendar.getInstance(IST).apply {
    timeInMillis = epochMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/**
 * Whole IST days between two instants, counting backwards from [todayStart].
 *
 * Integer division of the millisecond delta is only safe because IST has no DST
 * — every day in the zone is exactly 24h long, so no bucket can silently absorb
 * or lose an hour. This would be wrong in a zone that shifts.
 */
private fun daysBefore(todayStart: Long, epochMs: Long): Int =
    ((todayStart - istStartOfDay(epochMs)) / MS_PER_DAY).toInt()

/** `yyyy-MM-dd` in IST — the key format the busy-day set and the strip agree on. */
private fun dayKeyFormatter(): SimpleDateFormat =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = IST }

// ─────────────────────────────────────────────────────────────────────────────
// Row filters
// ─────────────────────────────────────────────────────────────────────────────

/** Bookings awaiting artist Accept/Decline — the "New requests" rail. */
internal fun pendingConfirmBookings(bookings: List<Booking>): List<Booking> =
    bookings.filter { it.status == BookingStatus.PendingConfirm }

/**
 * Confirmed + future shows for the "Upcoming gigs" rail.
 *
 * Deliberately NOT keyed on escrow state. Every booking is inserted `held` and
 * nothing in the matchmaker build ever transitions it, so an escrow filter is a
 * monotonically-growing all-time counter that lets cancelled and already-played
 * gigs inflate the rail. Undated rows are excluded rather than treated as today.
 */
internal fun upcomingConfirmed(
    bookings: List<Booking>,
    nowEpochMs: Long = System.currentTimeMillis(),
): List<Booking> {
    val todayStart = istStartOfDay(nowEpochMs)
    return bookings
        .filter { b ->
            b.status == BookingStatus.Confirmed &&
                (b.resolvedStartEpochMs()?.let { it >= todayStart } == true)
        }
        .sortedBy { it.resolvedStartEpochMs() ?: Long.MAX_VALUE }
}

/** Open + countered quote requests — the negotiation inbox. */
internal fun openGigRequests(requests: List<StoredRequest>): List<StoredRequest> =
    requests.filter { it.status == GigRequestStatus.Open || it.status == GigRequestStatus.Countered }

/**
 * Display label for artist-side rows — never falls back to venue.
 *
 * Venue defaults to the literal string "TBD" on a fresh booking, so a
 * venue fallback renders a row addressed to a client called "TBD".
 */
internal fun artistClientDisplayName(booking: Booking): String =
    booking.clientFullName?.takeIf { it.isNotBlank() } ?: "Client"

// ─────────────────────────────────────────────────────────────────────────────
// Hero: how much work came in
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The window the hero and its chart cover.
 *
 * [bucketCount] is how many daily bars the chart draws; [windowDays] is the
 * period the headline figure counts over. They match for the two finite ranges
 * and deliberately diverge for [All]: the headline counts every booking ever,
 * while the chart caps at 90 daily buckets because past that individual bars
 * compress into an unreadable flat line. That divergence is why the screen has
 * to distinguish "has bookings" from "has bookings *in the chart window*" — an
 * artist whose only work predates the cap has a real total and an empty chart,
 * and saying "no bookings yet" underneath a non-zero number is a lie.
 */
enum class EarningsRange(
    val label: String,
    val bucketCount: Int,
    val windowDays: Int?,
) {
    SevenDays("7D", bucketCount = 7, windowDays = 7),
    ThirtyDays("30D", bucketCount = 30, windowDays = 30),
    All("ALL", bucketCount = 90, windowDays = null),
}

/**
 * Daily booking COUNTS, oldest bucket first, today last. Always exactly
 * [buckets] long, zeros included, so the caller decides whether to draw a chart
 * from the underlying data rather than from the array's shape.
 *
 * Counts, not rupees. No money moves through the app in the matchmaker model —
 * the artist negotiates and is paid outside it — so a ₹ total on this hero would
 * be a number the product cannot stand behind. The per-row fees further down the
 * screen are different: those are the fee the two parties actually agreed on.
 *
 * Buckets by `createdAt` (when the request arrived), not by gig date, because
 * this is a measure of inbound demand — a gig booked today for next December is
 * demand that landed today.
 */
internal fun bookingCountSeries(
    bookings: List<Booking>,
    buckets: Int,
    nowEpochMs: Long = System.currentTimeMillis(),
): List<Int> {
    val todayStart = istStartOfDay(nowEpochMs)
    val totals = IntArray(buckets)
    for (b in bookings) {
        val diff = daysBefore(todayStart, b.createdAtEpochMs)
        if (diff in 0 until buckets) totals[buckets - 1 - diff] += 1
    }
    return totals.toList()
}

/**
 * Everything the hero needs, computed in one pass per window so the three read
 * sites (figure, comparison line, delta pill) cannot disagree with each other.
 */
data class HeroCounts(
    val headline: Int,
    val prior: Int,
    val deltaPercent: Int,
    val deltaUp: Boolean,
)

/**
 * Headline count for [range] plus the equivalent immediately-preceding window.
 *
 * The current window is `today − (n − 1) … now`, i.e. n distinct calendar days
 * ENDING today — the same span the chart buckets. Starting it a day earlier
 * (the obvious `now − n days`) would let a booking count toward the headline
 * while having no bar under it, which reads as a phantom.
 *
 * [EarningsRange.All] has no comparable prior period, so `prior` is 0 and the
 * caller hides the delta rather than showing a meaningless +100%.
 */
internal fun heroCounts(
    bookings: List<Booking>,
    range: EarningsRange,
    nowEpochMs: Long = System.currentTimeMillis(),
): HeroCounts {
    val todayStart = istStartOfDay(nowEpochMs)
    val days = range.windowDays

    val headline: Int
    val prior: Int
    if (days == null) {
        headline = bookings.size
        prior = 0
    } else {
        val currentStart = todayStart - (days - 1) * MS_PER_DAY
        val priorStart = todayStart - (2 * days - 1) * MS_PER_DAY
        headline = bookings.count { it.createdAtEpochMs in currentStart..nowEpochMs }
        prior = bookings.count { it.createdAtEpochMs in priorStart until currentStart }
    }

    // A zero prior window can't produce a percentage. Treat "something from
    // nothing" as +100% and "nothing from nothing" as flat 0%.
    val percent: Int
    val up: Boolean
    when {
        prior > 0 -> {
            val raw = Math.round((headline - prior) * 100.0 / prior).toInt()
            percent = kotlin.math.abs(raw)
            up = raw >= 0
        }
        headline > 0 -> {
            percent = 100
            up = true
        }
        else -> {
            percent = 0
            up = true
        }
    }
    return HeroCounts(headline = headline, prior = prior, deltaPercent = percent, deltaUp = up)
}

/** Short ALL-CAPS date ("MAY 15") [daysAgo] days back — the chart's axis labels. */
internal fun windowLabel(daysAgo: Int, nowEpochMs: Long = System.currentTimeMillis()): String {
    val fmt = SimpleDateFormat("MMM d", Locale.US).apply { timeZone = IST }
    return fmt.format(nowEpochMs - daysAgo * MS_PER_DAY).uppercase(Locale.US)
}

// ─────────────────────────────────────────────────────────────────────────────
// Stat row: what's committed
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The upcoming-gigs cell: how many confirmed future shows, and when the next one
 * is. [copy] bakes the wording in — singular/plural and the spread case — because
 * the caller only ever renders the string.
 */
data class UpcomingSnapshot(val count: Int, val copy: String)

/**
 * Count + next-gig copy over the confirmed future bookings.
 *
 * Uses gig date, not `createdAt`: the artist is asking "when do I next have to
 * show up", which is a fact about the schedule.
 */
internal fun upcomingSnapshot(
    bookings: List<Booking>,
    nowEpochMs: Long = System.currentTimeMillis(),
): UpcomingSnapshot {
    val upcoming = upcomingConfirmed(bookings, nowEpochMs)
    if (upcoming.isEmpty()) return UpcomingSnapshot(0, "No upcoming gigs")

    val todayStart = istStartOfDay(nowEpochMs)
    // Non-empty by construction: `upcomingConfirmed` admits a booking only when
    // `resolvedStartEpochMs()` answered, and that is a pure parse of the same
    // immutable row — so min/max cannot see an empty list here.
    val starts = upcoming.mapNotNull { it.resolvedStartEpochMs() }

    // Negative deltas are impossible here — `upcomingConfirmed` already dropped
    // anything before today — so no clamp is needed.
    val soonest = ((istStartOfDay(starts.min()) - todayStart) / MS_PER_DAY).toInt()
    val furthest = ((istStartOfDay(starts.max()) - todayStart) / MS_PER_DAY).toInt()
    val copy = when {
        soonest == 0 && furthest == 0 -> "Next gig today"
        // One gig is already covered: with a single start, min == max.
        soonest == furthest -> "Next gig in $soonest ${dayWord(soonest)}"
        else -> "Spread over $furthest days"
    }
    return UpcomingSnapshot(upcoming.size, copy)
}

private fun dayWord(n: Int): String = if (n == 1) "day" else "days"

// ─────────────────────────────────────────────────────────────────────────────
// Bookability breakdown
// ─────────────────────────────────────────────────────────────────────────────

/** One metric bar. A null [value] means "no data yet" and renders as an em-dash. */
data class BreakdownRow(val label: String, val value: Int?)

/**
 * The four score components as displayable rows.
 *
 * With zero completed gigs every metric is a meaningless 0 — and Cancellations,
 * which is shown flipped (100 − rate), would read as a perfect **100%** for an
 * artist who has never taken a booking. So the whole set collapses to nulls
 * until there is at least one gig behind it; the score explainer gates on the
 * same fact for the same reason.
 */
internal fun breakdownRows(breakdown: ScoreBreakdown): List<BreakdownRow> {
    val hasGigs = breakdown.totalGigs > 0
    fun v(value: Int): Int? = if (hasGigs) value else null
    return listOf(
        BreakdownRow("Show-up rate", v(breakdown.showUpRate)),
        BreakdownRow("Reply speed", v(breakdown.replySpeed)),
        BreakdownRow("Reviews", v(breakdown.reviewScore)),
        BreakdownRow("Cancellations", v(100 - breakdown.cancellationRate)),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 14-day strip
// ─────────────────────────────────────────────────────────────────────────────

/** One cell of the availability strip. [key] joins against [bookedDayKeys]. */
data class StripDay(val key: String, val weekday: String, val dayOfMonth: Int)

/** [count] consecutive IST days starting today. */
internal fun calendarStripDays(
    count: Int = 14,
    nowEpochMs: Long = System.currentTimeMillis(),
): List<StripDay> {
    val cal = Calendar.getInstance(IST).apply { timeInMillis = istStartOfDay(nowEpochMs) }
    val weekdayFmt = SimpleDateFormat("EEE", Locale.US).apply { timeZone = IST }
    val keyFmt = dayKeyFormatter()
    return (0 until count).map {
        val day = StripDay(
            key = keyFmt.format(cal.time),
            weekday = weekdayFmt.format(cal.time).uppercase(Locale.US),
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
        )
        cal.add(Calendar.DAY_OF_MONTH, 1)
        day
    }
}

/**
 * Days inside the next [withinDays] that already carry a confirmed gig.
 *
 * Confirmed only. A pending request is not a commitment — shading it would tell
 * the artist a day is spoken for while they still hold the decision.
 */
internal fun bookedDayKeys(
    bookings: List<Booking>,
    withinDays: Int = 14,
    nowEpochMs: Long = System.currentTimeMillis(),
): Set<String> {
    val todayStart = istStartOfDay(nowEpochMs)
    val end = todayStart + withinDays * MS_PER_DAY
    val keyFmt = dayKeyFormatter()
    return bookings
        .asSequence()
        .filter { it.status == BookingStatus.Confirmed }
        .mapNotNull { it.resolvedStartEpochMs() }
        .filter { it in todayStart until end }
        .map { keyFmt.format(it) }
        .toSet()
}

// ─────────────────────────────────────────────────────────────────────────────
// Profile completeness
// ─────────────────────────────────────────────────────────────────────────────

/**
 * What's missing from the artist's public profile, and how honestly we can
 * describe the consequence.
 *
 * [blocksDiscovery] is the one gap that genuinely keeps the artist out of search
 * — an unfinished wizard means the row was never published, so nobody can find
 * them at all. Everything in [strengthGaps] is a profile that IS live but thin,
 * so its copy stays neutral. Overstating a thin profile as "you won't be found"
 * would be false, and the artist would eventually learn to ignore the banner.
 */
data class ProfileGaps(
    val blocksDiscovery: Boolean,
    val strengthGaps: List<String>,
) {
    val needsWork: Boolean get() = blocksDiscovery || strengthGaps.isNotEmpty()

    val headline: String
        get() = if (blocksDiscovery) {
            "Finish your profile — required to appear in search"
        } else {
            "Finish setting up your profile"
        }

    val detail: String
        get() = if (blocksDiscovery) {
            "Your profile isn't live yet — tap to finish setting it up."
        } else {
            "Add ${strengthGaps.joinToString(" and ")} so clients pick you."
        }
}

/**
 * Completeness derived entirely from data the dashboard already loaded — no
 * extra round trip. Genre and bio are wizard-OPTIONAL but leave a visibly thin
 * profile, which is why they count as strength gaps rather than blockers.
 *
 * Photos, packages and samples are deliberately not checked: packages and the
 * tech rider are wizard-required so they are always present by the time this
 * screen renders, and photos would need their own fetch to test.
 *
 * Returns **null** for "can't say" — see [detailUnknown]. The caller renders a
 * null as no banner at all (or keeps whatever the last good refresh decided),
 * which is the only honest answer when the inputs never arrived.
 *
 * @param detailUnknown true when the artist row could not be read at all — the
 * fetch THREW, or there was no session to read it for. [genre] and [bio] live on
 * that row, so their nulls then mean "unknown", not "the artist left them
 * blank". Collapsing the two is what tells a complete artist their profile is
 * thin and walks them back into the wizard on a dropped connection; the same
 * distinction the returning-login router draws between a thrown fetch and a
 * genuinely-absent row. Note the wizard blocker above is decided BEFORE this,
 * because whether setup finished is knowable from the users row alone.
 */
internal fun profileGaps(
    setupComplete: Boolean,
    genre: String?,
    bio: String?,
    detailUnknown: Boolean,
): ProfileGaps? {
    if (!setupComplete) return ProfileGaps(blocksDiscovery = true, strengthGaps = emptyList())
    if (detailUnknown) return null
    val gaps = buildList {
        if (genre.isNullOrBlank()) add("your genre")
        if (bio.isNullOrBlank()) add("a short bio")
    }
    return ProfileGaps(blocksDiscovery = false, strengthGaps = gaps)
}

/**
 * First word of the artist's stage name, for the greeting. Falls back to the
 * universal "there" pre-hydration so a cold launch reads "Hey, there" rather
 * than flashing an empty line where a name is about to appear.
 */
internal fun greetingName(stageName: String?): String =
    stageName?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.takeIf { it.isNotBlank() } ?: "there"

/** "Wednesday, 7 Aug" — the dateline above the greeting. */
internal fun todayLabel(nowEpochMs: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("EEEE, d MMM", Locale.US).apply { timeZone = IST }.format(nowEpochMs)

/** Day-of-month + weekday for an upcoming-gig row's date block, e.g. `"16"` / `"SAT"`. */
data class GigDateParts(val day: String, val weekday: String)

/**
 * Date block for one upcoming gig.
 *
 * Derived from the resolved start instant rather than by slicing up the stored
 * date LABEL. The label's shape has changed across migrations (`EEE, MMM d,
 * yyyy` today, bare `yyyy-MM-dd` on older rows), and string-slicing whichever
 * one arrives is how a row ends up rendering "20" as its day-of-month. A row
 * with no parseable start renders blanks — the client name and fee still carry
 * it, which is better than inventing a date.
 */
internal fun gigDateParts(booking: Booking): GigDateParts {
    val start = booking.resolvedStartEpochMs() ?: return GigDateParts("", "")
    val dayFmt = SimpleDateFormat("d", Locale.US).apply { timeZone = IST }
    val weekdayFmt = SimpleDateFormat("EEE", Locale.US).apply { timeZone = IST }
    return GigDateParts(
        day = dayFmt.format(start),
        weekday = weekdayFmt.format(start).uppercase(Locale.US),
    )
}
