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
  `BookingDetail(bookingId)`, `Chat(threadId)`, `Search`,
  `artist_reviews/{artistId}`, `bookability/{artistId}` (both added Sep 2026 by
  section AP).
- **ArtistRoute:** `GigRequest(id)`, `ScoreExplainer`, `score_history` (Sep 2026,
  AP — the ledger moved from a sheet to a pushed screen).

`ArtistProfile(id)` routes through an **`ArtistRouteLoader`** (skeleton →
`ArtistsRepository.ensureFull(id)` fetch-on-miss → screen or not-found), mirroring
iOS. **Deep links** flow through a `DeepLinkRouter` (SharedFlow of nav events +
`pending*` ids) fed by FCM taps — the `TabRouter` analogue.

**Client "search circle".** iOS uses the iOS-26 `Tab(role:.search)` floating glass
circle. Android has no analogue → make Search a normal 5th bottom-nav destination
(or a search icon in the Discover top bar). Documented in RISKS_AND_DECISIONS.

---

## 3. Screens — Getting started (`feature/signup/`, all **S**, step-driven)

Re-cut Sep 2026 against the light design's **GS** section (screens 01 · 11 · 12 ·
13 · 27 · 28 · 29 · 30 · 31 · 62 · 71 · 90 · 114 · 118 · 119). All share one
`SignupViewModel` (the `OnboardingStore` port: step machine + handle-availability
debounce + returning-user hydration) and one `AuthViewModel` (session calls + the
one-time-code state). No back stack — `AnimatedContent` on `step`.

Shared chrome lives in `SignupChrome.kt`: `SignupScaffold` (header band, gutter
body, pinned CTA bar that owns the navigation and IME insets), `SignupHeader`,
`SignupProgressStrip`, `ConsentCheckbox`, `AppMark`, `InlineLink`,
`SignupEyebrow`, `HydrationErrorBanner`.

**SplashScreen** (01). *Purpose:* the one dark room. *Compose:* accent wordmark,
media well under the standard bottom scrim, headline. *Colour:* `darkest` — the
same value the launch window is painted in, so the pre-Compose hand-off has no
seam. *Nav:* rendered by `ArtistantNavHost` on `RootGate.Loading`; carries no
actions, because the gate has not yet decided which surface follows.

**WelcomeScreen** (118). *Purpose:* consent gate. *Compose:* `AppMark`, headline,
consent card with `ConsentCheckbox`, inline reason under the disabled CTA.
*State:* `termsAccepted`, plus an optional caller-supplied `blockedReason`.
*Nav:* "Get started"→signup order, "I already have an account"→login order;
Terms/Privacy→`LegalScreen` in a `ModalBottomSheet`.

**CommunityCommitmentScreen** (27). *Purpose:* the pledge, shown once. *Compose:*
four numbered `surface3` cards, a required tick, "Shown once" footnote.
*State:* `SignupConsentStore.communityAgreed` gates the role step.

**RoleScreen** (11 · 71). *Purpose:* pick client/artist. *Compose:* two cards —
accent tint + ring + filled radio when selected — a `BannerTone.Note` aside, and
a pinned Continue. *Gesture:* **select on tap, move on Continue**. 71 is the same
screen with `HydrationErrorBanner` above the title.

**SignupAuthScreen** (12). *Purpose:* sign in. *Compose:* phone field (`+91`
leading, `IN +91` trailing, `PhoneRules.error` inline underneath), "Or use email"
field, "Send code", an `or` rule, then Apple / Google / password rows. *APIs:*
`AuthGateway.sendPhoneOtp` / `sendEmailOtp`, `signInWithApple` /
`signInWithGoogle`. Phone wins when both fields hold something valid. *Rules:*
`PhoneRules` accepts three shapes only — 10 digits, `91`+10, `+91`+10 — and
refuses anything longer instead of taking its last ten digits. On LOGIN the send
passes `createUser = false`; the refusal that comes back is rendered as an offer
("No account for this number — create one?") landing on the welcome screen, which
is where the terms tick is.

**EnterCodeScreen** (119). *Purpose:* verify. *Compose:* `OtpField` (one real
field behind six drawn boxes, so SMS autofill has somewhere to land), resend
countdown, "Change number", autofill note, "NOT ARRIVING?" block. *State:*
`OtpResend` — 30s cooldown, email escape after two sends. *APIs:*
`verifyPhoneOtp` / `verifyEmailOtp`; the gate advances the flow from the session.
Every exit calls `AuthViewModel.clearOtp()` — the two on this screen, plus the
system back gesture, which `SignupFlow` takes because it never reaches a callback
here. The VM is activity-scoped, so without it the spent send count, the running
cooldown and the typed digits followed the user to the next number.

**EmailSignUpScreen** (28). *Purpose:* the reviewable path. *Compose:* name /
email / password with Show, the 8-character new-account rule stated with a tick,
the "already have an account" note, "Forgot password?". *APIs:*
`AuthViewModel.submitEmailAuth` → `signInWithEmail`, falling through to
`signUpWithEmail` only when nothing matched the credentials; `sendPasswordReset`.
Submit opens at GoTrue's 6 characters so an older account can still be opened
with its own password, and the 8 is enforced on the create branch. A modal over
the auth step (`emailSignUp`), not a step of its own.

**ProfileScreen** (29 · 90). *Purpose:* handle + name + city. *Compose:* handle
field with a status ring and a four-state chip, `HandleSuggestions` chips when
taken, name, city picker, the live-check note; the header carries the only
progress strip in the flow ("04 / 06"). *APIs:*
`UsersRepository.handleIsAvailable` (350ms debounce), `upsertSelfProfile`.
*State:* `Empty/Invalid/Checking/Available/Taken/Error` — `Error` reads
"Couldn't check" and never the tick.

**NotifPermissionScreen** (13). *Purpose:* ask with a reason. *Compose:* accent
glyph tile, "Quotes expire. We'll tell you first.", three kinds. *APIs:*
`POST_NOTIFICATIONS` (API 33+) then register FCM. Both buttons advance.

**DoneScreen** (30). *Purpose:* end on the score. *Compose:* accent check, "You're
in, {city}.", the Bookability Score primer. *Anim:* spring pop-in (0.6→1.0).
*APIs:* `Analytics.capture("signup_complete")` → finish. Success haptic. *Gap:*
the design's "412 acts play your city" has no count endpoint, so the number is
omitted rather than invented.

**LegalScreen** (31 · 114). *Purpose:* one viewer, two documents. *Compose:*
segmented Terms/Privacy, eyebrow-and-body sections, footer row out to the hosted
copy (which is the authoritative one). `enum LegalDoc { Terms, Privacy }`.

**PrivacyScreen** (62). *Purpose:* the switches that are switches, and the lines
that aren't. *Compose:* one switch row (read receipts; M3 `Switch` repainted in
tokens), a switch-shaped row of plain text for the city, Privacy-policy and
Data-export rows, the "that isn't a setting" footer. *State:* `PrivacyPreferences`
— DataStore under `privacy.read_receipts`, because the setting has no column in
the canonical schema; `feature/messages` reads it before `mark_thread_read`.
*Gap:* the design's city switch is not drawn as a control. `users.city` has no
visibility column, and a device flag cannot hide a value the server hands to
everyone who opens the profile — so the row states who sees it and that this
version does not adjust it. A visibility column is a schema change and starts in
the iOS repo.

---

## 4. Screens — Artist wizard (`feature/wizard/`, all **A**)

**Redesigned Sep 2026** against "Artistant iOS Light" screens 37, 38, 24, 39,
40, 41, 42, 43, 44, 45, 46 and 72 — see `docs/REDESIGN_2026-09.md`.

One `WizardViewModel` (the `ArtistOnboardingStore` port): `flowOrder` =
identity→location→pricing→tech→availability→cover→socials→bio→samples→preview→done,
per-step validation, pending-media handoff to `UploadQueue`, and a debounced
draft (`WizardDraftStore`) that restores across process death. Reached when
artist & !setupComplete.

**Chrome** (`WizardScreen`, `WizardScaffold`) — a back circle, a ten-cell
progress track and a "03 / 10" counter across the top; the step's own
`LazyColumn` under a plain 26/700 question; one pinned CTA on an opaque bar with
a hairline over it. Filled segments mean FINISHED, not reached, and the counter
and the Save & exit sheet repeat that same arithmetic. `AnimatedContent` on
`step`, fade only. The design's flow has eleven steps; ours has ten, and the
total is derived from `WizardFlowOrder` rather than typed.

**Save & exit (design 72)** — a `ModalBottomSheet` over `SheetScaffold`, not an
AlertDialog. It states how many steps are banked and that staged media is on
disk rather than in memory, then signs out keeping the draft. Reachable from
every step: it is the leading circle on step one (where there is nothing to go
back to) and a second close beside the counter after that.

**Steps** — each is a screen; CTA→`next()`, gated by `wizardCanAdvance`:
- **Identity (37)** — stage name, @handle (live availability, tick / cross,
  public address under the field), category chips, genre. The category's seeded
  pricing band is stated here, on the step that picks it. Genre stays a text
  field: the design draws chips, but `artists.genre` is free text with no
  vocabulary to constrain it to.
- **Location (38)** — base city (required, published), travel radius, event
  types. The last two are held in the draft only — there is no radius column and
  no `event_types` writer in this client — and the step's banner says so.
- **Pricing (24)** — editable tiers over `LazyColumn`: name / duration / ₹ fee /
  "most booked", and under a hairline the ALL-IN the host pays, derived through
  `BookingMath` so the wizard and the checkout cannot disagree. The design's
  market-rate line is replaced by the band we seeded, because there is no market
  aggregate on this backend.
- **Tech (39)** — the seven presets as checkable rows, anything typed as chips.
  No rider PDF slot: `tech_rider` stores text rows.
- **Availability (40)** — a seven-letter day strip plus start-time chips,
  compressed into the one badge a search row has space for and shown while still
  editable (`availabilityBadge`). No "notice you need" — no column for it.
- **Cover (41)** — a 3:4 slot, a Camera / Library pair, and the gradient that
  stands in when there is no photo. A denied camera permission routes to the
  system settings page rather than leaving a dead button. Photo only.
- **Socials (42)** — paste fields for Instagram / Spotify / YouTube. The design
  prefers OAuth; there is none on this backend, so the banner says plainly that
  the links are not verified.
- **Bio (43)** — ≤200-char multiline field, live counter (warm then danger near
  the cap) and `bioGuidance` saying what good looks like at that length, plus
  the service-tag picker (published via `updateServiceTags`).
- **Samples (44)** — ≤6 audio via **SAF** `OpenDocument` + `WizardMediaCache`,
  per-row title and duration. Upload state is READ off `UploadQueue` — queued /
  uploading / failed with a retry — because the queue is the only thing that
  survives a kill and knows.
- **Preview (45)** — centred back header; the cover, the identity line and seven
  rows, each stating its value with an inline Edit that jumps back to the owning
  step. `publish()`: upsert artist row, parallel packages + rider + service
  tags, flip `published`, enqueue pending media, →done.
- **Done (46)** — accent check, "You're live.", the copyable public address, and
  the New-tier expectation off `ScoreBands`. "Open my dashboard" →
  `setupComplete=true` (routes to ArtistTabs).

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

**ArtistProfileScreen** (C, pushed) → `feature/artist/`. **Redesigned Sep 2026 —
section AP, design screens 04 / 54 / 55 / 101 / 103.** *ViewModel:*
`ArtistProfileViewModel`. *Compose:* `BackHeader` + "···" (a sheet, not a
dropdown: Save / Share / Report); identity block (96dp round portrait on the
artist's own cover gradient, name, `ArtistProfileFacts.subtitle`, accent rating
pill computed off the same review list the section renders); ruled three-cell
stat strip (Shows · Bookability · Replies in — the middle cell opens
`ScoreBreakdownSheet`); About with a measured More/Less; **Packages replace the
rate card** (quiet accent-hairline selection seeding `BookingDraftStore`) + a
"Request a quote" row; Gallery strip; Listen (`SampleRow` + `SpotifyDisclosure`,
or screen 101's redirect card when there is no audio); "Most clients ask about"
(prompts); "What they play" (service tags); Reviews (preview + "See all"→
`ArtistReviewsScreen`, or screen 100's scoped failure). **Dock:** Message circle
+ a single accent CTA carrying the price ("Check availability · ₹26,000",
`PackagePricing.dockPrice`). *States:* 54 skeleton with **no navigation bar**;
55 not-found naming the cause with a route to Discover (a failed READ gets Retry
instead); 103 self view (`ViewerIdentity`) swaps the verbs and drops the booking
controls rather than letting them fail against the self-booking guard.
*Models:* `Artist`, `GalleryPhoto`, `Sample`, `Review`, `ScoreBreakdown`,
`ReportOutcome`. *APIs:* Artists/Reviews/Score/Reports repos. *Deps:* SavedStore,
ViewerIdentity, BookingDraftStore.

**BookingScreen** (C, `Booking`) → `feature/booking/`. **Redesigned — screen 05
"When's the show?" (BC).** *ViewModel:* `BookingViewModel`. *Compose:*
`FunnelStepBar` "Step 1 of 2", 26sp question, `FunnelCalendar` month grid (opens
on the first month with an open day; closed days dim and inert; back-step floors
at the current month), `PackageChoiceRow` list, start-time chips, venue field +
guests stepper (10–5000/10), bounded notes with a live counter. Dock: summary
line + fee, CTA "Request this date"→`Checkout`. *Pure logic:* `funnelMonthDays`,
`monthSelectableDays`, `firstOpenMonth`, `steppedMonth`, `dayOfMonthIfIn`,
`isAfterCurrentMonth`, `bookingSummaryLine`.

**RequestQuoteScreen** (C, `RequestQuote`) → `feature/booking/`. **Redesigned —
screen 17 "the brief, prefilled" (BC).** *Compose:* occasion chips, Date/Start
picker fields opening `FunnelCalendar` / slot sheets, guests + venue, budget
(numeric ₹, required), 500-char note with counter. CTA (gated amount>0)
→`RequestsRepository.create` (7-day expiry). *Data:* occasion and start time have
no `gig_requests` column and are composed into the message by `quoteBriefMessage`;
budget is one amount, not the design's range.

**CheckoutScreen** (C, `Checkout`) → `feature/booking/`. **Redesigned — screen 06
"no money in v1" (BC).** *ViewModel:* `CheckoutViewModel`. *Compose:* act card
(`checkoutActMeta` carries date/time/venue), "Your request" term rows, artist-fee
row, accent `NoteBlock` on the no-payment terms, "What happens next" card. CTA
→`SubscriptionService`/`Payments` seam → create → `Confirmed`. v1 quota gate →
`PaywallScreen`. *State:* retry banner on payment-ok/write-fail; narrated wait
covers the two-hop submit. Success/error haptics. *Deps:* EntitlementStore.

**ConfirmedScreen** (C, `Confirmed`) → `feature/booking/`. **Redesigned — screen 07
"say the outcome, not 'success'" (BC).** *Compose:* `OutcomeMark` (spring tick),
headline branching on status (`confirmedHeadline`), terms card (`confirmedTerms`),
"See the record"→`Invoice`. Actions: pending → View booking / Back to discover;
confirmed → Message the artist (`ChatOpenViewModel`) / Add to calendar.

**MatchConfirmedScreen** (C, `MatchConfirmed`) → `feature/booking/`. **New — screen
94 (BC).** Route `match_confirmed/{bookingId}`; the landing for a match reached
by chat negotiation. Outcome mark, "It's a match. You're both in.", act card with
a `Confirmed` badge, package + fee, frozen-terms note (mig 0096). CTA "View the
booking" + "Back to discover".

**InvoiceScreen** (C, `Invoice`) → `feature/booking/`. **New — screen 132 (BC).**
Route `invoice/{bookingId}`; reachable from Confirmed, and from Booking detail
once BN wires it. "A record, not a tax invoice": billed-to card + status pill,
booking terms, fee / Artistant's ₹0 / total = fee (`invoiceLines` never prints
the persisted `platform_fee_inr` or `gst_inr` as charges), disclaimer note. CTA
shares plain text, not a PDF.

**CounterOfferScreen** (A, `CounterOffer`) → `feature/booking/`. **New — screen 61
(BC).** Route `counter_offer/{requestId}` on the ARTIST graph — `gig_requests`
has one UPDATE policy (`gig_requests_update_artist`, mig 0002), so a client-side
counter is blocked on the backend. Sheet-styled destination: their offer above,
a 60dp amount well below, `counterDeltaLine` under it, note, "Send counter"
→`RequestsRepository.counter`.

**BookingsScreen** (C, tab root) → `feature/bookings/`. **Redesigned Sep 2026 —
designs 10 / 89 / 122.** *ViewModel:* `BookingsViewModel`. *Compose:*
`ScreenHeader` + a calendar circle → `month_calendar`; an Upcoming ⁄ Past
segmented control; then one of three affordances per row (`affordanceFor`) —
a confirmed picture card with a countdown badge and Message ⁄ Tech rider, an
awaiting row that names what it is waiting on, a played row carrying "Leave a
review". Empty (89) puts a dismissible "Add your name" nudge ABOVE the empty
state. Offline (122) renders a DataStore snapshot of the night's essentials.
*APIs:* `BookingsRepository.listForClient`, `UsersRepository.fetchSelfProfile`
(the nudge's only trigger). *Lifecycle:* refresh on init; every success writes
the snapshot, every failure reads it back.

**MonthCalendarScreen** (C, pushed — design 78) → `feature/bookings/`. The
shared calendar as its own destination, off the Bookings header. `MonthCalendarCard`
(header + weekday row + grid + legend) over the selected day's `DayEventRow`s.
Cancelled bookings keep their place in the day list but take no lime tile.

**BookingDetailScreen** (S, client primary / artist via the same route in its own
graph) → `feature/booking/`. **Redesigned Sep 2026 — designs 18 / 83 / 84 / 95 /
96 / 97 plus the cancel flow 117 → 52.** Five variants (`variantFor`), each a
different page: confirmed (run of show → getting there → the fee → Tech rider ⁄
Share ⁄ Cancel), awaiting (progress → what you asked for → Withdraw ⁄ Message),
cancelled (who and when → frozen terms → Message ⁄ Book again), disputed (the
policy, plus an honest "not in the app" for the event history), read-only
(everything visible, every action disabled, Update Artistant). Not-found offers
both explanations. *Deps:* `BookingDetailViewModel`, `ChatOpenViewModel`,
`ReviewSheetViewModel`.

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
settings hairline rows: Notifications (system settings), Privacy (pushes
`PrivacyScreen` on both graphs; the hosted policy is one tap further in, from
that screen's own Privacy-policy row),
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

**EpkScreen** (A, "Press kit" tab root) → `feature/epk/`. *ViewModel:*
`EpkViewModel`. **Redesigned Sep 2026 (design 23 / 87 / 76) — a HUB, not a
form.** *Compose:* `ScreenHeader` "Press kit" + "N% complete" + account gear;
`EpkQueueBanner` (76 working / 66 stalled, batch progress — supabase-kt reports no
byte counter, so no per-file %); `EpkCompletionMeter` (bar + the sentence that
says what to do next); `EpkCoverBlock` (`MediaSlot` filled / `DashedSlot` empty);
`EpkGalleryStrip` (three cells, fixed); six `EpkSectionListRow`s stating the FACT
when filled and the EFFECT when not — or `EpkInvitationRow`s on a bare kit (87).
Rows open a sheet (`EpkSheets.kt`: 65 add cover, 67 bio+services, 68 personality,
74 link, 75 audio, 66 stalled) or a pane (`EpkPanes.kt`: gallery, samples,
packages, tech, links+socials). Panes are in-screen with a `BackHandler`, not
NavHost destinations — one ViewModel, one write queue. *APIs:* Packages/
ArtistMedia/Samples/ArtistLinks/Artists repos. *Lifecycle:* 4 parallel loads on
user id (cancel-guarded); reload on `UploadQueue.batchCompleted`; ON_STOP flushes
the debounce. *Deps:* EPKStore, UploadQueue.

---

## 7. Screens — Shared / cross-role

**MessagesScreen** (S, tab root, both roles) → `feature/messages/`. *Design:* 19
(loaded) / 110 (empty). *ViewModel:* `MessagesViewModel` (`MessageStore` port).
*Compose:* `ScreenHeader` + archive `IconCircle` (dot when non-empty), v2
`SearchBar`, v2 `Chip` rail with live counts, the **permanent Artistant Support
row** (dark disc + lime "A", every segment and every state), then `LazyColumn`
thread rows→`Chat(id)`: `Avatar` 48, role-resolved counterpart name, star marker,
timeAgo, **deal state** (a live `gig_requests` quote renders "QUOTE ₹48,000 ·
holds till Fri" in place of the preview; lapsed says so) and an accent unread
count badge. Swipe archives. *APIs:* `listThreadsForUser`, `fetchMany`,
`listForClient`/`listForArtist`. *State:* threads; `activeThreads` (archived
excluded) is the ONLY source of every count. *Lifecycle:* two-stage hydrate
(artists, then names); `pendingThreadId` deep link; pull-to-refresh;
skeleton/empty/error. *Deps:* SessionManager, RoleStore, BookingStore,
ArtistsRepository, RequestsRepository.

**ChatScreen** (S, `Chat`) → `feature/messages/`. *Design:* 08 / 70 / 88.
*ViewModel:* `ChatViewModel`. *Compose:* header = back circle + avatar + name over
the gig line + "Details"; **Airbnb trust banner** (not redaction) as a `surface3`
card; centred status capsule at the head of the transcript, tapping through to
the booking; `LazyColumn` with day rules, sender captions, asymmetric bubbles and
**three message states** — sent, "Read by …", and failed drawn in `surface`
behind a danger rim with a tappable "Not sent · Tap to retry"; **quote card**
(QUOTE / COUNTER OFFER / AGREED, amount, terms, validity) with Accept + Counter
on the seat whose move it is; `ComposerBar` = `AppTextField` + an always-present
send disc. Accepting takes the screen as a three-phase `SendingNarration` (70) and
routes to `match_confirmed/{bookingId}`. *APIs:* `MessagesRepository` (listMessages
explicit columns, send, **realtime** `subscribeMessages`, markThreadRead,
findOrCreateThread), `RequestsRepository` (accept/counter). *Lifecycle:*
`ResumeEffect` → refresh + re-subscribe (gated), teardown on dispose + generation
bump. *Nav:* details sheet participant → `ArtistProfile`. *Deps:* SessionManager,
RoleStore, BookingStore, ReadReceiptsPreference.

**ArchivedScreen** (S, `archived`, both roles) → `feature/messages/`. *Design:* 60
/ 111. *ViewModel:* `MessagesViewModel`. *Compose:* left-aligned `BackHeader` with
the count, rows with a labelled Unarchive, and the badge rule printed on the
screen. Empty state teaches the gesture. Archiving is a DataStore flag —
`threads` has no archived column.

**SafetyCentreScreen** (S, `safety_centre`, both roles) → `feature/messages/`.
*Design:* 131. One dark hero card, three numbered rules, and a `ListRow` per
remedy (report a conversation, blocked accounts). No emergency-numbers row: no
per-city data exists.

**SupportScreen** (S, `support`, both roles) → `feature/messages/`. *Design:* 34.
*ViewModel:* `SupportChatViewModel` over the pure `SupportScript`. Two opening
bubbles (what it is, then what it wants), outlined option cards with detail lines,
one real deep link into the bookings tab, and a typed note → `app_feedback`.

**PaywallScreen** (S, sheet) → `feature/paywall/`. *ViewModel:* uses
`EntitlementStore`. *Compose:* close-x, role hero + editorial headline, perks,
price card (`product.displayPrice` + period + intro offer + auto-renew terms +
T&C links), subscribe CTA (spinner/"Waiting for approval…"), Restore. *APIs:*
Play Billing (dormant). *Lifecycle:* re-pull products if empty. *State:* purchase
outcome→onComplete + dismiss.

**ScoreExplainerScreen** (A, `ScoreExplainer`) → `feature/score/`. **Redesigned
Sep 2026 — design 50 / 79 / 80.** *Compose:* `BackHeader` + `SegmentedControl`
(Score · Stats · Opportunities). **Score:** `ScoreDonut`; **Stats:** the five
`Meter` rows from `ScoreFactors` (Show-up 30 / Reviews 25 / Reply 20 /
Reliability 15 [cancellations inverted] / Social 10); **Opportunities:**
`ScoreOpportunities` rows, each opening the press kit or the wizard — a "+N" pill
only where the points are real. *States:* **79 New** — a five-segment gig counter
and the words "not a low score, no score"; **80 failed** — the headline "This
isn't your real score" above an em-dash donut and five unavailable meters.
*APIs:* `ScoreRepository.breakdownForSelf`/`historyForSelf`,
`ArtistsRepository.ensureFull` (self, for the Opportunities tab). *Nav:* "See
score history"→`ScoreHistoryScreen`.

**BookabilityScreen** (C, `bookability/{artistId}`) → `feature/score/`. **New
Sep 2026 — design 16, "show the arithmetic".** *Compose:* accent headline card
(score / 100 · tier + one line of provenance) then the itemised `Meter` rows;
an `AccentNote` states that nothing on the screen can be bought. *State:* a
failed metrics read keeps the number and renders every factor unavailable rather
than hiding the section. *APIs:* `ScoreRepository.breakdown(artistId)`,
`ArtistsRepository`.

**ArtistReviewsScreen** (C, `artist_reviews/{artistId}`) → `feature/artist/`.
**New Sep 2026 — design 102.** *Compose:* `BackHeader` (corpus size in the
subtitle), `SearchBar`, `Chip` lenses (All *n* / 5 star / Recent), `ReviewCard`
list, dock CTA → `RequestQuote`. *Logic:* `ReviewSearch` — "Recent" is a stated
90-day window, not a re-sort. *States:* three distinct empties (no corpus /
nothing in this lens / no match for a query, which quotes the corpus size) plus
screen 100's failure banner.

**ReportArtistSheet** (C) → `feature/artist/ArtistProfileSheets.kt`. **New
Sep 2026 — design 56.** *Compose:* `SheetScaffold` inside a `ModalBottomSheet`,
five `ArtistReportReasons` radio rows, optional note, "Submit report" (disabled
until a reason is picked) + "Queued on this device if you're offline". *APIs:*
`ReportsRepository.reportArtist` → `ReportOutcome`; the toast says Sent or
Queued and never "received".

---

## 8. Screens — Sheets & settings

**ReviewSheet** (C) → `feature/booking/`. **Redesigned Sep 2026 — designs 20 /
98.** *Compose:* `ModalBottomSheet` over `SheetScaffold`; a 72dp glyph circle,
"How was {artist}?" (or "How was the set?" when the name never loaded), five
36dp stars with a word under them, four `Chip` tags onto the real
`reviews.categories` keys, optional prose, "Post review". Design 98 is the same
sheet with no name: the booking reference stands in and a warm banner says why.
*APIs:* `ReviewsRepository.insert` (from a `viewModelScope` that outlives the
sheet). Success haptic fires on the HOST.

**ScoreBreakdownSheet** (C) → `feature/score/`. **Redesigned Sep 2026 — design
99, "renders only what it can back".** *Compose:* `ModalBottomSheet` +
`SheetScaffold`; `ScoreDisc` + name + tier, the five `Meter` rows when the
metrics loaded, and a "See the full breakdown"→`BookabilityScreen` CTA.
*Degraded:* an `Attention` banner, then only the factors the artist ROW can
vouch for (`total_gigs`, `on_time_rate`, the loaded reviews) under WHAT WE CAN
SHOW, the rest as `UnavailableRow`s under NOT LOADED — no empty bar to misread
as a zero — plus Retry. `breakdownFailed` is threaded in rather than inferred
from a null breakdown, which is also what "not fetched yet" looks like.

**ScoreHistoryScreen** (A, `score_history`) → `feature/score/`. **Redesigned
Sep 2026 — design 51, "the ledger, not the number".** A pushed screen, not a
sheet. *Compose:* today's score + a signed delta pill, a bar chart scaled to the
window's own range (bars, not a line — each point is a discrete recomputation),
and a per-recomputation list. `score_history` stores `(score, computed_at)` and
no reason column, so the design's per-*event* attributions are deliberately not
invented; the decay window and the New-tier floor are stated instead. *APIs:*
`ScoreRepository.historyForSelf`. *States:* loading / failed (banner + retry) /
genuinely empty.

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
| `MonthCalendar` | month grid + legend + schedule (design 78) | `Column` of 7-wide `Row`s; four fills via `monthDayFill` (booked ⁄ unavailable ⁄ open ⁄ selected) + today's ring; `MonthCalendarCard`, `MonthCalendarLegend`, `DayEventRow`; month `DropdownMenu` |
| `DetailHeader` | back circle + LEFT-aligned record title | `Row` + `IconCircle` + 2-line `Column` + mirrored trailing slot |
| `EventTimeline` | 11dp dot, 2dp rule, no glyphs | `Row(IntrinsicSize.Min)` + weighted connector |
| `BottomActionBar` | pinned bar, hairline top, nav-bar inset | `Column` + `hairlineTop()` |
| `AccentNote` | lime-washed aside (52 ⁄ 83 ⁄ 89 ⁄ 95 ⁄ 117 ⁄ 122) | `accent` at 22% + a 60% rim |
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
