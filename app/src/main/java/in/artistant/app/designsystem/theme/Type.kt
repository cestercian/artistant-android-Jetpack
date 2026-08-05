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
    // caption is the small-caps/section-label style (BOOKINGS, UPCOMING, NEXT 14 DAYS…).
    // iOS kerns these ~0.8 (Typography.swift `sectionLabel()`); the letterSpacing lives on
    // the token so every SectionLabel / WizardSectionLabel call site inherits the editorial
    // kerning without per-site changes.
    val caption: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    val monoLarge: TextStyle = TextStyle(fontFamily = MonoFamily, fontSize = 24.sp, fontWeight = FontWeight.Bold),
    val monoMedium: TextStyle = TextStyle(fontFamily = MonoFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    val monoSmall: TextStyle = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    val scoreRing: TextStyle = TextStyle(fontFamily = MonoFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold),

    // ── Chrome + Discover hero ───────────────────────────────────────────────
    // These are ramp steps that carry per-site optical tuning (negative tracking
    // on the editorial serif, a hand-set leading on the two-line masthead, a
    // sub-caption tab label). They live on the token rather than at the call
    // site so the values stay reviewable in one place — the house rule is that
    // no screen inlines a raw sp.
    /**
     * Two-line editorial masthead ("Tonight in / <City>."). The tight leading is
     * deliberate: the serif's default line box is far too airy for a stacked
     * headline, and iOS pulls it in by 5pt. 37sp is the resulting line pitch,
     * set explicitly so it survives the Serif fallback until the brand .ttf lands.
     */
    val masthead: TextStyle = TextStyle(
        fontFamily = SerifFamily,
        fontSize = 28.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.8).sp,
    ),
    /** Hero artist name — the largest editorial moment in the app. */
    val heroName: TextStyle = TextStyle(
        fontFamily = SerifFamily,
        fontSize = 40.sp,
        letterSpacing = (-1).sp,
    ),
    /** "Featured this week" frame name — one ramp step down from [heroName]. */
    val frameName: TextStyle = TextStyle(
        fontFamily = SerifFamily,
        fontSize = 28.sp,
        letterSpacing = (-0.6).sp,
    ),
    /** Hero metadata strip: `INDIE BAND · BANGALORE · 82 TRUSTED · FROM ₹75K`. */
    val heroMeta: TextStyle = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    ),
    /** Same strip inside a featured frame — one point tighter to fit 300dp. */
    val frameMeta: TextStyle = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
    ),
    /** "● AVAILABLE FRI" capsule. */
    val heroStatus: TextStyle = TextStyle(
        fontFamily = MonoFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    /** Floating tab-bar label — below the caption step, matching iOS's tab type. */
    val tabLabel: TextStyle = TextStyle(
        fontFamily = SansFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
    ),
)
