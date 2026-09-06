<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# MS — Messaging & safety (screens 19, 110, 08, 70, 88, 33, 73, 60, 111, 131, 34, 127)

## Coverage
| Screen | File(s) | Reviewed | Findings |
| 19 Inbox | `feature/messages/MessagesScreen.kt` | yes | F-MS-01, F-MS-03, F-MS-04, F-MS-09, F-MS-14 |
| 110 Inbox other state | `feature/messages/MessagesScreen.kt` | yes | F-MS-05, F-MS-06, F-MS-11 |
| 08 Chat | `feature/messages/ChatScreen.kt`, `ChatQuoteCard.kt`, `MessageComposer.kt` | yes | F-MS-01, F-MS-07, F-MS-09, F-MS-12, F-MS-15 |
| 70 Chat accept narration | `ChatScreen.kt:970-1000`, `designsystem/component/SendingNarration.kt` | yes | none (no paced `delay`; phases follow real work) |
| 88 Chat other state | `feature/messages/ChatScreen.kt` | yes | F-MS-06, F-MS-11, F-MS-13 |
| 33 Thread details sheet | `feature/messages/ThreadDetailsSheet.kt` | yes | F-MS-01, F-MS-02, F-MS-08, F-MS-15 |
| 73 Report conversation sheet | `feature/messages/ReportConversationSheet.kt` | yes | F-MS-10, F-MS-13, F-MS-16 |
| 60 Archived list | `feature/messages/ArchivedScreen.kt` | yes | F-MS-03, F-MS-04, F-MS-14 |
| 111 Archived other state | `feature/messages/ArchivedScreen.kt` | yes | F-MS-05, F-MS-14 |
| 131 Safety centre | `feature/messages/SafetyCentreScreen.kt` | yes | F-MS-07, F-MS-08 |
| 34 Support chat | `feature/messages/SupportChat.kt` | yes | F-MS-03, F-MS-15, F-MS-16 |
| 127 Blocked accounts | `feature/profile/BlockedAccountsScreen.kt` | yes | F-MS-02, F-MS-03, F-MS-04, F-MS-06, F-MS-11, F-MS-14 |

Already filed by the lead, instances in this section (not re-filed): **F-CC-09** bare-spinner
loading pages — `ChatScreen.kt:191`, `ArchivedScreen.kt:131`, `BlockedAccountsScreen.kt:99`
(three of the six loading states in this section are a centred `CircularProgressIndicator`;
only the inbox, `MessagesScreen.kt:630` `InboxSkeleton`, keeps the design's promise).
**F-CC-15** `RevealOnAppear` around the whole blocked list, `BlockedAccountsScreen.kt:125`.
**F-CC-13** no pull-to-refresh on Archived (the inbox has `PullToRefreshBox`,
`MessagesScreen.kt:168`; its twin does not). **F-CC-10** compat aliases: `type.footnote`
×11 in this section (`MessagesScreen.kt:534`, `ChatScreen.kt:360`, `ArchivedScreen.kt:282`,
`ReportConversationSheet.kt:141,153`, `ThreadDetailsSheet.kt:673`, `SupportChat.kt:410`,
`BlockedAccountsScreen.kt:218,306,349`), `colors.brandSoft` ×2 (`MessagesScreen.kt:578`,
`ChatScreen.kt:732`). **F-CC-02** icon-family mixing: `Outlined.ChatBubbleOutline`,
`Filled.Archive`, `Filled.Star`, `Filled.Inventory2`, `Filled.WarningAmber`, `Outlined.Info`,
`Filled.Close`, `Outlined.Flag`, `Filled.Shield`, `Outlined.Block` all inside these 12 screens.
**F-CC-05** engineering copy: "In this version they can still send you messages"
(`BlockedAccountsScreen.kt:136`).

Checked and found sound (no finding): `SendingNarration` is a real step list driven by
`ChatViewModel.acceptQuote`'s three awaits (`ChatViewModel.kt:632,634,640`) — no paced
`delay`. `rememberMinuteClock`'s `delay(CLOCK_TICK_MS)` (`MessagesScreen.kt:684`) is a
60 s tick that re-reads one `Long`; it invalidates the row lambdas once a minute, which is
the cheapest correct way to keep "12m" honest. `LaunchedEffect(Unit)` at `ChatScreen.kt:116`
collects an event flow and at `:1076` sleeps to the next local midnight — neither refetches.
The two `TextAlign.Center` in `ChatScreen.kt` (804 system row, 838 day separator) are
correctly centred objects. `CounterQuoteSheet` and `ThreadDetailsSheet` both pass
`dragHandle = null`, `containerColor = colors.surface`, `skipPartiallyExpanded = true` — no
M3 default, one drag to dismiss.

## Findings

### F-MS-01 — Money is set in the sans, three different ways, in one section
- Screens: 19, 08, 33
- Where: `feature/messages/ChatQuoteCard.kt:86`; `feature/messages/ThreadDetailsSheet.kt:449-450`; `feature/messages/MessagesScreen.kt:573-574`
- Category: token
- Severity: P1
- Rule: (a) §2 Type — "Plus Jakarta Sans everywhere; **JetBrains Mono for eyebrow labels and numerals**"; `monoNumber` is Mono 18/700 (`Type.kt:231`), `displaySmall` is **Sans** 19/700 (`Type.kt:257`), `sectionTitle` and `badge` are Sans. (b) peer `feature/gigs/GigRequestDetailScreen.kt:510` sets the same object — a quote amount — as `AppTheme.type.monoHero`.
- What: the amount is the whole point of this section (screen 19's second line, screen 08's quote object, screen 33's "Artist fee"), and no surface here uses the mono numeral face. Worse, all three are different: ₹48,000 is 11.5sp `badge` in the inbox, 19sp `displaySmall` on the quote card, and 17sp `sectionTitle` on the details card — set at exactly the same size and weight as the words "Artist fee" beside it, so the number does not win its own row.
- Evidence:
  ```kotlin
  // ChatQuoteCard.kt:84-88
  Text(formatInr(quote.amountInr), style = AppTheme.type.displaySmall, color = colors.ink)
  // ThreadDetailsSheet.kt:449-450
  Text("Artist fee", style = AppTheme.type.sectionTitle, color = colors.ink)
  Text(formatInr(it), style = AppTheme.type.sectionTitle, color = colors.ink)
  // MessagesScreen.kt:572-575
  Text("QUOTE ${formatInr(quote.amountInr)}", style = AppTheme.type.badge, …)
  ```
- Fix: set every ₹ amount in this section in the mono ramp (`monoNumber` on the quote card and the fee row, `monoPill` in the inbox pill), and step the fee up from its "Artist fee" label.

### F-MS-02 — `BannerTone.Promotion` (a solid lime block) is used for neutral information
- Screens: 33, 127
- Where: `feature/messages/ThreadDetailsSheet.kt:292-296`; `feature/profile/BlockedAccountsScreen.kt:133-138`
- Category: token / hierarchy
- Severity: P1
- Rule: (a) §2 "`accent` `#d6f84b` — **the one signal**: primary CTA, selected chip, badges"; `Banner.kt:111` maps `Promotion` to `colors.accent` fill with `onAccent` text and a transparent stroke.
- What: two safety surfaces open with a full-width solid-lime block carrying purely explanatory prose — "No booking yet — this is an inquiry." and "A blocked account stops appearing in your inbox, and they aren't told." Nothing is being promoted and nothing is actionable, yet these are the loudest objects on their screens; on the details sheet the lime block sits directly above the row list whose only red item (Report) is the thing that should stand out. `Info` (`surface3` + hairline) is the tone these two are describing.
- Evidence:
  ```kotlin
  // ThreadDetailsSheet.kt:292-296
  Banner(
      title = "No booking yet — this is an inquiry.",
      detail = "Nothing is agreed until a request is sent and accepted.",
      tone = BannerTone.Promotion,
  ```
- Fix: switch both to `BannerTone.Info`; keep `Promotion` for the paywall, which is what an accent-filled banner is for.

### F-MS-03 — Three near-identical conversation rows, hand-rolled three times
- Screens: 19, 60, 127, 34
- Where: `feature/messages/MessagesScreen.kt:424-556` (`ThreadRow`); `feature/messages/ArchivedScreen.kt:231-303` (`ArchivedRow`); `feature/profile/BlockedAccountsScreen.kt:256-322` (`BlockedAccountRowUi`); `feature/messages/SupportChat.kt:440-460` (`SupportOptions`)
- Category: consistency
- Severity: P2
- Rule: (b) the same anatomy — 48 `Avatar` · name · meta · trailing control · inset `HRule` — is written out three times with drifting values, while `designsystem/component/ListRow.kt` exists and this section's own sheet uses it nine times (`ThreadDetailsSheet.kt:314-378`).
- What: the three lists differ in ways nobody chose. The name is `rowTitle` + **Bold** in the inbox and the archive but **SemiBold** in blocked accounts; the preview is `ink2` when unread and `ink4` when read in the inbox but always `ink4` in the archive; the star, the quote line and the unread badge exist only on the inbox row even though the archive projects the same `ThreadListItem`. Support adds a fourth variant — a hairline-bordered card with a chevron, i.e. a `ListRow` with a stroke around it.
- Evidence:
  ```kotlin
  MessagesScreen.kt:470  style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
  ArchivedScreen.kt:260  style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Bold),
  BlockedAccountsScreen.kt:279 style = AppTheme.type.rowTitle.copy(fontWeight = FontWeight.SemiBold),
  ```
- Fix: extract one `ConversationRow` into `designsystem/component/` taking optional badge/star/quote slots and a trailing action, and have all three lists call it.

### F-MS-04 — `rowTitle` and `footnote` get four invented weights between them
- Screens: 19, 60, 73, 88, 127, 34
- Where: `feature/messages/MessagesScreen.kt:308,470,534`; `feature/messages/ChatScreen.kt:343,360`; `feature/messages/ArchivedScreen.kt:260,282`; `feature/messages/ReportConversationSheet.kt:141,153,224`; `feature/messages/ThreadDetailsSheet.kt:400,433,673`; `feature/messages/SupportChat.kt:410,454`; `feature/profile/BlockedAccountsScreen.kt:218,279,306,349`
- Category: token
- Severity: P2
- Rule: (a) §2 gives `rowTitle` one spec (14.5/600) and no "footnote" step at all — `Type.kt:282` aliases `footnote` to `chip` (13.5/500). Every site above re-weights the step at the call site instead of using it.
- What: the same two roles are drawn at four weights. Row/tile names appear as rowTitle+Bold (inbox, archive, support option, booking card), rowTitle+SemiBold (blocked row, report row) and rowTitle+Medium (report reason). Inline text actions appear as footnote+Bold ("Review request", "Details", "Go to Bookings", "Report the conversation", blocked "Retry") and footnote+SemiBold ("Unarchive", "Unblock", "Read our trust & safety guide", "Back", "Discard this report"). Two links of different importance — an accent-ink navigation link and a grey "Back" — end up identical in size and weight, differing only in colour.
- Evidence:
  ```kotlin
  ReportConversationSheet.kt:141  AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold)  // "Read our trust & safety guide"
  ReportConversationSheet.kt:153  AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold)  // "Back"
  ReportConversationSheet.kt:224  AppTheme.type.rowTitle.copy(fontWeight = FontWeight.Medium)
  ```
- Fix: add one `linkLabel` step to the ramp, use `rowTitle` unmodified for row names, and delete every `.copy(fontWeight = …)` in the section.

### F-MS-05 — The inbox's failed-empty is an `EmptyState`; its twin the archive draws a `Banner`
- Screens: 110, 111
- Where: `feature/messages/MessagesScreen.kt:186-192`; `feature/messages/ArchivedScreen.kt:139-152`
- Category: consistency
- Severity: P2
- Rule: (b) two peer list screens, the same state ("read failed and there is nothing to show"), two different components and two different page shapes.
- What: fail to load the inbox and you get a centred `EmptyState` — glyph, title, body, Retry. Fail to load the archive, one tap away, and you get a `dangerSoft` `Banner` floating in the middle of the page with a pill-shaped Retry inside it. Same sentence structure, entirely different object.
- Evidence:
  ```kotlin
  // MessagesScreen.kt:186-192
  state.error != null && state.activeThreads.isEmpty() -> EmptyState(
      title = "Couldn't load messages", body = state.error, actionLabel = "Retry", …)
  // ArchivedScreen.kt:144-150
  Banner(title = "Couldn't load your archive", detail = state.error,
      tone = BannerTone.Failure, actionLabel = "Retry", …)
  ```
- Fix: use `EmptyState` for whole-page failures everywhere in the section (the blocked screen already does, `BlockedAccountsScreen.kt:110`) and reserve `Banner` for the strip-over-content case.

### F-MS-06 — Three "couldn't refresh" strips, three different components
- Screens: 110, 88, 127
- Where: `feature/messages/MessagesScreen.kt:161-172` (`Banner`); `feature/messages/ChatScreen.kt:936-961` (hand-rolled `Row` + a bare "Retry" word); `feature/profile/BlockedAccountsScreen.kt:328-361` (`StaleNotice`, a hand-rolled `Column` in `warm` with its own "Retry"/"Retrying…" word)
- Category: consistency
- Severity: P2
- Rule: (b) one message ("the refresh failed, the list on screen is stale, here is a retry") rendered by `Banner` on one screen and hand-rolled on two others.
- What: the chat's version is a plain 12.5sp grey sentence with an accent-ink "Retry" beside it and no container at all, so it reads as chat content rather than as chrome; the blocked screen's version is a `warm` sentence with the retry under it; the inbox's is a proper `dangerSoft` banner. A user who sees all three in a session sees three unrelated failure languages.
- Evidence:
  ```kotlin
  // ChatScreen.kt:944-951
  Text("Couldn't refresh this conversation.", style = AppTheme.type.caption, color = colors.ink2, …)
  Text("Retry", style = AppTheme.type.caption.copy(fontWeight = FontWeight.Bold),
       color = colors.accentInk, …)
  ```
- Fix: `Banner(tone = Attention, actionLabel = "Retry")` in all three places; the chat's copy already fits inside a banner's title/detail.

### F-MS-07 — Four accent-filled objects compete on the Safety centre; the numbered-rule row is duplicated
- Screens: 131
- Where: `feature/messages/SafetyCentreScreen.kt:108,135-160` (esp. `:150` `.background(colors.accent)`); peer `feature/signup/CommunityCommitmentScreen.kt:107-130` (esp. `:123`)
- Category: hierarchy / consistency
- Severity: P2
- Rule: (a) §2 "one accent per screen"; the accent is "the one signal". (b) §5 rule 3 — "a shared component used by two or more of your screens goes in `designsystem/component/`".
- What: the screen stacks three lime 28dp ordinal tiles, plus a lime shield on the dark card above them, so nothing on the page is emphasised. The community pledge does the identical thing with four tiles, and the row is written out twice — same `surface3` container, same `radii.lg`, same `iconXl` accent square, but a different inner style (`badge` here, `caption.copy(fontWeight = Black)` there) and different padding (`space.md` here, `space.lg` there). Two copies of one component that already drift.
- Evidence:
  ```kotlin
  // SafetyCentreScreen.kt:146-152
  Box(Modifier.size(dimens.size.iconXl).clip(RoundedCornerShape(dimens.radii.sm))
          .background(colors.accent), contentAlignment = Alignment.Center) {
      Text(number.toString(), style = AppTheme.type.badge, color = colors.onAccent)
  ```
- Fix: extract one `NumberedRule` into `designsystem/component/` and give the ordinal `surface2`/`ink` (or `ink`/`onDark`), keeping the lime for the screen's single signal.

### F-MS-08 — The Safety centre's dark card is painted `ink`, not `dark`
- Screens: 131
- Where: `feature/messages/SafetyCentreScreen.kt:79`
- Category: token
- Severity: P2
- Rule: (a) §2 — `ink` `#14150f` is "primary text, icons"; `dark` `#16171a` is "dark surfaces (splash, quote cards)" (`Color.kt:43` vs `Color.kt:74`).
- What: the screen's thesis card — its one dark object, with `onDark`/`onDarkSoft` text on it — uses the text token as a surface. It is two hex points from correct so nobody will see it, but it means the section's dark surfaces are not one colour: the inbox's Support disc and the support bot's mark both use `darkest` (`MessagesScreen.kt:300`, `SupportChat.kt:373`), this card uses `ink`, and the palette's own `dark` is used by neither.
- Evidence:
  ```kotlin
  // SafetyCentreScreen.kt:76-80
  .clip(RoundedCornerShape(dimens.radii.card))
  .background(colors.ink)
  ```
- Fix: `colors.dark`, and pick one of `dark`/`darkest` for the brand disc on both screens.

### F-MS-09 — The status capsule and the quote pill are hand-rolled next to `StatusPill`/`Pill`
- Screens: 88, 19
- Where: `feature/messages/ChatScreen.kt:567-586` (`StatusChip`); `feature/messages/MessagesScreen.kt:565-590` (`QuoteLine`); peer `feature/messages/ThreadDetailsSheet.kt:438` uses `StatusPill(label = context.statusLabel, tone = context.pillTone)`
- Category: consistency
- Severity: P2
- Rule: (b) the rubric's own case — "same concept must be the same component". `StatusPill.kt` and `Pill.kt` both exist, and the *same* `context.statusLabel` string is drawn by `StatusPill` on the details sheet and by a raw `Box`/`Text` in the transcript.
- What: three pill-shaped objects in the section, three implementations: `StatusPill` on the sheet, a `CircleShape`-clipped `Text` in `surface2` with `badge` type in the transcript, and a `radii.sm` `brandSoft` chip with `accentDeep` text in the inbox. Open a thread from a row and the same booking status changes shape and colour between the row, the transcript and the sheet.
- Evidence:
  ```kotlin
  // ChatScreen.kt:570-579
  Text(label, style = AppTheme.type.badge, color = colors.ink2,
       modifier = Modifier.clip(CircleShape).background(colors.surface2)
           .clickable(onClick = onClick).padding(…))
  ```
- Fix: `StatusPill` in the transcript with the same `pillTone` mapping the sheet uses; `Pill` for the inbox QUOTE line.

### F-MS-10 — `ReasonRow` is implemented twice, with different ring weights
- Screens: 73
- Where: `feature/messages/ReportConversationSheet.kt:170-231` (esp. `:203`, `:218`); peer `feature/artist/ArtistProfileSheets.kt:296-335` (esp. `:322`, `:324`)
- Category: consistency
- Severity: P2
- Rule: (b) the file's own doc comment says "**Same shape as report artist**, deliberately" — and then re-implements it. The unselected ring is `dimens.size.hairline` (1dp) here and `dimens.component.focusStroke` there.
- What: two private composables with the same name draw the same radio in two feature packages. Beyond the drift, selecting a reason lights a solid 20dp lime disc *and* enables the lime Submit CTA, putting two accent-filled objects on a report form at once.
- Evidence:
  ```kotlin
  // ReportConversationSheet.kt:216-219
  Box(Modifier.fillMaxWidth().height(dimens.size.iconLg)
      .border(dimens.size.hairline, colors.lineStrong, CircleShape))
  ```
- Fix: promote one `RadioRow` to `designsystem/component/`, use it from both sheets, and make the selected mark `ink`-on-`surface2` so the CTA keeps the accent.

### F-MS-11 — Three empty states in this section carry no action
- Screens: 110, 88, 127
- Where: `feature/messages/MessagesScreen.kt:207-215`; `feature/messages/ChatScreen.kt:201-205`; `feature/profile/BlockedAccountsScreen.kt:173-179`
- Category: consistency
- Severity: P2
- Rule: (a) §2 Principles — "**every empty state carries an action**". The section's other three (`MessagesScreen.kt:186,197`, `ArchivedScreen.kt:154`) all pass `actionLabel`.
- What: filter the inbox to a segment with nothing in it and you get "No conversations here / Nothing in bookings yet. Try another filter." with no way to clear the filter — the chips are above, but the state that names the problem does not offer the fix. Same for a search with no matches. "No one is blocked" and the chat's "No messages yet" likewise end in a full stop.
- Evidence:
  ```kotlin
  // MessagesScreen.kt:207-215
  state.visibleThreads.isEmpty() -> EmptyState(
      title = "No conversations here",
      body = if (state.query.isBlank()) { "Nothing in … yet. Try another filter." } else { … },
      icon = Icons.Outlined.ChatBubbleOutline,
  )
  ```
- Fix: `actionLabel = "Show all conversations"` / `"Clear search"` on the filter-empty; "Find an artist" on the chat's; leave blocked's empty (it is a true zero) or drop its glyph.

### F-MS-12 — The counter-quote sheet's only field does not take focus
- Screens: 08
- Where: `feature/messages/ChatQuoteCard.kt:200-217`
- Category: slow
- Severity: P2
- Rule: (c) fact — no `FocusRequester` exists anywhere in this section (`grep -rn 'FocusRequester\|requestFocus' feature/messages/` returns nothing).
- What: tapping "Counter" opens a sheet whose entire purpose is one number, and the reader must then tap the field to raise the keyboard. Two taps where one would do, on the one screen in this section where there is nothing else to press.
- Evidence:
  ```kotlin
  // ChatQuoteCard.kt:207-214
  AppTextField(value = raw, onValueChange = { raw = it.filter(Char::isDigit).take(AMOUNT_DIGITS) },
      label = "Your amount (₹)", hint = "e.g. 40000",
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), …)
  ```
- Fix: a `FocusRequester` with `LaunchedEffect(Unit) { focus.requestFocus() }` on the amount field.

### F-MS-13 — The details sheet hand-rolls a title bar with a 28 dp close target
- Screens: 33, 88
- Where: `feature/messages/ThreadDetailsSheet.kt:145-146`, `508-540` (esp. `:526`); peer `feature/messages/ChatQuoteCard.kt:200` uses `SheetScaffold(title = "Counter this quote")`
- Category: consistency
- Severity: P2
- Rule: (b) `SheetScaffold` takes a `title` (`SheetScaffold.kt:51`) and one sibling sheet uses it; this one calls `SheetScaffold { }` and draws its own bar. (c) the close disc is `dimens.size.iconXl` = **28 dp** (`Dimens.kt:72`) with no `minimumInteractiveComponentSize`, against a 44 dp floor (`size.rowMin`).
- What: the sheet everyone reaches from a chat has a bespoke header, and its only visible dismiss is a 28 dp circle in the top-right corner — smaller than every other tap target in the section, and the one control on the sheet a shaky thumb will miss. The bar's own doc comment promises "a centred title and one way out", but the title is in a `weight(1f)` `Text` with no `textAlign`, so it renders left-aligned and the 28 dp balancing `Spacer` at `:515` does nothing.
- Evidence:
  ```kotlin
  // ThreadDetailsSheet.kt:515-530
  Spacer(Modifier.size(dimens.size.iconXl))
  Text(title, style = AppTheme.type.sectionTitle, …, modifier = Modifier.weight(1f))
  Box(Modifier.size(dimens.size.iconXl).clip(CircleShape).background(colors.surface2)
      .clickable(onClick = onClose)…)
  ```
- Fix: use `SheetScaffold(title = …)` and give the close control `component.iconCircleSm` (40) via `IconCircle`, as the chat header does at `ChatScreen.kt:333`.

### F-MS-14 — Internal invariants are shipped as body copy, and the badge rule is said twice
- Screens: 60, 111, 127
- Where: `feature/messages/ArchivedScreen.kt:158-160` and `:186-190`; `feature/profile/BlockedAccountsScreen.kt:233-236`
- Category: slop / copy
- Severity: P2
- Rule: (a) §5 rule 2 asks for data honesty — the *fact*, not the mechanism; the rubric's §4 flags "a paragraph on a utility screen that explains the app's architecture to the user".
- What: the archive tells the reader "Archived threads are excluded from the Messages badge, so the count can never exceed what the inbox shows" — a spec assertion about a counter, in the second half of a sentence no user has a question about — and then says the badge half again in its empty-state body twelve lines earlier. Blocked accounts repeats the pattern: "Blocked threads are excluded from your unread badge, same as archived ones." Nobody wondering where their archived chats went is asking about badge arithmetic.
- Evidence:
  ```kotlin
  // ArchivedScreen.kt:186-190
  Text("Archived threads are excluded from the Messages badge, so the count can never " +
       "exceed what the inbox shows. Archiving is saved on this device only.", …)
  ```
- Fix: keep the honest half ("Archiving is saved on this device only.") and cut the badge sentence from both screens; it already lives in the archive's empty-state body once.

### F-MS-15 — Assistant-voice tics in the support script and the report receipt
- Screens: 34, 33
- Where: `feature/messages/SupportChat.kt:148,152,155,175`; `feature/messages/ThreadDetailsSheet.kt:615`
- Category: copy
- Severity: P3
- Rule: (a) §2 Principles — "copy states the fact, not 'success'"; rubric §4 flags reassurance/celebration openers.
- What: five lines open with filler rather than the answer — "Your safety comes first.", "No problem. Tap …", "Go ahead — type your message below …", "Thanks — we've logged your message.", and on the report receipt "Thanks — the report is with our safety team." Every one is a sentence the reader has to get past to reach the fact, and "Thanks" for filing a report about someone who frightened them is the wrong register.
- Evidence:
  ```kotlin
  SupportChat.kt:152  "No problem. Tap “$TYPE_IT_OUT” and tell us what's going on — …"
  ThreadDetailsSheet.kt:615  ReportOutcome.Sent -> "Thanks — the report is with our safety team."
  ```
- Fix: lead with the fact — "The report is with our safety team.", "Type it below and we'll get it to the team.", "To report someone, open the conversation's Details and tap Report."

### F-MS-16 — Centred text stacks under left-aligned forms
- Screens: 73, 08, 88
- Where: `feature/messages/ReportConversationSheet.kt:130,143,155`; `feature/messages/ChatQuoteCard.kt:232`; `feature/messages/ThreadDetailsSheet.kt:675`
- Category: spacing / consistency
- Severity: P3
- Rule: (b) everything above these lines on the same sheet — the question, the reason rows, the field, the CTA — is left-aligned to the 20 gutter; the footers are not.
- What: the report sheet ends in three stacked centred lines ("Goes to our safety team…", "Read our trust & safety guide", "Back"), the counter sheet ends in a centred caption, and the report-failure branch ends in a centred "Discard this report". Two of these are tappable and two are not, all five look the same, and the ragged centred block under a left-aligned form reads as a different screen's footer.
- Evidence:
  ```kotlin
  // ReportConversationSheet.kt:139-145
  Text("Read our trust & safety guide",
       style = AppTheme.type.footnote.copy(fontWeight = FontWeight.SemiBold),
       color = colors.accentInk, textAlign = TextAlign.Center, …)
  ```
- Fix: left-align the footers to the gutter, or make the two links `ListRow`s so a tappable line is visibly a row.

### F-MS-17 — Three silent character caps, no counter anywhere
- Screens: 08, 73
- Where: `feature/messages/MessageComposer.kt:198` (`MAX_MESSAGE_CHARS = 4_000`); `feature/messages/ReportConversationSheet.kt:234` (`NOTE_MAX_CHARS = 1_000`); `feature/messages/ChatQuoteCard.kt:248` (`AMOUNT_DIGITS = 8`)
- Category: slop
- Severity: P3
- Rule: (c) fact — all three cap on the way in (`text.take(max)`) and none renders a count or a limit; the composer's own doc comment argues the reader "should watch it stop at the limit", but nothing on screen says a limit exists.
- What: paste a long message and the field simply stops accepting keystrokes with no explanation. That is better than truncating on send, but the promised feedback was never drawn.
- Evidence:
  ```kotlin
  // MessageComposer.kt:63-64
  fun typed(text: String, maxChars: Int = MAX_MESSAGE_CHARS): ComposerState =
      copy(draft = text.take(maxChars))
  ```
- Fix: show "3,940/4,000" in `caption`/`ink4` once the draft passes ~90% of the cap, on the composer and the report note alike.

## Section-wide observations
- **Six loading states, four shapes.** One skeleton (`MessagesScreen.kt:625`), three bare
  centred spinners (F-CC-09), and the accept flow's `SendingNarration`. The design's
  "narrated, not a spinner" is honoured on the two screens that need it least.
- **Nothing in this section calls `ListRow` except the details sheet and the archive's
  footer.** Four hand-rolled row anatomies (inbox, archive, blocked, support options) and
  three hand-rolled pills sit beside `ListRow`, `Pill`, `StatusPill` and `Chip`.
- **`.copy(fontWeight = …)` appears 24 times** across eight files in the section — every one
  of them re-cutting `rowTitle`, `footnote`, `caption` or `body` at the call site.
- **Five distinct retry affordances**: `Banner` actionLabel (inbox, archive, report failure),
  `EmptyState` actionLabel (inbox, blocked), a bare accent-ink word (chat), a bare accent-ink
  word with a "Retrying…" swap (blocked stale), and "Try again" (report failure) vs "Retry"
  everywhere else.
- **The brand mark is drawn twice, differently**: `avatarMd` (48) + `monoNumber` on the inbox
  Support row (`MessagesScreen.kt:296-303`) and `avatarSm` (32) + `monoPill` on the support
  bot (`SupportChat.kt:365-377`). Same "A", two sizes and two type steps.
- **Swipe-to-archive draws two identical unlabelled `Filled.Archive` glyphs** on a `surface2`
  ground, one at each end (`MessagesScreen.kt:401-402`) — the gesture's only feedback is a
  grey icon that never says the word "Archive", on a section that elsewhere argues (correctly,
  `ArchivedScreen.kt:224-228`) that an unlabelled glyph is the wrong affordance here.
- **Two accent rims are alpha-derived** — `colors.accent.copy(alpha = 0.65f)`
  (`ChatQuoteCard.kt:75`, const at `:248`) and `colors.danger.copy(alpha = 0.4f)` (`ChatScreen.kt:741`). The
  second cites the design's own `rgba(164,64,44,.4)` so it is grounded; the first is a taste
  judgement with no token behind it, and the palette already ships `dangerLine`/`hairline`
  for strokes.
- **Accent budget on an actionable chat**: every outgoing bubble is an accent fill, the quote
  card carries an accent rim plus an accent-ink eyebrow, and the Accept CTA is accent — so
  the one decision on the screen is the same colour as the reader's own chatter. The funnel
  CTA is correctly mutually exclusive with the card (`ChatScreen.kt:965`), so the count is
  contained, but the Accept button has no colour left to win with.
