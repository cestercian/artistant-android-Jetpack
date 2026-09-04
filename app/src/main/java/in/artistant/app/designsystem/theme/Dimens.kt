package `in`.artistant.app.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4/8/12/16/24/32 spacing scale (xs…xxl). */
data class Space(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/** 8/12/18/24/32 corner radii. */
data class Radii(
    /**
     * The corner of a control small enough that [sm] would round it into a circle.
     *
     * The design keeps a squircle's radius at roughly a third of its box: 8 on the 24dp
     * consent square, 5 on the 16dp "at least 8 characters" tick. Reusing [sm] on the smaller
     * one produces a 16dp box with an 8dp radius, which is not a rounded square, it is a dot —
     * and a dot beside a rule reads as a bullet rather than as a checkbox.
     */
    val xs: Dp = 5.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 18.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /**
     * The signup flow's button radius, and the one radius on the ramp named
     * after a control rather than a step.
     *
     * Deliberately off the sm…xxl ladder — it sits between [md] (12) and [lg]
     * (18) and there is nothing to snap it to. That is not an accident of this
     * port: iOS carries the identical value under the identical name
     * (`Radii.buttonLg`), with a comment saying it is off-ramp and that
     * reconciling it would restyle every CTA in the app, which is a decision for
     * the design owner rather than a drive-by. Same reasoning holds here, so the
     * number gets a name instead of a ramp seat.
     *
     * Every signup CTA takes it — the auth step's three provider buttons and the
     * profile step's outlined lime Continue — which is what makes it a token and
     * not a literal worth leaving alone.
     */
    val buttonLg: Dp = 16.dp,
    /**
     * The light design's CONTROL radius — a search bar, a text field, a
     * secondary button (REDESIGN_2026-09 §2, "search bar: radius 15").
     *
     * Off the sm…xxl ladder for the same reason [buttonLg] is: it sits between
     * [md] (12) and [lg] (18) and there is nothing to snap it to. Rounding it to
     * either neighbour is visible — at 12 a 48dp bar reads as a box, at 18 it
     * starts reading as a capsule, and the design wants neither.
     */
    val control: Dp = 15.dp,
    /**
     * A content card's radius — a booking card, a thread card, a banner block.
     * Between [lg] (18) and [xl] (24), and measured off the markup, where every
     * "a thing you can tap that contains a picture and two lines" is 20.
     */
    val card: Dp = 20.dp,
)

/** Icon/control/avatar/ring/hero sizes (SCREEN_INVENTORY §1). */
data class Size(
    val iconSm: Dp = 12.dp,
    val iconMd: Dp = 16.dp,
    val iconLg: Dp = 20.dp,
    val iconXl: Dp = 28.dp,
    val controlMin: Dp = 48.dp,   // button/input min height
    val rowMin: Dp = 44.dp,
    val avatarSm: Dp = 32.dp,
    val avatarMd: Dp = 48.dp,
    val avatarLg: Dp = 64.dp,
    val avatarXl: Dp = 96.dp,
    val ringMd: Dp = 64.dp,
    val ringLg: Dp = 76.dp,
    val ringXl: Dp = 120.dp,
    val heroTall: Dp = 460.dp,
    val heroShort: Dp = 280.dp,
    /**
     * The cover PREVIEW card in the press-kit editor — a picture of the hero, not
     * the hero itself.
     *
     * Its own value rather than a [heroShort] reuse, because the two answer to
     * different things. A hero is sized to fill the top of a screen; this is a
     * thumbnail inside a labelled section that has eight more sections under it,
     * so it is sized to leave the sections visible. Taken from the reference,
     * where the card measures 180 units between a section label and a swatch row.
     */
    val coverPreview: Dp = 180.dp,
    /**
     * One gradient swatch in the cover picker. Landscape (56×38) so a row of six
     * reads as six little covers rather than as six buttons — the shape is the
     * only thing telling the artist what they are choosing.
     */
    val swatchW: Dp = 56.dp,
    val swatchH: Dp = 38.dp,
    /** 1pt hairline borders (Decline CTA, calendar cell chrome). */
    val hairline: Dp = 1.dp,
    /** Avatar ring / badge stroke (iOS Avatar uses fixed 2pt). */
    val stroke: Dp = 2.dp,
    /**
     * Emphasis stroke — a month-grid tile that is selected or is today. Half a
     * unit above [hairline], which is all it takes for a ring to read as picked;
     * at [stroke] it stops being a ring and becomes a border, and a grid of 35
     * bordered boxes is a spreadsheet.
     */
    val strokeEmphasis: Dp = 1.5.dp,

    // ── Month grid ────────────────────────────────────────────────────────
    /**
     * A day tile in the month grid. Fixed height rather than a square aspect
     * ratio: the tile stacks a numeral over a status dot, and a square cell on a
     * 7-column grid is ~48 wide on a phone — tall enough for the numeral alone,
     * not for the numeral plus the dot plus the air between them.
     */
    val gridCellH: Dp = 52.dp,
    /** The status dot under a day numeral (open / unavailable). */
    val gridDot: Dp = 5.dp,
    /** ArtistList row thumbnail (iOS ArtistRow thumb 62×78). */
    val listThumbW: Dp = 62.dp,
    val listThumbH: Dp = 78.dp,
    /**
     * Tailroom under the last row of a settings list, so the final entry — which
     * is "Delete account", the one row nobody should have to fight the floating
     * tab bar to read — clears the chrome and has somewhere to scroll to.
     * Smaller than [Hero.scrollTailroom]: that one buys a horizontal rail room
     * to overshoot, this one just buys a hairline some air.
     */
    val listTailroom: Dp = 56.dp,

    // ── Artist hero / booking funnel ──────────────────────────────────────
    /**
     * Floating hero control: a 40dp disc inside a 44dp tap target. The disc is
     * the visible circle and the extra 4dp is hit slop, so the buttons can sit
     * close to the screen edge without shrinking below the touch-target floor.
     */
    val heroControl: Dp = 40.dp,
    /** The mini score ring that rides in the hero identity row, and its stroke. */
    val ringXs: Dp = 34.dp,
    val ringXsStroke: Dp = 3.dp,
    /**
     * Stroke for the [ringXl] ring — the score explainer's hero, the one place the
     * ring IS the content rather than an ornament beside something else, so it takes
     * the heaviest weight in the ladder (iOS `ScoreExplainerView` draws 120pt at
     * 7pt). Named rather than typed at the call site so the weight sits next to
     * [ringXsStroke] and can be compared, not rediscovered.
     */
    val ringXlStroke: Dp = 8.dp,
    /**
     * Date card. Portrait on purpose (56×76): the weekday, the day numeral and
     * the availability dot stack, which is what makes a run of them scan as a
     * calendar strip rather than a row of buttons.
     */
    val dateCellW: Dp = 56.dp,
    val dateCellH: Dp = 76.dp,
    /** Availability dot — on a date card and in its Free/Busy legend. */
    val dot: Dp = 6.dp,
    /** Package-row radio: 20dp ring, 10dp filled core. */
    val radio: Dp = 20.dp,
    val radioCore: Dp = 10.dp,
    /**
     * A package option card: the inset inside its rim, and the gap between two
     * stacked cards.
     *
     * Both are deliberately off the [Space] ramp, which is why they live here
     * rather than as `space.lg` / `space.md` — same reasoning as
     * [ctaBarTailroom], and the same provenance: measured off the reference
     * build, where a matched-content package list (identical tier names, an
     * identical one-line "includes" string) renders a 45-unit row pitch that
     * `lg`/`md` overshoot by 6 and that `md`/`sm` would undershoot by the same
     * amount. There is no ramp step between them, so a picker whose rows are
     * meant to read as ONE dense block of choices — rather than as four
     * separately-padded cards — has nowhere on the ramp to land.
     *
     * Scoped to this one component on purpose. Anything else that wants a card
     * inset should still take `space.lg`; these two exist to hold a measurement,
     * not to open a second spacing scale.
     */
    val optionCardPad: Dp = 14.dp,
    val optionCardGap: Dp = 10.dp,
    /** Full-width pinned CTA (taller than `controlMin` — it is the only action). */
    val ctaTall: Dp = 52.dp,
    /**
     * Air under a pinned CTA bar, on top of whatever the gesture bar claims.
     *
     * Deliberately off the [Space] ramp, which is why it lives here: the funnel's
     * bottom bar is the one place the app wants MORE clearance than `xl` and less
     * than `xxl`, so that the terminal action of a paid flow is never a thumb's
     * width from the system gesture area. Measured off the reference build, where
     * the same bar reserves this much above its home-indicator inset.
     */
    val ctaBarTailroom: Dp = 28.dp,
    /**
     * Width kept clear at each end of a centred bar title, so it can never run
     * under the controls flanking it.
     *
     * Wider than [rowMin] because the widest of those controls is a labelled
     * pill, not an icon button, and the reservation has to be symmetric — an
     * asymmetric one centres the title against the bar but not against the eye.
     */
    val barTitleReserve: Dp = 76.dp,
)

/**
 * Geometry of the v2 component library (`designsystem/component/`), measured off
 * the "Artistant iOS Light" markup — REDESIGN_2026-09 §2, "Geometry".
 *
 * Its own group rather than more fields on [Size] because these are the
 * dimensions of NAMED CONTROLS, not steps on the icon/avatar/ring ladder [Size]
 * describes. A reader tuning the CTA height should not have to scan past twelve
 * icon sizes to find it, and a section agent looking for "how tall is a chip"
 * should have one place to look.
 */
data class Components(
    /** Page gutter. Every screen's horizontal padding. */
    val gutter: Dp = 20.dp,
    /** Primary CTA height. */
    val cta: Dp = 54.dp,
    /** Secondary button, text field, the sign-in provider rows. */
    val control: Dp = 50.dp,
    /** Header action circle. */
    val iconCircle: Dp = 42.dp,
    /** The smaller header circle, where the title band is tighter. */
    val iconCircleSm: Dp = 40.dp,
    /** The unread / attention dot that rides on an [iconCircle]. */
    val iconCircleDot: Dp = 7.dp,
    /** Search bar. */
    val searchBar: Dp = 48.dp,
    /** Hero card. */
    val heroCard: Dp = 262.dp,
    /** The circular save control floating on a hero card. */
    val heroSave: Dp = 34.dp,
    /** A rail tile's image band. */
    val tileImage: Dp = 118.dp,
    // ── Loading skeletons (screen 59) ─────────────────────────────────────
    // Measured off the loading screen rather than derived from the loaded one,
    // because the design's note is that the two must MATCH: "skeletons match
    // rail geometry, so the fill-in doesn't reflow what the eye already parsed".
    // Keeping them as their own numbers is what lets a reviewer check that.
    /** A skeleton tile — one whole tile, image plus its two text lines. */
    val skeletonTile: Dp = 188.dp,
    /** The bar standing in for a 26sp screen title, and for its subtitle. */
    val skeletonTitleWidth: Dp = 132.dp,
    val skeletonTitleHeight: Dp = 22.dp,
    val skeletonSubtitleWidth: Dp = 92.dp,
    /** The bar standing in for a section header, and any one-line label. */
    val skeletonSectionWidth: Dp = 148.dp,
    val skeletonLineHeight: Dp = 14.dp,
    /** Chip placeholders — two widths, alternated, so the row reads as words. */
    val skeletonChipWide: Dp = 78.dp,
    val skeletonChipNarrow: Dp = 60.dp,
    val skeletonChipHeight: Dp = 34.dp,
    /** A list row's minimum height (the design draws 56–64). */
    val row: Dp = 56.dp,
    /** One OTP box. */
    val otpBox: Dp = 60.dp,
    /** Chip padding: 9 vertical, 16 horizontal, fully rounded. */
    val chipPadH: Dp = 16.dp,
    val chipPadV: Dp = 9.dp,
    /** Sheet grabber. */
    val grabberW: Dp = 36.dp,
    val grabberH: Dp = 4.dp,
    /** The status dot leading a [StatusPill]-style label. */
    val statusDot: Dp = 7.dp,
    /** Emphasis stroke on a focused field or a selected OTP box. */
    val focusStroke: Dp = 1.5.dp,
    /**
     * A consent checkbox — the 18+/terms square on screen 118 and the pledge
     * square on screen 27, plus the stroke its unchecked state is drawn with.
     *
     * Its own number rather than an icon-ladder reuse because it is not an icon:
     * it sits at the top-left of a wrapping paragraph and its size is answerable
     * to the CAP HEIGHT of the copy beside it, not to the glyph ladder. The
     * stroke is a hair over [focusStroke] for the same reason the design draws it
     * that way — an unchecked square has nothing but its outline to be seen by.
     */
    val checkbox: Dp = 24.dp,
    val checkboxStroke: Dp = 1.8.dp,
    /** The circle behind an empty state's glyph, and the glyph inside it. */
    val emptyGlyphCircle: Dp = 72.dp,
    val emptyGlyph: Dp = 28.dp,
    /**
     * `max-width: 30ch` on an empty state's body copy, resolved to a Dp.
     *
     * A character-relative cap has no Compose equivalent that does not cost an
     * extra text measurement, and at the design's body size 30ch measures ~280
     * on the reference device. Fixed rather than computed because the thing it
     * protects is a READING MEASURE — centred copy running the full width of a
     * phone gives the eye nowhere reliable to land on the next line — and a
     * measure that grows with the font scale stops being a measure.
     */
    val readingMeasure: Dp = 280.dp,
    /** The action stack under an empty state's copy. */
    val emptyActionWidth: Dp = 270.dp,
    /** Toast: its corner radius is [Radii.buttonLg]; this is the icon disc. */
    val toastIcon: Dp = 24.dp,
    /** Air between a toast and whatever chrome is under it. */
    val toastGap: Dp = 22.dp,

    // ── Artist studio (design section AS: screens 09 / 85 / 86 / 22 / 133) ──
    /**
     * Internal padding of a HERO block — the studio's accent money card, the
     * availability calendar's card. One step above [cardPad]: these blocks carry
     * a 38sp figure or a seven-column grid, and at the card padding they read as
     * cramped against their own corner radius.
     */
    val heroPad: Dp = 20.dp,
    /**
     * Internal padding of a CONTENT card — a request row, a gig row, a stat
     * cell. Deliberately not [Space.lg] (16): the design measures 15 on every
     * one of them, and the difference shows when two sit side by side under a
     * 20dp page gutter.
     */
    val cardPad: Dp = 15.dp,
    /**
     * The avatar on a LIST row — a request card, a gig-request header.
     *
     * Between [Size.avatarSm] (32, a chat row) and [Size.avatarMd] (48, a
     * profile). The design uses it wherever a monogram identifies the other
     * party inside a card rather than inside a line of text.
     */
    val rowAvatar: Dp = 40.dp,
    /** A header status pill ("● Taking gigs") — its own padding, not a chip's. */
    val pillPadH: Dp = 13.dp,
    val pillPadV: Dp = 8.dp,
    /**
     * One cell of the dashboard's 14-day availability strip.
     *
     * Height only: the fourteen cells share the width equally, so a fixed width
     * would either overflow a narrow phone or leave a gap on a wide one.
     */
    val stripCellH: Dp = 44.dp,
    /** The earnings chart's drawing box (screen 133) and its bars' top radius. */
    val barChart: Dp = 78.dp,
    val barRadius: Dp = 3.dp,

    // ── Section BN ────────────────────────────────────────────────────────
    /**
     * The picture band on a confirmed booking's card (screen 10).
     *
     * Its own number rather than a [tileImage] reuse: that one is a rail tile's
     * band, sized so five artists fit across a scroll, and this is a full-width
     * card that has to hold a 16:9-ish crop of a stage without becoming the
     * whole screen. Measured off the markup, where the band is 140 against a
     * 350-wide card.
     */
    val bookingCardImage: Dp = 140.dp,
    /**
     * One star in the review rating (screens 20 / 98).
     *
     * Deliberately off the [Size] icon ladder, whose top step is 28: the stars
     * ARE the review — everything else on that screen is optional — and at icon
     * size a row of five reads as a legend rather than as the control. The tap
     * target is grown to the 44dp floor around it at the call site.
     */
    val reviewStar: Dp = 36.dp,
)

/**
 * Sizes expressed as a share of the viewport rather than a fixed Dp.
 *
 * Kept apart from [Size] because they are unitless: a hero measured in Dp is a
 * different fraction of a 5" phone than of a foldable, and this hero's whole
 * point is the proportion it holds on screen.
 */
data class Fractions(
    /**
     * Artist-profile hero height. Tall for a detail screen, deliberately — the
     * photo IS the product on this surface.
     */
    val artistHero: Float = 0.48f,
    /**
     * Bottom share of the hero over which the media dissolves into the page.
     * The gradient ends on the page background rather than on black: ramping to
     * black would bottom out darker than `bg` and leave a visible step exactly
     * where the seam is supposed to disappear.
     */
    val heroFade: Float = 0.45f,
)

/**
 * Global navigation chrome — the light tab bar (screens 02 / 10 / 19 / 26).
 *
 * The floating, blurred, four-cell pill of the dark design is gone. What
 * replaces it is a plain opaque bar pinned to the bottom edge with a hairline on
 * top, four unlabelled glyphs, and one raised accent circle in the middle
 * carrying the screen's primary action.
 *
 * **On the 88 in the design and why it is not [barHeight] here.** The markup
 * draws an 88-tall bar, but 34 of that is the iPhone's home-indicator zone — a
 * fixed inset on a device with exactly one bezel. Android's equivalent is not
 * fixed: gesture nav claims ~24dp and three-button nav ~48dp, and both arrive at
 * runtime as a window inset. Hard-coding 88 would leave the glyphs floating in
 * dead space on gesture nav and buried under the nav buttons on three-button.
 * So the bar is composed instead: [barTopPad] + [tabIcon] + [barBottomPad] of
 * real content, plus whatever the system says it owes the navigation bar, which
 * lands on ~88 for the hardware the design was drawn against.
 */
data class Chrome(
    /** Air above the glyph row. */
    val barTopPad: Dp = 14.dp,
    /** Air below the glyph row, before the system navigation inset. */
    val barBottomPad: Dp = 16.dp,
    /** Left/right gutter — wider than the page gutter, so five slots breathe. */
    val barPadH: Dp = 30.dp,
    /** Tab glyph box. */
    val tabIcon: Dp = 24.dp,
    /** The raised centre action circle. */
    val actionSize: Dp = 52.dp,
    /** How far that circle sits ABOVE the bar's top edge. */
    val actionLift: Dp = 12.dp,
    /** Glyph inside the action circle. */
    val actionIcon: Dp = 24.dp,
    /**
     * Tailroom a scrolling tab root reserves under its last row.
     *
     * The bar is opaque and the `Scaffold` already insets content above it, so
     * this is only the visual air a list wants before the hairline — not the
     * bar's own footprint, which is what the floating design had to reserve.
     */
    val contentTailroom: Dp = 16.dp,
)

/**
 * Discover's immersive hero + rails. Same provenance as [Chrome]: literal iOS
 * values, ported so the two apps read identically at a glance.
 */
data class Hero(
    /** Hero occupies this fraction of the screen height (iOS: screenH * 0.74). */
    val heightFraction: Float = 0.74f,
    /** "Book →" pill height. */
    val ctaHeight: Dp = 50.dp,
    /** Circular save-heart button. */
    val saveSize: Dp = 50.dp,
    /** Masthead avatar chip diameter (ring stroke sits on top of this). */
    val avatarSize: Dp = 40.dp,
    /** "● AVAILABLE FRI" status capsule height. */
    val statusPillHeight: Dp = 30.dp,
    /** Status/score signal dot. */
    val signalDot: Dp = 7.dp,
    /** The 3pt "·" between metadata fields. */
    val separatorDot: Dp = 3.dp,
    /** Page-dot height (and width when inactive). */
    val pageDot: Dp = 6.dp,
    /** Page-dot width when active — the dot stretches into a capsule. */
    val pageDotActiveWidth: Dp = 22.dp,
    /** Gap between page dots. */
    val pageDotGap: Dp = 7.dp,
    /** Clearance under the CTA row so the page dots never collide with it. */
    val ctaBottomGap: Dp = 46.dp,
    /** Glyph↔label gap inside the status capsule and the "Book →" pill. */
    val labelGap: Dp = 6.dp,
    /** Signal-dot↔score gap in the metadata strip. */
    val scoreGap: Dp = 5.dp,
    /** Section title↔chevron gap. */
    val headerChevronGap: Dp = 5.dp,
    /** Field gap in a featured frame's metadata strip (tighter than the hero's). */
    val frameMetaGap: Dp = 7.dp,
    /** Drop shadow under a featured frame. */
    val frameShadow: Dp = 10.dp,
    /** "Featured this week" frame. */
    val frameWidth: Dp = 300.dp,
    val frameHeight: Dp = 452.dp,
    /** Rail tile (smaller than the design-system default — Discover packs more). */
    val tileWidth: Dp = 150.dp,
    val tileHeight: Dp = 200.dp,
    /**
     * Search's two-up results grid. Height only: the tile's width comes from the
     * weighted grid column, which is what iOS says too — `ArtistTile(size:
     * CGSize(width: 0, height: 220), fullWidth: true)` in `SearchView`.
     */
    val gridTileHeight: Dp = 220.dp,
    /** Trailing scroll tailroom so the last rail clears the floating tab bar. */
    val scrollTailroom: Dp = 130.dp,
    /** Hero auto-advance interval. */
    val autoAdvanceMillis: Long = 6_000L,
)

/**
 * The artist dashboard's charts, strip and banners.
 *
 * Kept as its own group rather than folded into [Size] because these are the
 * measurements of a *data display* — a chart's drawing box, a meter's thickness,
 * a day cell — and they answer to legibility of the data rather than to the
 * control/avatar ladder [Size] describes. Mixing them in would mean the next
 * person tuning a bar height has to scan past twelve icon sizes to find it.
 */
data class Dashboard(
    /** The hero trend chart's drawing box. Tall enough that a 30-bucket series
     *  still has visible shape, short enough that the sections below stay on the
     *  first screen. */
    val chartHeight: Dp = 88.dp,
    /** The 7-day bar cluster that rides beside the bookings count. */
    val barsWidth: Dp = 86.dp,
    val barsHeight: Dp = 32.dp,
    /** Gap between bars in that cluster. */
    val barGap: Dp = 3.dp,
    /** A score-breakdown meter. A hairline would disappear; anything thicker
     *  reads as a progress bar and pulls focus from the number beside it. */
    val meterHeight: Dp = 3.dp,
    /**
     * Fixed gutter for a metric's label, so the four meters beside them start on
     * the same x. Letting each row size to its own text would stagger the bars
     * and destroy the only thing the set is for — comparing them at a glance.
     */
    val meterLabelWidth: Dp = 116.dp,
    /**
     * Availability-strip day cell. Narrower than [Size.dateCellW]: that card
     * carries a price and belongs to the booking funnel, this one carries a
     * weekday and a numeral, and fourteen of them have to be scannable in one
     * horizontal sweep.
     */
    val dayCellW: Dp = 42.dp,
    val dayCellH: Dp = 50.dp,
    /**
     * The dashboard's own score ring — a ramp step above [Size.ringLg], which is
     * the size the ring takes everywhere it accompanies something else. Here it
     * IS the section: the artist's score with four meters hanging off it, and at
     * the shared size it under-read against the four-row block beside it.
     */
    val scoreRing: Dp = 86.dp,
    /** The "there's a gig here" dot inside a day cell. */
    val dayDot: Dp = 3.dp,
    /** Attention dot leading a banner headline. */
    val bannerDot: Dp = 8.dp,
)

/**
 * The press kit's own geometry — design screens 23 / 87 / 76.
 *
 * Its own group for the reason [Dashboard] has one: these are the measurements of
 * a *page*, not steps on the control ladder [Size] describes, and the person
 * tuning how tall a cover band is should not have to walk past twelve icon sizes
 * to find it.
 */
data class PressKit(
    /**
     * The cover band, filled. A banner rather than a 16:9 frame: at the page
     * gutter a true 16:9 cover is 197dp tall, which pushes the gallery and the
     * whole section list off the first screen — and the first screen is the
     * entire argument of a completion page. The slot still says "16:9" because
     * that is the crop a client's tile will take.
     */
    val cover: Dp = 142.dp,
    /**
     * The cover band, empty. Shorter than [cover] on purpose (screen 87): an
     * outline is lighter than a fill, so at the same height the empty state reads
     * as the bigger object of the two, which is backwards.
     */
    val coverEmpty: Dp = 126.dp,
    /** A gallery cell in the three-up strip. */
    val galleryCell: Dp = 80.dp,
    /**
     * The completion meter. Thicker than [Dashboard.meterHeight] because this one
     * IS the headline — the score page's meters accompany a number, this one is
     * the number.
     */
    val meter: Dp = 6.dp,
    /** The upload banner's progress bar, a step down: it sits inside a card. */
    val uploadMeter: Dp = 5.dp,
)

/**
 * width : height ratios for media containers.
 *
 * Two, because two are what the app crops to: [editorial] for the wizard's cover
 * frames and [square] for the press-kit gallery. A ratio nobody reads is an
 * invitation to reach for the wrong crop, so the rest are added back when a
 * surface actually needs one.
 */
data class AspectRatios(
    val editorial: Float = 3f / 4f,
    val square: Float = 1f,
)

/**
 * The booking funnel's own geometry — screens 05 / 06 / 07 / 17 / 61 / 94 / 132.
 *
 * Its own group rather than more fields on [Components] for the reason that
 * group's own note gives: these are not steps on a ladder anybody else climbs.
 * A check disc that fills a quarter of the confirmation screen and a 62dp act
 * thumbnail are facts about the "you asked / they said yes" pages, and nothing
 * outside `feature/booking` draws either of them.
 *
 * Every value is measured off the extracted markup, not derived.
 */
data class Funnel(
    /** The lime disc that opens screens 07 and 94. */
    val outcomeDisc: Dp = 74.dp,
    /** The tick inside it — half the disc, so the mark reads at arm's length. */
    val outcomeGlyph: Dp = 38.dp,
    /** The act's cover thumbnail on the confirm and confirmed cards. */
    val actThumb: Dp = 62.dp,
    /** Inset of a grouped card (the act card, the calendar card, the invoice). */
    val cardPad: Dp = 18.dp,
    /** Inset of an accent-washed note block — tighter than a card on purpose. */
    val notePad: Dp = 13.dp,
    /** The counter sheet's amount well: taller than a field, it IS the screen. */
    val amountField: Dp = 60.dp,
    /** The brief's free-text box — three lines before it has to scroll. */
    val notesField: Dp = 92.dp,
)

/** Bundle handed to the theme so composables read `AppTheme.dimens.space.lg`. */
data class Dimens(
    val space: Space = Space(),
    val radii: Radii = Radii(),
    val size: Size = Size(),
    val component: Components = Components(),
    val aspect: AspectRatios = AspectRatios(),
    val fraction: Fractions = Fractions(),
    val chrome: Chrome = Chrome(),
    val hero: Hero = Hero(),
    val dashboard: Dashboard = Dashboard(),
    val pressKit: PressKit = PressKit(),
    val funnel: Funnel = Funnel(),
)
