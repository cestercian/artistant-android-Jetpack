package `in`.artistant.app.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Ambient token holders — composables read these via the AppTheme accessors.
val LocalAppColors: ProvidableCompositionLocal<AppColors> =
    staticCompositionLocalOf { AppColors() }
val LocalAppType: ProvidableCompositionLocal<AppType> =
    staticCompositionLocalOf { AppType() }
val LocalDimens: ProvidableCompositionLocal<Dimens> =
    staticCompositionLocalOf { Dimens() }

/**
 * Single theme wrapper. Light-only, one accent.
 *
 * [role] is still taken because navigation and the paywall branch on it, but it
 * no longer changes a colour — `withRole` is identity since the Sep-2026
 * redesign retired the client-lime / artist-violet split (REDESIGN_2026-09 §2).
 *
 * The Material3 scheme fed alongside is `lightColorScheme` now, and every role
 * it publishes is mapped: "stray Material components" reach for more than six of
 * them, and anything left unmapped keeps M3's BASELINE PURPLE. A `ModalBottomSheet`
 * takes its container from `surfaceContainerLow` and its drag handle from
 * `onSurfaceVariant`, which is how every sheet in the app once drew a lavender
 * handle over a purple-tinted pane. [BrandTypography] rides along so the type
 * those components default to is Plus Jakarta Sans rather than Roboto —
 * `MaterialTheme` publishes `typography.bodyLarge` as the ambient text style.
 */
@Composable
fun ArtistantTheme(
    role: AppRole = AppRole.Client,
    content: @Composable () -> Unit,
) {
    val colors = AppColors().withRole(role)
    val material = lightColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        // `primaryContainer` is what an M3 `FilledTonalButton`, an assist chip and
        // a date-picker selection reach for. Left at the default it is a lavender
        // that belongs to no screen in this app.
        primaryContainer = colors.brandSoft,
        onPrimaryContainer = colors.accentDeep,
        secondary = colors.accentInk,
        onSecondary = colors.onDark,
        secondaryContainer = colors.surface2,
        onSecondaryContainer = colors.ink,
        tertiary = colors.warm,
        onTertiary = colors.onDark,
        tertiaryContainer = colors.warmSoft,
        onTertiaryContainer = colors.ink,
        background = colors.page,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.surface2,
        onSurfaceVariant = colors.ink2,
        surfaceTint = colors.accent,
        inverseSurface = colors.dark,
        inverseOnSurface = colors.onDark,
        inversePrimary = colors.accent,
        surfaceContainerLowest = colors.surface,
        surfaceContainerLow = colors.page,
        surfaceContainer = colors.surface3,
        surfaceContainerHigh = colors.surface2,
        surfaceContainerHighest = colors.placeholder,
        outline = colors.hairline,
        outlineVariant = colors.lineSoft,
        error = colors.danger,
        onError = colors.onDark,
        errorContainer = colors.dangerSoft,
        onErrorContainer = colors.danger,
        scrim = Color.Black,
    )
    // Resolved here, at the single root every surface sits under, so no screen
    // has to remember to read the accessibility setting for itself. (Two screens
    // previously each rolled their own read of it; both now defer to this.)
    val reduceMotion = rememberReduceMotion()
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppType provides AppType(),
        LocalDimens provides Dimens(),
        LocalMotion provides Motion(),
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(colorScheme = material, typography = BrandTypography, content = content)
    }
}

/** Token accessors — `AppTheme.colors.brand`, mirroring SwiftUI `Color.brand`. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current
    val type: AppType
        @Composable @ReadOnlyComposable get() = LocalAppType.current
    val dimens: Dimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current
}
