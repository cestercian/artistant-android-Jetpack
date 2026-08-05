package `in`.artistant.app.feature.booking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.pressScale
import `in`.artistant.app.designsystem.theme.AppTheme
import `in`.artistant.app.designsystem.theme.MotionSpecs
import `in`.artistant.app.designsystem.theme.motion
import `in`.artistant.app.designsystem.theme.reduceMotion

/**
 * A floating circular control — the flat read of the glass discs the funnel and
 * the artist hero both use for back / message / save.
 *
 * Two frames on purpose: the visible disc is [Size.heroControl] and the hit
 * target is [Size.rowMin] around it, so a control can sit tight against a screen
 * edge without dropping under the touch-target floor. Fill and rim default to
 * transparent, so the same component covers both the chrome-free case (a plain
 * icon on `bg`) and the over-media case (a translucent disc that lets the photo
 * through instead of stamping an opaque sticker on it).
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = AppTheme.colors.ink,
    fill: Color = AppTheme.colors.glass,
    rim: Color = AppTheme.colors.glassLine,
    enabled: Boolean = true,
) {
    val dimens = AppTheme.dimens
    Box(
        modifier
            .size(dimens.size.rowMin)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(dimens.size.heroControl)
                .clip(CircleShape)
                .background(fill)
                .border(dimens.size.hairline, rim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(dimens.size.iconLg),
            )
        }
    }
}

/**
 * The shared booking-funnel top bar — a floating circular back control with the
 * title CENTRED across the full width, matching the platform inline nav bar the
 * iOS funnel uses.
 *
 * The title is centred against the BAR, not against the space left over beside
 * the button, which is why it is a `Box` overlay rather than a `Row` child: in a
 * Row the title's centre shifts by half a button and the header visibly
 * un-centres itself. Reused across Book / Checkout / Request-a-quote so their
 * chrome can't drift.
 */
@Composable
fun FunnelHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = space.md, vertical = space.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = AppTheme.type.headline,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            // Keep clear of the back control on both sides so a long artist
            // name ellipsises rather than sliding under the button.
            modifier = Modifier.padding(horizontal = AppTheme.dimens.size.rowMin),
        )
        CircleIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        )
    }
}

/**
 * Bottom CTA scrim (iOS `CTAScrim`) — a top hairline over the page background so
 * the pinned action reads as its own surface above the scrolling content. Opaque
 * rather than elevated: an elevated slab announces itself, and the hairline says
 * the same thing with a pixel.
 */
@Composable
fun CtaBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = AppTheme.colors
    Column(modifier.fillMaxWidth()) {
        HRule()
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(AppTheme.dimens.space.lg),
        ) { content() }
    }
}

/** Small-caps section label (iOS `sectionTitle`) shared across the funnel screens. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AppTheme.type.caption,
        color = AppTheme.colors.ink3,
        modifier = modifier,
    )
}

/**
 * The single lime signal on a screen: a brand-filled capsule with a trailing
 * arrow. A capsule, not the rounded rect `PrimaryButton` draws, because these are
 * the funnel's terminal actions and the pill silhouette is what separates "this
 * moves you forward" from every other tappable row.
 *
 * [fullWidth] false hugs the label so a dock can put a price beside it; true
 * stretches for a bar that carries nothing else.
 */
@Composable
fun FunnelCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Box(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = if (fullWidth) dimens.size.ctaTall else dimens.size.rowMin)
            .clip(CircleShape)
            .background(if (enabled) colors.brand else colors.bgSoft)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = dimens.space.lg, vertical = dimens.space.md),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = AppTheme.type.ctaLabel,
                color = if (enabled) colors.brandInk else colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                // The label already says where this goes; the arrow is a glyph,
                // not a second thing for a screen reader to announce.
                contentDescription = null,
                tint = if (enabled) colors.brandInk else colors.ink3,
                modifier = Modifier.size(dimens.size.iconMd),
            )
        }
    }
}

/**
 * "Popular" — a filled lime chip, not the outlined one the neutral [Pill] draws.
 * It has to out-read the package name it sits beside, and a soft-washed chip at
 * that size disappears into the card.
 *
 * Whether it renders at all is [PackagePricing.popularBadgeIsMeaningful]'s call,
 * not this component's: a badge every row carries distinguishes nothing.
 */
@Composable
fun PopularBadge(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val space = AppTheme.dimens.space
    Text(
        "Popular",
        style = AppTheme.type.caption,
        color = colors.brandInk,
        modifier = modifier
            .clip(CircleShape)
            .background(colors.brand)
            .padding(horizontal = space.sm, vertical = space.xs),
    )
}

/**
 * One selectable package tier — radio, name, optional Popular chip, price, and
 * the includes line beneath.
 *
 * Shared by the artist profile and the booking compose screen on purpose: they
 * render the SAME set for the same decision, and the device bugs this file's
 * neighbours fix were all two surfaces disagreeing about one package list. The
 * radio is what makes the row read as a choice rather than a listing — a bordered
 * card alone left clients unsure anything had been picked.
 */
@Composable
fun PackageOptionRow(
    pkg: ArtistPackage,
    selected: Boolean,
    showPopularBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val space = dimens.space
    val shape = RoundedCornerShape(dimens.radii.lg)
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    // Selection is a colour change on two surfaces (the rim and the wash), so
    // both cross-fade rather than switching. Switching them made picking a tier
    // read as the row being *replaced* — the eye reads an instant fill change as
    // new content, not as the same row changing state.
    val stateSpec = tween<Color>(
        durationMillis = MotionSpecs.durationMillis(motion.indicator, reduceMotion),
        easing = motion.standard,
    )
    val fill by animateColorAsState(
        targetValue = if (selected) colors.brandSoft else Color.Transparent,
        animationSpec = stateSpec,
        label = "packageFill",
    )
    val rim by animateColorAsState(
        targetValue = if (selected) colors.brand else colors.lineSoft,
        animationSpec = stateSpec,
        label = "packageRim",
    )
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .background(fill)
            .border(dimens.size.hairline, rim, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(space.lg),
        horizontalArrangement = Arrangement.spacedBy(space.md),
    ) {
        Box(
            Modifier
                .size(dimens.size.radio)
                .border(dimens.size.stroke, rim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // The core scales in from nothing, so the radio fills rather than
            // blinking — the one moment of motion that confirms the tap landed
            // on THIS row and not the one above it.
            val core by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = tween(
                    durationMillis = MotionSpecs.durationMillis(motion.indicator, reduceMotion),
                    easing = motion.emphasizedDecelerate,
                ),
                label = "radioCore",
            )
            Box(
                Modifier
                    .size(dimens.size.radioCore)
                    .graphicsLayer { scaleX = core; scaleY = core; alpha = core }
                    .clip(CircleShape)
                    .background(colors.brand),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(space.xs)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Name + badge claim the row minus the price, so the price is
                // always hard right and the badge always sits with the name it
                // qualifies. Inside, only the name is weighted (`fill = false`):
                // it takes what's left after the badge but never stretches past
                // its own text, so a short name doesn't drag the badge away.
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        pkg.name,
                        style = AppTheme.type.body,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (showPopularBadge) PopularBadge()
                }
                Text(formatInr(pkg.price), style = AppTheme.type.monoPrice, color = colors.ink)
            }
            val includes = packageIncludesLine(pkg)
            if (includes.isNotBlank()) {
                Text(includes, style = AppTheme.type.footnote, color = colors.ink3)
            }
        }
    }
}

/**
 * "60 min · Live band · Sound check" — duration first, then what the tier
 * includes. Blank parts are dropped rather than joined, so a package with no
 * `includes` reads as its duration instead of a duration with a dangling
 * separator.
 */
internal fun packageIncludesLine(pkg: ArtistPackage): String =
    (listOf(pkg.duration) + pkg.includes)
        .filter { it.isNotBlank() }
        .joinToString(" · ")

/**
 * Free / Busy key for the date strip. Present whenever the strip is, because the
 * dots are the strip's only availability signal and an unexplained colour is not
 * a signal.
 */
@Composable
fun AvailabilityLegend(modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = "Green dot means free, grey means busy"
        },
        horizontalArrangement = Arrangement.spacedBy(dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendDot(colors.good, "Free")
        LegendDot(colors.ink4, "Busy")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    val dimens = AppTheme.dimens
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = dimens.space.sm),
    ) {
        Box(
            Modifier
                .size(dimens.size.dot)
                .clip(CircleShape)
                .background(color),
        )
        Text(label, style = AppTheme.type.footnote, color = AppTheme.colors.ink3)
    }
}
