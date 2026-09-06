<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# PK — Press kit & media (screens 23, 87, 76, 65, 66, 67, 68, 74, 75)

## Coverage
| Screen | File(s) | Reviewed | Findings |
| 23 Press kit hub (filled) | `feature/epk/EpkScreen.kt`, `EpkHub.kt`, `EpkPressKit.kt`, `EpkPanes.kt` (ShareLinkSection) | yes | F-PK-01, F-PK-04, F-PK-06, F-PK-11, F-PK-13, F-PK-15 |
| 87 Empty kit (invitation rows) | `feature/epk/EpkScreen.kt:555-566,638-643`, `EpkHub.kt:457-511` | yes | F-PK-11, F-PK-13 |
| 76 Hub other state (upload banner) | `feature/epk/EpkHub.kt:129-229`, `EpkPressKit.kt:295-325` | yes | none (banner is the strongest block in the section) |
| 65 Add cover sheet | `feature/epk/EpkSheets.kt:249-298` | yes | none |
| 66 Stalled uploads sheet | `feature/epk/EpkSheets.kt:787-897`, `EpkPressKit.kt:352-368` | yes | F-PK-03, F-PK-10 |
| 67 Edit bio sheet | `feature/epk/EpkSheets.kt:322-407` | yes | F-PK-04, F-PK-09 |
| 68 Add personality sheet | `feature/epk/EpkSheets.kt:425-599` | yes | F-PK-07, F-PK-09 |
| 74 Edit link sheet | `feature/epk/EpkSheets.kt:617-711` | yes | F-PK-12 |
| 75 Add audio sheet | `feature/epk/EpkSheets.kt:732-771` | yes | F-PK-10 |
| (panes behind 23: gallery / samples / pricing / tech / links) | `feature/epk/EpkPanes.kt`, `EpkComponents.kt` | yes | F-PK-02, F-PK-04, F-PK-05, F-PK-06, F-PK-08, F-PK-12, F-PK-16 |

Cross-cutting instances in this section, already filed by the lead — not re-filed:
F-CC-02 (`EpkHub.kt:531-537` — all six section glyphs are Filled/AutoMirrored.Filled,
incl. `AutoMirrored.Filled.Chat`), F-CC-03 (`EpkSheets.kt:893-894` — a 54 PrimaryButton
beside a 50 SecondaryButton in one row), F-CC-09 (`EpkScreen.kt:222-223`),
F-CC-10 (itemised below as F-PK-06 because this section owns the list), F-CC-12
(`EpkHub.kt:269,335`; `EpkPanes.kt:364` — no `crossfade`), F-CC-13 (`EpkScreen.kt:216`),
F-CC-15 (`EpkScreen.kt:243`), F-DS-01 (`EpkPanes.kt:449` `ArtistGradient.palette(index)`
— the retired dark violet/black palette is the press kit's cover fallback and its
six-swatch picker; the wizard's twin at `feature/wizard/WizardMediaSteps.kt:358` is the
other caller).

## Findings

### F-PK-01 — Delete the press kit's own toast host; it fires under the tab bar
- Screens: 23, 87, 76
- Where: `feature/epk/EpkScreen.kt:267`; peer `navigation/ArtistantNavHost.kt:198-210`; `designsystem/component/Toast.kt:83`
- Category: consistency
- Severity: P1
- Rule: (c) fact + (b) the root host at `ArtistantNavHost.kt:209` passes `bottomPadding = gap + lightTabBarHeight()`
- What: `ArtistTab.Epk` (`navigation/ArtistTabsScaffold.kt:134`) is one of the four artist
  tabs, so the light tab bar (§2: height 88 incl. the home-indicator zone) owns the bottom
  edge of this screen. The root host clears it explicitly; the press kit's own host takes
  the default `bottomPadding = component.toastGap` = 22dp, so every confirmation this
  screen raises — "Pricing saved.", "Photo added.", "Photo order saved.", "Upload
  discarded." (`EpkViewModel.kt:1190,1434,1520,589`) — is drawn ~88dp lower than every
  other toast in the app, inside the tab bar's band. It also passes no `key`, so two
  identical notes in a row (two photos removed) do not re-arm the display timer.
- Evidence:
  ```kotlin
  // EpkScreen.kt:267
  ToastHost(message = state.statusNote, onDismiss = viewModel::consumeStatusNote)
  // ArtistantNavHost.kt:209
  bottomPadding = if (gate is RootGate.Tabs) gap + lightTabBarHeight() else gap,
  ```
- Fix: route `statusNote` through the root `ToastVm` and delete the in-package host (the
  migration CLAUDE.md already calls outstanding).

### F-PK-02 — Lime-on-white text: seven tap targets in the panes are unreadable
- Screens: 23 (panes)
- Where: `feature/epk/EpkComponents.kt:85,146,183`; `feature/epk/EpkPanes.kt:403,875`; correct peers `EpkPanes.kt:920,1079`
- Category: token
- Severity: P1
- Rule: (a) §2 — `accent` `#d6f84b` is a FILL; `accentInk` `#5e7307` is "accent used *as text/icon* on light"
- What: `EpkSectionHeader`'s action label is painted `colors.brand` (the accent), which is
  the label on all seven section actions — "+ Add" (photos, samples, links), "+ Add tier",
  "Add" — plus "Make cover" in the photo action row. `#d6f84b` on `#ffffff` is roughly
  1.1:1; the words are legible only as a shape. The same feature already does this
  correctly twice — "Edit" at `EpkPanes.kt:920` and "COPY" at `:1079` are `accentInk`. The
  inline field's cursor (`:146`) and its filled-state underline (`:183`, accent at 40%)
  disappear against white for the same reason.
- Evidence:
  ```kotlin
  // EpkComponents.kt:85
  color = if (actionEnabled) colors.brand else colors.ink4,
  // EpkPanes.kt:403
  EpkRowAction("Make cover", { onMove(index, 0) }, tone = colors.brand)
  // EpkComponents.kt:183
  .background(if (value.isBlank()) colors.line else colors.brand.copy(alpha = 0.4f)),
  ```
- Fix: `colors.accentInk` for every accent-as-text site; `colors.ink` for the cursor and
  `colors.ink` at hairline weight for the active underline.

### F-PK-03 — Screen 66 shows one accent CTA per stalled card plus "Retry all"
- Screens: 66
- Where: `feature/epk/EpkSheets.kt:893` (per card); `:831` ("Retry all")
- Category: hierarchy
- Severity: P1
- Rule: (a) §2 "one accent per screen"; the accent is "the one signal: primary CTA"
- What: `StalledUploadCard` gives every stalled upload its own full-accent `PrimaryButton`,
  and the sheet adds an accent "Retry all" underneath once there is more than one. Three
  stalled uploads renders four lime buttons stacked in one sheet, so nothing on the screen
  is the primary action. The pair inside the card is also the F-CC-03 mismatch — a 54dp
  PrimaryButton beside a 50dp SecondaryButton on the same baseline.
- Evidence:
  ```kotlin
  // EpkSheets.kt:889-895
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimens.space.sm)) {
      PrimaryButton("Retry", onRetry, modifier = Modifier.weight(1f))
      SecondaryButton("Discard", onDiscard, modifier = Modifier.weight(1f))
  }
  // EpkSheets.kt:831
  if (uploads.size > 1) { PrimaryButton("Retry all", onRetryAll, fullWidth = true) }
  ```
- Fix: make the per-card pair two `SecondaryButton`s (or two `EpkRowAction` words) and
  leave "Retry all" as the sheet's single accent CTA.

### F-PK-04 — Four different treatments for "this is a group label", two on screen 23
- Screens: 23, 67, and every pane
- Where: `feature/epk/EpkScreen.kt:574,609,634` (`SectionHeader`); `feature/epk/EpkComponents.kt:71` used at `EpkPanes.kt:280,485,564,835,892,961,1059`; `feature/epk/EpkSheets.kt:375`
- Category: consistency
- Severity: P2
- Rule: (a) §2 "monoLabel = JetBrains Mono 11 uppercase" for eyebrows / `sectionTitle` 17/700 for section headers; (b) `designsystem/component/SectionHeader.kt:52,93` already ships both
- What: The hub labels "Cover", "Gallery" and "Sections" with the shared `SectionHeader`
  (17/700, ink, sentence case). Directly below them, on the same scroll, `ShareLinkSection`
  labels its block "SHARE LINK" through `EpkSectionHeader` — `type.caption` (sans 12.5)
  uppercased at the call site, in `ink3`. An uppercase string in the sans is exactly what
  `EyebrowLabel` (monoLabel) exists to prevent. The bio sheet adds a third spelling,
  `caption.copy(fontWeight = SemiBold)` for "What you offer", and the panes carry seven
  more instances of the uppercased-caption version.
- Evidence:
  ```kotlin
  // EpkComponents.kt:71
  Text(title.uppercase(Locale.US), style = AppTheme.type.caption, color = colors.ink3)
  // EpkScreen.kt:574 / 609 / 634 — same page
  if (!bare) SectionHeader("Cover")
  // EpkSheets.kt:375
  style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
  ```
- Fix: retire `EpkSectionHeader` in favour of `SectionHeader` (with its `actionLabel`
  slot), or make it a thin wrapper over `EyebrowLabel` if the panes really want an eyebrow.

### F-PK-05 — The panes still ship the pre-redesign field and chip
- Screens: 23 (panes) vs 67, 68, 74
- Where: `feature/epk/EpkComponents.kt:108-186` (`EpkField`, 5 call sites in `EpkPanes.kt`), `:196-239` (`EpkChip`, 5 call sites); peers `EpkSheets.kt` uses `AppTextField` ×4 and `Chip` ×2
- Category: consistency
- Severity: P2
- Rule: (b) `designsystem/component/Chip.kt:31` is "9×16 padding (REDESIGN_2026-09 §2)", selected = `Bold`; `AppTextField` is the P1 component list's field
- What: `EpkPanes.kt`'s own KDoc (`:76-84`) says the section bodies were "moved rather than
  rewritten" and came across "without a per-widget restyle" — that is visible. A genre chip
  in the bio sheet and the "Popular" chip in the pricing pane are the same control in two
  shapes: `Chip` is 9×16 with a weight change on selection and `type.chip`; `EpkChip` is
  8×12 (`vertical = space.sm`, `horizontal = space.md`), never changes weight, and labels in
  `type.footnote`. Likewise the bio is typed into an `AppTextField` in the sheet and into a
  hairline `EpkField` one pane away.
- Evidence:
  ```kotlin
  // EpkComponents.kt:226
  .padding(horizontal = dimens.space.md, vertical = dimens.space.sm),
  // EpkComponents.kt:229-236 — label never goes to 700 when selected
  style = AppTheme.type.footnote,
  color = when { !enabled -> colors.ink4; selected -> colors.brandInk; else -> colors.ink2 },
  ```
- Fix: swap the five `EpkChip` sites to `Chip` and the five `EpkField` sites to
  `AppTextField`, then delete `EpkComponents.kt`.

### F-PK-06 — 39 compat-alias sites left in feature/epk (F-CC-10 instance list)
- Screens: 23 (panes), 87
- Where: `feature/epk/EpkComponents.kt` (6 colour + 3 type), `feature/epk/EpkPanes.kt` (10 colour + 20 type)
- Category: token
- Severity: P2
- Rule: (a) REDESIGN_2026-09 §4 — these names are P1 compatibility shims, not the
  redesigned tokens
- What: every alias site is in the two files the redesign did not rewrite; `EpkScreen.kt`,
  `EpkHub.kt`, `EpkSheets.kt` and `EpkPressKit.kt` are clean. Colour: `EpkComponents.kt:85`
  `brand`→`accentInk` (see F-PK-02), `:146` `brand`→`ink`, `:183` `line`→`hairline` and
  `brand`→`ink`, `:209` `brand`→`accent`, `:212` `line`→`hairline`, `:234`
  `brandInk`→`onAccent`; `EpkPanes.kt:350` `bgSoft`→`surface2`, `:353` `brand`→`accent`,
  `:374` `brandInk`→`onAccent`, `:378` `brand`→`accent`, `:403` `brand`→`accentInk`,
  `:410`/`:511`/`:715` `hot`→`danger`, `:455` `lineSoft`→`hairline`, `:875`
  `brand`→`accentInk`. Type: `footnote`→`subtitle` (or `body` where it is a paragraph) at
  `EpkComponents.kt:82,231,260` and `EpkPanes.kt:290,495,574,588,597,702,839,868,896,920,968,1037,1065`;
  `callout`→`rowTitle` at `EpkPanes.kt:667,750,795,911,1031`; `monoMicro`→`monoPill` at
  `EpkPanes.kt:373,1078`.
- Evidence:
  ```kotlin
  // EpkPanes.kt:350-353
  .background(colors.bgSoft)
  .border(dimens.size.stroke, if (selected) colors.brand else Color.Transparent, …)
  ```
- Fix: one mechanical pass over the two files, using the mapping above.

### F-PK-07 — Every answered prompt is an accent card, not just the first
- Screens: 68
- Where: `feature/epk/EpkSheets.kt:531-532` (fill + full-accent border); `:496-505` (accent CTA); order at `:448-453`
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 "one accent per screen"; (b) the composable's own KDoc (`:414-417`) states the design's note as "**one** filled card shows the shape, so the empty ones read as invitations"
- What: `PromptAnswerCard` paints an accent tint plus a full-strength `strokeEmphasis`
  accent border, and every answered prompt renders as one — four answered prompts give four
  lime-outlined cards above the lime "Save 4 answers" button. The card that was supposed to
  be the single worked example becomes the wallpaper, and the CTA stops being the loudest
  object on the sheet.
- Evidence:
  ```kotlin
  // EpkSheets.kt:530-532
  .background(colors.accent.copy(alpha = PROMPT_FILL))
  .border(dimens.size.strokeEmphasis, colors.accent, shape)
  ```
- Fix: give the accent treatment to the first card in `order` only; the rest get
  `surface3` with a hairline, which is what "answered" needs to read as.

### F-PK-08 — Two gradient pickers, and the press kit's one has no label
- Screens: 23 (gallery pane)
- Where: `feature/epk/EpkPanes.kt:428-466` and its call site `:150-161`; peer `feature/wizard/WizardMediaSteps.kt:343-376`
- Category: consistency
- Severity: P2
- Rule: (b) the wizard implements the same six-swatch control differently
- What: the same "pick your cover fallback" control exists twice. The wizard's swatches are
  `weight(1f)` across the full row at `radii.md`, ring the selection in `colors.ink` at
  `stroke` against a `hairline` rest state, and fire a `haptics.select()`; the press kit's
  are a fixed `56×38` (`size.swatchW/H`) inside a `horizontalScroll`, at `radii.sm`, ringed
  in `colors.brand` — a lime hairline over a bright gradient, i.e. the one thing the ring has
  to survive. The press-kit copy also ships with no label: its `item(key = "palette")` is a
  bare `Column(gutter) { GradientPicker(...) }`, so six coloured rectangles appear under the
  photo grid with nothing naming them, while every other block in the pane carries an
  `EpkSectionHeader`. Both call `ArtistGradient.palette` (F-DS-01).
- Evidence:
  ```kotlin
  // EpkPanes.kt:455 — selection ring
  if (isSelected) colors.brand else colors.lineSoft,
  // WizardMediaSteps.kt:364 — the same ring
  if (isSelected) colors.ink else colors.hairline,
  ```
- Fix: lift one picker into `designsystem/component/`, keep the wizard's ink ring, and give
  the press-kit instance a header ("Cover fallback") like every other block in the pane.

### F-PK-09 — Character counters are set in the sans, and disagree with each other
- Screens: 67, 68
- Where: `feature/epk/EpkSheets.kt:360-364` (bio, 200-char cap) and `:549-555` (prompt, 280-char cap); peers `feature/wizard/WizardMediaSteps.kt:557-566`, `feature/system/FeedbackScreen.kt:170-174`
- Category: token
- Severity: P2
- Rule: (a) §2 "mono for money/numerals"; (b) the wizard's counter for the SAME bio uses `monoPill`
- What: both press-kit counters are `type.caption` (Plus Jakarta Sans). The wizard's bio
  counter and the feedback screen's counter are `type.monoPill`. Worse, the two counters in
  this one file do not match each other: the bio counter is `ink4` and turns `warm` at the
  cap, the prompt counter is a flat `ink2` and never changes at 280 — so the one moment a
  counter exists for (keystrokes going missing) is silent on screen 68.
- Evidence:
  ```kotlin
  // EpkSheets.kt:360-364
  Text("${bio.length} / $MAX_BIO_CHARS", style = AppTheme.type.caption,
       color = if (bioIsAtCap(bio.length)) colors.warm else colors.ink4)
  // EpkSheets.kt:549-552
  Text("${answer.length} / ${ArtistPrompts.MAX_ANSWER_LENGTH}",
       style = AppTheme.type.caption, color = colors.ink2)
  ```
- Fix: `type.monoPill`, `ink4` → `warm` at the cap, on both.

### F-PK-10 — Three strings explain the implementation instead of the fact
- Screens: 66, 75, 23 (links pane)
- Where: `feature/epk/EpkSheets.kt:834`, `:758`, `feature/epk/EpkPanes.kt:992`
- Category: copy
- Severity: P2
- Rule: (a) §5.2 / rubric §4 — "the user should read the FACT, not the engineering"
- What: screen 66 already says "It's saved on this device — retry or discard it."
  (`:805`), then repeats it in developer vocabulary at the foot of the sheet: "The queue
  survives an app kill and resumes on next launch." Screen 75 puts a 130-character
  two-clause sentence into `Banner`'s **title** slot (`detail` unused) that reasons about
  "what storage actually accepts" — every other Banner in the section is a short title with
  the sentence in `detail` (`EpkScreen.kt:517-523`). The Instagram field's helper explains
  our navigation plumbing ("We deep-link clients straight into the Instagram app") rather
  than what to type, unlike the Spotify and YouTube helpers beside it.
- Evidence:
  ```kotlin
  // EpkSheets.kt:833-838
  Text("The queue survives an app kill and resumes on next launch.", …)
  // EpkSheets.kt:757-760
  Banner(title = "Only MP3, M4A and AAC are offered — the picker lists what storage
  actually accepts, so a file can't fail after you pick it.", tone = BannerTone.Note)
  ```
- Fix: drop `:834`; move the 75 sentence into `detail` under a short title ("MP3, M4A and
  AAC"); replace `:992` with what to paste ("Your handle, with or without the @").

### F-PK-11 — The booker is called "hosts" in four strings and "clients" in fifteen
- Screens: 23, 87, 74
- Where: `feature/epk/EpkPressKit.kt:64,66,69`; `feature/epk/EpkScreen.kt:559`; vs 15 "client" strings (`EpkPressKit.kt:79,233,450,471`; `EpkSheets.kt:291,348,466,675`; `EpkPanes.kt:289,494,587,596,619,895,992`)
- Category: copy
- Severity: P2
- Rule: (b) internal — both nouns appear on screen 23 within one scroll
- What: the gap payoff lines under the section rows say "hosts read this before anything
  else", "hosts book what they can hear", "hosts check you elsewhere", and the bare-kit
  banner says "Hosts can book you"; the invitation lines, the cover caption, the pricing
  line and every sheet in the same feature say "clients". On a filled/empty hub the artist
  reads both words for the same person a few rows apart. The links copy has a second, finer
  drift: the pane's empty line, the sheet's helper and the invitation are three wordings of
  one sentence — "your own site" (`EpkPanes.kt:895`), "your personal site"
  (`EpkSheets.kt:675`), "Anywhere a client should land" (`EpkPressKit.kt:79`).
- Evidence:
  ```kotlin
  // EpkPressKit.kt:64
  EpkSectionKey.Bio -> "Missing — hosts read this before anything else"
  // EpkSheets.kt:348 — the same sentence, other noun
  hint = "Clients read this before anything else on your profile.",
  ```
- Fix: "clients" everywhere (it is the majority and matches the rest of the app); one
  wording for the links sentence, defined once.

### F-PK-12 — Two destructive styles inside one feature
- Screens: 74 vs 23 (panes)
- Where: `feature/epk/EpkSheets.kt:679-701`; `feature/epk/EpkPanes.kt:410,511,715`
- Category: consistency
- Severity: P3
- Rule: (b) the same "remove this thing" action, two anatomies
- What: removing a link is a `Delete` icon plus "Remove this link" in
  `body.copy(SemiBold)` tinted `colors.danger`; removing a photo, a clip or a pricing tier
  is a bare word "Remove" in `type.footnote` tinted `colors.hot` — the same colour under
  its compat alias. Nothing distinguishes the two cases; both are one-tap, both are
  undoable only by re-adding.
- Evidence:
  ```kotlin
  // EpkPanes.kt:511
  trailing = { EpkRowAction("Remove", { onDelete(sample) }, tone = colors.hot) },
  // EpkSheets.kt:696-699
  Text("Remove this link", style = AppTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
       color = colors.danger)
  ```
- Fix: pick one — the icon + danger label row — and use it for all four; `colors.danger`
  in both places.

### F-PK-13 — Hub row chrome is off the `ListRow` spec (chevron tint, leading square)
- Screens: 23, 87
- Where: `feature/epk/EpkHub.kt:442`, `:411`, `:488`; `feature/epk/EpkSheets.kt:223`; peer `designsystem/component/ListRow.kt:115-117`
- Category: token
- Severity: P3
- Rule: (a) §2 "list row … chevron `ink4`"; (b) `ListRow.kt:117` uses `ink4`
- What: the six section rows tint their chevron `colors.lineStrong` (`#c6c9be`, the token
  §2 reserves for separators inside dark chrome and dots) rather than `ink4` — so the press
  kit's chevrons are paler than every other list row in the app; `EpkOptionRow` on screens
  65/75 repeats it. The two variants of the same row also use two leading-square sizes:
  `size.ringXs` 34dp on the filled row, `size.avatarSm` 32dp on the invitation row.
- Evidence:
  ```kotlin
  // EpkHub.kt:439-443
  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
       tint = colors.lineStrong, modifier = Modifier.size(dimens.size.iconLg))
  ```
- Fix: `colors.ink4` on all three chevrons; one square size (34) for both row variants.

### F-PK-14 — "Copied" confirmations differ from the booking screen's
- Screens: 23
- Where: `feature/epk/EpkPanes.kt:1076-1090`, constant `:1100`; peer `feature/booking/BookingDetailScreen.kt:649-653`, constant `:297`
- Category: consistency
- Severity: P3
- Rule: (b) the same copy-to-clipboard affordance, two spellings and two durations
- What: the press kit's share link swaps "COPY" → "COPIED", uppercase in `type.monoMicro`,
  reverting after 1400 ms. Booking detail's identical control is an `ActionRow` with a
  `ContentCopy` icon reading "Copy address" → "Copied", sentence case, reverting after
  1600 ms. Neither raises the app's toast, which is the third spelling of the same event.
- Evidence:
  ```kotlin
  // EpkPanes.kt:1077 + :1100
  if (copied) "COPIED" else "COPY",   // COPIED_RESET_MS = 1_400L
  // BookingDetailScreen.kt:651 + :297
  if (copied) "Copied" else "Copy address",   // COPIED_LABEL_MS = 1_600L
  ```
- Fix: one shared duration constant and one casing convention (sentence case, matching the
  toast copy rule).

### F-PK-15 — The header's account disc is 40, not the 42 every other header uses
- Screens: 23, 87, 76
- Where: `feature/epk/EpkScreen.kt:451-456`; `designsystem/component/IconCircle.kt:50`; `designsystem/theme/Dimens.kt:227,441`
- Category: token
- Severity: P3
- Rule: (a) §2 "icon circle (header actions) 42"
- What: this is the only `IconCircle` in the app that overrides the component's default
  `component.iconCircle` (42) — it passes `hero.avatarSize` (40), a token whose own KDoc
  calls it the "masthead avatar chip diameter". Beside the artist home / bookings / profile
  headers, which all take the default, the press kit's gear is 2dp small. (Separately, the
  sheet header's close disc at `EpkSheets.kt:146` is `size.avatarSm` = 32, so the section
  ships three sizes of the same object.)
- Evidence:
  ```kotlin
  // EpkScreen.kt:451-456
  IconCircle(icon = Icons.Filled.Settings, contentDescription = "Account and settings",
             onClick = onOpenAccount, size = AppTheme.dimens.hero.avatarSize)
  ```
- Fix: drop the `size` argument.

### F-PK-16 — "+ Add" draws its plus in the string, and the action slot doubles as status
- Screens: 23 (panes)
- Where: `feature/epk/EpkPanes.kt:282,487,566,892`; `:285,490,569,835,963`
- Category: copy
- Severity: P3
- Rule: (b) `SectionHeader`'s action slot elsewhere carries a word ("See all"), not a glyph typed into the label
- What: four section actions read "+ Add" / "+ Add tier" — a plus character inside a text
  label, at a step (`footnote`) that has no glyph to balance it. On the photos section the
  same slot is also used as a status readout: `actionLabel = if (uploading) "Uploading…"
  else "+ Add"`, so the control silently becomes a label mid-upload, while the peer
  sections put exactly that kind of status in the separate `trailingNote` slot ("Saving…",
  "6/6").
- Evidence:
  ```kotlin
  // EpkPanes.kt:282-285
  actionLabel = if (uploading) "Uploading…" else "+ Add",
  onAction = onAdd,
  actionEnabled = canAddPhoto(photos.size, uploading),
  trailingNote = if (photos.isEmpty()) null else "${photos.size}/$MAX_PHOTOS",
  ```
- Fix: label "Add"; move "Uploading…" into `trailingNote` beside the count.

### F-PK-17 — `PREVIEW_WIDTH = 350.dp` is preview-only (raw-unit sweep false positive)
- Screens: n/a
- Where: `feature/epk/EpkHub.kt:571,593`
- Category: token
- Severity: P3
- Rule: (a) "never a raw hex/dp/sp" — but the value is not in shipped UI
- What: the only raw `dp` the sweep found in this section sizes the `@Preview` column in
  `EpkHubPreview` (`.width(PREVIEW_WIDTH)`), which never renders in the app. Worth noting
  so it is not "fixed" into a token that then implies a real 350dp measurement; the honest
  change is to drop the width and let the preview use `widthDp` on the annotation.
- Evidence:
  ```kotlin
  // EpkHub.kt:568-571
  Column(Modifier.padding(AppTheme.dimens.component.gutter).width(PREVIEW_WIDTH), …)
  ```
- Fix: `@Preview(widthDp = 350)` and delete the constant, or leave it — no user impact.

## Section-wide observations
- The section is two codebases wearing one name. `EpkScreen`/`EpkHub`/`EpkSheets`/
  `EpkPressKit` are redesigned to spec (shared `SectionHeader`, `Banner`, `Chip`,
  `AppTextField`, `PrimaryButton`, `IconCircle`, honest empty/failed states, no aliases);
  `EpkPanes`/`EpkComponents` are the pre-redesign editor moved verbatim, and its own KDoc
  says so (`EpkPanes.kt:76-84`). All 39 alias sites, all 7 lime-on-white labels, both
  hand-rolled widgets and the duplicated gradient picker are in those two files.
- The hub itself is the strongest screen in the audit: three genuinely different load
  branches (`EpkScreen.kt:221-251`), a completion meter whose summary says the next action
  rather than restating the percentage, "bare" driving invitations instead of a 0% meter,
  and `stalledUploadDetail` replacing the design's un-measurable "stopped at 64%" with size
  + attempts (`EpkPressKit.kt:352-368`). Nothing to fix there.
- The meter question: `pressKit.meter` (6dp) and `dashboard.meterHeight` (3dp) are two
  different hand-rolled `Box` fills, not one component — but the 6dp is documented as "this
  one IS the number" (`Dimens.kt:559-563`) and the score meters accompany a numeral, so this
  is a deliberate step, not drift. `designsystem/component/Meter.kt` is used by neither.
- `EpkHub.kt:526`'s `Icons.Filled.X` is prose inside a KDoc explaining why the six glyphs
  are hoisted into an object — not an icon reference. The three `.copy(alpha = …)` in that
  file are the Banner Note tint (0.22/0.60, identical to `Banner.kt:279-280`) and the
  design's own `rgba(214,248,75,.34)` tick fill, so all three are measured, not invented.
- Icon family: every glyph in the section is Filled or AutoMirrored.Filled — six section
  glyphs, three sheet option icons, `Add`, `Check`, `ArrowUpward`, `Delete`, `Link`,
  `Settings`, `Close`, two `KeyboardArrowRight`. Zero Outlined. That is internally
  consistent but on the heavy side of F-CC-02 for a hairline design.
- Sheets: all six go through `EpkModalSheet` → `SheetScaffold` (one grabber, `dragHandle =
  null`, `containerColor = colors.surface`, `skipPartiallyExpanded = true`), and all six use
  the same `EpkSheetHeader` (centred 17/700 + close disc + optional leading word). They are
  the most consistent block in the section. The one deviation from the rest of the app is
  that they bypass `SheetScaffold(title = …)` — 8 of 11 other sheets pass the title — which
  is defensible since only these need the close disc.
- Autosave debounce is `SAVE_DEBOUNCE_MS = 1_200L` (`EpkViewModel.kt:1735`). It is over the
  rubric's 300 ms bar but it paces a background write, not the UI: every meter, count and
  section row reads the draft (`EpkScreen.kt:706-727`), and `ON_STOP` flushes
  (`EpkScreen.kt:177-183`). Not filed.
- Three `TextAlign.Center` in `EpkSheets.kt`: `:134` is the sheet title (correct), `:510`
  and `:837` are centred captions under a CTA — both are advice the sheet has already given
  elsewhere ("Skip" sits in the header; "saved on this device" is on every card).
