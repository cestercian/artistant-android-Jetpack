# SCREEN_INVENTORY.md — Artistant Android

Every iOS SwiftUI screen mapped to its Compose target, plus the design-system
tokens and the 27 reusable components. **45 screens, 27 components.** For each
screen: purpose, ViewModel, navigation, Compose equivalent, models, APIs, state,
dependencies, and notes on animation / gesture / lifecycle.

Roles: **C** = client-side, **A** = artist-side, **S** = shared.

---

## 1. Design system tokens

> **Superseded — Sep 2026.** The dark, dual-accent language this section
> described was replaced by **"Artistant iOS Light"**: light surfaces, one lime
> accent for both roles, Plus Jakarta Sans + JetBrains Mono, no editorial serif.
>
> **`docs/REDESIGN_2026-09.md` is the token sheet.** §2 has the palette, the
> type ramp and the geometry as measured off the design; §4 has the old→new
> mapping for every `AppColors` name that survived. Read it before touching a
> screen — the table that used to live here is now wrong in every row, so it has
> been removed rather than left to be copied out of by mistake.
>
> What has NOT changed: the token files are still the only place a hex, a dp or
> an sp may be written (`designsystem/theme/Color.kt`, `Type.kt`, `Dimens.kt`),
> screens still read them through `AppTheme.colors / type / dimens`, and the
> over-media chrome (`glass*`, `inkOnMedia*`, `ArtistGradient`) is deliberately
> untouched by the redesign because photos are still photos.
>
> The v2 component library that implements the new language lives in
> `designsystem/component/` — `PrimaryButton`, `SecondaryButton`, `IconCircle`,
> `SearchBar`, `Chip`, `SectionHeader`, `Tile`, `HeroCard`, `ListRow`, `Banner`,
> `StatusPill`, `EmptyState`, `Skeleton`, `Toast`, `LightTabBar`, `ScreenHeader`,
> `BackHeader`, `SheetScaffold`, `AppTextField`, `OtpField`.

---

## 2. Navigation model

Two role-scoped nav graphs under a single `NavHost`, gated by auth/role:

```
ArtistantRoot:
  not signed in ............... Signup graph (step-driven, no back stack)
  artist & !setupComplete ..... Wizard graph (step-driven)
  role == client .............. ClientTabs (Discover · Bookings · Messages · Profile · Search)
  role == artist .............. ArtistTabs (Home · Gigs · Messages · EPK)
```

**Typed routes** (`@Serializable`, Navigation-Compose) replace the two iOS `Route`
enums:
- **ClientRoute:** `ArtistProfile(id)`, `ScoreExplainer`, `Booking(artistId)`,
  `RequestQuote(artistId)`, `Checkout`, `Confirmed(bookingId)`,
  `BookingDetail(bookingId)`, `Chat(threadId)`, `Search`.
- **ArtistRoute:** `GigRequest(id)`, `ScoreExplainer`.

`ArtistProfile(id)` routes through an **`ArtistRouteLoader`** (skeleton →
`ArtistsRepository.ensureFull(id)` fetch-on-miss → screen or not-found), mirroring
iOS. **Deep links** flow through a `DeepLinkRouter` (SharedFlow of nav events +
`pending*` ids) fed by FCM taps — the `TabRouter` analogue.

**Client "search circle".** iOS uses the iOS-26 `Tab(role:.search)` floating glass
circle. Android has no analogue → make Search a normal 5th bottom-nav destination
(or a search icon in the Discover top bar). Documented in RISKS_AND_DECISIONS.

---

## 3. Screens — Signup flow (`feature/signup/`, all **S**, step-driven)

All share one `SignupViewModel` (the `OnboardingStore` port: step machine +
handle-availability debounce + returning-user hydration). Reached when not
signed in. No back stack — `AnimatedContent` on `step`.

**SignupFlow** (S) → `SignupFlow.kt` (container). *Purpose:* switch on `step`,
show hydration-error banner + Retry. *State:* `step`, `mode`, `authNotice`,
`profileHydrationError`. *Lifecycle:* syncs role on change. *Anim:* `AnimatedContent`
crossfade (iOS `.easeInOut(0.25)`). Ships shared chrome: progress dots, primary
button (press-scale), ghost button, back button, underline input.

**WelcomeScreen** (S). *Purpose:* hero + terms gate. *Compose:* radial-gradient
`Box`, wordmark, editorial headline, custom checkbox. *State:* `termsAccepted`.
*APIs:* none. *Gesture:* checkbox tap + selection haptic. *Nav:* "Get started"→signup
order, "I have an account"→login order; terms/privacy `ModalBottomSheet`→Legal.

**RoleScreen** (S). *Purpose:* pick client/artist. *Compose:* two full-bleed
tappable `RolePanel`s (lime/violet gradients, 150dp glyph). *Gesture:* **tap =
commit** → set role, 0.34s delay, advance; a 0.45s appear-debounce blocks
carry-over touches (skip for tests/a11y). *Anim:* `.easeOut(0.24)` select
border/shadow/check, sibling dims. Selection haptic.

**AuthScreen** (S) → Apple/Google/Email. *Purpose:* auth entry. *Compose:* 3 buttons
over an animated `LineupBackground` (two columns scrolling opposite,
`repeatForever`, motion-gated). Apple=solid-white custom button; Google/Email=glass.
*ViewModel/Service:* `SessionManager` (`signInWithApple`/`signInWithGoogle`), Email
opens `EmailAuthScreen` sheet. *State:* `isAuthenticating` overlay, `authNotice`
pill. *Lifecycle:* motion off under reduce-motion.

**EmailAuthScreen** (S). *Purpose:* email/password sign-in/up sheet. *Compose:*
`TextField`/password field, hairline underlines, focus order, submit labels.
*APIs:* `EmailAuth.signIn/signUp`. *State:* client validation; outcomes
signedIn/confirmationRequired/failed. *Anim:* toggle sign-up↔in `0.15`.

**ProfileScreen (signup)** (S). *Purpose:* handle + name + city. *Compose:*
auto-focus handle, live handle indicator (spinner/tick/xmark, underline tint), city
`DropdownMenu`, mono kicker, italic-accent headline, progress segments. *APIs:*
`UsersRepository.handleIsAvailable` (350ms debounce), `upsertSelfProfile`. *State:*
`handleStatus`; handles `.handleTaken`/`.notSignedIn`.

**NotifPermissionScreen** (S). *Purpose:* ask for notifications. *APIs:*
`PermissionsController` → `POST_NOTIFICATIONS` (API 33+) then register FCM.
Both buttons advance.

**DoneScreen** (S). *Purpose:* celebration. *Compose:* `ScoreRing(94)`, serif
"You're in, {firstName}.". *Anim:* spring pop-in checkmark (scale 0.6→1.0).
*APIs:* `Analytics.capture("signup_complete")` → finish. Success haptic.

**LegalScreen** (S). *Purpose:* terms/privacy modal. *Compose:* scrolling
title+body sections, footer link to hosted URL. `enum LegalDoc{terms,privacy}`.

---

## 4. Screens — Artist wizard (`feature/wizard/`, all **A**)

One `WizardViewModel` (the 585-line `ArtistOnboardingStore` port): `flowOrder`
= identity→location→pricing→tech→availability→cover→socials→bio→samples→preview→done,
per-step validation, pending-media handoff to `UploadQueue`. Reached when
artist & !setupComplete. Shared `WizardScaffold` chrome (serif title, subtitle,
back, primary CTA) + a segment progress bar. `AnimatedContent` on `step`
(`.easeInOut(0.2)`).

**WizardScaffold + steps** — each step is a screen; CTA→`advance()`, gated by
validation:
- **Identity** — stage name, @handle (live availability, mono, border by status),
  category grid (`FlowRow` chips), genre. Auto-focus.
- **Location** — base city (required) + event types (`FlowRow` capsules).
- **Pricing** — editable tiers (`LazyColumn` over mutable list): name/duration/₹
  price/popular; trash/add. Price≥1000 to pass.
- **Tech** — tech-rider multi-select `FlowRow` chips (presets).
- **Availability** — days-open + start-times capsule grids.
- **Cover** — video>photo>gradient. **Photo Picker** + **CameraX** (permission
  gate) + `VideoTrimmer` (Media3, ≤10s) + `WizardMediaCache` staging + gallery
  strip + gradient `LazyVerticalGrid`. *Lifecycle:* `LaunchedEffect` ensures artist
  row + loads remote media. Uploads deferred to publish.
- **Socials** — paste Spotify/Instagram/YouTube (`FlowRow` over platform enum).
- **Bio** — ≤200-char multiline field, live counter (warm/hot near cap). Skip/Continue.
- **Samples** — ≤6 audio via **SAF** `OpenDocument` sheet + `WizardMediaCache`,
  per-row title + duration + trash. Skip/Continue.
- **Preview + Publish** — `runPublish()`: upsert artist row (`setup_complete=true`),
  parallel packages+tech write, flip `published=true`, delete stale cover, enqueue
  pending media to WorkManager, →done. Progress overlay.
- **Done** — 3 concentric brand circles + spring checkmark, "You're live.", "Open
  dashboard" → `setupComplete=true` (routes to ArtistTabs).

---

## 5. Screens — Client-facing (`feature/…`)

**DiscoverScreen** (C, tab root) → `feature/discover/`. **Redesigned Sep 2026
(design screens 02 / 59).** *ViewModel:* `DiscoverViewModel` (`DiscoverFeedStore`
port; hero + 4 rails via 5 concurrent `search_artists`, one of them date-scoped
through the 0073 `p_date`). *Compose:* `ScreenHeader` ("Discover" + "City ·
today"), `SearchBarButton`, category `Chip` rail, one 262dp `HeroCard`, then
titled rails of two-up `Tile`s inside a `LazyColumn`; loading is `SkeletonPage`.
The auto-advancing hero pager and the full-bleed status-bar treatment are GONE —
the destination takes the ordinary scaffold insets. *Models:* `Artist`. *APIs:*
`SearchRepository.search` / `.facets`, `ArtistsRepository.cache`. *State:* hero,
rails (each carrying its "See all" `SearchSeedRequest`), categories,
selectedCategory, today, isLoading, loadError. *Nav:* tile/hero→`ArtistProfile(id)`,
search bar and "See all"→Search tab (seeded via `SearchSeed`), header heart→
`artist_list/saved`. *Lifecycle:* pull-to-refresh; the day is re-read per refresh
so an overnight session cannot caption today's roster with yesterday. *Deps:*
SavedStore, SearchSeed.

**SearchScreen** (C, tab root) → `feature/search/`. **Redesigned Sep 2026
(design screens 14 / 03 / 57 / 58).** *ViewModel:* `SearchViewModel` (`SearchStore`
port). *Compose:* `SearchBar` (ink rim while focused) + filter `IconCircle` with an
accent count badge, then ONE of four surfaces. Which one is a two-axis decision:
`hasActiveQuery` says whether there is a search, a local `editing` flag says
whether the cursor is in the field — without the second, screen 14 is unreachable
the moment a character is typed. 14 browse = Suggestions (facet terms with their
real counts, interleaved with acts from the live page) + Recent `FlowRow` chips +
Browse-by-occasion cards; 03 results = derived title/subtitle + active-filter chip
rail + result cards; 57 = `EmptyState` with a guaranteed action; 58 = warm `Banner`
over an `EmptyState`, failure stated twice. *APIs:* `search_artists` /
`search_facets` / `price_histogram`. *State:* filters
(query/city/date+flex/price/score/categories/eventType/services/sort), results,
pagination cursor+generation, recents (DataStore). *Lifecycle:* 280ms debounce;
`SearchSeed` collected so Discover's "See all" can arrive before this VM exists.
*Nav:* card→`ArtistProfile`; filter→`SearchFilterSheet`→`CompareByServiceSheet`.
Every computed string is a pure function in `SearchLabels.kt`, each unit-tested.

**SearchFilterSheet** (C) → `feature/search/`. **Redesigned Sep 2026 (design
screens 15 / 104 — one sheet in two states).** *Compose:* `ModalBottomSheet`
(transparent container, `dragHandle = null`) wrapping `SheetScaffold`: header row
(Clear / title / close circle), the active-filter chip summary with its "N filters
active" line, City / Date / Occasion disclosure rows, a Service row that pushes
`CompareByServiceSheet`, act-type chips, Budget (histogram + range slider) and
Bookability, over a PINNED CTA carrying the count. Closing IS applying, however it
closes. The design's "Must have" toggles are omitted — no PA, verification or
travel columns on `artists`.

**CompareByServiceSheet** (C) → `feature/search/`. **New, design screen 53.**
*Compose:* radio list of `SearchCatalog.services` over a pinned CTA. Radio, not
checkbox: `p_services` is an array-overlap test, so two selections widen the feed.
No per-row counts — `search_facets` publishes none for service tags.

**ArtistProfileScreen** (C, pushed) → `feature/artist/`. *ViewModel:*
`ArtistProfileViewModel`. *Compose:* `GeometryReader`→`BoxWithConstraints` hero
(48% height, `MediaContainer` video>photo>gradient, glass back/save/share,
`ShareLink`→Android share intent, score chip→`ScoreBreakdownSheet`); About (bio
clamp + more/less, gallery strip); **Packages = horizontally-swipeable perforated
ticket cards** (`HorizontalPager`, dashed tear line via `Canvas`, notch cutouts,
lime wash on select) + "Request a quote"→`RequestQuote`; Reviews (stars + italic
serif); disclosure rows (Sound=`SpotifyEmbed` + samples, Tech rider, Social→open
URL). **Glass dock** (`Scaffold` bottomBar): Message (async open chat→`Chat`) +
"Check availability"→`Booking`. *Models:* `Artist`, `ArtistMediaItem`, `SampleRow`,
`Review`, `ScoreBreakdown`. *APIs:* ArtistMedia/ArtistLinks/Samples/Reviews/Score
repos. *Lifecycle:* `LaunchedEffect(artistId,userId)` fires 4 parallel loads with
cancel guards. *Deps:* SavedStore, SessionManager, MessageStore.

**BookingScreen** (C, `Booking`) → `feature/booking/`. *ViewModel:* `BookingViewModel`
(`BookingStore.draft`). *Compose:* package radio picker, `DateScroller` (real
`daysAvailable`, busy dims), time `LazyVerticalGrid` 3-col (preferred slots),
venue field + guests stepper (10–5000/10), summary (fee only — v1 hides fees).
CTA→`Checkout`. *Lifecycle:* `LaunchedEffect(artistId)` ensures artist + `startDraft`.

**RequestQuoteScreen** (C, `RequestQuote`) → `feature/booking/`. *Compose:*
`DateScroller`, budget (mono ₹, numeric, required), optional message/venue/guests.
CTA (gated amount>0)→`RequestsRepository.create` (7-day expiry). Inline error;
success state→dismiss.

**CheckoutScreen** (C, `Checkout`) → `feature/booking/`. *ViewModel:*
`CheckoutViewModel`. *Compose:* summary card, confirm-match button →
`SubscriptionService`/`Payments` seam → `confirmDraftAsBooking` →`Confirmed`.
v1 quota gate → `PaywallScreen` sheet. *APIs:* create-booking path. *State:*
retry banner on payment-ok/write-fail. Analytics `booking_created`/`booking_paid`.
Success/error haptics. *Deps:* EntitlementStore.

**ConfirmedScreen** (C, `Confirmed`) → `feature/booking/`. *Compose:* spring halo +
checkmark, italic "Match confirmed", details card, `StatusTimeline`, actions:
View booking (pop-to-root + `pendingBookingDetail` + switch tab), `AddToCalendar`,
Back to discover. *Anim:* `LaunchedEffect` spring scale.

**BookingsScreen** (C, tab root) → `feature/bookings/`. *ViewModel:*
`BookingsViewModel`. *Compose:* `MonthCalendar` (booked=lime, `eventsForDay`,
`onSelectEvent`→`BookingDetail`) + per-day schedule rows tinted by status; error
banner. *APIs:* `BookingsRepository.listForClient`. *Lifecycle:* refresh on user id;
consume `pendingBookingDetail` deep link; pull-to-refresh.

**BookingDetailScreen** (S, client primary / artist via injected booking) →
`feature/bookings/`. *Compose:* artist header, `StatusTimeline`, KV rows +
`HRule`, bold fee, action row: Message→`Chat`, Add to calendar (confirmed),
Cancel (`AlertDialog`→`cancel`), Leave review (completed→`ReviewSheet`). *Lifecycle:*
consume `pendingReviewSheet` (auto-present). *Deps:* BookingStore, MessageStore.

**ArtistListScreen** (C, pushed, `artist_list/{kind}`) → `feature/profile/`.
**Redesigned Sep 2026 (design screens 32 / 112).** One screen, three row sources
— Bookings / Saved / Completed — with the kind chips as permanent navigation
(switching REPLACES the destination rather than stacking it). *ViewModel:*
`ArtistListViewModel`. *Compose:* `BackHeader` with a counted subtitle, the kind
rail, a within-list category rail derived from the rows themselves, then rows
(56dp cover, name + `TrustedTick`, act line, accent score chip, "from ₹x").
*State:* rows, selectedCategory (dropped when a reload no longer contains it),
isLoading, error. `SavedStore.refreshFromServer()` now reports whether the SERVER
copy was read, so a dropped connection renders as "couldn't load" rather than as
"Nothing saved yet". *Deps:* SavedStore, BookingsRepository, ArtistsRepository.

**ProfileScreen** (C, tab root) → `feature/profile/`. *Compose:* header card
(`Avatar` 64, "City · Role since YYYY"), 3-col stats, saved carousel→`ArtistProfile`,
settings hairline rows: Notifications (system settings), Privacy (`LegalScreen`),
**Export data** (`DataExportScreen`, DPDP), **Calendar sync** (toggle + target
`DropdownMenu`, `CalendarSyncService`), Help (mailto), **Sign out** (`AlertDialog`→
`signOut` + wipe prefs + reset stores + role→client), **Delete account** ("DELETE"
confirm field → `delete-account`). *Deps:* all per-user stores, SessionManager,
CalendarSyncService.

---

## 6. Screens — Artist-facing (`feature/…`)

**ArtistHomeScreen** (A, tab root) → `feature/artisthome/`. *ViewModel:*
`ArtistHomeViewModel`. *Compose:* greeting bar, earnings `Sparkline` + range
toggle (7D/30D/ALL) + delta pill (truthful empty state), bookability card
(`ScoreRing` + metric bars)→`ScoreExplainer`, 14-day availability strip
(`LazyRow` booked/busy/open from bookings + `CalendarSyncService.busyDays`,
MANAGE→`ManageAvailability`), gig-requests list→`GigRequest`, upcoming gigs.
`UploadProgressBanner`. Subscribe banner→`Paywall` (gated). *APIs:* Artists/
Bookings/Score repos, RequestStore. *Lifecycle:* refresh on user id (parallel);
`pendingGigRequestId` deep link; pull-to-refresh. *Deps:* EntitlementStore,
CalendarSyncService.

**ArtistGigsScreen** (A, tab root) → `feature/gigs/`. *Compose:* `MonthCalendar`
(booked days, events, select→`BookingDetail` via injected booking). *APIs:*
`BookingsRepository.listForArtist`. *Lifecycle:* refresh on user id;
pull-to-refresh; silent on failure.

**GigRequestDetailScreen** (A, `GigRequest`) → `feature/gigrequest/`. *Compose:*
sticky `CtaScrim` action bar (when open): Decline (`AlertDialog` + haptic),
Counter (`ModalBottomSheet` ₹ field), Accept (haptic→`requestStore.accept`);
calendar-clash card (`CalendarSyncService.clashes` top-2 + "+N more"); error
banner. *APIs:* RequestsRepository. *Lifecycle:* `LaunchedEffect(request.date)`
clash read.

**EpkScreen** (A, "Profile" tab root) → `feature/epk/`. *ViewModel:* `EpkViewModel`.
*Compose:* cover (video>photo>gradient + 6-preset picker), photos 3-col
`LazyVerticalGrid`, samples (≤6, SAF→immediate `SamplesRepository.upload`), social
rows, read-only bio, pricing tiers (1.2s debounce→`PackagesRepository.replaceAll`),
tech rider `FlowRow`, custom links (`EditArtistLinkSheet` CRUD), share-link card
(copy→clipboard "COPIED ✓"). *APIs:* Packages/ArtistMedia/Samples/ArtistLinks/
Artists repos. *Lifecycle:* 4 parallel loads on user id (cancel-guarded); reload
on `UploadQueue.batchCompleted`. *Deps:* EPKStore, UploadQueue.

---

## 7. Screens — Shared / cross-role

**MessagesScreen** (S, tab root, both roles) → `feature/messages/`. *ViewModel:*
`MessagesViewModel` (`MessageStore` port). *Compose:* `LazyColumn` thread rows→
`Chat(id)`: `Avatar` 44, role-resolved counterpart name, BOOKING pill, timeAgo,
2-line preview (verbatim body — **no** redaction), unread dot. *APIs:* `listThreadsForUser`. *State:*
threads (badge = Σ unread). *Lifecycle:* two-stage hydrate (artists, then names);
`pendingThreadId` deep link; pull-to-refresh; skeleton/empty/error. *Deps:*
SessionManager, RoleStore, BookingStore, ArtistsRepository.

**ChatScreen** (S, `Chat`) → `feature/messages/`. *ViewModel:* `ChatViewModel`.
*Compose:* `LazyColumn` (reverse) with `rememberLazyListState` auto-scroll to
bottom on new message; bubbles system(centered)/me(brand)/other(card), **Airbnb
trust banner** (not redaction), failed-send retry chip + haptic; **composer**
multiline field + send,
glass floating bar (`Scaffold` bottomBar). *APIs:* `MessagesRepository`
(listMessages explicit columns, send, **realtime** `subscribeMessages`,
markThreadRead, findOrCreateThread). *Lifecycle:* `LaunchedEffect(threadId,userId)`
= ensure/refresh/resolve-name/markRead/**subscribe realtime** (gated), foreground
reconnect (`lifecycle`/scenePhase), teardown on dispose + generation bump. *Nav:*
client's toolbar avatar→`ArtistProfile`. *Deps:* SessionManager, RoleStore,
BookingStore.

**PaywallScreen** (S, sheet) → `feature/paywall/`. *ViewModel:* uses
`EntitlementStore`. *Compose:* close-x, role hero + editorial headline, perks,
price card (`product.displayPrice` + period + intro offer + auto-renew terms +
T&C links), subscribe CTA (spinner/"Waiting for approval…"), Restore. *APIs:*
Play Billing (dormant). *Lifecycle:* re-pull products if empty. *State:* purchase
outcome→onComplete + dismiss.

**ScoreExplainerScreen** (A, `ScoreExplainer`) → `feature/score/`. *Compose:* hero
`ScoreRing(120,stroke 7)` or NEW pill; tiers table; 5 weighted metric rows
(Show-up 30% / Reviews 25% / Reply 20% / Cancellations 15% [flipped] / Social 10%)
with clamped bars. *APIs:* `ScoreRepository.breakdownForSelf`. *State:*
breakdownError + Retry. *Nav:* "View history"→`ScoreHistorySheet`.

---

## 8. Screens — Sheets & settings

**ReviewSheet** (C) → `feature/score/` or `feature/bookings/`. *Compose:*
`ModalBottomSheet`, 1–5 star rating (tap + haptic), `TextField` ≤200 counter,
Cancel/Submit (disabled until rating≥1, spinner, dismiss-guard). *APIs:*
`ReviewsRepository.insert`. Success haptic.

**ScoreBreakdownSheet** (C) → `feature/score/`. *Compose:* `ModalBottomSheet`
(medium/large detents → partial/full), `ScoreRing(64,stroke 5)` or NEW pill,
metric rows + `HRule`; reply speed inverted to "~Xm/~Xh/~a day"; translucent bg.
Data passed from ArtistProfile.

**ScoreHistorySheet** (A) → `feature/score/`. *Compose:* `Sparkline` (88dp) +
delta badge (up/down/flat, colored). *APIs:* `ScoreRepository.historyForSelf`.
*State:* fetchError; empty/success branches; cancel-guarded.

**DataExportScreen** (S) → `feature/profile/`. *Compose:* single "Export my data"→
`AccountService.exportData` → write temp JSON → Android **share sheet**
(`ACTION_SEND`). *State:* `ExportStatus` (stable a11y token); validates signed-URL
200–299. *Lifecycle:* cancel on dispose.

**ManageAvailabilityScreen** (A) → `feature/availability/`. *Compose:* two `FlowRow`
chip grids (days/times) + light haptic, live "HOW CLIENTS SEE YOU" preview pill,
bottom save bar (spinner + "Saving…"). *APIs:* `fetchSelfAvailability` /
`updateAvailability`. *Lifecycle:* seed on user id.

---

## 9. Component inventory → Compose (`designsystem/component/`)

### Platform-bridge components (need Android APIs)
| iOS component | iOS API | Compose target |
|---|---|---|
| `AutoplayVideoView` | AVPlayer/AVPlayerLayer (muted loop) | Media3 **ExoPlayer** in `AndroidView`/`PlayerSurface`, `REPEAT_MODE_ONE`, muted, pause off-screen |
| `SpotifyEmbedView` | WKWebView (`/embed`) | `WebView` in `AndroidView`, `mediaPlaybackRequiresUserGesture=false` |
| `CameraPicker` | UIImagePickerController(.camera) | **CameraX** or `ActivityResultContracts.TakePicture`/`CaptureVideo` |
| `AudioDocumentPicker` | UIDocumentPicker (audio) | `ActivityResultContracts.OpenDocument(["audio/*"])` |
| `AddToCalendarButton` | EKEventEditViewController | `Intent(ACTION_INSERT, Events.CONTENT_URI)` (permission-free) |
| `MediaContainer` (+scrim) | AsyncImage/video/gradient layers | `Box` + Coil `AsyncImage` + ExoPlayer + gradient `Brush` |
| `MediaSourcePickerSheet` | sheet of option rows | `ModalBottomSheet` |

### Custom-drawing components (Compose `Canvas`/custom `Layout`)
| Component | Draw | Compose |
|---|---|---|
| `ScoreRing`(+`ScoreNum`) | progress arc + track + mono numeral/NEW | `Canvas` `drawArc` (start top, round cap) + `Text`; `animateFloatAsState` |
| `Sparkline`(+`MiniBars`) | line path + gradient area + endpoint | `Canvas` `Path` + `drawPath` fill + marker |
| `MonthCalendar` | Apple-style month grid + schedule | `LazyVerticalGrid`/custom + `RoundedRect` tiles + status dots; month `DropdownMenu` |
| `MiniMonthCalendar` | 7-col grid, today ring | custom grid (kept for tests) |
| `DateScroller` | horizontal date cells + availability dot | `LazyRow` cells + `spring` select |
| `StatusTimeline`(+step) | 4-step vertical timeline | `Column` of circle+connector `Canvas` |
| `Avatar` | initials/image + hue-from-hash + ring | `Box` + Coil + generated `Brush` |
| `ArtistTile` | photo card + pills + gradient scrim | `Box` + `AsyncImage` + overlays |
| `Skeleton`(+variants) | shimmer sweep | `Modifier` shimmer (`drawWithCache` + `animateFloat`) |

### Structural / control / feedback
| Component | Compose |
|---|---|
| `PrimaryButton` (variant×size×fullWidth, press-scale) | `Button`/`Surface` + `Modifier.pressScale()` (`animateFloatAsState` 0.98) |
| `Pill` (tone×size) | `Surface`/`Box` capsule + `Text`/icon |
| `CardView` / `AppSection` / `HeaderBar` / `CtaScrim` | container composables; `CtaScrim`→translucent bottom bar |
| `KVRow` / `HRule` | `Row` key/value; `HRule`→`Divider`/`HorizontalDivider` (1dp `line`) |
| `EmptyStateView` | centered icon+title+sub+CTA |
| `EditArtistLinkSheet` | `ModalBottomSheet` form |
| `Toast`(`ToastCenter`) | app-wide overlay via a `SnackbarHost`-style host or custom top overlay + `AnimatedVisibility` |
| `UploadProgressBanner`(+`FailedUploadsSheet`) | top banner bound to `UploadQueue` state + `ModalBottomSheet` |
| `FlowLayout` (inline, not in Components/) | Compose **`FlowRow`** (built-in) |

---

## 10. SwiftUI → Compose API reference (no-direct-equivalent cases)

| SwiftUI | Compose / Android |
|---|---|
| `NavigationStack(path:)` + `navigationDestination` | `NavHost` + typed `composable<Route>` |
| `TabView` + `Tab` (iOS-26 glass, `.search` role, `.tabBarMinimizeBehavior`) | `Scaffold` + `NavigationBar`; **no glass/minimize/search-circle analogue** → standard Material 3 |
| `.sheet` / `.fullScreenCover` / `.presentationDetents` | `ModalBottomSheet` (partiallyExpanded/expanded) / full-screen destination |
| `@EnvironmentObject` / `@StateObject` / `@Published` | Hilt-injected ViewModel + `StateFlow` + `collectAsStateWithLifecycle` |
| `.task(id:)` / `.onAppear` / `.refreshable` | `LaunchedEffect(key)` / `DisposableEffect` / `PullToRefreshBox` |
| `.onChange(of: scenePhase)` | `LifecycleEventEffect` / `repeatOnLifecycle` |
| `.onOpenURL` (deep link) | manifest `intent-filter` + `NavController.handleDeepLink` |
| `AsyncImage` | Coil `AsyncImage` |
| `ShareLink` / `UIActivityViewController` | `Intent(ACTION_SEND)` chooser |
| `.ultraThinMaterial` / iOS-26 `.glassEffect` | translucent Material 3 surface (+ `RenderEffect` blur, API 31+) |
| `@FocusState` / `.submitLabel` | `FocusRequester` + `KeyboardOptions(imeAction=…)` |
| haptics (`UISelectionFeedbackGenerator`) | `HapticFeedback` / `View.performHapticFeedback` |
| `matchedGeometryEffect` (if used) | `AnimatedContent`/`SharedTransitionLayout` (Compose 1.7+) |
| `Timer.publish` (carousel) | `LaunchedEffect { while(true){ delay(); … } }` |
| `.spring`/`.easeInOut`/`.easeOut` | `spring()`/`tween(easing=…)` in `animate*AsState`/`AnimatedContent` |
| `NumberFormatter(en_IN)` | `NumberFormat.getInstance(Locale("en","IN"))` (pure util) |
| Dynamic Type (`relativeTo:`) | Compose honors `fontScale` automatically |
