<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# SH — System & housekeeping + global shell (screens 77, 120, 121, 123, 137, 138, 63, 64)

## Coverage
| Screen | File(s) | Reviewed | Findings |
| 77 Toast | `feature/system/ToastController.kt`; `navigation/ArtistantNavHost.kt:198-211`; `designsystem/component/Toast.kt` (host half) | yes | F-SH-03, F-SH-04, F-SH-19 |
| 123 Activity | `feature/system/ActivityScreen.kt`, `ActivityViewModel.kt` | yes | F-SH-06, F-SH-11, F-SH-12, F-SH-18, F-SH-22, F-SH-23 (icons: instance of F-CC-02) |
| 120 Update required | `feature/system/UpdateRequiredScreen.kt` | yes | F-SH-05, F-SH-06, F-SH-18 |
| 121 Service outage | `feature/system/ServiceOutageScreen.kt` | yes | F-SH-05, F-SH-06, F-SH-24 |
| 137 What's new | `feature/system/WhatsNewSheet.kt`, `ReleaseNotes.kt` | yes | F-SH-07, F-SH-20 (icons: instance of F-CC-02) |
| 138 Rate Artistant | `feature/system/RatePromptSheet.kt`, `RatePrompt.kt` | yes | F-SH-06, F-SH-24 |
| 63 Help centre | `feature/system/HelpCentreScreen.kt`, `HelpCentreViewModel.kt`, `HelpContent.kt` | yes | F-SH-06, F-SH-10, F-SH-15, F-SH-16, F-SH-17, F-SH-20, F-SH-22, F-SH-24 |
| 64 Send feedback | `feature/system/FeedbackScreen.kt`, `FeedbackViewModel.kt` | yes | F-SH-04, F-SH-06, F-SH-08, F-SH-09, F-SH-13, F-SH-18, F-SH-20 |
| Global shell (tab bar, nav motion, toast host, degraded banner, root gate) | `navigation/{ArtistantNavHost,ClientTabsScaffold,ArtistTabsScaffold,NavMotion}.kt`; `ui/SessionDegradedBanner.kt`, `ui/RootViewModel.kt`; `designsystem/component/LightTabBar.kt`; `designsystem/theme/Motion.kt` | yes | F-SH-01, F-SH-02, F-SH-03, F-SH-14, F-SH-21, F-SH-25 |

Answers to the two directed questions:
- **No overlay/dialog/spinner twin of F-CC-01 exists outside `ClientTabsScaffold`.** `ArtistTabsScaffold.kt` and `ArtistantNavHost.kt` contain zero `AlertDialog`, `CircularProgressIndicator` or `glassScrim` references (grepped both files); the artist graph has no "message this person" entry point at all (no `onMessage`/`openThread` call sites), so the client's chat-open scrim has no artist counterpart to compare against.
- **Nav transitions are consistent.** Both scaffolds pass the identical `navEnter/navExit/navPopEnter/navPopExit` builders at `ClientTabsScaffold.kt:247-250` and `ArtistTabsScaffold.kt:190-193`; `NavMotion.kt` is token-clean and reduce-motion-correct throughout. Nothing to file.

## Findings

### F-SH-01 — Tab bar spends a permanent accent on every root, on top of the root's own
- Screens: global shell (all tab roots), 123 by inheritance
- Where: `designsystem/component/LightTabBar.kt:236-265`; `navigation/ClientTabsScaffold.kt:230-234`; `navigation/ArtistTabsScaffold.kt:179-183`; peers `feature/messages/MessagesScreen.kt:517`, `feature/artisthome/ArtistHomeScreen.kt:593`, `:677`, `:912`
- Category: hierarchy
- Severity: P1
- Rule: (a) §2 Principles "one accent per screen"; (b) the component's own doc at `LightTabBar.kt:96` asserts the circle "carries the app's one accent, which is why no tab cell ever tints lime" — a claim the tab ROOTS then break
- What: `CentreAction` is a 52dp accent-filled disc lifted 12dp above the bar. It is present on every tab root of both roles, always, with no state in which it dims. So the accent budget is spent by chrome before any screen draws — and the roots draw their own: the artist Studio root has three accent fills (`ArtistHomeScreen.kt:593/677/912`) plus the disc, the client inbox one (`MessagesScreen.kt:517`) plus the disc, Discover its own (lead's F-DS-24) plus the disc. A user on Studio sees four lime objects at once, of which the largest is chrome that has nothing to do with the page.
- Evidence:
  ```kotlin
  // LightTabBar.kt:242-245
  .offset(y = -chrome.actionLift)
  .size(chrome.actionSize)          // 52dp
  .clip(CircleShape)
  .background(colors.accent)
  ```
- Fix: make the disc `ink`/`surface2`-filled (or accent only on the root whose verb it is) so the one accent per screen belongs to the screen, not to the frame.

### F-SH-02 — The client's accent action circle goes where the tab beside it goes
- Screens: global shell (client)
- Where: `navigation/ClientTabsScaffold.kt:230-234`; the tab it duplicates at `:108` and `:121-123`
- Category: hierarchy
- Severity: P2
- Rule: (c) fact — both controls call `navigateToTab(nav, ClientTab.Search.route)`
- What: the raised lime circle is labelled "Find an artist" and its `onClick` is the Search tab; `Icons.Filled.Search` is already the second glyph in the same bar, ~70dp to its left. The app's single most prominent control is a duplicate of its neighbour, and when the user is already on Search the circle is a no-op that re-navigates to the destination they are looking at (the glyph stays lime and inviting while doing nothing). The comment at `:224-228` concedes this is a placeholder ("a dedicated 'new booking' flow is a section-PR decision").
- Evidence:
  ```kotlin
  // ClientTabsScaffold.kt:230-234
  action = LightTabAction(
      label = "Find an artist",
      icon = Icons.Filled.Add,
      onClick = { navigateToTab(nav, ClientTab.Search.route) },
  ),
  ```
- Fix: give the circle a verb the bar does not already carry, or drop it on the client graph until one exists.

### F-SH-03 — Every toast raised from a pushed screen floats 88dp above nothing
- Screens: 77, and every screen that raises a toast
- Where: `navigation/ArtistantNavHost.kt:198-210`; `designsystem/component/Toast.kt:82`, `:103`; `navigation/ClientTabsScaffold.kt:216-217`; `navigation/ArtistTabsScaffold.kt:166-167`
- Category: spacing
- Severity: P1
- Rule: (c) fact — `gate is RootGate.Tabs` is true for the whole shell, while the tab bar is drawn only on tab roots
- What: the host reserves the tab bar's height whenever the root gate is `Tabs`. But both scaffolds suppress the bar on any pushed destination (`if (!showBottomBar) return@Scaffold`), and the gate does not change when a destination is pushed. So a toast raised from Send feedback, a booking detail, a chat, Blocked accounts — i.e. most toasts in the app — sits `toastGap` 22dp + `lightTabBarHeight()` (hairline + 14 + 48 + 16 + nav inset ≈ 88dp) up from the bottom edge with empty page under it. Separately, `bottomPadding` is applied as `vertical =`, so the same value is also added above the capsule.
- Evidence:
  ```kotlin
  // ArtistantNavHost.kt:209
  bottomPadding = if (gate is RootGate.Tabs) gap + lightTabBarHeight() else gap,
  // Toast.kt:101-104
  .padding(horizontal = AppTheme.dimens.component.gutter, vertical = bottomPadding)
  ```
- Fix: hoist the "is the tab bar actually drawn" fact out of the scaffolds (the `showBottomBar` boolean) and feed the host that, not the gate; and use `bottom =` rather than `vertical =`.

### F-SH-04 — "Queued on this device" is confirmed with a success tick
- Screens: 64, 77
- Where: `feature/system/FeedbackScreen.kt:81-94`; `navigation/ClientTabsScaffold.kt:432`; `feature/system/ToastController.kt:40`; `navigation/ArtistantNavHost.kt:204-208`
- Category: copy / consistency
- Severity: P2
- Rule: (b) `ToastIcon.Info` exists for exactly this and is never used; (c) fact — `onToast: (String) -> Unit` cannot pass an icon, so `show()` defaults to `ToastIcon.Confirm` → `Icons.Filled.Check`
- What: the ViewModel is careful to distinguish two outcomes ("sent" vs "queued") and the screen's own comment says the two facts "are genuinely different" — then hands both to a one-argument callback that renders both with the accent tick. A user whose feedback did NOT reach the server sees a checkmark, for 2.6s, while the screen pops out from under them. That is the only notice they get.
- Evidence:
  ```kotlin
  // FeedbackScreen.kt:86-92
  FeedbackOutcome.Sent -> "Feedback sent"
  FeedbackOutcome.Queued -> "Queued on this device"
  ...
  viewModel.consumeOutcome(); onClose()
  ```
- Fix: widen `onToast` to `(String, ToastIcon)` and send `ToastIcon.Info` for `Queued`.

### F-SH-05 — The two full-screen gates are twins that share no header, glyph, or title step
- Screens: 120, 121
- Where: `feature/system/UpdateRequiredScreen.kt:80-107` vs `feature/system/ServiceOutageScreen.kt:88-127`
- Category: consistency
- Severity: P2
- Rule: (b) two peer screens solving one problem four different ways; (a) `displaySmall` is 19sp (`theme/Type.kt:257-260`), not the 23sp the ServiceOutage comment claims
- What: 120 has no header, starts on a `Spacer(xxl)`, draws a **74dp accent-filled rounded square** (`funnel.outcomeDisc`, `radii.xl`) with a **Filled** glyph in `onAccent`, and titles itself **left-aligned in `displayHero` 30sp**. 121 draws a `BackHeader` with an **empty title string**, a **72dp `surface3` circle** (`emptyGlyphCircle`) with an **Outlined** glyph in `ink3`, and titles itself **centred in `displaySmall` 19sp**. Same job, same tier, nothing in common — and the file comment that justifies the smaller title cites a size (23sp) that no token in the ramp has.
- Evidence:
  ```kotlin
  // UpdateRequiredScreen.kt:83-97
  .size(dimens.funnel.outcomeDisc).clip(RoundedCornerShape(dimens.radii.xl)).background(colors.accent)
  Icon(Icons.Filled.ArrowUpward, tint = colors.onAccent, …)
  Text("Time for an update.", style = AppTheme.type.displayHero, …)
  // ServiceOutageScreen.kt:99-113
  .size(dimens.component.emptyGlyphCircle).clip(CircleShape).background(colors.surface3)
  Icon(Icons.Outlined.CloudOff, tint = colors.ink3, …)
  Text("Artistant is down", style = AppTheme.type.displaySmall, …)
  ```
- Fix: pick one gate anatomy (disc shape + fill + glyph family + title step + alignment) and apply it to both; the back circle is the only thing that should differ.

### F-SH-06 — Six of eight SH screens override `caption`'s weight, inventing a step off the ramp
- Screens: 120, 121, 123, 138, 63, 64
- Where: `ActivityScreen.kt:391`; `FeedbackScreen.kt:167`; `HelpCentreScreen.kt:176`; `RatePromptSheet.kt:180`; `ServiceOutageScreen.kt:160`; `UpdateRequiredScreen.kt:135`
- Category: token
- Severity: P2
- Rule: (a) §2 "caption 12.5 / 400"; `theme/Type.kt:191-195` ships `caption` at `FontWeight.Medium`, so every call site is correcting the token rather than using it
- What: seven call sites across six files all write `AppTheme.type.caption.copy(fontWeight = FontWeight.Normal)`. Either the token is wrong (the design sheet says 400 and the ramp says Medium) or all seven sites are; either way the section has a private eighth type step that the rest of the app does not use, so the same meta line is one weight here and another on a peer screen.
- Evidence:
  ```kotlin
  // six files, identical
  style = AppTheme.type.caption.copy(fontWeight = FontWeight.Normal),
  // theme/Type.kt:191-195
  val caption: TextStyle = TextStyle(fontFamily = SansFamily, fontSize = 12.5f.sp,
      fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
  ```
- Fix: settle `caption` at 400 in `Type.kt` per §2 and delete all seven `.copy(...)`.

### F-SH-07 — What's new stacks three accent tiles under an accent CTA
- Screens: 137
- Where: `feature/system/WhatsNewSheet.kt:154-161`, `:140-145`
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 Principles "one accent per screen"
- What: every release highlight gets a `iconCircleSm` (40dp) rounded square filled in `colors.accent`; the current note has three (`ReleaseNotes.kt:66-80`), and the "Got it" `PrimaryButton` below is a fourth accent-filled object in the same sheet. The component's own comment already sees the problem — "a row of accent discs reads as three buttons" — and then fills them accent anyway, changing only the corner radius.
- Evidence:
  ```kotlin
  // WhatsNewSheet.kt:156-161
  .size(dimens.component.iconCircleSm)
  .clip(RoundedCornerShape(dimens.radii.md))
  .background(colors.accent),
  ```
- Fix: draw the highlight tiles in `surface2` with an `ink` glyph and leave the accent to the CTA.

### F-SH-08 — Send feedback opens with the keyboard down and its only field unfocused
- Screens: 64
- Where: `feature/system/FeedbackScreen.kt:150-157`, `:238-287`
- Category: slow
- Severity: P2
- Rule: (c) fact — no `FocusRequester`/`requestFocus()` anywhere in the file
- What: the screen exists to type one thing into one box; the box is the middle of the page and takes a `weight(1f)`. The user arrives, the caret is nowhere, and they must aim a tap at the field before they can start — one avoidable tap on a screen whose whole purpose is the field. `imePadding()` is already wired at `:100`, so the layout is ready for the keyboard.
- Evidence:
  ```kotlin
  // FeedbackScreen.kt:262-266
  BasicTextField(value = value, onValueChange = onValueChange, enabled = enabled,
      interactionSource = interaction, …)
  ```
- Fix: add a `FocusRequester` on the `BasicTextField` and `LaunchedEffect(Unit) { requestFocus() }`.

### F-SH-09 — Send feedback hand-rolls a header and puts two dismiss controls in it
- Screens: 64
- Where: `feature/system/FeedbackScreen.kt:108-135`; peers `ActivityScreen.kt:131-156` and `HelpCentreScreen.kt:88` (both `BackHeader`)
- Category: consistency
- Severity: P2
- Rule: (b) the other two pushed SH screens use `BackHeader`; (c) both controls call the same `onClose`
- What: the row is `Text("Cancel")` on the left, a centred `sectionTitle`, and an `IconCircle(Close)` on the right — a text dismiss and a glyph dismiss, ~330dp apart, doing exactly the same thing. Neither is a back arrow, so the screen also breaks the section's back affordance (a 42dp `IconCircle(ArrowBack)`), and the trailing `IconCircle` is `iconCircleSm` 40dp where `BackHeader`'s circle is 42.
- Evidence:
  ```kotlin
  // FeedbackScreen.kt:114-134
  Text(text = "Cancel", … .clickable(role = Role.Button, onClick = onClose) …)
  Text(text = "Send feedback", style = AppTheme.type.sectionTitle, …)
  IconCircle(icon = Icons.Filled.Close, contentDescription = "Close", onClick = onClose, …)
  ```
- Fix: `BackHeader(title = "Send feedback", onBack = onClose)` and delete both bespoke controls.

### F-SH-10 — Help centre's no-results state names an action and offers none
- Screens: 63
- Where: `feature/system/HelpCentreScreen.kt:115-121`
- Category: consistency
- Severity: P2
- Rule: (a) §2 Principles "every empty state carries an action"; (b) `ActivityScreen.kt:184-185` passes `actionLabel`/`onAction` on the same component
- What: search for something with no answer and you get a title, a body that says "send us the question — we read everything", and nothing to press. Send feedback is a real screen one route away (`ClientNavRoutes.FEEDBACK`), and there is not even a "Clear search" to undo the query that produced the state. Its own peer two files over passes both parameters.
- Evidence:
  ```kotlin
  // HelpCentreScreen.kt:116-121
  EmptyState(
      title = "No answer for that",
      body = "Try a different word, or send us the question — we read everything.",
      modifier = Modifier.padding(top = dimens.space.xl),
  )
  ```
- Fix: pass `actionLabel = "Send feedback"` with an `onSendFeedback` lambda wired to the feedback route (or, minimally, "Clear search").

### F-SH-11 — "Mark all read" flashes into the Activity header and shifts the title on entry
- Screens: 123
- Where: `feature/system/ActivityScreen.kt:102`, `:142-155`; `feature/system/ActivityViewModel.kt:63`, `:130-144`
- Category: slow
- Severity: P2
- Rule: (c) fact — `hasUnread` gates on `unreadOnArrival != null`, which `markSeen()` sets in a separate emission before the DataStore write lands
- What: `markSeen()` first publishes `unreadOnArrival` (making `hasUnread` true) and then suspends on `log.markRead(unread)`. Between the two emissions the header's trailing slot swaps from a 48dp `Spacer` to the text "Mark all read", which is far wider — and the title is `centered = false` in a weighted column, so the title and subtitle visibly shift left, then shift back when the write lands. Every visit with unread rows gets this, during the 300ms push. The comment at `:139-141` believes the action only appears when "something landed while you were reading"; it also appears on arrival.
- Evidence:
  ```kotlin
  // ActivityViewModel.kt:134-142
  val unread = log.entries.first().filter { !it.read }.map { it.id }.toSet()
  unreadOnArrival.value = unread           // hasUnread flips true here
  if (unread.isNotEmpty()) log.markRead(unread)   // …and false again here
  ```
- Fix: set `unreadOnArrival` after `log.markRead(unread)` completes (or derive `hasUnread` from rows not in the arrival snapshot) so the control never appears for the marking pass.

### F-SH-12 — Activity puts the accent on every unread row, not on the one it says
- Screens: 123
- Where: `feature/system/ActivityScreen.kt:359-365` vs `:409-416`
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 "one accent per screen"; (b) the file's own comment at `:363-364`
- What: the leading disc is accent for exactly one row ("One accent per screen: the newest unread row gets the lime disc") — and then every unread row also draws an 8dp accent dot on its trailing edge. Arrive after a quiet week with six unread notifications and the page has seven lime objects, plus a lime selected chip in the filter row above. The rule the comment states is broken twelve lines further down in the same composable.
- Evidence:
  ```kotlin
  // ActivityScreen.kt:409-415
  if (unread) {
      Box(Modifier.size(dimens.dashboard.bannerDot).clip(CircleShape)
          .background(colors.accent))
  }
  ```
- Fix: draw the per-row unread dot in `ink` (weight already carries unread per `:378-383`), leaving the accent to the newest row's disc.

### F-SH-13 — Two character counters, two type ramps, two at-cap colours
- Screens: 64
- Where: `feature/system/FeedbackScreen.kt:171-175` vs `feature/epk/EpkSheets.kt:360-363` and `:549-553`
- Category: consistency
- Severity: P2
- Rule: (b) three call sites of the same `"N / MAX"` control, drawn three ways
- What: Feedback sets its counter in `monoPill` (JetBrains Mono 11.5/600) and turns it `danger` at the cap. The EPK bio counter sets the same string in `caption` (sans 12.5) and turns it `warm` at the cap; the prompt-answer counter uses `caption` in `ink2` and never changes colour. Same control, same grammar, three treatments — and red vs amber for the same event says two different things about how bad hitting the limit is.
- Evidence:
  ```kotlin
  // FeedbackScreen.kt:172-174
  text = "${state.body.length} / $FEEDBACK_MAX_CHARS",
  style = AppTheme.type.monoPill,
  color = if (state.remaining == 0) colors.danger else colors.ink4,
  ```
- Fix: extract one `CharCounter(length, max)` into `designsystem/component/` and call it from all three.

### F-SH-14 — The tab bar mixes solid and hollow glyphs in the app's most-seen chrome
- Screens: global shell (both roles)
- Where: `navigation/ClientTabsScaffold.kt:107-111`; `navigation/ArtistTabsScaffold.kt:86-91`
- Category: consistency
- Severity: P2
- Rule: (b) same concept, two icon families, side by side in one row (distinct from F-CC-02's list, which does not include the tab bars)
- What: both bars pair two genuinely solid glyphs with two hollow ones drawn from the `Filled` package: `Filled.Home` + `Filled.Search`/`Filled.CalendarMonth` are filled shapes, `Filled.ChatBubbleOutline` and `Filled.PersonOutline` are outlines. Against a hairline design the two solid glyphs read a full weight heavier than the two beside them, on every screen of the app, and the 52dp solid `Filled.Add`/`Filled.PlayArrow` in the centre makes it three weights in one row.
- Evidence:
  ```kotlin
  // ArtistTabsScaffold.kt:87-90
  Home("home", "Studio", Icons.Filled.Home),
  Gigs("gigs", "Gigs", Icons.Filled.CalendarMonth),
  Messages("messages", "Messages", Icons.Filled.ChatBubbleOutline),
  Epk("epk", "Profile", Icons.Filled.PersonOutline),
  ```
- Fix: take all four from `Icons.Outlined` (`Home`, `Search`/`CalendarMonth`, `ChatBubbleOutline`, `Person`) — selection is already carried by tint, not by fill.

### F-SH-15 — The FAQ accordion animates on Compose defaults while its own chevron uses the tokens
- Screens: 63
- Where: `feature/system/HelpCentreScreen.kt:233-240` vs `:202-206`
- Category: slow
- Severity: P3
- Rule: (b) the same interaction, two motion systems; (a) `theme/Motion.kt:207-228` — `motionTween` is the spelling, and reduce-motion comes with it
- What: the chevron rotates on `motionTween(motion.indicator)` — 200ms, standard easing, zero under reduce-motion. The answer panel it points at expands on a bare `AnimatedVisibility(visible = expanded)`, i.e. Compose's default `fadeIn + expandVertically` on a spring, which is neither 200ms nor reduce-motion-aware. Opening one FAQ therefore runs two clocks, and a user who has turned animations off still gets the panel springing open.
- Evidence:
  ```kotlin
  // HelpCentreScreen.kt:233
  AnimatedVisibility(visible = expanded) {
  ```
- Fix: pass `enter`/`exit` built from `motionTween(AppTheme.motion.contentReveal)`, matching the chevron's clock.

### F-SH-16 — The FAQ chevron is `lineStrong` where every other chevron in the app is `ink4`
- Screens: 63
- Where: `feature/system/HelpCentreScreen.kt:224-231` vs `designsystem/component/ListRow.kt:114-118`
- Category: token
- Severity: P3
- Rule: (a) §2 "list row … chevron `ink4`"; (b) `ListRow` tints its identical `KeyboardArrowRight` `colors.ink4`
- What: `lineStrong` (#c6c9be) is the palette's separator/dot colour, two steps lighter than `ink4` (#8a8d82). The chevron is the only affordance saying an FAQ row opens, and it is drawn in a divider colour — on a `page` ground it barely registers.
- Evidence:
  ```kotlin
  // HelpCentreScreen.kt:225-227
  imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
  contentDescription = null,
  tint = colors.lineStrong,
  ```
- Fix: `tint = colors.ink4`.

### F-SH-17 — Help centre's header greets where its peers state a fact, and centres what they left-align
- Screens: 63, 123
- Where: `feature/system/HelpCentreScreen.kt:88`; `feature/system/HelpCentreViewModel.kt:34-35`; peer `feature/system/ActivityScreen.kt:131-138`; component doc `designsystem/component/Headers.kt:92-103`
- Category: copy / consistency
- Severity: P3
- Rule: (b) `BackHeader`'s own doc: a header with a subtitle is left-aligned and the subtitle states "a quantity or a state" ("4 conversations", "2 blocked")
- What: Help passes `subtitle = "Hi Rhea, how can we help?"` and leaves `centered` at its `true` default, so it renders a centred title with a centred greeting under it — while Activity, one file over, passes `centered = false` with the factual "Notifications received on this device". Two peer pushed system screens, two header shapes; and the greeting is the only subtitle in the app that states nothing, asks a question, and repeats what the "Help" title already said.
- Evidence:
  ```kotlin
  // HelpCentreScreen.kt:88
  BackHeader(title = "Help", subtitle = state.greeting, onBack = onBack)
  // HelpCentreViewModel.kt:34-35
  ?.let { "Hi $it, how can we help?" } ?: "How can we help?"
  ```
- Fix: drop the greeting or replace it with the fact the page can state (the audience/article count), and pass `centered = false` to match Activity.

### F-SH-18 — `monoPill`, the accent-badge step, does duty as plain meta on three SH screens
- Screens: 120, 123, 64
- Where: `feature/system/ActivityScreen.kt:405`; `feature/system/UpdateRequiredScreen.kt:167-169`; `feature/system/FeedbackScreen.kt:173`
- Category: token
- Severity: P3
- Rule: (a) `theme/Type.kt:225-229` documents `monoPill` as "Accent badge riding on a card or a hero"; §2 puts row meta in `subtitle`/`caption` `ink4`
- What: three different jobs — a row timestamp, a version number, a character counter — all borrow the badge step, and two of them then re-weight it (`monoPill.copy(fontWeight = …)` at `UpdateRequiredScreen.kt:167`). The Activity timestamp additionally sits in `ink3` (body colour) where §2 puts row meta in `ink4`, so it reads darker than the row body it annotates.
- Evidence:
  ```kotlin
  // ActivityScreen.kt:403-406
  text = relativeStamp(entry.receivedAtMs, nowMs),
  style = AppTheme.type.monoPill,
  color = colors.ink3,
  ```
- Fix: use `monoLabel`/`caption` for meta and reserve `monoPill` for badges; put the Activity stamp in `ink4`.

### F-SH-19 — The toast's enter/exit ignores reduce-motion
- Screens: 77
- Where: `designsystem/component/Toast.kt:105-112`
- Category: slow
- Severity: P3
- Rule: (a) `theme/Motion.kt:207-220` — every animated surface goes through `motionTween` or `MotionSpecs.durationMillis`; (c) fact — a bare `tween` never reads `LocalReduceMotion`
- What: the host reaches for the right duration token (`motion.tabSwitch`) but wraps it in a raw `androidx.compose.animation.core.tween`, so the one global confirmation surface still slides and fades for a user who has turned animations off — the only surface in the shell that does (`NavMotion.kt`, `LightTabBar.kt` and `HelpCentreScreen.kt`'s chevron all honour it).
- Evidence:
  ```kotlin
  // Toast.kt:105-108
  enter = fadeIn(androidx.compose.animation.core.tween(AppTheme.motion.tabSwitch)) +
      slideInVertically(androidx.compose.animation.core.tween(AppTheme.motion.tabSwitch)) { it / SLIDE_DIVISOR },
  ```
- Fix: swap both `tween(...)` calls for `motionTween(AppTheme.motion.tabSwitch)`.

### F-SH-20 — `body.copy(fontWeight = …)` re-invents `rowTitle` on three SH surfaces
- Screens: 137, 63, 64
- Where: `feature/system/WhatsNewSheet.kt:174`, `:179`; `feature/system/HelpCentreScreen.kt:220`; `feature/system/FeedbackScreen.kt:268-272`, `:282`
- Category: token
- Severity: P3
- Rule: (a) §2 — `rowTitle` 14.5/600 is the step for a row/tile name; `body` 15/400 is the paragraph step
- What: the release-highlight title is `body.copy(fontWeight = Bold)`, its detail is `subtitle.copy(lineHeight = body.lineHeight)`, the FAQ question is `body.copy(fontWeight = SemiBold)`, and the feedback composer's own text style is `rowTitle.copy(fontWeight = Normal, lineHeight = body.lineHeight)` — i.e. `body` rebuilt out of `rowTitle`. Five hand-made steps for two roles the ramp already names, so the same "title of a small block" is 15/700 here and 14.5/600 on a peer.
- Evidence:
  ```kotlin
  // WhatsNewSheet.kt:174 / :179
  style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold),
  style = AppTheme.type.subtitle.copy(lineHeight = AppTheme.type.body.lineHeight),
  ```
- Fix: `rowTitle` for the titles, `body` for the paragraph, no `.copy`.

### F-SH-21 — Tab-root content reflows the instant a push starts, mid-slide
- Screens: global shell (both roles)
- Where: `navigation/ClientTabsScaffold.kt:216-217`, `:237`; `navigation/ArtistTabsScaffold.kt:166-167`, `:560-566`
- Category: slow
- Severity: P3
- Rule: (c) fact — `showBottomBar` is derived from `currentBackStackEntryAsState()`, which flips at `navigate()`, not at the end of the 300ms transition
- What: pushing off a tab root empties the `bottomBar` slot immediately. `Scaffold` recomputes `inner`, and every `TabPane` — including the outgoing root, still on screen and sliding for another 300ms — loses ~88dp of bottom padding in one frame. The screen the user is watching leave grows taller and its content shifts down while it slides away.
- Evidence:
  ```kotlin
  // ArtistTabsScaffold.kt:565
  Box(Modifier.fillMaxSize().background(background).padding(inner)) { content() }
  ```
- Fix: keep the bar composed and animate it out, or give `TabPane` a remembered bottom inset that only collapses once the transition settles. (Not device-verified — mechanism read from code.)

### F-SH-22 — Two pushed SH screens, two tailrooms under the last row
- Screens: 63, 123
- Where: `feature/system/ActivityScreen.kt:196` (`chrome.contentTailroom` = 16dp) vs `feature/system/HelpCentreScreen.kt:133` (`size.listTailroom` = 56dp)
- Category: spacing
- Severity: P3
- Rule: (b) same relationship (last row → bottom edge), two tokens, 3.5× apart, on two peer pushed screens with no bottom bar
- What: Activity's list ends 16dp from the bottom edge; Help's ends 56dp from it. Neither screen has a pinned CTA bar or a tab bar, so the relationship is identical. Help also spends a `Box(Modifier.size(...))` as a spacer, which sets a width it does not want.
- Evidence:
  ```kotlin
  // HelpCentreScreen.kt:133
  Box(Modifier.size(dimens.size.listTailroom))
  ```
- Fix: one token for "last row to bottom edge on a bar-less pushed screen"; use `Spacer(Modifier.height(...))`.

### F-SH-23 — Activity's row disc is 32 where the inbox row it mirrors is 48
- Screens: 123
- Where: `feature/system/ActivityScreen.kt:359-374` vs `feature/messages/MessagesScreen.kt:465`
- Category: consistency
- Severity: P3
- Rule: (b) two peer "list of things that happened" rows; the rest of the anatomy already matches (`rowTitle.copy(Bold)` title, `caption` body — `MessagesScreen.kt:470`, `:491`)
- What: the Activity row copies the inbox row exactly except for its leading disc: `size.avatarSm` 32dp against the inbox's `size.avatarMd` 48dp. Stacked in the same app the two lists read as different densities, and the Activity glyph inside a 32dp disc has to shrink to `iconMd` to fit.
- Evidence:
  ```kotlin
  // ActivityScreen.kt:360-361
  Modifier
      .size(dimens.size.avatarSm)
  ```
- Fix: `size.avatarMd`, matching the inbox.

### F-SH-24 — The section's secondary action comes in three shapes
- Screens: 121, 138, 63
- Where: `feature/system/RatePromptSheet.kt:171-177`; `feature/system/ServiceOutageScreen.kt:182-193`; `feature/system/HelpCentreScreen.kt:181-193`
- Category: consistency
- Severity: P3
- Rule: (a) §2 "secondary button: same height, `surface2`"; (b) three peers, three treatments
- What: 138's secondary is a full-width `PrimaryButton(variant = Ghost)`; 121's is a bare centred text link in `accentInk` under the CTA; 63's promoted card puts its action in a hand-rolled dark `ink`-filled pill with `onDark` text and no minimum tap height (its padding is `space.md`/`space.sm`, so the target can land under 44dp). None of them is the `surface2` secondary §2 describes.
- Evidence:
  ```kotlin
  // ServiceOutageScreen.kt:183-191
  text = STATUS_PAGE,
  style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
  color = colors.accentInk,
  modifier = Modifier.clickable(role = Role.Button) { … }.padding(dimens.space.xs),
  ```
- Fix: one `SecondaryButton` (54dp, `surface2`) for all three, and give the help card's pill `defaultMinSize(minHeight = size.controlMin)`.

### F-SH-25 — The tab bar draws its labels in a compat type alias
- Screens: global shell (Accessibility "Always show labels")
- Where: `designsystem/component/LightTabBar.kt:220`
- Category: token
- Severity: P3
- Rule: (a) §5 / rubric — `tabLabel` is on the compat-alias retire list, and this is a P1-foundation component of the redesign
- What: the one path that draws tab labels reaches for `AppTheme.type.tabLabel`, an alias kept alive so pre-redesign screens would compile. The redesigned bar should name a real step from the §2 ramp so a later ramp change reaches it.
- Evidence:
  ```kotlin
  // LightTabBar.kt:220
  style = AppTheme.type.tabLabel,
  ```
- Fix: point it at the §2 step it means (`caption`, or a named `tabLabel` re-cut in the new ramp) and retire the alias.

## Section-wide observations
- **The section is unusually well-reasoned in prose and less so in pixels.** Six of the eight screens carry a comment that states a design rule and is then contradicted a few lines below by the code under it: "one accent per screen" (F-SH-12, F-SH-07, F-SH-01), "a row of accent discs reads as three buttons", "nothing else on the page competing for it" (Feedback's CTA is accent too), "says its own name in 23sp" (the token is 19). When auditing this section, trust the code over the comment.
- **`.copy(...)` on a type token is the section's default spelling, not an exception:** 7 × `caption.copy(fontWeight = Normal)` (F-SH-06), 5 × `body`/`rowTitle`/`subtitle` `.copy` (F-SH-20), 2 × `monoPill.copy` / `rowTitle.copy(Bold)` on the gates. Roughly one invented step per 60 lines of SH UI code.
- **The global shell's motion is the best-behaved code in the section** — `NavMotion.kt` and `Motion.kt` are token-clean, reduce-motion-correct, unit-testable, and both scaffolds pass the identical four builders. The only two motion gaps are peripheral: the toast (F-SH-19) and the FAQ accordion (F-SH-15).
- **Nothing in the shell blocks the user except the two gates, which are supposed to.** No spinner page, no scrim, no `delay()`-paced UI, no M3 `AlertDialog` outside the already-filed F-CC-01; `ArtistTabsScaffold` and `ArtistantNavHost` carry no overlay at all.
- **Three of the four global chrome surfaces reserve or spend space they should not:** the toast reserves a tab bar that is not drawn (F-SH-03), the tab bar spends the screen's accent (F-SH-01), and the bar's disappearance reflows the screen it is leaving (F-SH-21). The fourth, `SessionDegradedBanner`, is clean — it uses the shared `Banner` at `BannerTone.Attention`, sits in the `Scaffold` `topBar` so it cannot cover content, and shares one composable across the shell and the reconnect splash.
- **Both pushed "utility" screens with a subtitle disagree about their own header** (F-SH-17), and the third pushed screen does not use `BackHeader` at all (F-SH-09). Three pushed SH screens, three header shapes.
- **Icon family mixing is present in five SH surfaces**, of which four are already the lead's F-CC-02 (`ActivityScreen` 3 filled / 3 outlined at `:429-437`, `WhatsNewSheet` `:204-208`, `RatePromptSheet`, `ArtistantNavHost` `:204-208`). The fifth — the tab bars — is filed here as F-SH-14 because it is chrome rather than a screen and is visible on every screen of the app.
- **Copy is genuinely good throughout** — the `"Success!"`/`"Thanks!"` hits in the exclaim sweep (`FeedbackScreen.kt:86`, `FeedbackViewModel.kt:28`, `ToastController.kt:34`) are all inside doc comments explaining why that copy is *not* used. No emoji, no "Oops", no "Please", no exclamation marks in any user-facing string in the section. The one copy failure is semantic rather than tonal (F-SH-04).
