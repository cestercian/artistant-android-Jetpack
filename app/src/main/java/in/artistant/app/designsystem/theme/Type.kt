package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Brand fonts (Instrument Serif / Geist / Geist Mono) aren't dropped yet.
// TODO: swap to bundled Instrument Serif / Geist / Geist Mono in res/font.
// Until then these fall back to the platform families so the ramp compiles.
val SerifFamily: FontFamily = FontFamily.Serif
val SansFamily: FontFamily = FontFamily.SansSerif
val MonoFamily: FontFamily = FontFamily.Monospace

/**
 * The AppType ramp from SCREEN_INVENTORY §1. Sizes are in sp so the system
 * font-scale applies (Compose honors fontScale by default).
 */
data class AppType(
    val displayHero: TextStyle = TextStyle(fontFamily = SerifFamily, fontSize = 40.sp),
    val displayTitle: TextStyle = TextStyle(fontFamily = SerifFamily, fontSize = 32.sp),
    val displayMedium: TextStyle = TextStyle(fontFamily = SerifFamily, fontSize = 28.sp),
    val displaySub: TextStyle = TextStyle(fontFamily = SerifFamily, fontSize = 24.sp),
    val displaySmall: TextStyle = TextStyle(fontFamily = SerifFamily, fontSize = 22.sp),
    val title: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold),
    val headline: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 16.sp),
    val callout: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    val footnote: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    val caption: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
    val monoLarge: TextStyle = TextStyle(fontFamily = MonoFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold),
    val monoMedium: TextStyle = TextStyle(fontFamily = MonoFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    val monoSmall: TextStyle = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    val scoreRing: TextStyle = TextStyle(fontFamily = MonoFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold),
)
