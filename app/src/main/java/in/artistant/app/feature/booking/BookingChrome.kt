package `in`.artistant.app.feature.booking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import `in`.artistant.app.common.util.formatInr
import `in`.artistant.app.data.model.ArtistPackage
import `in`.artistant.app.designsystem.component.HRule
import `in`.artistant.app.designsystem.component.IconCircle
import `in`.artistant.app.designsystem.component.TrustedTick
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
 * Bottom CTA scrim (iOS `CTAScrim`) — a top hairline over an opaque surface so
 * the pinned action reads as its own plane above the scrolling content. Opaque
 * rather than elevated: an elevated slab announces itself, and the hairline says
 * the same thing with a pixel.
 *
 * The light design draws it on `surface`, not on the page: the funnel screens are
 * white pages, and a bar tinted `page` under a white scroll shows up as a band of
 * a slightly different white, which reads as a rendering fault.
 *
 * Padding is the design's own — gutter each side, 16 above, 30 below — and the
 * navigation-bar inset is added on top rather than substituted for it, because a
 * gesture-nav phone reports 0–24dp there and the design's tailroom is not a
 * system inset, it is air.
 */
@Composable
fun CtaBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Column(modifier.fillMaxWidth()) {
        HRule()
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(
                    start = dimens.component.gutter,
                    end = dimens.component.gutter,
                    top = dimens.space.lg,
                )
                .padding(bottom = dimens.size.ctaBarTailroom)
                .padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            content = content,
        )
    }
}

/**
 * The caption under a pinned CTA — "Nothing is charged until you accept a quote",
 * "We recommend asking at least 3 artists".
 *
 * Part of the bar rather than free text at each call site because it is always
 * the same thing: one centred `ink4` line, 12sp, sitting the same distance under
 * the button on every screen that has one.
 */
@Composable
fun CtaCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.footnote,
        color = AppTheme.colors.ink4,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppTheme.dimens.space.md),
    )
}

/**
 * The funnel's pushed-screen header: a 40dp `surface2` circle, then a LEFT-aligned
 * title with an optional subtitle under it, then a reserved trailing slot.
 *
 * Left-aligned, unlike [FunnelHeader] and the library's `BackHeader`, because
 * these three screens (17, 06, 132) carry a second line that is doing work —
 * "Saanjh usually replies in 2 hours", "Nothing is charged — v1 takes no
 * payment", "#AR-40712 · 12 Oct 2026". Centring a two-line block against a
 * single back circle leaves the subtitle visually adrift; the design sets the
 * pair flush left against the circle and reserves the same width on the right so
 * a trailing action does not shove the title off-centre-of-itself.
 *
 * [leadingIcon] is the one thing that varies: a back arrow when the screen was
 * pushed onto something, a cross when it is a modal step of a flow.
 */
@Composable
fun FunnelBar(
    title: String,
    onLeading: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    leadingLabel: String = "Back",
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The design's own 10px above the bar. Without it a two-line
            // subtitle pushes the title up against the status bar, which reads
            // as the page having been cropped.
            .padding(horizontal = dimens.component.gutter, vertical = dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
    ) {
        IconCircle(
            icon = leadingIcon,
            contentDescription = leadingLabel,
            onClick = onLeading,
            size = dimens.component.iconCircleSm,
        )
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppTheme.type.sectionTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    // Two lines, not one: "Nothing is charged — v1 takes no
                    // payment" wraps on a narrow phone, and eliding the half that
                    // says "no payment" would leave the opposite promise on screen.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = dimens.space.xs / 2),
                )
            }
        }
        // The circle's mirror, so a title with no trailing control still starts
        // where a title with one does.
        if (trailing != null) trailing() else Spacer(Modifier.width(dimens.size.controlMin))
    }
}

/**
 * A flow step's header: a close circle, a centred "Step 1 of 2", nothing else
 * (screen 05).
 *
 * The step count is the title here — the screen's real headline is the 26sp
 * question underneath it, which is part of the scroll rather than part of the
 * chrome. That is what makes the funnel read as a form you are partway through
 * instead of as a page called "Book".
 */
@Composable
fun FunnelStepBar(
    step: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    closeLabel: String = "Close",
) {
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.component.gutter, vertical = dimens.space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(
            icon = Icons.Filled.Close,
            contentDescription = closeLabel,
            onClick = onClose,
            size = dimens.component.iconCircleSm,
        )
        Text(
            step,
            style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.colors.ink,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = dimens.space.sm),
        )
        Spacer(Modifier.width(dimens.size.controlMin))
    }
}

/*
 * The funnel's recurring accent-washed note (screens 06, 61, 94, 132) is
 * `designsystem/component/AccentNote` — one component, not two.
 *
 * This file grew its own `NoteBlock` while the artist-profile section grew
 * `AccentNote` in the shared library, both against the same markup and, as it
 * turned out, the same measured alphas. The library one wins on the redesign's
 * own rule: a block used by more than one section belongs there. Two banner
 * components that drift apart is exactly the failure the Banner KDoc warns
 * about, and it is cheaper to delete the second one at the merge than after it
 * has been styled twice.
 */

/**
 * One line of a decided term: a quiet label left, the value hard right.
 *
 * The same anatomy on the confirm screen, the confirmation, the match landing
 * and the invoice — which is the point. A request has to read identically before
 * it is sent, after it is accepted and on the record of it; four screens drawing
 * their own two-column row is how those three drift apart.
 */
@Composable
fun TermRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasis: Boolean = false,
) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.space.md),
    ) {
        Text(
            label,
            style = if (emphasis) {
                AppTheme.type.sectionTitle
            } else {
                AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Normal)
            },
            color = if (emphasis) colors.ink else colors.ink2,
        )
        Text(
            value,
            style = if (emphasis) AppTheme.type.sectionTitle else AppTheme.type.rowTitle,
            color = colors.ink,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A solid lime capsule stating a fact — "Confirmed", "Settled".
 *
 * `Pill(BrandSolid)` is the same fill but sets its label in `caption` (12.5
 * regular); the design's badge is 11.5 BOLD and tracked, which is what stops a
 * one-word capsule reading as a tag someone typed. That is [AppType.badge]'s
 * whole job.
 */
@Composable
fun AccentBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = AppTheme.type.badge,
        color = AppTheme.colors.onAccent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(CircleShape)
            .background(AppTheme.colors.accent)
            .padding(
                horizontal = AppTheme.dimens.space.md,
                vertical = AppTheme.dimens.space.xs + AppTheme.dimens.space.xs / 2,
            ),
    )
}

/**
 * The lime disc with a tick in it that opens screens 07 and 94.
 *
 * It springs in from [MARK_ENTRY_SCALE] on appear — the reference build's
 * `.spring(duration: 0.6)` — because the pop is what makes the outcome land.
 * Reduce-motion keeps the state and drops the travel (`snap()`), the same branch
 * `DateCell` takes.
 *
 * Decorative to a screen reader: the headline under it says the same thing in
 * words, and "tick" announced before "You've got a band on Saturday" is noise.
 */
@Composable
fun OutcomeMark(modifier: Modifier = Modifier) {
    val dimens = AppTheme.dimens
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else MARK_ENTRY_SCALE,
        animationSpec = if (AppTheme.reduceMotion) snap() else spring(),
        label = "outcomeMark",
    )
    Box(
        modifier
            .size(dimens.funnel.outcomeDisc)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(AppTheme.colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = AppTheme.colors.onAccent,
            modifier = Modifier.size(dimens.funnel.outcomeGlyph),
        )
    }
}

/** Where the outcome disc starts before it springs to full size. */
private const val MARK_ENTRY_SCALE = 0.6f

/**
 * A grouped `surface3` card — the act header, the terms block, the invoice's
 * billed-to. One inset, one radius, everywhere.
 */
@Composable
fun FunnelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val dimens = AppTheme.dimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radii.card))
            .background(AppTheme.colors.surface3)
            .padding(dimens.funnel.cardPad),
        content = content,
    )
}

/**
 * The act, as the funnel introduces it: cover thumbnail, name (+ trusted tick),
 * and up to two lines of meta.
 *
 * The thumbnail is drawn as a BACKGROUND layer inside a fixed slot rather than as
 * a `ContentScale.Crop` child, the same containment rule Discover's tiles follow:
 * a cover that fails to load leaves a `placeholder` square, never a collapsed row.
 */
@Composable
fun ActRow(
    name: String,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    trusted: Boolean = false,
    lines: List<String> = emptyList(),
    thumbSize: Dp = AppTheme.dimens.funnel.actThumb,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(thumbSize)
                .clip(RoundedCornerShape(dimens.radii.md))
                .background(colors.placeholder),
        ) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.space.xs / 2)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.space.xs + dimens.space.xs / 4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (trusted) TrustedTick()
            }
            lines.filter { it.isNotBlank() }.forEach { line ->
                Text(
                    line,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
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
            // `selectable`, not `clickable`: the pick is carried by a rim and a
            // wash, neither of which a screen reader can see. Role.RadioButton is
            // what makes the tier list read back as one choice out of several.
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            // `optionCardPad`, not `space.lg` — see the token's own note. A
            // matched-content measurement against the reference (same tier
            // names, same one-line includes string) put our row 4 units taller
            // than its counterpart, all of it this inset.
            .padding(dimens.size.optionCardPad),
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
        // One even rhythm across the whole legend — dot, gap, word, gap, dot,
        // gap, word — which is how the reference draws it. The nested
        // dot-to-label gap used to be 4 while the word-to-next-dot gap was 16,
        // so the pairs read as "Free ●Busy" instead of "● Free   ● Busy".
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
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
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
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

/**
 * The funnel's month calendar — screen 05's "calendar is the source of truth".
 *
 * Deliberately NOT `MonthDayGrid`. That grid belongs to the artist's own
 * availability studio: it draws day TILES with a status dot under each, fills
 * booked days lime and rings today, because there the month is a dashboard of
 * what is already happening. Here the month is a picker with exactly two states
 * per day — you can ask for it, or the artist has closed it — and drawing the
 * studio's five-state tile would spend four of them on distinctions this screen
 * does not make.
 *
 * A day the artist has closed is dimmed AND inert, not merely dim: the design's
 * note is that hosts never request a dead date, and a date that looks live and
 * silently swallows a tap teaches the opposite.
 */
@Composable
fun FunnelCalendar(
    monthLabel: String,
    days: List<FunnelDay>,
    selectableDays: Set<Int>,
    selectedDay: Int?,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    canGoBack: Boolean = true,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    FunnelCard(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                monthLabel,
                style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The back chevron greys at the current month rather than
            // disappearing: a control that vanishes moves the one beside it, and
            // the pair is how the header says "this steps".
            MonthStep(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                label = "Previous month",
                enabled = canGoBack,
                onClick = onPrevMonth,
            )
            MonthStep(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                label = "Next month",
                enabled = true,
                onClick = onNextMonth,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = dimens.space.lg),
            horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
        ) {
            WEEKDAY_INITIALS.forEach { d ->
                Text(
                    d,
                    style = AppTheme.type.monoWeekday,
                    color = colors.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        days.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = dimens.space.xs),
                horizontalArrangement = Arrangement.spacedBy(dimens.space.xs),
            ) {
                week.forEach { day ->
                    CalendarDay(
                        day = day,
                        selectable = day.inMonth && day.number in selectableDays,
                        selected = day.inMonth && day.number == selectedDay,
                        onClick = { onDay(day.number) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Monday-first, matching the design's header row and [funnelMonthDays]. */
private val WEEKDAY_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")
private const val DAYS_PER_WEEK = 7

@Composable
private fun MonthStep(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val dimens = AppTheme.dimens
    Box(
        Modifier
            .size(dimens.size.rowMin)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) AppTheme.colors.ink else AppTheme.colors.lineStrong,
            modifier = Modifier.size(dimens.size.iconLg),
        )
    }
}

@Composable
private fun CalendarDay(
    day: FunnelDay,
    selectable: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val ink = when {
        selected -> colors.onAccent
        !day.inMonth -> colors.lineStrong
        selectable -> colors.ink
        // A closed day keeps its numeral — the host has to be able to read the
        // month — but drops a rung so it cannot be mistaken for an offer.
        else -> colors.ink3
    }
    Box(
        modifier
            .clip(RoundedCornerShape(dimens.radii.md))
            .background(if (selected) colors.accent else Color.Transparent)
            .selectable(selected = selected, enabled = selectable, onClick = onClick)
            .semantics {
                if (day.inMonth && !selectable) stateDescription = "Unavailable"
            }
            .padding(vertical = dimens.component.chipPadV),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.number.toString(),
            style = AppTheme.type.rowTitle.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = ink,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One package tier as screen 05 draws it: a radio that becomes a filled tick, the
 * tier name over its one-line includes, and the price hard right.
 *
 * Distinct from [PackageOptionRow] — the pre-redesign row the artist profile
 * still draws — on purpose. That one animates a growing dot inside a ring and
 * sets its price in mono; this one is the light design's, where the chosen tier
 * takes an accent wash, an accent rim and a solid tick, and the price is set in
 * the sans so it reads as a word in the row rather than as a readout.
 */
@Composable
fun PackageChoiceRow(
    name: String,
    includes: String,
    price: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.colors
    val dimens = AppTheme.dimens
    val shape = RoundedCornerShape(dimens.radii.buttonLg)
    val interaction = remember { MutableInteractionSource() }
    val motion = AppTheme.motion
    val reduceMotion = AppTheme.reduceMotion
    // Both surfaces cross-fade rather than switch: an instant fill change reads
    // as the row being REPLACED, not as the same row changing state.
    val stateSpec = tween<Color>(
        durationMillis = MotionSpecs.durationMillis(motion.indicator, reduceMotion),
        easing = motion.standard,
    )
    val fill by animateColorAsState(
        targetValue = if (selected) colors.accent.copy(alpha = SELECTED_WASH) else colors.surface3,
        animationSpec = stateSpec,
        label = "choiceFill",
    )
    val rim by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.hairline,
        animationSpec = stateSpec,
        label = "choiceRim",
    )
    Row(
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .clip(shape)
            .background(fill)
            .border(dimens.component.focusStroke, rim, shape)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(dimens.size.optionCardPad),
        horizontalArrangement = Arrangement.spacedBy(dimens.space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(dimens.size.radio)
                .clip(CircleShape)
                .background(if (selected) colors.accent else Color.Transparent)
                .border(
                    dimens.size.stroke,
                    if (selected) colors.accent else colors.lineStrong,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(dimens.size.iconSm),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimens.space.xs / 2)) {
            Text(
                name,
                style = AppTheme.type.rowTitle,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (includes.isNotBlank()) {
                Text(
                    includes,
                    style = AppTheme.type.caption,
                    color = colors.ink4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            formatInr(price),
            style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
            color = colors.ink,
            maxLines = 1,
        )
    }
}

/** The accent's alpha behind the chosen tier. */
private const val SELECTED_WASH = 0.26f
