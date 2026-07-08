package `in`.artistant.app.feature.artisthome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.core.config.AppEnvironment
import `in`.artistant.app.data.model.Booking
import `in`.artistant.app.data.model.GigRequestStatus
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.StoredRequest
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.MiniBars
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.component.Sparkline
import `in`.artistant.app.designsystem.component.UploadProgressBanner
import `in`.artistant.app.designsystem.component.tierColor
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.feature.artisthome.ArtistHomeViewModel.Companion.bucketCount
import `in`.artistant.app.feature.artisthome.ArtistHomeViewModel.Companion.dailyBuckets
import `in`.artistant.app.feature.artisthome.ArtistHomeViewModel.Companion.delta
import `in`.artistant.app.feature.artisthome.ArtistHomeViewModel.Companion.upcomingConfirmed
import `in`.artistant.app.feature.artisthome.ArtistHomeViewModel.Companion.windowCounts
import `in`.artistant.app.feature.booking.InitialAvatar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Artist Home dashboard (port of iOS `ArtistHomeView`). Reads top-down: identity →
 * bookings (money proxy) → score → calendar → action items. No card chrome on the
 * lists — hairline dividers between rows, charts inline. Pull-to-refresh re-runs the
 * parallel loads. Navigation is hoisted to the caller (the tab scaffold) so this
 * stays free of the nav graph.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ArtistHomeScreen(
    onOpenScoreExplainer: () -> Unit,
    onManageAvailability: () -> Unit,
    onOpenGigRequest: (String) -> Unit,
    onSubscribe: () -> Unit,
    viewModel: ArtistHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val range by viewModel.range.collectAsStateWithLifecycle()
    val requests by viewModel.requests.collectAsStateWithLifecycle()
    val uploadBanner by viewModel.uploadBanner.collectAsStateWithLifecycle()
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(colors.bg),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            GreetingBar(state.greetingName)
            UploadProgressBanner(state = uploadBanner, onRetryAll = viewModel::clearFinishedUploads)

            if (AppEnvironment.subscriptionsEnabled) SubscribeBanner(onSubscribe)

            state.error?.let { DashboardErrorBanner(it, onRetry = viewModel::refresh) }

            EarningsHero(
                bookings = state.bookings,
                range = range,
                onRange = viewModel::setRange,
            )
            StatRow(state.bookings)
            BookabilityCard(state.breakdown, onOpen = onOpenScoreExplainer)
            AvailabilityStrip(state.bookings, onManage = onManageAvailability)
            RequestsSection(requests, onOpen = onOpenGigRequest)
            UpcomingSection(state.bookings)
            Spacer(Modifier.height(space.xxl))
        }
    }
}

// MARK: - Greeting

private val DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.US)

@Composable
private fun GreetingBar(name: String) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().background(colors.bg).padding(horizontal = space.xl, vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(LocalDate.now().format(DATE_FMT), style = AppTheme.type.footnote, color = colors.ink3)
            Text(
                "Hey, $name",
                style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
        }
        InitialAvatar(name, size = 36)
    }
}

// MARK: - Subscribe + error banners

@Composable
private fun SubscribeBanner(onSubscribe: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier
            .padding(horizontal = space.xl, vertical = space.md)
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(colors.brand)
            .clickable { onSubscribe() }
            .padding(space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Start your 3 months free",
                style = AppTheme.type.callout.copy(fontWeight = FontWeight.Bold),
                color = colors.brandInk,
            )
            Text(
                "Stay listed and keep getting client requests.",
                style = AppTheme.type.caption,
                color = colors.brandInk.copy(alpha = 0.8f),
            )
        }
        Text("›", style = AppTheme.type.headline, color = colors.brandInk)
    }
}

@Composable
private fun DashboardErrorBanner(message: String, onRetry: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().background(colors.bgCard).padding(horizontal = space.xl, vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Couldn't refresh your dashboard",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.hot,
            )
            Text(message, style = AppTheme.type.footnote, color = colors.ink3, maxLines = 1)
        }
        Text(
            "Retry",
            style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.brand,
            modifier = Modifier.clip(CircleShape).clickable { onRetry() }.padding(space.xs),
        )
    }
}

// MARK: - Earnings hero

@Composable
private fun EarningsHero(
    bookings: List<Booking>,
    range: EarningsRange,
    onRange: (EarningsRange) -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val today = LocalDate.now()

    // Single derivation per (bookings, range): counts drive the headline + delta,
    // the series drives the chart. Both from the same bookings — no re-walk fan-out.
    val counts = remember(bookings, range) { windowCounts(bookings, range, today) }
    val series = remember(bookings, range) { dailyBuckets(bookings, bucketCount(range), today) }
    val hasChart = series.any { it > 0 }
    val d = delta(counts.first, counts.second)

    val rangeCopy = when (range) {
        EarningsRange.SevenDays -> "LAST 7 DAYS"
        EarningsRange.ThirtyDays -> "LAST 30 DAYS"
        EarningsRange.All -> "ALL TIME"
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = space.xl, vertical = space.xl)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BOOKINGS · $rangeCopy",
                style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = colors.ink3,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            RangePicker(range, onRange)
        }
        Spacer(Modifier.height(space.sm))

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
            Text(
                "${counts.first}",
                style = AppTheme.type.monoLarge.copy(fontWeight = FontWeight.Bold, fontSize = 52.sp),
                color = colors.ink,
                maxLines = 1,
            )
            if (range != EarningsRange.All) DeltaPill(d)
        }

        Text(
            if (range == EarningsRange.All) {
                if (counts.first > 0) "All time" else "No bookings yet"
            } else {
                "vs ${counts.second} prior ${if (range == EarningsRange.SevenDays) "7 days" else "30 days"}"
            },
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )

        if (hasChart) {
            Spacer(Modifier.height(space.lg))
            Sparkline(series, modifier = Modifier.fillMaxWidth().height(88.dp))
        } else {
            Spacer(Modifier.height(space.lg))
            Text(
                "Your bookings chart appears here as they come in.",
                style = AppTheme.type.caption,
                color = colors.ink3,
            )
        }
    }
}

@Composable
private fun RangePicker(range: EarningsRange, onRange: (EarningsRange) -> Unit) {
    val colors = AppTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        EarningsRange.entries.forEach { r ->
            val sel = r == range
            Text(
                r.label,
                style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = if (sel) colors.brandInk else colors.ink3,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (sel) colors.brand else Color.Transparent)
                    .clickable { onRange(r) }
                    .padding(horizontal = AppTheme.dimens.space.sm, vertical = AppTheme.dimens.space.xs),
            )
        }
    }
}

@Composable
private fun DeltaPill(d: EarningsDelta) {
    val colors = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(if (d.up) "▲" else "▼", style = AppTheme.type.caption, color = if (d.up) colors.good else colors.hot)
        Text(
            "${d.percent}%",
            style = AppTheme.type.footnote.copy(fontWeight = FontWeight.Bold),
            color = if (d.up) colors.good else colors.hot,
        )
    }
}

// MARK: - Stat row (upcoming gigs | bookings 7d)

@Composable
private fun StatRow(bookings: List<Booking>) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val today = LocalDate.now()
    val upcoming = remember(bookings) { upcomingConfirmed(bookings) }
    val b7 = remember(bookings) { dailyBuckets(bookings, 7, today) }
    val b7Count = b7.sumOf { it }.toInt()

    HRule()
    Row(Modifier.fillMaxWidth().padding(horizontal = space.xl, vertical = space.lg)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.sm)) {
            Text("UPCOMING", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${upcoming.size}",
                    style = AppTheme.type.monoLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.warm,
                    modifier = Modifier.weight(1f),
                )
                Text("gig${if (upcoming.size == 1) "" else "s"}", style = AppTheme.type.caption, color = colors.ink3)
            }
            Text(
                if (upcoming.isEmpty()) "No upcoming gigs" else "Confirmed and on the calendar",
                style = AppTheme.type.caption,
                color = colors.ink3,
            )
        }
        Box(Modifier.width(1.dp).height(56.dp).background(colors.lineSoft))
        Column(
            Modifier.weight(1f).padding(start = space.md),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            Text("BOOKINGS / 7D", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$b7Count",
                    style = AppTheme.type.monoLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                if (b7Count > 0) MiniBars(b7, modifier = Modifier.width(86.dp).height(32.dp))
            }
        }
    }
    HRule()
}

// MARK: - Bookability

@Composable
private fun BookabilityCard(breakdown: ScoreBreakdown, onOpen: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val scoreColor = breakdown.numericScore?.let { tierColor(breakdown.tier) } ?: colors.ink3

    Column(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = space.xl, vertical = space.xl),
        verticalArrangement = Arrangement.spacedBy(space.lg),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(space.lg), verticalAlignment = Alignment.Top) {
            ScoreRing(value = breakdown.numericScore, size = 86.dp, stroke = 6.dp, showLabel = false)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("BOOKABILITY", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), color = colors.ink3)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    Text(
                        breakdown.numericScore?.toString() ?: "—",
                        style = AppTheme.type.monoLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp),
                        color = scoreColor,
                    )
                    Text(
                        breakdown.tier.label.uppercase(),
                        style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black),
                        color = scoreColor,
                    )
                }
            }
        }
        // Weighted metric bars. Zero gigs → every value is meaningless (0, or 100 for
        // the flipped Cancellations row), so show "—" + an empty bar instead of a lie.
        val hasGigs = breakdown.totalGigs > 0
        MetricBar("Show-up rate", if (hasGigs) breakdown.showUpRate else null)
        MetricBar("Reply speed", if (hasGigs) breakdown.replySpeed else null)
        MetricBar("Reviews", if (hasGigs) breakdown.reviewScore else null)
        MetricBar("Cancellations", if (hasGigs) 100 - breakdown.cancellationRate else null)
    }
}

@Composable
private fun MetricBar(label: String, value: Int?) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val pct = (value ?: 0).coerceIn(0, 100)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
        Text(label, style = AppTheme.type.footnote, color = colors.ink2, modifier = Modifier.width(120.dp))
        Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(colors.bgSoft)) {
            if (value != null) {
                Box(Modifier.fillMaxWidth(pct / 100f).height(3.dp).clip(CircleShape).background(colors.brand))
            }
        }
        Text(
            value?.toString() ?: "—",
            style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
            color = colors.ink2,
            modifier = Modifier.width(24.dp),
        )
    }
}

// MARK: - 14-day availability strip

@Composable
private fun AvailabilityStrip(bookings: List<Booking>, onManage: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val start = LocalDate.now()
    val booked = remember(bookings) {
        ArtistHomeViewModel.bookedDatesNext14Days(bookings, start)
    }

    HRule()
    Column(Modifier.padding(vertical = space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = space.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "NEXT 14 DAYS",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.ink3,
                modifier = Modifier.weight(1f),
            )
            Text(
                "MANAGE ›",
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
                color = colors.brand,
                modifier = Modifier.clickable { onManage() },
            )
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = space.xl),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items14(start, booked)
        }
    }
}

/** LazyRow content builder for the 14 day cells (booked = brand fill, else hairline). */
private fun androidx.compose.foundation.lazy.LazyListScope.items14(start: LocalDate, booked: Set<LocalDate>) {
    items(14) { i ->
        val date = start.plusDays(i.toLong())
        DayCell(date, isBooked = booked.contains(date))
    }
}

@Composable
private fun DayCell(date: LocalDate, isBooked: Boolean) {
    val colors = AppTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US).uppercase(),
            style = AppTheme.type.monoSmall,
            color = colors.ink3,
        )
        Box(
            Modifier
                .size(width = 42.dp, height = 50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isBooked) colors.brand else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isBooked) colors.brandInk else colors.ink,
            )
        }
    }
}

// MARK: - Requests + upcoming

@Composable
private fun RequestsSection(requests: List<StoredRequest>, onOpen: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.padding(horizontal = space.xl, vertical = space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("New requests", style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold), color = colors.ink, modifier = Modifier.weight(1f))
            val open = requests.count { it.status == GigRequestStatus.Open }
            Text("$open open", style = AppTheme.type.caption, color = colors.ink3)
        }
        if (requests.isEmpty()) {
            Text(
                "Inbox zero — share your profile link to start getting bookings.",
                style = AppTheme.type.footnote,
                color = colors.ink3,
            )
        } else {
            requests.forEachIndexed { i, r ->
                RequestRow(r, onOpen)
                if (i < requests.lastIndex) HRule()
            }
        }
    }
}

@Composable
private fun RequestRow(stored: StoredRequest, onOpen: (String) -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val r = stored.raw
    Column(
        Modifier.fillMaxWidth().clickable { onOpen(stored.id) }.padding(vertical = space.md),
        verticalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(space.md)) {
            InitialAvatar(r.client, size = 32)
            Column(Modifier.weight(1f)) {
                Text(r.client, style = AppTheme.type.callout.copy(fontWeight = FontWeight.Bold), color = colors.ink)
                Text("${r.timeAgo} · ${r.`package`}", style = AppTheme.type.caption, color = colors.ink3)
            }
            Text(
                stored.status.label.uppercase(),
                style = AppTheme.type.caption.copy(fontWeight = FontWeight.Black),
                color = statusColor(stored.status),
            )
        }
        Text(r.message, style = AppTheme.type.footnote, color = colors.ink2, maxLines = 2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.date, style = AppTheme.type.monoSmall, color = colors.ink3, modifier = Modifier.weight(1f))
            Text(
                formatInr(stored.counterAmount ?: r.amount),
                style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
            )
        }
    }
}

@Composable
private fun statusColor(s: GigRequestStatus): Color {
    val c = AppTheme.colors
    return when (s) {
        GigRequestStatus.Open -> c.brand
        GigRequestStatus.Countered -> c.warm
        GigRequestStatus.Accepted -> c.good
        GigRequestStatus.Declined, GigRequestStatus.Expired -> c.ink3
    }
}

@Composable
private fun UpcomingSection(bookings: List<Booking>) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val gigs = remember(bookings) { upcomingConfirmed(bookings) }
    Column(Modifier.padding(horizontal = space.xl, vertical = space.lg), verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text("Upcoming gigs", style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold), color = colors.ink)
        if (gigs.isEmpty()) {
            Text("No upcoming gigs yet", style = AppTheme.type.footnote, color = colors.ink3)
        } else {
            gigs.forEachIndexed { i, g ->
                UpcomingRow(g)
                if (i < gigs.lastIndex) HRule()
            }
        }
    }
}

@Composable
private fun UpcomingRow(b: Booking) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().padding(vertical = space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Column(Modifier.width(56.dp)) {
            Text(b.clientFullName ?: b.venue, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink, maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            Text(b.dateLabel, style = AppTheme.type.footnote, color = colors.ink, maxLines = 1)
            Text("${b.timeLabel} · ${b.venue}", style = AppTheme.type.caption, color = colors.ink3, maxLines = 1)
        }
        Text(formatInr(b.total), style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold), color = colors.ink)
    }
}

