# Artistant Android — designer's-eye QA of the 138 "iOS Light" screens (Sep 5, 2026)

**Scope.** Every screen of the Sep-2026 light redesign (11 sections, 138 design numbers,
including the four the checklist marks blocked) plus the design-system layer and the
global shell they inherit. Reviewed for visual inconsistency, spacing, hierarchy, copy
tone / AI-slop patterns, and slow or laggy interactions.

**Method.** The design export (PNG/HTML) lives outside the repo and is not in this
environment, so every finding is grounded in one of three verifiable things and says
which: (a) a rule in `docs/REDESIGN_2026-09.md` §2 or a token in `designsystem/theme/`;
(b) an internal inconsistency between peer screens; (c) an objective Compose / interaction
fact. Per-section auditors read every screen file in full (every `when (state)` branch);
the lead re-verified every P1/P2 line before it was filed. Line numbers are as of
`main` at 02ebeb2.

**What was NOT done, and why.** The screens were not walked on a device or emulator.
This sandbox's egress policy denies `dl.google.com` (HTTP 403 on CONNECT), which hosts
both the Android SDK repository (platform, emulator, system images) and Gradle's
`google()` Maven repository (AGP, Compose). Docker Hub image blobs redirect to a CDN the
policy also denies, and the VM exposes no `/dev/kvm`. So no build, no emulator, and no
JVM screenshot render (Roborazzi / Compose Preview screenshot testing) is possible here.
To make a visual pass possible in a future session: allow `dl.google.com` in the
environment's network policy (Claude Code on the web → environment → network); the
emulator would still need `-no-accel -gpu swiftshader_indirect` on this KVM-less VM.
The fastest visual check today is the repo's own debug harness on the maintainer's AVD —
the walk list is in §"Device walk" below.


## Numbers

**270 findings** across 13 reports, consolidated into **21 themes** — one GitHub issue each (#162–#182) under the tracking epic [#161](https://github.com/cestercian/artistant-android-Jetpack/issues/161). P1: 30, P2: 142, P3: 98.

| Report | Findings | P1 | P2 | P3 |
|---|---|---|---|---|
| CC — Cross-cutting (lead) ([appendix](design-qa/CC.md)) | 15 | 2 | 6 | 7 |
| GS — Getting started ([appendix](design-qa/GS.md)) | 25 | 1 | 11 | 13 |
| DS — Discover & search ([appendix](design-qa/DS.md)) | 25 | 2 | 10 | 13 |
| AP — The artist profile ([appendix](design-qa/AP.md)) | 25 | 2 | 16 | 7 |
| BC — Book & confirm ([appendix](design-qa/BC.md)) | 21 | 2 | 11 | 8 |
| BN — The booking & the night ([appendix](design-qa/BN.md)) | 19 | 4 | 11 | 4 |
| MS — Messaging & safety ([appendix](design-qa/MS.md)) | 17 | 2 | 12 | 3 |
| WZ — Artist setup wizard ([appendix](design-qa/WZ.md)) | 16 | 1 | 10 | 5 |
| PK — Press kit & media ([appendix](design-qa/PK.md)) | 17 | 3 | 8 | 6 |
| AS — Artist studio ([appendix](design-qa/AS.md)) | 20 | 4 | 9 | 7 |
| AC — Account & settings ([appendix](design-qa/AC.md)) | 23 | 2 | 15 | 6 |
| SH — System & housekeeping ([appendix](design-qa/SH.md)) | 25 | 2 | 12 | 11 |
| DSYS — Design system ([appendix](design-qa/DSYS.md)) | 22 | 3 | 11 | 8 |

Severity: **P1** visibly breaks the design language or blocks/slows the user; **P2** inconsistent between peers or off-token; **P3** polish. Categories: token · spacing · hierarchy · consistency · slop · copy · slow.

## Read this first — the P1s

- **F-CC-01** — Opening a chat blacks out the whole app behind a spinner, then an M3 "OK" dialog — screens 04, 18, 19, 26 (every client surface that opens a thread) — `navigation/ClientTabsScaffold.kt:787-811 (used at :563 and :677)`
- **F-CC-02** — One concept, up to four glyphs: Filled and Outlined Material icons are mixed across the app — screens 02, 04, 08, 18, 19, 20, 33, 63, 77, 123, 131, 137, 138 and every tab root (bar glyphs) — `chat — designsystem/component/LightTabBar.kt:302 + navigation/ClientTabsScaffold.kt:109 + navigation/ArtistTabsScaffold.kt:89 (Filled.ChatBu…`
- **F-GS-01** — Legal viewer ships in a raw M3 sheet: default grabber, status-bar pad, back arrow — screens 31, 114 (opened from 118 and 12) — `feature/signup/WelcomeScreen.kt:211-220; feature/signup/SignupFlow.kt:205-214; feature/signup/SignupChrome.kt:86-90; feature/signup/LegalScr…`
- **F-DS-01** — Retired dark-palette gradient is the cover floor on every DS surface — screens 02, 03, 32 (and 14 suggestion thumbs) — `feature/discover/DiscoverScreen.kt:408-423; feature/search/SearchScreen.kt:862-877; feature/profile/ArtistListScreen.kt:388-409; palette des…`
- **F-DS-03** — Filter sliders keep M3's default thumb/track and a white thumb on a white sheet — screens 15, 104 — `feature/search/SearchFilterSheet.kt:360-375 (RangeSlider), :405-416 (Slider); sheet fill designsystem/component/SheetScaffold.kt:66`
- **F-AP-01** — Accent fills multiply on 04, 16, 99 and the Opportunities tab of 50 — screens 04, 16, 99, 50 — `feature/artist/ArtistProfileScreen.kt:644, :906-909, :1344; feature/score/BookabilityScreen.kt:293, :227-231; feature/score/ScoreBreakdownSh…`
- **F-AP-02** — RevealOnAppear stacks a 300 ms fade on the 300 ms push on 04 and 50 — screens 04, 50 — `feature/artist/ArtistProfileScreen.kt:369; feature/score/ScoreExplainerScreen.kt:205; designsystem/theme/Motion.kt:53, :66, :82; feature/art…`
- **F-BC-01** — 05 loads behind a bare centred spinner with no header — screens 05 — `feature/booking/BookingScreen.kt:86-95 (spinner :93)`
- **F-BC-02** — 06's submit is an opaque full-screen spinner takeover that also swallows Back — screens 06 — `feature/booking/CheckoutScreen.kt:198-201, :275-311 (spinner :292), :99 (BackHandler swallow)`
- **F-BN-01** — Not-found tells the user to pull-to-refresh a list that can't — screens 84, 10 — `feature/booking/BookingDetailScreen.kt:1023-1032; feature/bookings/BookingsScreen.kt:210-216`
- **F-BN-02** — Review sheet opens pre-rated five stars, "Great night" — screens 20, 98 — `feature/booking/ReviewSheet.kt:87-95 (rating: Int = 5); :254-269; :391-397`
- **F-BN-03** — Disputed dock leads with a dead accent CTA and demotes the live actions — screens 96 — `feature/booking/BookingDetailScreen.kt:921-960`
- **F-BN-04** — Month calendar has no loading or failed state; both render as "Nothing this month" — screens 78 — `feature/bookings/MonthCalendarScreen.kt:59-83, 159-170`
- **F-MS-01** — Money is set in the sans, three different ways, in one section — screens 19, 08, 33 — `feature/messages/ChatQuoteCard.kt:86; feature/messages/ThreadDetailsSheet.kt:449-450; feature/messages/MessagesScreen.kt:573-574`
- **F-MS-02** — `BannerTone.Promotion` (a solid lime block) is used for neutral information — screens 33, 127 — `feature/messages/ThreadDetailsSheet.kt:292-296; feature/profile/BlockedAccountsScreen.kt:133-138`
- **F-WZ-01** — Preview draws its own title twice, at two type steps — screens 45 — `feature/wizard/WizardScaffold.kt:66,79-89; feature/wizard/WizardScreen.kt:270-282`
- **F-PK-01** — Delete the press kit's own toast host; it fires under the tab bar — screens 23, 87, 76 — `feature/epk/EpkScreen.kt:267; peer navigation/ArtistantNavHost.kt:198-210; designsystem/component/Toast.kt:83`
- **F-PK-02** — Lime-on-white text: seven tap targets in the panes are unreadable — screens 23 (panes) — `feature/epk/EpkComponents.kt:85,146,183; feature/epk/EpkPanes.kt:403,875; correct peers EpkPanes.kt:920,1079`
- **F-PK-03** — Screen 66 shows one accent CTA per stalled card plus "Retry all" — screens 66 — `feature/epk/EpkSheets.kt:893 (per card); :831 ("Retry all")`
- **F-AS-01** — Screen 09 spends the one accent six ways at once — screens 09 — `feature/artisthome/ArtistHomeScreen.kt:593; :540, :545; :458 (→ designsystem/component/Banner.kt:108); :677; :799; :912`
- **F-AS-02** — Three different page-title treatments across five screens of one section — screens 09, 36, 22, 133, 35 — `ArtistHomeScreen.kt:132-151; feature/gigs/ArtistGigsScreen.kt:153-163; feature/availability/ManageAvailabilityScreen.kt:76-84, :106; feature…`
- **F-AS-03** — Money is mono in the heroes and three different sans steps in the rows — screens 09, 133, 36 — `ArtistHomeScreen.kt:601 vs :881, :935; EarningsScreen.kt:146 vs :303; ArtistGigsScreen.kt:304; GigRequestDetailScreen.kt:510`
- **F-AS-04** — The clash warning scrolls away while Accept stays pinned — screens 35 — `GigRequestDetailScreen.kt:351-353 inside the scroll column :194-227; dock at :229-265`
- **F-AC-01** — Screen 49 shows two full-width accent CTAs at once — screens 49 — `feature/profile/DataExportScreen.kt:421-426; feature/profile/DataExportScreen.kt:256-261`
- **F-AC-02** — Accent tick discs used as bullet points on four screens — screens 81, 49, 25, 93 — `feature/profile/DataExportScreen.kt:302-308; …:431-437; feature/paywall/PaywallScreen.kt:349; feature/paywall/PaywallScreen.kt:496`
- **F-SH-01** — Tab bar spends a permanent accent on every root, on top of the root's own — screens global shell (all tab roots), 123 by inheritance — `designsystem/component/LightTabBar.kt:236-265; navigation/ClientTabsScaffold.kt:230-234; navigation/ArtistTabsScaffold.kt:179-183; peers fea…`
- **F-SH-03** — Every toast raised from a pushed screen floats 88dp above nothing — screens 77, and every screen that raises a toast — `navigation/ArtistantNavHost.kt:198-210; designsystem/component/Toast.kt:82, :103; navigation/ClientTabsScaffold.kt:216-217; navigation/Artis…`
- **F-DSYS-01** — `caption` ships Medium +0.5sp for a reason that covers 1 of 237 sites — screens — — `designsystem/theme/Type.kt:181-194`
- **F-DSYS-05** — The tab bar's own arithmetic contradicts its KDoc by 24dp; it ships 15–39dp over §2's 88 — screens — — `designsystem/component/LightTabBar.kt:98-104 (the claim), :190 (the cell), :287-291 (the measurement); tokens at theme/Dimens.kt:406,408,412`
- **F-DSYS-06** — The three most-tapped small controls are 36, 36 and 20dp — screens — — `designsystem/component/Chip.kt:87-90; designsystem/component/SegmentedControl.kt:101; designsystem/component/SearchBar.kt:88-91`

## Themes

Each theme is one issue. Members are listed with the auditor's id; open the appendix for the evidence block and the full "what" and "fix".

### T01 · [P1] Opening a chat blacks out the client app behind a spinner, then a stock Material dialog

*Category:* slow · *Issue:* [#162](https://github.com/cestercian/artistant-android-Jetpack/issues/162)

**Why.** §2 principles: "narrated, not a spinner"; §5 rule 7: never ship an M3 default; `Color.kt` marks `glassScrim` as retired for light surfaces. The artist shell opens the same threads with no overlay at all.

**Fix direction.** Disable the tapped control and narrate inline ("Opening the conversation…") with no scrim; on failure raise the app's `Banner`/toast with a retry verb, and make both role shells behave identically.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-01 | P1 | 04, 18, 19, 26 (every client surface tha | `navigation/ClientTabsScaffold.kt:787-811 (used at :563 and :677)` | Opening a chat blacks out the whole app behind a spinner, then an M3 "OK" dialog |

### T02 · [P1] One accent per screen is violated on most screens: accent fills multiply

*Category:* hierarchy · *Issue:* [#163](https://github.com/cestercian/artistant-android-Jetpack/issues/163)

**Why.** §2: the accent is "the one signal … one per screen". Twenty-six findings count two to six accent-filled surfaces in one viewport — accent cards, selected chips, badges, tick discs, note washes, the tab bar's permanent action circle — and the same accent tinted by hand with alpha.

**Fix direction.** Per screen, name the one action the accent belongs to; everything else drops to `surface2`/`accentSoft`/`accentInk`. Retire the alpha-mixed lime tints for `accentSoft`, and decide what the tab bar's raised circle costs each root.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-GS-03 | P2 | 27, 11, 30, 13 | `feature/signup/CommunityCommitmentScreen.kt:119-131 (+ CTA :81-87); feature/signup/RoleScr…` | Two to five accent-filled surfaces compete on 27, 11, 30 and 13 |
| F-DS-10 | P2 | 03, 104 | `feature/search/SearchScreen.kt:348-358 (badge), :609-617 + :809-837 (accent chips); commen…` | Results spend accent on every active-filter chip AND the count badge |
| F-DS-24 | P3 | 02 | `feature/discover/DiscoverScreen.kt:125-134 (bell dot — designsystem/component/IconCircle.k…` | Discover's first screenful has four accent-filled objects, each documented as "the one accent" |
| F-AP-01 | P1 | 04, 16, 99, 50 | `feature/artist/ArtistProfileScreen.kt:644, :906-909, :1344; feature/score/BookabilityScree…` | Accent fills multiply on 04, 16, 99 and the Opportunities tab of 50 |
| F-AP-09 | P2 | 04, 51, 50 | `feature/artist/ArtistProfileScreen.kt:638-665, :906, :964; feature/score/ScoreHistoryScree…` | Hand-rolled pills tint the accent with alpha where `Pill` and `accentSoft` exist |
| F-BC-05 | P2 | 05, 94 | `05: BookingChrome.kt:945 (selected day), :996 / :1001 / :1027 (package wash, rim, tick dis…` | Four accent fills compete at once on 05 and on 94 |
| F-BC-06 | P2 | 05 | `feature/booking/BookingChrome.kt:996, :1072; peer designsystem/component/AccentNote.kt:129…` | Selected-package wash is a hand-mixed 26% lime, not `accentSoft` |
| F-BN-03 | P1 | 96 | `feature/booking/BookingDetailScreen.kt:921-960` | Disputed dock leads with a dead accent CTA and demotes the live actions |
| F-BN-14 | P2 | 89, 122, 18, 95, 83, 117, 52 | `feature/booking/BookingDetailScreen.kt:662, :704, :727, :1119, :1146; feature/bookings/Boo…` | Accent-tinted note used as a general callout, up to twice per page |
| F-MS-02 | P1 | 33, 127 | `feature/messages/ThreadDetailsSheet.kt:292-296; feature/profile/BlockedAccountsScreen.kt:1…` | `BannerTone.Promotion` (a solid lime block) is used for neutral information |
| F-MS-07 | P2 | 131 | `feature/messages/SafetyCentreScreen.kt:108,135-160 (esp. :150 .background(colors.accent));…` | Four accent-filled objects compete on the Safety centre; the numbered-rule row is duplicated |
| F-WZ-06 | P2 | 40, 46 | `WizardFormSteps.kt:625,679,691 ; WizardPublishSteps.kt:295-303,384,400` | Availability and Done spend the accent three ways at once |
| F-PK-03 | P1 | 66 | `feature/epk/EpkSheets.kt:893 (per card); :831 ("Retry all")` | Screen 66 shows one accent CTA per stalled card plus "Retry all" |
| F-PK-07 | P2 | 68 | `feature/epk/EpkSheets.kt:531-532 (fill + full-accent border); :496-505 (accent CTA); order…` | Every answered prompt is an accent card, not just the first |
| F-AS-01 | P1 | 09 | `feature/artisthome/ArtistHomeScreen.kt:593; :540, :545; :458 (→ designsystem/component/Ban…` | Screen 09 spends the one accent six ways at once |
| F-AS-15 | P3 | 09, 133 | `ArtistHomeScreen.kt:980, :983-984 used at :540, :545, :598, :624; EarningsScreen.kt:318 us…` | Five alpha-manufactured tints where the palette already names the colour |
| F-AC-01 | P1 | 49 | `feature/profile/DataExportScreen.kt:421-426; feature/profile/DataExportScreen.kt:256-261` | Screen 49 shows two full-width accent CTAs at once |
| F-AC-02 | P1 | 81, 49, 25, 93 | `feature/profile/DataExportScreen.kt:302-308; …:431-437; feature/paywall/PaywallScreen.kt:3…` | Accent tick discs used as bullet points on four screens |
| F-AC-15 | P2 | 128 | `feature/profile/DevicesScreen.kt:204-241; feature/profile/DevicesScreen.kt:136-142` | Devices spends its accent on a static fact and leaves the only action grey |
| F-AC-22 | P3 | 124 | `feature/profile/NotificationSettingsScreen.kt:156-160` | The screen's one accent spent on "nothing is wrong" |
| F-SH-01 | P1 | global shell (all tab roots), 123 by inh | `designsystem/component/LightTabBar.kt:236-265; navigation/ClientTabsScaffold.kt:230-234; n…` | Tab bar spends a permanent accent on every root, on top of the root's own |
| F-SH-02 | P2 | global shell (client) | `navigation/ClientTabsScaffold.kt:230-234; the tab it duplicates at :108 and :121-123` | The client's accent action circle goes where the tab beside it goes |
| F-SH-07 | P2 | 137 | `feature/system/WhatsNewSheet.kt:154-161, :140-145` | What's new stacks three accent tiles under an accent CTA |
| F-SH-12 | P2 | 123 | `feature/system/ActivityScreen.kt:359-365 vs :409-416` | Activity puts the accent on every unread row, not on the one it says |
| F-DSYS-04 | P2 | — | `designsystem/component/AccentNote.kt:61-62, :121-122, :129-132; designsystem/component/Ban…` | `AccentNote` and `Banner(BannerTone.Note)` draw the identical aside twice |
| F-DSYS-21 | P3 | — | `designsystem/component/SendingNarration.kt:79-92, :149-159, :164-169` | `SendingNarration` paints three accent-filled objects at once |

### T03 · [P1] Icon language: Filled and Outlined Material glyphs are mixed, one concept drawn up to four ways

*Category:* consistency · *Issue:* [#164](https://github.com/cestercian/artistant-android-Jetpack/issues/164)

**Why.** 225 `Icons.Filled`, 29 `Icons.Outlined` and 52 `AutoMirrored` call sites; fifteen files mix families; chat, star, shield, bell, flag and person each have two to four spellings, including inside one `when` in `Banner.kt`.

**Fix direction.** One family (Outlined suits the hairline design) and one glyph per concept in an `AppIcons` object; forbid direct `Icons.*` imports in feature code.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-02 | P1 | 02, 04, 08, 18, 19, 20, 33, 63, 77, 123, | `chat — designsystem/component/LightTabBar.kt:302 + navigation/ClientTabsScaffold.kt:109 + …` | One concept, up to four glyphs: Filled and Outlined Material icons are mixed across the app |
| F-GS-20 | P3 | 13, 30, 11, 28, 90 | `feature/signup/NotifPermissionScreen.kt:105-109, :146-150; feature/signup/DoneScreen.kt:95…` | Glyph wells differ in shape, fill token and glyph family across peers |
| F-AP-18 | P2 | 04 | `feature/artist/ArtistProfileScreen.kt:1337, :1378-1383; feature/artist/ArtistProfileMedia.…` | Icon families disagree with the rest of the app for save, chat and audio |
| F-BC-19 | P3 | 06, 17, 132 | `feature/booking/RequestQuoteScreen.kt:144 (time picker) vs CheckoutScreen.kt:222 ("What ha…` | Icon semantics: `Filled.Schedule` means two things, and the record shares with the iOS glyph |
| F-SH-14 | P2 | global shell (both roles) | `navigation/ClientTabsScaffold.kt:107-111; navigation/ArtistTabsScaffold.kt:86-91` | The tab bar mixes solid and hollow glyphs in the app's most-seen chrome |
| F-DSYS-14 | P2 | — | `designsystem/component/Banner.kt:127-131` | `Banner` mixes Outlined and Filled glyphs inside one `when` |
| F-WZ-15 | P3 | 24, 44 | `WizardFormSteps.kt:383,428 ; WizardMediaSteps.kt:801,814 ; WizardMediaSteps.kt:686 ; Wizar…` | Off-palette tints and stand-in glyphs: `.copy(alpha=…)`, "AUDIO CLIP", a full-width "＋" |

### T04 · [P1] Loading is a spinner or a takeover, and failed, empty and wrong-tab are drawn as the same block

*Category:* consistency · *Issue:* [#165](https://github.com/cestercian/artistant-android-Jetpack/issues/165)

**Why.** §2 principles: "loading, empty and failed are three different screens and say which one they are"; "every empty state carries an action". Eighteen screens load behind a bare ring, Checkout takes over the page with one, page 2 of Search spins where page 1 skeletons, and several sections draw failed and empty with one glyph circle and no action.

**Fix direction.** A `Skeleton` variant per layout family drawn under the screen's real header; `EmptyState` with an action for empty, `Banner(Failure)`+Retry for failed; delete the takeovers.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-09 | P2 | 05, 06, 22, 23, 35, 36, 60, 08, 26, 49,  | `- feature/gigs/GigRequestDetailScreen.kt:115 - feature/gigs/ArtistGigsScreen.kt:124 - feat…` | Eighteen screens load behind a bare centred spinner |
| F-CC-13 | P3 | 02, 19, 09, 36, 23 have it; 10, 03, 32,  | `present — feature/discover/DiscoverScreen.kt, feature/messages/MessagesScreen.kt, feature/…` | Pull-to-refresh exists on some stale-able lists and not their twins |
| F-GS-07 | P2 | 12 | `feature/signup/SignupAuthScreen.kt:270-275 (vs :203); peers feature/signup/EnterCodeScreen…` | Sign-in overlays an M3-default spinner on the provider rows while the CTA already narrates |
| F-DS-08 | P2 | 58 | `feature/search/SearchScreen.kt:177-185 (BannerTone.Attention), :186-194 (Icons.Filled.Refr…` | Search's failed state is drawn as a warning, Discover's and its own paging failure as a failure |
| F-DS-15 | P3 | 03 | `feature/search/SearchScreen.kt:627-633 vs :170 + :754-778` | Page 2 loads behind a spinner; page 1 behind a skeleton |
| F-DS-16 | P3 | 03, 32 | `feature/profile/ArtistListScreen.kt:336-369 (top = lg, spacedBy(lg)) vs :186-194 (top = md…` | Skeletons don't match the list they stand in (Discover states the rule; peers break it) |
| F-DS-19 | P3 | 02, 57, 58, 32, 112 | `feature/discover/DiscoverScreen.kt:161, :184 (Icons.Filled.FavoriteBorder for "Couldn't lo…` | Every empty/failed state wears a glyph circle; four of six wear the Saved heart |
| F-AP-19 | P3 | 101, 50, 80, 102, 51, 04 | `feature/artist/ArtistProfileScreen.kt:998-1037, :1211-1215; feature/score/ScoreExplainerSc…` | Empty and failed blocks that skip the component or the action; two Retry controls on 80 |
| F-AP-20 | P3 | 16, 50, 51, 54 | `feature/score/BookabilityScreen.kt:191-197; feature/score/ScoreExplainerScreen.kt:214-219;…` | Loading skeletons on 16, 50 and 51 are one block for a page of a card plus five meters |
| F-BC-01 | P1 | 05 | `feature/booking/BookingScreen.kt:86-95 (spinner :93)` | 05 loads behind a bare centred spinner with no header |
| F-BC-02 | P1 | 06 | `feature/booking/CheckoutScreen.kt:198-201, :275-311 (spinner :292), :99 (BackHandler swall…` | 06's submit is an opaque full-screen spinner takeover that also swallows Back |
| F-BN-04 | P1 | 78 | `feature/bookings/MonthCalendarScreen.kt:59-83, 159-170` | Month calendar has no loading or failed state; both render as "Nothing this month" |
| F-BN-16 | P2 | 10, 89, 84, 78 | `feature/bookings/BookingsScreen.kt:145-156, :245-259, :523-529; feature/booking/BookingDet…` | Empty, failed and "wrong tab" are the same block with the same glyph |
| F-MS-05 | P2 | 110, 111 | `feature/messages/MessagesScreen.kt:186-192; feature/messages/ArchivedScreen.kt:139-152` | The inbox's failed-empty is an `EmptyState`; its twin the archive draws a `Banner` |
| F-MS-06 | P2 | 110, 88, 127 | `feature/messages/MessagesScreen.kt:161-172 (Banner); feature/messages/ChatScreen.kt:936-96…` | Three "couldn't refresh" strips, three different components |
| F-MS-11 | P2 | 110, 88, 127 | `feature/messages/MessagesScreen.kt:207-215; feature/messages/ChatScreen.kt:201-205; featur…` | Three empty states in this section carry no action |
| F-AS-05 | P2 | 36, 133, 85 | `ArtistGigsScreen.kt:142-146; EarningsScreen.kt:171-175; ArtistHomeScreen.kt:328-353` | Three empty states in the section, none with an action |
| F-AS-06 | P2 | 09, 86, 133, 35, 36, 22 | `Banner + Retry at ArtistHomeScreen.kt:159-167, :369-376; EarningsScreen.kt:128-134; GigReq…` | The same "couldn't load it" fact is drawn four different ways |
| F-AS-09 | P2 | 86 | `ArtistHomeScreen.kt:370 (identical string at :160); feature/artisthome/ArtistStudioLogic.k…` | Screen 86 says "couldn't refresh" and "out of date" about a dashboard that never loaded |
| F-AS-10 | P2 | 86 | `ArtistHomeScreen.kt:368-377 and :426-433; header retry at :138-143` | Screen 86 stacks two banners and offers Retry twice |
| F-AC-10 | P2 | 92 | `feature/paywall/PaywallScreen.kt:417-458; footer at …:272-287` | Screen 92 states the outage twice, in two different shapes, and contradicts its own button |
| F-AC-11 | P2 | 81, 82, 49, 113 | `feature/profile/DataExportScreen.kt:279-284; :294; :326-330; :383; :450-455 and :461-466` | Data export's four states change the shape of the top of the page |
| F-SH-10 | P2 | 63 | `feature/system/HelpCentreScreen.kt:115-121` | Help centre's no-results state names an action and offers none |

### T05 · [P1] Headers: pushed screens fork BackHeader at 40 dp / 15 sp, or hand-roll their own bar

*Category:* consistency · *Issue:* [#166](https://github.com/cestercian/artistant-android-Jetpack/issues/166)

**Why.** §2: header 56 tall, centred 17/700 with a 42 back circle. Signup, the funnel, the artist profile, the wizard, availability, the paywall and feedback each draw their own; the design system itself ships three headers at three heights, and the back circle is 42 on 22 screens and 40 on 35 call sites.

**Fix direction.** One `BackHeader` (with a trailing slot and an optional middle slot) at 56/42/17 used by every pushed screen; `ScreenHeader` for tab roots; delete the forks.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-07 | P2 | 22, 105, 106 | `feature/availability/ManageAvailabilityScreen.kt:80-89` | Manage availability hand-rolls a bare Material back button and a spinner page |
| F-GS-02 | P2 | 27, 11, 71, 12, 119, 28, 29, 90, 31, 114 | `feature/signup/SignupChrome.kt:154-158, :172-178, :93; peer designsystem/component/Headers…` | Section hand-rolls its pushed header at 15/700 with a 40 circle; `BackHeader` is 17/700 + 42 |
| F-AP-05 | P2 | 04, 103 | `feature/artist/ArtistProfileScreen.kt:523-565; peers feature/artist/ArtistReviewsScreen.kt…` | Profile header is hand-rolled although BackHeader has the trailing slot meant for 04 |
| F-BC-03 | P2 | 05, 06, 17, 132 | `feature/booking/BookingChrome.kt:222-231 (KDoc claim), :258-263, :288, :315-320, :331; des…` | The funnel's pushed header is a 40dp fork of `BackHeader(centered = false)` |
| F-BC-13 | P2 | 61 | `feature/booking/CounterOfferScreen.kt:237-250 (scrim), :260-289 (header); navigation/Artis…` | 61 is a pushed route dressed as a sheet: a grey void behind it and a hand-rolled header with three ways out |
| F-DS-11 | P2 | 32 | `feature/profile/ArtistListScreen.kt:91-101; the other six BackHeader( call sites in featur…` | Artist list's BackHeader inset is `space.sm`; every other BackHeader uses the gutter |
| F-WZ-01 | P1 | 45 | `feature/wizard/WizardScaffold.kt:66,79-89; feature/wizard/WizardScreen.kt:270-282` | Preview draws its own title twice, at two type steps |
| F-AS-02 | P1 | 09, 36, 22, 133, 35 | `ArtistHomeScreen.kt:132-151; feature/gigs/ArtistGigsScreen.kt:153-163; feature/availabilit…` | Three different page-title treatments across five screens of one section |
| F-AS-11 | P2 | 86 | `ArtistHomeScreen.kt:398-405 vs :493-497` | Screen 86 hand-rolls the section header it uses a component for on 09/85 |
| F-AC-08 | P2 | 25, 91, 92, 93 | `feature/paywall/PaywallScreen.kt:160-170` | The paywall's four states carry no header or title |
| F-AC-16 | P2 | 47, 49, 81, 82, 113, 115, 48, 124, 128 | `feature/profile/AccountScreen.kt:241-248; feature/profile/DevicesScreen.kt:133; feature/pr…` | Centred nav titles carrying subtitles, against the component's own documented rule |
| F-SH-09 | P2 | 64 | `feature/system/FeedbackScreen.kt:108-135; peers ActivityScreen.kt:131-156 and HelpCentreSc…` | Send feedback hand-rolls a header and puts two dismiss controls in it |
| F-PK-15 | P3 | 23, 87, 76 | `feature/epk/EpkScreen.kt:451-456; designsystem/component/IconCircle.kt:50; designsystem/th…` | The header's account disc is 40, not the 42 every other header uses |
| F-BN-17 | P3 | 10, 122 | `feature/bookings/BookingsScreen.kt:109-125, :614-622` | The Bookings header's trailing control changes identity by state |
| F-DSYS-15 | P3 | — | `designsystem/component/Headers.kt:47-51 (no min height), :111, :63, :136; designsystem/com…` | Three headers, three bar heights, none of them §2's 56 — and two subtitle steps |
| F-DSYS-16 | P3 | — | `designsystem/theme/Dimens.kt:227 (iconCircle 42), :229 (iconCircleSm 40); designsystem/com…` | The header back circle is 42 on 22 screens and 40 on 35 call sites |

### T06 · [P1] Sheets bypass SheetScaffold: raw Material sheets, three title treatments, half-height pickers

*Category:* consistency · *Issue:* [#167](https://github.com/cestercian/artistant-android-Jetpack/issues/167)

**Why.** §5 rule 7. The legal viewer, the profile's three sheets, the quote pickers, Save & exit and thread details each build their own sheet head (default grabber, 28 dp close targets, centred text under left-aligned forms).

**Fix direction.** Every sheet goes through `SheetScaffold(title=)` with `skipPartiallyExpanded = true`; delete the private sheet heads.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-GS-01 | P1 | 31, 114 (opened from 118 and 12) | `feature/signup/WelcomeScreen.kt:211-220; feature/signup/SignupFlow.kt:205-214; feature/sig…` | Legal viewer ships in a raw M3 sheet: default grabber, status-bar pad, back arrow |
| F-AP-06 | P2 | 04 (overflow), 56, 99 | `feature/artist/ArtistProfileSheets.kt:81-89, :209-228; feature/score/ScoreBreakdownSheet.k…` | Three sheets, three title treatments; `SheetScaffold(title=)` unused by all of them |
| F-BC-12 | P2 | 17 | `feature/booking/RequestQuoteScreen.kt:214-220, :240-246 vs feature/booking/TechRiderSheet.…` | 17's picker sheets keep M3's default shape and open half-height; the sibling TechRider sheet does it right |
| F-WZ-09 | P2 | 72 | `WizardScreen.kt:422-441; peers feature/booking/RequestQuoteScreen.kt:221,247, feature/book…` | Save & exit hand-rolls a sheet header that `SheetScaffold` already provides |
| F-MS-13 | P2 | 33, 88 | `feature/messages/ThreadDetailsSheet.kt:145-146, 508-540 (esp. :526); peer feature/messages…` | The details sheet hand-rolls a title bar with a 28 dp close target |
| F-MS-16 | P3 | 73, 08, 88 | `feature/messages/ReportConversationSheet.kt:130,143,155; feature/messages/ChatQuoteCard.kt…` | Centred text stacks under left-aligned forms |

### T07 · [P1] Material defaults leak: dropdown, slider, refresh indicator and alert dialogs

*Category:* token · *Issue:* [#168](https://github.com/cestercian/artistant-android-Jetpack/issues/168)

**Why.** §5 rule 7: "never ship an M3 default colour". The city picker is a stock `DropdownMenu`, the filter sliders keep M3's thumb (white on a white sheet), pull-to-refresh uses M3's indicator, and three confirmations are `AlertDialog` + `TextButton`.

**Fix direction.** Restyle each with the tokens or replace with the app's own sheet/row; a `ConfirmSheet` for sign-out, decline and the chat failure.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-GS-08 | P2 | 29, 90 | `feature/signup/ProfileScreen.kt:253-279` | City picker is a default M3 `DropdownMenu` (shadow, 4 dp corners) on a no-chrome design |
| F-DS-03 | P1 | 15, 104 | `feature/search/SearchFilterSheet.kt:360-375 (RangeSlider), :405-416 (Slider); sheet fill d…` | Filter sliders keep M3's default thumb/track and a white thumb on a white sheet |
| F-DS-14 | P3 | 02 | `feature/discover/DiscoverScreen.kt:137-141; no indicator = or PullToRefreshDefaults anywhe…` | Discover's pull-to-refresh ships M3's default indicator; the gesture exists on one of three lists |
| F-CC-08 | P2 | 47 (sign out), 35 (decline), client shel | `feature/profile/AccountScreen.kt:179-200; feature/gigs/GigRequestDetailScreen.kt:271-292; …` | Three stock Material dialogs where the design has none |
| F-AC-05 | P2 | 47 | `feature/profile/AccountScreen.kt:178-203; feature/profile/AccountScreen.kt:379-384` | Sign out asks for a confirmation the dialog itself says is harmless |

### T08 · [P1] Money and scores are set in the sans at four to five sizes; mono is used for the wrong things

*Category:* token · *Issue:* [#169](https://github.com/cestercian/artistant-android-Jetpack/issues/169)

**Why.** §2: JetBrains Mono for "eyebrow labels and numerals"; money is all-in and in ₹. The from-price, the fee, the quote amount, the score numeral, the stat bands, the earnings rows and the character counters each pick a different sans step; the booking id gets the mono the fee should have.

**Fix direction.** One `Money` and one `ScoreNumeral` composable on the mono steps, Indian grouping, used everywhere a number is content.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-DS-05 | P2 | 02, 03, 32 | `designsystem/component/HeroCard.kt:207-211 (hero "₹42,000"); feature/discover/DiscoverHero…` | The price line is set four ways, none of them mono |
| F-AP-03 | P2 | 04, 16, 50, 99, 51 | `feature/artist/ArtistProfileScreen.kt:746-752; feature/score/BookabilityScreen.kt:301-305;…` | The score numeral is set five different ways across five screens |
| F-AP-04 | P2 | 04 | `feature/artist/ArtistProfileScreen.kt:746-752, :955-960, :1345-1349; peer feature/profile/…` | Stat strip and package prices in the sans; the account stat band uses mono |
| F-BC-14 | P3 | 05, 06, 61, 132 | `feature/booking/BookingScreen.kt:246-253 (subtitle.copy(SemiBold), 13.5); BookingChrome.kt…` | Money is the smallest text in 05's dock, and set in the sans at four sizes across the section |
| F-BN-07 | P2 | 18, 95, 83, 97 | `feature/booking/BookingDetailScreen.kt:1341-1353 (FeeRow); called at :663; :1322-1335 (Ter…` | The fee is set in bold sans; the booking id gets the mono |
| F-MS-01 | P1 | 19, 08, 33 | `feature/messages/ChatQuoteCard.kt:86; feature/messages/ThreadDetailsSheet.kt:449-450; feat…` | Money is set in the sans, three different ways, in one section |
| F-WZ-08 | P2 | 24 | `WizardFormSteps.kt:365-397; peer feature/booking/CheckoutScreen.kt:168-176` | Pricing hand-rolls Checkout's money row: lowercase label, raw divider, no mono on the fee |
| F-AS-03 | P1 | 09, 133, 36 | `ArtistHomeScreen.kt:601 vs :881, :935; EarningsScreen.kt:146 vs :303; ArtistGigsScreen.kt:…` | Money is mono in the heroes and three different sans steps in the rows |
| F-AS-18 | P3 | 133 | `EarningsScreen.kt:266; Type.kt:351` | The earnings axis borrows the calendar's weekday style and sits at body ink |
| F-PK-09 | P2 | 67, 68 | `feature/epk/EpkSheets.kt:360-364 (bio, 200-char cap) and :549-555 (prompt, 280-char cap); …` | Character counters are set in the sans, and disagree with each other |
| F-SH-13 | P2 | 64 | `feature/system/FeedbackScreen.kt:171-175 vs feature/epk/EpkSheets.kt:360-363 and :549-553` | Two character counters, two type ramps, two at-cap colours |
| F-MS-17 | P3 | 08, 73 | `feature/messages/MessageComposer.kt:198 (MAX_MESSAGE_CHARS = 4_000); feature/messages/Repo…` | Three silent character caps, no counter anywhere |
| F-DSYS-11 | P2 | — | `designsystem/component/MonthCalendar.kt:517-521 vs :420; designsystem/theme/Type.kt:346-34…` | The calendar sets its day numerals in the sans while `monoDay` exists for exactly that |

### T09 · [P1] Type ramp drift: 100+ call sites invent steps with .copy(), aliases still live, caption ships bold

*Category:* token · *Issue:* [#170](https://github.com/cestercian/artistant-android-Jetpack/issues/170)

**Why.** §2 defines fourteen steps. The app mints weight variants at ~100 sites, `caption` ships Medium +0.5 sp for one of 237 uses, `footnote` (48 sites) aliases the chip label, `rowTitle`/`callout`/`headline` overlap, and three display sizes exist that §2 never names.

**Fix direction.** Add the two or three real variants the app needs to `AppType`, retire `.copy(fontWeight=…)` and the compat aliases file by file, and re-cut `caption` to §2's 12.5/400.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-10 | P3 | PK (23/87/76 + sheets), BC (05/06/07/17/ | `colour aliases — feature/epk 18, feature/booking 12, feature/availability 6, feature/wizar…` | Compatibility aliases still live on redesigned screens |
| F-GS-19 | P3 | 01, 11, 13, 27, 29, 30, 90, 119, 12, 62, | `` | 22 invented type steps via `.copy(fontSize/fontWeight)`, incl. numerals in the sans and two error weights |
| F-DS-20 | P3 | 14, 03, 15, 104, 53, 32 | `feature/search/SearchScreen.kt:542 (rowTitle→700 occasion card), :827 (chip→700 in a hand-…` | Ten `.copy(fontWeight = …)` sites invent steps the ramp does not have |
| F-DS-06 | P2 | 02, 03, 14, 32 | `designsystem/component/Tile.kt:110 (rowTitle); feature/search/SearchScreen.kt:481 (rowTitl…` | Artist name uses three different steps across list surfaces |
| F-AP-12 | P2 | 04, 100, 101, 50, 51 | `feature/artist/ArtistProfileScreen.kt:445, :957, :1024, :1100, :1257; feature/score/ScoreE…` | `.copy(fontWeight = …)` invents type steps at eight sites |
| F-AP-21 | P3 | 54, 56, 79, 51, 04, 101 | `feature/artist/ArtistProfileScreen.kt:256, :1002; feature/artist/ArtistProfileSheets.kt:25…` | Tokens borrowed from unrelated roles and arithmetic on tokens |
| F-AP-11 | P2 | 101, 04 | `feature/artist/ArtistProfileScreen.kt:1076-1104` | Prompt answer is smaller than its question, contrary to the block's own comment |
| F-BC-15 | P3 | 05, 06, 07, 94, 132, 61, 17 | `feature/booking/BookingChrome.kt:323, :375, :520, :842, :955-957, :1064; BookingScreen.kt:…` | Twenty `.copy(fontWeight = …)` steps, and the field label is defined three times |
| F-MS-04 | P2 | 19, 60, 73, 88, 127, 34 | `feature/messages/MessagesScreen.kt:308,470,534; feature/messages/ChatScreen.kt:343,360; fe…` | `rowTitle` and `footnote` get four invented weights between them |
| F-WZ-03 | P2 | 37, 39, 41, 44, 45, 46, 72 | `feature/wizard/WizardFormSteps.kt:178,488; WizardMediaSteps.kt:182,279; WizardPublishSteps…` | `caption` is drawn at three weights; the 700 variant is an un-tokenised step |
| F-AS-19 | P3 | 09, 85, 133, 36, 35, 107 | `ArtistHomeScreen.kt:344, :565, :616, :623, :656, :863, :881, :917, :935; ArtistGigsScreen.…` | Twelve `.copy()` calls invent type steps, eight of them on `rowTitle` |
| F-AS-07 | P2 | 22, 105 | `ManageAvailabilityScreen.kt:188, :229, :260` | Manage availability hand-uppercases three sans eyebrows |
| F-AC-23 | P3 | 25, 48 | `feature/paywall/PaywallScreen.kt:542-546; feature/profile/DeleteAccountScreen.kt:600-605` | Type steps invented at the call site |
| F-AC-17 | P2 | 47, 69, 81, 49, 115, 130, 25, 93 | `designsystem/component/ListRow.kt:77, :89; designsystem/component/SwitchRow.kt:54, :72; de…` | Three row heights and two row type steps inside the same lists |
| F-SH-06 | P2 | 120, 121, 123, 138, 63, 64 | `ActivityScreen.kt:391; FeedbackScreen.kt:167; HelpCentreScreen.kt:176; RatePromptSheet.kt:…` | Six of eight SH screens override `caption`'s weight, inventing a step off the ramp |
| F-SH-20 | P3 | 137, 63, 64 | `feature/system/WhatsNewSheet.kt:174, :179; feature/system/HelpCentreScreen.kt:220; feature…` | `body.copy(fontWeight = …)` re-invents `rowTitle` on three SH surfaces |
| F-SH-18 | P3 | 120, 123, 64 | `feature/system/ActivityScreen.kt:405; feature/system/UpdateRequiredScreen.kt:167-169; feat…` | `monoPill`, the accent-badge step, does duty as plain meta on three SH screens |
| F-SH-25 | P3 | global shell (Accessibility "Always show | `designsystem/component/LightTabBar.kt:220` | The tab bar draws its labels in a compat type alias |
| F-PK-06 | P2 | 23 (panes), 87 | `feature/epk/EpkComponents.kt (6 colour + 3 type), feature/epk/EpkPanes.kt (10 colour + 20 …` | 39 compat-alias sites left in feature/epk (F-CC-10 instance list) |
| F-BN-12 | P2 | 117, 52 vs 20 | `feature/booking/BookingDetailScreen.kt:1094-1098, :1129-1133 vs feature/booking/ReviewShee…` | The same question-heading role uses two type steps |
| F-DSYS-01 | P1 | — | `designsystem/theme/Type.kt:181-194` | `caption` ships Medium +0.5sp for a reason that covers 1 of 237 sites |
| F-DSYS-02 | P2 | — | `designsystem/component/ListRow.kt:89; designsystem/component/SwitchRow.kt:72; designsystem…` | Three sizes ship for "the name of a thing": 15, 15, 14.5 |
| F-DSYS-10 | P2 | — | `designsystem/theme/Type.kt:282 (alias), :197-199 (target), :179 (the correct step)` | The app's helper/meta step is `footnote` = `chip` at **Medium**; §2's meta is 400 |
| F-DSYS-17 | P3 | — | `SectionHeader.kt:61, :121; Banner.kt:178, :198; AppTextField.kt:105, :209; AccentNote.kt:8…` | The ramp has no weight variants, so ten components mint one with `.copy(fontWeight=…)` |
| F-DSYS-20 | P3 | — | `theme/Type.kt:247-259; designsystem/component/EmptyState.kt:82; designsystem/component/She…` | Three undocumented display steps, and three sizes for "the title of the thing you just opened" |

### T10 · [P2] Body copy set in the caption colour or the caption step

*Category:* hierarchy · *Issue:* [#171](https://github.com/cestercian/artistant-android-Jetpack/issues/171)

**Why.** §2: body is 15/400 in `ink3`; `ink4` is captions and meta. On roughly twenty screens the paragraph that explains the screen is drawn in the meta grey or at 12.5 sp under a 21–26 sp title.

**Fix direction.** `body`/`ink3` for paragraphs everywhere, including `EmptyState`.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-14 | P3 | 57, 89, 110, 111, 84 and every `EmptySta | `designsystem/component/EmptyState.kt:89-90` | Empty-state body copy is set in the caption colour |
| F-GS-04 | P2 | 118, 13, 30 | `feature/signup/WelcomeScreen.kt:124-128; feature/signup/NotifPermissionScreen.kt:127-131; …` | Body paragraphs set in `ink4` (meta colour) on 118, 13 and 30 |
| F-AP-10 | P2 | 16, 99, 50, 80, 51, 101, 04 | `feature/score/BookabilityScreen.kt:313-322; feature/score/ScoreBreakdownSheet.kt:204-221; …` | Explanatory sentences set in `caption`; one body paragraph in `ink4` |
| F-BC-16 | P3 | 06, 07, 94, 17 | `feature/booking/ConfirmedScreen.kt:162-164; MatchConfirmedScreen.kt:171-172; RequestQuoteS…` | Body copy set in `ink4` on 07 / 94 / 17 and in `subtitle` on 06 |
| F-AC-03 | P2 | 47, 48, 49, 81, 113, 115, 116, 25, 91, 9 | `feature/profile/AccountChrome.kt:126-131; feature/paywall/PaywallScreen.kt:345-346; …:385-…` | Body copy set in `ink4` on eight screens; peers set it in `ink3` |
| F-AC-04 | P2 | 124, 129, 130, 115 | `feature/profile/NotificationSettingsScreen.kt:246-259; feature/profile/AccessibilityScreen…` | Multi-sentence paragraphs set in `caption` 12.5 / `ink4` |

### T11 · [P1] Copy: engineering vocabulary, "v1", "Please", raw exceptions, restating subtitles, mixed casing

*Category:* copy · *Issue:* [#172](https://github.com/cestercian/artistant-android-Jetpack/issues/172)

**Why.** §2 principles: "copy states the fact". Users are told about disk caches, servers, syncs, versions and recomputations; two screens show `Throwable.message`; a not-found screen tells them to pull-to-refresh a list that cannot; the same destination has two names.

**Fix direction.** A copy pass over the ~60 strings listed, one voice (second person, sentence case, facts), and a lint rule against the jargon list.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-05 | P2 | 44 (samples), 72 (save & exit), 06, 62,  | `feature/wizard/WizardMediaSteps.kt:115 "Staged uploads are cached on disk, so quitting the…` | Engineering vocabulary in user-facing copy |
| F-GS-11 | P2 | 29, 62, 12 (VM strings) | `feature/signup/ProfileScreen.kt:298-302; feature/signup/PrivacyScreen.kt:200-204, :160-164…` | Engineering vocabulary and "Please" in user-facing copy |
| F-GS-16 | P3 | 01, 118 | `feature/signup/SplashScreen.kt:47, :123, :130; feature/signup/WelcomeScreen.kt:119, :125` | Splash and Welcome carry different taglines although the KDoc says they share one |
| F-GS-17 | P3 | 29, 90 | `feature/signup/ProfileScreen.kt:138, :353, :377; :282-286 vs :205-216` | Handle rule stated three times; the Name helper sits under City |
| F-GS-18 | P3 | 31, 114, 118, 12, 62 | `feature/signup/LegalScreen.kt:72-101, :57-58; feature/signup/WelcomeScreen.kt:175; feature…` | Legal headings and document names follow two casing conventions |
| F-GS-24 | P3 | 12 | `feature/signup/SignupAuthScreen.kt:173-182` | The dial code is shown twice on the phone field |
| F-DS-04 | P2 | 03, 32 | `feature/search/SearchViewModel.kt:574 → feature/search/SearchScreen.kt:637-645; feature/pr…` | Raw `Throwable.message` is shown as body copy on Search and Artist list |
| F-DS-18 | P3 | 02, 58, 53 | `feature/discover/DiscoverScreen.kt:183; feature/discover/DiscoverViewModel.kt:391, :393; f…` | Copy: first person, "Something went wrong", a period on a banner title, a restating intro |
| F-AP-13 | P2 | 16, 50, 79, 99, 51, 04 | `feature/score/BookabilityScreen.kt:178, :264, :307, :317-318; feature/score/ScoreExplainer…` | The same facts are worded differently on 16, 50, 99 and 51; the product term is cased two ways |
| F-AP-14 | P2 | 50, 99, 51 | `feature/score/ScoreExplainerScreen.kt:413-420; feature/score/ScoreBreakdownSheet.kt:216-21…` | Engineering vocabulary in user-facing copy: "server", "weights", "recomputations" |
| F-AP-15 | P2 | 51 | `feature/score/ScoreHistoryScreen.kt:194, :351, :369` | History rows print raw ISO dates and a "·" for an absent value |
| F-AP-17 | P2 | 103, 50 | `feature/artist/ArtistProfileScreen.kt:387-392, :550-557, :1328-1335; feature/score/ScoreEx…` | Screen 103 says "this is you" three times; 50's subtitle restates the segmented control under it |
| F-AP-24 | P3 | 56, 102, 51, 100 | `feature/artist/ArtistProfileSheets.kt:259; feature/artist/ArtistReviewsScreen.kt:160; feat…` | Button and label conventions drift: "Submit report", fragment subtitles, an eyebrow that is a sentence |
| F-BC-09 | P2 | 06, 132 | `feature/booking/CheckoutScreen.kt:109, :178-179; feature/booking/InvoiceLogic.kt:57` | "v1" and "this version" reach the user three times |
| F-BC-10 | P2 | 132 | `feature/booking/InvoiceScreen.kt:138 vs :145, :169, :235; feature/booking/InvoiceLogic.kt:…` | 132 is titled "Invoice" while every other string on it calls it a record |
| F-BC-18 | P3 | 05, 06, 17, 61 | `feature/booking/BookingScreen.kt:132 vs CheckoutScreen.kt:108; BookingScreen.kt:115-116 + …` | Copy nits: a step counter with no step 2, a failed state that restates itself, ungrouped hints, a warning under a live button |
| F-BN-01 | P1 | 84, 10 | `feature/booking/BookingDetailScreen.kt:1023-1032; feature/bookings/BookingsScreen.kt:210-2…` | Not-found tells the user to pull-to-refresh a list that can't |
| F-BN-05 | P2 | 78, 97, 122, 18 | `feature/bookings/MonthCalendarScreen.kt:98; feature/booking/BookingDetailScreen.kt:833-837…` | Three screens explain their own implementation to the user |
| F-MS-14 | P2 | 60, 111, 127 | `feature/messages/ArchivedScreen.kt:158-160 and :186-190; feature/profile/BlockedAccountsSc…` | Internal invariants are shipped as body copy, and the badge rule is said twice |
| F-MS-15 | P3 | 34, 33 | `feature/messages/SupportChat.kt:148,152,155,175; feature/messages/ThreadDetailsSheet.kt:61…` | Assistant-voice tics in the support script and the report receipt |
| F-WZ-10 | P2 | 41, 45 | `WizardMediaSteps.kt:138,145-176,181; WizardPublishSteps.kt:93,261; token designsystem/them…` | The cover slot says 4:5, crops 3:4, and previews 4:3 |
| F-PK-10 | P2 | 66, 75, 23 (links pane) | `feature/epk/EpkSheets.kt:834, :758, feature/epk/EpkPanes.kt:992` | Three strings explain the implementation instead of the fact |
| F-PK-11 | P2 | 23, 87, 74 | `feature/epk/EpkPressKit.kt:64,66,69; feature/epk/EpkScreen.kt:559; vs 15 "client" strings …` | The booker is called "hosts" in four strings and "clients" in fifteen |
| F-PK-16 | P3 | 23 (panes) | `feature/epk/EpkPanes.kt:282,487,566,892; :285,490,569,835,963` | "+ Add" draws its plus in the string, and the action slot doubles as status |
| F-AS-17 | P3 | 09, 133 | `ArtistHomeScreen.kt:598 vs EarningsScreen.kt:86, :186-190` | "Earned this month" claims money the Earnings screen spends a banner disowning |
| F-AC-12 | P2 | 115, 48, 116 | `feature/profile/DeleteAccountScreen.kt:368; :434; :496-509` | A three-stage flow that numbers only two of its stages |
| F-AC-14 | P2 | 26, 47 | `feature/profile/ProfileScreen.kt:211-212; feature/profile/AccountScreen.kt:332, :341, :345` | The same destination has two different names, and "and" / "&" are mixed |
| F-AC-20 | P3 | 113, 91, 47, 26 | `feature/profile/DataExportScreen.kt:270; feature/paywall/PaywallScreen.kt:262-269; feature…` | Further copy leaks and one dead string (beyond F-CC-05) |
| F-AC-21 | P3 | 129, 48 | `feature/profile/AccessibilityScreen.kt:163-201; feature/profile/DeleteAccountScreen.kt:469…` | Row titles written as sentences, and three row shapes with no cue which respond |
| F-SH-04 | P2 | 64, 77 | `feature/system/FeedbackScreen.kt:81-94; navigation/ClientTabsScaffold.kt:432; feature/syst…` | "Queued on this device" is confirmed with a success tick |
| F-SH-17 | P3 | 63, 123 | `feature/system/HelpCentreScreen.kt:88; feature/system/HelpCentreViewModel.kt:34-35; peer f…` | Help centre's header greets where its peers state a fact, and centres what they left-align |

### T12a · [P2] Rows, pills, chips and status capsules are hand-rolled beside the components that exist

*Category:* consistency · *Issue:* [#173](https://github.com/cestercian/artistant-android-Jetpack/issues/173)

**Why.** `ListRow`, `Pill`, `StatusPill`, `Chip` and `Avatar` exist, yet the inbox, archive and blocked lists each hand-roll their row, statuses come as `Pill` on one screen and `StatusPill` on the next, chevrons vary in tint, and avatars are 32/40/48 across peer lists.

**Fix direction.** One `ConversationRow`, one status capsule (`StatusPill`), one chevron tint (`ink4`), one avatar size per list class.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-GS-05 | P2 | 62 | `feature/signup/PrivacyScreen.kt:224-265, :169-176, :187-194; peers designsystem/component/…` | Privacy hand-rolls its switch row and re-passes the chevron `ListRow` already draws |
| F-DS-09 | P2 | 32 | `feature/profile/ArtistListScreen.kt:106-118 (kind switcher), :120-145 (category filter)` | Artist list stacks two identical Chip rails: navigation and filter look the same |
| F-DS-12 | P2 | 32 | `feature/profile/ArtistListScreen.kt:312-322 (hand-rolled score pill) vs :274-281 (Pill(tex…` | One list row draws two pill styles; the score pill sits on a compat alias |
| F-DS-07 | P2 | 14, 15, 32 | `feature/search/SearchScreen.kt:508-513; feature/profile/ArtistListScreen.kt:283-288; featu…` | Chevrons are `lineStrong` and `ink3` here; the token and `ListRow` say `ink4` |
| F-AP-08 | P2 | 04, 50 | `feature/artist/ArtistProfileMedia.kt:164, :166, :167-174; feature/score/ScoreExplainerScre…` | Three row types, three chevrons; the disclosure row still uses compat aliases |
| F-AP-23 | P3 | 04 | `feature/artist/ArtistProfileScreen.kt:1114-1141; designsystem/component/Chip.kt:41` | "What they play" hand-rolls outline chips; the comment's reason does not hold |
| F-BC-11 | P2 | 94, 132 | `feature/booking/MatchConfirmedScreen.kt:208-214 (AccentBadge, defined BookingChrome.kt:398…` | The booking's status word is three different capsules across 94, 132 and the library |
| F-BN-08 | P2 | 10, 122, 18, 83, 96, 97 | `feature/booking/BookingDetailScreen.kt:605, :523-529; feature/bookings/BookingsScreen.kt:4…` | Booking status is a `Pill` here and a `StatusPill` one screen away |
| F-BN-06 | P2 | 122 vs 10 | `feature/bookings/BookingsScreen.kt:623-650 vs :290-374` | Offline snapshot redraws bookings in a different card language, with the badge screen 10 forbids |
| F-MS-03 | P2 | 19, 60, 127, 34 | `feature/messages/MessagesScreen.kt:424-556 (ThreadRow); feature/messages/ArchivedScreen.kt…` | Three near-identical conversation rows, hand-rolled three times |
| F-MS-09 | P2 | 88, 19 | `feature/messages/ChatScreen.kt:567-586 (StatusChip); feature/messages/MessagesScreen.kt:56…` | The status capsule and the quote pill are hand-rolled next to `StatusPill`/`Pill` |
| F-AS-08 | P2 | 22, 105 | `ManageAvailabilityScreen.kt:239-255, :265-282; designsystem/component/Pill.kt:38, :62; des…` | Selectable day/time chips are status `Pill`s: 28dp targets, pale-wash selection |
| F-AS-12 | P2 | 09, 35 | `ArtistHomeScreen.kt:859; GigRequestDetailScreen.kt:318 (component.rowAvatar = 40, Dimens.k…` | The section's person-rows use a 40dp avatar; every other list in the app uses 48 |
| F-SH-23 | P3 | 123 | `feature/system/ActivityScreen.kt:359-374 vs feature/messages/MessagesScreen.kt:465` | Activity's row disc is 32 where the inbox row it mirrors is 48 |
| F-SH-16 | P3 | 63 | `feature/system/HelpCentreScreen.kt:224-231 vs designsystem/component/ListRow.kt:114-118` | The FAQ chevron is `lineStrong` where every other chevron in the app is `ink4` |
| F-PK-13 | P3 | 23, 87 | `feature/epk/EpkHub.kt:442, :411, :488; feature/epk/EpkSheets.kt:223; peer designsystem/com…` | Hub row chrome is off the `ListRow` spec (chevron tint, leading square) |
| F-DSYS-07 | P2 | — | `designsystem/component/Pill.kt:25, :50, :62, :80-86; designsystem/component/StatusPill.kt:…` | Two capsule vocabularies for status, with two tone ladders and two type steps |
| F-DSYS-09 | P2 | — | `designsystem/component/Chip.kt:53-57; designsystem/component/SegmentedControl.kt:87-91` | Two selection languages for "pick one of N"; one paints a line token as a fill |

### T12b · [P2] Buttons, fields, counters and selection controls exist as duplicated private copies

*Category:* consistency · *Issue:* [#174](https://github.com/cestercian/artistant-android-Jetpack/issues/174)

**Why.** Two private `DestructiveButton`s each documented as the only one, two `ReasonRow`s, a pre-redesign field and chip still shipping in the press-kit panes, hand-rolled `BasicTextField`s that lose the focus state, and a secondary button whose height and label step disagree with the token sheet.

**Fix direction.** Promote the one good copy of each into `designsystem/component/` and delete the rest.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-03 | P3 | 57, 07, 94, 118, 25 and every `EmptyStat | `designsystem/component/PrimaryButton.kt:86 vs :142 (KDoc at :111-119); docs/REDESIGN_2026-…` | The token sheet and the secondary button disagree about its height |
| F-GS-06 | P2 | 31, 114 | `feature/signup/LegalScreen.kt:198-242; peer designsystem/component/SegmentedControl.kt:48-…` | `LegalSegments` duplicates the design-system `SegmentedControl` |
| F-GS-12 | P2 | 118, 13 | `feature/signup/WelcomeScreen.kt:101-112; feature/signup/NotifPermissionScreen.kt:178-192` | The secondary action under the CTA is styled two ways on 118 and 13 |
| F-GS-15 | P3 | 28, 31, 114 | `feature/signup/EmailSignUpScreen.kt:116-127, :139-145; feature/signup/LegalScreen.kt:134-1…` | Two controls for one action on 28 and 31/114; 28's footer tells the user to go back but offers no way |
| F-GS-25 | P3 | 27, 11, 119, 28, 29, 30, 31 | `feature/signup/SignupChrome.kt:105-122; peer designsystem/component/BottomActionBar.kt:35-…` | `SignupScaffold`'s footer re-implements `BottomActionBar` |
| F-BC-21 | P3 | 61 | `feature/booking/CounterOfferScreen.kt:405-412 vs designsystem/component/AppTextField.kt:89…` | The counter amount well ignores the section's field recipe |
| F-BN-10 | P2 | 52, plus Delete account | `feature/booking/BookingDetailScreen.kt:1190-1213 vs feature/profile/DeleteAccountScreen.kt…` | Two private `DestructiveButton`s, each documented as the only one |
| F-BN-11 | P2 | 18 | `feature/booking/BookingDetailScreen.kt:675-690, :1402-1417 (FlatAction)` | On screen 18 "Cancel" is the third of three identical grey buttons |
| F-MS-10 | P2 | 73 | `feature/messages/ReportConversationSheet.kt:170-231 (esp. :203, :218); peer feature/artist…` | `ReasonRow` is implemented twice, with different ring weights |
| F-WZ-05 | P2 | 37/38/43 (chips), 39 (tech), 40 (days) | `WizardFormSteps.kt:521-556 (CheckRow), :612-643 (DayStrip), :729-772 (WizardChipSection → …` | "Selected" is drawn three different ways inside one wizard |
| F-WZ-12 | P3 | 43, 44 | `WizardMediaSteps.kt:513-545 (bio), :652-661 (sample title); peer designsystem/component/Ap…` | Two hand-rolled `BasicTextField`s lose the app's focus state |
| F-WZ-14 | P3 | 45, 72, 44 | `WizardPublishSteps.kt:186-215; WizardScaffold.kt:110-141; WizardMediaSteps.kt:789-810` | Preview rows and progress tracks are one-offs where shared components exist |
| F-PK-05 | P2 | 23 (panes) vs 67, 68, 74 | `feature/epk/EpkComponents.kt:108-186 (EpkField, 5 call sites in EpkPanes.kt), :196-239 (Ep…` | The panes still ship the pre-redesign field and chip |
| F-PK-12 | P3 | 74 vs 23 (panes) | `feature/epk/EpkSheets.kt:679-701; feature/epk/EpkPanes.kt:410,511,715` | Two destructive styles inside one feature |
| F-AS-13 | P2 | 106, 22 | `ManageAvailabilityScreen.kt:298-312; error text at :285-288; copy at feature/availability/…` | Screen 106: the dock is hand-rolled and the reason Save vanished is at the bottom of a scroll |
| F-AC-06 | P2 | 48 (and BC's cancel stage) | `feature/profile/DeleteAccountScreen.kt:575-607; feature/booking/BookingDetailScreen.kt:119…` | Two private copies of `DestructiveButton`, each documented as the only one |
| F-AC-07 | P2 | 47 | `feature/profile/AccountScreen.kt:412-439` | The calendar picker is a hand-rolled single-select with a sub-44dp target |
| F-AC-19 | P3 | 26 | `feature/profile/ProfileScreen.kt:217-229; component at feature/profile/AccountChrome.kt:21…` | Profile hand-rolls the feedback line the section already exports |
| F-SH-24 | P3 | 121, 138, 63 | `feature/system/RatePromptSheet.kt:171-177; feature/system/ServiceOutageScreen.kt:182-193; …` | The section's secondary action comes in three shapes |
| F-DSYS-13 | P2 | — | `designsystem/component/Meter.kt:82-97; theme/Dimens.kt:505 (meterHeight 3), :563 (pressKit…` | One shared `Meter`, used by one feature; four other bar geometries hand-rolled |
| F-DSYS-19 | P3 | — | `designsystem/component/OtpField.kt:137; designsystem/component/AppTextField.kt:117-125` | `OtpField` draws a 1.5dp stroke at rest; every other input draws 1dp |
| F-DSYS-12 | P2 | — | `designsystem/component/SampleRow.kt:184, :187, :201, :222, :228` | `SampleRow` is the one off-token component: raw 36/18dp and alpha-mixed greys |

### T12c · [P2] Outcome discs, act headers, block labels and stat bands are drawn N ways across sections

*Category:* consistency · *Issue:* [#175](https://github.com/cestercian/artistant-android-Jetpack/issues/175)

**Why.** The confirm/confirmed/match-confirmed trio disagree about their own act header and actions; the wizard's Done disc is 64 where the funnel's is 74; "this is a group label" has four treatments in the press kit alone; the two three-up stat bands are two components in two type families; the two full-screen gates share no header or glyph.

**Fix direction.** One `OutcomeHeader`, one `ActHeader`, one `GroupLabel`, one `StatBand`, one gate layout.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-BC-07 | P2 | 06, 07, 94 | `feature/booking/CheckoutScreen.kt:145-150 (ActRow, 62dp actThumb, cover); ConfirmedScreen.…` | The act header is drawn three ways on 06 / 07 / 94, and 07's never gets a cover |
| F-BC-08 | P2 | 07, 94, 17 | `feature/booking/ConfirmedScreen.kt:212, :216-221, :227-236; MatchConfirmedScreen.kt:238, :…` | The three outcome screens disagree about their own actions |
| F-WZ-04 | P2 | 37, 24, 39, 40, 41, 43, 44 | `WizardFormSteps.kt:250 (SectionHeader), :447,479,579,755 and WizardMediaSteps.kt:99,516 (W…` | Four different components label a block across eleven steps |
| F-WZ-16 | P3 | 46 | `WizardPublishSteps.kt:293-303; peers feature/booking/BookingChrome.kt:438, feature/system/…` | The Done disc is 64dp where the app's outcome disc token is 74dp |
| F-PK-04 | P2 | 23, 67, and every pane | `feature/epk/EpkScreen.kt:574,609,634 (SectionHeader); feature/epk/EpkComponents.kt:71 used…` | Four different treatments for "this is a group label", two on screen 23 |
| F-PK-08 | P2 | 23 (gallery pane) | `feature/epk/EpkPanes.kt:428-466 and its call site :150-161; peer feature/wizard/WizardMedi…` | Two gradient pickers, and the press kit's one has no label |
| F-AP-07 | P2 | 04, 16, 50, 99, 51 | `feature/artist/ArtistProfileScreen.kt:772; feature/score/BookabilityScreen.kt:223; feature…` | The "what moves it" block gets a different heading component on every screen |
| F-AP-16 | P2 | 04, 102, 50 | `feature/artist/ArtistProfileScreen.kt:1318-1343; feature/artist/ArtistReviewsScreen.kt:280…` | Pinned action bars differ between 04 and 102/50; the Message circle is 52 next to a 54 CTA |
| F-AC-18 | P3 | 26, 47, 69 | `feature/profile/AccountChrome.kt:149-188; peer feature/artist/ArtistProfileScreen.kt:679-7…` | Two three-up stat bands, two implementations, two type families |
| F-SH-05 | P2 | 120, 121 | `feature/system/UpdateRequiredScreen.kt:80-107 vs feature/system/ServiceOutageScreen.kt:88-…` | The two full-screen gates are twins that share no header, glyph, or title step |

### T13 · [P1] Motion and rendering cost: reveal stacked on the push, per-frame skeleton recomposition, animated values read in composition

*Category:* slow · *Issue:* [#176](https://github.com/cestercian/artistant-android-Jetpack/issues/176)

**Why.** `RevealOnAppear` runs a second 300 ms animation on twelve screens after the 300 ms push; every skeleton block recomposes every frame; Done and Discover read animated values in composition; toasts ignore reduce-motion; tab roots reflow mid-slide; no cover photo ever fades in.

**Fix direction.** Reveal only on loading→loaded after the transition ends; `graphicsLayer {}` for animated alpha/scale/rotation; one hoisted skeleton transition; a shared `ImageLoader` with crossfade; `motionTween` everywhere.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-15 | P2 | 04, 05, 06, 16/50, 18/95/83/96/97, 22, 3 | `feature/artist/ArtistProfileScreen.kt:369, feature/booking/BookingScreen.kt:126, feature/b…` | Pushed screens animate twice: a 300 ms reveal stacked on the 300 ms push |
| F-CC-04 | P2 | 59, 54 and every screen that draws `Skel | `designsystem/component/Skeleton.kt:55, :68, :205-207` | Every skeleton block recomposes on every frame while loading |
| F-CC-12 | P3 | 02, 03, 04, 10, 18, 23, 32, 41, 45 | `the fifteen AsyncImage( sites in feature/discover/DiscoverScreen.kt, feature/search/Search…` | Every cover photo pops in; nothing fades |
| F-AP-02 | P1 | 04, 50 | `feature/artist/ArtistProfileScreen.kt:369; feature/score/ScoreExplainerScreen.kt:205; desi…` | RevealOnAppear stacks a 300 ms fade on the 300 ms push on 04 and 50 |
| F-GS-22 | P3 | 30 | `feature/signup/DoneScreen.kt:73-78, :98` | Done's pop-in reads an animated value in composition through `Modifier.scale` |
| F-DS-22 | P3 | 14, 15 | `feature/search/SearchScreen.kt:377 (searchSuggestions(...) — filters facets and interleave…` | Work in composition, and an animated rotation read in composition |
| F-BC-17 | P3 | 05, 06, 07 | `feature/booking/BookingScreen.kt:126, CheckoutScreen.kt:125 (RevealOnAppear, contentReveal…` | Entry motion differs step to step, and 07 reflows once its read lands |
| F-SH-11 | P2 | 123 | `feature/system/ActivityScreen.kt:102, :142-155; feature/system/ActivityViewModel.kt:63, :1…` | "Mark all read" flashes into the Activity header and shifts the title on entry |
| F-SH-15 | P3 | 63 | `feature/system/HelpCentreScreen.kt:233-240 vs :202-206` | The FAQ accordion animates on Compose defaults while its own chevron uses the tokens |
| F-SH-19 | P3 | 77 | `designsystem/component/Toast.kt:105-112` | The toast's enter/exit ignores reduce-motion |
| F-SH-21 | P3 | global shell (both roles) | `navigation/ClientTabsScaffold.kt:216-217, :237; navigation/ArtistTabsScaffold.kt:166-167, …` | Tab-root content reflows the instant a push starts, mid-slide |
| F-AS-04 | P1 | 35 | `GigRequestDetailScreen.kt:351-353 inside the scroll column :194-227; dock at :229-265` | The clash warning scrolls away while Accept stays pinned |
| F-BN-18 | P3 | 18 | `feature/booking/BookingDetailScreen.kt:158-164, :296-297 (COPIED_LABEL_MS = 1_600L) vs fea…` | Copy-to-clipboard acknowledges itself for a different length of time than its twin |
| F-PK-14 | P3 | 23 | `feature/epk/EpkPanes.kt:1076-1090, constant :1100; peer feature/booking/BookingDetailScree…` | "Copied" confirmations differ from the booking screen's |

### T14 · [P1] Retired dark palette and off-palette colour: the violet cover gradient, 360-hue avatars, lime text on white, `ink` as a surface

*Category:* token · *Issue:* [#177](https://github.com/cestercian/artistant-android-Jetpack/issues/177)

**Why.** `ArtistGradient.kt` still holds six raw-hex palettes of the retired dark design (violet #7C5CFF → #0F1014) used as every cover's fallback and as the wizard's and press kit's swatches; `Avatar` mints 360 saturated hues; seven press-kit tap targets are `accent` text on white (≈1.3:1); three components fill with `ink` where `dark` is the token.

**Fix direction.** Replace the palettes with `placeholder`/`surface2` tints (or a two-stop lime-to-ink gradient), give `Avatar` one palette, `accentInk` for any accent used as text, `dark` for dark surfaces.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-DS-01 | P1 | 02, 03, 32 (and 14 suggestion thumbs) | `feature/discover/DiscoverScreen.kt:408-423; feature/search/SearchScreen.kt:862-877; featur…` | Retired dark-palette gradient is the cover floor on every DS surface |
| F-WZ-07 | P2 | 41 | `WizardMediaSteps.kt:344-380; peer feature/epk/EpkPanes.kt:443-462` | Gradient swatches drift from the press kit's row and paint the retired palette |
| F-PK-02 | P1 | 23 (panes) | `feature/epk/EpkComponents.kt:85,146,183; feature/epk/EpkPanes.kt:403,875; correct peers Ep…` | Lime-on-white text: seven tap targets in the panes are unreadable |
| F-DSYS-08 | P2 | — | `designsystem/component/Avatar.kt:101-113` | `Avatar` mints 360 saturated hues on a one-accent palette |
| F-DSYS-03 | P2 | — | `designsystem/component/Toast.kt:135 (+ text at :163); designsystem/component/Banner.kt:218…` | `ink` is used as a surface fill in three components; `dark` is the palette's token for that |
| F-MS-08 | P2 | 131 | `feature/messages/SafetyCentreScreen.kt:79` | The Safety centre's dark card is painted `ink`, not `dark` |
| F-BN-15 | P3 | 10, 83, 97, 122 | `feature/bookings/BookingsScreen.kt:490 (ENDED_ALPHA = 0.55f, applied :433, :630); feature/…` | Three different alpha values mean "stepped back" |
| F-DSYS-22 | P3 | — | `designsystem/component/MonthCalendar.kt:579; designsystem/component/Meter.kt:85` | Two tokens used outside their role: a space as a radius, a dot as a bar height |

### T15 · [P1] Focus and tap targets: fields that never take focus, controls at 20–36 dp

*Category:* slow · *Issue:* [#178](https://github.com/cestercian/artistant-android-Jetpack/issues/178)

**Why.** Enter code, email, handle, search, feedback and the counter sheet all open with their only field unfocused; inline text actions are ~36 dp, the sheet header action 40 dp, wizard text buttons 33 dp, and the search bar / chip / segment cores 36, 36 and 20 dp.

**Fix direction.** `FocusRequester` on entry for single-field screens; `minimumInteractiveComponentSize()` or `size.rowMin` on every small control.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-GS-09 | P2 | 119, 28, 29 | `feature/signup/EnterCodeScreen.kt:119-126; feature/signup/EmailSignUpScreen.kt:154-165; fe…` | Enter code (and the email/handle forms) never request focus on entry |
| F-GS-10 | P2 | 119, 28, 118 | `feature/signup/SignupChrome.kt:353-361; call sites feature/signup/EnterCodeScreen.kt:147, …` | Inline text actions have ~36 dp tap targets |
| F-DS-02 | P2 | 14 | `feature/search/SearchScreen.kt:106, :323; no requestFocus anywhere in feature/search/, fea…` | Search field never requests focus on entry |
| F-DS-21 | P3 | 15, 104, 53 | `feature/search/SearchFilterSheet.kt:487-495 (shared with CompareByServiceSheet.kt:88-94)` | Sheet header's text action is a 40dp box with no overflow handling and no touch target |
| F-MS-12 | P2 | 08 | `feature/messages/ChatQuoteCard.kt:200-217` | The counter-quote sheet's only field does not take focus |
| F-BN-09 | P2 | 89, 52, 20, 96 | `feature/bookings/BookingsScreen.kt:563-581; feature/booking/BookingDetailScreen.kt:1175-11…` | Tertiary actions are bare `Text`s at 20–40 dp |
| F-WZ-02 | P2 | 45, 72, 39, 41 | `feature/wizard/WizardPublishSteps.kt:236-256; WizardScreen.kt:364-380; WizardFormSteps.kt:…` | Text-only buttons are 33dp tall; eight of them on the Preview step |
| F-SH-08 | P2 | 64 | `feature/system/FeedbackScreen.kt:150-157, :238-287` | Send feedback opens with the keyboard down and its only field unfocused |
| F-DSYS-06 | P1 | — | `designsystem/component/Chip.kt:87-90; designsystem/component/SegmentedControl.kt:101; desi…` | The three most-tapped small controls are 36, 36 and 20dp |

### T16 · [P1] Toasts: the wrong black, a shadow, a second host under the tab bar, 88 dp above nothing on pushed screens

*Category:* token · *Issue:* [#179](https://github.com/cestercian/artistant-android-Jetpack/issues/179)

**Why.** The capsule paints `ink` with a 12 dp shadow where the palette names `dark` and the design is flat; the press kit mounts its own host that fires under the tab bar; the root host pads for a tab bar that pushed screens do not show.

**Fix direction.** One host, `dark` fill, no shadow, bottom padding driven by whether a bar is actually visible.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-06 | P3 | 77 and every toast | `designsystem/component/Toast.kt:133-135` | Toast is drawn on the wrong black and casts a shadow on a flat design |
| F-SH-03 | P1 | 77, and every screen that raises a toast | `navigation/ArtistantNavHost.kt:198-210; designsystem/component/Toast.kt:82, :103; navigati…` | Every toast raised from a pushed screen floats 88dp above nothing |
| F-PK-01 | P1 | 23, 87, 76 | `feature/epk/EpkScreen.kt:267; peer navigation/ArtistantNavHost.kt:198-210; designsystem/co…` | Delete the press kit's own toast host; it fires under the tab bar |

### T17 · [P1] Spacing and radii drift between peers

*Category:* spacing · *Issue:* [#180](https://github.com/cestercian/artistant-android-Jetpack/issues/180)

**Why.** Peer `surface3` cards use four radii (16/18/20/24); insets drift between twin screens; two pinned bars have different tailroom; spacers stack on `spacedBy`; the tab bar ships 15–39 dp over §2's 88; provider rows are 52 beside a 54 CTA.

**Fix direction.** One radius per object class, one inset per relationship, one tailroom, and a tab-bar height that matches its own KDoc.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-GS-13 | P3 | 12 | `feature/signup/SignupAuthScreen.kt:322 (dimens.size.ctaTall = 52, Dimens.kt:187); designsy…` | Provider rows are 52 dp beside a 54 dp CTA |
| F-GS-21 | P3 | 118, 27, 13, 30, 11 | `feature/signup/WelcomeScreen.kt:140; feature/signup/CommunityCommitmentScreen.kt:112; feat…` | Peer `surface3` cards use four radii (16 / 18 / 20 / 24) |
| F-DS-17 | P3 | 02, 03, 14, 32 | `feature/discover/DiscoverScreen.kt:116-118 (top = space.sm), :195-198 (bottom listTailroom…` | Peer insets drift: header top and list tailroom |
| F-DS-25 | P3 | 03 | `feature/search/SearchScreen.kt:666-672` | The result card is `surface3` on `page` with no hairline; the only boxed card in the section |
| F-AP-22 | P3 | 04, 100, 103, 50 | `feature/artist/ArtistProfileScreen.kt:381, :391, :406, :417, :463, :499, :1170, :1186; fea…` | Spacers stack with `spacedBy`, and the top of 04 stacks up to three notices with double padding |
| F-BC-04 | P2 | 05, 06, 07, 94, 132, 17 | `feature/booking/BookingChrome.kt:171, :186-194 (CtaBar) vs designsystem/component/BottomAc…` | Two pinned bars with different tailroom; the funnel's dock makes every caller re-pad its actions |
| F-BN-19 | P3 | 18, 96, 52, 20 | `feature/booking/BookingDetailScreen.kt:546 (card, radii.card 20), :783 and :1271 (cards, r…` | Four radii for three classes of object on one page |
| F-AS-14 | P3 | 22, 105, 106 | `ManageAvailabilityScreen.kt:105, :187, :300 (space.xl = 24) and :138, :183 (space.lg = 16)…` | Manage availability pads by 24 and 16; the rest of the section pads by the 20 gutter |
| F-AS-20 | P3 | 09 | `ArtistHomeScreen.kt:246-257; designsystem/component/SectionHeader.kt:37-40` | "N waiting" touches the section header it hangs from |
| F-SH-22 | P3 | 63, 123 | `feature/system/ActivityScreen.kt:196 (chrome.contentTailroom = 16dp) vs feature/system/Hel…` | Two pushed SH screens, two tailrooms under the last row |
| F-DSYS-05 | P1 | — | `designsystem/component/LightTabBar.kt:98-104 (the claim), :190 (the cell), :287-291 (the m…` | The tab bar's own arithmetic contradicts its KDoc by 24dp; it ships 15–39dp over §2's 88 |

### T18 · [P3] Dead and duplicate code carrying the raw literals, aliases and stale tokens

*Category:* consistency · *Issue:* [#181](https://github.com/cestercian/artistant-android-Jetpack/issues/181)

**Why.** Nine design-system files, a pre-redesign auth screen, seven funnel helpers, three signup helpers, half a dashboard token group, ten type steps, two haptics and an unreachable component gallery have no caller — and they are where most remaining raw dp and compat aliases live.

**Fix direction.** Delete them and fix the two stale `PARITY_CHECKLIST.md` rows.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-CC-11 | P3 | — | `zero callers outside their own file — designsystem/component/ScoreRing.kt (raw 64/6dp, 1.2…` | Dead and duplicate components still compile into the app |
| F-GS-23 | P3 | 12 (legacy) | `ui/auth/AuthScreen.kt (0 references to AuthScreen(; M3 TextButton at :145, default ModalBo…` | Dead code: `AuthScreen.kt`, `EditorialHeadline.kt`, `SignupBackButton`, `SignupInputRow` |
| F-DS-13 | P3 | (component; none) | `designsystem/component/ArtistTile.kt:51-52 (192.dp / 252.dp), :65 (radii.md, not the tile'…` | `ArtistTile.kt` is dead and carries the section's raw dp, compat type steps and `Color.White` |
| F-AP-25 | P3 | (none — component) | `designsystem/component/ScoreRing.kt:38-39, :118, :131, :134` | `ScoreRing.kt` is dead code carrying the section's only raw literals |
| F-BC-20 | P3 | 05, 06, 17 (the live alias users) | `feature/booking/BookingChrome.kt:84 CircleIconButton, :132 FunnelHeader (type.headline :14…` | `BookingChrome.kt` carries seven dead composables (13 of its 16 compat aliases live in them); `StatusTimeline` is drawn by no screen |
| F-AS-16 | P3 | 09, 85, 86, 133 | `designsystem/theme/Dimens.kt:518 dayCellW 42, :519 dayCellH 50, :526 scoreRing 86, barsWid…` | Two dimension groups describe one dashboard, and half of one is dead |
| F-DSYS-18 | P3 | — | `designsystem/component/ComponentGallery.kt:38; theme/Type.kt:245,284,286,314,339, 343,390,…` | Dead surface: an unreachable gallery, 10 unused type steps, 2 unused haptics, 1 unused token |
| F-PK-17 | P3 | n/a | `feature/epk/EpkHub.kt:571,593` | `PREVIEW_WIDTH = 350.dp` is preview-only (raw-unit sweep false positive) |

### T20 · [P1] Slop patterns: a review pre-rated five stars, helper text under every field, permanent explanatory banners, glyph circles, "See all" everywhere

*Category:* slop · *Issue:* [#182](https://github.com/cestercian/artistant-android-Jetpack/issues/182)

**Why.** The review sheet opens at five stars with "Great night" and Post enabled; the wizard puts helper text under every field and a banner on five of nine steps; every rail says "See all" regardless of size; one eyebrow sits over sixteen rows and another over one; the paywall centres seven paragraphs.

**Fix direction.** Start the review at zero; helper text only where the field needs it; banners only for a state; "See all" only past the rail's width; left-align what the section left-aligns.

| Finding | Sev | Screens | Where | Title |
|---|---|---|---|---|
| F-BN-02 | P1 | 20, 98 | `feature/booking/ReviewSheet.kt:87-95 (rating: Int = 5); :254-269; :391-397` | Review sheet opens pre-rated five stars, "Great night" |
| F-BN-13 | P2 | 20, 98 | `feature/booking/ReviewSheet.kt:206-241` | The review sheet is titled twice and fronted by a stock person glyph |
| F-GS-14 | P3 | 12, 28, 29, 71, 11, 119, 62 | `feature/signup/SignupAuthScreen.kt:150, :215, :228; feature/signup/EmailSignUpScreen.kt:23…` | Banners stack (up to three) and five screens carry a permanent explanatory Note |
| F-DS-23 | P3 | 02 | `feature/discover/DiscoverScreen.kt:360-365; feature/discover/DiscoverViewModel.kt:368-370` | "See all" on every rail regardless of size |
| F-WZ-11 | P2 | 42, 37, 38, 24, 43 | `WizardMediaSteps.kt:400,411,422,475 and :427-436; WizardFormSteps.kt:100-112, :222-241, :2…` | Helper text under every field, and an explanatory Banner on five of nine steps |
| F-WZ-13 | P3 | 44 | `WizardMediaSteps.kt:619-674; peer designsystem/component/SampleRow.kt:54-72` | The sample row's accent play disc is inert, and diverges from the design system's `SampleRow` |
| F-AC-09 | P2 | 25, 91, 92, 93 | `feature/paywall/PaywallScreen.kt:206, :249, :266, :381, :387, :445, :451` | The paywall centres seven text blocks; the rest of the section is left-aligned |
| F-AC-13 | P2 | 47, 69 | `feature/profile/AccountScreen.kt:269-280; :283; :296-392` | One eyebrow over sixteen rows, another over one |

## Screen coverage — all 138 design numbers

Section codes: GS = Getting started, DS = Discover & search, AP = The artist profile, BC = Book & confirm, BN = The booking & the night, MS = Messaging & safety, WZ = Artist setup wizard, PK = Press kit & media, AS = Artist studio, AC = Account & settings, SH = System & housekeeping. A screen with no finding of its own still inherits the cross-cutting (CC) and design-system (DSYS) themes.

| # | Screen (auditor's label) | Section | Files | Findings |
|---|---|---|---|---|
| 001 | 01 | GS | `feature/signup/SplashScreen.kt, ui/ArtistantRoot.kt` | F-GS-16, F-GS-19 |
| 002 | 02 Discover | DS | `feature/discover/DiscoverScreen.kt, DiscoverViewModel.kt, DiscoverHero` | F-DS-01, F-DS-05, F-DS-06, F-DS-14, F-DS-17, F-DS-18, F-DS-19, F-DS-23, F-DS-24 |
| 003 | 03 Search results | DS | `feature/search/SearchScreen.kt:551-778` | F-DS-01, F-DS-04, F-DS-05, F-DS-06, F-DS-10, F-DS-15, F-DS-16, F-DS-25 |
| 004 | 04 Artist profile | AP | `feature/artist/ArtistProfileScreen.kt, ArtistProfileMedia.kt, ArtistPr` | F-AP-01 |
| 005 | 05 | BC | `feature/booking/BookingScreen.kt, BookingChrome.kt (FunnelStepBar, Fun` | F-BC-01, F-BC-03, F-BC-04, F-BC-05, F-BC-06, F-BC-14, F-BC-15, F-BC-17, F-BC-18, F-BC-20 |
| 006 | 06 | BC | `feature/booking/CheckoutScreen.kt, CheckoutLogic.kt, BookingChrome.kt ` | F-BC-02, F-BC-03, F-BC-04, F-BC-07, F-BC-09, F-BC-14, F-BC-16, F-BC-17, F-BC-19 |
| 007 | 07 | BC | `feature/booking/ConfirmedScreen.kt, BookingChrome.kt (OutcomeMark, Fun` | F-BC-04, F-BC-07, F-BC-08, F-BC-16, F-BC-17 |
| 008 | 08 Chat | MS | `feature/messages/ChatScreen.kt, ChatQuoteCard.kt, MessageComposer.kt` | F-MS-01, F-MS-07, F-MS-09, F-MS-12, F-MS-15 |
| 009 | 09 Studio dashboard | AS | `feature/artisthome/ArtistHomeScreen.kt:203-299, ArtistStudioLogic.kt` | F-AS-01, F-AS-02, F-AS-03, F-AS-15, F-AS-16, F-AS-17, F-AS-19, F-AS-20 |
| 010 | 10 Bookings list | BN | `feature/bookings/BookingsScreen.kt:195-490, BookingsLogic.kt` | F-BN-08, F-BN-09, F-BN-15, F-BN-16, F-BN-17 |
| 011 | 11 | GS | `feature/signup/RoleScreen.kt, SignupChrome.kt` | F-GS-02, F-GS-03, F-GS-14, F-GS-19, F-GS-20, F-GS-21 |
| 012 | 12 | GS | `feature/signup/SignupAuthScreen.kt, ui/auth/AuthViewModel.kt` | F-GS-01, F-GS-02, F-GS-07, F-GS-09, F-GS-11, F-GS-13, F-GS-14, F-GS-18, F-GS-24 |
| 013 | 13 | GS | `feature/signup/NotifPermissionScreen.kt` | F-GS-03, F-GS-04, F-GS-12, F-GS-19, F-GS-20, F-GS-21 |
| 014 | 14 Search browse | DS | `feature/search/SearchScreen.kt:365-548` | F-DS-02, F-DS-07, F-DS-17, F-DS-20, F-DS-22 |
| 015 | 15 Filters sheet | DS | `feature/search/SearchFilterSheet.kt` | F-DS-03, F-DS-20, F-DS-21, F-DS-22 |
| 016 | 16 Bookability (client audit) | AP | `feature/score/BookabilityScreen.kt` | F-AP-01 |
| 017 | 17 | BC | `feature/booking/RequestQuoteScreen.kt, RequestQuoteViewModel.kt (strin` | F-BC-03, F-BC-04, F-BC-08, F-BC-12, F-BC-15, F-BC-16, F-BC-18, F-BC-19 |
| 018 | 18 Booking detail confirmed | BN | `feature/booking/BookingDetailScreen.kt:613-691, 872-879` | F-BN-07, F-BN-10, F-BN-11, F-BN-14, F-BN-16 |
| 019 | 19 Inbox | MS | `feature/messages/MessagesScreen.kt` | F-MS-01, F-MS-03, F-MS-04, F-MS-09, F-MS-14 |
| 020 | 20 Review sheet | BN | `feature/booking/ReviewSheet.kt:190-347` | F-BN-02, F-BN-12, F-BN-13, F-BN-18, F-BN-19 |
| 021 | Open gigs | — | — | blocked — not shipped (PARITY_CHECKLIST.md), by design |
| 022 | 22 Manage availability | AS | `feature/availability/ManageAvailabilityScreen.kt` | F-AS-02, F-AS-06, F-AS-07, F-AS-08, F-AS-13, F-AS-14 |
| 023 | 23 Press kit hub (filled) | PK | `feature/epk/EpkScreen.kt, EpkHub.kt, EpkPressKit.kt, EpkPanes.kt (Shar` | F-PK-01, F-PK-04, F-PK-06, F-PK-11, F-PK-13, F-PK-15 |
| 024 | 24 Pricing | WZ | `feature/wizard/WizardFormSteps.kt:245-424` | F-WZ-04, F-WZ-08, F-WZ-13, F-WZ-14 |
| 025 | 25 Pro paywall (offer) | AC | `feature/paywall/PaywallScreen.kt:330-352` | F-AC-02, F-AC-03, F-AC-08, F-AC-09, F-AC-21 |
| 026 | 26 Profile (client tab root) | AC | `feature/profile/ProfileScreen.kt` | F-AC-14, F-AC-18, F-AC-19, F-AC-20 |
| 027 | 27 | GS | `feature/signup/CommunityCommitmentScreen.kt` | F-GS-02, F-GS-03, F-GS-19, F-GS-21 |
| 028 | 28 | GS | `feature/signup/EmailSignUpScreen.kt` | F-GS-02, F-GS-09, F-GS-10, F-GS-14, F-GS-15, F-GS-20 |
| 029 | 29 | GS | `feature/signup/ProfileScreen.kt, SignupStep.kt` | F-GS-02, F-GS-08, F-GS-09, F-GS-11, F-GS-14, F-GS-17, F-GS-19 |
| 030 | 30 | GS | `feature/signup/DoneScreen.kt` | F-GS-03, F-GS-04, F-GS-19, F-GS-20, F-GS-21, F-GS-22 |
| 031 | 31 | GS | `feature/signup/LegalScreen.kt` | F-GS-01, F-GS-02, F-GS-06, F-GS-15, F-GS-18 |
| 032 | 32 Artist list | DS | `feature/profile/ArtistListScreen.kt, ArtistListKind.kt` | F-DS-01, F-DS-04, F-DS-05, F-DS-06, F-DS-07, F-DS-09, F-DS-11, F-DS-12, F-DS-16, F-DS-17, F-DS-19 |
| 033 | 33 Thread details sheet | MS | `feature/messages/ThreadDetailsSheet.kt` | F-MS-01, F-MS-02, F-MS-08, F-MS-15 |
| 034 | 34 Support chat | MS | `feature/messages/SupportChat.kt` | F-MS-03, F-MS-15, F-MS-16 |
| 035 | 35 Gig request detail | AS | `feature/gigs/GigRequestDetailScreen.kt:341-360` | F-AS-04, F-AS-12, F-AS-19 |
| 036 | 36 Gigs | AS | `feature/gigs/ArtistGigsScreen.kt` | F-AS-02, F-AS-03, F-AS-05, F-AS-06, F-AS-19 |
| 037 | 37 Identity | WZ | `feature/wizard/WizardFormSteps.kt:70-186` | F-CC-09, F-WZ-03, F-WZ-04, F-WZ-11 |
| 038 | 38 Location | WZ | `feature/wizard/WizardFormSteps.kt:188-243` | F-WZ-11 |
| 039 | 39 Tech | WZ | `feature/wizard/WizardFormSteps.kt:426-500` | F-WZ-02, F-WZ-03, F-WZ-04, F-WZ-05 |
| 040 | 40 Availability | WZ | `feature/wizard/WizardFormSteps.kt:565-700` | F-WZ-04, F-WZ-05, F-WZ-06 |
| 041 | 41 Cover | WZ | `feature/wizard/WizardMediaSteps.kt:88-380` | F-CC-02, F-WZ-02, F-WZ-07, F-WZ-10 |
| 042 | 42 Socials | WZ | `feature/wizard/WizardMediaSteps.kt:382-477` | F-WZ-11 |
| 043 | 43 Bio | WZ | `feature/wizard/WizardMediaSteps.kt:479-573` | F-WZ-04, F-WZ-12 |
| 044 | 44 Samples | WZ | `feature/wizard/WizardMediaSteps.kt:575-814` | F-CC-02, F-WZ-04, F-WZ-12, F-WZ-13, F-WZ-15 |
| 045 | 45 Preview | WZ | `feature/wizard/WizardPublishSteps.kt:68-261` | F-WZ-01, F-WZ-02, F-WZ-03, F-WZ-14 |
| 046 | 46 Done | WZ | `feature/wizard/WizardPublishSteps.kt:263-416` | F-CC-15, F-WZ-06, F-WZ-16 |
| 047 | 47 Account settings list | AC | `feature/profile/AccountScreen.kt` | F-AC-05, F-AC-07, F-AC-13, F-AC-14, F-AC-16, F-AC-17, F-AC-18 |
| 048 | 48 Delete stage 2 (consequences) | AC | `feature/profile/DeleteAccountScreen.kt:420-480` | F-AC-03, F-AC-06, F-AC-12, F-AC-21 |
| 049 | 49 Data export ready | AC | `feature/profile/DataExportScreen.kt:379-443` | F-AC-01, F-AC-02, F-AC-03, F-AC-11, F-AC-16, F-AC-17 |
| 050 | 50 Score explainer (Score / Stats / Opportunitie | AP | `feature/score/ScoreExplainerScreen.kt, ScoreDonut.kt, ScoreOpportuniti` | F-AP-01 |
| 051 | 51 Score history | AP | `feature/score/ScoreHistoryScreen.kt` | F-AP-03 |
| 052 | 52 Cancel stage 2 | BN | `feature/booking/BookingDetailScreen.kt:1127-1188` | F-BN-09, F-BN-10, F-BN-12 |
| 053 | 53 Compare by service | DS | `feature/search/CompareByServiceSheet.kt` | F-DS-18, F-DS-20 |
| 054 | 54 Profile loading (skeleton, no nav bar) | AP | `ArtistProfileScreen.kt:222-284` | F-AP-20, F-AP-21 |
| 055 | 55 Artist not found | AP | `ArtistProfileScreen.kt:301-345` | none |
| 056 | 56 Report artist sheet | AP | `feature/artist/ArtistProfileSheets.kt:168-345` | F-AP-06, F-AP-21, F-AP-24 |
| 057 | 57 Search empty | DS | `feature/search/SearchScreen.kt:202-223, SearchLabels.kt:252-288` | F-DS-19 |
| 058 | 58 Search failed | DS | `feature/search/SearchScreen.kt:175-196` | F-DS-08, F-DS-18, F-DS-19 |
| 059 | 59 Discover loading | DS | `feature/discover/DiscoverScreen.kt:143-156` | none |
| 060 | 60 Archived list | MS | `feature/messages/ArchivedScreen.kt` | F-MS-03, F-MS-04, F-MS-14 |
| 061 | 61 | BC | `feature/booking/CounterOfferScreen.kt` | F-BC-13, F-BC-14, F-BC-18, F-BC-21 |
| 062 | 62 | GS | `feature/signup/PrivacyScreen.kt, PrivacyPreferences.kt` | F-GS-02, F-GS-05, F-GS-11, F-GS-14 |
| 063 | 63 Help centre | SH | `feature/system/HelpCentreScreen.kt, HelpCentreViewModel.kt, HelpConten` | F-SH-06, F-SH-10, F-SH-15, F-SH-16, F-SH-17, F-SH-20, F-SH-22, F-SH-24 |
| 064 | 64 Send feedback | SH | `feature/system/FeedbackScreen.kt, FeedbackViewModel.kt` | F-SH-04, F-SH-06, F-SH-08, F-SH-09, F-SH-13, F-SH-18, F-SH-20 |
| 065 | 65 Add cover sheet | PK | `feature/epk/EpkSheets.kt:249-298` | none |
| 066 | 66 Stalled uploads sheet | PK | `feature/epk/EpkSheets.kt:787-897, EpkPressKit.kt:352-368` | F-PK-03, F-PK-10 |
| 067 | 67 Edit bio sheet | PK | `feature/epk/EpkSheets.kt:322-407` | F-PK-04, F-PK-09 |
| 068 | 68 Add personality sheet | PK | `feature/epk/EpkSheets.kt:425-599` | F-PK-07, F-PK-09 |
| 069 | 69 Account list, artist group injected | AC | `feature/profile/AccountScreen.kt:269-280` | F-AC-13, F-AC-18 |
| 070 | 70 Chat accept narration | MS | `ChatScreen.kt:970-1000, designsystem/component/SendingNarration.kt` | none |
| 071 | 71 | GS | `feature/signup/RoleScreen.kt (hydrationError), SignupChrome.kt (Hydrat` | F-GS-14 |
| 072 | 72 Wizard shell / Save & exit | WZ | `feature/wizard/WizardScreen.kt, WizardScaffold.kt` | F-CC-09, F-WZ-01, F-WZ-02, F-WZ-09, F-WZ-14 |
| 073 | 73 Report conversation sheet | MS | `feature/messages/ReportConversationSheet.kt` | F-MS-10, F-MS-13, F-MS-16 |
| 074 | 74 Edit link sheet | PK | `feature/epk/EpkSheets.kt:617-711` | F-PK-12 |
| 075 | 75 Add audio sheet | PK | `feature/epk/EpkSheets.kt:732-771` | F-PK-10 |
| 076 | 76 Hub other state (upload banner) | PK | `feature/epk/EpkHub.kt:129-229, EpkPressKit.kt:295-325` | none |
| 077 | 77 Toast | SH | `feature/system/ToastController.kt; navigation/ArtistantNavHost.kt:198-` | F-SH-03, F-SH-04, F-SH-19 |
| 078 | 78 Month calendar | BN | `feature/bookings/MonthCalendarScreen.kt` | F-BN-04, F-BN-05, F-BN-16 |
| 079 | 79 Explainer — New tier | AP | `ScoreExplainerScreen.kt:317-331, 353-387` | F-AP-13, F-AP-21 |
| 080 | 80 Explainer — failed read | AP | `ScoreExplainerScreen.kt:275-315` | F-AP-10, F-AP-19 |
| 081 | 81 Data export idle | AC | `feature/profile/DataExportScreen.kt:289-319` | F-AC-02, F-AC-03, F-AC-11, F-AC-17 |
| 082 | 82 Data export requested | AC | `feature/profile/DataExportScreen.kt:321-376` | F-AC-11 |
| 083 | 83 Cancelled | BN | `feature/booking/BookingDetailScreen.kt:710-761` | F-BN-14, F-BN-15 |
| 084 | 84 Not found | BN | `feature/booking/BookingDetailScreen.kt:987-1035, BookingDetailViewMode` | F-BN-01, F-BN-16 |
| 085 | 85 Dashboard — cold | AS | `ArtistHomeScreen.kt:305-357` | F-AS-05, F-AS-19 |
| 086 | 86 Dashboard — unavailable | AS | `ArtistHomeScreen.kt:363-434` | F-AS-09, F-AS-10, F-AS-11 |
| 087 | 87 Empty kit (invitation rows) | PK | `feature/epk/EpkScreen.kt:555-566,638-643, EpkHub.kt:457-511` | F-PK-11, F-PK-13 |
| 088 | 88 Chat other state | MS | `feature/messages/ChatScreen.kt` | F-MS-06, F-MS-11, F-MS-13 |
| 089 | 89 Bookings empty + nudge | BN | `feature/bookings/BookingsScreen.kt:496-584` | F-BN-09, F-BN-14, F-BN-16 |
| 090 | 90 | GS | `feature/signup/ProfileScreen.kt (HandleStatus.Taken)` | F-GS-17, F-GS-19, F-GS-20 |
| 091 | 91 Paywall pending | AC | `feature/paywall/PaywallScreen.kt:354-410` | F-AC-03, F-AC-08, F-AC-09, F-AC-20 |
| 092 | 92 Paywall outage | AC | `feature/paywall/PaywallScreen.kt:412-459` | F-AC-08, F-AC-09, F-AC-10 |
| 093 | 93 Paywall active | AC | `feature/paywall/PaywallScreen.kt:461-502` | F-AC-02, F-AC-08 |
| 094 | 94 | BC | `feature/booking/MatchConfirmedScreen.kt` | F-BC-04, F-BC-05, F-BC-07, F-BC-08, F-BC-11, F-BC-16 |
| 095 | 95 Awaiting | BN | `feature/booking/BookingDetailScreen.kt:693-708, 881-915` | F-BN-14 |
| 096 | 96 Disputed | BN | `feature/booking/BookingDetailScreen.kt:774-813, 921-960` | F-BN-03, F-BN-13 |
| 097 | 97 Read-only | BN | `feature/booking/BookingDetailScreen.kt:816-838, 962-964` | F-BN-05, F-BN-15 |
| 098 | 98 Review sheet, no name | BN | `feature/booking/ReviewSheet.kt:243-251` | F-BN-13 |
| 099 | 99 Score breakdown sheet (degraded) | AP | `feature/score/ScoreBreakdownSheet.kt` | F-AP-01 |
| 100 | 100 Profile with scoped failure banner | AP | `ArtistProfileScreen.kt:399-408, 1161-1222` | F-AP-22, F-AP-24 |
| 101 | 101 No-audio redirect | AP | `ArtistProfileScreen.kt:990-1037, 1083-1107` | F-AP-10, F-AP-11, F-AP-19 |
| 102 | 102 All reviews | AP | `feature/artist/ArtistReviewsScreen.kt, ReviewSearch.kt` | F-AP-16, F-AP-19, F-AP-24 |
| 103 | 103 Self view | AP | `ArtistProfileScreen.kt:383-393, 550-557, 1328-1335` | F-AP-17 |
| 104 | 104 Filters, filters on | DS | `feature/search/SearchFilterSheet.kt:136-149, SearchScreen.kt:791-840` | F-DS-03, F-DS-10, F-DS-20 |
| 105 | 105 Availability preview | AS | `ManageAvailabilityScreen.kt:187-226` | F-AS-07, F-AS-08 |
| 106 | 106 Failed seed | AS | `ManageAvailabilityScreen.kt:298-312, ManageAvailabilityViewModel.kt:14` | F-AS-13 |
| 107 | 107 Countered | AS | `GigRequestDetailScreen.kt:369-434` | F-AS-19 |
| 108 | 108 Declined | AS | `GigRequestDetailScreen.kt:437-480` | none |
| 109 | 109 Not found | AS | `GigRequestDetailScreen.kt:155-180` | none |
| 110 | 110 Inbox other state | MS | `feature/messages/MessagesScreen.kt` | F-MS-05, F-MS-06, F-MS-11 |
| 111 | 111 Archived other state | MS | `feature/messages/ArchivedScreen.kt` | F-MS-05, F-MS-14 |
| 112 | 112 Artist list, empty | DS | `feature/profile/ArtistListScreen.kt:165-184, ArtistListKind.kt:48-73` | F-DS-19 |
| 113 | 113 Data export failed | AC | `feature/profile/DataExportScreen.kt:445-481` | F-AC-11, F-AC-20 |
| 114 | 114 | GS | `feature/signup/LegalScreen.kt` | F-GS-01, F-GS-06, F-GS-18 |
| 115 | 115 Delete stage 1 (reason) | AC | `feature/profile/DeleteAccountScreen.kt:355-418` | F-AC-04, F-AC-06, F-AC-12, F-AC-17 |
| 116 | 116 Delete stage 3 (receipt) | AC | `feature/profile/DeleteAccountScreen.kt:482-549` | F-AC-03, F-AC-12 |
| 117 | 117 Cancel stage 1 | BN | `feature/booking/BookingDetailScreen.kt:1091-1125` | F-BN-12 |
| 118 | 118 | GS | `feature/signup/WelcomeScreen.kt` | F-GS-01, F-GS-04, F-GS-10, F-GS-12, F-GS-16, F-GS-21 |
| 119 | 119 | GS | `feature/signup/EnterCodeScreen.kt` | F-GS-02, F-GS-09, F-GS-10, F-GS-14, F-GS-19 |
| 120 | 120 Update required | SH | `feature/system/UpdateRequiredScreen.kt` | F-SH-05, F-SH-06, F-SH-18 |
| 121 | 121 Service outage | SH | `feature/system/ServiceOutageScreen.kt` | F-SH-05, F-SH-06, F-SH-24 |
| 122 | 122 Bookings offline | BN | `feature/bookings/BookingsScreen.kt:586-710, BookingsSnapshot.kt` | F-BN-05, F-BN-06, F-BN-08 |
| 123 | 123 Activity | SH | `feature/system/ActivityScreen.kt, ActivityViewModel.kt` | F-CC-02, F-SH-06, F-SH-11, F-SH-12, F-SH-18, F-SH-22, F-SH-23 |
| 124 | 124 Notification settings | AC | `feature/profile/NotificationSettingsScreen.kt` | F-AC-04, F-AC-16, F-AC-22 |
| 125 | Invite a friend | — | — | blocked — not shipped (PARITY_CHECKLIST.md), by design |
| 126 | — | — | — | no coverage row (see note) |
| 127 | 127 Blocked accounts | MS | `feature/profile/BlockedAccountsScreen.kt` | F-MS-02, F-MS-03, F-MS-04, F-MS-06, F-MS-11, F-MS-14 |
| 128 | 128 Devices | AC | `feature/profile/DevicesScreen.kt` | F-AC-15, F-AC-16 |
| 129 | 129 Accessibility | AC | `feature/profile/AccessibilityScreen.kt` | F-AC-04, F-AC-17, F-AC-21 |
| 130 | 130 Language & region | AC | `feature/profile/LanguageScreen.kt` | F-AC-04, F-AC-07, F-AC-17 |
| 131 | 131 Safety centre | MS | `feature/messages/SafetyCentreScreen.kt` | F-MS-07, F-MS-08 |
| 132 | 132 | BC | `feature/booking/InvoiceScreen.kt, InvoiceLogic.kt` | F-BC-03, F-BC-04, F-BC-09, F-BC-10, F-BC-11, F-BC-19 |
| 133 | 133 Earnings | AS | `feature/artisthome/EarningsScreen.kt` | F-AS-03, F-AS-05, F-AS-15, F-AS-17, F-AS-18, F-AS-19 |
| 134 | Tax details | — | — | blocked — not shipped (PARITY_CHECKLIST.md), by design |
| 135 | Get verified | — | — | blocked — not shipped (PARITY_CHECKLIST.md), by design |
| 136 | — | — | — | no coverage row (see note) |
| 137 | 137 What's new | SH | `feature/system/WhatsNewSheet.kt, ReleaseNotes.kt` | F-CC-02, F-SH-07, F-SH-20 |
| 138 | 138 Rate Artistant | SH | `feature/system/RatePromptSheet.kt, RatePrompt.kt` | F-SH-06, F-SH-24 |

Screens without a coverage row in any report: 126, 136. They are reachable only through the section files already audited (the auditors keyed their tables by the design's primary numbers); their states are covered by the themes above but were not itemised — worth a look on the device walk.

## Device walk

This audit was code-grounded. The fastest visual confirmation is the repo's own debug harness on the maintainer's `artistant` AVD (RELEASE.md §10):

```bash
./gradlew :app:assembleDevDebug
adb install -r -t app/build/outputs/apk/dev/debug/app-dev-debug.apk
# client tabs, seeded rows (02/03/04/05/06/07/10/18/19/08/26/47 …)
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,skip-signup-as-client,seed-fixture-data,seed-open-quote"
# artist tabs (09/36/35/22/23/133 …)
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,skip-signup-as-artist,seed-fixture-data,seed-pending-request"
# the states no tap can reach
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,skip-signup-as-client,seed-disputed-booking"     # 96
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,skip-signup-as-client,seed-read-only-booking"    # 97
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,skip-signup-as-client,seed-blocked-user"         # 127
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,skip-signup-as-client,block-list-unavailable"    # 127 failed
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,force-update"                                     # 120
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,service-outage"                                   # 121
adb shell am start -S -n in.artistant.app/.MainActivity -e uitest "reset,land-in-wizard-at-identity"                       # 72/37…46
adb exec-out screencap -p > shot.png
```

`-S` is load-bearing (a flagged start against a running `singleTop` activity goes to `onNewIntent` and the harness never installs). The signup screens (01/11/12/13/27/28/29/30/31/62/71/90/114/118/119) need no flag — launch with `reset` only. Walk each theme's screen list above and tick the acceptance box on its issue.

## Appendices

The thirteen raw reports, verbatim, with evidence blocks: [CC](design-qa/CC.md), [GS](design-qa/GS.md), [DS](design-qa/DS.md), [AP](design-qa/AP.md), [BC](design-qa/BC.md), [BN](design-qa/BN.md), [MS](design-qa/MS.md), [WZ](design-qa/WZ.md), [PK](design-qa/PK.md), [AS](design-qa/AS.md), [AC](design-qa/AC.md), [SH](design-qa/SH.md), [DSYS](design-qa/DSYS.md).
