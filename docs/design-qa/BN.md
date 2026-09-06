<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# BN — The booking & the night (screens 10, 89, 122, 18, 95, 83, 96, 97, 84, 117, 52, 20, 98, 78)

## Coverage
| Screen | File(s) | Reviewed | Findings |
|---|---|---|---|
| 10 Bookings list | `feature/bookings/BookingsScreen.kt:195-490`, `BookingsLogic.kt` | yes | F-BN-08, F-BN-09, F-BN-15, F-BN-16, F-BN-17 |
| 89 Bookings empty + nudge | `feature/bookings/BookingsScreen.kt:496-584` | yes | F-BN-09, F-BN-14, F-BN-16 |
| 122 Bookings offline | `feature/bookings/BookingsScreen.kt:586-710`, `BookingsSnapshot.kt` | yes | F-BN-06, F-BN-05, F-BN-08 |
| 18 Booking detail confirmed | `feature/booking/BookingDetailScreen.kt:613-691, 872-879` | yes | F-BN-07, F-BN-10, F-BN-11, F-BN-14, F-BN-16 |
| 95 Awaiting | `feature/booking/BookingDetailScreen.kt:693-708, 881-915` | yes | F-BN-14 |
| 83 Cancelled | `feature/booking/BookingDetailScreen.kt:710-761` | yes | F-BN-14, F-BN-15 |
| 96 Disputed | `feature/booking/BookingDetailScreen.kt:774-813, 921-960` | yes | F-BN-03, F-BN-13 |
| 97 Read-only | `feature/booking/BookingDetailScreen.kt:816-838, 962-964` | yes | F-BN-05, F-BN-15 |
| 84 Not found | `feature/booking/BookingDetailScreen.kt:987-1035`, `BookingDetailViewModel.kt:30-39` | yes | F-BN-01, F-BN-16 |
| 117 Cancel stage 1 | `feature/booking/BookingDetailScreen.kt:1091-1125` | yes | F-BN-12 |
| 52 Cancel stage 2 | `feature/booking/BookingDetailScreen.kt:1127-1188` | yes | F-BN-09, F-BN-10, F-BN-12 |
| 20 Review sheet | `feature/booking/ReviewSheet.kt:190-347` | yes | F-BN-02, F-BN-12, F-BN-13, F-BN-18, F-BN-19 |
| 98 Review sheet, no name | `feature/booking/ReviewSheet.kt:243-251` | yes | F-BN-13 |
| 78 Month calendar | `feature/bookings/MonthCalendarScreen.kt` | yes | F-BN-04, F-BN-05, F-BN-16 |

## Findings

### F-BN-01 — Not-found tells the user to pull-to-refresh a list that can't
- Screens: 84, 10
- Where: `feature/booking/BookingDetailScreen.kt:1023-1032`; `feature/bookings/BookingsScreen.kt:210-216`
- Category: copy | slow
- Severity: P1
- Rule: (c) fact — `grep -rn "PullToRefresh\|pullRefresh" feature/bookings/ feature/booking/` returns nothing; the Bookings list is a plain `Column(verticalScroll)`. Also an instance of F-CC-13.
- What: The one instruction on screen 84 is a gesture the app does not implement. A user who followed the push link, hit "Booking not found", tapped "Back to Bookings" and pulled down gets nothing — the only refresh in the section is the `Refresh` icon circle, and that one only appears while the list is cached (`BookingsScreen.kt:110-124`), i.e. not in this case. The sentence is also a question aimed at the user about their own sync state.
- Evidence:
  ```kotlin
  "Opened from a notification on a device that hasn't synced yet? " +
      "Pull Bookings to refresh."
  ```
- Fix: either add `PullToRefreshBox` to `BookingsList`/`CachedBookings`, or replace the footnote with the action that exists ("Try again" is already the primary here for `Unreachable`).

### F-BN-02 — Review sheet opens pre-rated five stars, "Great night"
- Screens: 20, 98
- Where: `feature/booking/ReviewSheet.kt:87-95` (`rating: Int = 5`); `:254-269`; `:391-397`
- Category: hierarchy | slop
- Severity: P1
- Rule: (c) fact + (a) §2 "one accent per screen" / "copy states the fact"
- What: The sheet mounts with all five stars filled in `accent` and the word "Great night" under them, before the client has touched anything. "Post review" is enabled from frame one, so a five-star review can be published with zero rating input, and the screen's entire accent budget is spent on an opinion nobody gave. It also removes the only signal that the taps landed — every star is already lit.
- Evidence:
  ```kotlin
  data class ReviewSheetUiState(
      val rating: Int = 5,
  …
  StarRow(rating = ui.rating, onRate = { haptics.select(); viewModel.setRating(it) })
  Text(ratingWord(ui.rating), …)   // "Great night" on entry
  ```
- Fix: start at `rating = 0` (all stars `hairline`, no word under them) and keep `PrimaryButton` disabled until a star is tapped.

### F-BN-03 — Disputed dock leads with a dead accent CTA and demotes the live actions
- Screens: 96
- Where: `feature/booking/BookingDetailScreen.kt:921-960`
- Category: hierarchy
- Severity: P1
- Rule: (a) §2 "one accent per screen" / one primary per screen; (b) every other variant's dock has exactly one primary (`:877`, `:887`, `:963`)
- What: The dispute page's bar stacks four objects: a permanently disabled accent `PrimaryButton` "Add evidence", a caption explaining why it is dead, a `SecondaryButton` "Message <name>", and a bare centred accent-ink text link "Message Artistant Support". The accent — the page's "the thing to do" — is spent on the one control that does nothing, while the two things the user can actually do are ranked below it, in two different button languages. Two of the three read "Message …".
- Evidence:
  ```kotlin
  PrimaryButton("Add evidence", onClick = {}, fullWidth = true, enabled = false)
  Text("Evidence goes through Support in this version.", …, textAlign = TextAlign.Center)
  SecondaryButton(text = … "Message $counterparty", …)
  Text("Message Artistant Support", style = …subtitle.copy(fontWeight = SemiBold), color = colors.accentInk, …)
  ```
- Fix: drop the disabled button, make "Message Artistant Support" the `PrimaryButton` (it is the dispute's real next step) and "Message <name>" the secondary; move the "evidence goes through Support" sentence into the body's "While this is open" list.

### F-BN-04 — Month calendar has no loading or failed state; both render as "Nothing this month"
- Screens: 78
- Where: `feature/bookings/MonthCalendarScreen.kt:59-83, 159-170`
- Category: consistency | slop
- Severity: P1
- Rule: (a) §2 principle "loading, empty and failed are three different screens and say which one they are"; (b) `BookingsScreen.kt:129-157` reads `state.isLoading` and `state.error` for the same ViewModel
- What: The screen reads only `state.items`. It shares `BookingsViewModel` with the list but never looks at `isLoading`, `error`, `offline` or `cached`, so while the first read is in flight — and permanently after a failed one — the grid draws with zero busy days under the headline "Nothing this month. No bookings land in this month." That is a confident false statement about the user's diary. Separately, the `EmptyState` at `:160` is not an empty state at all: with the calendar fully populated above it, the glyph-in-a-circle block "Pick a day / Tap a highlighted day to see what is on it" is a permanent instruction card that only disappears once a day is selected.
- Evidence:
  ```kotlin
  EmptyState(
      title = if (busyDays.isEmpty()) "Nothing this month" else "Pick a day",
      body = if (busyDays.isEmpty()) { "No bookings land in this month. …" } else …,
      icon = Icons.Filled.CalendarMonth,
  )
  ```
- Fix: branch on `state.isLoading` (skeleton grid) and `state.error` (failed + Retry) before the empty branch, and replace the "Pick a day" block with a one-line hint under the grid rather than an `EmptyState`.

### F-BN-05 — Three screens explain their own implementation to the user
- Screens: 78, 97, 122, 18
- Where: `feature/bookings/MonthCalendarScreen.kt:98`; `feature/booking/BookingDetailScreen.kt:833-837`; `feature/bookings/BookingsScreen.kt:704-707`; `feature/booking/BookingDetailScreen.kt:666-672`
- Category: copy | slop
- Severity: P2
- Rule: (a) §2 "copy states the fact"; rubric §4 — a paragraph explaining the app's architecture / "this version". Same family as F-CC-05 (which already covers this section's "Cached 9:04 am", "Showing your last sync", "Nothing is cached on this device yet", "$venue — cached, tap to copy").
- What: The month calendar's header subtitle is a note about code reuse ("Shared by Bookings and Gigs") shown where every peer `DetailHeader` puts a fact about the record. Screen 97 ends with a sentence defending its own UX decision. Screen 122's `AccentNote` explains the caching policy. The fee card says "Artistant holds no money in this version". None of these is a fact about this booking; all four are the team talking to itself.
- Evidence:
  ```kotlin
  DetailHeader(title = "Month calendar", subtitle = "Shared by Bookings and Gigs", onBack = onBack)
  Text("Actions are disabled rather than hidden, so you can see what would be available.", …)
  AccentNote("Venue, load-in notes and show times stay readable offline — that is the data you need on the night.")
  "Artistant holds no money in this version — this is the number you and the artist agreed…"
  ```
- Fix: subtitle → the month being shown or nothing; delete the 97 footnote (the disabled rows already say it); 122's note → "Saved for the night: venue, load-in notes, show times."; fee → "Paid directly to the artist after the set."

### F-BN-06 — Offline snapshot redraws bookings in a different card language, with the badge screen 10 forbids
- Screens: 122 vs 10
- Where: `feature/bookings/BookingsScreen.kt:623-650` vs `:290-374`
- Category: consistency | hierarchy
- Severity: P2
- Rule: (b) peer — the same object one tab-swipe earlier is a `radii.xl` picture card with an accent countdown; screen 10's own note (quoted at `BookingsLogic.kt:30-35`) says "Confirmed, pending and played each get a different affordance, **not a badge**"
- What: Going offline changes what a booking *is*: the r24 card with the act's photo, the countdown badge and Message / Tech rider becomes an r20 padded box with the artist's name and an accent-filled `Pill("Confirmed")` — a status badge, on every row, in the same `BrandSolid` tone the live list reserves for the "Leave a review" call to action. So the accent means "do this" on one screen and "this is the status" on the other, and it repeats once per row instead of once per screen.
- Evidence:
  ```kotlin
  Pill(if (confirmed) "Confirmed" else "Unknown",
       tone = if (confirmed) PillTone.BrandSolid else PillTone.Warm)
  ```
- Fix: keep the live card's shape offline (placeholder in the image band, actions disabled with the reason) and drop the per-row status pill — the banner at `:614` already says the whole list is a snapshot.

### F-BN-07 — The fee is set in bold sans; the booking id gets the mono
- Screens: 18, 95, 83, 97
- Where: `feature/booking/BookingDetailScreen.kt:1341-1353` (`FeeRow`); called at `:663`; `:1322-1335` (`TermsList`, `mono` only for the id — `BookingDetailLogic.kt:374`)
- Category: token | hierarchy
- Severity: P2
- Rule: (a) §2 "JetBrains Mono for eyebrow labels and numerals", "money is all-in and in ₹"; rubric §1 "money set in the sans" / "the important number not the largest, mono object in its block"
- What: `formatInr(booking.fee)` — the biggest fact on a confirmed booking — renders as `type.body.copy(fontWeight = FontWeight.Bold)`, i.e. 15sp sans with a call-site weight override, while the only mono on the page is the truncated booking id and "Guests" prints as a sans numeral too. The numerals ramp is inverted: the machine value is typographically louder than the money.
- Evidence:
  ```kotlin
  Text(value, style = AppTheme.type.body.copy(fontWeight = FontWeight.Bold), color = colors.ink)
  // TermsList: style = if (term.mono) AppTheme.type.monoSmall else rowTitle.copy(SemiBold)
  ```
- Fix: render the fee (and Guests) in the mono numeral style at one step up, and drop the `.copy(fontWeight=…)`.

### F-BN-08 — Booking status is a `Pill` here and a `StatusPill` one screen away
- Screens: 10, 122, 18, 83, 96, 97
- Where: `feature/booking/BookingDetailScreen.kt:605`, `:523-529`; `feature/bookings/BookingsScreen.kt:478-482`, `:646-649` vs `feature/messages/ThreadDetailsSheet.kt:438`
- Category: consistency
- Severity: P2
- Rule: (b) peer — `designsystem/component/StatusPill.kt:32-46` is documented as "A dot and a mono label: '● CONFIRMED', '● AWAITING'" and the messages surface uses it for exactly this value
- What: The same `BookingStatus` gets two components with two visual grammars: a dotless caption capsule (`Pill`, six `PillTone`s incl. an accent-filled one) on every BN screen, and the dot + mono `StatusPill` (five `StatusTone`s) in the thread details sheet. A user who opens a booking from its thread sees the status change shape.
- Evidence:
  ```kotlin
  Pill(statusLabel, tone = statusTone)                      // BookingDetailScreen.kt:605
  StatusPill(label = context.statusLabel, tone = context.pillTone)  // ThreadDetailsSheet.kt:438
  ```
- Fix: pick one — `StatusPill` reads as the design's status object — and route both through the existing `bookingStatusTone` helper.

### F-BN-09 — Tertiary actions are bare `Text`s at 20–40 dp
- Screens: 89, 52, 20, 96
- Where: `feature/bookings/BookingsScreen.kt:563-581`; `feature/booking/BookingDetailScreen.kt:1175-1185`, `:949-959`; `feature/booking/ReviewSheet.kt:335-345`
- Category: slow | consistency
- Severity: P2
- Rule: (c) fact — `space.sm` = 8, `space.md` = 12, `size.iconLg` = 20 (`designsystem/theme/Dimens.kt:9-10, 71`); none of these call `minimumInteractiveComponentSize()` or set a min height
- What: The name nudge's "Go" is a hand-rolled dark chip about 31 dp tall and its dismiss is a **20 dp** `Icon` with a `clickable` on it — a target less than half the 44 dp `size.rowMin` the same file uses for its card actions. "Keep booking" (the escape hatch on the destructive stage) is ~32 dp, "Not now" and "Message Artistant Support" ~40 dp. All four are the only way out of their screen or the only alternative to a destructive act.
- Evidence:
  ```kotlin
  Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = colors.ink2,
       modifier = Modifier.size(dimens.size.iconLg).clip(CircleShape)
           .clickable(role = Role.Button, onClick = onDismiss))
  ```
- Fix: give each a `heightIn(min = dimens.size.rowMin)` (or wrap the nudge's Close in a 40 dp `IconCircle`, which the header two lines up already uses).

### F-BN-10 — Two private `DestructiveButton`s, each documented as the only one
- Screens: 52, plus Delete account
- Where: `feature/booking/BookingDetailScreen.kt:1190-1213` vs `feature/profile/DeleteAccountScreen.kt:576-605`
- Category: consistency
- Severity: P2
- Rule: (b) peer — both KDoc blocks claim uniqueness ("The one destructive control in the app" / "the one control in the app painted in `danger`"), and the second one's comment even references the first
- What: The app's destructive button exists twice, privately, in two features, and the copies have already drifted: `DeleteAccountScreen`'s takes `enabled` (disabled → `hairline` fill, `ink3` label) and adds `fontWeight = FontWeight.Bold` on top of `type.cta`; BookingDetail's has no disabled state at all, so the cancel confirm stays live while `cancelBooking` is in flight and can be double-tapped. Same height (`component.cta` 54) and radius (`radii.buttonLg` 16), different behaviour.
- Evidence:
  ```kotlin
  private fun DestructiveButton(text: String, onClick: () -> Unit)                    // BookingDetail
  private fun DestructiveButton(text: String, enabled: Boolean, onClick: () -> Unit)  // DeleteAccount
  ```
- Fix: promote one into `designsystem/component/` with `enabled`, and use it in both places.

### F-BN-11 — On screen 18 "Cancel" is the third of three identical grey buttons
- Screens: 18
- Where: `feature/booking/BookingDetailScreen.kt:675-690`, `:1402-1417` (`FlatAction`)
- Category: hierarchy | copy
- Severity: P2
- Rule: (b) peer — the same act two taps later is a full-width `danger` CTA behind a two-stage flow (`:1170-1173`); rubric §2 "destructive action styling consistent across Delete account / Cancel booking / Decline"
- What: A confirmed booking's destructive action is a `surface2` box identical in size, radius, weight and colour to "Tech rider" and "Share", sitting third in a row of three. Nothing marks it. The client's label is the bare word "Cancel", which in a row of buttons reads as "dismiss/back" rather than "cancel this booking" — and the artist gets a different word ("Cancel gig") for the same control.
- Evidence:
  ```kotlin
  FlatAction("Tech rider", onTechRider, Modifier.weight(1f))
  FlatAction("Share", onShare, Modifier.weight(1f))
  FlatAction(if (viewer == BookingViewer.Artist) "Cancel gig" else "Cancel", onCancel, Modifier.weight(1f))
  ```
- Fix: pull it out of the row into its own full-width row with `danger` label text, and say "Cancel booking" to both sides.

### F-BN-12 — The same question-heading role uses two type steps
- Screens: 117, 52 vs 20
- Where: `feature/booking/BookingDetailScreen.kt:1094-1098`, `:1129-1133` vs `feature/booking/ReviewSheet.kt:225-231`
- Category: token | consistency
- Severity: P2
- Rule: (a) §2 — `screenTitle` 26/700 is the page title; `designsystem/theme/Type.kt:251-256` documents `displaySub` (21/700) as "Artist / person name at the top of a detail screen"
- What: Three sheets/pages in one feature ask the user a question at the top. The cancel stages set it in `screenTitle` (26); the review sheet sets the same thing in `displaySub` (21), a step whose stated job is a person's *name*. Two peers, two sizes, and one of them is borrowing a name style for a sentence.
- Evidence:
  ```kotlin
  Text(if (isDecline) "Why are you declining?" else "Why are you cancelling?", style = AppTheme.type.screenTitle, …)
  Text(if (named) "How was $artistName?" else "How was the set?", style = AppTheme.type.displaySub, …)
  ```
- Fix: one step for a question heading in this feature — `screenTitle` on the pages, and let the sheet's own scaffold title carry the rank (see F-BN-13).

### F-BN-13 — The review sheet is titled twice and fronted by a stock person glyph
- Screens: 20, 98
- Where: `feature/booking/ReviewSheet.kt:206-241`
- Category: hierarchy | slop
- Severity: P2
- Rule: (a) rubric §4 "a glyph-in-a-circle above every empty state"; (b) peer — every other BN surface identifies the act with its picture (`BookingDetailScreen.kt:573-584`, `BookingsScreen.kt:315-322, 436-443`)
- What: `SheetScaffold(title = "Leave a review")` draws the sheet's title bar, and the button that opened it also said "Leave a review" — then the body opens with a second, larger heading. Above both sits an `emptyGlyphCircle` containing `Icons.Outlined.Person`: a generic silhouette standing in for the artist on a page whose whole subject is that artist, while the host already holds `state.artist?.coverUrl` and passes it to `IdentityCard` on the screen underneath.
- Evidence:
  ```kotlin
  SheetScaffold(modifier = modifier, title = "Leave a review") {
      Box(Modifier.size(dimens.component.emptyGlyphCircle)…) { Icon(Icons.Outlined.Person, …) }
      Text(if (named) "How was $artistName?" else "How was the set?", style = AppTheme.type.displaySub, …)
  ```
- Fix: pass the cover into the sheet and draw the act's photo in that disc (initials disc as the fallback, as `IdentityCard` already does); drop one of the two titles.

### F-BN-14 — Accent-tinted note used as a general callout, up to twice per page
- Screens: 89, 122, 18, 95, 83, 117, 52
- Where: `feature/booking/BookingDetailScreen.kt:662`, `:704`, `:727`, `:1119`, `:1146`; `feature/bookings/BookingsScreen.kt:545`, `:704`; component fill at `designsystem/component/AccentNote.kt:61-62, 121-122`
- Category: token | hierarchy
- Severity: P2
- Rule: (a) §2 "one accent per screen"; §4 gives `accentSoft`/`brandSoft` `#f5fbda` as the token for "accent at ~12% on white"; rubric §1 flags `.copy(alpha=…)` tints and §3 flags `AccentNote` as a generic callout
- What: Seven of the section's blocks are wrapped in the accent-tinted note, including the fee card on screen 18 — which is a primary section, not an aside — so a confirmed booking shows an accent-filled status `Pill`, an accent-tinted card, and (when completed) an accent CTA at once. On 95 and 83 the accent note stacks under a full-width `Banner`, giving the page two tinted callouts before its first fact. The tint itself is manufactured as `accent.copy(alpha = FILL_ALPHA)` with an `accent.copy(alpha = STROKE_ALPHA)` hairline rather than the `accentSoft` token that exists for it.
- Evidence:
  ```kotlin
  .background(colors.accent.copy(alpha = FILL_ALPHA))
  .border(dimens.size.hairline, colors.accent.copy(alpha = STROKE_ALPHA), shape)
  ```
- Fix: repaint `AccentNote`/`AccentNoteCard` from `accentSoft` + `hairline`, and keep it to one per page — the fee block should be a plain `surface3` card like `IdentityCard`.

### F-BN-15 — Three different alpha values mean "stepped back"
- Screens: 10, 83, 97, 122
- Where: `feature/bookings/BookingsScreen.kt:490` (`ENDED_ALPHA = 0.55f`, applied `:433`, `:630`); `feature/booking/BookingDetailScreen.kt:300` (`DIMMED = 0.6f`, applied `:549`, `:723`); `:821` (`Modifier.alpha(0.85f)`, literal)
- Category: token | consistency
- Severity: P3
- Rule: (b) internal — one relationship ("this record is terminal / frozen"), three values, one of them an un-named literal
- What: A cancelled row fades to 0.55, the cancelled detail page's card and terms to 0.6, the read-only page's terms to 0.85. Placed side by side these read as three different meanings; they are the same meaning.
- Evidence:
  ```kotlin
  Column(Modifier.alpha(0.85f)) { TermsList(bookingTerms(booking, packageName)) }
  ```
- Fix: one token (e.g. `AppColors.dimmed` or a single `DIMMED` in the design system) used by all three.

### F-BN-16 — Empty, failed and "wrong tab" are the same block with the same glyph
- Screens: 10, 89, 84, 78
- Where: `feature/bookings/BookingsScreen.kt:145-156`, `:245-259`, `:523-529`; `feature/booking/BookingDetailScreen.kt:1009-1015`; `feature/bookings/MonthCalendarScreen.kt:160-169`
- Category: consistency | slop
- Severity: P2
- Rule: (a) §2 "loading, empty and failed are three different screens and say which one they are"; "every empty state carries an action"
- What: Six `EmptyState` calls across the section, all with `icon = Icons.Filled.CalendarMonth`, all the same glyph-in-a-circle + title + body + button. "Couldn't load bookings", "You're offline", "No bookings yet", "Nothing coming up", "Booking not found" and "Pick a day" are visually one screen with the words swapped. Two of them carry no action at all (the Past segment at `:257-258` passes `actionLabel = null`; the month calendar passes none), and the failed branch pipes `state.error` — `error.message` straight off the throwable, `BookingsViewModel.kt:215` — into `body` as user-facing prose. Placement differs too: this section's empty states sit under a `Spacer(space.xxl)` at the top, except screen 84's, which is vertically centred (`:1002-1016`).
- Evidence:
  ```kotlin
  EmptyState(title = if (state.offline) "You're offline" else "Couldn't load bookings",
             body = if (state.offline) { … } else { state.error },
             icon = Icons.Filled.CalendarMonth, actionLabel = "Try again", …)
  ```
- Fix: distinct glyph (or none) for failure vs empty, an action on every one, one vertical placement, and never render a raw `Throwable.message` as body copy.

### F-BN-17 — The Bookings header's trailing control changes identity by state
- Screens: 10, 122
- Where: `feature/bookings/BookingsScreen.kt:109-125`, `:614-622`
- Category: consistency | hierarchy
- Severity: P3
- Rule: (b) internal — the same 40 dp slot holds two unrelated destinations
- What: The header circle is the month calendar normally and a Refresh button when the list is cached, so the calendar entry point silently disappears exactly when the user is most likely to be checking a date on the night. And it duplicates an action already on screen: the cached banner three rows down carries "Retry".
- Evidence:
  ```kotlin
  if (state.showsCached) { IconCircle(icon = Icons.Filled.Refresh, contentDescription = "Try again", …) }
  else { IconCircle(icon = Icons.Filled.CalendarMonth, contentDescription = "Month calendar", …) }
  ```
- Fix: keep the calendar circle in the header at all times; the banner's "Retry" is the retry.

### F-BN-18 — Copy-to-clipboard acknowledges itself for a different length of time than its twin
- Screens: 18
- Where: `feature/booking/BookingDetailScreen.kt:158-164`, `:296-297` (`COPIED_LABEL_MS = 1_600L`) vs `feature/epk/EpkPanes.kt:1051-1056`, `:1100` (`COPIED_RESET_MS = 1_400L`)
- Category: consistency | slow
- Severity: P3
- Rule: (b) peer — identical interaction (label swaps to "Copied", reverts on a timer), two constants, two names, two durations
- What: The same acknowledgement pattern is implemented twice with hand-tuned timings 200 ms apart.
- Evidence:
  ```kotlin
  private const val COPIED_LABEL_MS = 1_600L   // BookingDetailScreen.kt:297
  private const val COPIED_RESET_MS = 1_400L   // EpkPanes.kt:1100
  ```
- Fix: one constant in the design system, used by both.

### F-BN-19 — Four radii for three classes of object on one page
- Screens: 18, 96, 52, 20
- Where: `feature/booking/BookingDetailScreen.kt:546` (card, `radii.card` 20), `:783` and `:1271` (cards, `radii.buttonLg` 16), `:1410` and `:1428` (buttons, `radii.md` 12), `:1206` (CTA, `radii.buttonLg` 16); `feature/bookings/BookingsScreen.kt:390` (button, `radii.md`)
- Category: token
- Severity: P3
- Rule: (a) §2 geometry — card 16–18, CTA 16; (b) internal — `IdentityCard` and `ConsequenceCard` are the same object at two radii, and buttons on the same page sit at 12 while the CTA sits at 16
- What: On screen 18 alone a card is r20, the flat actions under it are r12 and the dock's CTA is r16; on screen 52 the consequence cards are r16 (a *button* token) while the identity card above them is r20. The corner language changes three times in one scroll.
- Evidence:
  ```kotlin
  .clip(RoundedCornerShape(dimens.radii.card))       // IdentityCard, 20
  .clip(RoundedCornerShape(dimens.radii.buttonLg))   // ConsequenceCard, 16
  .clip(RoundedCornerShape(dimens.radii.md))         // FlatAction / DisabledAction, 12
  ```
- Fix: cards on `radii.lg` (18), all buttons on `radii.buttonLg` (16), and stop using `buttonLg` for cards.

## Section-wide observations
- **`BottomActionBar` is not dead — F-CC-11 is wrong about it.** It has six call sites: `BookingDetailScreen.kt:877, 881, 921, 962, 1160` and `feature/system/{UpdateRequiredScreen.kt:141, ServiceOutageScreen.kt:175}`. It is exactly what the BN screens pin their CTA with, and screen 18 deliberately has no dock (`:844-851`). `StatusTimeline` I did not check; `StatusPill` *is* live (`ThreadDetailsSheet.kt:438`) — see F-BN-08.
- **The five detail variants share their top honestly.** All five go through one `DetailHeader` + one `IdentityCard` + one variant banner, in that order (`BookingDetailScreen.kt:346-419`), and the `DetailHeader` KDoc names the exact screens. The divergences that remain are the dock (F-BN-03), the destructive styling (F-BN-11) and the dim level (F-BN-15) — not the frame.
- **Type steps are being invented at call sites: 10 `.copy(fontWeight = …)`** — `BookingsScreen.kt:448, 472, 553, 565`; `BookingDetailScreen.kt:951, 1177, 1329, 1349`; `ReviewSheet.kt:263, 337`. Four of them are the same "tertiary link" recipe (`subtitle` + SemiBold), which wants to be one component (see F-BN-09).
- **Centred text on forms: 7 sites** — `ReviewSheet.kt:229, 237, 265, 339` and `BookingDetailScreen.kt:930, 953, 1179`. The review sheet centres its header block and its footer link but left-aligns everything between, so the page's axis moves twice.
- **The review sheet hand-rolls its input.** `ReviewSheet.kt:291-312` uses `BasicTextField` + a custom `decorationBox` instead of `AppTextField`, and paints the placeholder `ink4` where the design system's own field uses the `hint` token (`AppTextField.kt:169`, `SearchBar.kt:77`).
- **Month grouping is gone from the list** and `monthLabelFromDateLabel` (`designsystem/component/MonthCalendar.kt:829`) now has no production caller — only tests. CLAUDE.md's Aug-5 entry still describes month headers on Bookings/Gigs as shipped; a long Upcoming list is now a flat run of cards with no date scaffolding.
- **Icon families mix inside single components** (instances of F-CC-02): `DetailHeader` pairs `AutoMirrored.Filled.ArrowBack` with an `AutoMirrored.Outlined.Message` trailing circle (`DetailHeader.kt:61` + its own preview at `:109`; `BookingDetailScreen.kt:353`); `BookingsScreen.kt` is 9 Filled / 2 Outlined; `ReviewSheet.kt` puts `Outlined.Person` and `Filled.Star` on the same 400 px of screen.
- **Also instances of already-filed cross-cutting findings:** F-CC-05 jargon (`BookingsLogic.kt:199-201`, `BookingsScreen.kt:108, 148, 678`, `BookingsSnapshot.kt:144`, plus `BookingDetailScreen.kt:402-404` "This app version doesn't know this status" and `:927` "in this version"); F-CC-12 no crossfade (`BookingsScreen.kt:316, 437`; `BookingDetailScreen.kt:578`); F-CC-13 no pull-to-refresh (see F-BN-01); F-CC-15 `RevealOnAppear` (`BookingsScreen.kt:209`, `BookingDetailScreen.kt:211`); F-CC-04 skeletons (`BookingsScreen.kt:718-747`, `BookingDetailScreen.kt:1437-1461`).
