<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

> **Note (lead):** this auditor omitted the per-finding Severity line. Severities were assigned by the lead from the auditor's own ordering and its reported counts (P1 / P2 / P3 = 4 / 9 / 7) and are marked below.

# AS — Artist studio (screens 09, 85, 86, 133, 36, 35, 107, 108, 109, 22, 105, 106)

## Coverage
| Screen | File(s) | Reviewed | Findings |
|---|---|---|---|
| 09 Studio dashboard | `feature/artisthome/ArtistHomeScreen.kt:203-299`, `ArtistStudioLogic.kt` | yes | F-AS-01, F-AS-02, F-AS-03, F-AS-15, F-AS-16, F-AS-17, F-AS-19, F-AS-20 |
| 85 Dashboard — cold | `ArtistHomeScreen.kt:305-357` | yes | F-AS-05, F-AS-19 |
| 86 Dashboard — unavailable | `ArtistHomeScreen.kt:363-434` | yes | F-AS-09, F-AS-10, F-AS-11 |
| 133 Earnings | `feature/artisthome/EarningsScreen.kt` | yes | F-AS-03, F-AS-05, F-AS-15, F-AS-17, F-AS-18, F-AS-19 |
| 36 Gigs | `feature/gigs/ArtistGigsScreen.kt` | yes | F-AS-02, F-AS-03, F-AS-05, F-AS-06, F-AS-19 |
| 35 Gig request detail | `feature/gigs/GigRequestDetailScreen.kt:341-360` | yes | F-AS-04, F-AS-12, F-AS-19 |
| 107 Countered | `GigRequestDetailScreen.kt:369-434` | yes | F-AS-19 (+ obs. 6) |
| 108 Declined | `GigRequestDetailScreen.kt:437-480` | yes | none (obs. 6) |
| 109 Not found | `GigRequestDetailScreen.kt:155-180` | yes | none |
| 22 Manage availability | `feature/availability/ManageAvailabilityScreen.kt` | yes | F-AS-02, F-AS-06, F-AS-07, F-AS-08, F-AS-13, F-AS-14 |
| 105 Availability preview | `ManageAvailabilityScreen.kt:187-226` | yes | F-AS-07, F-AS-08 |
| 106 Failed seed | `ManageAvailabilityScreen.kt:298-312`, `ManageAvailabilityViewModel.kt:141` | yes | F-AS-13 |

Screen 21 (Open gigs) is blocked — not flagged.

## Findings

### F-AS-01 — Screen 09 spends the one accent six ways at once
- Screens: 09
- Where: `feature/artisthome/ArtistHomeScreen.kt:593`; `:540`, `:545`; `:458` (→ `designsystem/component/Banner.kt:108`); `:677`; `:799`; `:912`
- Category: hierarchy
- Severity: P1 (assigned by the lead, see note)
- Rule: (a) §2 "Principles … one accent per screen"; and the file's own doc, `ArtistHomeScreen.kt:66-67` — "The accent card at the top is the only accent on the page" — which the code below it contradicts.
- What: In one viewport an artist sees a solid-lime money card, a lime-washed "Taking gigs" pill with a lime rim in the header directly above it, a lime-tinted profile-gaps banner between them (`BannerTone.Note` is `accent.copy(alpha = NOTE_FILL)`), and a lime score meter immediately below. Scroll on and up to fourteen booked strip cells are solid `accent` plus a lime rule on every Upcoming row. Nothing is the signal because everything is.
- Evidence:
  ```kotlin
  .background(colors.accent)                                             // :593 money card
  .background(if (taking) colors.accent.copy(alpha = PILL_FILL) …)       // :540 header pill
  tone = BannerTone.Note,                                                // :458 → accent tint
  .background(colors.accent)                                             // :677 score meter
  .background(if (booked) colors.accent else colors.surface3)            // :799 strip cell ×14
  .background(colors.accent)                                             // :912 upcoming rule
  ```
- Fix: keep the money card as the page's accent; render the header pill in `surface2`/`ink2` with an `accentInk` dot, the score meter and the booked strip cells in `ink`/`lineStrong`, and the Upcoming rule in `hairline`.

### F-AS-02 — Three different page-title treatments across five screens of one section
- Screens: 09, 36, 22, 133, 35
- Where: `ArtistHomeScreen.kt:132-151`; `feature/gigs/ArtistGigsScreen.kt:153-163`; `feature/availability/ManageAvailabilityScreen.kt:76-84`, `:106`; `feature/artisthome/EarningsScreen.kt:84-89`; `feature/gigs/GigRequestDetailScreen.kt:188-193`
- Category: consistency
- Severity: P1 (assigned by the lead, see note)
- Rule: (a) §2 "header 56 tall: title 26 + subtitle"; (b) `Headers.kt:39-43` `ScreenHeader(title, subtitle, trailing)` and `:86-104` `BackHeader` exist and two of the five screens use them.
- What: Studio uses `ScreenHeader` (26sp + subtitle + trailing slot). Gigs — the other artist tab root — hand-rolls a `Column` and sets its page title in `displaySub` (21/700, `Type.kt:252`), so two sibling tab roots have masthead titles 5sp apart. Manage availability hand-rolls the same 21sp title under a bare M3 `IconButton` (see F-CC-07). Earnings and Gig request use `BackHeader`. Four screens, three chromes.
- Evidence:
  ```kotlin
  Text("Gigs", style = AppTheme.type.displaySub, color = colors.ink)          // ArtistGigsScreen.kt:155
  Text("Availability", style = AppTheme.type.displaySub, color = colors.ink)  // ManageAvailabilityScreen.kt:106
  ```
- Fix: `ScreenHeader(title = "Gigs", subtitle = monthLine)` on 36; `BackHeader(title = "Availability", subtitle = …)` on 22.

### F-AS-03 — Money is mono in the heroes and three different sans steps in the rows
- Screens: 09, 133, 36
- Where: `ArtistHomeScreen.kt:601` vs `:881`, `:935`; `EarningsScreen.kt:146` vs `:303`; `ArtistGigsScreen.kt:304`; `GigRequestDetailScreen.kt:510`
- Category: token
- Severity: P1 (assigned by the lead, see note)
- Rule: (a) §2 "JetBrains Mono for eyebrow labels and numerals"; rubric §1 "money set in the sans".
- What: Every hero figure is `monoHero`; every row figure is Plus Jakarta Sans, and not even the same step — the request card's amount is `rowTitle.copy(Bold)`, the Upcoming row's fee two blocks below it is `footnote.copy(Bold)` (a compat alias for `chip`, `Type.kt:282`), the earnings row is `rowTitle.copy(Bold)`, the gig row is `rowTitle.copy(Bold)`. Two money figures on the same screen 09 are set in two different faces at two different sizes.
- Evidence:
  ```kotlin
  Text(formatInr(amountInr), style = AppTheme.type.rowTitle.copy(FontWeight.Bold))  // :881 request card
  Text(formatInr(booking.fee), style = AppTheme.type.footnote.copy(FontWeight.Bold)) // :935 upcoming row
  ```
- Fix: one mono money style (`monoPrice`/`monoSmall`) for every row figure in the section; `formatInr` already does the Indian grouping, so only the face changes.

### F-AS-04 — The clash warning scrolls away while Accept stays pinned
- Screens: 35
- Where: `GigRequestDetailScreen.kt:351-353` inside the scroll column `:194-227`; dock at `:229-265`
- Category: slow / hierarchy
- Severity: P1 (assigned by the lead, see note)
- Rule: (c) fact; and the screen's own doc, `:60-65` — "the screen's job is to put everything the artist needs in front of that one answer".
- What: `OpenBody` puts the "Calendar clash" banner last in a `verticalScroll` column that already carries a proposal hero card, up to six fact rows and the client's free-text message. The Accept button lives in a pinned dock and is therefore always reachable; the warning that is the entire reason this screen exists is not. On a long request the artist can tap the irreversible answer without the warning ever being on screen.
- Evidence:
  ```kotlin
  clashWarning(state.clashes)?.let { warning ->
      Banner(title = "Calendar clash", detail = warning, tone = BannerTone.Attention)
  }
  ```
- Fix: hoist the clash banner out of the scroll column and render it directly above the dock (inside `.dockSurface()`), so it is pinned with the button it is warning about.

### F-AS-05 — Three empty states in the section, none with an action
- Screens: 36, 133, 85
- Where: `ArtistGigsScreen.kt:142-146`; `EarningsScreen.kt:171-175`; `ArtistHomeScreen.kt:328-353`
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (a) §2 Principles "every empty state carries an action"; `designsystem/component/EmptyState.kt:31-40` says so of itself — "[actionLabel] is not decorative".
- What: "No gigs yet" and "Nothing agreed in this window" are `EmptyState` calls with `actionLabel` omitted, so both are dead ends. The cold dashboard's empty is not `EmptyState` at all — it is a hand-rolled `surface3` box with a bold `rowTitle` and a centred subtitle, a fourth empty shape in a section that already has three. The same file's *failure* state one screen over does carry "Try again" (`EarningsScreen.kt:102-107`), so failed and empty are the wrong way round on effort.
- Evidence:
  ```kotlin
  EmptyState(title = "No gigs yet",
      body = "Requests and confirmed gigs will show up on your calendar.",
      modifier = Modifier.align(Alignment.Center))                       // ArtistGigsScreen.kt:142
  ```
- Fix: `actionLabel = "Set your availability"` on 36, `actionLabel = "Change window"`/"See all time" on 133, and replace the hand-rolled cold block with `EmptyState(actionLabel = "Finish your profile")`.

### F-AS-06 — The same "couldn't load it" fact is drawn four different ways
- Screens: 09, 86, 133, 35, 36, 22
- Where: Banner + Retry at `ArtistHomeScreen.kt:159-167`, `:369-376`; `EarningsScreen.kt:128-134`; `GigRequestDetailScreen.kt:140-146`, `:215-221`. Bare text, no retry, at `ArtistGigsScreen.kt:187-192`; `ManageAvailabilityScreen.kt:133-139`; `ManageAvailabilityScreen.kt:287`
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) five peer call sites in the same section use `Banner(tone = Failure, actionLabel = "Retry")`.
- What: Gigs renders a failed refresh as a naked red `footnote` line floating between the calendar grid and the day heading, with no retry and no frame. Manage availability renders "couldn't load your booked nights" as a bare `danger` caption and its save error as a bare `colors.hot` footnote. Three of the section's six screens tell the artist a read failed without giving them the button the other three give them.
- Evidence:
  ```kotlin
  Text(msg, style = AppTheme.type.footnote, color = colors.danger,
      modifier = Modifier.padding(horizontal = dimens.component.gutter)) // ArtistGigsScreen.kt:187-192
  ```
- Fix: `Banner(tone = BannerTone.Failure, actionLabel = "Retry", onAction = …)` at all three sites.

### F-AS-07 — Manage availability hand-uppercases three sans eyebrows
- Screens: 22, 105
- Where: `ManageAvailabilityScreen.kt:188`, `:229`, `:260`
- Category: token
- Severity: P2 (assigned by the lead, see note)
- Rule: (a) §2 `monoLabel` = "JetBrains Mono 11 / 500 / +0.12em / uppercase"; rubric §1 "uppercase strings in a non-mono style"; (b) `EyebrowLabel` is used at `ArtistHomeScreen.kt:451`, `:598`, `EarningsScreen.kt:178`, `GigRequestDetailScreen.kt:398`, `:451`, `:470`, `:509`, `:578`.
- What: "HOW CLIENTS SEE YOU", "DAYS YOU PLAY" and "PREFERRED START TIMES" are literal uppercase strings set in `type.caption` — Plus Jakarta Sans 12.5 — coloured `ink3`. Eight peer eyebrows in the same section go through `EyebrowLabel`/`monoLabel`. Uppercase sans without the mono's tracking reads as shouting rather than as a label.
- Evidence:
  ```kotlin
  Text("HOW CLIENTS SEE YOU", style = AppTheme.type.caption, color = colors.ink3)  // :188
  Text("DAYS YOU PLAY", style = AppTheme.type.caption, color = colors.ink3)        // :229
  ```
- Fix: `EyebrowLabel("How clients see you")` etc. — the component uppercases at the call site.

### F-AS-08 — Selectable day/time chips are status `Pill`s: 28dp targets, pale-wash selection
- Screens: 22, 105
- Where: `ManageAvailabilityScreen.kt:239-255`, `:265-282`; `designsystem/component/Pill.kt:38`, `:62`; `designsystem/component/Chip.kt:41-71`
- Category: consistency / slow
- Severity: P2 (assigned by the lead, see note)
- Rule: (a) §2 "chip padding 9×16, radius 999", selected = accent; (c) `Pill.kt:62` pads `space.md × space.xs` = 12×4, so a chip is roughly 28dp tall against `size.rowMin` = 44.
- What: The screen's primary interaction — picking the days you play — is a `Pill` made tappable with `Modifier.toggleable`. `PillTone.Brand` is `brandSoft` (the accent at ~12%), so a *selected* day is a pale wash rather than the accent fill §2 and `Chip` both specify, and the target is ~28dp with no `minimumInteractiveComponentSize`. Worse, the identical component is read-only status on `ArtistGigsScreen.kt:311` and `GigRequestDetailScreen.kt:335`: in one section the same object is both a button and a label.
- Evidence:
  ```kotlin
  Pill(text = day, tone = if (on) PillTone.Brand else PillTone.Neutral,
      modifier = Modifier.toggleable(value = on, role = Role.Checkbox, …))  // :239-255
  PillTone.Brand -> colors.brandSoft to colors.accentDeep                   // Pill.kt:38
  ```
- Fix: use `Chip(text, selected, onClick)` for both grids and leave `Pill` to statuses.

### F-AS-09 — Screen 86 says "couldn't refresh" and "out of date" about a dashboard that never loaded
- Screens: 86
- Where: `ArtistHomeScreen.kt:370` (identical string at `:160`); `feature/artisthome/ArtistStudioLogic.kt:458`
- Category: copy
- Severity: P2 (assigned by the lead, see note)
- Rule: (a) §2 Principles "loading, empty and failed are three different screens and say which one they are"; the file's own doc `:69-75` defines Unavailable as "the state where no read has ever landed".
- What: The never-loaded screen reuses the stale-over-data screen's banner title verbatim, and its header subtitle reads "Some of this is out of date". Nothing on the page is out of date — every figure is an em-dash and the strip is grey. The artist is told their data is stale when in fact they have none.
- Evidence:
  ```kotlin
  Banner(title = "Couldn't refresh your dashboard", …)          // :370, same string as :160
  DashboardMode.Unavailable -> "Some of this is out of date"    // ArtistStudioLogic.kt:458
  ```
- Fix: "Couldn't load your dashboard" + subtitle "We haven't reached your bookings yet".

### F-AS-10 — Screen 86 stacks two banners and offers Retry twice
- Screens: 86
- Where: `ArtistHomeScreen.kt:368-377` and `:426-433`; header retry at `:138-143`
- Category: hierarchy
- Severity: P2 (assigned by the lead, see note)
- Rule: rubric §3 "banners stacked (two `Banner`s visible at once)"; (a) one action per state.
- What: The failure screen opens with a `Failure` banner carrying a Retry, ends with an `Attention` banner carrying the "we won't draw these days as open" sentence, and puts a third retry — a `Filled.Refresh` `IconCircle` — in the header. Three framed attention objects and two retry affordances on a page whose only content is four em-dashes.
- Evidence:
  ```kotlin
  Banner(title = "Couldn't refresh your dashboard", …, actionLabel = "Retry")     // :369-376
  Banner(title = "We won't draw these days as open. …", tone = BannerTone.Attention) // :427-431
  ```
- Fix: one `Failure` banner whose `detail` carries the double-booking sentence; drop the header `IconCircle` (pull-to-refresh at `:104-109` already covers it) or drop the banner action, not both.

### F-AS-11 — Screen 86 hand-rolls the section header it uses a component for on 09/85
- Screens: 86
- Where: `ArtistHomeScreen.kt:398-405` vs `:493-497`
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) the same strip's header is `SectionHeader(title, actionLabel, onAction)` in `availabilityStrip`.
- What: The unavailable strip draws its own `Row` with a `sectionTitle` and the word "unavailable" in `subtitle`, bottom-aligned. Thirty lines away the working strip uses `SectionHeader`. Same block, same screen family, two implementations — and the hand-rolled one has no "Manage" route out, so the one thing an artist could still usefully do (open the availability editor) is missing exactly where the data failed.
- Evidence:
  ```kotlin
  Text("Next 14 days", style = AppTheme.type.sectionTitle, …)
  Text("unavailable", style = AppTheme.type.subtitle, …)        // :403-404
  ```
- Fix: `SectionHeader("Next 14 days", actionLabel = "Manage", onAction = onOpenAvailability)` and put the state word in the strip caption.

### F-AS-12 — The section's person-rows use a 40dp avatar; every other list in the app uses 48
- Screens: 09, 35
- Where: `ArtistHomeScreen.kt:859`; `GigRequestDetailScreen.kt:318` (`component.rowAvatar` = 40, `Dimens.kt:327`) vs `feature/messages/MessagesScreen.kt:465`, `feature/messages/ArchivedScreen.kt:255`, `feature/profile/BlockedAccountsScreen.kt:275` (`size.avatarMd` = 48, `Dimens.kt:76`)
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) three peer person-lists outside the section, plus rubric §2 "avatar size on rows".
- What: `component.rowAvatar` (40) has call sites only inside this section. A client on a studio request card is drawn 8dp smaller than the same client on the Messages row the artist taps next, so the two lists do not read as the same system.
- Fix: settle on `avatarMd` (48) for a person on a row, or move Messages/Archived/Blocked to `rowAvatar`; one token, not two.

### F-AS-13 — Screen 106: the dock is hand-rolled and the reason Save vanished is at the bottom of a scroll
- Screens: 106, 22
- Where: `ManageAvailabilityScreen.kt:298-312`; error text at `:285-288`; copy at `feature/availability/ManageAvailabilityViewModel.kt:141`; peer dock at `GigRequestDetailScreen.kt:231`
- Category: hierarchy / consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) `Modifier.dockSurface()` (`designsystem/component/DockSurface.kt:30`) is the section's dock; rubric §3 "a disabled CTA with no stated reason".
- What: The failed-seed state replaces "Save changes" with a bare "Retry" — not the design's disabled Save — and the sentence that explains it ("Couldn't load availability. Retry before saving.") renders as a red `footnote` below the time-slot chips, i.e. off screen unless the artist scrolls to the end. The artist sees a Save button turn into a Retry button for no visible reason. The dock itself is a hand-built `Column { HRule(); Box(padding(space.xl)) }` rather than `.dockSurface()`, so it sits on a 24dp inset while every other dock in the section sits on the 20dp gutter.
- Evidence:
  ```kotlin
  Column { HRule(); Box(Modifier.padding(space.xl)) {
      if (state.seedFailed) PrimaryButton(text = "Retry", onClick = viewModel::seed, fullWidth = true)
      else PrimaryButton(text = if (state.isSaving) "Saving…" else "Save changes", …) } }  // :298-312
  ```
- Fix: `.dockSurface().padding(gutter)`, keep "Save changes" present-but-disabled, and put the reason in a `Banner(Failure, actionLabel = "Retry")` immediately above the dock.

### F-AS-14 — Manage availability pads by 24 and 16; the rest of the section pads by the 20 gutter
- Screens: 22, 105, 106
- Where: `ManageAvailabilityScreen.kt:105`, `:187`, `:300` (`space.xl` = 24) and `:138`, `:183` (`space.lg` = 16); `Dimens.kt:12`, `:11`, `:221`
- Category: spacing
- Severity: P3 (assigned by the lead, see note)
- Rule: (a) §2 "page horizontal padding 20" = `component.gutter`; (b) every other screen in the section pads by `dimens.component.gutter`.
- What: Three different horizontal insets on one screen and none of them is the page gutter: the title block and the chip section start at 24, the calendar summary line at 16, the Save dock at 24. Against Earnings or Studio, opened one tap apart, the whole page is shifted 4dp inward. The `:181-183` comment justifies the 16 (matching the grid's own padding); nothing justifies the 24.
- Fix: `dimens.component.gutter` on the prose blocks and the dock.

### F-AS-15 — Five alpha-manufactured tints where the palette already names the colour
- Screens: 09, 133
- Where: `ArtistHomeScreen.kt:980`, `:983-984` used at `:540`, `:545`, `:598`, `:624`; `EarningsScreen.kt:318` used at `:202`
- Category: token
- Severity: P3 (assigned by the lead, see note)
- Rule: (a) rubric §1 "alpha hacks (`.copy(alpha=…)`) that manufacture an off-palette tint"; §4 already defines `accentSoft`/`brandSoft` = "accent at ~12% on white".
- What: Four constants invent five colours that are not in the sheet: `PILL_FILL` 0.3 and `PILL_LINE` 0.6 (accent washes for the header pill, where `accentSoft` exists), `DELTA_FILL` 0.4 (another accent wash for the delta pill — so the section's two accent-tinted pills are tinted at two different strengths), and `ON_ACCENT_SOFT` 0.6 applied twice on the money card: to the eyebrow and to "₹48,000 agreed, still to play". That second one is 60% of `#0b0b0c` over lime — a muddy olive at 13.5sp carrying the card's only qualifying fact.
- Evidence:
  ```kotlin
  private const val ON_ACCENT_SOFT = 0.6f   // :980
  private const val PILL_FILL = 0.3f        // :983
  private const val PILL_LINE = 0.6f        // :984
  private const val DELTA_FILL = 0.4f       // EarningsScreen.kt:318
  ```
- Fix: `accentSoft` for both pill fills, `hairline`-equivalent rim, and a real on-accent-secondary token (or plain `onAccent` at full strength) for the money card's second line.

### F-AS-16 — Two dimension groups describe one dashboard, and half of one is dead
- Screens: 09, 85, 86, 133
- Where: `designsystem/theme/Dimens.kt:518` `dayCellW` 42, `:519` `dayCellH` 50, `:526` `scoreRing` 86, `barsWidth` 86 / `barsHeight` 32 (no call sites anywhere) vs `:337` `component.stripCellH` 44 and `:339` `component.barChart` 78, which the studio actually draws with (`ArtistHomeScreen.kt:418`, `:797`; `EarningsScreen.kt:238`)
- Category: token
- Severity: P3 (assigned by the lead, see note)
- Rule: (b) internal inconsistency; relates to F-CC-11.
- What: The `Dashboard` group is documented as "the artist dashboard's charts, strip and banners", but the artist dashboard uses none of its strip or ring members — the 14-day cell is `component.stripCellH` (44, not `dayCellH` 50) and the earnings bars are `component.barChart` (78, not `dashboard.chartHeight` 88, which now belongs to Search and Score history). A maintainer tuning "the dashboard's day cell" edits a token nothing draws. Answering F-CC-11 directly: with `Sparkline`, `MiniBars` and `ScoreRing` all callerless, the dashboard's "chart" is a hand-rolled 4.5dp meter (`ArtistHomeScreen.kt:664-680`), its "bars" are a hand-rolled `Row` of `Box`es (`EarningsScreen.kt:245-259`), and its ring does not exist at all.
- Fix: delete `dayCellW`/`dayCellH`/`scoreRing`/`barsWidth`/`barsHeight` (and the three dead components), or move `stripCellH`/`barChart` into `Dashboard` so one group owns the data display.

### F-AS-17 — "Earned this month" claims money the Earnings screen spends a banner disowning
- Screens: 09, 133
- Where: `ArtistHomeScreen.kt:598` vs `EarningsScreen.kt:86`, `:186-190`
- Category: copy
- Severity: P3 (assigned by the lead, see note)
- Rule: (b) two screens, one number, opposite words.
- What: The dashboard's hero eyebrow is "Earned this month". One tap later the same figures are headed "Agreed fees, settled directly" and closed with "These are the fees you agreed in the app, not money Artistant moved… we can't certify it as income". The section is careful everywhere else — "played", "agreed, still to play", "Their proposal", "Agreed at" — and then leads with the one word it spends a banner retracting.
- Evidence:
  ```kotlin
  EyebrowLabel("Earned this month", color = colors.onAccent.copy(alpha = ON_ACCENT_SOFT))  // :598
  ```
- Fix: "Played this month" — it matches `money.playedInr`, which is what the figure is.

### F-AS-18 — The earnings axis borrows the calendar's weekday style and sits at body ink
- Screens: 133
- Where: `EarningsScreen.kt:266`; `Type.kt:351`
- Category: token
- Severity: P3 (assigned by the lead, see note)
- Rule: (a) §2 meta/caption is `ink4`; (b) `monoWeekday` is documented "Calendar: the M T W T F S S strip".
- What: The four month labels under the bar chart use `monoWeekday` — a 10sp calendar token — coloured `ink3`, the body-copy step. Every other meta line on the page ("across 3 gigs" `:157`, the row date `:295`, the row state `:309`) is `caption`/`ink4`, so the least important text on the screen is the darkest small text on it.
- Fix: `monoLabel` (the eyebrow/numeral token) at `ink4`.

### F-AS-19 — Twelve `.copy()` calls invent type steps, eight of them on `rowTitle`
- Screens: 09, 85, 133, 36, 35, 107
- Where: `ArtistHomeScreen.kt:344`, `:565`, `:616`, `:623`, `:656`, `:863`, `:881`, `:917`, `:935`; `ArtistGigsScreen.kt:281`, `:304`; `EarningsScreen.kt:215`, `:303`; `GigRequestDetailScreen.kt:322`, `:534-537`, `:564`
- Category: token
- Severity: P3 (assigned by the lead, see note)
- Rule: (a) rubric §1 "`.copy(fontWeight=…)` inventing a new step".
- What: `rowTitle` is defined as 14.5/600 (`Type.kt:169`) and every one of its eight call sites in this section immediately overrides it to Bold — the token is either wrong or the call sites are, but a ramp that is never used as defined is not a ramp. `footnote` (itself a compat alias for `chip`, `Type.kt:282`) is bumped to SemiBold/Bold four more times, and `AmountCell` switches `monoMedium` between Normal and Bold to signal selection.
- Evidence:
  ```kotlin
  style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold)   // :863, :881, :917, ArtistGigsScreen:281 …
  ```
- Fix: either re-cut `rowTitle` to 700 or add a `rowTitleStrong` step, then delete the `.copy()`s; replace `footnote` with the real step (see F-CC-10).

### F-AS-20 — "N waiting" touches the section header it hangs from
- Screens: 09
- Where: `ArtistHomeScreen.kt:246-257`; `designsystem/component/SectionHeader.kt:37-40`
- Category: spacing
- Severity: P3 (assigned by the lead, see note)
- Rule: (c) fact — both `Text`s are inside one `item {}`, so the `LazyColumn`'s `verticalArrangement = Arrangement.spacedBy(dimens.space.lg)` (`:130`) does not apply between them and no local spacer is set.
- What: "Requests" and "1 waiting" render flush against each other, unlike every other title→subtitle pair in the section (`ScreenHeader` and `BackHeader` both space their own subtitle). `SectionHeader` has `actionLabel` but no `subtitle` slot, so the caller improvised.
- Evidence:
  ```kotlin
  SectionHeader(title = "Requests", modifier = Modifier.padding(horizontal = gutter))
  Text(text = if (waiting == 1) "1 waiting" else "$waiting waiting", style = …subtitle, …)
  ```
- Fix: add a `subtitle: String?` slot to `SectionHeader` and pass the count through it.

## Section-wide observations
- **Cross-cutting instances in this section** (filed by the lead, listed for the fix pass): F-CC-02 filled glyphs — `ArtistHomeScreen.kt:140` `Filled.Refresh` in a 42 `IconCircle`, `EarningsScreen.kt:208` `AutoMirrored.Filled.TrendingUp/Down`, `ManageAvailabilityScreen.kt:81` `AutoMirrored.Filled.ArrowBack`. F-CC-07 — `ManageAvailabilityScreen.kt:80-89`. F-CC-08 — `GigRequestDetailScreen.kt:271-294` (the `AlertDialog` runs to 294, not 292). F-CC-09 — `ArtistGigsScreen.kt:122-126`, `GigRequestDetailScreen.kt:110-117`, `ManageAvailabilityScreen.kt:87-89`. F-CC-15 — `GigRequestDetailScreen.kt:181`, `ArtistGigsScreen.kt:148`, `ManageAvailabilityScreen.kt:95`.
- **F-CC-10 compat aliases, exact sites.** `feature/availability/ManageAvailabilityScreen.kt` carries all six colour sites — `:73` `bg`, `:194` `bgSoft`, `:197` `lineSoft`, `:208` `brandSoft`, `:211` `brandInk`, `:287` `hot` — and four of the type sites: `:110`, `:214`, `:287`, `:291` `footnote`. The rest of the section adds six more `footnote` sites: `ArtistHomeScreen.kt:616`, `:623`, `:935`; `ArtistGigsScreen.kt:189`; `GigRequestDetailScreen.kt:413`, `:564`. Manage availability is the one screen in the section that was never redesigned — it is still the pre-redesign layout with new colours poured into old names.
- **Three screens hand-roll what the design system already ships**: the masthead (F-AS-02), the section header (F-AS-11), the dock (F-AS-13), the empty state (F-AS-05), the eyebrow (F-AS-07) and the preview avatar (`ManageAvailabilityScreen.kt:204-212` builds a `Box` + "You" text where `Avatar()` is used at `ArtistHomeScreen.kt:859` and `GigRequestDetailScreen.kt:318`).
- **The card anatomy is otherwise strong and consistent**: `RequestCard` (`ArtistHomeScreen.kt:849-885`), `UpcomingRow` (`:893-938`) and `GigDayRow` (`ArtistGigsScreen.kt:257-313`) all share `radii.card` + `surface3` + `component.cardPad` (15) + `rowAvatar`-height accent rule + `space.md` gaps. Only the money typography (F-AS-03) and the avatar size (F-AS-12) break the set.
- **`ClockColumn` is genuinely shared** — `designsystem/component/MonthCalendar.kt:742`, called from `DayEventRow` at `:692` (screen 78) and from `ArtistGigsScreen.kt:270` (screen 36), so the stacked "8:00 / pm" is drawn identically on both. No finding; worth keeping that way.
- **Narration density on 107/108.** `CounteredBody` stacks an `AccentNote` (`:376`), a `Note` banner (`:420`) and a trailing caption (`:427`); `DeclinedBody` stacks a `Failure` banner (`:444`), a caption (`:456`) and a `surface3` explainer card (`:462`). Each block is individually honest and well-written, but three prose asides per state is a lot of talking on a screen whose only remaining job is read-only.
- **`LaunchedEffect(Unit)` at `GigRequestDetailScreen.kt:101` is fine** — it collects a one-shot event flow for a haptic, not a refetch. The only nit is that it is not `repeatOnLifecycle`-wrapped, so an Accept that lands while the screen is backgrounded still buzzes on return.
- **Pull-to-refresh coverage is uneven across the app**: `PullToRefreshBox` is present on Studio (`ArtistHomeScreen.kt:104`), Gigs (`ArtistGigsScreen.kt:116`), Discover, EPK and Messages, and absent from the client's Bookings — the artist can pull to refresh their calendar, the client cannot pull to refresh theirs. Out of section (BN), noted for the lead.
- **`ProposalCard`'s doc is stale**: `GigRequestDetailScreen.kt:496` says "one figure, at hero size, on the accent" but `:505` paints `colors.surface3`. Code is right (F-AS-01 argues for exactly that); the comment should follow.
