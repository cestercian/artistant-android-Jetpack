package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4/8/12/16/24/32 spacing scale (xs…xxl). */
data class Space(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** 8/12/18/24/32 corner radii. */
data class Radii(
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 18.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** Icon/control/avatar/ring/hero sizes (SCREEN_INVENTORY §1). */
data class Size(
    val iconXs: Dp = 10.dp,
    val iconSm: Dp = 12.dp,
    val iconMd: Dp = 16.dp,
    val iconLg: Dp = 20.dp,
    val iconXl: Dp = 28.dp,
    val controlMin: Dp = 48.dp,   // button/input min height
    val rowMin: Dp = 44.dp,
    val avatarSm: Dp = 32.dp,
    val avatarMd: Dp = 48.dp,
    val avatarLg: Dp = 64.dp,
    val avatarXl: Dp = 96.dp,
    val ringSm: Dp = 44.dp,
    val ringMd: Dp = 64.dp,
    val ringLg: Dp = 76.dp,
    val ringXl: Dp = 120.dp,
    val heroTall: Dp = 460.dp,
    val heroMed: Dp = 360.dp,
    val heroShort: Dp = 280.dp,
    /**
     * The cover PREVIEW card in the press-kit editor — a picture of the hero, not
     * the hero itself.
     *
     * Its own value rather than a [heroShort] reuse, because the two answer to
     * different things. A hero is sized to fill the top of a screen; this is a
     * thumbnail inside a labelled section that has eight more sections under it,
     * so it is sized to leave the sections visible. Taken from the reference,
     * where the card measures 180 units between a section label and a swatch row.
     */
    val coverPreview: Dp = 180.dp,
    /**
     * One gradient swatch in the cover picker. Landscape (56×38) so a row of six
     * reads as six little covers rather than as six buttons — the shape is the
     * only thing telling the artist what they are choosing.
     */
    val swatchW: Dp = 56.dp,
    val swatchH: Dp = 38.dp,
    /** 1pt hairline borders (Decline CTA, calendar cell chrome). */
    val hairline: Dp = 1.dp,
    /** Avatar ring / badge stroke (iOS Avatar uses fixed 2pt). */
    val stroke: Dp = 2.dp,
    /**
     * Emphasis stroke — a month-grid tile that is selected or is today. Half a
     * unit above [hairline], which is all it takes for a ring to read as picked;
     * at [stroke] it stops being a ring and becomes a border, and a grid of 35
     * bordered boxes is a spreadsheet.
     */
    val strokeEmphasis: Dp = 1.5.dp,

    // ── Month grid ────────────────────────────────────────────────────────
    /**
     * A day tile in the month grid. Fixed height rather than a square aspect
     * ratio: the tile stacks a numeral over a status dot, and a square cell on a
     * 7-column grid is ~48 wide on a phone — tall enough for the numeral alone,
     * not for the numeral plus the dot plus the air between them.
     */
    val gridCellH: Dp = 52.dp,
    /** The status dot under a day numeral (open / unavailable). */
    val gridDot: Dp = 5.dp,
    /** ArtistList row thumbnail (iOS ArtistRow thumb 62×78). */
    val listThumbW: Dp = 62.dp,
    val listThumbH: Dp = 78.dp,
    /**
     * Tailroom under the last row of a settings list, so the final entry — which
     * is "Delete account", the one row nobody should have to fight the floating
     * tab bar to read — clears the chrome and has somewhere to scroll to.
     * Smaller than [Hero.scrollTailroom]: that one buys a horizontal rail room
     * to overshoot, this one just buys a hairline some air.
     */
    val listTailroom: Dp = 56.dp,

    // ── Artist hero / booking funnel ──────────────────────────────────────
    /**
     * Floating hero control: a 40dp disc inside a 44dp tap target. The disc is
     * the visible circle and the extra 4dp is hit slop, so the buttons can sit
     * close to the screen edge without shrinking below the touch-target floor.
     */
    val heroControl: Dp = 40.dp,
    /** The mini score ring that rides in the hero identity row, and its stroke. */
    val ringXs: Dp = 34.dp,
    val ringXsStroke: Dp = 3.dp,
    /**
     * Date card. Portrait on purpose (56×76): the weekday, the day numeral and
     * the availability dot stack, which is what makes a run of them scan as a
     * calendar strip rather than a row of buttons.
     */
    val dateCellW: Dp = 56.dp,
    val dateCellH: Dp = 76.dp,
    /** Availability dot — on a date card and in its Free/Busy legend. */
    val dot: Dp = 6.dp,
    /** Package-row radio: 20dp ring, 10dp filled core. */
    val radio: Dp = 20.dp,
    val radioCore: Dp = 10.dp,
    /** Full-width pinned CTA (taller than `controlMin` — it is the only action). */
    val ctaTall: Dp = 52.dp,
    /**
     * Air under a pinned CTA bar, on top of whatever the gesture bar claims.
     *
     * Deliberately off the [Space] ramp, which is why it lives here: the funnel's
     * bottom bar is the one place the app wants MORE clearance than `xl` and less
     * than `xxl`, so that the terminal action of a paid flow is never a thumb's
     * width from the system gesture area. Measured off the reference build, where
     * the same bar reserves this much above its home-indicator inset.
     */
    val ctaBarTailroom: Dp = 28.dp,
    /**
     * Width kept clear at each end of a centred bar title, so it can never run
     * under the controls flanking it.
     *
     * Wider than [rowMin] because the widest of those controls is a labelled
     * pill, not an icon button, and the reservation has to be symmetric — an
     * asymmetric one centres the title against the bar but not against the eye.
     */
    val barTitleReserve: Dp = 76.dp,
)

/**
 * Sizes expressed as a share of the viewport rather than a fixed Dp.
 *
 * Kept apart from [Size] because they are unitless: a hero measured in Dp is a
 * different fraction of a 5" phone than of a foldable, and this hero's whole
 * point is the proportion it holds on screen.
 */
data class Fractions(
    /**
     * Artist-profile hero height. Tall for a detail screen, deliberately — the
     * photo IS the product on this surface.
     */
    val artistHero: Float = 0.48f,
    /**
     * Bottom share of the hero over which the media dissolves into the page.
     * The gradient ends on the page background rather than on black: ramping to
     * black would bottom out darker than `bg` and leave a visible step exactly
     * where the seam is supposed to disappear.
     */
    val heroFade: Float = 0.45f,
)

/**
 * Global navigation chrome — the floating tab bar.
 *
 * Every value here was measured off the shipped iOS build (screenshot pixel
 * measurement at 3× against a 402×874pt viewport) rather than taken from
 * Material's `NavigationBar` defaults, because the brief is iOS parity, not
 * Material convention. iOS points map 1:1 onto dp — both are density-independent
 * units at the same nominal scale — so the numbers transfer unchanged.
 */
data class Chrome(
    /** Height of the floating 4-tab pill (iOS measures 62pt). */
    val tabBarHeight: Dp = 62.dp,
    /** Inset from the screen's left/right/bottom edges to the bar. */
    val tabBarInset: Dp = 21.dp,
    /** Breathing room reserved ABOVE the bar so content doesn't butt into it. */
    val tabBarTopGap: Dp = 12.dp,
    /** Gap between the tab pill and the detached search circle. */
    val tabBarGap: Dp = 8.dp,
    /** Diameter of the detached search circle (matches the pill height). */
    val tabSearchSize: Dp = 62.dp,
    /** Selected-tab capsule height inside the pill (62 − 4pt top/bottom). */
    val tabSelectionHeight: Dp = 54.dp,
    /** Tab glyph box. */
    val tabIconSize: Dp = 24.dp,
    /** Gap between a tab's glyph and its label. */
    val tabIconLabelGap: Dp = 3.dp,
    /** Search-circle glyph box (iOS magnifier measures ~23pt). */
    val tabSearchIconSize: Dp = 23.dp,
)

/**
 * Discover's immersive hero + rails. Same provenance as [Chrome]: literal iOS
 * values, ported so the two apps read identically at a glance.
 */
data class Hero(
    /** Hero occupies this fraction of the screen height (iOS: screenH * 0.74). */
    val heightFraction: Float = 0.74f,
    /** "Book →" pill height. */
    val ctaHeight: Dp = 50.dp,
    /** Circular save-heart button. */
    val saveSize: Dp = 50.dp,
    /** Masthead avatar chip diameter (ring stroke sits on top of this). */
    val avatarSize: Dp = 40.dp,
    /** "● AVAILABLE FRI" status capsule height. */
    val statusPillHeight: Dp = 30.dp,
    /** Status/score signal dot. */
    val signalDot: Dp = 7.dp,
    /** The 3pt "·" between metadata fields. */
    val separatorDot: Dp = 3.dp,
    /** Page-dot height (and width when inactive). */
    val pageDot: Dp = 6.dp,
    /** Page-dot width when active — the dot stretches into a capsule. */
    val pageDotActiveWidth: Dp = 22.dp,
    /** Gap between page dots. */
    val pageDotGap: Dp = 7.dp,
    /** Clearance under the CTA row so the page dots never collide with it. */
    val ctaBottomGap: Dp = 46.dp,
    /** Glyph↔label gap inside the status capsule and the "Book →" pill. */
    val labelGap: Dp = 6.dp,
    /** Signal-dot↔score gap in the metadata strip. */
    val scoreGap: Dp = 5.dp,
    /** Section title↔chevron gap. */
    val headerChevronGap: Dp = 5.dp,
    /** Field gap in a featured frame's metadata strip (tighter than the hero's). */
    val frameMetaGap: Dp = 7.dp,
    /** Drop shadow under a featured frame. */
    val frameShadow: Dp = 10.dp,
    /** "Featured this week" frame. */
    val frameWidth: Dp = 300.dp,
    val frameHeight: Dp = 452.dp,
    /** Rail tile (smaller than the design-system default — Discover packs more). */
    val tileWidth: Dp = 150.dp,
    val tileHeight: Dp = 200.dp,
    /** Trailing scroll tailroom so the last rail clears the floating tab bar. */
    val scrollTailroom: Dp = 130.dp,
    /** Hero auto-advance interval. */
    val autoAdvanceMillis: Long = 6_000L,
)

/**
 * The artist dashboard's charts, strip and banners.
 *
 * Kept as its own group rather than folded into [Size] because these are the
 * measurements of a *data display* — a chart's drawing box, a meter's thickness,
 * a day cell — and they answer to legibility of the data rather than to the
 * control/avatar ladder [Size] describes. Mixing them in would mean the next
 * person tuning a bar height has to scan past twelve icon sizes to find it.
 */
data class Dashboard(
    /** The hero trend chart's drawing box. Tall enough that a 30-bucket series
     *  still has visible shape, short enough that the sections below stay on the
     *  first screen. */
    val chartHeight: Dp = 88.dp,
    /** The 7-day bar cluster that rides beside the bookings count. */
    val barsWidth: Dp = 86.dp,
    val barsHeight: Dp = 32.dp,
    /** Gap between bars in that cluster. */
    val barGap: Dp = 3.dp,
    /** A score-breakdown meter. A hairline would disappear; anything thicker
     *  reads as a progress bar and pulls focus from the number beside it. */
    val meterHeight: Dp = 3.dp,
    /**
     * Fixed gutter for a metric's label, so the four meters beside them start on
     * the same x. Letting each row size to its own text would stagger the bars
     * and destroy the only thing the set is for — comparing them at a glance.
     */
    val meterLabelWidth: Dp = 116.dp,
    /**
     * Availability-strip day cell. Narrower than [Size.dateCellW]: that card
     * carries a price and belongs to the booking funnel, this one carries a
     * weekday and a numeral, and fourteen of them have to be scannable in one
     * horizontal sweep.
     */
    val dayCellW: Dp = 42.dp,
    val dayCellH: Dp = 50.dp,
    /**
     * The dashboard's own score ring — a ramp step above [Size.ringLg], which is
     * the size the ring takes everywhere it accompanies something else. Here it
     * IS the section: the artist's score with four meters hanging off it, and at
     * the shared size it under-read against the four-row block beside it.
     */
    val scoreRing: Dp = 86.dp,
    /** The "there's a gig here" dot inside a day cell. */
    val dayDot: Dp = 3.dp,
    /** Attention dot leading a banner headline. */
    val bannerDot: Dp = 8.dp,
)

/** width : height ratios for media containers. */
data class AspectRatios(
    val portrait: Float = 4f / 5f,
    val editorial: Float = 3f / 4f,
    val landscape: Float = 16f / 9f,
    val square: Float = 1f,
    val stripWide: Float = 21f / 9f,
)

/** Bundle handed to the theme so composables read `AppTheme.dimens.space.lg`. */
data class Dimens(
    val space: Space = Space(),
    val radii: Radii = Radii(),
    val size: Size = Size(),
    val aspect: AspectRatios = AspectRatios(),
    val fraction: Fractions = Fractions(),
    val chrome: Chrome = Chrome(),
    val hero: Hero = Hero(),
    val dashboard: Dashboard = Dashboard(),
)
