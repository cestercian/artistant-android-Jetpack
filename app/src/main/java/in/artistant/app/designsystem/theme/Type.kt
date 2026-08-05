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

    // ─────────────────────────────────────────────────────────────────────
    // Artist-profile / booking-funnel steps.
    //
    // These are not new inventions — they are the sizes those two surfaces
    // already use on iOS, which the ramp above had no slot for. They were
    // being approximated with the nearest existing step (a 17sp dock price
    // rendered as 16sp `monoMedium`, a 26sp hero price as 24sp `monoLarge`,
    // a 9sp label as 12sp `caption`), and "nearest step" is exactly how the
    // two apps drifted apart. Each carries the kerning its iOS counterpart
    // does, because at display sizes the tracking is as load-bearing as the
    // point size: mono numerals set at default tracking read visibly looser.
    //
    // Defaults reference earlier constructor parameters (legal in Kotlin) so
    // a derived style can never fall out of sync with the step it derives from.
    // ─────────────────────────────────────────────────────────────────────

    /** Artist hero name — the editorial serif, tightened. */
    val heroName: TextStyle = displayTitle.copy(letterSpacing = (-0.8).sp),

    /** "from ₹75,000" hero figure in the profile's Booking block. */
    val monoHero: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 26.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
    ),

    /** Stat-strip numerals and the day-of-month on a date card. */
    val monoStat: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 18.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
    ),

    /** The price sitting left of the profile's pinned CTA. */
    val monoDock: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 17.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
    ),

    /** Package-row price. */
    val monoPrice: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold,
    ),

    /** Time-slot pills. */
    val monoChip: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    ),

    /**
     * Sub-caption mono, wide-tracked: "FROM" under the dock price, the tier
     * word inside the score chip. Deliberately tiny — it is a unit label on a
     * number, not a line anyone reads on its own.
     */
    val monoMicro: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 9.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp,
    ),

    /** The same micro size at normal tracking/weight — the chip's "Details ›". */
    val monoMicroSoft: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 9.sp, fontWeight = FontWeight.Medium,
    ),

    /**
     * Pinned-CTA label. Body-sized and bold, NOT `headline` (18sp): a lime
     * capsule that has to share one row with a price cannot afford the wider
     * label, and iOS sets its dock CTA at the body step for the same reason.
     */
    val ctaLabel: TextStyle = body.copy(fontWeight = FontWeight.Bold),
)
