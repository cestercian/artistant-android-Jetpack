<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# AP — The artist profile (screens 04, 54, 55, 100, 101, 103, 56, 102, 16, 50, 79, 80, 99, 51)

Source root: `app/src/main/java/in/artistant/app/`. Design export not available; every finding cites rule (a) §2 of `docs/REDESIGN_2026-09.md` / a token file, (b) a peer in the repo, or (c) a Compose fact.

## Coverage
| Screen | File(s) | Reviewed | Findings |
|---|---|---|---|
| 04 Artist profile | feature/artist/ArtistProfileScreen.kt, ArtistProfileMedia.kt, ArtistProfileFacts.kt | yes | F-AP-01, 02, 03, 04, 05, 08, 09, 10, 11, 12, 16, 18, 19, 21, 22, 23, 24 |
| 54 Profile loading (skeleton, no nav bar) | ArtistProfileScreen.kt:222-284 | yes | F-AP-20, F-AP-21 |
| 55 Artist not found | ArtistProfileScreen.kt:301-345 | yes | none |
| 100 Profile with scoped failure banner | ArtistProfileScreen.kt:399-408, 1161-1222 | yes | F-AP-22, F-AP-24 |
| 101 No-audio redirect | ArtistProfileScreen.kt:990-1037, 1083-1107 | yes | F-AP-10, F-AP-11, F-AP-19 |
| 103 Self view | ArtistProfileScreen.kt:383-393, 550-557, 1328-1335 | yes | F-AP-17 |
| 56 Report artist sheet | feature/artist/ArtistProfileSheets.kt:168-345 | yes | F-AP-06, F-AP-21, F-AP-24 |
| 102 All reviews | feature/artist/ArtistReviewsScreen.kt, ReviewSearch.kt | yes | F-AP-16, F-AP-19, F-AP-24 |
| 16 Bookability (client audit) | feature/score/BookabilityScreen.kt | yes | F-AP-01, 03, 07, 09, 10, 13, 20 |
| 50 Score explainer (Score / Stats / Opportunities) | feature/score/ScoreExplainerScreen.kt, ScoreDonut.kt, ScoreOpportunities.kt, ScoreFactors.kt | yes | F-AP-01, 02, 03, 07, 08, 09, 10, 12, 13, 14, 17, 19, 21 |
| 79 Explainer — New tier | ScoreExplainerScreen.kt:317-331, 353-387 | yes | F-AP-13, F-AP-21 |
| 80 Explainer — failed read | ScoreExplainerScreen.kt:275-315 | yes | F-AP-10, F-AP-19 |
| 99 Score breakdown sheet (degraded) | feature/score/ScoreBreakdownSheet.kt | yes | F-AP-01, 03, 06, 07, 10, 13, 14 |
| 51 Score history | feature/score/ScoreHistoryScreen.kt | yes | F-AP-03, 07, 09, 12, 14, 15, 19, 20, 21, 24 |

## Findings

### F-AP-01 — Accent fills multiply on 04, 16, 99 and the Opportunities tab of 50
- Screens: 04, 16, 99, 50
- Where: `feature/artist/ArtistProfileScreen.kt:644`, `:906-909`, `:1344`; `feature/score/BookabilityScreen.kt:293`, `:227-231`; `feature/score/ScoreBreakdownSheet.kt:118-121`, `:164-170`, `:232-236`; `feature/score/ScoreExplainerScreen.kt:486-495`, `:251-255`; `designsystem/component/Meter.kt:94`
- Category: hierarchy
- Severity: P1
- Rule: (a) §2 "`accent` — the one signal"; Principles "one accent per screen"
- What: Every screen in this section paints more than one accent-filled surface at once. 04: the rating pill (30% accent wash), the pre-selected package row (accent hairline + 22% wash — `selectedPackageIndex` defaults to 0 / `popularIdx`, `ArtistProfileViewModel.kt:94`, `:215`, so one row is always drawn selected) and the pinned CTA. 16: a full-width accent card plus up to five accent meter fills. 99: the accent `ScoreDisc`, the accent meter fills and an accent `PrimaryButton`. 50 Opportunities: one accent-filled "+N" pill per row (up to seven rows from `ScoreOpportunities.of`) above a pinned accent CTA.
- Evidence:
  ```kotlin
  // ScoreExplainerScreen.kt:486-495
  win.points?.let { points ->
      Text(
          "+$points",
          style = AppTheme.type.monoPill.copy(fontWeight = FontWeight.Bold),
          color = colors.onAccent,
          modifier = Modifier
              .clip(RoundedCornerShape(dimens.radii.sm))
              .background(colors.accent)
  ```
- Fix: keep the CTA (04/99/50) or the headline card (16) as the one accent fill; render the "+N" pill and the rating/delta pills as `Pill(tone = PillTone.Brand)` (brandSoft ground, `accentDeep` text), draw the selected package with the hairline only, and give `Meter` a non-accent fill on screens that already carry an accent object.

### F-AP-02 — RevealOnAppear stacks a 300 ms fade on the 300 ms push on 04 and 50
- Screens: 04, 50
- Where: `feature/artist/ArtistProfileScreen.kt:369`; `feature/score/ScoreExplainerScreen.kt:205`; `designsystem/theme/Motion.kt:53`, `:66`, `:82`; `feature/artist/ArtistProfileMedia.kt:149`, `:173`
- Category: slow
- Severity: P1
- Rule: (c) fact — `contentReveal = medium2 = 300`, `stackPush = medium2`
- What: The whole loaded profile (header + scroll column + dock) and the whole explainer scroll are wrapped in `RevealOnAppear`, whose fade+lift runs `Motion.contentReveal` (300 ms) after the 300 ms stack push — roughly 600 ms before the two heaviest pages in the section settle. Peers in this section (102, 16, 51, 99) do not reveal. The Spotify disclosure chevron also reads its animated value in composition (`Modifier.rotate(chevron)`), recomposing the row every frame of the turn.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:369-370
  RevealOnAppear {
      Column(Modifier.fillMaxSize().background(colors.surface)) {
  // ScoreExplainerScreen.kt:205
  RevealOnAppear(Modifier.weight(1f)) {
  // ArtistProfileMedia.kt:173
  modifier = Modifier.size(dimens.size.iconLg).rotate(chevron),
  ```
- Fix: drop `RevealOnAppear` on both screens (let the nav transition be the reveal), and move the chevron rotation into `graphicsLayer { rotationZ = chevron }`.

### F-AP-03 — The score numeral is set five different ways across five screens
- Screens: 04, 16, 50, 99, 51
- Where: `feature/artist/ArtistProfileScreen.kt:746-752`; `feature/score/BookabilityScreen.kt:301-305`; `feature/score/ScoreDonut.kt:107-114`; `feature/score/ScoreBreakdownSheet.kt:262-267`; `feature/score/ScoreHistoryScreen.kt:227-231`
- Category: token
- Severity: P2
- Rule: (a) §2 "JetBrains Mono for eyebrow labels and numerals"; `monoNumber` / `monoCount` exist (`Type.kt:231`, `:335`); (b) peers disagree
- What: The same number — the Bookability score — is `displaySmall` (19 sans) in the profile stat cell, `displayHero` (30 sans, the onboarding step) on the 16 accent card, `displayHero.copy(fontSize = glyphSize, fontWeight = ExtraBold)` in the donut, `monoNumber` (18 mono) in the 99 disc, and `displayHero` again on 51. Only the sheet follows the mono rule; a reader moving 04 → 99 → 16 sees the number change family, weight and size at each step.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:748      style = AppTheme.type.displaySmall,
  // BookabilityScreen.kt:303        style = AppTheme.type.displayHero,
  // ScoreDonut.kt:109-111          style = AppTheme.type.displayHero.copy(fontSize = glyphSize, fontWeight = FontWeight.ExtraBold),
  // ScoreBreakdownSheet.kt:264     style = AppTheme.type.monoNumber,
  // ScoreHistoryScreen.kt:229      style = AppTheme.type.displayHero,
  ```
- Fix: one mono step for the score everywhere — `monoCount` for headline placements (16, 50, 51) and `monoNumber` for inline ones (04 cell, 99 disc); size the donut glyph from `monoCount` rather than from `displayHero`.

### F-AP-04 — Stat strip and package prices in the sans; the account stat band uses mono
- Screens: 04
- Where: `feature/artist/ArtistProfileScreen.kt:746-752`, `:955-960`, `:1345-1349`; peer `feature/profile/AccountChrome.kt:176`
- Category: token
- Severity: P2
- Rule: (a) §2 mono for numerals, money; (b) `AccountStatBand` sets its values in `monoCount`
- What: The three-cell strip (Shows / Bookability / Replies in) sets "12", "78", "~2h" in `displaySmall` sans; the account page's three-cell band sets the same kind of value in `monoCount`. The package price is `rowTitle.copy(fontWeight = Bold)` sans, and the price riding the CTA ("Check availability · ₹26,000") is plain `cta` sans. Indian grouping is correct (`formatInr`, `common/util/Inr.kt:9-16`).
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:955-958
  Text(
      formatInr(pkg.price),
      style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
      color = colors.ink,
  // AccountChrome.kt:176
  Text(stat.value, style = AppTheme.type.monoCount, color = colors.ink)
  ```
- Fix: `monoCount` for the stat values (matching `AccountStatBand`), `monoPill`/`monoNumber` for the package price, and build the CTA label as an `AnnotatedString` with the ₹ figure in `MonoFamily`.

### F-AP-05 — Profile header is hand-rolled although BackHeader has the trailing slot meant for 04
- Screens: 04, 103
- Where: `feature/artist/ArtistProfileScreen.kt:523-565`; peers `feature/artist/ArtistReviewsScreen.kt:157-166`, `feature/score/BookabilityScreen.kt:177-182`; `designsystem/component/Headers.kt:77`, `:104`
- Category: consistency
- Severity: P2
- Rule: (b) every other pushed screen in the section uses `BackHeader`; `Headers.kt:77` documents the "optional trailing slot (screens 04 / 12 / 18 / 119)"
- What: `ProfileHeader` re-implements the centred-title bar as a `Row` (its own `statusBarsPadding`, `sm` vertical padding, `sectionTitle`, `caption` subtitle, second `IconCircle`) while `BackHeader(trailing = …)` exists for exactly this screen. Any later change to the header's height, title style or back-circle geometry lands on 102/16/50/51 and not on 04.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:526-531
  Row(
      Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = dimens.component.gutter)
          .padding(top = dimens.space.sm, bottom = dimens.space.sm),
  ```
- Fix: replace `ProfileHeader` with `BackHeader(title, subtitle = if (isSelf) "How clients see you" else null, onBack, trailing = { IconCircle(MoreHoriz…) })`.

### F-AP-06 — Three sheets, three title treatments; `SheetScaffold(title=)` unused by all of them
- Screens: 04 (overflow), 56, 99
- Where: `feature/artist/ArtistProfileSheets.kt:81-89`, `:209-228`; `feature/score/ScoreBreakdownSheet.kt:92-111`; `designsystem/component/SheetScaffold.kt:49-53`
- Category: consistency
- Severity: P2
- Rule: (b) `SheetScaffold` carries a `title: String?` (line 51); the rubric's sheet contract is grabber + title + hairline
- What: The overflow sheet's "title" is the bare artist name with no close control; the report sheet and the breakdown sheet each hand-roll a `Row` of `sectionTitle` + `IconCircle(Close, iconCircleSm)`. None passes `title` to the scaffold, so the three sheets a reader can open from one page have three different heads (no close / close / close, with different bottom paddings `sm` / `md` / `lg`).
- Evidence:
  ```kotlin
  // ArtistProfileSheets.kt:81-88
  SheetScaffold {
      Text(
          artistName,
          style = AppTheme.type.sectionTitle,
  // ScoreBreakdownSheet.kt:92-94
  SheetScaffold {
      Row(
          Modifier.fillMaxWidth().padding(bottom = space.lg),
  ```
- Fix: use `SheetScaffold(title = …)` on all three and decide once whether the section's sheets carry a close circle.

### F-AP-07 — The "what moves it" block gets a different heading component on every screen
- Screens: 04, 16, 50, 99, 51
- Where: `feature/artist/ArtistProfileScreen.kt:772`; `feature/score/BookabilityScreen.kt:223`; `feature/score/ScoreBreakdownSheet.kt:163`, `:173`, `:196`; `feature/score/ScoreExplainerScreen.kt:395`, `:436-446`; `feature/score/ScoreHistoryScreen.kt:180`
- Category: consistency
- Severity: P2
- Rule: (a) §2 `sectionTitle` = section header, `monoLabel` = eyebrow; (b) peers disagree on the same block
- What: The itemised-factors block is headed by `SectionHeader("What moves it")` (17/700 sans) on 16, `EyebrowLabel("What moves it")` (11 mono uppercase) on 99, `EyebrowLabel("What goes into it")` on 50 Stats, and on 50 Opportunities by a hand-set `Text("Small wins", rowTitle.copy(Bold))` with a caption under it. 04 uses `SectionHeader` for every block; 51 uses an eyebrow. Same role, three components and three wordings.
- Evidence:
  ```kotlin
  // BookabilityScreen.kt:223        SectionHeader("What moves it")
  // ScoreBreakdownSheet.kt:163      EyebrowLabel("What moves it")
  // ScoreExplainerScreen.kt:395     EyebrowLabel("What goes into it")
  // ScoreExplainerScreen.kt:436-438
  Text(
      "Small wins",
      style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
  ```
- Fix: `SectionHeader` for page sections (16, 50 tabs), `EyebrowLabel` only inside the sheet where the design's eyebrow rhythm applies, and one wording ("What moves it").

### F-AP-08 — Three row types, three chevrons; the disclosure row still uses compat aliases
- Screens: 04, 50
- Where: `feature/artist/ArtistProfileMedia.kt:164`, `:166`, `:167-174`; `feature/score/ScoreExplainerScreen.kt:510-515`; peer `designsystem/component/ListRow.kt:115-117`; `feature/artist/ArtistProfileScreen.kt:871-876`
- Category: consistency
- Severity: P2
- Rule: (a) §2 "list row … chevron `ink4`"; compat aliases `callout`/`footnote` are debt on a redesigned screen (2 in `ArtistProfileMedia.kt`, 0 elsewhere in the section)
- What: On 04 the "Custom date or budget?" row is a `ListRow` (chevron `ink4`, `KeyboardArrowRight`) while the Spotify `DisclosureRow` two blocks up hand-rolls its row with `callout`/`footnote`, a chevron in `ink3` at `iconLg`; on 50 `OpportunityRow` hand-rolls again with the chevron in `lineStrong` at `iconMd`. Three tints and two sizes for the same "this opens" glyph.
- Evidence:
  ```kotlin
  // ArtistProfileMedia.kt:164-173
  Text(title, style = AppTheme.type.callout, color = colors.ink)
  Spacer(Modifier.weight(1f))
  Text(detail, style = AppTheme.type.footnote, color = colors.ink3)
  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
      tint = colors.ink3, modifier = Modifier.size(dimens.size.iconLg).rotate(chevron))
  // ScoreExplainerScreen.kt:513-514
  tint = colors.lineStrong, modifier = Modifier.size(dimens.size.iconMd),
  ```
- Fix: `rowTitle`/`subtitle` in place of `callout`/`footnote`; chevron `ink4` at one size on all three rows (or build `DisclosureRow` and `OpportunityRow` on `ListRow` with a `trailing` slot).

### F-AP-09 — Hand-rolled pills tint the accent with alpha where `Pill` and `accentSoft` exist
- Screens: 04, 51, 50
- Where: `feature/artist/ArtistProfileScreen.kt:638-665`, `:906`, `:964`; `feature/score/ScoreHistoryScreen.kt:246-287`; `feature/score/ScoreExplainerScreen.kt:486-495`; `designsystem/component/Pill.kt:38-39`; `designsystem/theme/Color.kt:120-122`
- Category: token
- Severity: P2
- Rule: (a) §4 `brandSoft` "accent at ~12% on white" is the token wash; `Pill(tone = Brand)` = brandSoft/accentDeep, `BrandSolid` = accent/onAccent
- What: Three different off-palette limes are manufactured: `accent.copy(alpha = 0.3f)` behind the rating pill, `0.22f` behind the selected package, `0.4f` behind the history delta pill — while the "+N" pill hand-rolls the solid variant. Each is a `Row`/`Text` with `clip(CircleShape)` + `background` + `badge`/`monoPill` text, i.e. a re-implementation of `Pill`.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:644     .background(colors.accent.copy(alpha = RATING_PILL_ALPHA))
  // ArtistProfileScreen.kt:906     .background(if (selected) colors.accent.copy(alpha = SELECTED_ROW_ALPHA) else colors.surface3)
  // ScoreHistoryScreen.kt:260      delta > 0 -> colors.accent.copy(alpha = DELTA_PILL_ALPHA)
  // Pill.kt:38-39
  PillTone.Brand -> colors.brandSoft to colors.accentDeep
  PillTone.BrandSolid -> colors.accent to colors.onAccent
  ```
- Fix: `Pill(text, tone = Brand)` for rating and positive delta, `accentSoft` for the selected package wash, `Pill(tone = BrandSolid)` (or Brand, per F-AP-01) for "+N"; delete the three alpha constants.

### F-AP-10 — Explanatory sentences set in `caption`; one body paragraph in `ink4`
- Screens: 16, 99, 50, 80, 51, 101, 04
- Where: `feature/score/BookabilityScreen.kt:313-322`; `feature/score/ScoreBreakdownSheet.kt:204-221`; `feature/score/ScoreExplainerScreen.kt:303-309`, `:407-420`, `:441-446`; `feature/score/ScoreHistoryScreen.kt:201-208`; `feature/artist/ArtistProfileScreen.kt:858-868`, `:1028-1034`
- Category: token
- Severity: P2
- Rule: (a) §2 `body` = 15 / 1.6 for paragraphs (`ink3`); `caption` = 12.5 `ink4` meta
- What: Full sentences the reader is meant to read — "Computed from completed bookings, verified reviews and response times on Artistant. Nothing here can be bought." (99), the weights paragraph (50 Stats), the three-sentence rule footer on 51, the New-tier explanation on the 16 card, the pricing modifiers on 04 — are all set at 12.5 sp. On 80 the one paragraph that is `body` is coloured `ink4`. Meta-sized copy carrying the screen's argument is the pattern the rubric names "caption used for body copy".
- Evidence:
  ```kotlin
  // ScoreHistoryScreen.kt:201-207
  Text(
      "Recomputed on every review, completed booking or cancellation. " +
          "This view keeps the last 12 months. Under " +
          "${ScoreBands.MIN_GIGS_FOR_RANK} completed gigs stays on the New " +
          "tier whatever the number says.",
      style = AppTheme.type.caption,
      color = colors.ink4,
  ```
- Fix: `body` + `ink3` for every multi-sentence paragraph; keep `caption` for one-line meta (dates, "today", "of 100 · Silver").

### F-AP-11 — Prompt answer is smaller than its question, contrary to the block's own comment
- Screens: 101, 04
- Where: `feature/artist/ArtistProfileScreen.kt:1076-1104`
- Category: hierarchy
- Severity: P2
- Rule: (a) `caption` is meta, `body` is paragraphs; (c) the comment at 1079-1080 states the opposite intent
- What: The KDoc says "The question is the smaller half because the answer is what the client came to read", but the question is `rowTitle.copy(Bold)` (14.5/700) and the answer is `caption` (12.5) in `ink2`. The client reads the artist's own words at the smallest step on the page.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:1098-1103
  Text(
      prompt.question,
      style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
      color = colors.ink,
  )
  Text(prompt.answer, style = AppTheme.type.caption, color = colors.ink2)
  ```
- Fix: question in `caption`/`ink4` (or `EyebrowLabel`), answer in `body`/`ink`.

### F-AP-12 — `.copy(fontWeight = …)` invents type steps at eight sites
- Screens: 04, 100, 101, 50, 51
- Where: `feature/artist/ArtistProfileScreen.kt:445`, `:957`, `:1024`, `:1100`, `:1257`; `feature/score/ScoreExplainerScreen.kt:438`, `:489`; `feature/score/ScoreHistoryScreen.kt:352`
- Category: token
- Severity: P2
- Rule: (a) §2 ramp has `rowTitle` 14.5/600 and `monoPill` 11.5/600; a 14.5/700 and an 11.5/700 are not on it
- What: Four sites bold `rowTitle` (package price, no-audio title, prompt question, review name), two bold `monoPill` ("+N", history delta), one semibolds `subtitle` for the "Discard this report" text action (in `ink4`, while the "More"/"Less" action at `:784` and `SectionHeader`'s action are the same `.copy(SemiBold)` in `accentInk` — two link styles on one page). Each is a locally invented weight the ramp cannot audit.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:1257    style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
  // ArtistProfileScreen.kt:445-447 style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold), … color = colors.ink4
  // ScoreHistoryScreen.kt:352      style = AppTheme.type.monoPill.copy(fontWeight = FontWeight.Bold),
  ```
- Fix: `rowTitle` as-is (600 is the row-title weight), `monoPill` as-is, and one text-action style (`accentInk` semibold via a shared `TextAction`) for More/Less/Discard.

### F-AP-13 — The same facts are worded differently on 16, 50, 99 and 51; the product term is cased two ways
- Screens: 16, 50, 79, 99, 51, 04
- Where: `feature/score/BookabilityScreen.kt:178`, `:264`, `:307`, `:317-318`; `feature/score/ScoreExplainerScreen.kt:191`, `:290`, `:319-320`, `:334-335`, `:417`; `feature/score/ScoreBreakdownSheet.kt:134`, `:154-156`, `:192`; `feature/score/ScoreHistoryScreen.kt:173-174`, `:202`; `feature/artist/ArtistProfileFacts.kt:47`
- Category: copy
- Severity: P2
- Rule: (b) peers; rubric "sentence case everywhere"
- What: Header "Bookability score" (16) vs "Bookability Score" (50). Denominator "/ 100 · Silver" (16) vs "of 100 · Silver" (50) vs "Silver tier" (99). The New-tier line appears as "not a low score, no score" (16), "That is not a low score — it is no score." (99) and "it isn't a low score, it's no score." (50). The recompute rule is "Recalculated after every set" (16), "recomputed after every set" (50 Stats), "Recomputed on every review, completed booking or cancellation" (50 Score, 51) — "after every set" and "on every review…" are different claims. The review average is "4.92 (128)" on 04 and "4.9 / 5" on 99.
- Evidence:
  ```kotlin
  // BookabilityScreen.kt:178      title = "Bookability score",
  // ScoreExplainerScreen.kt:191   title = "Bookability Score",
  // BookabilityScreen.kt:307      text = if (score != null) "/ 100 · ${tier.label}" else "no score yet",
  // ScoreExplainerScreen.kt:290   else -> "of 100 · ${tier.label}"
  // BookabilityScreen.kt:264      "Recalculated after every set",
  ```
- Fix: one casing ("Bookability score"), one denominator phrase, one New-tier sentence and one recompute sentence shared as constants in `ScoreFactors` (or a `ScoreCopy` object), and one decimal precision for the average.

### F-AP-14 — Engineering vocabulary in user-facing copy: "server", "weights", "recomputations"
- Screens: 50, 99, 51
- Where: `feature/score/ScoreExplainerScreen.kt:413-420`; `feature/score/ScoreBreakdownSheet.kt:216-217`; `feature/score/ScoreHistoryScreen.kt:143`, `:172`, `:180`, `:235`
- Category: slop
- Severity: P2
- Rule: rubric §4 (words like server/backend in user-facing strings); Principles "copy states the fact"
- What: Artists read "The total is the server's own number", "The total is still the server's number", and "Weights are fixed at 40 / 25 / 15 / 10 / 10" — the algorithm's coefficients as a sentence. On 51 every heading is "recomputations" ("Last 12 recomputations", "No recomputations yet", "Every recomputation", "+4 over 12 recomputations"), a word from the compute job, on a screen the design calls "Score history".
- Evidence:
  ```kotlin
  // ScoreExplainerScreen.kt:414-417
  "Weights are fixed at ${ScoreFactors.SHOW_UP_WEIGHT} / " +
      "${ScoreFactors.REVIEWS_WEIGHT} / ${ScoreFactors.REPLY_WEIGHT} / " +
      "${ScoreFactors.RELIABILITY_WEIGHT} / ${ScoreFactors.SOCIAL_WEIGHT}. " +
      "The total is the server's own number, recomputed after every set.",
  // ScoreHistoryScreen.kt:143   subtitle = if (points.isEmpty()) null else "Last ${points.size} recomputations",
  ```
- Fix: "Artistant works the total out; the rows show what each part is worth." / "Last 12 updates", "No updates yet", "Every update".

### F-AP-15 — History rows print raw ISO dates and a "·" for an absent value
- Screens: 51
- Where: `feature/score/ScoreHistoryScreen.kt:194`, `:351`, `:369`
- Category: copy
- Severity: P2
- Rule: rubric copy conventions — "Sat 12 Oct" dates, "—" for absent values; `EM_DASH` is already used by `ScoreDonut.kt:60`
- What: `point.computedAtIso.take(ISO_DATE_LENGTH)` renders "2026-08-14" under every row; the first row's delta column shows "·" where the section's own donut and `UnavailableRow` use the em dash.
- Evidence:
  ```kotlin
  // ScoreHistoryScreen.kt:194    date = point.computedAtIso.take(ISO_DATE_LENGTH),
  // ScoreHistoryScreen.kt:351    text = delta?.let { signed(it) } ?: "·",
  // ScoreHistoryScreen.kt:369    Text(date, style = AppTheme.type.caption, color = colors.ink4)
  ```
- Fix: parse once in the ViewModel and format "Thu 14 Aug" (the app's `d MMM` formatter); use `EM_DASH` for the first row.

### F-AP-16 — Pinned action bars differ between 04 and 102/50; the Message circle is 52 next to a 54 CTA
- Screens: 04, 102, 50
- Where: `feature/artist/ArtistProfileScreen.kt:1318-1343`; `feature/artist/ArtistReviewsScreen.kt:280-292`; `feature/score/ScoreExplainerScreen.kt:237-243`; `designsystem/theme/Dimens.kt:223`, `:414`
- Category: consistency
- Severity: P2
- Rule: (a) §2 "secondary button — same height" as the 54 CTA; (b) peers
- What: 04's dock is `HRule` + `padding(vertical = md)`; 102 and 50 pin the same `PrimaryButton` with no rule and `top = sm, bottom = lg`. On 04 the secondary control beside the CTA is an `IconCircle` at `chrome.actionSize` (52) beside `component.cta` (54), so the two controls in one row have different heights.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:1336-1343
  IconCircle(
      icon = Icons.AutoMirrored.Filled.Chat,
      … size = dimens.chrome.actionSize,
  // ArtistReviewsScreen.kt:284-285
  .padding(horizontal = dimens.component.gutter)
  .padding(bottom = space.lg, top = space.sm),
  ```
- Fix: one `ActionBar` composable (rule or no rule, decided once) used by 04/102/50, and the Message circle at `component.cta`.

### F-AP-17 — Screen 103 says "this is you" three times; 50's subtitle restates the segmented control under it
- Screens: 103, 50
- Where: `feature/artist/ArtistProfileScreen.kt:387-392`, `:550-557`, `:1328-1335`; `feature/score/ScoreExplainerScreen.kt:190-204`
- Category: slop
- Severity: P2
- Rule: rubric §4 "a subtitle that restates the title"; §3 hierarchy
- What: On the self view the header subtitle reads "How clients see you", the `AccentNote` directly under it reads "This is how clients see your profile. Booking controls are off while you're looking at your own act.", and the dock at the bottom reads "You're viewing your own profile." — the dock keeps its rule and padding to hold a sentence. On 50 the `BackHeader` subtitle is "Score · Stats · Opportunities", the labels of the `SegmentedControl` rendered 12 dp below it.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:1329-1333
  Text(
      "You're viewing your own profile.",
      style = AppTheme.type.subtitle,
      color = colors.ink4,
  // ScoreExplainerScreen.kt:192
  subtitle = ScoreTab.entries.joinToString(" · ") { it.label },
  ```
- Fix: keep the `AccentNote` only (drop the header subtitle and the dock on 103); drop 50's subtitle or make it the artist's name/tier.

### F-AP-18 — Icon families disagree with the rest of the app for save, chat and audio
- Screens: 04
- Where: `feature/artist/ArtistProfileScreen.kt:1337`, `:1378-1383`; `feature/artist/ArtistProfileMedia.kt:159`; `feature/artist/ArtistProfileScreen.kt:1016`; peers `feature/discover/DiscoverScreen.kt:161`, `:184`; `feature/messages/MessagesScreen.kt:191`; `navigation/ClientTabsScaffold.kt:109`
- Category: consistency
- Severity: P2
- Rule: (b) same concept, different glyph across screens
- What: "Save" is a `Filled.Bookmark`/`BookmarkBorder` on the profile and a `Filled.FavoriteBorder` heart on Discover. The Message control is `AutoMirrored.Filled.Chat` (a solid bubble) while Messages and both tab bars use `ChatBubbleOutline`. The overflow sheet mixes families in three rows (`Filled.Bookmark`, `Outlined.IosShare`, `Filled.Flag`). Audio is `Filled.MusicNote` on the Spotify row and `Filled.LibraryMusic` on the no-audio block two rows apart.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:1378-1383
  internal object ProfileActionIcons {
      val Saved = Icons.Filled.Bookmark
      val Save = Icons.Filled.BookmarkBorder
      val Share = Icons.Outlined.IosShare
      val Report = Icons.Filled.Flag
  }
  ```
- Fix: `FavoriteBorder`/`Favorite` for save (matching Discover), `Outlined.ChatBubbleOutline` for Message, `Outlined.Flag`/`Outlined.Share` in the sheet, one audio glyph.

### F-AP-19 — Empty and failed blocks that skip the component or the action; two Retry controls on 80
- Screens: 101, 50, 80, 102, 51, 04
- Where: `feature/artist/ArtistProfileScreen.kt:998-1037`, `:1211-1215`; `feature/score/ScoreExplainerScreen.kt:449-462`, `:245`, `:276-282`; `feature/artist/ArtistReviewsScreen.kt:227-234`; `feature/score/ScoreHistoryScreen.kt:170-175`
- Category: consistency
- Severity: P3
- Rule: (a) Principles "every empty state carries an action", failed states carry Retry; (b) `EmptyState` is the section's empty component (55, 102, 51)
- What: The no-audio block (101) hand-rolls a glyph-circle + title + caption on a `surface3` card with `radii.xl` and no action, although the copy names the redirect ("Their gallery and reviews are the best signal"). On 50 Opportunities a failed profile read renders as a paragraph ("We couldn't read your profile just now…") with no Retry while the pinned CTA says "See score history". `EmptyState` on 102 (no reviews) and 51 (no history) is called without `actionLabel`. On 80 the failure `Banner` carries Retry and the pinned CTA is also "Retry".
- Evidence:
  ```kotlin
  // ScoreExplainerScreen.kt:450-453
  Text(
      if (state.artist == null) {
          "We couldn't read your profile just now, so there's nothing to " +
              "suggest. Your score is unaffected."
  // ScoreExplainerScreen.kt:245
  state.failed -> PrimaryButton("Retry", viewModel::refresh, fullWidth = true)
  ```
- Fix: `EmptyState(actionLabel = "See reviews")` for 101 (scroll/route to reviews); a `Banner(Failure, Retry)` for the failed profile read on Opportunities; on 80 keep one Retry (the banner's) and let the CTA stay "See what counts".

### F-AP-20 — Loading skeletons on 16, 50 and 51 are one block for a page of a card plus five meters
- Screens: 16, 50, 51, 54
- Where: `feature/score/BookabilityScreen.kt:191-197`; `feature/score/ScoreExplainerScreen.kt:214-219`; `feature/score/ScoreHistoryScreen.kt:156-159`; `feature/artist/ArtistProfileScreen.kt:251-260`; peer `feature/artist/ArtistReviewsScreen.kt:203-208`
- Category: consistency
- Severity: P3
- Rule: (a) Principles "narrated, not a spinner"; rubric "skeleton matching geometry"; (b) 102 draws four card-shaped blocks
- What: The three score pages each show a single `skeletonTile`-high block (radius `xl`, `xl`, `lg` — three values for the same block) before a headline + section title + five meters land, so the content jumps. The profile skeleton (54) draws the stat strip as three 76 dp rounded blocks (`size.dateCellH`, `radii.lg`) where the real strip is hairline-ruled cells with no fill.
- Evidence:
  ```kotlin
  // BookabilityScreen.kt:192-195
  SkeletonBlock(Modifier.fillMaxWidth().height(dimens.component.skeletonTile), radius = dimens.radii.xl)
  // ScoreHistoryScreen.kt:157-158
  SkeletonBlock(Modifier.fillMaxWidth().height(dimens.component.skeletonTile), radius = dimens.radii.lg)
  // ArtistProfileScreen.kt:253-258
  SkeletonBlock(Modifier.weight(1f).height(dimens.size.dateCellH), radius = dimens.radii.lg)
  ```
- Fix: a headline block + five `Meter`-height lines on 16/50, a headline + chart block + rows on 51, and a ruled three-cell strip in the 54 skeleton.

### F-AP-21 — Tokens borrowed from unrelated roles and arithmetic on tokens
- Screens: 54, 56, 79, 51, 04, 101
- Where: `feature/artist/ArtistProfileScreen.kt:256`, `:1002`; `feature/artist/ArtistProfileSheets.kt:254`; `feature/score/ScoreExplainerScreen.kt:380`; `feature/score/ScoreHistoryScreen.kt:316`, `:359`; `feature/artist/ArtistProfileMedia.kt:110`, `:209`; `designsystem/theme/Dimens.kt:28-30`
- Category: token
- Severity: P3
- Rule: (a) §2 tile radius 18 (`radii.lg`), card radius 16–18; §5 rule 3 tokens only
- What: `size.dateCellH` (a calendar cell) sizes the skeleton stat cells; `component.skeletonTile` is the report note field's `minHeight`; `space.sm` is the gig-progress bar height; `space.xs / 2` and `listThumbW / 2` are new off-ramp values; gallery tiles and the Spotify embed clip at `radii.md` (12) against the 18 the tile spec names; the no-audio card is `radii.xl` (24) while every other card on 04 (packages, prompts, reviews) is `radii.buttonLg` (16).
- Evidence:
  ```kotlin
  // ArtistProfileSheets.kt:254     minHeight = dimens.component.skeletonTile,
  // ScoreExplainerScreen.kt:380    .height(dimens.space.sm)
  // ScoreHistoryScreen.kt:316      horizontalArrangement = Arrangement.spacedBy(dimens.space.xs / 2),
  // ScoreHistoryScreen.kt:359      modifier = Modifier.width(dimens.size.listThumbW / 2),
  // ArtistProfileMedia.kt:110      .clip(RoundedCornerShape(dimens.radii.md))
  ```
- Fix: name the values (`component.noteFieldMin`, `size.progressBar`, `size.deltaColumn`), use `radii.lg` on tiles and `radii.buttonLg` on the no-audio card.

### F-AP-22 — Spacers stack with `spacedBy`, and the top of 04 stacks up to three notices with double padding
- Screens: 04, 100, 103, 50
- Where: `feature/artist/ArtistProfileScreen.kt:381`, `:391`, `:406`, `:417`, `:463`, `:499`, `:1170`, `:1186`; `feature/score/ScoreExplainerScreen.kt:212`, `:234`
- Category: spacing
- Severity: P3
- Rule: rubric §1 spacing — "a `Spacer` that stacks with `spacedBy`", "banners stacked"
- What: The profile column is `spacedBy(xl)` and then adds `Spacer(md)` as its last child, `padding(top = md)` on the self note and on both banners, `padding(top = lg)` on the identity block, and `Spacer(xs)` inside the `spacedBy(md)` reviews block; with a self note and a tile-fallback banner present the gaps read 20 / 36 / 36 / 40 dp down the page. The `AccentNote`, the load-error `Banner` and the report-failure `Banner` can all be on screen together. 50 ends a `spacedBy(lg)` column with `Spacer(xl)` above a bar that adds `top = sm`.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:381    verticalArrangement = Arrangement.spacedBy(space.xl),
  // ArtistProfileScreen.kt:391    modifier = Modifier.padding(top = space.md),
  // ArtistProfileScreen.kt:463    modifier = Modifier.padding(top = space.lg),
  // ArtistProfileScreen.kt:499    Spacer(Modifier.height(space.md))
  // ArtistProfileScreen.kt:1186   Spacer(Modifier.height(space.xs))
  ```
- Fix: `contentPadding`-style top/bottom on the column, no per-child top padding, and one notice slot at the top (self note or failure, the report banner replacing it while active).

### F-AP-23 — "What they play" hand-rolls outline chips; the comment's reason does not hold
- Screens: 04
- Where: `feature/artist/ArtistProfileScreen.kt:1114-1141`; `designsystem/component/Chip.kt:41`
- Category: token
- Severity: P3
- Rule: (a) §2 chip = `surface2` unselected, accent selected, 9×16 r999; (b) 102 uses `Chip`
- What: The tags are `Text` with a hairline `border` on a transparent ground; the KDoc says a filled chip "is the selected state in this app's chip language", but `Chip`'s unselected fill is `surface2`, not the accent, so the outline variant is a fourth chip style rather than the neutral one.
- Evidence:
  ```kotlin
  // ArtistProfileScreen.kt:1134-1140
  modifier = Modifier
      .clip(CircleShape)
      .border(dimens.size.hairline, colors.hairline, CircleShape)
      .padding(horizontal = dimens.component.chipPadH, vertical = dimens.component.chipPadV),
  ```
- Fix: `Chip(label, selected = false, onClick = null)` (or add a read-only flag to `Chip`).

### F-AP-24 — Button and label conventions drift: "Submit report", fragment subtitles, an eyebrow that is a sentence
- Screens: 56, 102, 51, 100
- Where: `feature/artist/ArtistProfileSheets.kt:259`; `feature/artist/ArtistReviewsScreen.kt:160`; `feature/score/ScoreHistoryScreen.kt:143`, `:203-205`; `feature/artist/ArtistProfileScreen.kt:1187`
- Category: copy
- Severity: P3
- Rule: rubric copy conventions — button labels are verbs ("Send request", not "Submit"); eyebrows are labels; peers
- What: The report CTA reads "Submit report" at rest and "Sending report…" in flight. 102's failed subtitle is the lowercase fragment "count unavailable" while 51's is "Last 12 recomputations". The 51 footer sentence has no subject ("Under 5 completed gigs stays on the New tier"). On 100 the eyebrow renders as "THE REST OF THE PROFILE IS FINE" — a sentence in the 11 sp uppercase mono label.
- Evidence:
  ```kotlin
  // ArtistProfileSheets.kt:259     text = if (submitting) "Sending report…" else "Submit report",
  // ArtistReviewsScreen.kt:160     state.failed -> "count unavailable"
  // ArtistProfileScreen.kt:1187    EyebrowLabel("The rest of the profile is fine")
  ```
- Fix: "Send report"; "Count unavailable" (or drop the subtitle); "An artist under 5 completed gigs stays on the New tier…"; eyebrow "Still on this page".

### F-AP-25 — `ScoreRing.kt` is dead code carrying the section's only raw literals
- Screens: (none — component)
- Where: `designsystem/component/ScoreRing.kt:38-39`, `:118`, `:131`, `:134`
- Category: slop
- Severity: P3
- Rule: (c) fact — zero call sites for `ScoreRing(` in `app/src/main`; rubric §4 "dead code"
- What: The ring component ships `64.dp`, `6.dp`, `1.2.sp` ×2 and `4.dp` literals and `ink.copy(alpha = 0.08f)`, but no screen renders it: 50 draws `ScoreDonut` (feature-local), 99 draws `ScoreDisc`, 04 and 16 use text. The literals never reach a pixel, yet a grep for off-token values in the design system hits them first.
- Evidence:
  ```kotlin
  // ScoreRing.kt:38-39
  size: Dp = 64.dp,
  stroke: Dp = 6.dp,
  // ScoreRing.kt:134
  style = AppTheme.type.caption.copy(letterSpacing = 1.2.sp),
  ```
- Fix: delete `ScoreRing.kt` (or promote `ScoreDonut` into it on tokens and use one ring on 50 and 99).

## Section-wide observations
- Accent discipline is the section's weakest point: 4 of 5 loaded pages paint 3+ accent-filled surfaces at once (F-AP-01), and three separate alpha constants (0.3 / 0.22 / 0.4) re-derive the wash that `brandSoft` already defines (F-AP-09).
- The score numeral has no home style: five placements, five specs, only one of them mono (F-AP-03); the profile's stat strip likewise ignores the `monoCount` precedent set by `AccountStatBand` (F-AP-04).
- Every pushed screen but 04 uses `BackHeader`; 04 hand-rolls the bar despite `Headers.kt` naming screen 04 as the trailing-slot use case (F-AP-05). All three sheets hand-roll their heads past `SheetScaffold(title=)` (F-AP-06).
- Section headings: `SectionHeader` (04, 16) vs `EyebrowLabel` (50 Stats, 99, 51) vs bold `rowTitle` (50 Opportunities) — 3 components for one role (F-AP-07). Eyebrow counts per screen stay ≤ 3, so the "log file" pattern is absent.
- Hand-rolled rows outnumber `ListRow` uses 3:2 (DisclosureRow, OpportunityRow, HistoryRow vs the quote row and the overflow sheet); each picks its own chevron tint (F-AP-08).
- Copy is honest but unshared: the New-tier line, the denominator, the recompute rule and the average's precision each exist in 2–3 wordings across 16/50/99/51 (F-AP-13); "server", "weights" and "recomputations" leak the compute job into artist-facing strings (F-AP-14).
- Good: the three states are genuinely three screens on 04/54/55, 100, 102 (three empties) and 80; sheets set `dragHandle = null` + `containerColor = surface` + `skipPartiallyExpanded = true` throughout; 50 uses the shared `SegmentedControl`; lazy lists carry keys; the review filter is memoised (`ArtistReviewsScreen.kt:146`); the Spotify embed's 380 dp is a documented third-party minimum (not flagged); `Icons.Filled.Star` is consistent within the section and with `booking/ReviewSheet.kt:369` (the `Outlined.StarBorder` variants live in `feature/system`, outside AP). Compat aliases: 2 in the section, both in `ArtistProfileMedia.kt` (F-AP-08); no compat colours.
- Not flagged, worth a design call: 50's pinned primary "See what counts" only switches to the Stats segment that the control above already offers; and both 04 `AsyncImage`s omit `crossfade`, which matches the rest of the app (only `signup/SignupFlow.kt` uses it).
