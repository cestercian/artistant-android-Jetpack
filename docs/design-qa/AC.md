<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# AC — Account & settings (screens 25, 26, 47, 48, 49, 69, 81, 82, 91, 92, 93, 113, 115, 116, 124, 128, 129, 130)

## Coverage
| Screen | File(s) | Reviewed | Findings |
| 26 Profile (client tab root) | feature/profile/ProfileScreen.kt | yes | F-AC-14, F-AC-18, F-AC-19, F-AC-20 |
| 47 Account settings list | feature/profile/AccountScreen.kt | yes | F-AC-05, F-AC-07, F-AC-13, F-AC-14, F-AC-16, F-AC-17, F-AC-18 |
| 69 Account list, artist group injected | feature/profile/AccountScreen.kt:269-280 | yes | F-AC-13, F-AC-18 |
| 49 Data export ready | feature/profile/DataExportScreen.kt:379-443 | yes | F-AC-01, F-AC-02, F-AC-03, F-AC-11, F-AC-16, F-AC-17 |
| 81 Data export idle | feature/profile/DataExportScreen.kt:289-319 | yes | F-AC-02, F-AC-03, F-AC-11, F-AC-17 |
| 82 Data export requested | feature/profile/DataExportScreen.kt:321-376 | yes | F-AC-11 |
| 113 Data export failed | feature/profile/DataExportScreen.kt:445-481 | yes | F-AC-11, F-AC-20 |
| 115 Delete stage 1 (reason) | feature/profile/DeleteAccountScreen.kt:355-418 | yes | F-AC-04, F-AC-06, F-AC-12, F-AC-17 |
| 48 Delete stage 2 (consequences) | feature/profile/DeleteAccountScreen.kt:420-480 | yes | F-AC-03, F-AC-06, F-AC-12, F-AC-21 |
| 116 Delete stage 3 (receipt) | feature/profile/DeleteAccountScreen.kt:482-549 | yes | F-AC-03, F-AC-12 |
| 124 Notification settings | feature/profile/NotificationSettingsScreen.kt | yes | F-AC-04, F-AC-16, F-AC-22 |
| 129 Accessibility | feature/profile/AccessibilityScreen.kt | yes | F-AC-04, F-AC-17, F-AC-21 |
| 130 Language & region | feature/profile/LanguageScreen.kt | yes | F-AC-04, F-AC-07, F-AC-17 |
| 128 Devices | feature/profile/DevicesScreen.kt | yes | F-AC-15, F-AC-16 |
| 25 Pro paywall (offer) | feature/paywall/PaywallScreen.kt:330-352 | yes | F-AC-02, F-AC-03, F-AC-08, F-AC-09, F-AC-21 |
| 91 Paywall pending | feature/paywall/PaywallScreen.kt:354-410 | yes | F-AC-03, F-AC-08, F-AC-09, F-AC-20 |
| 92 Paywall outage | feature/paywall/PaywallScreen.kt:412-459 | yes | F-AC-08, F-AC-09, F-AC-10 |
| 93 Paywall active | feature/paywall/PaywallScreen.kt:461-502 | yes | F-AC-02, F-AC-08 |

## Findings

### F-AC-01 — Screen 49 shows two full-width accent CTAs at once
- Screens: 49
- Where: `feature/profile/DataExportScreen.kt:421-426`; `feature/profile/DataExportScreen.kt:256-261`
- Category: hierarchy
- Severity: P1
- Rule: (a) §2 "the one signal: primary CTA" / "one accent per screen"
- What: On the Ready state the export card contains a full-width accent `PrimaryButton` "Share the file", and the pinned footer — always on screen — contains a second full-width accent `PrimaryButton` "Request a fresh export". Two identical lime bars sit about 100dp apart and the *destructive-ish* action (throw this file away and ask for a new one) carries exactly the same weight as the thing the screen exists for. Add the accent file disc at :401 and the accent tick discs at :431-437 and screen 49 has ten accent-filled objects.
- Evidence:
  ```kotlin
  // :421 inside the ready card
  PrimaryButton(text = "Share the file", onClick = onShare, fullWidth = true, …)
  // :256 pinned footer, visible at the same time
  is ExportState.Ready -> PrimaryButton(text = "Request a fresh export", …)
  ```
- Fix: make "Share the file" the footer's single `PrimaryButton` on Ready and demote "Request a fresh export" to a `SecondaryButton` (or a text row under the card).

### F-AC-02 — Accent tick discs used as bullet points on four screens
- Screens: 81, 49, 25, 93
- Where: `feature/profile/DataExportScreen.kt:302-308`; `…:431-437`; `feature/paywall/PaywallScreen.kt:349`; `feature/paywall/PaywallScreen.kt:496`
- Category: slop
- Severity: P1
- Rule: (a) §2 "one accent per screen"; (b) `CheckList.kt:65` — `MarkState.Done` is a filled accent disc with a white check, i.e. "this step is finished"
- What: Four static lists are rendered with `CheckRow(state = MarkState.Done)`, which paints a 22dp accent-filled disc with a tick per row. On screen 81 that is six lime ticks against a list of things the export *would* contain — before the user has asked for anything, the page reads as though six steps already completed. Same six on 49, and four on each paywall screen. `MarkState.Done` genuinely means "done" three lines away on the same screen (`DataExportScreen.kt:355` "Request received"), so the mark now means two different things in one file.
- Evidence:
  ```kotlin
  // DataExportScreen.kt:302 — "What's included", nothing has been requested yet
  EXPORT_CONTENTS.forEachIndexed { index, item ->
      CheckRow(title = item, state = MarkState.Done, showHairline = …)
  // PaywallScreen.kt:349 — the perk list
  PRO_PERKS.forEach { perk -> CheckRow(perk.title, MarkState.Done, subtitle = perk.detail) }
  ```
- Fix: give these lists an inert bullet (a `lineStrong` dot or a hairline-ruled `ListRow`) and reserve the accent tick for rows that report a state that actually happened.

### F-AC-03 — Body copy set in `ink4` on eight screens; peers set it in `ink3`
- Screens: 47, 48, 49, 81, 113, 115, 116, 25, 91, 92
- Where: `feature/profile/AccountChrome.kt:126-131`; `feature/paywall/PaywallScreen.kt:345-346`; `…:385-386`; `…:449-450`
- Category: token
- Severity: P2
- Rule: (a) §2 palette — `ink3 #6d7168` "body copy on light", `ink4 #8a8d82` "captions, meta"
- What: `AccountPageTitle`'s paragraph — the sentence under every page title in this section — is `type.body` in `colors.ink4`, one step too pale for the design's body colour. The paywall repeats it three times for its state paragraphs. Two screens in the same section do it correctly (`DevicesScreen.kt:171-172` and `DataExportScreen.kt:477-478` are `body` + `ink3`), so the section disagrees with itself about what a paragraph looks like.
- Evidence:
  ```kotlin
  // AccountChrome.kt:126 — one component, ten screens
  Text(body, style = AppTheme.type.body, color = colors.ink4, …)
  // DevicesScreen.kt:171 — the same kind of paragraph, correct
  Text(…, style = AppTheme.type.body, color = colors.ink3, …)
  ```
- Fix: `colors.ink3` in `AccountPageTitle` and at the three paywall paragraphs.

### F-AC-04 — Multi-sentence paragraphs set in `caption` 12.5 / `ink4`
- Screens: 124, 129, 130, 115
- Where: `feature/profile/NotificationSettingsScreen.kt:246-259`; `feature/profile/AccessibilityScreen.kt:221-229`; `feature/profile/LanguageScreen.kt:196-201`; `…:209-216`; `feature/profile/DeleteAccountScreen.kt:410-415`
- Category: token
- Severity: P2
- Rule: (a) §2 type table — `caption` is 12.5/400 `ink4` for captions; `body` is 15/400/1.6 for paragraphs
- What: The longest single run of prose in the whole section — the Notifications footer, six sentences over nine source lines — is set in the app's smallest sans step in its palest ink. Accessibility, Language (twice) and Delete stage 1 do the same. `caption` is the step used for a row's second line; a paragraph of policy is not meta. Devices sets a paragraph of the same length and job in `body`/`ink3` (`DevicesScreen.kt:161-174`), so this is also a peer inconsistency.
- Evidence:
  ```kotlin
  // NotificationSettingsScreen.kt:246-257 (abridged) — six sentences
  Text("A switch that's off means this phone doesn't show that notification at all — …",
      style = AppTheme.type.caption, color = colors.ink4, …)
  ```
- Fix: `type.body` / `ink3` for any run over one sentence; keep `caption` for row meta.

### F-AC-05 — Sign out asks for a confirmation the dialog itself says is harmless
- Screens: 47
- Where: `feature/profile/AccountScreen.kt:178-203`; `feature/profile/AccountScreen.kt:379-384`
- Category: slow
- Severity: P2
- Rule: (c) fact — a confirm dialog for a reversible action; (b) every other consequential action in this section is a screen or an inline button
- What: Tapping "Sign out" raises a two-tap modal whose body reads "Your bookings and chats are safe on your account and re-sync when you sign back in" — the app interrupts you to tell you the action costs nothing. Meanwhile the genuinely irreversible action, Delete account, gets a full three-stage screen with a typed keyword, and the near-identical "Sign out everywhere else" on screen 128 fires straight off a button with no confirmation at all. Three destructive-ish actions, three different ceremonies, inversely proportional to the damage. (The M3-default aspect of this dialog is F-CC-08; this finding is about its existence and shape.)
- Evidence:
  ```kotlin
  text = { Text("This clears your data from this device. Your bookings and chats are " +
      "safe on your account and re-sync when you sign back in.", …) }
  ```
- Fix: sign out from the row directly (with an undo-less toast), or if a confirm stays, drop it to the same inline pattern the rest of the section uses and cut the reassurance sentence.

### F-AC-06 — Two private copies of `DestructiveButton`, each documented as the only one
- Screens: 48 (and BC's cancel stage)
- Where: `feature/profile/DeleteAccountScreen.kt:575-607`; `feature/booking/BookingDetailScreen.kt:1190-1213`
- Category: consistency
- Severity: P2
- Rule: (b) two features hand-roll the same object
- What: Both files declare a private `@Composable DestructiveButton`, both with a `danger` fill, `component.cta` height and `radii.buttonLg`, and both KDocs claim it is "the one destructive control in the app". They already differ: the delete copy takes an `enabled` flag with a `hairline`/`ink3` disabled skin and re-asserts `fontWeight = FontWeight.Bold` on top of `type.cta`; the booking copy has neither. The same divergence exists one row up — the "pick a reason before you destroy something" list is `CheckRow` here (`DeleteAccountScreen.kt:392-400`) and a bespoke `ReasonRow` there (`BookingDetailScreen.kt:1220-1225`).
- Evidence:
  ```kotlin
  // DeleteAccountScreen.kt:600
  Text(text, style = AppTheme.type.cta, color = if (enabled) colors.onDark else colors.ink3,
       fontWeight = FontWeight.Bold)
  // BookingDetailScreen.kt:1211
  Text(text, style = AppTheme.type.cta, color = colors.onDark)
  ```
- Fix: promote one `DestructiveButton` (with the `enabled` state) into `designsystem/component/`, delete both copies, and pick one reason-row component for both flows.

### F-AC-07 — The calendar picker is a hand-rolled single-select with a sub-44dp target
- Screens: 47
- Where: `feature/profile/AccountScreen.kt:412-439`
- Category: consistency
- Severity: P2
- Rule: (b) peer `LanguageScreen.kt:166-181` picks one of N with `CheckRow`; (c) `space.sm` = 8dp, so the row is ≈34dp
- What: Choosing which device calendar to mirror into is a radio group drawn as bare `Text` with a `clickable` and 8dp of vertical padding — no mark, no `Role.RadioButton`, no hairline, and the only signal that one is chosen is that its label turns `accentInk`. `CheckRow`, used for the identical job two screens away, gives a 22dp mark, `Role.RadioButton` semantics and a 44dp minimum. On a phone this is a ~34dp tap target inside a settings list whose other rows are 56.
- Evidence:
  ```kotlin
  Text(option.title, style = AppTheme.type.subtitle,
      color = if (selected) colors.accentInk else colors.ink3,
      modifier = Modifier.fillMaxWidth().clickable { onSelect(option.id) }
          .padding(vertical = dimens.space.sm))
  ```
- Fix: replace with `CheckRow(state = if (selected) MarkState.Done else MarkState.Pending, onClick = …)`.

### F-AC-08 — The paywall's four states carry no header or title
- Screens: 25, 91, 92, 93
- Where: `feature/paywall/PaywallScreen.kt:160-170`
- Category: consistency
- Severity: P2
- Rule: (b) the other nine screens in this section use `BackHeader`; (a) §2 "header 56 tall: title 26 + subtitle, or centred 17/700 with a 42 back circle"
- What: The paywall opens with a bare `Row` holding one right-aligned close `IconCircle` and nothing else — no title band on any of the four states. Screen 93 (Active) is reached by *pushing* from the settings list's "Subscription" row, so a user arrives at a titleless page whose only chrome is an X where every neighbouring settings page has a back circle and a name. Screen 91's heading is then `displaySub` (21sp) in the body, half a step below the 26sp page titles used everywhere else in the section.
- Evidence:
  ```kotlin
  Row(Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = dimens.space.sm),
      horizontalArrangement = Arrangement.End) {
      IconCircle(icon = Icons.Filled.Close, contentDescription = "Close", onClick = onClose, …)
  ```
- Fix: give it `BackHeader(title = "Artistant Pro", …)` with the close circle in `trailing`, and set the state headings from `AccountPageTitle`.

### F-AC-09 — The paywall centres seven text blocks; the rest of the section is left-aligned
- Screens: 25, 91, 92, 93
- Where: `feature/paywall/PaywallScreen.kt:206`, `:249`, `:266`, `:381`, `:387`, `:445`, `:451`
- Category: consistency
- Severity: P2
- Rule: (b) every other body, caption and error line in the section is left-aligned
- What: Seven `textAlign = TextAlign.Center` calls, including two multi-line paragraphs (`:387`, `:451`) and the footer's error line (`:206`). The equivalent error lines on `DataExportScreen.kt:236-241` and `LanguageScreen.kt:147-152` are left-aligned in the same pinned-footer position, so the same object is set two ways depending on which file you are in. Centred ragged paragraphs also read as a marketing page rather than a settings destination, which is what screen 93 is.
- Evidence:
  ```kotlin
  Text("Your bank or Google Play is confirming the payment. This can take a minute.",
      style = AppTheme.type.body, color = colors.ink4, textAlign = TextAlign.Center)
  ```
- Fix: keep centring for the single-line CTA footnote if the design draws it that way; left-align the paragraphs and the error line.

### F-AC-10 — Screen 92 states the outage twice, in two different shapes, and contradicts its own button
- Screens: 92
- Where: `feature/paywall/PaywallScreen.kt:417-458`; footer at `…:272-287`
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 "loading, empty and failed are three different screens"; (b) `EmptyState`'s glyph-in-a-circle is the empty language, not the failed one
- What: The failed state opens with a `BannerTone.Failure` "Subscription unavailable / We couldn't reach the store", then immediately below draws a 72dp `surface3` circle with a `Filled.Refresh` glyph, a 21sp "Can't load plans right now" and a paragraph — the empty-state shape, saying the same thing a second time. A third block, an `AccentNote` about restoring, sits under that, so the screen is banner + empty-state + accent aside stacked. The paragraph then says "Pull back and try again in a moment" while a "Try again" button is pinned two inches below it.
- Evidence:
  ```kotlin
  Banner(title = "Subscription unavailable", tone = BannerTone.Failure,
      detail = "We couldn't reach the store.", …)
  …
  Text("Pull back and try again in a moment. Your current plan is unchanged.", …)
  ```
- Fix: keep one failure statement — the banner, with the reason and the "your current plan is unchanged" clause — drop the decorative refresh glyph and the duplicate heading, and let the pinned "Try again" be the only instruction.

### F-AC-11 — Data export's four states change the shape of the top of the page
- Screens: 81, 82, 49, 113
- Where: `feature/profile/DataExportScreen.kt:279-284`; `:294`; `:326-330`; `:383`; `:450-455` and `:461-466`
- Category: consistency
- Severity: P2
- Rule: (b) four states of one destination; (a) §2 principle "loading, empty and failed … say which one they are"
- What: 81 and 49 open with a 26sp `AccountPageTitle`; 82 opens with a `surface3` spinner card and no title at all; 113 opens with a Banner and no title. Walking the flow, the page title appears, vanishes, reappears, vanishes — the header's subtitle is doing all the "where am I" work while the body's first object keeps changing kind. 113 additionally shows two Banners at once (`Failure` at :450 and `Attention` at :461) with a check list between them, which §3 rules out.
- Evidence:
  ```kotlin
  ExportState.Idle -> ExportIdle()        // AccountPageTitle("Get a copy of everything", …)
  ExportState.Requested -> ExportRequested()  // Row(surface3) { CircularProgressIndicator… }
  is ExportState.Ready -> ExportReady(…)  // AccountPageTitle("Your copy is ready", …)
  is ExportState.Failed -> ExportFailed(…) // Banner(Failure) … Banner(Attention)
  ```
- Fix: open all four with `AccountPageTitle` ("Assembling your file" / "Couldn't build your export") and demote the second Banner on 113 to plain body copy under an eyebrow.

### F-AC-12 — A three-stage flow that numbers only two of its stages
- Screens: 115, 48, 116
- Where: `feature/profile/DeleteAccountScreen.kt:368`; `:434`; `:496-509`
- Category: consistency
- Severity: P2
- Rule: (b) the flow's own two other stages
- What: Stage 1's header says "Step 1 of 3", stage 2's says "Step 2 of 3", and stage 3 passes no `header` at all to `AccountScaffold` — the counter never reaches 3, and the receipt is the one screen in the section with no header band, so the body starts hard against the status bar inset. The `BackHandler` at :495 means the affordance exists, it just is not drawn.
- Evidence:
  ```kotlin
  header = { BackHeader(title = "Delete account", onBack = onBack, subtitle = "Step 1 of 3") }
  header = { BackHeader(title = "Delete account", onBack = onBack, subtitle = "Step 2 of 3") }
  AccountScaffold(modifier = …, footer = { PrimaryButton(…"Close Artistant"…) }) {   // no header
  ```
- Fix: give stage 3 a header with the title and "Step 3 of 3" (no back circle — pass a header variant without one, or route its back to `onClose` as the `BackHandler` already does).

### F-AC-13 — One eyebrow over sixteen rows, another over one
- Screens: 47, 69
- Where: `feature/profile/AccountScreen.kt:269-280`; `:283`; `:296-392`
- Category: hierarchy
- Severity: P2
- Rule: (b) the screen's own grouping; (a) §2 list rows with hairline separators
- What: The artist block gets an "Artist" eyebrow for a single row. Everything else — subscription, calendar sync, activity, notifications, language, accessibility, devices, export, privacy, trust & safety, blocked accounts, help, feedback, what's new, rate, sign out, delete — sits under one "Account" eyebrow: sixteen hairline-separated rows with no break, ending with two destructive actions that have no separation from "Rate Artistant". The source itself names the missing groups in comments ("The support tail", "terminal actions").
- Evidence:
  ```kotlin
  EyebrowLabel("Artist", color = colors.ink4)      // :271 — one row follows
  …
  EyebrowLabel("Account", color = colors.ink4)     // :283 — sixteen rows follow
  ```
- Fix: split into the groups the comments already describe — Account / Preferences / Privacy & safety / Support / (unlabelled) destructive tail — each with its own `EyebrowLabel` and `AccountGap`.

### F-AC-14 — The same destination has two different names, and "and" / "&" are mixed
- Screens: 26, 47
- Where: `feature/profile/ProfileScreen.kt:211-212`; `feature/profile/AccountScreen.kt:332`, `:341`, `:345`
- Category: copy
- Severity: P2
- Rule: (b) same `onClick` target, different label
- What: `onSafetyCentre` is "Help and safety" on the Profile tab and "Trust & safety" in the settings list. `onPrivacy` is "Legal and privacy" on one and "Privacy" on the other. A user who taps the row on 26 and then looks for it on 47 is looking for a row that does not exist under that name. Separately the section spells the conjunction three ways in adjacent rows: "Legal and privacy", "Help and safety", "Trust & safety", "Language & region".
- Evidence:
  ```kotlin
  // ProfileScreen.kt:211
  ListRow(title = "Help and safety", onClick = onSafetyCentre)
  ListRow(title = "Legal and privacy", onClick = onPrivacy, showHairline = false)
  // AccountScreen.kt:341
  ListRow(title = "Privacy", onClick = onPrivacy)
  ListRow(title = "Trust & safety", onClick = onSafetyCentre)
  ```
- Fix: one label per destination ("Trust & safety", "Privacy") in both lists, and one conjunction convention across the section.

### F-AC-15 — Devices spends its accent on a static fact and leaves the only action grey
- Screens: 128
- Where: `feature/profile/DevicesScreen.kt:204-241`; `feature/profile/DevicesScreen.kt:136-142`
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 accent = "the one signal: primary CTA"; `component.focusStroke` = 1.5dp vs "cards … hairline stroke"
- What: `ThisDeviceCard` is the loudest object on the screen — `brandSoft` fill, a 1.5dp `accent` border (the design's cards take a hairline), and an accent-filled tile holding a white `Filled.Smartphone`. It is not tappable and states something the user already knows ("Pixel 8 · this device"). The screen's actual purpose, "Sign out everywhere else", is a `SecondaryButton` in `surface2`. The eye lands on the read-only card and the security action is the quietest thing on the page. (`colors.brandSoft` here is one of the two compat colour aliases in `feature/profile` — F-CC-10.)
- Evidence:
  ```kotlin
  .background(colors.brandSoft, shape)
  .border(dimens.component.focusStroke, colors.accent, shape)
  … Box(Modifier.size(dimens.size.avatarSm).background(colors.accent, …))
  ```
- Fix: draw this device as a plain `surface`/hairline card with an `ink` glyph on `surface2`, and give the accent to the action — or if signing out others must stay quiet, at least stop the fact card outranking it.

### F-AC-16 — Centred nav titles carrying subtitles, against the component's own documented rule
- Screens: 47, 49, 81, 82, 113, 115, 48, 124, 128
- Where: `feature/profile/AccountScreen.kt:241-248`; `feature/profile/DevicesScreen.kt:133`; `feature/profile/NotificationSettingsScreen.kt:152`; `feature/profile/DataExportScreen.kt:232`; `feature/profile/DeleteAccountScreen.kt:368`, `:434`
- Category: consistency
- Severity: P2
- Rule: (b) `designsystem/component/Headers.kt:92-103` — "a screen that is ONE THING gets a centred title … a screen whose header also states a quantity or a state gets a left-aligned title with its subtitle under it"; peer `feature/profile/BlockedAccountsScreen.kt:92` passes `centered = false`
- What: Six screens in this section pass a `subtitle` to `BackHeader` and leave `centered` at its default `true`, producing a centred 17sp title with a centred second line squeezed between a 42dp back circle and nothing — exactly the shape `BackHeader`'s KDoc says is for the *other* case. "Account / t•••@artistant.in", "Data export / Requested just now" and "Delete account / Step 1 of 3" are all state lines, which is the documented trigger for `centered = false`. Blocked accounts, Archived, Support and Activity all get this right, so this is the section that diverges.
- Evidence:
  ```kotlin
  BackHeader(title = "Data export", onBack = onBack, subtitle = exportSubtitle(export))
  BackHeader(title = "Devices", onBack = onBack, subtitle = "Where you're signed in")
  ```
- Fix: pass `centered = false` wherever a `subtitle` is supplied (or drop the subtitle on the ones that are genuinely "one thing").

### F-AC-17 — Three row heights and two row type steps inside the same lists
- Screens: 47, 69, 81, 49, 115, 130, 25, 93
- Where: `designsystem/component/ListRow.kt:77`, `:89`; `designsystem/component/SwitchRow.kt:54`, `:72`; `designsystem/component/CheckList.kt:169`, `:179`
- Category: token
- Severity: P2
- Rule: (a) §2 geometry "list row 56–64 high"; §2 type "rowTitle 14.5/600"
- What: `ListRow` and `SwitchRow` set `defaultMinSize(minHeight = component.row)` = 56 and title at `rowTitle.copy(fontSize = body.fontSize)` = 15sp; `CheckRow` sets `defaultMinSize(minHeight = size.rowMin)` = 44 and title at plain `rowTitle` = 14.5sp. Screens that mix them — the account list is `ListRow`+`SwitchRow`, the language and delete-reason and export-contents lists are `CheckRow` — therefore run 12dp shorter and half a point smaller than their neighbours, and the `CheckRow` lists fall below the design's 56–64 band entirely. The `.copy(fontSize = …)` on the other two is itself an invented step.
- Evidence:
  ```kotlin
  // ListRow.kt:89 / SwitchRow.kt:72
  style = AppTheme.type.rowTitle.copy(fontSize = AppTheme.type.body.fontSize),
  // CheckList.kt:179
  Text(title, style = AppTheme.type.rowTitle, color = titleColor)
  ```
- Fix: one row minimum (`component.row`) and one row title step across the three components — resolve `rowTitle` to whichever value is correct and delete the `.copy`.

### F-AC-18 — Two three-up stat bands, two implementations, two type families
- Screens: 26, 47, 69
- Where: `feature/profile/AccountChrome.kt:149-188`; peer `feature/artist/ArtistProfileScreen.kt:679-748`
- Category: consistency
- Severity: P3
- Rule: (b) the artist profile's `StatStrip` solves the identical problem
- What: The account side's band (`AccountStatBand`) is hairline-fenced, uses `IntrinsicSize.Min` dividers inset by `space.sm`, prints values in `type.monoCount` (JetBrains Mono 22/700) and labels in `caption`. The artist profile's strip is hairline-fenced, uses `IntrinsicSize.Min` dividers inset by `space.md`, and prints values in `type.displaySmall` (Plus Jakarta Sans 19/700). Same object, two files, two type families and a 3sp step difference — so the number a host sees on 26 and the number a guest sees on the artist profile do not look like the same kind of fact. (F-AP-04 covers the profile side; this is the account side's half.)
- Evidence:
  ```kotlin
  // AccountChrome.kt:176
  Text(stat.value, style = AppTheme.type.monoCount, color = colors.ink)   // mono 22
  // ArtistProfileScreen.kt:746
  Text(value, style = AppTheme.type.displaySmall, …)                       // sans 19
  ```
- Fix: pick one (mono reads as the "money and numerals" rule in §2), move `AccountStatBand` to `designsystem/component/` and delete `StatStrip`.

### F-AC-19 — Profile hand-rolls the feedback line the section already exports
- Screens: 26
- Where: `feature/profile/ProfileScreen.kt:217-229`; component at `feature/profile/AccountChrome.kt:211-228`
- Category: consistency
- Severity: P3
- Rule: (b) `AccountChrome.AccountFeedbackLine` exists for exactly this and is used at `AccountScreen.kt:395`, `:398` and `AccessibilityScreen.kt:217`
- What: `AccountChrome` exports a tap-to-dismiss failure line and documents it as "the affordance every action failure in this section gets"; `ProfileScreen` then re-implements it inline, in the same file that imports the rest of the chrome. The two happen to match today, which is the problem — the next change to one silently diverges the other.
- Evidence:
  ```kotlin
  Text(message, style = AppTheme.type.caption, color = colors.danger,
      modifier = Modifier.fillMaxWidth().clickable(onClick = onDismissMessage)
          .padding(vertical = space.sm).semantics { testTag = "profile.actionError" })
  ```
- Fix: `AccountFeedbackLine(message, colors.danger, onDismissMessage, "profile.actionError")`.

### F-AC-20 — Further copy leaks and one dead string (beyond F-CC-05)
- Screens: 113, 91, 47, 26
- Where: `feature/profile/DataExportScreen.kt:270`; `feature/paywall/PaywallScreen.kt:262-269`; `feature/profile/ProfileViewModel.kt:421`; `…:429`; `…:136`
- Category: copy
- Severity: P3
- Rule: (a) §2 principle "copy states the fact"; (b) sentence-case button labels elsewhere in the section
- What: (1) `SecondaryButton(text = "Contact Support")` is the only Title Case button label in the section — its neighbours are "Try again", "Keep my account", "Sign out everywhere else". (2) The paywall's pending state promises "Safe to close — we'll notify you either way", but the export screen two files away documents that this app has no notification path for a non-booking event; the honest line is that Play confirms it and this screen picks it up. (3) `ProfileViewModel.kt:421` tells the user how to navigate the app ("Open Account from the artist Profile tab to manage availability.") instead of doing it. (4) `ProfileViewModel.kt:429` is passive engineering voice: "Calendar permission is required to sync gigs." (5) `ProfileViewModel.kt:136` returns "Not available yet" for `!subscriptionsEnabled`, but `AccountScreen.kt:296` hides the whole row in exactly that case — the string can never render.
- Evidence:
  ```kotlin
  SecondaryButton(text = "Contact Support", onClick = onContactSupport, fullWidth = true)
  Text("Safe to close — we'll notify you either way.", …)
  !subscriptionsEnabled -> "Not available yet"      // row is gated on subscriptionsEnabled
  ```
- Fix: "Contact support"; "Safe to close — Play confirms it and this screen picks it up"; "Artistant needs calendar access to add your gigs."; delete the unreachable branch.

### F-AC-21 — Row titles written as sentences, and three row shapes with no cue which respond
- Screens: 129, 48
- Where: `feature/profile/AccessibilityScreen.kt:163-201`; `feature/profile/DeleteAccountScreen.kt:469-477`
- Category: copy
- Severity: P3
- Rule: (b) every other settings row in the section is titled with a noun phrase
- What: On 129 the rows are titled "Follows your system text size" and "Bold text and higher contrast" — statements, where the section's convention is "Reduce motion", "Devices", "Data export". Worse, the Display group stacks three shapes in a row: a `ListRow` with a chevron that leaves the app (`:175`), a `ListRow` with no `onClick` and therefore no chevron and no reaction (`:188`), and a `SwitchRow` (`:194`) — the inert one is visually identical to the tappable one minus a 20dp glyph, so the only way to learn it does nothing is to tap it. Separately on 48 the confirm field's `label = "Type DELETE"` and `hint = "DELETE"` restate each other.
- Evidence:
  ```kotlin
  ListRow(title = "Follows your system text size", subtitle = "Every screen reflows …", onClick = onOpenTextSize)
  ListRow(title = "Bold text and higher contrast", subtitle = "Android applies both …")   // no onClick
  ```
- Fix: title the rows "Text size" and "Bold text and higher contrast", move the statement to the subtitle, and give the non-interactive fact row a visibly different shape (no row treatment — an eyebrow + body paragraph).

### F-AC-22 — The screen's one accent spent on "nothing is wrong"
- Screens: 124
- Where: `feature/profile/NotificationSettingsScreen.kt:156-160`
- Category: hierarchy
- Severity: P3
- Rule: (a) §2 "one accent per screen"; §3 "`AccentNote` used as a generic callout rather than the one aside per screen"
- What: On the happy path — permission granted, which is the state almost every user is in — screen 124 opens with an accent-tinted, accent-bordered `AccentNote` reading "System permission is on." The section's single strongest visual signal is being used to report the default. In the interesting case (permission denied) the same slot correctly gets a `Banner`.
- Evidence:
  ```kotlin
  true -> AccentNote(text = "System permission is on. These decide what this phone raises inside it.", …)
  false -> Banner(title = "Notifications are blocked", tone = BannerTone.Attention, …)
  ```
- Fix: drop the granted branch entirely (the eight switches are self-explanatory when the permission is on), or state it as one `caption` line under the header.

### F-AC-23 — Type steps invented at the call site
- Screens: 25, 48
- Where: `feature/paywall/PaywallScreen.kt:542-546`; `feature/profile/DeleteAccountScreen.kt:600-605`
- Category: token
- Severity: P3
- Rule: (a) §2 type table — the ramp is the ramp
- What: `AppMark` builds a new step out of two others (`monoNumber.copy(fontSize = displayHero.fontSize)`) rather than naming one; `DestructiveButton` re-asserts `fontWeight = FontWeight.Bold` on top of `type.cta`, which is already 700 — harmless today, silently overriding the ramp the moment `cta`'s weight is tuned.
- Evidence:
  ```kotlin
  style = AppTheme.type.monoNumber.copy(fontSize = AppTheme.type.displayHero.fontSize),
  Text(text, style = AppTheme.type.cta, color = …, fontWeight = FontWeight.Bold)
  ```
- Fix: add a named `appMark` step (or reuse `signup`'s) and delete the `fontWeight` override.

## Section-wide observations

- **No decorative icons on settings rows anywhere in this section.** `grep` for `leading =` across `feature/profile/` and `feature/paywall/` returns nothing: every `ListRow` and `SwitchRow` on 26/47/69/124/129 is text + chevron/switch only. This is the rubric's §4 "an icon leading every settings row" hazard avoided, and it should stay that way.
- **`AccountScaffold` is doing its job.** Ten screens share one page shape (header band, gutter-inset body, hairline-topped pinned footer with `imePadding`), and the tailroom token (`size.listTailroom`) is applied identically on all of them. The section's problems are inside the bodies, not the frame.
- **`CheckRow` is doing four unrelated jobs**: a radio group (130, 115), a bullet list (81, 49, 25, 93), a progress narration (82), and a receipt with mixed marks (116). Four meanings for one accent disc is the root of F-AC-02, and it is why the same list of rows means "chosen", "included", "done" and "still to come" on different screens.
- **The accent budget is blown on the two screens that matter most.** Screen 49 carries ten accent-filled objects (2 CTAs, 1 file disc, 6 ticks, 1 note) and screen 25 carries seven; screens 128 and 124 spend their one accent on a non-actionable fact. Meanwhile the genuinely primary action on 128 is grey.
- **Paragraph styling is unresolved across the section**: `body`/`ink4` (10 screens via `AccountPageTitle`, plus 3 paywall blocks), `body`/`ink3` (2 screens), `caption`/`ink4` (5 long footers). Three answers for "a paragraph on a settings page".
- **Destructive treatments, four actions, four ceremonies**: Delete account = 3 screens + typed keyword + `danger`-filled CTA; Cancel booking = 2 stages + a second, separate `danger`-filled CTA; Sign out = M3 dialog + `danger` `TextButton`; Sign out everywhere else = plain `SecondaryButton`, no confirm. Only the first two agree.
- **Header discipline slipped in exactly one direction**: six screens pass a `subtitle` to a centred `BackHeader` (F-AC-16) and one screen draws no header at all (F-AC-12, stage 3), while the paywall draws neither header nor title (F-AC-08). The section's own peers (Blocked accounts, Archived, Activity, Support) already use the correct `centered = false` form.
- **Verified clean, for the record**: `DataExportScreen.kt:184`'s `LaunchedEffect(Unit) { resumeRestored() }` is safe — `DataExportStore.resumeRestored` (`:195-203`) joins the restore, takes the lock and returns unless the state is still `Requested`, so re-entry cannot re-issue a request. No `Modifier.shadow`, `elevation`, gradient, `delay()`-paced UI, `Color.White/Black`, `.copy(alpha=…)` tint hack, nested lazy list or unkeyed lazy list appears anywhere in the section's files (all four scratchpad sweeps return nothing for `feature/profile` or `feature/paywall` except the two `brandSoft` uses already on F-CC-10).
