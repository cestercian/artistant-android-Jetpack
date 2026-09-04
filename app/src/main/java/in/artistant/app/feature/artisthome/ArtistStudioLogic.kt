package `in`.artistant.app.feature.artisthome

import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.BookingStatus
import `in`.artistant.app.data.model.resolvedStartEpochMs
import `in`.artistant.app.data.repository.ScoreHistoryPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The Sep-2026 artist studio's derivations: money, standing, and which of the
 * three dashboards the screen is allowed to draw.
 *
 * Split from [ArtistHomeLogic] rather than bolted onto it because everything
 * here is about the redesign's **money-first** ordering (screens 09 / 85 / 86 /
 * 133), where that file is about the counts the old dashboard led with. Same
 * rules apply: pure functions over an injected clock, everything anchored to
 * IST, nothing that reads the network.
 *
 * The one non-obvious constraint running through all of it: **Artistant never
 * holds the money.** v1 is a matchmaker — the fee is agreed in the app and paid
 * outside it — so every rupee figure below is an *agreed fee*, never a payout,
 * and the screens that show one say so (screen 133's note is not decoration).
 */

private val IST: TimeZone get() = TimeZone.getTimeZone("Asia/Kolkata")

private fun istCalendar(epochMs: Long): Calendar =
    Calendar.getInstance(IST).apply { timeInMillis = epochMs }

/** Midnight-in-IST for the instant [epochMs] falls in. */
private fun istStartOfDay(epochMs: Long): Long = istCalendar(epochMs).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** First instant of the IST calendar month [epochMs] falls in. */
internal fun istStartOfMonth(epochMs: Long): Long = istCalendar(epochMs).apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

// ─────────────────────────────────────────────────────────────────────────────
// What counts as money
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Bookings whose fee both parties have actually agreed to.
 *
 * `pending_confirm` is excluded on purpose: it is a request the artist has not
 * answered, so its `fee` is a number someone *asked* for. Counting it would put
 * a stranger's opening offer into the artist's earnings, and declining it would
 * make their earnings go down. `cancelled`, `disputed` and the decode-only
 * [BookingStatus.Unknown] are out for the obvious reasons.
 */
internal fun isAgreed(booking: Booking): Boolean =
    booking.status == BookingStatus.Confirmed || booking.status == BookingStatus.Completed

/**
 * Has this gig already happened?
 *
 * Keyed on the gig's own start instant rather than on `completed`, because the
 * server has no job that flips a confirmed booking to `completed` the morning
 * after — an artist with a year of played shows would otherwise read zero. A row
 * that is explicitly `completed` counts regardless of what its date parses to,
 * so a missing or unreadable `date_label` cannot un-play a finished gig.
 */
internal fun isPlayed(booking: Booking, nowEpochMs: Long): Boolean =
    booking.status == BookingStatus.Completed ||
        (booking.resolvedStartEpochMs()?.let { it < nowEpochMs } == true)

/**
 * Screen 09's money card: what the current IST calendar month has been worth.
 *
 * Three numbers, and they are deliberately three rather than one:
 *
 *  - [playedInr] / [showsPlayed] — fees for gigs this month that have happened.
 *    This is the headline, because it is the only figure that is finished.
 *  - [aheadInr] — fees already agreed for gigs still to come this month. The
 *    design's second line reads "₹48,000 awaiting settlement", which this build
 *    cannot say: nothing is in custody and nothing is being settled. This is the
 *    honest version of the same fact — money agreed, not yet played.
 */
data class MonthMoney(
    val playedInr: Int,
    val showsPlayed: Int,
    val aheadInr: Int,
    val gigsAhead: Int,
) {
    val isEmpty: Boolean get() = playedInr == 0 && aheadInr == 0 && showsPlayed == 0 && gigsAhead == 0
}

internal fun monthMoney(
    bookings: List<Booking>,
    nowEpochMs: Long = System.currentTimeMillis(),
): MonthMoney {
    val monthStart = istStartOfMonth(nowEpochMs)
    val nextMonthStart = istCalendar(monthStart).apply { add(Calendar.MONTH, 1) }.timeInMillis

    var played = 0
    var playedCount = 0
    var ahead = 0
    var aheadCount = 0
    for (b in bookings) {
        if (!isAgreed(b)) continue
        val start = b.resolvedStartEpochMs() ?: continue
        if (start < monthStart || start >= nextMonthStart) continue
        if (isPlayed(b, nowEpochMs)) {
            played += b.fee
            playedCount += 1
        } else {
            ahead += b.fee
            aheadCount += 1
        }
    }
    return MonthMoney(
        playedInr = played,
        showsPlayed = playedCount,
        aheadInr = ahead,
        gigsAhead = aheadCount,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Screen 133 — Earnings
// ─────────────────────────────────────────────────────────────────────────────

/** The window screen 133's segmented control picks between. */
enum class EarningsWindow(val label: String) {
    ThirtyDays("30 days"),
    ThisYear("This year"),
    AllTime("All time"),
}

/** One bar of the earnings chart. [recent] bars take the accent; the rest don't. */
data class EarningsBar(val label: String, val amountInr: Int, val recent: Boolean)

/**
 * One earned line on screen 133's RECENT list.
 *
 * [state] is the word under the amount. The design prints "Agreed" / "Settled";
 * settlement is not a thing this product can observe, so the two words here are
 * "Agreed" (confirmed, still to play) and "Played" (it happened). Both are facts
 * the booking row can answer for.
 */
data class EarningsRow(
    val bookingId: String,
    val title: String,
    val dateLabel: String,
    val amountInr: Int,
    val state: String,
)

/**
 * Everything screen 133 renders for one window.
 *
 * [deltaPercent] is null when there is no comparable prior window — [AllTime]
 * has none by definition, and a window whose predecessor earned nothing cannot
 * produce a percentage. The screen hides the pill rather than printing +100%
 * against nothing, which is the same rule [heroCounts] follows one screen over.
 */
data class EarningsSummary(
    val totalInr: Int,
    val gigCount: Int,
    val deltaPercent: Int?,
    val deltaUp: Boolean,
    val bars: List<EarningsBar>,
    val rows: List<EarningsRow>,
) {
    /** A chart of nothing but zeros is a flat line pretending to be data. */
    val hasChart: Boolean get() = bars.any { it.amountInr > 0 }
}

/** One window's half-open gig-date span, plus the span it is compared against. */
private data class EarningsSpan(
    val start: Long,
    val end: Long,
    val priorStart: Long,
    val priorEnd: Long,
)

private const val MONTH_BUCKETS = 12
private const val RECENT_ROWS = 6

/**
 * Agreed fees over [window], bucketed for the chart and listed for RECENT.
 *
 * Buckets by **gig date**, not by when the booking was created: this screen
 * answers "what did I earn in September", and a gig booked in March for
 * September is September's money. That is the opposite choice from the
 * dashboard's inbound-demand chart, and deliberately so.
 */
internal fun earningsSummary(
    bookings: List<Booking>,
    window: EarningsWindow,
    nowEpochMs: Long = System.currentTimeMillis(),
): EarningsSummary {
    val agreed = bookings.filter { isAgreed(it) && it.resolvedStartEpochMs() != null }

    // Each window is a half-open [start, end) span of GIG dates.
    //
    // The end matters as much as the start, and it is not always "now". "30
    // days" is a look-BACK — a gig next Friday is not something the last thirty
    // days earned. "This year" is a calendar year, so it legitimately contains
    // gigs that are agreed but not yet played, which is why the list below has
    // an "Agreed" state at all. "All time" is unbounded in both directions.
    val span = when (window) {
        EarningsWindow.ThirtyDays -> {
            val start = istStartOfDay(nowEpochMs) - 29L * MS_PER_DAY
            EarningsSpan(start, nowEpochMs, start - 30L * MS_PER_DAY, start)
        }
        EarningsWindow.ThisYear -> {
            val jan = istCalendar(nowEpochMs).apply {
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val nextJan = istCalendar(jan).apply { add(Calendar.YEAR, 1) }.timeInMillis
            val priorJan = istCalendar(jan).apply { add(Calendar.YEAR, -1) }.timeInMillis
            EarningsSpan(jan, nextJan, priorJan, jan)
        }
        // No bounds and, by construction, no prior period to compare with.
        EarningsWindow.AllTime -> EarningsSpan(Long.MIN_VALUE, Long.MAX_VALUE, 0, 0)
    }

    val inWindow = agreed.filter { b ->
        val start = b.resolvedStartEpochMs() ?: return@filter false
        start >= span.start && start < span.end
    }
    val total = inWindow.sumOf { it.fee }

    val deltaPercent: Int?
    val deltaUp: Boolean
    if (window == EarningsWindow.AllTime) {
        deltaPercent = null
        deltaUp = true
    } else {
        val prior = agreed.filter { b ->
            val start = b.resolvedStartEpochMs() ?: return@filter false
            start >= span.priorStart && start < span.priorEnd
        }.sumOf { it.fee }
        if (prior > 0) {
            val raw = Math.round((total - prior) * 100.0 / prior).toInt()
            deltaPercent = kotlin.math.abs(raw)
            deltaUp = raw >= 0
        } else {
            deltaPercent = null
            deltaUp = true
        }
    }

    return EarningsSummary(
        totalInr = total,
        gigCount = inWindow.size,
        deltaPercent = deltaPercent,
        deltaUp = deltaUp,
        bars = earningsBars(inWindow, nowEpochMs),
        rows = recentRows(inWindow, nowEpochMs),
    )
}

/**
 * The chart's buckets.
 *
 * Monthly for every window, twelve of them, because a 30-bar daily chart of an
 * artist who plays four gigs a month is 26 empty columns and 4 spikes — which
 * reads as "you barely work" rather than as "you play weekends". The 30-day
 * window therefore shows the twelve months ENDING now with the window's own
 * total called out above it; the chart is context, the figure is the answer.
 *
 * The most recent quarter takes the accent, matching the design's three lime
 * bars out of twelve.
 */
private fun earningsBars(
    inWindow: List<Booking>,
    nowEpochMs: Long,
): List<EarningsBar> {
    val monthFmt = SimpleDateFormat("MMM", Locale.US).apply { timeZone = IST }

    val buckets = IntArray(MONTH_BUCKETS)
    val labels = arrayOfNulls<String>(MONTH_BUCKETS)
    val cursor = istCalendar(istStartOfMonth(nowEpochMs)).apply {
        add(Calendar.MONTH, -(MONTH_BUCKETS - 1))
    }
    for (i in 0 until MONTH_BUCKETS) {
        labels[i] = monthFmt.format(cursor.time).uppercase(Locale.US)
        cursor.add(Calendar.MONTH, 1)
    }

    val firstBucketStart = istCalendar(istStartOfMonth(nowEpochMs))
        .apply { add(Calendar.MONTH, -(MONTH_BUCKETS - 1)) }
        .timeInMillis
    for (b in inWindow) {
        val start = b.resolvedStartEpochMs() ?: continue
        if (start < firstBucketStart) continue
        val index = monthsBetween(firstBucketStart, start)
        if (index in 0 until MONTH_BUCKETS) buckets[index] += b.fee
    }

    val recentFrom = MONTH_BUCKETS - MONTH_BUCKETS / 4
    return (0 until MONTH_BUCKETS).map { i ->
        EarningsBar(
            label = labels[i].orEmpty(),
            amountInr = buckets[i],
            recent = i >= recentFrom,
        )
    }
}

/** Whole IST months from [fromEpochMs]'s month to [toEpochMs]'s month. */
private fun monthsBetween(fromEpochMs: Long, toEpochMs: Long): Int {
    val from = istCalendar(fromEpochMs)
    val to = istCalendar(toEpochMs)
    return (to.get(Calendar.YEAR) - from.get(Calendar.YEAR)) * 12 +
        (to.get(Calendar.MONTH) - from.get(Calendar.MONTH))
}

private fun recentRows(inWindow: List<Booking>, nowEpochMs: Long): List<EarningsRow> {
    val dateFmt = SimpleDateFormat("d MMM", Locale.US).apply { timeZone = IST }
    return inWindow
        .sortedByDescending { it.resolvedStartEpochMs() ?: 0L }
        .take(RECENT_ROWS)
        .map { b ->
            val start = b.resolvedStartEpochMs()
            EarningsRow(
                bookingId = b.id,
                title = earningsRowTitle(b),
                dateLabel = start?.let { dateFmt.format(it) } ?: b.date,
                amountInr = b.fee,
                state = if (isPlayed(b, nowEpochMs)) "Played" else "Agreed",
            )
        }
}

/**
 * "Sangeet · Rhea Menon" — the package the gig was booked as, then who booked
 * it. Either half may be missing; the venue is never used as a fallback for the
 * client name (a fresh booking's venue defaults to the literal "TBD", which
 * would render a gig for a client called TBD — see [artistClientDisplayName]).
 */
internal fun earningsRowTitle(booking: Booking): String {
    val pkg = booking.packageName?.trim()?.takeIf { it.isNotEmpty() && it != "Custom" }
    val client = booking.clientFullName?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        pkg != null && client != null -> "$pkg · $client"
        client != null -> client
        pkg != null -> pkg
        else -> "Gig"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Standing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Screen 09's "+4" beside the Bookability figure: how far the score has moved
 * over [windowDays].
 *
 * Null whenever there is nothing to compare against — an empty history, or a
 * history that only starts inside the window. That is not the same as "it hasn't
 * moved": printing a confident "+0" for an artist whose score we have only ever
 * seen once is inventing a trend out of one point.
 *
 * The baseline is the LAST reading at or before the cutoff (the score as it
 * stood when the window opened), not the oldest reading overall — otherwise an
 * artist with two years of history would be shown a two-year delta under a
 * one-month label.
 */
internal fun scoreDelta(
    history: List<ScoreHistoryPoint>,
    currentScore: Int,
    windowDays: Int = 30,
    nowEpochMs: Long = System.currentTimeMillis(),
): Int? {
    if (history.isEmpty()) return null
    val cutoff = nowEpochMs - windowDays * MS_PER_DAY
    val dated = history.mapNotNull { point ->
        parseIsoInstant(point.computedAtIso)?.let { it to point.score }
    }
    if (dated.isEmpty()) return null
    val baseline = dated.filter { it.first <= cutoff }.maxByOrNull { it.first }?.second
        ?: return null
    return currentScore - baseline
}

/**
 * Lenient ISO-8601 read, matching the shapes PostgREST emits for a `timestamptz`
 * (`+00:00`, `Z`, and with or without fractional seconds).
 */
internal fun parseIsoInstant(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    val cleaned = iso.trim().replace("Z", "+0000")
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
    )
    for (p in patterns) {
        val parsed = runCatching {
            SimpleDateFormat(p, Locale.US).apply { isLenient = true }.parse(cleaned)?.time
        }.getOrNull()
        if (parsed != null) return parsed
    }
    return null
}

// ─────────────────────────────────────────────────────────────────────────────
// Which dashboard (screens 09 / 85 / 86)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The three dashboards the design draws, as one decision.
 *
 * They are not cosmetic variants. [Unavailable] exists because of the one
 * failure mode on this screen that costs real money: a dropped read leaves the
 * availability strip with no booked days in it, and a strip with no booked days
 * renders as *fourteen open days*. An artist glancing at that accepts a gig on a
 * night they are already playing. So when nothing landed, nothing is drawn as
 * open — every figure is an em-dash and the strip is inert grey (screen 86).
 *
 * [Cold] is the opposite problem: a genuinely new artist gets four zeros, which
 * is accurate and reads as failure. Screen 85 turns the same fact into the one
 * thing that is actually true and useful — every day is open.
 */
enum class DashboardMode { Ready, Cold, Unavailable }

internal fun dashboardMode(
    hasLoaded: Boolean,
    hasError: Boolean,
    money: MonthMoney,
    openRequests: Int,
    upcomingGigs: Int,
    bookings7d: Int,
): DashboardMode = when {
    // Never Cold on a failed first load: "all 14 days open" is exactly the
    // sentence a dashboard must not say when it has not read the calendar.
    !hasLoaded && hasError -> DashboardMode.Unavailable
    money.isEmpty && openRequests == 0 && upcomingGigs == 0 && bookings7d == 0 -> DashboardMode.Cold
    else -> DashboardMode.Ready
}

/** The header's second line, per mode (screens 09 / 85 / 86). */
internal fun dashboardSubtitle(mode: DashboardMode, stageName: String?): String = when (mode) {
    DashboardMode.Unavailable -> "Some of this is out of date"
    DashboardMode.Cold -> "Nothing needs you right now"
    DashboardMode.Ready -> stageName?.trim()?.takeIf { it.isNotEmpty() } ?: "Your studio"
}

/**
 * Screen 85's line under the 14-day strip.
 *
 * Counts the days in the strip that carry no confirmed gig. "All 14 days open"
 * is the cold case; the partial case still says how many, because "some days are
 * open" is not something an artist can plan against.
 */
internal fun stripOpenDaysCopy(totalDays: Int, bookedDays: Int): String {
    val open = (totalDays - bookedDays).coerceAtLeast(0)
    return when {
        open == 0 -> "Every day in the next $totalDays is spoken for."
        open == totalDays -> "All $totalDays days open — you'll show up in every search for them."
        else -> "$open of $totalDays days open — you'll show up in every search for those."
    }
}

private const val MS_PER_DAY = 24L * 60 * 60 * 1000
