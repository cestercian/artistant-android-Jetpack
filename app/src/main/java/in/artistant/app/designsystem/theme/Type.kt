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
// "Artistant iOS Light" (docs/REDESIGN_2026-09.md §2) ships TWO faces, not three:
//
//   Plus Jakarta Sans   everything      VARIABLE — wght 200–800, roman + italic
//   JetBrains Mono      eyebrow labels, numerals, prices
//                                       VARIABLE — wght 100–800
//
// The editorial serif is RETIRED. Instrument Serif carried the old dark design's
// mastheads and hero names; the light design sets those in the same sans at a
// tighter tracking, so there is no serif register left to fill. [SerifFamily]
// survives as an ALIAS of [SansFamily] so the ~40 call sites that still name it
// compile and render in the new voice — the eleven section PRs delete the names
// as they rewrite their screens. Do not add a new call site.
//
// Both are SIL Open Font License 1.1; the license texts live in `/licenses` at
// the repo root. The OFL requires them to travel with the fonts, so don't drop
// those files when trimming the tree.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One weight of a variable font.
 *
 * Both faces ship as a SINGLE .ttf carrying a `wght` axis rather than one file
 * per weight, so the same resource is declared once per weight the ramp asks
 * for. Two things have to agree for that to work, and getting only one of them
 * right is the classic failure:
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
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFace(
    resId: Int,
    weight: FontWeight,
    style: FontStyle = FontStyle.Normal,
): Font = Font(
    resId = resId,
    weight = weight,
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/**
 * The weights the ramp and its call sites actually request.
 *
 * [FontWeight.ExtraBold] (800) is the top rung rather than `Black` (900) because
 * that is where BOTH files' `wght` axis ends — Plus Jakarta Sans runs 200–800,
 * JetBrains Mono 100–800. The six `FontWeight.Black` call sites left in the app
 * resolve to this entry, which is the heaviest real master; declaring a 900 that
 * the axis cannot reach would just clamp to the same outline while telling the
 * matcher a lie about what is available.
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
    FontWeight.ExtraBold, // 800 — the axis maximum on both faces
)

/**
 * Plus Jakarta Sans — the whole UI.
 *
 * Roman and italic are separate variable files, and both are declared: italic
 * spans are load-bearing in the signup copy, and without the italic file Android
 * obliques the roman, which is a slant rather than the designed letterforms.
 */
val SansFamily: FontFamily = FontFamily(
    BrandWeights.map { variableFace(R.font.plus_jakarta_sans_variable, it) } +
        BrandWeights.map {
            variableFace(R.font.plus_jakarta_sans_italic_variable, it, FontStyle.Italic)
        },
)

/** JetBrains Mono — eyebrow labels, prices, scores, metadata strips. */
val MonoFamily: FontFamily =
    FontFamily(BrandWeights.map { variableFace(R.font.jetbrains_mono_variable, it) })

/**
 * Retired: the editorial serif is gone from the design language.
 *
 * Kept as an alias of [SansFamily] purely so existing call sites compile through
 * the P1 foundation PR. It is not a family — reaching for it gets you the sans.
 */
@Deprecated(
    "The editorial serif is retired (REDESIGN_2026-09 §2). Use SansFamily.",
    ReplaceWith("SansFamily"),
)
val SerifFamily: FontFamily = SansFamily

/**
 * The AppType ramp, re-cut to `docs/REDESIGN_2026-09.md` §2.
 *
 * Two groups of names live here:
 *
 *  - **The design's own names** — `screenTitle`, `sectionTitle`, `cardTitle`,
 *    `rowTitle`, `subtitle`, `chip`, `cta`, `badge`, `monoLabel`, `monoPill`,
 *    plus `body` / `caption` / `displayHero`, which the old ramp already had.
 *    These are measured off the extracted markup and are what new code uses.
 *  - **Compatibility aliases** — every name the old dark ramp published, pointed
 *    at the nearest new step so the ~450 existing call sites compile and render
 *    on the light palette. They are marked below. The section PRs retire them.
 *
 * Sizes are in sp so the system font-scale applies. Tracking is expressed in sp
 * because that is Compose's unit; the design states it in `em`, so each value is
 * `em × size` (26sp at −0.03em → −0.78sp).
 */
data class AppType(
    // ── The design ramp ──────────────────────────────────────────────────────

    /** Large left-aligned page title: "Discover", "Bookings", "Messages". */
    val screenTitle: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 26.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.78).sp,
    ),

    /**
     * The onboarding hero — the only step above [screenTitle].
     *
     * A full ramp step below what the dark design set (40sp serif). The light
     * design has no editorial register to be grand in, so the largest type in
     * the product is a 30sp sans headline and everything else steps down from
     * there.
     */
    val displayHero: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 30.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.84).sp,
    ),

    /** Section header above a rail or a block: "Available Sat night". */
    val sectionTitle: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 17.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.34).sp,
    ),

    /** Title on a hero card, over media. */
    val cardTitle: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 18.5f.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.37).sp,
    ),

    /** A list row's or tile's own name. */
    val rowTitle: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 14.5f.sp, fontWeight = FontWeight.SemiBold,
    ),

    /** Body copy. Line height is 1.6× per §2 — the light design runs airier. */
    val body: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 15.sp, lineHeight = 24.sp,
    ),

    /** Secondary line under a title: "Bengaluru · Sat 12 Oct". Draw in `ink4`. */
    val subtitle: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 13.5f.sp),

    /**
     * Smallest body step: helper text, field labels, meta under a row.
     *
     * §2 sets this at 12.5/400. It carries a hair of tracking and Medium weight
     * here because the same token still backs the app's remaining ALL-CAPS
     * section labels — at 400 with no tracking those set as a cramped grey
     * smudge. New uppercase labels should take [monoLabel] instead, which is
     * what the design actually draws; this is the compromise that keeps the
     * inherited ones readable until the section PRs convert them.
     */
    val caption: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 12.5f.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp,
    ),

    /** Filter/segment pill label. Bold at the call site when selected. */
    val chip: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 13.5f.sp, fontWeight = FontWeight.Medium,
    ),

    /** The label on a 54dp primary button. */
    val cta: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 16.5f.sp, fontWeight = FontWeight.Bold,
    ),

    /** Accent badge riding on a card or a hero: "Top rated", "In 3 days". */
    val badge: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 11.5f.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.23.sp,
    ),

    /**
     * The eyebrow: mono, small, wide, uppercased AT THE CALL SITE.
     *
     * The style cannot uppercase for you — Compose has no text-transform — so
     * pass `.uppercase()` where you set the string. Tracking is what makes this
     * step work; at default tracking mono caps read as a serial number.
     */
    val monoLabel: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 11.sp,
        fontWeight = FontWeight.Medium, letterSpacing = 1.32.sp,
    ),

    /** Mono inside a pill or a status capsule — one step up, less tracking. */
    val monoPill: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 11.5f.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.69.sp,
    ),

    /** A numeral that is content rather than chrome — a stat, the logo "A". */
    val monoNumber: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 18.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
    ),

    // ── Compatibility aliases ────────────────────────────────────────────────
    // Every name below existed in the dark ramp and still has call sites. Each
    // points at the nearest step above, so the app renders in the new voice
    // without 450 edits landing in this PR — and so the eleven section agents
    // are not all editing the same file at the same time. Kotlin lets a default
    // reference an earlier parameter, which is what keeps an alias from drifting
    // away from the step it aliases.

    /** @see screenTitle */
    val displayTitle: TextStyle = screenTitle,
    /** One step between [displayHero] and [screenTitle]. */
    val displayMedium: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 24.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp,
    ),
    /** Artist / person name at the top of a detail screen (§2: 21/700). */
    val displaySub: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 21.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.53).sp,
    ),
    /** Name inside a header block (§2: 19/700). */
    val displaySmall: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 19.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.38).sp,
    ),
    /** @see displayMedium */
    val title: TextStyle = displayMedium,
    /** @see screenTitle */
    val pageTitle: TextStyle = screenTitle,
    /** @see displayHero */
    val signupDisplay: TextStyle = displayHero,
    /** @see displayHero */
    val welcomeDisplay: TextStyle = displayHero,
    /** Two-line masthead: [displayMedium] with the leading pulled in. */
    val masthead: TextStyle = displayMedium.copy(lineHeight = 30.sp),
    /** @see displayMedium */
    val heroName: TextStyle = displayMedium,
    /** @see displaySub */
    val frameName: TextStyle = displaySub,
    /** @see displaySub */
    val profileHeroName: TextStyle = displaySub,
    /** @see sectionTitle */
    val headline: TextStyle = sectionTitle,
    /** @see rowTitle */
    val callout: TextStyle = rowTitle,
    /** @see chip */
    val footnote: TextStyle = chip,
    /** @see monoLabel */
    val railLabel: TextStyle = monoLabel,
    /** @see monoLabel */
    val statLabel: TextStyle = monoLabel,
    /** @see cta */
    val ctaLabel: TextStyle = cta,
    /**
     * Tab-bar label.
     *
     * The light design's tab bar draws GLYPHS ONLY — there is no label under the
     * icon any more (screens 02 / 10 / 19 / 26). The step survives for the two
     * remaining call sites and for a11y-driven label rows; nothing in the new
     * chrome sets it.
     */
    val tabLabel: TextStyle = TextStyle(
        fontFamily = SansFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium,
    ),

    // Mono steps. The light design uses mono for the same three jobs the dark one
    // did — eyebrows, numerals, prices — so these keep their sizes and are simply
    // re-based onto JetBrains Mono.
    val monoLarge: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 24.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
    ),
    val monoMedium: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
    ),
    val monoSmall: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 12.sp, fontWeight = FontWeight.Medium,
    ),
    val scoreRing: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold,
    ),
    /** The hero "from ₹75,000" figure. */
    val monoHero: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 26.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp,
    ),
    /** @see monoNumber */
    val monoStat: TextStyle = monoNumber,
    /** The price sitting left of a pinned CTA. */
    val monoDock: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 17.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp,
    ),
    /** Package-row price. */
    val monoPrice: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold,
    ),
    /** The account page's stat triple. Spelled out, not derived from [scoreRing]:
     *  tuning the ring must not move the counters. */
    val monoCount: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold,
    ),
    /** Time-slot pills. */
    val monoChip: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
    ),
    /** Calendar: year beside the month name. */
    val monoYear: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Medium,
    ),
    /** Calendar: a day numeral in the grid. */
    val monoDay: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 16.sp, fontWeight = FontWeight.Medium,
    ),
    /** Calendar: the M T W T F S S strip. */
    val monoWeekday: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
    ),
    /**
     * The "pm" under the clock on a day's schedule row (screens 36 and 78, which
     * draw the pair identically: the clock at 12, the meridiem at 11).
     *
     * Spelled out rather than aliased to [monoLabel], which is also 11: that one
     * is the eyebrow and carries +0.12em of tracking for uppercase runs. This is
     * one lowercase word sitting under a numeral, and eyebrow tracking on it
     * reads as a gap between the p and the m.
     */
    val monoMeridiem: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium,
    ),
    /**
     * A date in the dashboard's 14-day availability strip (screens 85 / 86).
     *
     * The smallest numeral in the app, and the design's own 10px: fourteen cells
     * share one 350-wide row, so a cell is about 21 wide and a two-digit date has
     * to fit inside that. It was drawn with [monoCount] — the 22sp account-page
     * counter — which made "10" wider than its cell and wrapped every date from
     * the 10th onwards onto two lines ("1" over "0"). Spelled out rather than
     * aliased to [monoWeekday]: that one is the calendar's letter row and is
     * SemiBold because letters at 10sp need the weight; these are digits inside a
     * filled cell and take the design's plain 400.
     */
    val monoStripDay: TextStyle = TextStyle(
        fontFamily = MonoFamily, fontSize = 10.sp, fontWeight = FontWeight.Normal,
    ),
    /**
     * @see monoLabel
     *
     * The dark ramp set this at 9sp. It grows to the design's 11sp eyebrow here
     * on purpose: 9sp mono was already the smallest type in the app, and on a
     * near-white ground the thinner light-on-dark stroke advantage is gone.
     */
    val monoMicro: TextStyle = monoLabel,
    /** [monoMicro] without the eyebrow tracking. */
    val monoMicroSoft: TextStyle = monoLabel.copy(letterSpacing = 0.sp),
    /** @see monoLabel */
    val heroMeta: TextStyle = monoLabel,
    /** [monoLabel] one point tighter, to fit a 300dp frame. */
    val frameMeta: TextStyle = monoLabel.copy(fontSize = 10.5f.sp, letterSpacing = 0.9.sp),
    /** @see monoPill */
    val heroStatus: TextStyle = monoPill,
)

/**
 * Material3's own type scale, re-based onto Plus Jakarta Sans.
 *
 * [AppType] covers everything the app styles deliberately, but it is not the only
 * type on screen. `MaterialTheme` publishes `typography.bodyLarge` as the ambient
 * `LocalTextStyle`, so any `Text()` written without an explicit `style` — the
 * Avatar monogram, the wordmark disc, the OAuth provider initial — plus every
 * Material component that types itself (TextButton, TextField, AlertDialog) picks
 * up whatever family the M3 default carries. That default is `FontFamily.Default`
 * → Roboto, which would leave a visible seam: brand faces everywhere the ramp
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

/**
 * How much of the reader's font scale a **fixed-width cell** can actually spend.
 *
 * Almost nothing in this app needs this: type scales, boxes grow, and a taller row is the
 * correct answer to a bigger font. The exception is a numeral in a cell whose width is
 * decided by a grid rather than by its contents — the dashboard's fourteen-day availability
 * strip is fourteen `weight(1f)` cells in one row, so a cell is about 21dp wide however large
 * the type is, and a two-digit mono date at scale 2.0 is wider than that. A cell cannot be
 * widened without taking the width from its neighbours, and there is no fifteenth column to
 * take it from.
 *
 * The alternatives are all worse. Wrapping turns "10" into "1" over "0", which reads as two
 * dates. Clipping — Compose's default with `softWrap = false` — cuts the numeral mid-glyph
 * and shows half a digit, which reads as a different date. Ellipsis prints "…". This shrinks
 * instead: past [cap] the numeral stops growing, so a reader at 2.0 gets the strip at
 * [cap]-sized digits rather than at guesswork, and everything up to [cap] scales normally.
 *
 * Returns a MULTIPLIER for an `sp` size, not a size: `sp` is multiplied by the system scale at
 * draw time, so dividing the declared size by the overshoot is what pins the drawn result.
 *
 * @param fontScale `LocalDensity.current.fontScale`.
 * @param cap the largest scale this cell can hold. 1 or less means "no scaling at all", which
 *   is not this function's business to allow — callers pass a real ceiling.
 */
fun cappedFontScale(fontScale: Float, cap: Float): Float =
    if (fontScale > cap) cap / fontScale else 1f
