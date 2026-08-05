package `in`.artistant.app.feature.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.Artist
import `in`.artistant.app.data.model.Review
import `in`.artistant.app.designsystem.component.EmptyState
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.ScoreRing
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.domain.artist.PackagePricing
import `in`.artistant.app.domain.score.ScoreBands
import `in`.artistant.app.domain.score.ScoreTier
import `in`.artistant.app.domain.score.tierColor
import `in`.artistant.app.feature.booking.CircleIconButton
import `in`.artistant.app.feature.booking.FunnelCta
import `in`.artistant.app.feature.booking.PackageOptionRow
import `in`.artistant.app.feature.score.ScoreBreakdownSheet
import java.util.Locale

/**
 * Artist profile.
 *
 * The page is a hero and then a column of editorial blocks: a full-bleed cover
 * carrying the whole identity (chips, name, genre, Bookability Score), a
 * hairline stat strip, About, Booking, Packages, Reviews, and a pinned action
 * dock. Nothing is a card except the things the client picks between.
 *
 * The hero is 48% of the screen and the identity sits ON it rather than in a
 * header below it, because on a two-sided marketplace the photo is the pitch —
 * the earlier layout led with a 360dp band of cover and then restated the name,
 * category and city underneath in chrome, which spent the top third of the page
 * saying one thing twice.
 *
 * Action placement mirrors iOS rather than stacking every CTA in the bar: the
 * dock carries the "from" price plus the one primary action, Message rides in
 * the hero cluster, and Request a quote sits with the pricing it negotiates.
 */
@Composable
fun ArtistProfileScreen(
    onBack: () -> Unit,
    onBook: (artistId: String) -> Unit = {},
    onRequestQuote: (artistId: String) -> Unit = {},
    onMessage: (artistId: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ArtistProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val space = AppTheme.dimens.space
    val colors = AppTheme.colors

    when {
        state.isLoading && state.artist == null -> {
            Box(modifier.fillMaxSize().background(colors.bg), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.brand)
            }
        }
        state.artist == null -> {
            Column(modifier.fillMaxSize().background(colors.bg)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink)
                }
                EmptyState(
                    title = "Artist not found",
                    body = state.loadError,
                    actionLabel = "Retry",
                    onAction = viewModel::refresh,
                )
            }
        }
        else -> {
            val artist = state.artist!!
            val tier = ScoreBands.tier(artist.score, artist.gigs)
            Column(modifier.fillMaxSize().background(colors.bg)) {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Hero(
                        artist = artist,
                        tier = tier,
                        onBack = onBack,
                        isSaved = state.isSaved,
                        onToggleSaved = viewModel::toggleSaved,
                        onMessage = { onMessage(artist.id) },
                        onScoreClick = viewModel::openScoreSheet,
                    )
                    StatStrip(reviews = state.reviews, tier = tier)
                    Column(
                        Modifier.padding(horizontal = space.lg, vertical = space.xl),
                        verticalArrangement = Arrangement.spacedBy(space.xl),
                    ) {
                        if (artist.bio.isNotBlank()) {
                            AboutBlock(bio = artist.bio)
                        }
                        BookingBlock(artist = artist)
                        PackagesBlock(
                            artist = artist,
                            selectedIndex = state.selectedPackageIndex,
                            onSelect = viewModel::selectPackage,
                            onRequestQuote = { onRequestQuote(artist.id) },
                        )
                        ReviewsBlock(reviews = state.reviews)
                        Spacer(Modifier.height(space.md))
                    }
                }
                // "from" means MINIMUM, so it is computed over the packages we
                // actually loaded — not the selected row (which made the page
                // advertise the dearest tier) and not `artist.price`, the
                // server's denormalized `artists.min_price`, which is confirmed
                // stale on dev (a row reading ₹51,000 while a ₹22,000 package
                // exists). `artist.price` survives only as the empty-set
                // fallback. iOS does the same thing via `cheapestPackage`.
                val fromPrice = PackagePricing.fromPrice(artist.packages, fallback = artist.price)
                ActionDock(
                    fromPrice = fromPrice,
                    // Hand the tapped tier over BEFORE navigating — the route
                    // carries only the artist id, so the booking screen reads the
                    // selection from the shared draft store on the way in.
                    onBook = {
                        viewModel.startBooking()
                        onBook(artist.id)
                    },
                )
            }
            if (state.showScoreSheet) {
                ScoreBreakdownSheet(
                    artist = artist,
                    breakdown = state.scoreBreakdown,
                    reviews = state.reviews,
                    reviewsFailed = state.reviewsFailed,
                    onDismiss = viewModel::dismissScoreSheet,
                )
            }
        }
    }
}

/**
 * Full-bleed cover with the identity resting on its bottom edge.
 *
 * The dissolve at the bottom is a gradient ending on the PAGE background, not on
 * black. Ramping to black bottoms out darker than `bg` and leaves a visible step
 * exactly where the seam is meant to vanish; ending on `bg` makes the join exact
 * by definition.
 *
 * Height is a fraction of the screen rather than a fixed Dp — the proportion is
 * the design, and a 360dp band is a different picture on every device.
 */
@Composable
private fun Hero(
    artist: Artist,
    tier: ScoreTier,
    onBack: () -> Unit,
    isSaved: Boolean,
    onToggleSaved: () -> Unit,
    onMessage: () -> Unit,
    onScoreClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val heroHeight = LocalConfiguration.current.screenHeightDp.dp * dimens.fraction.artistHero

    Box(
        Modifier
            .fillMaxWidth()
            .height(heroHeight),
    ) {
        // Gradient floor first so a slow/failed cover load never shows a hole.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(artist.gradient)),
        )
        if (!artist.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = artist.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        1f - dimens.fraction.heroFade to Color.Transparent,
                        1f to colors.bg,
                    ),
                ),
        )

        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = space.lg, vertical = space.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                tint = Color.White,
            )
            // Message + Save share the hero's trailing cluster. Message lives
            // here (not in the dock) so the bottom bar stays one row and the
            // primary CTA never competes for width with a secondary.
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                CircleIconButton(
                    icon = Icons.Filled.Chat,
                    contentDescription = "Message ${artist.name}",
                    onClick = onMessage,
                    tint = Color.White,
                )
                CircleIconButton(
                    icon = if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isSaved) "Unsave" else "Save",
                    onClick = onToggleSaved,
                    tint = if (isSaved) colors.brand else Color.White,
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = space.lg)
                .padding(bottom = space.xl),
            verticalArrangement = Arrangement.spacedBy(space.sm),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                MediaChip(artist.category)
                MediaChip(artist.city, icon = Icons.Filled.LocationOn)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(space.xs)) {
                    Text(
                        artist.name,
                        style = AppTheme.type.profileHeroName,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(artist.genre, style = AppTheme.type.callout, color = colors.inkOnMedia)
                }
                // The score trails the name so it reads as PART of the identity
                // ("Test Artist — 82 Trusted") rather than a stat exiled to a
                // strip below. It is the only number on the first screen.
                ScoreChip(
                    score = artist.score,
                    gigs = artist.gigs,
                    tier = tier,
                    onClick = onScoreClick,
                )
            }
        }
    }
}

/**
 * A quiet chip resting on the cover. Translucent white rather than a surface
 * token: an opaque fill would punch a hole in the photo, and these are captions
 * on the image, not controls over it.
 */
@Composable
private fun MediaChip(text: String, icon: ImageVector? = null) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.chipOnMedia)
            .border(dimens.size.hairline, colors.chipOnMediaLine, CircleShape)
            .padding(horizontal = dimens.space.md, vertical = dimens.space.xs),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                // The chip's own text names the place; the pin is decoration.
                contentDescription = null,
                tint = colors.ink2,
                modifier = Modifier.size(dimens.size.iconSm),
            )
        }
        Text(text, style = AppTheme.type.caption, color = colors.ink2)
    }
}

/**
 * The Bookability Score, as a tappable capsule on the hero: the ring, the tier
 * word, and a disclosure into the breakdown.
 *
 * It renders straight off the artist row's cached score, so the hero never
 * paints without it — the breakdown fetch only feeds the sheet. A New-tier
 * artist gets the ring's "NEW" state (an honest "no data yet"), and the label
 * names the metric instead of echoing "NEW" twice.
 *
 * **Contrast is the constraint here, not decoration.** This capsule is the only
 * place small text sits directly on an artist-supplied photo, and it has to hold
 * for every tier over every cover. Two things make that true: a darkening
 * backdrop (a lightening wash leaves the result at the mercy of the image — over
 * this hero's mid-tone gradient it landed on the *same luminance* as the muted
 * New-tier ink, rendering the label at 1.2:1 against its own fill), and, for the
 * New state, an on-media ink instead of the tier map's `ink4`. That grey is
 * correct on the `bg` ladder and invisible on a photo. Nothing here overrides a
 * SCORED tier's colour: those are the signal, and they are bright enough to
 * carry themselves against the dark backdrop.
 */
@Composable
private fun ScoreChip(score: Int, gigs: Int, tier: ScoreTier, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val isNew = tier == ScoreTier.New
    Row(
        Modifier
            .clip(CircleShape)
            .background(colors.glassDark)
            .border(dimens.size.hairline, colors.glassLine, CircleShape)
            .clickable(onClick = onClick)
            .padding(start = dimens.space.sm, end = dimens.space.md, top = dimens.space.xs, bottom = dimens.space.xs)
            .semantics(mergeDescendants = true) {
                contentDescription = if (isNew) {
                    "Bookability score: New, score not established yet. Tap for details."
                } else {
                    "Bookability score: $score out of 100, ${tier.label} tier. Tap for details."
                }
            },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ScoreRing(
            value = if (isNew) null else score,
            size = dimens.size.ringXs,
            stroke = dimens.size.ringXsStroke,
            showLabel = false,
            totalGigs = gigs,
            mutedTint = colors.inkOnMedia,
        )
        Column {
            Text(
                if (isNew) "BOOKABILITY" else tier.label.uppercase(Locale.US),
                style = AppTheme.type.monoMicro,
                // For a scored tier this word IS the tier, so it carries the tier
                // colour. For New it reads "BOOKABILITY" — the name of the
                // metric, not a tier — so tinting it with the tier map's grey was
                // a category error on top of a contrast failure.
                color = if (isNew) colors.inkOnMedia else tierColor(tier, colors),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Details", style = AppTheme.type.monoMicroSoft, color = colors.inkOnMediaSoft)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.inkOnMediaSoft,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
            }
        }
    }
}

/**
 * Rating · tier · review count, ruled above and below.
 *
 * Rendered ONLY when the artist has reviews: with none, the sole cell backed by
 * data is the tier — which the hero chip already states — and a lone redundant
 * cell is worse than no strip. Rating and count are computed from the same list
 * the Reviews block renders, so the two surfaces cannot disagree.
 */
@Composable
private fun StatStrip(reviews: List<Review>, tier: ScoreTier) {
    if (reviews.isEmpty()) return
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val average = reviews.map { it.rating }.average()

    Column(
        Modifier.padding(horizontal = space.lg, vertical = space.lg),
    ) {
        HRule()
        // IntrinsicSize.Min so the hairline dividers can measure themselves
        // against the tallest cell instead of collapsing to zero height.
        Row(Modifier.height(IntrinsicSize.Min)) {
            StatCell(
                modifier = Modifier.weight(1f),
                value = "%.1f".format(Locale.US, average),
                label = "Rating",
                valueColor = colors.ink,
                star = true,
            )
            StatDivider()
            // The tier is a WORD, so it opts out of the mono-numerals rule; its
            // colour is the signal (elite / trusted / rising / new).
            StatCell(
                modifier = Modifier.weight(1f),
                value = tier.label,
                label = "Tier",
                valueColor = tierColor(tier, colors),
                mono = false,
            )
            StatDivider()
            StatCell(
                modifier = Modifier.weight(1f),
                value = "${reviews.size}",
                label = if (reviews.size == 1) "Review" else "Reviews",
                valueColor = colors.ink,
            )
        }
        HRule()
    }
}

@Composable
private fun StatDivider() {
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .fillMaxHeight()
            .padding(vertical = dimens.space.md)
            .width(dimens.size.hairline)
            .background(AppTheme.colors.lineSoft),
    )
}

@Composable
private fun StatCell(
    value: String,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    mono: Boolean = true,
    star: Boolean = false,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(
        modifier
            .padding(vertical = dimens.space.lg)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dimens.space.xs),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (star) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = colors.brand,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
            }
            Text(
                value,
                style = if (mono) AppTheme.type.monoStat else AppTheme.type.headline,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(label.uppercase(Locale.US), style = AppTheme.type.caption, color = colors.ink3)
    }
}

@Composable
private fun AboutBlock(bio: String) {
    val colors = AppTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.lg)) {
        Text("About", style = AppTheme.type.displaySmall, color = colors.ink)
        Text(bio, style = AppTheme.type.body, color = colors.ink2)
    }
}

/**
 * The "from" figure as an editorial line: a big mono number with the tier it
 * belongs to trailing it on the same baseline. It restates the dock's price
 * deliberately — the dock is a control the eye skips, this is the page saying
 * what booking this artist costs.
 *
 * With no published packages there is no minimum, so the figure is omitted
 * rather than back-filled from the artist row's own price. That value is the
 * server's denormalized `min_price`, which is confirmed stale on dev — the dock
 * keeps it as a last-resort fallback because a control with no number at all is
 * worse, but this line is the page *stating* a price, and stating a known-stale
 * one as a headline is the exact bug the "from means minimum" fix was about.
 * "Pricing on request" is true, and the quote row below acts on it.
 */
@Composable
private fun BookingBlock(artist: Artist) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    val cheapest = artist.packages.minByOrNull { it.price }
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text("Booking", style = AppTheme.type.displaySmall, color = colors.ink)
        if (cheapest != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                Text(
                    formatInr(cheapest.price),
                    style = AppTheme.type.monoHero,
                    color = colors.ink,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    "from · ${cheapest.name}",
                    style = AppTheme.type.footnote,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        } else {
            Text(
                "Pricing on request",
                style = AppTheme.type.body,
                color = colors.ink2,
            )
        }
        // Quiet, deliberately NOT lime: it is a note, and the neighbouring
        // "from · package" caption sets the register. Absent/0 hides the line.
        if (artist.newArtistDiscountPct > 0) {
            Text(
                "New-artist offer: ${artist.newArtistDiscountPct}% off your booking",
                style = AppTheme.type.footnote,
                color = colors.ink2,
            )
        }
    }
}

/**
 * The tiers, and under them the way out of the tiers.
 *
 * The heading and rows are conditional; **the quote row is not**. An artist with
 * no published packages is an ordinary state — the wizard publishes one, but an
 * artist can have none, and every artist has none until they finish the wizard.
 * Guarding the whole block on a non-empty list took the negotiation entry down
 * with it, so the client left with the least to go on got the fewest ways to
 * start a conversation. The quote row is what that client needs MOST.
 */
@Composable
private fun PackagesBlock(
    artist: Artist,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onRequestQuote: () -> Unit,
) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        if (artist.packages.isNotEmpty()) {
            Text("Packages", style = AppTheme.type.displaySmall, color = colors.ink)
            // Asked once per set, not per row: existing server rows carry
            // `popular = true` on every package, and a badge every row shares
            // distinguishes nothing.
            val badgesMeanSomething = PackagePricing.popularBadgeIsMeaningful(artist.packages)
            artist.packages.forEachIndexed { index, pkg ->
                PackageOptionRow(
                    pkg = pkg,
                    selected = index == selectedIndex,
                    showPopularBadge = badgesMeanSomething && pkg.popular,
                    onClick = { onSelect(index) },
                )
            }
        }
        // The negotiation entry sits HERE, with the pricing context, rather than
        // in the dock — collapsing the bar to one row is the point of the layout.
        RequestQuoteRow(onClick = onRequestQuote)
    }
}

/**
 * The negotiation entry. Muted question + lime answer: lime is the signal (this
 * is the actionable half), the grey lead-in is the framing.
 */
@Composable
private fun RequestQuoteRow(onClick: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = space.sm),
        horizontalArrangement = Arrangement.spacedBy(space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Custom date or budget?", style = AppTheme.type.footnote, color = colors.ink3)
        Text("Request a quote", style = AppTheme.type.footnote, color = colors.brand)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(AppTheme.dimens.size.iconMd),
        )
    }
}

@Composable
private fun ReviewsBlock(reviews: List<Review>) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(verticalArrangement = Arrangement.spacedBy(space.md)) {
        Text("Reviews", style = AppTheme.type.displaySmall, color = colors.ink)
        if (reviews.isEmpty()) {
            Text("No reviews yet.", style = AppTheme.type.body, color = colors.ink3)
            return@Column
        }
        reviews.take(5).forEach { review ->
            Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(space.sm)) {
                    Text(review.name, style = AppTheme.type.callout, color = colors.ink)
                    Text(
                        "★".repeat(review.rating.coerceIn(0, 5)),
                        style = AppTheme.type.caption,
                        color = colors.brand,
                    )
                }
                if (review.org.isNotBlank()) {
                    Text(review.org, style = AppTheme.type.caption, color = colors.ink3)
                }
                Text(
                    review.body,
                    style = AppTheme.type.body,
                    color = colors.ink2,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Compact bottom action bar.
 *
 * One row: the "from" figure hard-left (informational, deliberately outside the
 * tap target), the filled primary CTA hard-right at its intrinsic width, and the
 * gap between them left as air. It replaces a ~4x taller slab that stacked the
 * price over three full-width buttons and ate roughly a fifth of the screen; the
 * two secondaries moved to where their context is (Request a quote sits with the
 * packages, Message sits in the hero cluster).
 *
 * Nothing is hidden behind it: the dock is a sibling of the scrolling column in
 * the parent `Column`, and that column takes `weight(1f)`, so the scroll viewport
 * is measured as (screen − dock) and the content is inset rather than occluded.
 * The page also carries trailing air so the last row never sits flush against
 * the lid.
 *
 * `FlowRow`, not `Row`, because the bar has to survive accessibility text scales.
 * A `Row` measures its non-weighted children first, so the intrinsic-width CTA
 * claimed the whole bar before a weighted price column was measured: at 18sp the
 * label needs ~213dp of a 360dp phone's 328dp row, and by fontScale 1.5 it needs
 * ~296dp, leaving the price ~32dp to render ₹51,000 in — squeezed, then clipped.
 * FlowRow measures both at their intrinsic width and moves the CTA onto its own
 * line when they no longer share one, so neither is ever truncated and the CTA
 * keeps its tap target. No breakpoint constant is involved — the wrap is decided
 * by what actually fits, which is also how the chip grids in the funnel handle
 * the same problem.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionDock(fromPrice: Int, onBook: () -> Unit) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Column(Modifier.fillMaxWidth().background(colors.bg)) {
        // Hairline lid — honest about where the bar begins, instead of a slab of
        // elevated fill doing the same job with 10x the ink.
        HRule()
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = space.lg, vertical = space.md),
            // The gap IS the design while both fit on one line; once the CTA
            // wraps, SpaceBetween leaves each line start-aligned, which is what
            // a stacked dock should look like anyway.
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(space.md),
        ) {
            Column(
                // No weight: a weighted child is measured with whatever the CTA
                // left over, which is exactly how the price got squeezed.
                // Intrinsic width + SpaceBetween gives the same look and lets
                // FlowRow see the real width when it decides to wrap.
                modifier = Modifier
                    // Per-item alignment: this Compose version has no
                    // `itemVerticalAlignment` on FlowRow, so each child centres
                    // itself against the taller one on the shared line.
                    .align(Alignment.CenterVertically)
                    // Read as one phrase; the two stacked lines are typography,
                    // not two separate facts for a screen reader to announce.
                    .semantics(mergeDescendants = true) {
                        contentDescription = "From ${formatInr(fromPrice)}"
                    },
            ) {
                Text(formatInr(fromPrice), style = AppTheme.type.monoDock, color = colors.ink)
                Text("FROM", style = AppTheme.type.monoMicro, color = colors.ink3)
            }
            // Intrinsic width (no fullWidth) so the CTA hugs its label and the
            // space between the two is air rather than button.
            FunnelCta(
                text = "Check availability",
                onClick = onBook,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}
