<!-- Appendix of docs/DESIGN_QA_2026-09.md — raw section report, verbatim from the section auditor; line numbers as of main@02ebeb2. -->

# CC — Cross-cutting (design system + shell), verified by the lead auditor

## Findings
### F-CC-01 — Opening a chat blacks out the whole app behind a spinner, then an M3 "OK" dialog
- Screens: 04, 18, 19, 26 (every client surface that opens a thread)
- Where: `navigation/ClientTabsScaffold.kt:787-811` (used at :563 and :677)
- Category: slow
- Severity: P1
- Rule: (a) §2 principles "narrated, not a spinner"; §5 rule 7 "never ship an M3 default"; Color.kt marks `glassScrim` as RETIRED for light surfaces; (b) `ArtistTabsScaffold.kt` opens the same threads with no overlay at all
- What: While a thread is being created/opened the client shell paints a full-screen 70% black scrim (`colors.glassScrim`, a token the palette file says survives only for controls floating on a PHOTO) with a lime `CircularProgressIndicator(color = colors.brand)` in the middle and swallows every tap. If it fails, the user gets a stock Material `AlertDialog` titled "Couldn't open the chat" with a `TextButton("OK")` — the only M3-default dialog chrome and the only "OK" button label in the app. The artist shell has neither, so the two roles feel like two apps at the exact same moment.
- Evidence:
  ```kotlin
  Box(Modifier.fillMaxSize().background(colors.glassScrim).clickable(...) { /* swallow */ }) {
      CircularProgressIndicator(color = colors.brand)
  }
  error?.let { message -> AlertDialog(shape = RoundedCornerShape(AppTheme.dimens.radii.xxl), ...,
      confirmButton = { TextButton(onClick = onDismissError) { Text("OK") } }) }
  ```
- Fix: Disable the tapped control and narrate inline ("Opening the conversation…") without a scrim; on failure raise the app's own `Banner`/toast with a retry verb ("Try again"), and mirror whatever the artist shell does so both roles match.

### F-CC-02 — One concept, up to four glyphs: Filled and Outlined Material icons are mixed across the app
- Screens: 02, 04, 08, 18, 19, 20, 33, 63, 77, 123, 131, 137, 138 and every tab root (bar glyphs)
- Where: chat — `designsystem/component/LightTabBar.kt:302` + `navigation/ClientTabsScaffold.kt:109` + `navigation/ArtistTabsScaffold.kt:89` (`Filled.ChatBubbleOutline`), `feature/messages/MessagesScreen.kt:191,203,214` (`Outlined.ChatBubbleOutline`), `designsystem/component/DetailHeader.kt:109` + `feature/booking/BookingDetailScreen.kt:353` (`AutoMirrored.Outlined.Message`), `feature/artist/ArtistProfileScreen.kt:1337` + `feature/epk/EpkHub.kt:533` + `feature/system/WhatsNewSheet.kt:207` (`AutoMirrored.Filled.Chat`); star — `feature/artist/ArtistProfileScreen.kt:651,1270`, `feature/booking/ReviewSheet.kt:369`, `feature/messages/MessagesScreen.kt:482`, `feature/search/SearchScreen.kt:715` (`Filled.Star`) vs `feature/system/ActivityScreen.kt:433`, `feature/system/RatePromptSheet.kt:157` (`Outlined.StarBorder`); shield — `feature/messages/SafetyCentreScreen.kt:87` (Filled) vs `feature/system/ActivityScreen.kt:436`, `feature/system/WhatsNewSheet.kt:208` (Outlined); bell — `designsystem/component/Headers.kt:164`, `designsystem/component/IconCircle.kt:129` (Filled) vs `feature/discover/DiscoverScreen.kt:126`, `feature/system/ActivityScreen.kt:183` (Outlined); flag — `feature/artist/ArtistProfileScreen.kt:1382`, `navigation/ArtistantNavHost.kt:205` (Filled) vs `feature/messages/ThreadDetailsSheet.kt:393` (Outlined); person — tab bar `Filled.PersonOutline` vs `feature/booking/ReviewSheet.kt:219` `Outlined.Person`
- Category: consistency
- Severity: P1
- Rule: (b) internal inconsistency — 225 `Icons.Filled` + 29 `Icons.Outlined` + 52 `AutoMirrored` call sites, 15 files mixing families; (a) the light design is a hairline system, and Material's Filled set is the heavy one
- What: The same idea is drawn with different glyph weights and even different glyphs depending on which screen you are on: the Messages tab glyph is a filled-family outline bubble, the inbox's empty state an outlined-family bubble, a booking's "Message" action a mirrored outlined envelope, and the profile's "Message" a filled solid bubble. Stars are solid on the profile and hollow in Activity; the bell is solid in the header component and hollow on Discover. A user reads this as several apps stitched together.
- Evidence:
  ```kotlin
  // LightTabBar.kt:302-303          // MessagesScreen.kt:191            // ArtistProfileScreen.kt:1337
  Icons.Filled.ChatBubbleOutline     Icons.Outlined.ChatBubbleOutline   Icons.AutoMirrored.Filled.Chat
  ```
- Fix: Pick one family (Outlined matches the hairline design) and one glyph per concept in a single `AppIcons` object (chat, star, shield, bell, flag, person, calendar, place…), and reference it everywhere; forbid direct `Icons.Filled/Outlined` imports in feature code.

### F-CC-03 — The token sheet and the secondary button disagree about its height
- Screens: 57, 07, 94, 118, 25 and every `EmptyState` with two actions
- Where: `designsystem/component/PrimaryButton.kt:86` vs `:142` (KDoc at `:111-119`); `docs/REDESIGN_2026-09.md` §2 Geometry
- Category: token
- Severity: P3
- Rule: (a) §2 Geometry says "secondary button: same height, surface2" (the CTA is 54); the component's own KDoc says "50 against 54 — because the design draws it that way"
- What: `PrimaryButton` is 54 (`component.cta`), `SecondaryButton` 50 (`component.control`). One of the two documents is wrong, and the disagreement cannot be settled from the repo. Wherever the pair stacks, the second button is 4dp shorter at the same radius and label size — intentional per the KDoc, contradicted by the sheet the section agents were told to build from.
- Evidence:
  ```kotlin
  .defaultMinSize(minHeight = dimens.component.cta)      // PrimaryButton, :86
  .defaultMinSize(minHeight = dimens.component.control)  // SecondaryButton, :142
  ```
- Fix: Measure screen 57 in the export once and make §2 and the KDoc say the same number.

### F-CC-04 — Every skeleton block recomposes on every frame while loading
- Screens: 59, 54 and every screen that draws `SkeletonBlock`/`SkeletonPage` (48 call sites)
- Where: `designsystem/component/Skeleton.kt:55`, `:68`, `:205-207`
- Category: slow
- Severity: P2
- Rule: (c) Compose fact; the repo's own `PressFeedback.kt` documents the correct pattern ("reads scale during the draw phase, so a press re-draws the node and nothing else")
- What: `skeletonPulse()` returns an animated `Float` read in composition, then passed to `Modifier.alpha(...)`. Every animation frame therefore invalidates the composition of every block, and each block owns its own `rememberInfiniteTransition`. A loading page with eight blocks recomposes eight nodes ~60×/s at exactly the moment the app is also parsing the network response — the design's "skeleton, not spinner" choice is paid for twice.
- Evidence:
  ```kotlin
  Box(modifier.alpha(skeletonPulse()).clip(...).background(colors.placeholder))   // :55
  private fun skeletonPulse(): Float { val transition = rememberInfiniteTransition(label = "skeleton") … }  // :205
  ```
- Fix: Hoist one transition per page (`SkeletonPage`) and apply it via `graphicsLayer { alpha = pulse.value }` so only the draw phase runs per frame.

### F-CC-05 — Engineering vocabulary in user-facing copy
- Screens: 44 (samples), 72 (save & exit), 06, 62, 124, 49/81/82, 10/122, 18, 50/99, 29
- Where: `feature/wizard/WizardMediaSteps.kt:115` "Staged uploads are cached on disk, so quitting the app won't lose them."; `feature/wizard/WizardScreen.kt:479` "staged media is cached on disk, not held in memory."; `feature/booking/CheckoutScreen.kt:109` "Nothing is charged — v1 takes no payment"; `feature/signup/PrivacyScreen.kt:201` "This switch is saved on this device. Artistant has no server-side privacy …"; `feature/profile/NotificationSettingsScreen.kt:252,255` "no server-side notification setting, so they don't follow you to another …", "are waiting on the server, not on you."; `feature/profile/DataExportScreen.kt:457,476` "Sent to the server", "record on this device or on the server."; `feature/bookings/BookingsLogic.kt:199-201` "Cached 9:04 am"; `feature/bookings/BookingsScreen.kt:108,148,678` "Showing your last sync", "Nothing is cached on this device yet, so there is nothing to show …", "$venue — cached, tap to copy"; `feature/score/ScoreExplainerScreen.kt:417` and `ScoreBreakdownSheet.kt:216` "the server's own number"; `feature/signup/ProfileScreen.kt:299` "Checked live against the server."; `feature/profile/ProfileViewModel.kt:635`, `AccountChrome.kt:194` "we couldn't reach the server"; `feature/artisthome/ArtistHomeViewModel.kt:323` and `feature/discover/DiscoverViewModel.kt:393` "Something went wrong"
- Category: copy
- Severity: P2
- Rule: (a) §2 principles "copy states the fact"; RUBRIC §4 (backend honesty is a house rule, but the user reads the fact, not the architecture)
- What: The redesign's honesty principle has leaked as implementation detail: "cached on disk", "not held in memory", "v1", "server-side", "sync", "the server's own number". A performer or host does not know what a server-side setting is; what they need is "Saved on this phone only" or "Not charged — Artistant takes no payment yet". Two screens also fall back to the generic "Something went wrong", which the same principle forbids.
- Evidence:
  ```kotlin
  "Staged uploads are cached on disk, so quitting the app won't lose them. "   // WizardMediaSteps.kt:115
  "Nothing is charged — v1 takes no payment"                                     // CheckoutScreen.kt:109
  "This switch is saved on this device. Artistant has no server-side privacy "   // PrivacyScreen.kt:201
  ```
- Fix: A copy pass on the 20 strings above: say the user-visible fact ("Kept on this phone until you sign out", "Your uploads survive closing the app", "Nothing is charged"), and replace "Something went wrong" with what failed and a retry.

### F-CC-06 — Toast is drawn on the wrong black and casts a shadow on a flat design
- Screens: 77 and every toast
- Where: `designsystem/component/Toast.kt:133-135`
- Category: token
- Severity: P3
- Rule: (a) Color.kt: `dark` "Dark surface — toast, dark card"; §2 "no card chrome"
- What: The capsule paints `colors.ink` (#14150F, the TEXT black) instead of the surface token `dark` (#16171A) the palette reserves for the toast, and adds a 12dp `shadow(space.md)` — the only drop shadow in the product. Next to the splash (`darkest`) and quote cards (`dark`) that makes three near-blacks on dark objects.
- Evidence:
  ```kotlin
  .shadow(dimens.space.md, RoundedCornerShape(dimens.radii.buttonLg))
  .clip(RoundedCornerShape(dimens.radii.buttonLg))
  .background(colors.ink)
  ```
- Fix: `background(colors.dark)`, drop the shadow (a hairline in `lineStrong` if separation is needed).

### F-CC-07 — Manage availability hand-rolls a bare Material back button and a spinner page
- Screens: 22, 105, 106
- Where: `feature/availability/ManageAvailabilityScreen.kt:80-89`
- Category: consistency
- Severity: P2
- Rule: (b) 22 other pushed screens use `BackHeader` (centred 17/700 title + 42 `IconCircle`); (a) "narrated, not a spinner"
- What: The screen's header is a naked M3 `IconButton` with `Icons.AutoMirrored.Filled.ArrowBack` and no title, in a Row padded `space.sm` (8dp) rather than the 20dp gutter; the loading state is a centred `CircularProgressIndicator`. It is the only pushed screen in the artist studio that does not look like the others.
- Evidence:
  ```kotlin
  IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.ink) }
  …
  state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = colors.accentInk) }
  ```
- Fix: `BackHeader(title = "Availability")` and the calendar's skeleton (a 7-column grid of `SkeletonBlock`s) for loading.

### F-CC-08 — Three stock Material dialogs where the design has none
- Screens: 47 (sign out), 35 (decline), client shell (chat failure)
- Where: `feature/profile/AccountScreen.kt:179-200`; `feature/gigs/GigRequestDetailScreen.kt:271-292`; `navigation/ClientTabsScaffold.kt:804-810`
- Category: consistency
- Severity: P2
- Rule: (a) §5 rule 7; (b) every other confirmation in the app is a sheet (`SheetScaffold`, 10 files) or a staged screen (cancel 117→52, delete 48→115→116)
- What: Sign out, Decline a request and the chat failure use `AlertDialog` + `TextButton` pairs — Material's centred dialog with text-only actions in `primary` colour, the one control the token sheet never styles. Decline is irreversible and the dialog is the lightest confirmation in the app; Cancel booking, which is comparable, gets two full screens.
- Evidence:
  ```kotlin
  AlertDialog( … confirmButton = { TextButton(onClick = viewModel::signOut) { … } }, dismissButton = { TextButton(onClick = viewModel::dismissSignOutConfirm) { … } })
  ```
- Fix: One `ConfirmSheet` on `SheetScaffold` (title, one line, primary + secondary buttons; danger variant for Decline / Sign out) used by all three.

### F-CC-09 — Eighteen screens load behind a bare centred spinner
- Screens: 05, 06, 22, 23, 35, 36, 60, 08, 26, 49, 25, 03, 29, 12, 72, 37, 127
- Where:
  - `feature/gigs/GigRequestDetailScreen.kt:115`
  - `feature/gigs/ArtistGigsScreen.kt:124`
  - `feature/wizard/WizardScreen.kt:171`
  - `feature/wizard/WizardFormSteps.kt:160`
  - `feature/profile/BlockedAccountsScreen.kt:99`
  - `feature/profile/ProfileScreen.kt:299`
  - `feature/profile/DataExportScreen.kt:338`
  - `feature/paywall/PaywallScreen.kt:319`
  - `feature/paywall/PaywallScreen.kt:370`
  - `feature/booking/CheckoutScreen.kt:292`
  - `feature/booking/BookingScreen.kt:93`
  - `feature/search/SearchScreen.kt:630`
  - `feature/signup/ProfileScreen.kt:386`
  - `feature/signup/SignupAuthScreen.kt:271`
  - `feature/epk/EpkScreen.kt:223`
  - `feature/messages/ChatScreen.kt:191`
  - `feature/messages/ArchivedScreen.kt:131`
  - `feature/availability/ManageAvailabilityScreen.kt:88`
  - `navigation/ClientTabsScaffold.kt:800`
- Category: consistency
- Severity: P2
- Rule: (a) §2 principles "loading, empty and failed are three different screens and say which one they are", "narrated, not a spinner"; (b) Discover (59) and the artist profile (54) draw geometry-matched skeletons
- What: Outside Discover and the profile, the loading state is Material's indeterminate ring centred on an empty page — no header, no shape of what is coming, no words. The same app therefore has two loading languages, and the more common one is the one the design notes reject.
- Fix: A `Skeleton` variant per layout family (list, form, detail) and the screen's real header above it; keep the ring only inline inside a control that is working (send, check handle).

### F-CC-10 — Compatibility aliases still live on redesigned screens
- Screens: PK (23/87/76 + sheets), BC (05/06/07/17/61/132), AS 22, WZ, AC 25
- Where: colour aliases — `feature/epk` 18, `feature/booking` 12, `feature/availability` 6, `feature/wizard` 4, `feature/profile` 2, `feature/messages` 2, `feature/paywall` 1, `navigation` 1, `ui` 2, `designsystem/component` 38 (`brand` ×25, `brandSoft` ×12, `brandInk` ×9, `hot` ×8, `lineSoft` ×7, `line` ×7, `good` ×5, `bg` ×5, `bgSoft` ×4, `bgCard` ×3, `bgElev` ×1); type aliases — `feature/epk` 23, `feature/messages` 7, `feature/booking` 7, `feature/availability` 4, `feature/gigs` 3, `feature/artisthome` 3, `feature/profile` 3, `feature/artist` 2 (`footnote` ×48, `callout` ×9, `headline` ×3, `monoStat` ×2, `monoMicro` ×2, `title`, `tabLabel`, `ctaLabel`)
- Category: token
- Severity: P3
- Rule: (a) Color.kt / Type.kt: "They exist so call sites keep compiling through P1; the eleven section PRs replace them … Do not add a new call site"
- What: 82 colour-alias and 67 type-alias call sites remain, concentrated in the press kit and the funnel. `footnote` in particular aliases `chip` (13.5 Medium), so 48 pieces of helper/meta text are set in the chip label's weight rather than `subtitle`/`caption`. Aliases are how the next drift happens: nobody can grep a screen for its real steps.
- Fix: Mechanical rename per file to the §2 names; delete the alias block once the count is zero.

### F-CC-11 — Dead and duplicate components still compile into the app
- Screens: —
- Where: zero callers outside their own file — `designsystem/component/ScoreRing.kt` (raw 64/6dp, 1.2sp), `Sparkline.kt` (raw 48/3dp), `MiniBars.kt`, `CardView.kt` (`border(1.dp, colors.line)`), `ScreenTitleBar.kt`, `StatusTimeline.kt` (six raw dp literals, `colors.brand`), `ArtistTile.kt` (raw 192/252dp, `Color.White` ×3, a twin of `Tile.kt`), `DateScroller.kt`; plus `ui/auth/AuthScreen.kt` (153 lines, M3 `TextButton`), and the section agents' lists: `feature/signup/EditorialHeadline.kt`, `SignupBackButton`, `SignupInputRow` (F-GS-23); `feature/booking/BookingChrome.kt` `FunnelHeader`, `FunnelCta`, `PackageOptionRow`, `PopularBadge`, `SectionLabel`, `AvailabilityLegend`, `CircleIconButton` (F-BC report)
- Category: consistency
- Severity: P3
- Rule: (a) CLAUDE.md rule 5 (no speculative code) and §5 rule 3 (tokens only); (b) two components for one job drift — the live twins already disagree (ArtistTile hard-codes its size and white text; StatusTimeline hard-codes 22/14/18/2/8dp)
- What: Nine design-system files, a whole pre-redesign auth screen and a dozen funnel/signup helpers have no caller. They are where the remaining raw dp literals and 13 of the funnel file's 16 compat aliases live, so every "tokens only" sweep keeps tripping over code no screen draws. `PARITY_CHECKLIST.md` still lists `ScoreRing` as a shipped primitive. (`BottomActionBar` is live — six call sites in BookingDetail and the two gates — and is not on this list.)
- Fix: Delete them and fix the checklist rows.

### F-CC-12 — Every cover photo pops in; nothing fades
- Screens: 02, 03, 04, 10, 18, 23, 32, 41, 45
- Where: the fifteen `AsyncImage(` sites in `feature/discover/DiscoverScreen.kt`, `feature/search/SearchScreen.kt`, `feature/artist/ArtistProfileScreen.kt`, `feature/artist/ArtistProfileMedia.kt`, `feature/bookings/BookingsScreen.kt` (×2), `feature/booking/BookingDetailScreen.kt`, `feature/booking/BookingChrome.kt`, `feature/epk/EpkHub.kt` (×2), `feature/epk/EpkPanes.kt`, `feature/profile/ArtistListScreen.kt`, `feature/wizard/WizardMediaSteps.kt`, `feature/wizard/WizardPublishSteps.kt` — none passes an `ImageRequest` with `crossfade`, and no shared `ImageLoader` sets it (the only `crossfade` matches in the tree are Compose `Crossfade` transitions in `feature/signup/SignupFlow.kt`)
- Category: slow
- Severity: P3
- Rule: (c) Coil defaults to an instant swap; the repo's own `RevealOnAppear.kt` argues that "a full screen of text and photos appearing between two frames reads as a glitch"
- What: Every hero, tile and thumbnail hard-cuts from the `placeholder` grey to the photo. On a rail of five tiles arriving at different times that is five separate flashes; on the profile hero it is the largest object on the screen changing between two frames.
- Fix: One app-level `ImageLoader` (Hilt-provided) with `crossfade(AppTheme.motion.contentReveal)`, or a thin `AppImage` wrapper that every site uses.

### F-CC-13 — Pull-to-refresh exists on some stale-able lists and not their twins
- Screens: 02, 19, 09, 36, 23 have it; 10, 03, 32, 123, 60 do not
- Where: present — `feature/discover/DiscoverScreen.kt`, `feature/messages/MessagesScreen.kt`, `feature/artisthome/ArtistHomeScreen.kt`, `feature/gigs/ArtistGigsScreen.kt`, `feature/epk/EpkScreen.kt`; absent — `feature/bookings/BookingsScreen.kt`, `feature/search/SearchScreen.kt`, `feature/profile/ArtistListScreen.kt`, `feature/system/ActivityScreen.kt`, `feature/messages/ArchivedScreen.kt`
- Category: consistency
- Severity: P3
- Rule: (b) the client's Bookings tab is the one list whose rows change without the user (accept/decline) and it is the one tab root without the gesture the neighbouring tabs have
- Fix: Add the gesture to Bookings, Activity and Archived; leave Search/ArtistList (query-driven) alone and say so in a comment.

### F-CC-14 — Empty-state body copy is set in the caption colour
- Screens: 57, 89, 110, 111, 84 and every `EmptyState`
- Where: `designsystem/component/EmptyState.kt:89-90`
- Category: hierarchy
- Severity: P3
- Rule: (a) §2 type table — body 15/400 on light is `ink3`; `ink4` is "captions, meta"
- What: The one sentence an empty state has to say is drawn in the meta grey, a step lighter than body copy, under a 21sp title — the title shouts and the explanation whispers.
- Fix: `color = colors.ink3`.

### F-CC-15 — Pushed screens animate twice: a 300 ms reveal stacked on the 300 ms push
- Screens: 04, 05, 06, 16/50, 18/95/83/96/97, 22, 35, 46, 127 (pushed) and 10, 23, 36 (tab roots)
- Where: `feature/artist/ArtistProfileScreen.kt:369`, `feature/booking/BookingScreen.kt:126`, `feature/booking/CheckoutScreen.kt:125`, `feature/booking/BookingDetailScreen.kt:211`, `feature/score/ScoreExplainerScreen.kt:205`, `feature/gigs/GigRequestDetailScreen.kt:181`, `feature/availability/ManageAvailabilityScreen.kt:95`, `feature/wizard/WizardPublishSteps.kt:285`, `feature/profile/BlockedAccountsScreen.kt:125`, `feature/bookings/BookingsScreen.kt:209`, `feature/epk/EpkScreen.kt:243`, `feature/gigs/ArtistGigsScreen.kt:148`; component `designsystem/component/RevealOnAppear.kt`
- Category: slow
- Severity: P2
- Rule: (c) `Motion.stackPush` = 300 ms slide+fade (`theme/Motion.kt`), `Motion.contentReveal` = 300 ms fade+lift; both run on the same destination
- What: `RevealOnAppear` wraps the loaded branch of twelve screens. On a pushed destination whose data is already cached (the profile from a tile, a booking from the list) the screen slides in over 300 ms and its content then fades and lifts in over another 300 ms, so the page is not still for ~600 ms after the tap; the same content on a tab root (Bookings, Gigs, Press kit) re-runs the reveal on every tab switch that re-composes it. The component's own KDoc argues for softening a spinner→content cut, which is the one case where the push has already finished.
- Evidence:
  ```kotlin
  // RevealOnAppear.kt — fadeIn(tween(duration)) + slideInVertically(tween(duration)) on first composition
  // ArtistProfileScreen.kt:369, BookingScreen.kt:126, BookingDetailScreen.kt:211 … wrap the whole loaded body
  ```
- Fix: Reveal only when the screen transitions from Loading to Loaded AFTER the nav transition has finished (gate on `!transition.isRunning` or skip when data was available at first composition); never on tab roots.
