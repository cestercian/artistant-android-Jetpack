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
    val chrome: Chrome = Chrome(),
    val hero: Hero = Hero(),
)
