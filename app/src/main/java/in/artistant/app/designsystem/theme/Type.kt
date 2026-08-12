package `in`.artistant.app.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import `in`.artistant.app.R

// ─────────────────────────────────────────────────────────────────────────────
// The brand faces — the same three families the iOS app ships (Typography.swift),
// bundled under `res/font`. Before this they were the platform fallbacks
// (Serif/SansSerif/Monospace → Noto Serif + Roboto + Roboto Mono), which is why
// every headline and every price read as "an Android app" next to the iOS build:
// the sizes and tracking were already right, only the typeface was wrong.
//
//   Instrument Serif   editorial headlines, mastheads   STATIC — Regular + Italic
//   Geist              all UI sans                      VARIABLE — wght 100–900
//   Geist Mono         prices, scores, meta strips      VARIABLE — wght 100–900
//
// Both are SIL Open Font License 1.1; the license texts live in `/licenses` at
// the repo root. The OFL requires them to travel with the fonts, so don't drop
// those files when trimming the tree.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One weight of a variable font.
 *
 * Geist and Geist Mono ship as a SINGLE .ttf carrying a `wght` axis (100–900,
 * default 400) rather than one file per weight, so the same resource is declared
 * once per weight the ramp actually asks for. Two things have to agree for that
 * to work, and getting only one of them right is the classic failure:
 *
 *  - [Font.weight] is what Compose's font MATCHER keys on. When a TextStyle asks
 *    for `FontWeight.SemiBold` it picks the declared entry closest to 600.
 *  - `variationSettings` is what is actually applied to the typeface
 *    (`Typeface.Builder.setFontVariationSettings`, API 26+ — exactly this app's
 *    minSdk, so it always applies). WITHOUT it, every entry would resolve to the
 *    file's default instance and Medium/SemiBold/Bold would all render at 400,
 *    identically — a ramp that looks wired but is flat.
 *
 * Declaring the real weights also avoids SYNTHETIC bold: when the nearest match
 * is far enough off, Compose fakes weight by smearing the glyph outline, which
 * next to a genuine 600 master reads as muddy rather than heavier.
 *
 * The opt-in is on the `Font(…, variationSettings = …)` overload, still marked
 * experimental on Compose BOM 2024.12.01 (ui-text 1.7.6). It is the only way to
 * drive a variable axis from Kotlin; the non-experimental escape hatch is an XML
 * `<font-family>` with `android:fontVariationSettings` per entry, which would
 * move the ramp's weights out of this file into four res/font XMLs. Kept here so
 * the weights stay reviewable next to the styles that use them — if a future BOM
 * changes the signature, that XML form is the fallback.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFace(resId: Int, weight: FontWeight): Font = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * The weights the ramp and its call sites actually request — `grep -rho
 * 'FontWeight\.[A-Za-z]*'` over `app/src` returns exactly these five and nothing
 * else. Each is a real point on the wght axis, so none of them is synthesised.
 *
 * Add a weight HERE before using it in a TextStyle: an undeclared weight does not
 * fail, it silently snaps to the nearest declared one, which is the kind of bug
 * that only shows up in a side-by-side screenshot.
 */
private val BrandWeights = listOf(
    FontWeight.Normal, // 400
    FontWeight.Medium, // 500
    FontWeight.SemiBold, // 600
    FontWeight.Bold, // 700
    FontWeight.Black, // 900
)

/**
 * Instrument Serif — the editorial voice.
 *
 * Static, and it has only Regular and Italic: there is NO bold master upstream.
 * That is survivable because no serif step in [AppType] sets a `fontWeight` (they
 * all inherit Normal) — but it is also a constraint on future edits. Asking this
 * family for Bold gets Compose's synthetic smear, and on a high-contrast display
 * serif that looks broken rather than emphatic. A headline that needs more weight
 * needs a different family, not a heavier request.
 *
 * The Italic face is load-bearing, not ornamental: the Discover masthead sets the
 * city in italic-lime, and every `EditorialHeadline` accent word is an italic span
 * over a serif style. Both arrive as `FontStyle.Italic`, so the italic file has to
 * be declared here — otherwise Android obliques the roman, which on a serif with
 * a true cursive italic is visibly not the same letterforms.
 */
val SerifFamily: FontFamily = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/**
 * Geist — the UI sans.
 *
 * No italic master exists upstream (Vercel ships roman only), so the few italic
 * sans accents — e.g. the signup ProfileScreen's "then we're *in*." — fall back to
 * a synthetic oblique. That is the same thing iOS does with the same file, so the
 * two apps stay matched.
 */
val SansFamily: FontFamily =
    FontFamily(BrandWeights.map { variableFace(R.font.geist_variable, it) })

/** Geist Mono — prices, scores, metadata strips. Same variable-axis treatment. */
val MonoFamily: FontFamily =
    FontFamily(BrandWeights.map { variableFace(R.font.geist_mono_variable, it) })

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
    /**
     * A tab root's LARGE page title, and its second line.
     *
     * Distinct from [title] (24sp) and from `ScreenTitleBar`'s [headline] (18sp),
     * which is the centred inline bar most tab roots wear. The press-kit editor is
     * the one artist root whose reference draws a large left-aligned title with a
     * subtitle under it, and the two sizes are not interchangeable: at 24sp the
     * title stops out-ranking the section labels below it and the page reads as
     * starting mid-way through itself.
     *
     * 34sp is measured off the reference (ascender-to-baseline band of 25.3 units
     * over a ~0.74 ascender ratio). It is sans rather than the editorial serif on
     * purpose — this is chrome naming the screen, not an editorial moment, and the
     * serif steps are reserved for the ones that are.
     */
    val pageTitle: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 34.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
    ),
    /**
     * The one oversized sans headline in the product — the signup profile step's
     * "A few words, then we're *in*."
     *
     * Its own step above [pageTitle] because it is doing a different job: that
     * one names a screen you have arrived at, this one is the whole screen. It
     * is the largest type the app sets, and it is SANS rather than the editorial
     * serif — the reference deliberately switches face here, so the moment reads
     * as a statement rather than as another editorial header.
     *
     * 50sp with -1 tracking, both taken from the reference. The size is the point
     * (it was set at 44 with no tracking, which read a full ramp step quieter),
     * and the negative tracking is what stops a headline this large from
     * looking gappy — at display sizes the default sidebearings are tuned for
     * body copy and show as holes between letters.
     */
    val signupDisplay: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 50.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
    ),
    val headline: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    val body: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 16.sp),
    val callout: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    val footnote: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    // caption is the small-caps/section-label style (BOOKINGS, UPCOMING, NEXT 14 DAYS…).
    // iOS kerns these ~0.8 (Typography.swift `sectionLabel()`); the letterSpacing lives on
    // the token so every SectionLabel / WizardSectionLabel call site inherits the editorial
    // kerning without per-site changes.
    val caption: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
    /**
     * [caption] tracked wider, for a label that has to hold a screen on its own.
     *
     * Search's browse rails are three short words on an otherwise empty page —
     * at the standard section kerning they read as body copy rather than as
     * headers, which is why the reference tracks these particular labels further
     * than the ones that sit above a dense block.
     */
    val railLabel: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp),
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
    /** Artist-PROFILE hero name. Distinct from [heroName], the larger 40sp Discover
     *  hero: the profile name sits on a 48%-height hero, Discover on a 74% one, so
     *  they are different steps of the ramp rather than a duplicate. */
    val profileHeroName: TextStyle = displayTitle.copy(letterSpacing = (-0.8).sp),

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

    /**
     * The account page's stat triple — Bookings / Saved / Completed.
     *
     * Its own step rather than [monoLarge] (24sp): three counts share one row
     * and each is a navigation target, so they have to read as a SET of numbers.
     * At the currency step they compete with the serif name above them, which is
     * the one thing on that screen allowed to be the largest.
     *
     * It lands on the same 22sp as [scoreRing] today, and that is a coincidence
     * of two unrelated surfaces wanting the same optical size — not a shared
     * value. Tuning the ring must not move the counters, so it is spelled out
     * rather than derived.
     */
    val monoCount: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold,
    ),

    /**
     * The label under a [monoCount]. One point below [caption], carrying the
     * same editorial kerning: it sits directly beneath a number it is captioning
     * rather than heading a section, so it has to sit lower in the hierarchy
     * than the section labels that share its all-caps treatment.
     */
    val statLabel: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
    ),

    /** Time-slot pills. */
    val monoChip: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    ),

    // ── Month calendar ───────────────────────────────────────────────────────
    // A calendar is a table of numerals, so all three of its steps are mono: the
    // day numbers have to sit on the same optical column down every week, which
    // proportional figures cannot do. These were the last surface still setting
    // its numerals in the sans `caption` step, which is why the grid read as a
    // list of small labels rather than as a calendar.

    /** The year beside the month name in a calendar header. Quiet on purpose —
     *  the month is the headline, the year is the disambiguator. */
    val monoYear: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium,
    ),

    /** A day numeral in the month grid. Taken to Bold at the call site for the
     *  two days that earn it: today, and a day carrying a gig. */
    val monoDay: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium,
    ),

    /** The M T W T F S S strip above the grid. Deliberately below every other
     *  step — it is a column header nobody reads twice. */
    val monoWeekday: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
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
    // ── Chrome + Discover hero ───────────────────────────────────────────────
    // These are ramp steps that carry per-site optical tuning (negative tracking
    // on the editorial serif, a hand-set leading on the two-line masthead, a
    // sub-caption tab label). They live on the token rather than at the call
    // site so the values stay reviewable in one place — the house rule is that
    // no screen inlines a raw sp.
    /**
     * Two-line editorial masthead ("Tonight in / <City>."). The tight leading is
     * deliberate: the serif's default line box is far too airy for a stacked
     * headline, and iOS pulls it in by 5pt. 37sp is the resulting line pitch, set
     * explicitly rather than left to the font's own line box — Instrument Serif's
     * default leading is generous, and the value is now measured against the real
     * face rather than the Noto Serif fallback it was first tuned on.
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

/**
 * Material3's own type scale, re-based onto Geist.
 *
 * [AppType] covers everything the app styles deliberately, but it is not the only
 * type on screen. `MaterialTheme` publishes `typography.bodyLarge` as the ambient
 * `LocalTextStyle`, so any `Text()` written without an explicit `style` — the
 * Avatar monogram, the wordmark disc, the OAuth provider initial — plus every
 * Material component that types itself (TextButton, TextField, AlertDialog) picks
 * up whatever family the M3 default carries. That default is `FontFamily.Default`
 * → Roboto, which would have left a visible seam: brand faces everywhere the ramp
 * reaches and the system font everywhere it does not.
 *
 * Only the family is swapped. M3's sizes, line heights and tracking are kept as-is
 * — this is a fallback layer, not a second ramp, and anything that needs real
 * design attention should be reaching for [AppType] instead.
 */
val BrandTypography: Typography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = SansFamily),
        displayMedium = displayMedium.copy(fontFamily = SansFamily),
        displaySmall = displaySmall.copy(fontFamily = SansFamily),
        headlineLarge = headlineLarge.copy(fontFamily = SansFamily),
        headlineMedium = headlineMedium.copy(fontFamily = SansFamily),
        headlineSmall = headlineSmall.copy(fontFamily = SansFamily),
        titleLarge = titleLarge.copy(fontFamily = SansFamily),
        titleMedium = titleMedium.copy(fontFamily = SansFamily),
        titleSmall = titleSmall.copy(fontFamily = SansFamily),
        bodyLarge = bodyLarge.copy(fontFamily = SansFamily),
        bodyMedium = bodyMedium.copy(fontFamily = SansFamily),
        bodySmall = bodySmall.copy(fontFamily = SansFamily),
        labelLarge = labelLarge.copy(fontFamily = SansFamily),
        labelMedium = labelMedium.copy(fontFamily = SansFamily),
        labelSmall = labelSmall.copy(fontFamily = SansFamily),
    )
}
