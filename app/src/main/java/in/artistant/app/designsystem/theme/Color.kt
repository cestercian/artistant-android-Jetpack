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

    // ── Over-media chrome ─────────────────────────────────────────────────
    // Controls that float on a photo can't use the surface ladder above: those
    // are opaque, and an opaque disc on a hero reads as a sticker. These are
    // translucent white instead, so whatever is behind them still shows and the
    // control belongs to the image. They are the flat stand-in for the blurred
    // material iOS uses in the same spots — Compose has no cheap live blur, and
    // a fake blur costs a render pass per frame for chrome this small.
    /** Floating hero control that carries only an ICON (back / message / save). */
    val glass: Color = Color(0x33FFFFFF),
    /**
     * Backdrop for over-media chrome that carries TEXT.
     *
     * A lightening wash cannot do this job. Its result depends entirely on the
     * photo behind it, so the text contrast is whatever the artist's cover
     * happens to allow — and on a mid-tone cover the wash lands in the same
     * luminance band as the muted inks, which is how the score capsule ended up
     * rendering its label at 1.2:1 against its own fill. Darkening to near-opaque
     * makes the backdrop predictable, so a text colour can be chosen once and
     * hold over any cover. An icon does not need this (a white glyph survives the
     * wash), which is why [glass] stays as it is.
     */
    val glassDark: Color = Color(0xB8000000),
    /** Rim on a glass control — reads the edge without a hard outline. */
    val glassLine: Color = Color(0x24FFFFFF),
    /** Quiet chip resting ON media (category, city) — fill and its rim. */
    val chipOnMedia: Color = Color(0x0FFFFFFF),
    val chipOnMediaLine: Color = Color(0x14FFFFFF),
    /** Secondary text on media — dimmer than `ink2`, which is tuned for `bg`. */
    val inkOnMedia: Color = Color(0xBFFFFFFF),
    /** Tertiary text on media (the score chip's "Details ›"). */
    val inkOnMediaSoft: Color = Color(0x99FFFFFF),
    // ── Translucent "glass" stand-ins ────────────────────────────────────────
    // The floating tab bar and the Discover hero controls sit on a blurred
    // material in the reference design. Blur is deliberately OUT here (design
    // owner's call), so each of those surfaces is approximated by TWO flat
    // layers painted in order: a black scrim, then a white veil.
    //
    // Why two and not one: a single translucent fill cannot both darken a photo
    // backdrop AND lift the near-black page background — it can only move in one
    // direction. The scrim darkens over photos; the veil re-lifts the result so
    // the shape still reads against `bg`.
    //
    // How the split is chosen. Over `bg` (#0A0A0A) the scrim has almost nothing
    // left to darken, so the veil alone sets the resting appearance — which
    // means the scrim can be strong WITHOUT changing how chrome looks on an
    // ordinary dark screen. That is the whole trick, and it is why the first
    // calibration was wrong: at 42% the bar held its own against the page
    // background but let a bright hero flood through, reading as a tinted pane
    // rather than as chrome. Measured composites, page background → bright
    // magenta media (202,96,174):
    //
    //   scrim   over bg   selected   over bright media   white label contrast
    //    42%      #1F1F      #3E3E      (131, 76,116)            5.9 : 1
    //    70%      #1C1C      #3C3C      ( 80, 51, 72)           10.1 : 1
    //
    // Reference chrome measures #1C1C1C / #3D3D3D over the same background, so
    // the stronger scrim is not a trade — it matches the resting appearance
    // BETTER while taming bright media. It also desaturates (0.42 → 0.36 on that
    // magenta), which is the other thing a real blurred material does for free.
    // Above ~78% the bar stops reading as translucent at all.
    /** 70% black — chrome scrim (floating tab bar / search circle). */
    val glassScrim: Color = Color(0xB2000000),
    /** 10% white — chrome veil, painted over [glassScrim]. */
    val glassVeil: Color = Color(0x1AFFFFFF),
    /** 14% white — the selected-tab capsule's lift, painted over the chrome. */
    val glassSelected: Color = Color(0x24FFFFFF),
    /**
     * 50% black — softer scrim for controls floating directly on the hero photo.
     * Lighter than the chrome pair because these sit ON the subject rather than
     * over it, but strengthened on the same reasoning: the reference sample was
     * taken over a DARK photo region, where 30% and 50% are indistinguishable
     * (23 vs 20 against a measured 21), so the stronger value is free insurance
     * for the bright hero we have no reference sample for.
     */
    val glassSoftScrim: Color = Color(0x80000000),
    /** 5% white — softer veil, painted over [glassSoftScrim]. */
    val glassSoftVeil: Color = Color(0x0DFFFFFF),
)

/** Apply a role's accent trio onto the fixed base. */
fun AppColors.withRole(role: AppRole): AppColors {
    val a = role.accent()
    return copy(brand = a.brand, brandInk = a.brandInk, brandSoft = a.brandSoft)
}
