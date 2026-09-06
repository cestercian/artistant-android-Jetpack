<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# DSYS — Design system layer (`designsystem/component/` ×47, `designsystem/theme/` ×8, `Haptics.kt`, `res/`)

Source root for all paths: `app/src/main/java/in/artistant/app/` unless prefixed `app/src/main/res/`.
Every line number below was confirmed with `grep -n` / `awk` against the file on disk.

## Coverage
| Component file | Reviewed | Findings |
|---|---|---|
| theme/Color.kt | yes | none (palette matches §2/§4 exactly; `surface3` #F6F7F3 is one of the two values §2 permits) |
| theme/Type.kt | yes | F-DSYS-01, F-DSYS-02, F-DSYS-10, F-DSYS-11, F-DSYS-18, F-DSYS-20 |
| theme/Dimens.kt | yes | F-DSYS-13, F-DSYS-16, F-DSYS-18 |
| theme/Motion.kt | yes | none (durations 100/200/300/400, reduce-motion resolved from all three `Settings.Global` scales) |
| theme/ArtistantTheme.kt, Role.kt, SystemBars.kt | yes | none |
| theme/ArtistGradient.kt | yes | already filed by lead (F-DS-01) |
| Headers.kt | yes | F-DSYS-15, F-DSYS-16, F-DSYS-17 |
| DetailHeader.kt | yes | F-DSYS-15, F-DSYS-16 |
| SectionHeader.kt | yes | F-DSYS-17 |
| PrimaryButton.kt | yes | F-DSYS-19 |
| Chip.kt | yes | F-DSYS-06, F-DSYS-09, F-DSYS-17 |
| SegmentedControl.kt | yes | F-DSYS-06, F-DSYS-09 |
| SearchBar.kt | yes | F-DSYS-06 |
| ListRow.kt | yes | F-DSYS-02 |
| SwitchRow.kt | yes | F-DSYS-02 (M3 Switch colours are fully mapped — no finding there) |
| Tile.kt | yes | none (18 radius / 14.5 name / 12.5 ink4 meta = §2 exactly; it is the reference the others miss) |
| Pill.kt / StatusPill.kt | yes | F-DSYS-07 |
| Banner.kt | yes | F-DSYS-03, F-DSYS-04, F-DSYS-14, F-DSYS-17 |
| AccentNote.kt | yes | F-DSYS-04, F-DSYS-17 |
| Toast.kt | yes | F-DSYS-03 (F-CC-06 already filed the shadow) |
| EmptyState.kt | yes | F-DSYS-20 (F-CC-14 already filed the `ink4` body) |
| SendingNarration.kt | yes | F-DSYS-21 |
| Avatar.kt | yes | F-DSYS-08, F-DSYS-17 |
| MonthCalendar.kt | yes (signatures + fill/precedence/legend) | F-DSYS-03, F-DSYS-11, F-DSYS-22 |
| Meter.kt | yes | F-DSYS-13, F-DSYS-22 |
| SampleRow.kt | yes | F-DSYS-12 |
| OtpField.kt | yes | F-DSYS-19 |
| AppTextField.kt | yes | F-DSYS-17, F-DSYS-19 |
| LightTabBar.kt | yes | F-DSYS-05 |
| SheetScaffold.kt | yes | F-DSYS-20 |
| ComponentGallery.kt | yes | F-DSYS-18 |
| DockSurface.kt | yes | F-DSYS-18 (compat alias `bgElev` at :35) |
| HRule.kt | yes | none |
| IconCircle.kt | yes | F-DSYS-16 |
| HeroCard.kt | yes | F-DSYS-17 |
| CheckList.kt, DashedSlot.kt, EventTimeline.kt | yes | none |
| PressFeedback.kt, RevealOnAppear.kt | yes | none (`graphicsLayer{}` lambda used correctly — the counter-example to F-CC-04) |
| Skeleton.kt | yes | already filed (F-CC-04) |
| BottomDarkenScrim.kt | yes | none (on media, per §4) |
| TrustedTick.kt | yes | none |
| ArtistTile.kt, CardView.kt, DateScroller.kt, MiniBars.kt, ScoreRing.kt, ScreenTitleBar.kt, Sparkline.kt, StatusTimeline.kt, BottomActionBar.kt | yes | F-DSYS-18 (confirms/corrects F-CC-11) |
| designsystem/Haptics.kt | yes | F-DSYS-18 |
| res/font/ (3 TTF), values/colors.xml, values/themes.xml, drawable/ic_notification.xml, drawable/ic_launcher_*.xml, mipmap-anydpi-v26/ | yes | none — all correct, see observations |

---

## Findings

### F-DSYS-01 — `caption` ships Medium +0.5sp for a reason that covers 1 of 237 sites
- Component: `theme/Type.kt`; every component that draws meta
- Where: `designsystem/theme/Type.kt:181-194`
- Category: token
- Severity: P1
- Rule: (a) §2 type table, "caption | 12.5 / 400, `ink4`"
- What: §2 sets the caption at **12.5/400 with no tracking**. The token ships at
  **Medium (500) with +0.5sp letter-spacing**, and the KDoc justifies it because "the same
  token still backs the app's remaining ALL-CAPS section labels". That reason is dead:
  `type.caption` has **237 call sites and exactly one of them uppercases**. So 236 blocks of
  lowercase meta copy — every `ListRow` subtitle, every `BackHeader`/`DetailHeader` subtitle,
  every `Banner` detail, every `Pill`, every `Tile` meta, every error line under a field —
  render a weight heavier and half a point wider than the design draws them. On a hairline
  design where meta is supposed to recede, the whole small-type layer sits forward.
- Evidence:
  ```kotlin
  // Type.kt:191
  val caption: TextStyle = TextStyle(
      fontFamily = SansFamily, fontSize = 12.5f.sp,
      fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp,
  ),
  ```
- Fix: drop `caption` to `FontWeight.Normal` / `letterSpacing = 0.sp` per §2 and move the one
  uppercase call site to `monoLabel` (which the KDoc already names as the correct answer).

### F-DSYS-02 — Three sizes ship for "the name of a thing": 15, 15, 14.5
- Component: `ListRow.kt`, `SwitchRow.kt`, `Tile.kt`
- Where: `designsystem/component/ListRow.kt:89`; `designsystem/component/SwitchRow.kt:72`;
  `designsystem/component/Tile.kt:110`
- Category: token
- Severity: P2
- Rule: (a) §2 geometry, "list row … name 14.5/600"; (b) peer `Tile.kt:110` uses the token unmodified
- What: §2 gives one row/tile name step — `rowTitle`, 14.5/600. `Tile` uses it as published.
  `ListRow` and `SwitchRow` both override it to the **body size (15sp)** with an uncommented
  `.copy(fontSize = AppTheme.type.body.fontSize)`, inventing a 15/600 step that is not on the
  ramp. A settings list and an artist rail therefore set the same kind of label at two sizes,
  and the ramp's own `rowTitle` no longer means what it is named for.
- Evidence:
  ```kotlin
  // ListRow.kt:89  (SwitchRow.kt:72 is identical)
  style = AppTheme.type.rowTitle.copy(fontSize = AppTheme.type.body.fontSize),
  // Tile.kt:110
  style = AppTheme.type.rowTitle,
  ```
- Fix: delete both `.copy(fontSize = …)` calls; if 15 is genuinely the drawn size, change
  `rowTitle` once in `Type.kt` so all three agree.

### F-DSYS-03 — `ink` is used as a surface fill in three components; `dark` is the palette's token for that
- Component: `Toast.kt`, `Banner.kt`, `MonthCalendar.kt`
- Where: `designsystem/component/Toast.kt:135` (+ text at `:163`);
  `designsystem/component/Banner.kt:218` (+ text at `:205`);
  `designsystem/component/MonthCalendar.kt:470` (+ text at `:474`)
- Category: token
- Severity: P2
- Rule: (a) §2 palette — `ink` = "primary text, icons", `dark` #16171a = "dark surfaces
  (splash, quote cards)"; deepens the lead's F-CC-06
- What: three separate components paint a **dark object** with the *text* token and then set
  their label with `onDark`, which is the pair defined for `dark`. The toast capsule, the
  banner's inline action pill, and the calendar's selected day are all near-black surfaces
  built from `#14150F` instead of `#16171A`. The mismatch is 2 units of luminance so nobody
  will see it on a screenshot — the cost is that the palette no longer tells you which token
  owns dark chrome, and a future retune of `dark` will move the splash and miss all three.
  It also means the calendar's *selection* is a black square while `accent` means "booked":
  the one signal is spent on a data state and the user's own choice gets ink.
- Evidence:
  ```kotlin
  // Toast.kt:135        .background(colors.ink)   … :163 color = colors.onDark
  // Banner.kt:218       else -> colors.ink,       … :205 else -> colors.onDark
  // MonthCalendar.kt:470  MonthDayFill.Selected -> colors.ink
  ```
- Fix: repoint all three fills to `colors.dark`; if the calendar's selected day should carry
  the accent instead, that is a separate call for the design owner.

### F-DSYS-04 — `AccentNote` and `Banner(BannerTone.Note)` draw the identical aside twice
- Component: `AccentNote.kt`, `Banner.kt`
- Where: `designsystem/component/AccentNote.kt:61-62`, `:121-122`, `:129-132`;
  `designsystem/component/Banner.kt:108`, `:115`, `:279-280`
- Category: consistency
- Severity: P2
- Rule: (b) two design-system components solving one problem with the same numbers
- What: both paint `accent` at **0.22** fill with an `accent` **0.60** hairline at
  `radii.buttonLg`. They are visually the same object — an accent-washed aside — but they
  disagree on everything else: `AccentNote` pads 12×12, tints the glyph `accentInk`, and sets
  its copy as `caption.copy(fontWeight = Normal, letterSpacing = body.letterSpacing,
  lineHeight = body.lineHeight)` (a hand-rebuilt body step); `Banner`'s Note pads 16×12, tints
  the glyph `accentDeep`, and sets `subtitle` in `ink2`. `AccentNote` has **26 call sites** and
  `Banner` is the one banner component, so which of the two a screen picked is arbitrary and
  the reader gets two slightly different asides on adjacent screens.
- Evidence:
  ```kotlin
  // AccentNote.kt:129   private const val FILL_ALPHA = 0.22f
  // AccentNote.kt:132   private const val STROKE_ALPHA = 0.6f
  // Banner.kt:279       private const val NOTE_FILL = 0.22f
  // Banner.kt:280       private const val NOTE_LINE = 0.60f
  ```
- Fix: delete `BannerTone.Note` and route its call sites to `AccentNote` (or the reverse) —
  one accent-wash aside with one inset and one type step.

### F-DSYS-05 — The tab bar's own arithmetic contradicts its KDoc by 24dp; it ships 15–39dp over §2's 88
- Component: `LightTabBar.kt`
- Where: `designsystem/component/LightTabBar.kt:98-104` (the claim), `:190` (the cell),
  `:287-291` (the measurement); tokens at `theme/Dimens.kt:406,408,412`
- Category: token
- Severity: P1
- Rule: (a) §2 geometry, "tab bar | height 88 (incl. home-indicator zone)"; (c) the two
  arithmetics in the same file do not agree
- What: the KDoc derives the design's 88 as `barTopPad + tabIcon + barBottomPad` = 14 + **24**
  + 16 = 54 of content plus a 34dp indicator zone. The code does not build the row out of the
  24dp glyph — `TabGlyph` lays out `Modifier.size(controlMin)` (**48**), and
  `lightTabBarHeight()` says so explicitly ("a 48dp tap target, not the 24dp glyph"). Real
  height is therefore `1 + 14 + 48 + 16` = **79dp of content** plus the system inset:
  ~103dp on gesture navigation, ~127dp on three-button. The global chrome on all four tab
  roots is 15–39dp taller than the design, and the toast host that composes off the same
  function inherits the error. The stated "lands on ~88" is only true for a device with a
  9dp navigation inset, which does not exist.
- Evidence:
  ```kotlin
  // LightTabBar.kt:190      Modifier.size(AppTheme.dimens.size.controlMin)   // 48, not tabIcon 24
  // LightTabBar.kt:287-291
  return AppTheme.dimens.size.hairline + chrome.barTopPad +
      AppTheme.dimens.size.controlMin + chrome.barBottomPad + systemNavigationInset()
  ```
- Fix: pick one — either cut `barTopPad`/`barBottomPad` so `hairline + pads + 48 + inset`
  lands on the design's band, or keep the 48 cell and correct the KDoc so nobody re-derives
  the wrong number. Do not leave the file asserting two different heights.

### F-DSYS-06 — The three most-tapped small controls are 36, 36 and 20dp
- Component: `Chip.kt`, `SegmentedControl.kt`, `SearchBar.kt`
- Where: `designsystem/component/Chip.kt:87-90`; `designsystem/component/SegmentedControl.kt:101`;
  `designsystem/component/SearchBar.kt:88-91`
- Category: slow
- Severity: P1
- Rule: (c) objective — no `sizeIn(minHeight = rowMin)` / `minimumInteractiveComponentSize()`
  on any of the three; peers `IconCircle.kt:74-76`, `SectionHeader.kt:68`, `Banner.kt:211`,
  `SampleRow.kt:166-168` and `LightTabBar.kt:190` all grow the node to the floor
- What: a `Chip` is a 13.5sp line with 9dp of vertical padding — about **36dp** tall — and it
  is the filter rail on Discover, Search and Messages, i.e. the most-tapped control in the
  product. A `SegmentedControl` segment is composed to 36dp with a comment saying so. The
  search bar's clear "×" applies `clickable` *before* `.size(iconLg)`, so its whole tap node
  is **20dp**. Every other pressable in this design system already knows the trick — five
  components grow an invisible node to `controlMin` around a smaller visual — so this is
  three misses in a codebase that otherwise gets it right, not a house style.
- Evidence:
  ```kotlin
  // SearchBar.kt:88-91 — clickable is applied above size(20), so the node IS 20dp
  .clip(RoundedCornerShape(dimens.radii.sm))
  .clickable(role = Role.Button, onClick = onClear)
  .size(dimens.size.iconLg)
  // Chip.kt:87-90 — 9dp padding on a 13.5sp line, no minimum
  .padding(horizontal = dimens.component.chipPadH, vertical = dimens.component.chipPadV)
  ```
- Fix: wrap each in `Modifier.sizeIn(minHeight = dimens.size.rowMin)` (and `minWidth` for the
  clear glyph) outside the `clip`, exactly as `SectionHeader.kt:68` does — the visible chip
  stays 36, the target becomes 44.

### F-DSYS-07 — Two capsule vocabularies for status, with two tone ladders and two type steps
- Component: `Pill.kt`, `StatusPill.kt`
- Where: `designsystem/component/Pill.kt:25`, `:50`, `:62`, `:80-86`;
  `designsystem/component/StatusPill.kt:30`, `:76`, `:88`
- Category: consistency
- Severity: P2
- Rule: (b) same concept, two components — `Pill` has **30** call sites, `StatusPill` **8**
- What: the system publishes two status capsules that disagree on tone set, type and
  geometry. `Pill` has `Neutral/Brand/BrandSolid/Good/Warm/Hot`, sets sans `caption`, pads
  12×4 (~21dp tall) and owns `bookingStatusTone()`, the app-wide status mapping. `StatusPill`
  has `Live/Pending/Failed/Done/Neutral`, sets **mono, uppercased** `monoPill`, pads 12×6 and
  carries a 7dp dot. Add `Chip` (16×9, 13.5 sans) and there are three capsule heights and
  three type registers for "a short word in a rounded box". A booking that shows a `Pill` on
  one screen and a `StatusPill` on the next reads as two different systems.
- Evidence:
  ```kotlin
  // Pill.kt:25        enum class PillTone { Neutral, Brand, BrandSolid, Good, Warm, Hot }
  // Pill.kt:50,62     style = AppTheme.type.caption … padding(horizontal = space.md, vertical = space.xs)
  // StatusPill.kt:30  enum class StatusTone { Live, Pending, Failed, Done, Neutral }
  // StatusPill.kt:76,88  padding(horizontal = space.md, vertical = space.xs + space.xs / 2) … type.monoPill
  ```
- Fix: keep `StatusPill` for machine states (it is the one the design's dot-plus-mono note
  describes) and reduce `Pill` to the non-status label it also serves, or fold `PillTone`'s
  status members into `StatusTone` and let `bookingStatusTone()` return the one enum.

### F-DSYS-08 — `Avatar` mints 360 saturated hues on a one-accent palette
- Component: `Avatar.kt`
- Where: `designsystem/component/Avatar.kt:101-113`
- Category: token
- Severity: P2
- Rule: (a) golden rule 2 ("never a raw hex") and §2 "one accent for both roles… the one
  signal"
- What: an avatar without a photo is filled with a DJB2 hash → HSV gradient at
  `s = 0.55/0.70, v = 0.55/0.40`. On the retired dark design that read as a neutral tile; on
  a near-white page with a single lime accent, every photo-less avatar is a **saturated,
  arbitrary colour disc** — a purple monogram beside a lime CTA, a different one per name.
  The values are raw `Color.hsv(…)`, not tokens, and the KDoc's justification ("matching iOS
  `Avatar.gradient`") points at the design that was replaced in Sep 2026. Avatars appear in
  every thread row, every booking card and every review, so this is the most-repeated
  off-palette colour in the app.
- Evidence:
  ```kotlin
  // Avatar.kt:108-113
  return Brush.linearGradient(
      colors = listOf(
          Color.hsv(hue * 360f, 0.55f, 0.55f),
          Color.hsv(hue2 * 360f, 0.70f, 0.40f),
      ),
  )
  ```
- Fix: replace the hue hash with the palette — `surface2` fill and `ink2` initials (or a
  `placeholder` fill), which is what the light design draws for an absent image everywhere
  else (`Tile.kt:103`, `MediaSlot`).

### F-DSYS-09 — Two selection languages for "pick one of N"; one paints a line token as a fill
- Component: `Chip.kt`, `SegmentedControl.kt`
- Where: `designsystem/component/Chip.kt:53-57`; `designsystem/component/SegmentedControl.kt:87-91`
- Category: consistency
- Severity: P2
- Rule: (a) §2, "`accent` … the one signal: primary CTA, **selected chip**, badges";
  (b) `Chip.kt:54` solves the same problem with the accent
- What: a selected `Chip` fills with `accent`. A selected `Segment` fills with
  **`colors.hairline`** — the divider token — over a `surface3` track, so selection reads as a
  faintly darker grey lozenge. Both controls are "choose one of a small set"; a user who
  learns the accent means "this one is on" gets no such signal from the segmented control.
  Using `hairline` as an area fill also breaks the token's role: it is defined as "dividers
  and card strokes".
- Evidence:
  ```kotlin
  // Chip.kt:54            targetValue = if (selected) colors.accent else colors.surface2,
  // SegmentedControl.kt:88 targetValue = if (selected) colors.hairline else Color.Transparent,
  ```
- Fix: paint the selected segment `surface` (a raised white pill on the `surface3` track,
  which is the usual light-design answer) or the accent — either is a stated token; a line
  colour is not.

### F-DSYS-10 — The app's helper/meta step is `footnote` = `chip` at **Medium**; §2's meta is 400
- Component: `theme/Type.kt`
- Where: `designsystem/theme/Type.kt:282` (alias), `:197-199` (target), `:179` (the correct step)
- Category: token
- Severity: P2
- Rule: (a) §2 type table — "subtitle / meta | 13.5 / **400**", "chip | 13.5 / 500"
- What: `footnote` still has **48 call sites** and points at `chip`, which is 13.5/**500**
  because a chip label needs the weight. Helper text and meta lines that reach for `footnote`
  therefore set a half-step heavier than the design's 13.5/400 `subtitle`, which sits right
  next to it on the ramp at the same size. Combined with F-DSYS-01 this means *both* of the
  app's small-text steps ship at Medium and neither matches §2.
- Evidence:
  ```kotlin
  // Type.kt:179  val subtitle: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 13.5f.sp)   // 400 ✓
  // Type.kt:197  val chip: TextStyle = TextStyle(… fontSize = 13.5f.sp, fontWeight = FontWeight.Medium)
  // Type.kt:282  val footnote: TextStyle = chip,
  ```
- Fix: repoint `footnote` at `subtitle` (same size, correct weight) — a one-line change that
  fixes all 48 sites without touching them — then retire the alias as the sections convert.

### F-DSYS-11 — The calendar sets its day numerals in the sans while `monoDay` exists for exactly that
- Component: `MonthCalendar.kt`, `theme/Type.kt`
- Where: `designsystem/component/MonthCalendar.kt:517-521` vs `:420`;
  `designsystem/theme/Type.kt:346-349`
- Category: token
- Severity: P2
- Rule: (a) §2, "JetBrains Mono for eyebrow labels **and numerals**"; (b) the same component
  sets its weekday strip in mono at `:420`
- What: `Type.kt:347` publishes `monoDay` with the KDoc "**Calendar: a day numeral in the
  grid**". `DayTile` ignores it and sets `type.chip.copy(fontWeight = …)` — the sans filter-chip
  step — for every date, while the M T W T F S S strip 100 lines above it *does* use
  `monoWeekday`. The grid therefore reads as mono letters over proportional digits, and the
  numerals do not column-align down the weeks, which is the whole reason a calendar is set in
  mono. `monoDay` has exactly **1** call site in the app and it is not here.
- Evidence:
  ```kotlin
  // MonthCalendar.kt:420   style = AppTheme.type.monoWeekday,      // the letter row: mono
  // MonthCalendar.kt:519   style = AppTheme.type.chip.copy(        // the day numerals: sans
  //                            fontWeight = if (marked || isToday) FontWeight.Bold else FontWeight.Medium,
  //                        ),
  ```
- Fix: set `DayTile`'s numeral in `type.monoDay` (bolding via `.copy` only if the design draws
  a heavier marked day).

### F-DSYS-12 — `SampleRow` is the one off-token component: raw 36/18dp and alpha-mixed greys
- Component: `SampleRow.kt`
- Where: `designsystem/component/SampleRow.kt:184`, `:187`, `:201`, `:222`, `:228`
- Category: token
- Severity: P2
- Rule: (a) golden rule 2 — "use the design tokens … **never** a raw hex/dp/sp"; §2 palette
  has no 8%/12% ink tints
- What: the play disc is a literal `36.dp`, its glyph a literal `18.dp`, its rest fill
  `colors.ink.copy(alpha = 0.08f)` and the playback track `colors.ink.copy(alpha = 0.12f)` —
  two greys manufactured by alpha rather than taken from the surface ladder (`surface2`
  #F1F2EC and `hairline` #E6E8DF are exactly these jobs). It also reaches for the retired
  compat aliases `colors.brand` and `colors.bg` for the playing state. Per the repo sweep
  (`sweeps/raw_units.txt`) these are the only raw `dp` literals left in a live design-system
  component — `StatusTimeline`, `ScoreRing`, `Sparkline` and `ArtistTile` also carry them but
  are dead (F-DSYS-18).
- Evidence:
  ```kotlin
  // SampleRow.kt:184-187
  .size(36.dp)
  .pressScale(interaction)
  .clip(CircleShape)
  .background(if (playing) colors.brand else colors.ink.copy(alpha = 0.08f)),
  // SampleRow.kt:201    modifier = Modifier.size(18.dp),
  // SampleRow.kt:222    .background(colors.ink.copy(alpha = 0.12f)),
  ```
- Fix: add a `component.playDisc` (36) / reuse `size.iconLg`-family token for the glyph, and
  swap the two alpha mixes for `surface2` / `hairline` and the aliases for `accent` / `page`.

### F-DSYS-13 — One shared `Meter`, used by one feature; four other bar geometries hand-rolled
- Component: `Meter.kt`, `theme/Dimens.kt`
- Where: `designsystem/component/Meter.kt:82-97`; `theme/Dimens.kt:505` (`meterHeight` 3),
  `:563` (`pressKit.meter` 6), `:565` (`uploadMeter` 5); consumers at
  `feature/artisthome/ArtistHomeScreen.kt:668`, `feature/wizard/WizardMediaSteps.kt:799,806`,
  `feature/wizard/WizardScaffold.kt:136`, `feature/epk/EpkHub.kt:95,214`
- Category: consistency
- Severity: P2
- Rule: (b) five progress bars, five geometries, one shared component ignored by four of them
- What: the design system ships `Meter` (label + mono value + a 6dp accent bar), and **all 8
  of its call sites are in `feature/score/`**. Everywhere else a progress bar is drawn inline
  from a different token: 3dp (`dashboard.meterHeight`, wizard + artist home), 4.5dp
  (`meterHeight + meterHeight/2`, artist home), 5dp (`pressKit.uploadMeter`) and 6dp
  (`pressKit.meter`). The same "how far along" object is four thicknesses across the product.
  `Meter` itself also takes its bar height from `size.dot` (6) — a token named for a dot.
- Evidence:
  ```kotlin
  // Meter.kt:85            .height(dimens.size.dot)                 // 6
  // ArtistHomeScreen.kt:668 .height(dimens.dashboard.meterHeight + dimens.dashboard.meterHeight / 2)  // 4.5
  // WizardMediaSteps.kt:799 .height(dimens.dashboard.meterHeight)   // 3
  // EpkHub.kt:214           .height(dimens.pressKit.uploadMeter)    // 5
  ```
- Fix: give `Meter` a `thickness` parameter defaulted to one token (`component.meter`), route
  the five inline bars through it, and delete the three redundant Dimens entries.

### F-DSYS-14 — `Banner` mixes Outlined and Filled glyphs inside one `when`
- Component: `Banner.kt`
- Where: `designsystem/component/Banner.kt:127-131`
- Category: consistency
- Severity: P2
- Rule: (b) one expression, two icon families; deepens the lead's F-CC-02
- What: the lead filed the app-wide 225/29/52 Filled-vs-Outlined split. This is the sharpest
  instance and it is inside a single design-system component: three of the five tones default
  to `Icons.Outlined.Info` and the other two to `Icons.Filled.WarningAmber` /
  `Icons.Filled.ErrorOutline`. So an info banner and a warning banner stacked on the same
  screen carry a hairline glyph and a solid glyph at the same 20dp — the exact contrast the
  §2 "hairlines, no card chrome" language is trying to avoid. (`Icons.Filled.ErrorOutline` is
  also the outline artwork under a `Filled` name, so the file is inconsistent with itself
  about what "Filled" buys.)
- Evidence:
  ```kotlin
  // Banner.kt:127-131
  val glyph = icon ?: when (tone) {
      BannerTone.Info, BannerTone.Note, BannerTone.Promotion -> Icons.Outlined.Info
      BannerTone.Attention -> Icons.Filled.WarningAmber
      BannerTone.Failure -> Icons.Filled.ErrorOutline
  }
  ```
- Fix: take all five from `Icons.Outlined` (`Info`, `WarningAmber`, `ErrorOutline` all exist
  there) — one family for one component is the cheapest possible start on F-CC-02.

### F-DSYS-15 — Three headers, three bar heights, none of them §2's 56 — and two subtitle steps
- Component: `Headers.kt`, `DetailHeader.kt`
- Where: `designsystem/component/Headers.kt:47-51` (no min height), `:111`, `:63`, `:136`;
  `designsystem/component/DetailHeader.kt:38-40`, `:56`, `:77`
- Category: token
- Severity: P3
- Rule: (a) §2 geometry, "header | **56 tall**"; (b) `ScreenHeader` and `BackHeader` set the
  same subtitle role at two different steps
- What: `BackHeader` and `DetailHeader` both floor at `size.controlMin` (**48**), and
  `ScreenHeader` sets no minimum at all — its height is whatever the 26sp title measures.
  `DetailHeader`'s own KDoc argues from "the design's 56dp bar" while the code two lines below
  writes 48, so the file states the rule and misses it. Separately, `ScreenHeader` draws its
  subtitle in `type.subtitle` (13.5) while `BackHeader` and `DetailHeader` draw theirs in
  `type.caption` (12.5, and Medium per F-DSYS-01) — the same second line, two sizes and two
  weights depending on which header the screen happened to use.
- Evidence:
  ```kotlin
  // Headers.kt:63   style = AppTheme.type.subtitle,     // ScreenHeader's second line — 13.5/400
  // Headers.kt:111  .defaultMinSize(minHeight = dimens.size.controlMin)   // 48, not 56
  // Headers.kt:136  style = AppTheme.type.caption,      // BackHeader's second line — 12.5/500
  // DetailHeader.kt:77  style = AppTheme.type.caption,
  ```
- Fix: add a `component.header = 56.dp` token, floor all three on it, and pick one subtitle
  step (§2's `subtitle`) for all three.

### F-DSYS-16 — The header back circle is 42 on 22 screens and 40 on 35 call sites
- Component: `IconCircle.kt`, `Headers.kt`, `DetailHeader.kt`, `theme/Dimens.kt`
- Where: `designsystem/theme/Dimens.kt:227` (`iconCircle` 42), `:229` (`iconCircleSm` 40);
  `designsystem/component/IconCircle.kt:50`; `designsystem/component/Headers.kt:114-118`;
  `designsystem/component/DetailHeader.kt:64`
- Category: consistency
- Severity: P3
- Rule: (a) §2 geometry, "icon circle (header actions) | **42**"; (b) 35 call sites pass 40
- What: `BackHeader` takes `IconCircle`'s 42 default and has **22** call sites. Against that,
  `component.iconCircleSm` (40) is passed explicitly at **35** sites — `DetailHeader`,
  `WizardScreen`, `BookingScreen`, `BookingDetailScreen`, `InvoiceScreen`, `BookingChrome`,
  `Skeleton` — while the literal `component.iconCircle` token appears at only **2**. So the
  design's 42 is reached almost entirely by accident (the default), the majority of pushed
  screens draw 40, and a user paging between a `BackHeader` screen and a `DetailHeader` screen
  sees the back button change size. `DetailHeader` documents a real reason for its own 40
  (two lines of text between the circles); the other 28 sites do not.
- Evidence:
  ```kotlin
  // Dimens.kt:227-229
  val iconCircle: Dp = 42.dp,     // §2's header action circle — 2 literal call sites
  val iconCircleSm: Dp = 40.dp,   // 35 call sites
  ```
- Fix: audit the 35; anything that is a plain header back/action circle should drop the
  `size =` argument and inherit 42, leaving `iconCircleSm` to the two-line `DetailHeader` case
  its KDoc describes.

### F-DSYS-17 — The ramp has no weight variants, so ten components mint one with `.copy(fontWeight=…)`
- Component: 10 files
- Where: `SectionHeader.kt:61`, `:121`; `Banner.kt:178`, `:198`; `AppTextField.kt:105`, `:209`;
  `AccentNote.kt:80-83`, `:89-92`; `Toast.kt:162`; `HeroCard.kt:211`; `Avatar.kt:64-67`;
  `Meter.kt:77`; `SendingNarration.kt:179-185`; `MonthCalendar.kt:519`, `:703`;
  `Chip.kt:70-72`; `SegmentedControl.kt:114-116`; `StatusTimeline.kt:81`; `DateScroller.kt:199`
- Category: token
- Severity: P3
- Rule: (a) rubric — "`.copy(fontWeight=…/fontSize=…/letterSpacing=…)` inventing a new step"
- What: `AppType` publishes one weight per step, so every component that needs "the caption
  but bold" or "the subtitle but semibold" builds it inline. There are **19 such call sites
  across 12 design-system files**, producing at least six undeclared steps: 13.5/600
  (`SectionHeader` action), 13.5/700 (`Banner` title), 12.5/700 (`GroupLabel`, `Banner`
  action), 12.5/600 (`AppTextField` label), 12.5/400-with-body-metrics (`AccentNote` body) and
  15/600 (F-DSYS-02). Two of them also override `letterSpacing`/`lineHeight` to undo
  F-DSYS-01's tracking locally, which is the tell that the base step is wrong.
- Evidence:
  ```kotlin
  // AccentNote.kt:89-92 — rebuilding body out of caption, one call site at a time
  style = AppTheme.type.caption.copy(
      fontWeight = FontWeight.Normal,
      letterSpacing = AppTheme.type.body.letterSpacing,
      lineHeight = AppTheme.type.body.lineHeight,
  ),
  ```
- Fix: after F-DSYS-01 lands, publish the three genuinely repeated variants as named steps
  (`captionStrong`, `subtitleStrong`, `groupLabel`) and delete the inline copies; leave only
  `Chip`/`Segment`'s selected-weight swap, which is a state, not a step.

### F-DSYS-18 — Dead surface: an unreachable gallery, 10 unused type steps, 2 unused haptics, 1 unused token
- Component: `ComponentGallery.kt`, `theme/Type.kt`, `theme/Dimens.kt`, `Haptics.kt`,
  the nine files in F-CC-11
- Where: `designsystem/component/ComponentGallery.kt:38`; `theme/Type.kt:245,284,286,314,339,
  343,390,392,394,396`; `theme/Dimens.kt:481`; `designsystem/Haptics.kt:95,98`;
  `designsystem/component/DateScroller.kt:85,109,116,138`
- Category: slop
- Severity: P3
- Rule: (c) zero call sites, verified by grep; corrects and extends the lead's F-CC-11
- What: three corrections and one addition to F-CC-11.
  **(1) `ComponentGallery` has zero callers** — no route, no debug entry, nothing. The one
  artefact that documents the design system cannot be opened, which is why the drift in this
  report went unseen; it also covers only ~18 of the 47 components (no `Avatar`, `SwitchRow`,
  `SegmentedControl`, `Meter`, `AccentNote`, `SheetScaffold`, `MonthCalendar`, `BackHeader`,
  `DetailHeader`, `SendingNarration`).
  **(2) `CardView` is called exactly once — from `ComponentGallery.kt:136`**, which is itself
  dead, so F-CC-11 is right that it is unreachable; worth recording that removing the gallery
  removes its last reference.
  **(3) `DateScroller` is confirmed dead**: it defines `DateCell(` and three `dateChipLines(`
  overloads, and all four have zero call sites outside the file (this closes F-CC-11's open
  question about the name). `ArtistTile` likewise has zero real callers — its only hit is a
  prose mention in `Dimens.kt:474`.
  **(4) New**: ten published type steps have zero call sites (`displayTitle`, `railLabel`,
  `statLabel`, `scoreRing`, `monoChip`, `monoYear`, `monoMicroSoft`, `heroMeta`, `frameMeta`,
  `heroStatus`), `Hero.autoAdvanceMillis` (6 s) has no pager to advance, and `Haptics.impact()`
  / `Haptics.heavy()` are never fired (the live set is 12 `success`, 10 `tap`, 7 `select`,
  4 `warning`, 3 `error`).
- Evidence:
  ```
  $ grep -rn "ComponentGallery" --include=*.kt . | grep -v component/ComponentGallery.kt   → (none)
  $ grep -rn "DateCell(\|dateChipLines(" --include=*.kt . | grep -v component/DateScroller.kt → (none)
  $ grep -rno "type.scoreRing\|type.monoChip\|type.monoYear\|type.heroStatus" --include=*.kt . → 0
  ```
- Fix: either wire `ComponentGallery` to a debug-harness route and finish it, or delete it
  with the nine dead components; drop the ten dead type steps and the two dead haptics in the
  same sweep.

### F-DSYS-19 — `OtpField` draws a 1.5dp stroke at rest; every other input draws 1dp
- Component: `OtpField.kt`, `AppTextField.kt`
- Where: `designsystem/component/OtpField.kt:137`; `designsystem/component/AppTextField.kt:117-125`
- Category: token
- Severity: P3
- Rule: (b) peer `AppTextField.kt:118-122` switches thickness with focus; `component.focusStroke`
  is named for the focused state
- What: `AppTextField` uses `component.focusStroke` (1.5) only when focused or in error and
  falls back to `size.hairline` (1) at rest — which is the token's whole point.
  `OtpField`'s boxes pass `focusStroke` unconditionally, so six empty 60dp boxes sit on screen
  outlined at **1.5dp hairline** while the phone-number field directly above them (screen 118's
  flow) outlines at 1dp. Same design, same radius, visibly different edge weight.
- Evidence:
  ```kotlin
  // AppTextField.kt:117-125
  .border(
      width = if (focused || error != null) dimens.component.focusStroke else dimens.size.hairline,
      color = stroke, shape = shape,
  )
  // OtpField.kt:137
  .border(dimens.component.focusStroke, stroke, shape),
  ```
- Fix: mirror `AppTextField` — `if (active || isError) focusStroke else size.hairline`.

### F-DSYS-20 — Three undocumented display steps, and three sizes for "the title of the thing you just opened"
- Component: `theme/Type.kt`, `EmptyState.kt`, `SheetScaffold.kt`, `Headers.kt`
- Where: `theme/Type.kt:247-259`; `designsystem/component/EmptyState.kt:82`;
  `designsystem/component/SheetScaffold.kt:93`; `designsystem/component/Headers.kt:127`
- Category: hierarchy
- Severity: P3
- Rule: (a) §2's type table defines 30 / 26 / 18.5 / 17 / 15 / 14.5 / 13.5 / 12.5 / 11.5 / 11
  — it contains **no 24, 21 or 19**; the KDoc citations are to text that does not exist
- What: `Type.kt` inserts `displayMedium` 24, `displaySub` 21 and `displaySmall` 19 between
  §2's 26 and 17, and two of them carry a false citation — "Artist / person name at the top of
  a detail screen (**§2: 21/700**)" and "Name inside a header block (**§2: 19/700**)". §2 says
  neither. The consequence is that three peer components each pick a different one for the
  same job: a pushed screen's title is 17 (`BackHeader`), a sheet's title is 19
  (`SheetScaffold`), an empty state's title is 21 (`EmptyState`), and `SendingNarration`'s is
  24. Four sizes for "the heading of the thing that just appeared".
- Evidence:
  ```kotlin
  // Type.kt:251-252  /** Artist / person name at the top of a detail screen (§2: 21/700). */
  //                  val displaySub: TextStyle = TextStyle(… fontSize = 21.sp …)
  // EmptyState.kt:82     style = AppTheme.type.displaySub,   // 21
  // SheetScaffold.kt:93  style = AppTheme.type.displaySmall, // 19
  // Headers.kt:127       style = AppTheme.type.sectionTitle, // 17
  ```
- Fix: correct the two KDoc citations (they are the only §2 references in the file that do not
  resolve), then collapse the sheet/empty-state/narration titles onto one step.

### F-DSYS-21 — `SendingNarration` paints three accent-filled objects at once
- Component: `SendingNarration.kt`
- Where: `designsystem/component/SendingNarration.kt:79-92`, `:149-159`, `:164-169`
- Category: hierarchy
- Severity: P3
- Rule: (a) §2 principles, "one accent per screen"
- What: the narration screen fills a 72dp disc with `accent`, then fills a 20dp disc with
  `accent` for **every completed step**, then colours the running `CircularProgressIndicator`
  `accent` as well. A three-step send shows up to five lime objects simultaneously, all at
  different sizes, with nothing among them ranked highest — on a screen whose whole job is to
  say "this is happening, here is where it got to".
- Evidence:
  ```kotlin
  // SendingNarration.kt:83   .background(colors.accent),        // 72dp hero disc
  // SendingNarration.kt:150  .clip(CircleShape).background(colors.accent),  // per done step
  // SendingNarration.kt:166  color = colors.accent,             // the running ring
  ```
- Fix: keep the accent on the hero disc, draw done ticks as `accentInk` glyphs with no fill
  (the same substitution `StatusPill.kt:56` already makes), and let the running ring take
  `ink`.

### F-DSYS-22 — Two tokens used outside their role: a space as a radius, a dot as a bar height
- Component: `MonthCalendar.kt`, `Meter.kt`
- Where: `designsystem/component/MonthCalendar.kt:579`; `designsystem/component/Meter.kt:85`
- Category: token
- Severity: P3
- Rule: (a) golden rule 2 — use the token for the thing; `radii.xs` (5) exists for exactly the
  first case
- What: the calendar legend's swatch takes its corner radius from `dimens.space.xs` (a spacing
  token) rather than `dimens.radii.xs`, so retuning the app's 4dp gap silently reshapes the
  legend. `Meter`'s progress track takes its height from `dimens.size.dot` (6), a token named
  for the timeline/status dot, so retuning the dot resizes every score meter. Both are a
  4-character fix and both are the kind of coupling that turns a spacing tweak into a visual
  regression three screens away.
- Evidence:
  ```kotlin
  // MonthCalendar.kt:579  val shape: Shape = RoundedCornerShape(dimens.space.xs)
  // Meter.kt:85           .height(dimens.size.dot)
  ```
- Fix: `radii.xs` for the swatch; add `component.meter` (6) for the track (see F-DSYS-13).

---

## Section-wide observations

- **The palette is the healthiest layer in the app.** `Color.kt` matches §2 and §4 value for
  value; `surface3` #F6F7F3 is one of the two hexes §2 lists; `lineSoft` #ECEEE7 and
  `brandSoft` #F5FBDA match §4 exactly. The five "retired" glass tokens are all still used
  **on media only** — `BottomDarkenScrim`, `HeroCard:142`, `ArtistTile:224`, `StatusPill:65`
  (its `onMedia` branch), `CounterOfferScreen:244` and `ClientTabsScaffold:793` (over a
  full-bleed cover) — so the "retired" label is accurate and nothing paints a scrim on a light
  page. No component in the layer carries a `Modifier.shadow` except `Toast.kt:133` (already
  filed as F-CC-06), and no gradient exists off media except `ArtistGradient` (F-DS-01) and
  `Avatar` (F-DSYS-08).
- **Type is where the drift is.** Every finding about a *number* traces back to `Type.kt`:
  two of the three small-text steps ship at Medium (F-DSYS-01, F-DSYS-10), three display steps
  exist that §2 does not define and two cite it falsely (F-DSYS-20), and because the ramp
  publishes one weight per step, 19 call sites across 12 files mint their own (F-DSYS-17).
  Ten steps have no call sites at all. Fixing `caption` and `footnote` — two lines — moves
  285 call sites onto the design.
- **`res/` is clean and needs nothing.** All three variable TTFs are present
  (`plus_jakarta_sans_variable`, `plus_jakarta_sans_italic_variable`, `jetbrains_mono_variable`)
  and all three are declared across five weights with `variationSettings`, so the ramp is real
  rather than synthetic. `colors.xml`'s `page` #FAFAF6 matches `AppColors.page`; the dark
  launch window (#0F100C, `themes.xml`) is `darkest` and is documented as screen 01's "one dark
  room" with the Compose flip in `SystemBars.kt`; `ic_notification.xml` is a white 24dp alpha
  mark tinted `brand_lime` at `ArtistantMessagingService.kt:211`, which is the correct way to
  colour a small icon; the adaptive icon has a full-bleed `darkest` background, a lime "A"
  foreground and a separate opaque monochrome layer for API 33+ themed icons. No leftover
  dark-theme values anywhere in `values/`.
- **Six components already solve the tap-target problem correctly** — `IconCircle:74-76`,
  `SectionHeader:68`, `Banner:211`, `SampleRow:166-168`, `LightTabBar:190` and `MonthCalendar`'s
  `heightIn(rowMin)` all grow an invisible node around a smaller visual. That makes the three
  misses in F-DSYS-06 oversights rather than a house position, and gives the fix a pattern to
  copy verbatim.
- **Duplication clusters around "a small thing in a rounded box".** Three capsules
  (`Chip` 16×9, `Pill` 12×4, `StatusPill` 12×6) with three type registers, two accent-wash
  asides with identical alphas (F-DSYS-04), two status tone ladders (F-DSYS-07), three headers
  (F-DSYS-15) and five progress-bar thicknesses (F-DSYS-13). None of it is wrong in isolation;
  together it is why two adjacent screens can look like two products.
- **Motion and press feedback are correct and should be left alone.** `Motion.kt` resolves
  reduce-motion from all three `Settings.Global` scales and returns 0 rather than a smaller
  number; `PressFeedback.kt:61` reads the animated value inside a `graphicsLayer {}` lambda —
  which is exactly the fix F-CC-04 asks for in `Skeleton.kt`, so the correct implementation
  already exists in the same package.
- **The one thing that would have caught most of this is `ComponentGallery`,** and it has no
  callers (F-DSYS-18). A design system whose own catalogue cannot be opened drifts silently;
  wiring it to the debug harness alongside the existing flags is cheaper than any single fix
  in this report and would have surfaced the three capsule heights, the two accent-wash asides
  and the 42-vs-40 back circle on sight.
- **`SwitchRow` is the model for how an M3 component should be restyled here** — all twelve
  `SwitchDefaults.colors` slots are mapped to tokens, including the six disabled ones, so no
  M3 default can leak through in any state (§5 rule 7). It is worth citing as the reference
  when the remaining M3 surfaces are audited.
