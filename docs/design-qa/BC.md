<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# BC — Book & confirm (screens 05, 06, 07, 94, 132, 61, 17)

## Coverage
| Screen | File(s) | Reviewed | Findings |
| 05 | feature/booking/BookingScreen.kt, BookingChrome.kt (FunnelStepBar, FunnelCalendar, PackageChoiceRow, CtaBar), BookingSlots.kt | yes | F-BC-01, F-BC-03, F-BC-04, F-BC-05, F-BC-06, F-BC-14, F-BC-15, F-BC-17, F-BC-18, F-BC-20 |
| 06 | feature/booking/CheckoutScreen.kt, CheckoutLogic.kt, BookingChrome.kt (FunnelBar, ActRow, TermRow, CtaCaption) | yes | F-BC-02, F-BC-03, F-BC-04, F-BC-07, F-BC-09, F-BC-14, F-BC-16, F-BC-17, F-BC-19 |
| 07 | feature/booking/ConfirmedScreen.kt, BookingChrome.kt (OutcomeMark, FunnelCard) | yes | F-BC-04, F-BC-07, F-BC-08, F-BC-16, F-BC-17 |
| 94 | feature/booking/MatchConfirmedScreen.kt | yes | F-BC-04, F-BC-05, F-BC-07, F-BC-08, F-BC-11, F-BC-16 |
| 132 | feature/booking/InvoiceScreen.kt, InvoiceLogic.kt | yes | F-BC-03, F-BC-04, F-BC-09, F-BC-10, F-BC-11, F-BC-19 |
| 61 | feature/booking/CounterOfferScreen.kt | yes | F-BC-13, F-BC-14, F-BC-18, F-BC-21 |
| 17 | feature/booking/RequestQuoteScreen.kt, RequestQuoteViewModel.kt (strings) | yes | F-BC-03, F-BC-04, F-BC-08, F-BC-12, F-BC-15, F-BC-16, F-BC-18, F-BC-19 |

Also read: `TechRiderSheet.kt` (no findings of its own; it is the correct peer in F-BC-12), `BookingDraftStore.kt` (no UI strings), `designsystem/component/Headers.kt`, `BottomActionBar.kt`, `DockSurface.kt` (unused by this section). `BookingDetailScreen.kt` / `BookingDetailLogic.kt` / `ReviewSheet.kt` were left to their auditor.

## Findings

### F-BC-01 — 05 loads behind a bare centred spinner with no header
- Screens: 05
- Where: `feature/booking/BookingScreen.kt:86-95` (spinner `:93`)
- Category: slow
- Severity: P1
- Rule: (a) §2 principles "narrated, not a spinner"; (b) peers `InvoiceScreen.kt:159-166`, `MatchConfirmedScreen.kt:136-143`, `CounterOfferScreen.kt:293-298` narrate one `body` line ("Reading the booking…")
- What: The first thing the client sees after tapping Book is a lone `CircularProgressIndicator` on a white page — no step bar, no close circle, no words. Every other loading state in this section is a sentence under the screen's own header; this is the one spinner page in the funnel, and it sits at its front door.
- Evidence:
  ```kotlin
  state.isLoading && state.artist == null -> {
      Box(
          modifier
              .fillMaxSize()
              .background(colors.surface),
          contentAlignment = Alignment.Center,
      ) {
          CircularProgressIndicator(color = colors.accentInk)
  ```
- Fix: Render `FunnelStepBar("Step 1 of 2")` plus the question and a "Reading {artist}'s calendar…" line (or a `Skeleton` of the calendar card) in place of the spinner.

### F-BC-02 — 06's submit is an opaque full-screen spinner takeover that also swallows Back
- Screens: 06
- Where: `feature/booking/CheckoutScreen.kt:198-201`, `:275-311` (spinner `:292`), `:99` (`BackHandler` swallow)
- Category: slow
- Severity: P1
- Rule: (c) blocking full-screen scrim + spinner; rubric §4 "narration step lists that are spinners with captions"; (b) `feature/messages/ChatScreen.kt:987` uses the library `SendingNarration` for the same job
- What: Tapping Send replaces the whole review page with a white sheet holding a `CircularProgressIndicator`, a 19sp title ("Sending your request to X…") and a caption, and Back is disabled for the duration. The user loses the terms they just read, and the "narration" is a spinner with a sentence under it. The double-submit reason in the comment is solved by the disabled CTA alone (`enabled = !state.blocked`, `:190`).
- Evidence:
  ```kotlin
  Column(
      Modifier
          .fillMaxSize()
          .background(colors.surface)
          .clickable(interactionSource = interaction, indication = null) {}
          .padding(space.xxl),
  ) {
      CircularProgressIndicator(color = colors.accentInk)
  ```
- Fix: Keep the page; put `SendingNarration` (the chat's component) inside `CtaBar` in place of the button while `waitPhase != null`, and drop `NarratedWait` and the `BackHandler`.

### F-BC-03 — The funnel's pushed header is a 40dp fork of `BackHeader(centered = false)`
- Screens: 05, 06, 17, 132
- Where: `feature/booking/BookingChrome.kt:222-231` (KDoc claim), `:258-263`, `:288`, `:315-320`, `:331`; `designsystem/component/Headers.kt:103`, `:114-118`, `:148`; `feature/booking/BookingScreen.kt:102-113` (a third bar on the failed branch)
- Category: consistency
- Severity: P2
- Rule: (a) §2 "header … centred 17/700 with a 42 back circle"; (b) `BackHeader` already offers `centered = false` with a subtitle — the exact reason `FunnelBar`'s KDoc gives for existing ("Left-aligned, unlike … the library's `BackHeader`")
- What: Three of the section's screens draw `FunnelBar`, 05 draws `FunnelStepBar`, and 05's failed branch draws a bare `Row` with a lone `IconCircle` and no title. All use `iconCircleSm` (40) where the library and §2 use 42, and the trailing "mirror" spacer is `controlMin` (48), so the title block sits 8dp off the mirror the comment promises. FunnelBar also pads 2dp under the title and allows two subtitle lines; BackHeader pads 0 and allows one — the same pushed screen in two sections looks different by a few dp everywhere.
- Evidence:
  ```kotlin
  IconCircle(
      icon = leadingIcon,
      contentDescription = leadingLabel,
      onClick = onLeading,
      size = dimens.component.iconCircleSm,
  )
  // The circle's mirror, so a title with no trailing control still starts
  // where a title with one does.
  if (trailing != null) trailing() else Spacer(Modifier.width(dimens.size.controlMin))
  ```
- Fix: Delete `FunnelBar`/`FunnelStepBar`; call `BackHeader(centered = false, subtitle = …)` on 06/17/132 and `BackHeader(title = "Step 1 of 2")` on 05 (close glyph via a parameter), and give 05's failed branch the same bar.

### F-BC-04 — Two pinned bars with different tailroom; the funnel's dock makes every caller re-pad its actions
- Screens: 05, 06, 07, 94, 132, 17
- Where: `feature/booking/BookingChrome.kt:171`, `:186-194` (`CtaBar`) vs `designsystem/component/BottomActionBar.kt:46-53`; `designsystem/theme/Dimens.kt:197`; callers `BookingScreen.kt:260`, `ConfirmedScreen.kt:200`, `:235`, `MatchConfirmedScreen.kt:250`
- Category: spacing
- Severity: P2
- Rule: (b) twin bars: `CtaBar` bottom = `ctaBarTailroom` 28 + inset, `BottomActionBar` bottom = `space.lg` 16 + inset; (a) `CtaBar`'s KDoc says "16 above, 30 below" while the token it applies is 28
- What: A funnel screen's button floats 12dp higher above the home indicator than every other pushed screen's, and `CtaBar` has no `spacedBy`, so the second action is placed by hand: `top = space.md` on 05/07/94 but `top = space.lg` for 07's third action.
- Evidence:
  ```kotlin
  // BookingChrome.kt:186-194
  .padding(start = gutter, end = gutter, top = dimens.space.lg)
  .padding(bottom = dimens.size.ctaBarTailroom)
  // BottomActionBar.kt:49-53
  top = dimens.space.lg,
  bottom = dimens.space.lg + WindowInsets.navigationBars…,
  verticalArrangement = Arrangement.spacedBy(dimens.space.md),
  ```
- Fix: Make `CtaBar` a thin wrapper over `BottomActionBar` (one tailroom token, `spacedBy(md)`) and remove the per-caller `padding(top = …)`.

### F-BC-05 — Four accent fills compete at once on 05 and on 94
- Screens: 05, 94
- Where: 05: `BookingChrome.kt:945` (selected day), `:996` / `:1001` / `:1027` (package wash, rim, tick disc), `BookingScreen.kt:366` + `designsystem/component/Chip.kt:54` (time chip — preselected "8:30 PM" by `BookingSlots.kt:66-67`), `BookingScreen.kt:255` (CTA). 94: `MatchConfirmedScreen.kt:162` (disc), `:208` (`AccentBadge`), `:229` (`AccentNote`, 22% accent fill per `AccentNote.kt:129`), `:237` (CTA); the Monogram KDoc `:262-263` admits the accent "is already spent on the tick and the badge"
- Category: hierarchy
- Severity: P2
- Rule: (a) §2 principles "one accent per screen"
- What: With a date picked, 05 shows a lime day cell, a lime-washed package row with a lime tick, a lime time chip and a lime CTA in one viewport. 94 shows a 74dp lime disc, a lime "Confirmed" capsule, a lime-washed note and a lime CTA. Nothing on either page is *the* signal.
- Evidence:
  ```kotlin
  .background(if (selected) colors.accent else Color.Transparent)      // day, :945
  .background(if (selected) colors.accent else Color.Transparent)      // tick disc, :1027
  targetValue = if (selected) colors.accent else colors.surface2,      // Chip.kt:54
  ```
- Fix: On 05 carry package/time selection with an `ink` rim + `ink` tick and 700 weight (keep the day and the CTA lime); on 94 set the status word in `StatusPill` (neutral tone) so only the disc and the CTA are lime.

### F-BC-06 — Selected-package wash is a hand-mixed 26% lime, not `accentSoft`
- Screens: 05
- Where: `feature/booking/BookingChrome.kt:996`, `:1072`; peer `designsystem/component/AccentNote.kt:129` mixes its own 22%; `docs/REDESIGN_2026-09.md` §4 defines `accentSoft` = `#f5fbda` ("accent at ~12% on white")
- Category: token
- Severity: P2
- Rule: (a) rubric "alpha hacks (`.copy(alpha=…)`) that manufacture an off-palette tint" (sweep `colors.txt:8`)
- What: 05's chosen tier is a 26% lime over `surface`, 06's note one screen later is a 22% lime — two lime washes that are neither the palette's soft tint nor each other.
- Evidence:
  ```kotlin
  targetValue = if (selected) colors.accent.copy(alpha = SELECTED_WASH) else colors.surface3,
  …
  private const val SELECTED_WASH = 0.26f
  ```
- Fix: `colors.accentSoft` for the wash (and for `AccentNote`'s fill), delete `SELECTED_WASH`.

### F-BC-07 — The act header is drawn three ways on 06 / 07 / 94, and 07's never gets a cover
- Screens: 06, 07, 94
- Where: `feature/booking/CheckoutScreen.kt:145-150` (`ActRow`, 62dp `actThumb`, cover); `ConfirmedScreen.kt:170-178` (`ActRow`, 64dp `avatarLg`, no `coverUrl`); `MatchConfirmedScreen.kt:182`, `:266-290` (48dp initials disc); `BookingChrome.kt:498-512` (a null cover leaves the `placeholder` square)
- Category: consistency
- Severity: P2
- Rule: (b) the same object (the act on the card) on three consecutive screens
- What: 06 shows the artist's cover at 62dp/r12; 07 shows a permanently empty `placeholder`-grey square at 64dp beside the name (the ViewModel fetches the artist at `:86` but the screen never passes `artist.coverUrl`); 94 shows a grey circle with initials at 48dp. The client watches the same artist's picture turn into a blank slot and then into letters.
- Evidence:
  ```kotlin
  ActRow(
      name = who,
      thumbSize = dimens.size.avatarLg,
      lines = listOfNotNull(
          bookingReference(booking.id)…?.let { "Booking #$it" },
      ),
  )
  ```
- Fix: Keep the artist in `ConfirmedUiState`, pass `coverUrl` and the default `actThumb` on 07, and replace 94's `Monogram` with the same `ActRow`.

### F-BC-08 — The three outcome screens disagree about their own actions
- Screens: 07, 94, 17
- Where: `feature/booking/ConfirmedScreen.kt:212`, `:216-221`, `:227-236`; `MatchConfirmedScreen.kt:238`, `:242-251`; `RequestQuoteScreen.kt:400`
- Category: consistency
- Severity: P2
- Rule: (b) copy + action styling across peers; rubric §2 "button labels are verbs"; (c) touch target
- What: "View booking" (07) vs "View the booking" (94). "Back to discover" is a `SecondaryButton` on 07 but an `accentInk` text link on 94. 07 stacks three actions in the dock (primary, secondary, then a "See the record" link). 17's outcome ends on "Done". The two text links are `Text.clickable` with only a top padding, roughly 32–36dp tall.
- Evidence:
  ```kotlin
  // ConfirmedScreen.kt:227-236
  Text("See the record", style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
      color = colors.accentInk, textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth().clickable { onOpenInvoice(bookingId) }.padding(top = space.lg))
  // MatchConfirmedScreen.kt:242-251
  Text("Back to discover", … .clickable(onClick = onBackToDiscover).padding(top = space.md))
  ```
- Fix: One dock recipe for 07/94/17 — `PrimaryButton` + `SecondaryButton` — with "View booking" on both, "Back to discover" as the secondary everywhere (17 included, instead of "Done"), and "See the record" moved into the card as a row with a chevron.

### F-BC-09 — "v1" and "this version" reach the user three times
- Screens: 06, 132
- Where: `feature/booking/CheckoutScreen.kt:109`, `:178-179`; `feature/booking/InvoiceLogic.kt:57`
- Category: copy
- Severity: P2
- Rule: rubric §4 (engineering vocabulary — "v1", "this build" — in user-facing strings; sweep `jargon.txt:16`)
- What: The 06 header reads "Nothing is charged — v1 takes no payment"; its note ends "Artistant takes nothing in this version."; the record prints "₹0 — no fee in this version". The user should read the fact, not the release plan.
- Evidence:
  ```kotlin
  subtitle = "Nothing is charged — v1 takes no payment",
  …
  "and settle directly — Artistant takes nothing in this version.",
  …
  InvoiceLine("Artistant fee", "₹0 — no fee in this version"),
  ```
- Fix: "Nothing is charged" / "…settle directly. Artistant takes nothing." / `"₹0"`.

### F-BC-10 — 132 is titled "Invoice" while every other string on it calls it a record
- Screens: 132
- Where: `feature/booking/InvoiceScreen.kt:138` vs `:145`, `:169`, `:235`; `feature/booking/InvoiceLogic.kt:85`, `:118-120`
- Category: copy
- Severity: P2
- Rule: (b) internal inconsistency; the file's own KDoc (`:103`) quotes the design note "a record, not a tax invoice"
- What: The header says "Invoice"; the share button says "Share this record", the failed state "No record to show", the shared text opens "Artistant — booking record", and the closing paragraph says Artistant "issues no tax invoice". The one word the design says this document must not use is its title.
- Evidence:
  ```kotlin
  FunnelBar(
      title = "Invoice",
      subtitle = invoiceSubtitle(reference, booking?.date.orEmpty()),
  ```
- Fix: `title = "Booking record"`.

### F-BC-11 — The booking's status word is three different capsules across 94, 132 and the library
- Screens: 94, 132
- Where: `feature/booking/MatchConfirmedScreen.kt:208-214` (`AccentBadge`, defined `BookingChrome.kt:398-413`); `feature/booking/InvoiceScreen.kt:208` (`Pill` + `bookingStatusTone`); `designsystem/component/StatusPill.kt:46` (the library's status capsule, unused here)
- Category: consistency
- Severity: P2
- Rule: (b) rubric "same concept must be the same component"; `AccentBadge`'s own KDoc (`:392-395`) says `Pill` sets the wrong type step (12.5 regular vs the design's 11.5 bold)
- What: "Confirmed" is a solid-lime 11.5/700 capsule on 94 and a toned `Pill` in 12.5 regular on 132 for the same booking; the library's `StatusPill` is a third shape neither screen uses.
- Evidence:
  ```kotlin
  AccentBadge(if (booking.status == BookingStatus.Confirmed) "Confirmed" else booking.status.label)
  …
  Pill(booking.status.label, tone = bookingStatusTone(booking.status))
  ```
- Fix: `StatusPill(booking.status)` on both; delete `AccentBadge`.

### F-BC-12 — 17's picker sheets keep M3's default shape and open half-height; the sibling TechRider sheet does it right
- Screens: 17
- Where: `feature/booking/RequestQuoteScreen.kt:214-220`, `:240-246` vs `feature/booking/TechRiderSheet.kt:45-51`; `designsystem/component/SheetScaffold.kt:61-66` (the scaffold paints its own rounded `surface`)
- Category: consistency
- Severity: P2
- Rule: §5 rule 7 "never ship an M3 default"; (c) `rememberModalBottomSheetState()` without `skipPartiallyExpanded` opens a tall sheet at half height, so the month grid arrives cut off and needs a drag
- What: The date and time sheets pass `containerColor = colors.surface` so M3's own container and corner shape show under `SheetScaffold`'s, and allow the partially-expanded stop; `TechRiderSheet` in the same package passes `Color.Transparent` and `skipPartiallyExpanded = true`.
- Evidence:
  ```kotlin
  val sheet = rememberModalBottomSheetState()
  ModalBottomSheet(onDismissRequest = { showDates = false }, sheetState = sheet,
      dragHandle = null, containerColor = colors.surface) {
      SheetScaffold(title = "When's the show?") { FunnelCalendar(…) }
  ```
- Fix: `rememberModalBottomSheetState(skipPartiallyExpanded = true)` and `containerColor = Color.Transparent` on both sheets, exactly as `TechRiderSheet.kt:45-51`.

### F-BC-13 — 61 is a pushed route dressed as a sheet: a grey void behind it and a hand-rolled header with three ways out
- Screens: 61
- Where: `feature/booking/CounterOfferScreen.kt:237-250` (scrim), `:260-289` (header); `navigation/ArtistTabsScaffold.kt:508-509` (`composable(route = COUNTER_OFFER)`); `designsystem/theme/Color.kt:174` (`glassSoftScrim` = `#80000000`); `designsystem/component/IconCircle.kt:85` (no touch floor); peers `RequestQuoteScreen.kt:221`, `:247` pass `SheetScaffold(title = …)`
- Category: consistency
- Severity: P2
- Rule: (b) `SheetScaffold` has a title slot the other sheets use; (c) a 32dp control (`avatarSm`, an avatar token) and a scrim with no page under it
- What: Because the destination is an ordinary `composable`, the 50% black scrim dims nothing — the artist sees a mid-grey full screen with a white panel at the bottom. Inside, the panel draws its own "Cancel" link, a centred title and a 32dp close circle; Cancel, the circle and the scrim all dismiss.
- Evidence:
  ```kotlin
  Box(modifier.fillMaxSize().background(colors.glassSoftScrim).clickable(… onClick = onDismiss)) {
      Column(Modifier.align(Alignment.BottomCenter)) {
          SheetScaffold(modifier = Modifier.clickable(…) { }) {
              Row(…) {
                  Text("Cancel", style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold), …)
                  Text("Counter offer", style = AppTheme.type.sectionTitle, …)
                  IconCircle(icon = Icons.Filled.Close, …, size = dimens.size.avatarSm)
  ```
- Fix: Either make 61 a real `ModalBottomSheet` hosted by the gig detail (then `SheetScaffold(title = "Counter offer")`, one grabber, no Cancel/×), or make it a plain pushed page with `BackHeader` and no scrim.

### F-BC-14 — Money is the smallest text in 05's dock, and set in the sans at four sizes across the section
- Screens: 05, 06, 61, 132
- Where: `feature/booking/BookingScreen.kt:246-253` (`subtitle.copy(SemiBold)`, 13.5); `BookingChrome.kt:1062-1067` (`rowTitle` bold, 14.5); `CheckoutScreen.kt:171-175` and `InvoiceScreen.kt:224` (`sectionTitle`, 17, via `TermRow` emphasis); `CounterOfferScreen.kt:322-326` (`displaySmall`, 19); `designsystem/theme/Type.kt:330` (`monoPrice`, used only by `feature/paywall/PaywallScreen.kt`)
- Category: hierarchy
- Severity: P3
- Rule: (a) §2 "JetBrains Mono for … numerals", rubric "the important number (money) not the largest/mono object in its block"; the KDoc at `BookingChrome.kt:970-972` says the light design sets the package price in the sans, which is accepted for the rows — but not for the dock
- What: In 05's pinned bar the fee ("₹1,20,000") is 13.5sp semibold, the same size as the grey summary beside it and smaller than the button label under it; the same number is 17sp on 06 and 19sp on 61.
- Evidence:
  ```kotlin
  Text(
      formatInr(fee),
      style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),
      color = colors.ink,
  ```
- Fix: Dock price in `rowTitle` bold (or `monoNumber`) — the one step every price row already uses — and one step for the emphasised fee on 06/61/132.

### F-BC-15 — Twenty `.copy(fontWeight = …)` steps, and the field label is defined three times
- Screens: 05, 06, 07, 94, 132, 61, 17
- Where: `feature/booking/BookingChrome.kt:323`, `:375`, `:520`, `:842`, `:955-957`, `:1064`; `BookingScreen.kt:248-250`, `:293`; `CheckoutScreen.kt:229`; `ConfirmedScreen.kt:229`; `MatchConfirmedScreen.kt:186`, `:244`; `CounterOfferScreen.kt:268`, `:312-314`, `:342`; `RequestQuoteScreen.kt:291`; `FieldLabel` duplicated in `BookingScreen.kt:290-297` and `RequestQuoteScreen.kt:288-295`, both re-deriving `designsystem/component/AppTextField.kt:105`
- Category: token
- Severity: P3
- Rule: (a) rubric "`.copy(fontWeight=…)` inventing a new step"; §5 rule 3 "a shared component used by two or more screens goes in `designsystem/component/`"
- What: The step indicator, the dock price, every text-link action, every card title and the "Their offer" / "Your number" labels are each a one-off weight on a named step; the 12.5-semibold field label exists as two private composables plus the text field's own copy.
- Evidence:
  ```kotlin
  style = AppTheme.type.subtitle.copy(fontWeight = FontWeight.SemiBold),   // BookingChrome.kt:323
  style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Normal),     // :375
  style = AppTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),    // BookingScreen.kt:293
  ```
- Fix: Add `fieldLabel`, `rowTitleStrong` and `linkLabel` to `AppType`, export one `FieldLabel` from the design system, and replace the twenty copies.

### F-BC-16 — Body copy set in `ink4` on 07 / 94 / 17 and in `subtitle` on 06
- Screens: 06, 07, 94, 17
- Where: `feature/booking/ConfirmedScreen.kt:162-164`; `MatchConfirmedScreen.kt:171-172`; `RequestQuoteScreen.kt:394-395`; `CheckoutScreen.kt:238-241` (`subtitle`, `ink2`), `:256-261` (`subtitle`, `ink3`)
- Category: token
- Severity: P3
- Rule: (a) §2 `body` 15/400 is "body copy on light" in `ink3`; `subtitle`/`ink4` are meta
- What: The one sentence under each outcome headline ("Your request is with X — we'll notify you…") is set in the caption colour; 06's "What happens next" paragraph and the custom-quote notice are set in the 13.5 meta step.
- Evidence:
  ```kotlin
  style = AppTheme.type.body,
  color = colors.ink4,            // ConfirmedScreen.kt:162-163
  …
  style = AppTheme.type.subtitle,
  color = colors.ink2,            // CheckoutScreen.kt:239-240
  ```
- Fix: `body` + `ink3` for all five paragraphs.

### F-BC-17 — Entry motion differs step to step, and 07 reflows once its read lands
- Screens: 05, 06, 07
- Where: `feature/booking/BookingScreen.kt:126`, `CheckoutScreen.kt:125` (`RevealOnAppear`, `contentReveal` = `medium2` = 300 ms per `designsystem/theme/Motion.kt:53`, `:82`) — absent on 07 / 94 / 132 / 17 / 61; `ConfirmedScreen.kt:167-186`, `:190-222`
- Category: slow
- Severity: P3
- Rule: (c) a 300 ms fade stacked on the nav push; two of seven funnel screens fade in, five cut
- What: 05 and 06 fade their whole content tree in after the push; the next screen (07) appears instantly with "Request sent." and no card, then the card pops in and the dock swaps its two buttons when the booking read resolves.
- Evidence:
  ```kotlin
  RevealOnAppear {                                   // BookingScreen.kt:126
  …
  val booking = ui.booking
  if (booking != null) { FunnelCard(…) { … } }       // ConfirmedScreen.kt:167-169
  ```
- Fix: Drop `RevealOnAppear` on 05/06 (the nav transition already animates), and on 07 reserve the card with a `Skeleton` until the read resolves.

### F-BC-18 — Copy nits: a step counter with no step 2, a failed state that restates itself, ungrouped hints, a warning under a live button
- Screens: 05, 06, 17, 61
- Where: `feature/booking/BookingScreen.kt:132` vs `CheckoutScreen.kt:108`; `BookingScreen.kt:115-116` + `BookingViewModel.kt:91`; `RequestQuoteScreen.kt:155`, `:175`; `CounterOfferScreen.kt:370-384`
- Category: copy
- Severity: P3
- Rule: (b) rubric copy conventions ("₹1,20,000" grouping; a subtitle that restates the title; a disabled CTA with a stated reason — here the inverse)
- What: 05 announces "Step 1 of 2" but 06's bar says "Confirm request", never "Step 2 of 2". 05's failed state is titled "Artist not found" over the body "Artist not found.". 17's budget hint is "35000" where the app prints "₹35,000". 61 prints "You've already countered this one." *under* a still-enabled "Send counter".
- Evidence:
  ```kotlin
  hint = "35000",
  …
  PrimaryButton(text = if (state.isSending) "Sending counter…" else "Send counter", … enabled = state.canSend)
  if (state.request?.status == GigRequestStatus.Countered) {
      Text("You've already countered this one.", …)
  ```
- Fix: Title 06's bar "Step 2 of 2" (subtitle "Confirm request") or drop the counter; body "We couldn't find this artist."; hint "35,000"; on 61 disable the CTA (or hide the caption) when already countered.

### F-BC-19 — Icon semantics: `Filled.Schedule` means two things, and the record shares with the iOS glyph
- Screens: 06, 17, 132
- Where: `feature/booking/RequestQuoteScreen.kt:144` (time picker) vs `CheckoutScreen.kt:222` ("What happens next"); `InvoiceScreen.kt:144` (`Icons.Filled.IosShare` — the only share glyph on a live screen; `Icons.Filled.Share` appears only in `designsystem/component/IconCircle.kt`'s preview)
- Category: consistency
- Severity: P3
- Rule: (b) one glyph per concept; platform convention for share on Android is Material's `Share`
- What: The clock glyph is "pick a start time" on 17 and "what happens next" on 06; the record's trailing circle carries the iOS square-and-arrow.
- Evidence:
  ```kotlin
  icon = Icons.Filled.IosShare,
  contentDescription = "Share this record",
  ```
- Fix: `Icons.Filled.Share` on 132; drop the glyph from "What happens next" (a card title needs none).

### F-BC-20 — `BookingChrome.kt` carries seven dead composables (13 of its 16 compat aliases live in them); `StatusTimeline` is drawn by no screen
- Screens: 05, 06, 17 (the live alias users)
- Where: `feature/booking/BookingChrome.kt:84` `CircleIconButton`, `:132` `FunnelHeader` (`type.headline` `:143`), `:544` `SectionLabel` (uppercases the sans `caption`, `:546-547`), `:563` `FunnelCta` (a capsule CTA at `ctaTall` 52 with `ctaLabel`/`brand`/`bgSoft`, `:575-598`), `:614` `PopularBadge`, `:639` `PackageOptionRow` (its KDoc `:967-969` says the artist profile "still draws" it — nothing does), `:773` `AvailabilityLegend` (`colors.good` `:787`); `designsystem/component/StatusTimeline.kt:57`, `:74`, `:76-77`, `:102`, `:115` (raw 22/14/18/2/8 dp) referenced only by `EventTimeline.kt`. Live compat aliases: `type.footnote` at `BookingChrome.kt:212` (`CtaCaption`), `BookingScreen.kt:222`, `RequestQuoteScreen.kt:196`
- Category: slop
- Severity: P3
- Rule: rubric §4 "dead code"; §1 compat aliases; `footnote` is now an alias of `chip` (13.5/500, `Type.kt:282`), so `CtaCaption`'s KDoc "12sp" (`:205`) is wrong and both note counters are set in the chip style
- What: A quarter of the funnel's chrome file is a second, pre-redesign chrome (a centred glass-disc header, a pill CTA, a mono-priced package row with a "Popular" chip, a Free/Busy legend) that no screen calls; the three live compat uses set a caption in the chip step.
- Evidence:
  ```kotlin
  fun CtaCaption(text: String, modifier: Modifier = Modifier) {
      Text(text, style = AppTheme.type.footnote, color = AppTheme.colors.ink4, …
  ```
- Fix: Delete the seven composables; `CtaCaption` and the two counters → `AppTheme.type.caption`.

### F-BC-21 — The counter amount well ignores the section's field recipe
- Screens: 61
- Where: `feature/booking/CounterOfferScreen.kt:405-412` vs `designsystem/component/AppTextField.kt:89-98` and `RequestQuoteScreen.kt:323-325` (`PickerField`); KDoc `:396` says 22sp, `displaySmall` is 19 (`Type.kt:257-258`)
- Category: consistency
- Severity: P3
- Rule: (b) every other field in the section is a `surface2` well with a `hairline` rim that turns `ink` on focus
- What: The one field on 61 is a white box with a permanent 1.5dp ink border — it always looks focused, and it is the only white-on-white well in the funnel.
- Evidence:
  ```kotlin
  .clip(shape)
  .background(colors.surface)
  .border(dimens.component.focusStroke, colors.ink, shape)
  .defaultMinSize(minHeight = dimens.funnel.amountField)
  ```
- Fix: `surface2` fill, `hairline` rim, `ink` rim only while focused (track `interactionSource`), and correct the KDoc to 19sp.

## Section-wide observations
- Headers: five pushed-screen bar recipes in seven screens — `FunnelStepBar` (05), `FunnelBar` (06/17/132), a bare `Row` + `IconCircle` (05 failed), a hand-rolled sheet header (61), none (07/94) — and not one is the library `BackHeader`; every circle is 40, never the §2 42.
- Loading: three narrated lines (94/132/61), one spinner page (05), one spinner takeover (06 submit), zero `Skeleton`s; 07 deliberately has no loading state. Failed states all carry Retry, but their titles read as empty ("Nothing to show" 94:146, "No record to show" 132:169, "Nothing to counter" 61:302, "Nothing to confirm" 06:116) while the bodies say "Couldn't load…" — failure ≠ empty is carried by the body only.
- Compat aliases are almost gone: the only live ones are `footnote` ×3; the other 13 sit in dead code (F-BC-20).
- Copy is otherwise on convention (sentence case, "Couldn't", "·", "…", "—" for absent values); the exceptions are the "v1"/"this version" trio, "Done", and three labels for the same number ("Artist fee" 06/94, "Agreed fee … · direct" 07:296, "Total" 132).
- Money: `formatInr` groups Indian-style everywhere (`common/util/Inr.kt:9-16`); every price in the section is sans, at four sizes (F-BC-14); `monoPrice` exists and is used only by the paywall.
- The two note counters match each other (500 chars, `VENUE_NOTES_MAX` BookingViewModel.kt:24, `QUOTE_NOTE_MAX` RequestQuoteViewModel.kt:30, both drawn in `footnote`); the chat composer's cap is 4,000 (`feature/messages/MessageComposer.kt:198`) — a different object, not a mismatch.
- `CheckoutScreen.kt:74` `LaunchedEffect(Unit)` only collects haptic events (no refetch) — fine; `OutcomeMark`'s `LaunchedEffect(Unit)` (BookingChrome.kt:430) re-springs on every entry, which is the intended pop, and it animates through `graphicsLayer`. `AsyncImage` in `ActRow` has no crossfade, consistently within the section.
- `AccentNote` appears once per screen on 06/94/132/61 (never stacked), and `Banner`s never stack with it — the one-aside rule holds; the accent-count failures are the selection states (05) and the badge (94), not the notes.
