package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * The "Artistant iOS Light" palette (docs/REDESIGN_2026-09.md §2 and §4).
 *
 * Two things changed in Sep 2026 and both are load-bearing:
 *
 *  1. **Light.** The warm-black ladder is gone. Everything sits on a warm off-
 *     white (`page`) with near-black ink on top. Every value below is measured
 *     off the extracted design markup, not derived.
 *  2. **One accent.** The client/artist split (lime vs violet) is retired. There
 *     is a single accent — `#d6f84b` — for both roles, so `withRole` no longer
 *     changes a colour. [AppRole] survives because navigation still branches on
 *     it; the palette does not.
 *
 * Names come in two groups: the design's own tokens, and the old dark-ramp names
 * remapped per §4 so existing screens keep compiling. Read via
 * `AppTheme.colors.*`, never a raw hex.
 */
data class AppColors(
    // ── Surfaces ─────────────────────────────────────────────────────────────
    /** The canvas everything sits on. Warm off-white, never pure #fff. */
    val page: Color = Color(0xFFFAFAF6),
    /** A card or sheet lifted off [page]. */
    val surface: Color = Color(0xFFFFFFFF),
    /** Pills, icon circles, unselected chips, input fields. */
    val surface2: Color = Color(0xFFF1F2EC),
    /** Search bars and grouped list backgrounds — a hair cooler than [surface2]. */
    val surface3: Color = Color(0xFFF6F7F3),
    /** An image slot before its media loads. */
    val placeholder: Color = Color(0xFFEBECE4),

    // ── Lines ────────────────────────────────────────────────────────────────
    /** Dividers and card strokes. */
    val hairline: Color = Color(0xFFE6E8DF),
    /** A separator that has to survive on a dark surface, and timeline dots. */
    val lineStrong: Color = Color(0xFFC6C9BE),

    // ── Ink ──────────────────────────────────────────────────────────────────
    /** Primary text and icons. */
    val ink: Color = Color(0xFF14150F),
    /** Secondary text, unselected chip label. */
    val ink2: Color = Color(0xFF5C5F55),
    /** Body copy on light. */
    val ink3: Color = Color(0xFF6D7168),
    /** Captions and meta ("Bengaluru · Sat 12 Oct"). */
    val ink4: Color = Color(0xFF8A8D82),
    /** Placeholder text and the search glyph. */
    val hint: Color = Color(0xFF7E8274),

    // ── The one accent ───────────────────────────────────────────────────────
    /** The single signal: primary CTA, selected chip, badges. One per screen. */
    val accent: Color = Color(0xFFD6F84B),
    /** Text and glyphs sitting ON [accent]. */
    val onAccent: Color = Color(0xFF0B0B0C),
    /**
     * The accent used as TEXT or an icon on a light ground — "See all", the
     * review star, an inline link.
     *
     * It is not [accent]: lime on off-white measures about 1.3:1, which is not
     * text, it is decoration. This is the same hue dropped to a leaf green that
     * reads at 5.4:1 on [page] while still being recognisably the brand signal.
     */
    val accentInk: Color = Color(0xFF5E7307),
    /** [accentInk] deeper still, for a label inside an accent-tinted pill. */
    val accentDeep: Color = Color(0xFF3F4D05),

    // ── Dark surfaces ────────────────────────────────────────────────────────
    // The light design keeps a few deliberately dark objects: the splash ("the
    // one dark room"), the toast, a quote card's chrome. These are those.
    /** Dark surface — toast, dark card. */
    val dark: Color = Color(0xFF16171A),
    /** The darkest step: splash background, notch. */
    val darkest: Color = Color(0xFF0F100C),
    /** Text on a dark surface. */
    val onDark: Color = Color(0xFFFFFFFF),
    /** Secondary text on a dark surface or a media gradient. */
    val onDarkSoft: Color = Color(0xFFC3C7B8),

    // ── Status ───────────────────────────────────────────────────────────────
    /** Destructive and failed. */
    val danger: Color = Color(0xFFA4402C),
    /** Fill and stroke of a danger banner. */
    val dangerSoft: Color = Color(0xFFF9EFEC),
    val dangerLine: Color = Color(0xFFF0E2DE),
    /** Warnings and pending. */
    val warm: Color = Color(0xFF8A6A2A),
    /** Fill and stroke of a warm banner. */
    val warmSoft: Color = Color(0xFFF7F3EA),
    val warmLine: Color = Color(0xFFF2EAD9),

    // ── §4 compatibility names ───────────────────────────────────────────────
    // Everything below is an old dark-ramp name remapped onto the light palette
    // per REDESIGN_2026-09 §4. They exist so ~1,900 call sites keep compiling
    // through P1; the eleven section PRs replace them with the names above as
    // they rewrite each screen. Do not add a new call site.
    /** @see page */
    val bg: Color = page,
    /** @see surface */
    val bgElev: Color = surface,
    /** @see surface3 */
    val bgCard: Color = surface3,
    /** @see surface2 */
    val bgSoft: Color = surface2,
    /** @see hairline */
    val line: Color = hairline,
    /** A softer hairline, for a divider inside an already-bounded block. */
    val lineSoft: Color = Color(0xFFECEEE7),
    /** @see danger */
    val hot: Color = danger,
    /** @see accentInk — the old "good" green is now the accent read as text. */
    val good: Color = accentInk,
    /** @see accent */
    val brand: Color = accent,
    /** @see onAccent */
    val brandInk: Color = onAccent,
    /** The accent at ~12% over white — a tint you can put ink on top of. */
    val brandSoft: Color = Color(0xFFF5FBDA),
    /** @see brandSoft */
    val accentSoft: Color = brandSoft,

    // ── Over-media chrome ────────────────────────────────────────────────────
    // Photos stay dark, so everything in this block is unchanged by the light
    // redesign (§4 says so explicitly). A control floating on a cover cannot use
    // the surface ladder above: those are opaque, and an opaque disc on a hero
    // reads as a sticker. These are translucent instead, so whatever is behind
    // them still shows and the control belongs to the image.
    /** Floating hero control that carries only an ICON (back / message / save). */
    val glass: Color = Color(0x33FFFFFF),
    /**
     * Backdrop for over-media chrome that carries TEXT.
     *
     * A lightening wash cannot do this job. Its result depends entirely on the
     * photo behind it, so the text contrast is whatever the artist's cover
     * happens to allow — and on a mid-tone cover the wash lands in the same
     * luminance band as the muted inks. Darkening to near-opaque makes the
     * backdrop predictable, so a text colour can be chosen once and hold over
     * any cover. An icon does not need this (a white glyph survives the wash),
     * which is why [glass] stays as it is.
     */
    val glassDark: Color = Color(0xB8000000),
    /** Rim on a glass control — reads the edge without a hard outline. */
    val glassLine: Color = Color(0x24FFFFFF),
    /** Quiet chip resting ON media (category, city) — fill and its rim. */
    val chipOnMedia: Color = Color(0x0FFFFFFF),
    val chipOnMediaLine: Color = Color(0x14FFFFFF),
    /** Secondary text on media. */
    val inkOnMedia: Color = Color(0xBFFFFFFF),
    /** Tertiary text on media. */
    val inkOnMediaSoft: Color = Color(0x99FFFFFF),
    /**
     * The media scrim: `rgba(11,11,12,.95)` at the bottom edge, ramping to
     * transparent (§2). Only the opaque end is a token — the gradient's other
     * stop is `Color.Transparent`, which needs no name.
     */
    val mediaScrim: Color = Color(0xF20B0B0C),

    // ── Retired translucent chrome ───────────────────────────────────────────
    // The dark design floated its tab bar and hero controls on a two-layer
    // scrim+veil stand-in for a blurred material. The light design's chrome is
    // OPAQUE (a white bar with a hairline top — screens 02 / 10 / 19 / 26), so
    // the pair no longer describes anything the app draws over a light page.
    // They survive for the controls that still float on a PHOTO, where the
    // reasoning is unchanged.
    /** 70% black — scrim under a control floating on media. */
    val glassScrim: Color = Color(0xB2000000),
    /** 10% white — veil painted over [glassScrim]. */
    val glassVeil: Color = Color(0x1AFFFFFF),
    /** 14% white — a selected capsule's lift over that chrome. */
    val glassSelected: Color = Color(0x24FFFFFF),
    /** 50% black — softer scrim for a control sitting ON the subject. */
    val glassSoftScrim: Color = Color(0x80000000),
    /** 5% white — softer veil, painted over [glassSoftScrim]. */
    val glassSoftVeil: Color = Color(0x0DFFFFFF),
)

/**
 * Apply a role's accent onto the base palette.
 *
 * **Identity, deliberately.** The redesign has ONE accent for both roles, so
 * there is nothing left for this to change. It is kept (rather than deleted with
 * its call sites) because it is the seam the design owner would reopen if a
 * second accent ever came back, and because deleting it would touch the theme
 * root in a PR that eleven other agents are about to branch from.
 */
@Suppress("UNUSED_PARAMETER")
fun AppColors.withRole(role: AppRole): AppColors = this
