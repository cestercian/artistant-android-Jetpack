package `in`.artistant.app.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// Ambient token holders — composables read these via the AppTheme accessors.
val LocalAppColors: ProvidableCompositionLocal<AppColors> =
    staticCompositionLocalOf { AppColors() }
val LocalAppType: ProvidableCompositionLocal<AppType> =
    staticCompositionLocalOf { AppType() }
val LocalDimens: ProvidableCompositionLocal<Dimens> =
    staticCompositionLocalOf { Dimens() }

/**
 * Single theme wrapper. Dark-only. [role] drives the role-reactive brand accent
 * (client lime / artist violet) — a single-role session recomposes the tree on
 * change, which is cheap and correct. We also feed a Material3 dark scheme so
 * stray Material components (ripples, text-field defaults) look right.
 */
@Composable
fun ArtistantTheme(
    role: AppRole = AppRole.Client,
    content: @Composable () -> Unit,
) {
    val colors = AppColors().withRole(role)
    val material = darkColorScheme(
        primary = colors.brand,
        onPrimary = colors.brandInk,
        background = colors.bg,
        onBackground = colors.ink,
        surface = colors.bgCard,
        onSurface = colors.ink,
    )
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppType provides AppType(),
        LocalDimens provides Dimens(),
    ) {
        MaterialTheme(colorScheme = material, content = content)
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
