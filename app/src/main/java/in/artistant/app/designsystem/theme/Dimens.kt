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
    /** 1pt hairline borders (Decline CTA, calendar cell chrome). */
    val hairline: Dp = 1.dp,
    /** Avatar ring / badge stroke (iOS Avatar uses fixed 2pt). */
    val stroke: Dp = 2.dp,
    /** ArtistList row thumbnail (iOS ArtistRow thumb 62×78). */
    val listThumbW: Dp = 62.dp,
    val listThumbH: Dp = 78.dp,

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
)
