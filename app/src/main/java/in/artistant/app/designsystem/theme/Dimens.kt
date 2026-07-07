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
)
