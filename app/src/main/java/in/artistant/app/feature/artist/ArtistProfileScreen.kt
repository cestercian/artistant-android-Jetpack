package `in`.artistant.app.feature.artist

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.data.model.ScoreBreakdown
import `in`.artistant.app.data.model.averageRating
import `in`.artistant.app.designsystem.component.BottomDarkenScrim
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.MediaContainer
import `in`.artistant.app.designsystem.component.Pill
import `in`.artistant.app.designsystem.component.ScoreBreakdownSheet
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.component.tierColor
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import kotlinx.coroutines.launch

/**
 * The client-facing artist profile (iOS `ArtistView`, Direction-D layout): a
 * ~48%-height hero over a single scroll of About → Packages → Reviews →
 * disclosure rows, with a floating glass dock (Message + Check availability).
 * Media + samples ride on [artist]; reviews + score come from the ViewModel.
 */
@Composable
fun ArtistProfileScreen(
    loaded: ArtistProfileUiState.Loaded,
    isSaved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onBooking: (String) -> Unit,
    onRequestQuote: (String) -> Unit,
    onMessage: (String) -> Unit,
) {
    val artist = loaded.artist
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var showScoreSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(colors.bg)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Hero(
                    artist = artist,
                    isSaved = isSaved,
                    onBack = onBack,
                    onSave = onSave,
                    onScoreTap = { showScoreSheet = true },
                )
            }
            item { AboutSection(artist, Modifier.padding(horizontal = space.lg, vertical = space.xl)) }
            item { BookingSection(artist, Modifier.padding(horizontal = space.lg)) }
            item { PackagesSection(artist, onRequestQuote, Modifier.padding(top = space.xl)) }
            item { ReviewsSection(loaded, Modifier.padding(horizontal = space.lg, vertical = space.xl)) }
            item { IndexRows(artist, Modifier.padding(horizontal = space.lg).padding(bottom = 120.dp)) }
        }

        GlassDock(
            onMessage = { onMessage(artist.id) },
            onBooking = { onBooking(artist.id) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showScoreSheet) {
        ScoreBreakdownSheet(
            artist = artist,
            breakdown = loaded.breakdown,
            reviews = loaded.reviews,
            reviewsFailed = loaded.reviewsFailed,
            onDismiss = { showScoreSheet = false },
        )
    }
}

// MARK: - Hero

@Composable
private fun Hero(
    artist: Artist,
    isSaved: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onScoreTap: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val context = LocalContext.current
    // 48% of the screen, expressed as an aspect ratio so MediaContainer stays
    // the one sanctioned home of the crop-fill image (iOS §9).
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val heroHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.48f).dp
        val aspect = (maxWidth.value / heroHeight.value).coerceAtLeast(0.1f)
        MediaContainer(
            aspectRatio = aspect,
            remoteUrl = artist.coverUrl,
            fallbackGradient = artist.gradient,
            contentDescription = artist.name,
        ) {
            BottomDarkenScrim()

            // Top glass controls.
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(space.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                GlassCircle(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    GlassCircle(
                        if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        if (isSaved) "Unsave artist" else "Save artist",
                        onSave,
                        tint = if (isSaved) colors.brand else Color.White,
                    )
                    GlassCircle(Icons.Filled.Share, "Share artist", onClick = { shareArtist(context, artist) })
                }
            }

            // Identity block + score chip on the hero's bottom edge.
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(space.lg),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    Pill(text = artist.category)
                    Pill(text = artist.city)
                }
                Spacer(Modifier.height(space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, style = AppTheme.type.displayTitle, color = Color.White, maxLines = 2)
                        Text(artist.genre, style = AppTheme.type.callout, color = Color.White.copy(alpha = 0.75f))
                    }
                    Spacer(Modifier.width(space.sm))
                    ScoreChip(artist, onScoreTap)
                }
            }
        }
    }
}

@Composable
private fun ScoreChip(artist: Artist, onTap: () -> Unit) {
    val colors = AppTheme.colors
    val tier = ScoreBands.tier(artist.score, artist.gigs)
    Row(
        Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable { onTap() }
            .padding(start = AppTheme.dimens.space.sm, end = AppTheme.dimens.space.md)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.sm),
    ) {
        ScoreRing(value = if (tier == ScoreTier.New) null else artist.score, size = 34.dp, stroke = 3.dp, showLabel = false)
        Column {
            Text(tier.label.uppercase(), style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold), color = tierColor(tier))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Details", style = AppTheme.type.monoSmall, color = Color.White.copy(alpha = 0.6f))
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun GlassCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
) {
    // ponytail: approximates iOS Liquid Glass with a translucent circle — a real
    // blur (RenderEffect, API 31+) is a nice-to-have, deferred.
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

// MARK: - About

@Composable
private fun AboutSection(artist: Artist, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    var expanded by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.lg)) {
        Text("About", style = AppTheme.type.displaySmall, color = colors.ink)
        // TODO(gallery): iOS renders a horizontal photo strip here; needs the Android
        // Artist model to carry a gallery-photo list via an artist_media fetch (a
        // model + repo change tracked as a follow-up), so it's intentionally absent.
        if (artist.bio.isNotEmpty()) {
            Text(
                artist.bio,
                style = AppTheme.type.body,
                color = colors.ink2,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
            )
            if (artist.bio.length > 180) {
                Text(
                    if (expanded) "Less" else "More",
                    style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
    }
}

// MARK: - Booking

/**
 * The dossier block between About and Packages (iOS `ArtistView.bookingSection`):
 * a "from" price (the cheapest package — package order is wizard-authored, so
 * min() not first()) and a short row of "usually free" weekday chips. No stats,
 * no buttons — those live in the score sheet / the glass dock respectively.
 */
@Composable
private fun BookingSection(artist: Artist, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val cheapest = artist.packages.minByOrNull { it.price }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text("Booking", style = AppTheme.type.displaySmall, color = colors.ink)

        if (cheapest != null) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Text(formatInr(cheapest.price), style = AppTheme.type.monoLarge, color = colors.ink)
                Text(
                    "from · ${cheapest.name}",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }

        // "Usually free" — HONEST availability: the artist's weekday preference set
        // is the only signal (no per-date collision data), so the copy claims a
        // pattern, not a promise. Hidden entirely when no preference is set.
        if (artist.daysAvailable.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
                Text("USUALLY FREE", style = AppTheme.type.caption, color = colors.ink3)
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    artist.daysAvailable.take(3).forEach { day ->
                        Text(
                            day.uppercase(),
                            style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.ink,
                            modifier = Modifier
                                .border(1.dp, colors.line, RoundedCornerShape(AppTheme.dimens.radii.sm))
                                .padding(horizontal = space.md, vertical = space.sm),
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Packages

@Composable
private fun PackagesSection(artist: Artist, onRequestQuote: (String) -> Unit, modifier: Modifier = Modifier) {
    if (artist.packages.isEmpty()) return
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val initial = artist.packages.indexOfFirst { it.popular }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initial, pageCount = { artist.packages.size })
    val scope = rememberCoroutineScope()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text("Packages", style = AppTheme.type.displaySmall, color = colors.ink, modifier = Modifier.padding(horizontal = space.lg))
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = space.lg, vertical = space.xs),
            pageSpacing = space.md,
        ) { page ->
            // Tapping a peeking ticket scrolls it to centre so the active lime wash
            // follows the tap (iOS parity: iOS sets `pkg = i` on tap). `active`
            // tracks pagerState.currentPage, so swipe + tap stay in agreement.
            TicketCard(artist.packages[page], active = page == pagerState.currentPage) {
                scope.launch { pagerState.animateScrollToPage(page) }
            }
        }
        // The negotiation entry lives with the pricing context (iOS parity).
        Row(
            Modifier.padding(horizontal = space.lg).clickable { onRequestQuote(artist.id) },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Custom date or budget?", style = AppTheme.type.footnote, color = colors.ink3)
            Text("Request a quote", style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold), color = colors.brand)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.brand, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * A package "ticket". ponytail: a clean bordered card, NOT the iOS perforated
 * ticket-stub (dashed tear line + edge notches) — that's a nice-to-have,
 * approximated simply here. TODO(M5): dashed perforation + notch cutouts.
 */
@Composable
private fun TicketCard(pkg: ArtistPackage, active: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .background(if (active) colors.brandSoft else Color.Transparent)
            .border(1.dp, if (active) colors.brand.copy(alpha = 0.4f) else colors.lineSoft, RoundedCornerShape(AppTheme.dimens.radii.md))
            .clickable { onClick() }
            .padding(space.lg),
    ) {
        Text(
            if (pkg.popular) "MOST BOOKED" else pkg.duration.uppercase(),
            style = AppTheme.type.monoSmall.copy(fontWeight = FontWeight.Bold),
            color = if (pkg.popular) colors.brand else colors.ink4,
        )
        Spacer(Modifier.height(space.xs))
        Text(pkg.name, style = AppTheme.type.headline.copy(fontWeight = FontWeight.Bold), color = colors.ink, maxLines = 1)
        val subtitle = pkg.includes.take(2).joinToString(" · ").ifEmpty { pkg.duration }
        Text(subtitle, style = AppTheme.type.caption, color = colors.ink3, maxLines = 1)
        Spacer(Modifier.height(space.md))
        HRule()
        Spacer(Modifier.height(space.md))
        Text(formatInr(pkg.price), style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold), color = if (active) colors.ink else colors.ink2)
    }
}

// MARK: - Reviews

@Composable
private fun ReviewsSection(loaded: ArtistProfileUiState.Loaded, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val reviews = loaded.reviews
    Column(modifier, verticalArrangement = Arrangement.spacedBy(space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Reviews", style = AppTheme.type.displaySmall, color = colors.ink)
            Spacer(Modifier.weight(1f))
            if (reviews.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = colors.brand, modifier = Modifier.size(14.dp))
                    Text(String.format("%.1f", reviews.averageRating()), style = AppTheme.type.monoMedium.copy(fontWeight = FontWeight.Bold), color = colors.ink)
                    Text("· ${reviews.size}", style = AppTheme.type.monoMedium, color = colors.ink3)
                }
            }
        }
        when {
            loaded.reviewsFailed -> ReviewNotice("Couldn't load reviews", "This artist may have reviews — we just couldn't load them.")
            reviews.isEmpty() -> ReviewNotice("No reviews yet", "Reviews land here a few hours after each show ends.")
            else -> reviews.forEach { ReviewRow(it) }
        }
    }
}

@Composable
private fun ReviewRow(r: Review) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.width(2.dp).height(64.dp).background(colors.line))
        Spacer(Modifier.width(space.md))
        Column(verticalArrangement = Arrangement.spacedBy(space.sm)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(5) { i ->
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (i < r.rating) colors.brand else colors.ink4,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            if (r.body.isNotEmpty()) {
                Text(
                    "“${r.body}”",
                    style = AppTheme.type.displaySmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(17f, androidx.compose.ui.unit.TextUnitType.Sp), fontStyle = FontStyle.Italic),
                    color = colors.ink,
                )
            }
            val attribution = if (r.org.isEmpty()) r.name.uppercase() else "${r.name.uppercase()} · ${r.org.uppercase()}"
            Text(attribution, style = AppTheme.type.monoSmall, color = colors.ink3)
        }
    }
}

@Composable
private fun ReviewNotice(title: String, sub: String) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppTheme.dimens.radii.md))
            .border(1.dp, colors.lineSoft, RoundedCornerShape(AppTheme.dimens.radii.md))
            .padding(AppTheme.dimens.space.lg),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink2)
        Text(sub, style = AppTheme.type.footnote, color = colors.ink3)
    }
}

// MARK: - Index rows (Sound / Tech rider / social)

/**
 * The quiet tail of the page (iOS `ArtistView.indexRows`): hairline utility rows.
 * Every affordance here is REAL — nothing shows a tappable chevron that does
 * nothing:
 *   • Sound — the artist's samples as READ-ONLY rows (title + duration). No play
 *     control: audio playback + the Spotify embed are M5.
 *   • Tech rider — a real expandable disclosure (tap toggles) revealing the tech
 *     checklist inline. Hidden when the artist has no tech items.
 *   • Social — one link-out row per platform the artist actually linked, each
 *     opening the URL via an ACTION_VIEW intent.
 */
@Composable
private fun IndexRows(artist: Artist, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var techExpanded by remember { mutableStateOf(false) }
    Column(modifier) {
        HRule()

        // Sound — read-only sample rows. TODO(M5): Spotify embed (webview) + audio
        // playback; until then render titles + durations with NO play/chevron so
        // nothing implies playback that doesn't exist.
        if (artist.samples.isNotEmpty()) {
            SoundHeaderRow(artist.samples.size)
            artist.samples.forEach { SampleRow(it.title, it.duration) }
            HRule()
        }

        // Tech rider — an interactive disclosure (the only chevron on this list).
        if (artist.tech.isNotEmpty()) {
            DisclosureRow(
                title = "Tech rider",
                detail = "${artist.tech.size} item${if (artist.tech.size == 1) "" else "s"}",
                expanded = techExpanded,
                onToggle = { techExpanded = !techExpanded },
            )
            if (techExpanded) {
                Column(Modifier.padding(bottom = AppTheme.dimens.space.md)) {
                    artist.tech.forEach { TechRow(it) }
                }
            }
            HRule()
        }

        // Social link-outs — Instagram, then Spotify OR YouTube (Spotify wins when
        // both exist, mirroring iOS). Only the platforms the artist linked appear.
        val instagram = artist.instagramHandle
        if (instagram != null) {
            LinkOutRow("Instagram", "${artist.followers} followers") {
                openUrl(context, "https://www.instagram.com/$instagram")
            }
            HRule()
        }
        val spotify = artist.spotifyArtistUrl
        val youtube = artist.youtubeChannelUrl
        if (spotify != null) {
            LinkOutRow("Spotify", "${artist.streams} monthly") { openUrl(context, spotify) }
            HRule()
        } else if (youtube != null) {
            LinkOutRow("YouTube", "${artist.streams} subscribers") { openUrl(context, youtube) }
            HRule()
        }
    }
}

/** A non-tappable label row heading the read-only Sound section. */
@Composable
private fun SoundHeaderRow(trackCount: Int) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Sound", style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
        Spacer(Modifier.weight(1f))
        Text(
            "$trackCount track${if (trackCount == 1) "" else "s"}",
            style = AppTheme.type.footnote,
            color = colors.ink3,
        )
    }
}

/**
 * A read-only sample row (title + duration). Intentionally NOT clickable and with
 * NO play control — audio playback lands in M5; implying it now would be a lie.
 */
@Composable
private fun SampleRow(title: String, duration: String) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppTheme.type.callout, color = colors.ink2, maxLines = 1, modifier = Modifier.weight(1f))
        Text(duration, style = AppTheme.type.monoSmall, color = colors.ink3)
    }
}

/** An expandable disclosure row — the rotating chevron marks it as interactive. */
@Composable
private fun DisclosureRow(title: String, detail: String, expanded: Boolean, onToggle: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = AppTheme.dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
        Spacer(Modifier.weight(1f))
        Text(detail, style = AppTheme.type.footnote, color = colors.ink3)
        Spacer(Modifier.width(AppTheme.dimens.space.sm))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(14.dp).rotate(if (expanded) 90f else 0f),
        )
    }
}

/** One tech-rider checklist item (lime check + text), shown inline when expanded. */
@Composable
private fun TechRow(item: String) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        Modifier.fillMaxWidth().padding(vertical = space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(space.sm),
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.brand, modifier = Modifier.size(16.dp))
        Text(item, style = AppTheme.type.callout, color = colors.ink2)
    }
}

/** An outbound social row: title + mono value + ↗; opens the URL on tap. */
@Composable
private fun LinkOutRow(title: String, value: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = AppTheme.dimens.space.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppTheme.type.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.ink)
        Spacer(Modifier.weight(1f))
        if (value.isNotEmpty()) {
            Text(value, style = AppTheme.type.monoSmall, color = colors.ink3)
            Spacer(Modifier.width(AppTheme.dimens.space.sm))
        }
        Icon(Icons.Filled.NorthEast, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(14.dp))
    }
}

// MARK: - Intents

/**
 * Fire an ACTION_VIEW for a social link. Guarded so a missing browser / malformed
 * URL is a no-op, not a crash (iOS `UIApplication.open` fails silently too).
 */
private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/**
 * Share the artist's (intended) public EPK link via ACTION_SEND (iOS
 * `artistShareURL`): `artistant.in/<handle>`, or the bare domain if the handle is
 * blank.
 */
private fun shareArtist(context: Context, artist: Artist) {
    val handle = artist.handle.trim()
    val url = if (handle.isEmpty()) "https://artistant.in" else "https://artistant.in/$handle"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, artist.name)
        putExtra(Intent.EXTRA_TEXT, "Book ${artist.name} on Artistant\n$url")
    }
    runCatching { context.startActivity(Intent.createChooser(send, "Share artist")) }
}

// MARK: - Glass dock

@Composable
private fun GlassDock(onMessage: () -> Unit, onBooking: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    // ponytail: translucent capsule approximating iOS Liquid Glass; real blur deferred.
    Row(
        modifier
            .padding(space.lg)
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.bgElev.copy(alpha = 0.92f))
            .border(1.dp, colors.line, CircleShape)
            .padding(space.sm),
        horizontalArrangement = Arrangement.spacedBy(space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Message",
            style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .clickable { onMessage() }
                .padding(horizontal = space.lg, vertical = space.md),
        )
        Row(
            Modifier
                .weight(1f)
                .clip(CircleShape)
                .background(colors.brand)
                .clickable { onBooking() }
                .padding(vertical = space.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Check availability →", style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.brandInk)
        }
    }
}
