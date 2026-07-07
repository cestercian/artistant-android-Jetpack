package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Every color token from SCREEN_INVENTORY §1 — dark-only warm-black ladder.
 * `brand`/`brandInk`/`brandSoft` are role-reactive (filled by [AppRole.accent]);
 * everything else is fixed. Read via `AppTheme.colors.*`, never a raw hex.
 */
data class AppColors(
    // Backgrounds
    val bg: Color = Color(0xFF0A0A0A),
    val bgElev: Color = Color(0xFF141414),
    val bgCard: Color = Color(0xFF1A1A1A),
    val bgSoft: Color = Color(0xFF222222),
    // Hairlines
    val line: Color = Color(0xFF2A2A2A),
    val lineSoft: Color = Color(0xFF1F1F1F),
    // Ink ladder
    val ink: Color = Color(0xFFF5F5F5),
    val ink2: Color = Color(0xFFA8A8A8),
    val ink3: Color = Color(0xFF6E6E6E),
    val ink4: Color = Color(0xFF4F5366),
    // Status
    val hot: Color = Color(0xFFFF5A5F),
    val warm: Color = Color(0xFFFFB454),
    val good: Color = Color(0xFF5BE074),
    // Fixed accent (violet) — distinct from the role-reactive brand.
    val accent: Color = Color(0xFF7C5CFF),
    val accentSoft: Color = Color(0xFF1C1733),
    val accentInk: Color = Color(0xFFC9BFFF),
    // Role-reactive brand — defaults to client lime; overridden by the theme.
    val brand: Color = Color(0xFFC8FF00),
    val brandInk: Color = Color(0xFF0A0A0A),
    val brandSoft: Color = Color(0xFF1D2309),
)

/** Apply a role's accent trio onto the fixed base. */
fun AppColors.withRole(role: AppRole): AppColors {
    val a = role.accent()
    return copy(brand = a.brand, brandInk = a.brandInk, brandSoft = a.brandSoft)
}
