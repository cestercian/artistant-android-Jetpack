package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The session is single-role; flipping this re-themes every brand surface.
 * Client = acid lime, Artist = electric violet (SCREEN_INVENTORY §1).
 */
enum class AppRole { Client, Artist }

/** The three role-reactive accent tokens resolved from [AppRole]. */
data class RoleAccent(
    val brand: Color,
    val brandInk: Color,
    val brandSoft: Color,
)

fun AppRole.accent(): RoleAccent = when (this) {
    AppRole.Client -> RoleAccent(
        brand = Color(0xFFC8FF00),
        brandInk = Color(0xFF0A0A0A),
        brandSoft = Color(0xFF1D2309),
    )
    AppRole.Artist -> RoleAccent(
        brand = Color(0xFF7C5CFF),
        brandInk = Color(0xFFFFFFFF),
        brandSoft = Color(0xFF1C1733),
    )
}
