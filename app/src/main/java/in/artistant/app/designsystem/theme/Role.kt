package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Which side of the marketplace the session is on.
 *
 * It no longer re-themes anything. The Sep-2026 redesign has ONE accent for both
 * roles (docs/REDESIGN_2026-09.md §2), so a client and an artist see the same
 * lime. The enum stays because navigation branches on it — `ArtistantNavHost`
 * picks a whole scaffold from this value — and because the paywall, the wizard
 * and the signup role picker all still ask which seat the user is in.
 */
enum class AppRole { Client, Artist }

/**
 * The accent trio. One value now, not two.
 *
 * Kept as a type rather than inlined so the role→accent lookup has somewhere to
 * live if a second accent ever comes back. [AppRole.accent] answers the same for
 * both roles today, which is the point: the dual-accent rule is retired, not
 * conditionally disabled.
 */
data class RoleAccent(
    val brand: Color,
    val brandInk: Color,
    val brandSoft: Color,
)

private val SingleAccent = RoleAccent(
    brand = Color(0xFFD6F84B),
    brandInk = Color(0xFF0B0B0C),
    brandSoft = Color(0xFFF5FBDA),
)

/** The accent for a role — the same one, whichever role it is. */
@Suppress("UnusedReceiverParameter")
fun AppRole.accent(): RoleAccent = SingleAccent
