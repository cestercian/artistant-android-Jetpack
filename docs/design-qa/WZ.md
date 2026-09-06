<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

> **Note (lead):** this auditor omitted the per-finding Severity line. Severities were assigned by the lead from the auditor's own ordering and its reported counts (P1 / P2 / P3 = 1 / 10 / 5) and are marked below.

# WZ — Artist setup wizard (screens 72, 37, 38, 24, 39, 40, 41, 42, 43, 44, 45, 46)

## Coverage
| Screen | File(s) | Reviewed | Findings |
| 72 Wizard shell / Save & exit | feature/wizard/WizardScreen.kt, WizardScaffold.kt | yes | F-WZ-01, F-WZ-02, F-WZ-09, F-WZ-14, F-CC-09 |
| 37 Identity | feature/wizard/WizardFormSteps.kt:70-186 | yes | F-WZ-03, F-WZ-04, F-WZ-11, F-CC-09 |
| 38 Location | feature/wizard/WizardFormSteps.kt:188-243 | yes | F-WZ-11 |
| 24 Pricing | feature/wizard/WizardFormSteps.kt:245-424 | yes | F-WZ-04, F-WZ-08, F-WZ-13, F-WZ-14 |
| 39 Tech | feature/wizard/WizardFormSteps.kt:426-500 | yes | F-WZ-02, F-WZ-03, F-WZ-04, F-WZ-05 |
| 40 Availability | feature/wizard/WizardFormSteps.kt:565-700 | yes | F-WZ-04, F-WZ-05, F-WZ-06 |
| 41 Cover | feature/wizard/WizardMediaSteps.kt:88-380 | yes | F-WZ-02, F-WZ-07, F-WZ-10, F-CC-02 |
| 42 Socials | feature/wizard/WizardMediaSteps.kt:382-477 | yes | F-WZ-11 |
| 43 Bio | feature/wizard/WizardMediaSteps.kt:479-573 | yes | F-WZ-04, F-WZ-12 |
| 44 Samples | feature/wizard/WizardMediaSteps.kt:575-814 | yes | F-WZ-04, F-WZ-12, F-WZ-13, F-WZ-15, F-CC-02 |
| 45 Preview | feature/wizard/WizardPublishSteps.kt:68-261 | yes | F-WZ-01, F-WZ-02, F-WZ-03, F-WZ-14 |
| 46 Done | feature/wizard/WizardPublishSteps.kt:263-416 | yes | F-WZ-06, F-WZ-16, F-CC-15 |

## Findings

### F-WZ-01 — Preview draws its own title twice, at two type steps
- Screens: 45
- Where: `feature/wizard/WizardScaffold.kt:66,79-89`; `feature/wizard/WizardScreen.kt:270-282`
- Category: hierarchy
- Severity: P1 (assigned by the lead, see note)
- Rule: (b) internal — the same string is rendered by two composables on one screen; (a) §2 "header 56 tall: title 26 + subtitle, **or** centred 17/700 with a 42 back circle" — this screen ships both.
- What: `WizardTopBar` swaps the progress track for a centred `sectionTitle` "Preview" + `caption` "Exactly what clients see" on the Preview step. But `WizardStepScaffold` unconditionally emits `WizardStepHeader(step)` as its first item for *every* step, and that header prints `wizardStepTitle(step)` at `screenTitle` (26/700) plus the same subtitle at `body`. So screen 45 opens with "Preview / Exactly what clients see" in the bar and "Preview / Exactly what clients see" again, twice the size, 12dp under it. The 26sp repeat also then competes with the 21sp `displaySub` stage name at `WizardPublishSteps.kt:144`, so the top third of the screen has three titles.
- Evidence:
  ```kotlin
  // WizardScaffold.kt:66
  item(key = "wizard.headline") { WizardStepHeader(step) }
  // WizardScreen.kt:271-281 (Preview branch of the top bar)
  Text(wizardStepTitle(step), style = AppTheme.type.sectionTitle, color = colors.ink)
  Text(wizardStepSubtitle(step), style = AppTheme.type.caption, color = colors.ink4)
  ```
- Fix: give `WizardStepScaffold` a `showHeader: Boolean = step != WizardStep.Preview` (or pass the header in from the step), so a step that puts its title in the bar does not repeat it in the list.

### F-WZ-02 — Text-only buttons are 33dp tall; eight of them on the Preview step
- Screens: 45, 72, 39, 41
- Where: `feature/wizard/WizardPublishSteps.kt:236-256`; `WizardScreen.kt:364-380`; `WizardFormSteps.kt:485-497`; `WizardMediaSteps.kt:276-285`
- Category: slow
- Severity: P2 (assigned by the lead, see note)
- Rule: (c) fact — a 12.5sp label plus `space.sm`/`space.md` padding measures ~33dp; `dimens.size.rowMin` is 44 and no call site uses `minimumInteractiveComponentSize()`.
- What: every secondary action in this section is a bare `Text` with `clickable` and 8–12dp of padding: the preview's `EditPill` (one per cover, identity and each of the five rows — seven or eight on one screen), the footer's tappable "Skip for now", the tech step's "Add", and the cover step's destructive "Remove photo". `caption` is 12.5sp (≈17dp line box); `+ 2 × space.sm (8)` = 33dp, `+ 2 × space.sm` vertical on the Edit pill = 33dp. All are under the 44dp floor the repo already tokenises as `size.rowMin`, and the two most-tapped ones (Edit, Skip) sit next to items they must not be confused with.
- Evidence:
  ```kotlin
  // WizardPublishSteps.kt:237-253 — EditPill
  Text("Edit", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold), …)
      .padding(horizontal = dimens.space.md, vertical = dimens.space.sm)
  // WizardMediaSteps.kt:278-285 — the only way to delete a staged cover
  Text("Remove photo", …).clickable(role = Role.Button, onClick = vm::clearCoverPick)
      .padding(dimens.space.sm)
  ```
- Fix: give these a `defaultMinSize(minHeight = dimens.size.rowMin)` (or route them through a shared `TextAction` composable that does), rather than padding a caption.

### F-WZ-03 — `caption` is drawn at three weights; the 700 variant is an un-tokenised step
- Screens: 37, 39, 41, 44, 45, 46, 72
- Where: `feature/wizard/WizardFormSteps.kt:178,488`; `WizardMediaSteps.kt:182,279`; `WizardPublishSteps.kt:238,396`; `WizardScreen.kt:366-368`
- Category: token
- Severity: P2 (assigned by the lead, see note)
- Rule: (a) §2 type table has one `caption` (12.5/400); (b) the design system's own small label is `caption.copy(fontWeight = SemiBold)` (`designsystem/component/AppTextField.kt:104`, mirrored deliberately by `WizardFieldLabel` at `WizardFormSteps.kt:718-728`).
- What: six sites in this section set `caption.copy(fontWeight = FontWeight.Bold)` (700) and one sets it conditionally to `Bold`/`Medium` — so the same 12.5sp caption appears at 400 (helper text, meta), 600 (field labels) and 700 ("Available", "Add", "Recommended 4:5", "Remove photo", "Edit", the "New" badge, the footer note). Three weights of one step means the small type carries no consistent meaning: "Remove photo" (destructive) and "Edit" (navigational) and "New" (a badge) are all the same object typographically. §2 already has a `badge` step (11.5/700/+0.02em) for the badge case.
- Evidence:
  ```kotlin
  // WizardScreen.kt:366-368 — a fourth weight invented inline
  style = AppTheme.type.caption.copy(
      fontWeight = if (skippable) FontWeight.Bold else FontWeight.Medium,
  ),
  ```
- Fix: use `type.badge` for the "New" pill and add one `captionStrong` (or reuse `AppTextField`'s SemiBold label style) for the text actions; delete the inline `.copy(fontWeight = …)`.

### F-WZ-04 — Four different components label a block across eleven steps
- Screens: 37, 24, 39, 40, 41, 43, 44
- Where: `WizardFormSteps.kt:250` (`SectionHeader`), `:447,479,579,755` and `WizardMediaSteps.kt:99,516` (`WizardFieldLabel`), `WizardFormSteps.kt:668` and `WizardMediaSteps.kt:581` (`EyebrowLabel`), `WizardFormSteps.kt:75-99` (AppTextField's own `label`)
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) internal — one wizard, four conventions for "what this block is".
- What: Pricing opens with `SectionHeader(title = "Packages")` (17/700); Tech, Availability, Cover and Bio label their blocks with `WizardFieldLabel` (12.5/600 `ink4`); Samples and the availability badge use `EyebrowLabel` (mono caps); Identity and Socials rely on `AppTextField(label = …)`. Worse, the Pricing step's six fields carry **no label at all** — "Package name", "60 min" and "0" are hints only, so the moment the artist types, the tier's three inputs are unlabelled boxes (and the price box's only marker is a leading "₹"). The step that publishes real money is the one step with no field labels.
- Evidence:
  ```kotlin
  // WizardFormSteps.kt:325-362 — three fields, three hints, zero labels
  AppTextField(value = row.name,     hint = "Package name", …)
  AppTextField(value = row.duration, hint = "60 min", …)
  AppTextField(value = row.price,    hint = "0", leading = { Text("₹", …) }, …)
  ```
- Fix: pick one block-label convention (`WizardFieldLabel` for questions, `EyebrowLabel` only for labelled regions per its own docstring) and drop `SectionHeader` here; give the package fields `label =` "Package", "Length", "Your fee".

### F-WZ-05 — "Selected" is drawn three different ways inside one wizard
- Screens: 37/38/43 (chips), 39 (tech), 40 (days)
- Where: `WizardFormSteps.kt:521-556` (CheckRow), `:612-643` (DayStrip), `:729-772` (WizardChipSection → `designsystem/component/Chip.kt:54-62`)
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) internal + (a) §2 "chip: padding 9×16, radius 999, selected = accent"; `Chip.kt:62` sets an unselected label to `ink2`.
- What: three multi-select controls, three visual languages. A `Chip` fills with `accent` and keeps `ink2` when off. `CheckRow` (tech presets) fills with `brandSoft`, strokes with `accent` and shows a 20dp accent tick box. `DayStrip` fills a square with `accent` and greys the letter to `ink4` when off — one step darker than the chip beside it in the same step. So on the Availability step the day squares and the time-slot chips answer the same question ("when do you play?") and disagree about both the selected fill shape and the unselected ink.
- Evidence:
  ```kotlin
  // WizardFormSteps.kt:625-638 — day square
  .background(if (isOn) colors.accent else colors.surface2)
  Text(day.take(1), style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
       color = if (isOn) colors.onAccent else colors.ink4)
  ```
- Fix: use `ink2` for the unselected day letter and either give the tech presets the chip's accent fill or give the day strip the `brandSoft`+stroke treatment — two languages at most (grid-of-answers vs list-of-demands), not three.

### F-WZ-06 — Availability and Done spend the accent three ways at once
- Screens: 40, 46
- Where: `WizardFormSteps.kt:625,679,691` ; `WizardPublishSteps.kt:295-303,384,400`
- Category: hierarchy
- Severity: P2 (assigned by the lead, see note)
- Rule: (a) §2 Principles "one accent per screen" — the accent is "the one signal: primary CTA, selected chip, badges".
- What: on Availability the artist can have seven accent-filled day squares, a solid-accent "Thu–Sun evenings" badge pill inside the preview card, accent-filled time chips, and the accent CTA pinned below — the lime stops meaning "this is the action". On Done, a 64dp accent disc, an accent "New" pill, an `accent`-stroked `brandSoft` card and the accent CTA are on screen together, so the eye lands on the disc rather than "Open my dashboard".
- Evidence:
  ```kotlin
  // WizardFormSteps.kt:676-697 — badge pill hand-rolled rather than Pill/StatusPill
  Row(Modifier.clip(CircleShape).background(colors.accent)
      .padding(horizontal = dimens.component.chipPadH, vertical = dimens.component.chipPadV))
  ```
- Fix: render the availability badge as the app's `StatusPill`/`Pill` in `surface2`+`ink` (it is a preview of a label, not a live control), and drop the Done screen's "New" pill to `surface2`/`accentInk` so the disc and the CTA are the only accent-filled objects.

### F-WZ-07 — Gradient swatches drift from the press kit's row and paint the retired palette
- Screens: 41
- Where: `WizardMediaSteps.kt:344-380`; peer `feature/epk/EpkPanes.kt:443-462`
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) peer `EpkPanes.kt:447` sizes the same swatch `size(swatchW = 56, swatchH = 38)` and rings the selected one in the accent; (a) `ArtistGradient.palette` is the retired dark violet/black palette — already filed as **F-DS-01**.
- What: the press kit and the wizard set the same field (cover gradient) with two different pickers. The wizard stretches its swatches with `weight(1f)` and only honours `swatchH`, so the 56dp `swatchW` token exists and is ignored on one of its two call sites; it rings the picked swatch in `colors.ink` where the press kit uses the accent; and it enumerates the palette from a private `GRADIENT_LAST = 5` instead of `ArtistGradient.count`, which the press kit uses — two sources of truth for how many swatches exist. Both draw the retired dark palette (F-DS-01), which on this screen is a violet-to-black block sitting on `#fafaf6` under the words "Behind your photo".
- Evidence:
  ```kotlin
  // WizardMediaSteps.kt:349-364
  (0..GRADIENT_LAST).forEach { index -> Box(Modifier.weight(1f).height(dimens.size.swatchH)
      .background(Brush.linearGradient(ArtistGradient.palette(index)))
      .border(if (isSelected) dimens.size.stroke else dimens.size.hairline,
              if (isSelected) colors.ink else colors.hairline, shape))
  ```
- Fix: extract the press kit's swatch row into `designsystem/component/` and call it from both; when F-DS-01 relights `ArtistGradient`, both surfaces follow.

### F-WZ-08 — Pricing hand-rolls Checkout's money row: lowercase label, raw divider, no mono on the fee
- Screens: 24
- Where: `WizardFormSteps.kt:365-397`; peer `feature/booking/CheckoutScreen.kt:168-176`
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) peer `CheckoutScreen.kt:172-176` renders the same fact as `TermRow(label = "Artist fee", value = formatInr(...), emphasis = true)` under an `HRule`.
- What: the same "label · money" pattern is built from scratch here. The artist's own number is labelled **"your fee"** in lowercase `caption`/`ink4`, and the host's number "Host sees all-in" in `subtitle`/`ink3` — two labels for the two halves of one comparison, at two steps of the ramp and two capitalisations, three lines apart. Checkout calls the same quantity "Artist fee". Only the all-in figure is mono (`monoDock`); the fee the artist types stays in the field's sans, so the step whose premise is "these two numbers belong together" types them in two families. The rule between them is a fully-qualified `androidx.compose.material3.HorizontalDivider` (also at `WizardScreen.kt:466`) where the app has `HRule` — P3 on its own, and worth noting `HRule` still paints the `line` compat alias while these two calls pass the newer `hairline`, so the fix is to update `HRule`, not to inline more dividers.
- Evidence:
  ```kotlin
  // WizardFormSteps.kt:367-396
  Text("your fee", style = AppTheme.type.caption, color = colors.ink4, …)
  androidx.compose.material3.HorizontalDivider(thickness = dimens.size.hairline, …)
  Text("Host sees all-in", style = AppTheme.type.subtitle, color = colors.ink3, …)
  Text(allIn?.let(::formatInr) ?: "—", style = AppTheme.type.monoDock, color = colors.ink)
  ```
- Fix: label it "Artist fee" to match Checkout, set both numbers in the mono step, and swap the two `HorizontalDivider` call sites for `HRule` (repointing `HRule` at `colors.hairline`).

### F-WZ-09 — Save & exit hand-rolls a sheet header that `SheetScaffold` already provides
- Screens: 72
- Where: `WizardScreen.kt:422-441`; peers `feature/booking/RequestQuoteScreen.kt:221,247`, `feature/booking/ReviewSheet.kt:206`, `feature/booking/TechRiderSheet.kt:52`, `feature/messages/ChatQuoteCard.kt:200`, `designsystem/component/SheetScaffold.kt:49-53`
- Category: consistency
- Severity: P2 (assigned by the lead, see note)
- Rule: (b) `SheetScaffold` takes `title: String? = null` and six peer sheets pass it; this is the only sheet in the app that draws its own.
- What: the sheet calls `SheetScaffold(showGrabber = true)` and then builds a title row by hand — a `Spacer(iconCircleSm)`, a centred `sectionTitle`, and a 40dp close `IconCircle` — so its title sits at a different offset from every other sheet in the app and gains a close affordance no other sheet has (on top of the grabber, the scrim tap and system back: four ways to dismiss).
- Evidence:
  ```kotlin
  // WizardScreen.kt:422-441
  SheetScaffold(showGrabber = true) {
      Row(Modifier.fillMaxWidth(), …) {
          Spacer(Modifier.width(dimens.component.iconCircleSm))
          Text("Save & exit", style = AppTheme.type.sectionTitle, textAlign = TextAlign.Center, …)
          IconCircle(icon = Icons.Filled.Close, …, size = dimens.component.iconCircleSm)
  ```
- Fix: `SheetScaffold(title = "Save & exit")` and delete the row; the grabber plus "Keep going" is already two ways out.

### F-WZ-10 — The cover slot says 4:5, crops 3:4, and previews 4:3
- Screens: 41, 45
- Where: `WizardMediaSteps.kt:138,145-176,181`; `WizardPublishSteps.kt:93,261`; token `designsystem/theme/Dimens.kt:577` (`editorial = 3f/4f`)
- Category: copy
- Severity: P2 (assigned by the lead, see note)
- Rule: (c) fact — `aspect.editorial` is 0.75 (3:4); the badge printed over it reads "Recommended 4:5" (0.8); the preview card is 4:3 (1.333).
- What: the artist is shown a portrait 3:4 box with a dark pill on it saying "Recommended 4:5", then on the next screen their photo appears in a landscape 4:3 card. Whichever is right, two of the three are wrong, and the one the artist will act on (the badge) is the one that matches nothing. Separately, the gradient the step lets them choose is painted **only inside the `else` branch** — when there is no photo the slot shows `placeholder` grey and an outlined image glyph — even though the copy under the picker promises the gradient "stands in if you skip one" and the composable's own docstring claims it paints the gradient first "rather than as an `else` branch". The one state where the gradient is the entire cover is the state that never shows it.
- Evidence:
  ```kotlin
  // WizardMediaSteps.kt:145-168
  if (state.pendingCoverPath == null) { Icon(Icons.Outlined.Image, …); Text("Cover photo", …) }
  else { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(ArtistGradient.palette(...)))) ; AsyncImage(...) }
  // WizardMediaSteps.kt:181 — over both branches
  "Recommended 4:5",
  ```
- Fix: paint the gradient as the slot's floor in both branches, and make the badge state the ratio the box actually is (and the preview card use the same one).

### F-WZ-11 — Helper text under every field, and an explanatory Banner on five of nine steps
- Screens: 42, 37, 38, 24, 43
- Where: `WizardMediaSteps.kt:400,411,422,475` and `:427-436`; `WizardFormSteps.kt:100-112`, `:222-241`, `:270-283`; `WizardMediaSteps.kt:498-509`
- Category: slop
- Severity: P2 (assigned by the lead, see note)
- Rule: (a) rubric §4 "helper text under every field"; §4 "a paragraph on a utility screen that explains the app's architecture".
- What: the Socials step gives all three fields a `caption` helper *and* closes with an info Banner, so a screen with three inputs carries seven blocks of prose. Across the wizard, five steps end in an `Info` Banner whose job is to explain the app to the artist ("Pick one and step 3 opens with tiers to edit instead of an empty form", "Radius and event types are saved with your setup — this app doesn't publish them yet"). Three of them are also worded as engineering (instances of **F-CC-05**): "We deep-link clients straight into the Instagram app" (`:400`), "The queue is written to disk, so closing the app pauses it rather than losing it" (`:775-777`), plus the two the sweep already has. And the Banner titles end in full stops ("Category seeds your pricing tiers.", "These links are not verified.") while the failure Banners in the same section do not ("Not saved", `WizardScreen.kt:153`).
- Evidence:
  ```kotlin
  // WizardMediaSteps.kt:398-401
  helper = "We deep-link clients straight into the Instagram app.",
  ```
- Fix: keep the helper only where it teaches something the hint cannot (Spotify's "Profile → Share"), cut the info Banners on Identity and Bio, and settle one rule for Banner-title punctuation.

### F-WZ-12 — Two hand-rolled `BasicTextField`s lose the app's focus state
- Screens: 43, 44
- Where: `WizardMediaSteps.kt:513-545` (bio), `:652-661` (sample title); peer `designsystem/component/AppTextField.kt:95-135`
- Category: consistency
- Severity: P3 (assigned by the lead, see note)
- Rule: (b) `AppTextField` swaps the well to `surface` and the hairline to `component.focusStroke` on focus; both copies here are static.
- What: the bio field re-draws AppTextField's well by hand (`radii.control` + `surface2` + hairline + `space.lg`) but never changes on focus, so it is the one field in the wizard that gives no feedback when the cursor lands in it. The sample title is worse: it is an editable `BasicTextField` drawn as plain `rowTitle` text on `surface3` with no well, no label and no hint — nothing on the row says the clip's name can be renamed, and a blank title renders as an empty row. (The bio's hand-roll is partly forced: `AppTextField` exposes `maxLines` but no `minLines`.)
- Evidence:
  ```kotlin
  // WizardMediaSteps.kt:652-660 — an input that looks like a label
  BasicTextField(value = title, onValueChange = onTitleChange, singleLine = true,
      textStyle = AppTheme.type.rowTitle.copy(color = colors.ink), …)
  ```
- Fix: add `minLines` to `AppTextField` and use it for the bio; give the sample title the same field chrome (or an explicit edit affordance).

### F-WZ-13 — The sample row's accent play disc is inert, and diverges from the design system's `SampleRow`
- Screens: 44
- Where: `WizardMediaSteps.kt:619-674`; peer `designsystem/component/SampleRow.kt:54-72`
- Category: slop
- Severity: P3 (assigned by the lead, see note)
- Rule: (c) fact — the disc has no `clickable`; (b) a public `SampleRow` composable of the same name already exists.
- What: every staged clip gets a 32dp **accent-filled** circle with a `Filled.PlayArrow` in it that does nothing (the docstring admits it: "drawn but inert"). It is the single loudest object on the step — up to six of them, each the same fill as the Continue button — and it is the one thing on the screen that looks pressable and is not. The design system's `SampleRow` draws the same concept with a `PlayControl` that actually plays, on a bare row with vertical padding; this private twin wraps it in a `surface3` card. Two components, one name, two looks for a clip.
- Evidence:
  ```kotlin
  // WizardMediaSteps.kt:636-650 — no clickable anywhere on this Box
  Box(Modifier.size(dimens.size.avatarSm).clip(CircleShape).background(colors.accent)) {
      Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = colors.onAccent, …)
  ```
- Fix: drop the disc to `surface2`/`ink4` (or wire it to the existing `SamplePlayback`), and rename the private composable so it does not shadow the design-system one.

### F-WZ-14 — Preview rows and progress tracks are one-offs where shared components exist
- Screens: 45, 72, 44
- Where: `WizardPublishSteps.kt:186-215`; `WizardScaffold.kt:110-141`; `WizardMediaSteps.kt:789-810`
- Category: consistency
- Severity: P3 (assigned by the lead, see note)
- Rule: (b) internal — `ListRow` (56–64, hairline, chevron) is the app's list row; the two progress tracks in this section colour themselves differently.
- What: the seven preview rows are hand-rolled `surface3` blocks at `radii.buttonLg` with a text "Edit" instead of `ListRow`, and their value line is `caption` (12.5) while `PreviewIdentity`'s meta line 40dp above is `subtitle` (13.5) — two steps for the same role on one screen. The same `surface3` + rounded-block shape is also the tech `CheckRow`, the upload strip and the sample row, at two different radii (`buttonLg` vs `card`), so "a filled block" means four unrelated things here. And the two progress tracks disagree: the step track is `accent` on `hairline` (`WizardScaffold.kt:138`), the upload track is `onAccent` on `onAccent @ 18%` (`WizardMediaSteps.kt:795-808`) — near-black on lime, which is the inverse of the first.
- Evidence:
  ```kotlin
  // WizardPublishSteps.kt:205-211
  Text(label, style = AppTheme.type.rowTitle, color = colors.ink)
  Text(value, style = AppTheme.type.caption, color = if (filled) colors.ink3 else colors.ink4, …)
  ```
- Fix: build the preview rows from `ListRow` with a trailing Edit action, set the value line at `subtitle`, and give both progress tracks one token pair.

### F-WZ-15 — Off-palette tints and stand-in glyphs: `.copy(alpha=…)`, "AUDIO CLIP", a full-width "＋"
- Screens: 24, 44
- Where: `WizardFormSteps.kt:383,428` ; `WizardMediaSteps.kt:801,814` ; `WizardMediaSteps.kt:686` ; `WizardFormSteps.kt:830`
- Category: token
- Severity: P3 (assigned by the lead, see note)
- Rule: (a) rubric §1 "alpha hacks that manufacture an off-palette tint"; (b) peer `domain/sample/SamplePlayback.kt:121-126` returns `null` for an unknown duration rather than a string.
- What: three small off-system choices. `colors.accent.copy(alpha = 0.5f)` invents a half-lime rule inside the highlighted pricing card and `colors.onAccent.copy(alpha = 0.18f)` invents a grey for the upload track — neither is in §2, which already ships `hairline`, `lineStrong` and `brandSoft` for exactly these jobs. A clip whose duration could not be measured prints the shouty literal **"AUDIO CLIP"** where every other row prints "1:42", in `monoPill` (which is not the uppercase step) — the design-system helper answers `null` for the same case, and the section's own convention for an absent value is the em dash it uses at `WizardFormSteps.kt:394`. And `DashedAction` fakes an icon with a full-width plus and two spaces, `"＋  $label"`, in the sans.
- Evidence:
  ```kotlin
  // WizardMediaSteps.kt:686
  if (seconds <= 0.0) return "AUDIO CLIP"
  // WizardFormSteps.kt:830
  if (enabled) "＋  $label" else label,
  ```
- Fix: use `hairline`/`lineStrong`/`brandSoft` instead of the two alpha copies, print "—" for an unmeasured clip, and lead `DashedAction` with a real `Icons.Outlined.Add`.

### F-WZ-16 — The Done disc is 64dp where the app's outcome disc token is 74dp
- Screens: 46
- Where: `WizardPublishSteps.kt:293-303`; peers `feature/booking/BookingChrome.kt:438`, `feature/system/UpdateRequiredScreen.kt:83`; tokens `designsystem/theme/Dimens.kt:79,594`
- Category: token
- Severity: P3 (assigned by the lead, see note)
- Rule: (b) `dimens.funnel.outcomeDisc` (74) is the token for "the disc on a terminal outcome screen"; this screen uses `dimens.size.ringMd` (64).
- What: "You're live." is the wizard's outcome screen and the booking funnel's "Request sent" is its own; they draw the same object at two sizes from two token families, so the app's two success moments do not match. Same finding shape as the `RevealOnAppear` wrapper on this screen, already filed as **F-CC-15**.
- Evidence:
  ```kotlin
  // WizardPublishSteps.kt:294-297
  Modifier.size(dimens.size.ringMd).clip(CircleShape).background(colors.accent)
  ```
- Fix: use `dimens.funnel.outcomeDisc` here.

## Section-wide observations
- **The shell is genuinely shared** — one `WizardStepScaffold` + `WizardTopBar` + `WizardFooter` own the header, progress track, counter and CTA for all eleven steps; no step re-draws chrome. The one leak is the Preview title (F-WZ-01).
- **Field conventions drift step by step**, as expected from eleven authors' worth of steps: 6 steps use `AppTextField` (Identity ×3, Pricing ×3/row, Socials ×3, Tech ×1), 2 hand-roll `BasicTextField` (Bio, sample title), and only Identity + Socials pass a `label`; helper text appears under 3 of 3 Socials fields, 1 of 4 Identity fields and none of Pricing's.
- **Small text actions are the section's weakest object**: 4 distinct ones (Edit, Skip for now, Add, Remove photo), all ~33dp, all `caption`/700, three colours (`accentInk`, `ink3`, `danger`) — see F-WZ-02/F-WZ-03.
- **Icon families are mixed as F-CC-02 predicts**; the specific glyphs here are `Filled.Close`, `Filled.Check`, `Filled.PhotoCamera`, `Filled.PhotoLibrary`, `Filled.PlayArrow`, `Filled.Upload`, `Filled.ContentCopy` against `Outlined.Image` (×2) and `Outlined.DeleteOutline` — and the two *deletes* in the section disagree with each other: a clip is removed by an outlined bin glyph, a cover by red text.
- **Compat aliases are nearly gone**: 4 × `colors.brandSoft` (`WizardFormSteps.kt:314,530`, `WizardMediaSteps.kt:743`, `WizardPublishSteps.kt:384`) and 1 × `type.monoStat` (`WizardFormSteps.kt:852`) — instances of **F-CC-10**, nothing else.
- **Both spinners are the ones the lead already has** (`WizardScreen.kt:171` restoring, `WizardFormSteps.kt:160` handle check) — **F-CC-09**. No scrim, no `delay()`, no work in composition worth flagging: the lists are lazy with stable keys, `popularBadgeWouldMeanSomething` is hoisted out of the row loop (`WizardFormSteps.kt:248`), and `AnimatedContent`'s fade goes through `motionTween` so reduce-motion collapses it.
- **The wizard's info Banners carry the section's whole "honesty" budget** — 5 of 9 form steps end in one, and 3 of the 5 explain implementation rather than consequence (F-WZ-11, F-CC-05).
- **The accent-filled CTA sometimes says "Skip for now"** (`WizardLogic.kt:692-695`): on an empty optional step the one accent object on the page is labelled with the escape hatch, and the same words then move under the button once the step is filled. Deliberate and documented, but it means "the lime button" is not reliably "the thing that moves you forward".
