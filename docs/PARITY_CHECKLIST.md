# PARITY_CHECKLIST.md — iOS file → Android status

Durable file-by-file tracker for porting Artistant from iOS (`~/Desktop/ios-swift`)
to Android. **FEATURE_CHECKLIST.md** stays task-oriented; this matrix is
**iOS path → Android target → status**.

**Status:** `done` · `partial` · `missing` · `obsolete`

**Product truth (Jul 2026, iOS):** chat redaction **gone** (mig `0071`); booking =
**request → accept** (`pending_confirm`, “Request sent.”, artist Accept/Decline);
Airbnb-style trust (safety banner + “always communicate through Artistant” +
report). Schema through ~0085. Android must not rebuild the retired redaction
moat.

**Android current:** M0–M1 complete; **M2–M7 on `main` (PR #44)** plus
`feature/parity-polish` closing remaining partials (BookingDetail CTAs, Artist
Home dashboard, Messages filters, MonthDayGrid, Help/Feedback, SearchRecents).

---

## Screens — Signup / Auth

Re-implemented against the Sep-2026 light design (section **GS**, screens 01 · 11 ·
12 · 13 · 27 · 28 · 29 · 30 · 31 · 62 · 71 · 90 · 114 · 118 · 119) —
`docs/REDESIGN_2026-09.md` §P2. The "design" column is the extracted screen the
Android file now mirrors.

| iOS path | Design | Android target | Status | Notes |
|---|---|---|---|---|
| — (launch window) | 01 Onboarding | `feature/signup/SplashScreen.kt` | done | "The one dark room" — the only dark surface in the app, rendered on `RootGate.Loading` in `darkest` so the pre-Compose launch window hands off with no seam. Carries no actions: the markup's two CTAs belong to 118 |
| `Screens/Signup/SignupWelcomeView.swift` | 118 Welcome — blocked | `feature/signup/WelcomeScreen.kt` | done | Disabled CTA always paired with an inline reason ("Tick this to continue"). Only real conditions gate it — the terms tick, plus a caller-supplied connectivity reason. No "signups paused" state: `app_settings` is server-only, so it cannot be stated truthfully |
| `Screens/Signup/CommunityCommitmentView` | 27 Community pledge | `feature/signup/CommunityCommitmentScreen.kt` | done | Four numbered rules, a required tick, "Shown once — you won't see this again". The Decline dead-end screen is gone; back is the decline |
| `Screens/Signup/SignupRoleView.swift` | 11 Role picker · 71 Hydration error | `feature/signup/RoleScreen.kt` | done | Select on tap, move on Continue (was: commit-and-self-advance). 71 is the same screen with a failure banner + Retry, which also renders on the handle step — the one the gate actually enters on |
| `Screens/Signup/SignupAuthView.swift` | 12 Sign in | `feature/signup/SignupAuthScreen.kt` | done | Phone OTP first, then Apple / Google / password. Only three number shapes are accepted (10 digits, 91+10, +91+10) and anything else gets the inline reason — a longer paste is never trimmed to its last ten digits. LOGIN sends with `createUser = false`, so that door cannot mint an account behind an un-ticked consent box; GoTrue's refusal becomes "No account for this number — create one?" back to the welcome screen. Apple and Google marks not bundled yet, so those rows are labelled buttons |
| — (new) | 119 Enter code | `feature/signup/EnterCodeScreen.kt` | done | `SignupStep.Code`, 6-box `OtpField`, 30s resend, "Change number", email escape after two sends. Every way back out calls `AuthViewModel.clearOtp()` — including the system gesture, which `SignupFlow` handles, since the activity-scoped VM otherwise carried the spent send count into the next number |
| `Screens/Signup/EmailAuthView.swift` | 28 Email sign-up | `feature/signup/EmailSignUpScreen.kt` | done | A modal over the auth step (`emailSignUp` flag), not a step. One button, two acts: the password is offered to sign-IN first and only creates an account when nothing matched it, which is what the banner above it promises. Sign-in opens at GoTrue's 6 characters (an older account may hold one); the 8 the tick draws is enforced on the branch that creates. "Forgot password?" calls `resetPasswordForEmail` |
| `Screens/Signup/SignupProfileView.swift` | 29 Handle & city · 90 Handle taken | `feature/signup/ProfileScreen.kt` | done | Four live states; "Couldn't check" never borrows the available tick, while still leaving Continue tappable (the unique constraint is the backstop). Taken offers `HandleSuggestions`. 90's "THE OTHER THREE STATES" panel is design documentation, not a control, and is deliberately not drawn |
| `Screens/Signup/SignupNotifPermissionView.swift` | 13 Notifications | `feature/signup/NotifPermissionScreen.kt` | done | Names the loss ("Quotes expire. We'll tell you first."), three kinds. FCM register via PushService (soft without google-services.json) |
| `Screens/Signup/SignupDoneView.swift` | 30 You're in | `feature/signup/DoneScreen.kt` | partial | Ends on the score. The design's "412 acts play your city" has no count endpoint behind it, so the sentence keeps its shape and drops its number |
| `Screens/Signup/LegalView.swift` | 31 Terms · 114 Privacy policy | `feature/signup/LegalScreen.kt` | done | One segmented viewer, both documents one tap apart. Copy rewritten to the redesign's — the old 11-section terms described platform fees and refunds on a product that takes no payment |
| — (new) | 62 Privacy | `feature/signup/PrivacyScreen.kt` | partial | ONE switch (read receipts), stored in `PrivacyPreferences` (DataStore) because it has no column in the 107 canonical migrations, and the screen says so. Nothing reads it yet — enforcement is `feature/messages`, under the key `privacy.read_receipts`. The design's second switch, "Show my city", is a stated fact instead: `users.city` has no visibility column, so a device flag could only have hidden the city from its owner. Reached from the account settings list's Privacy row |
| `Screens/Signup/SignupFlowView.swift` | — | `feature/signup/SignupFlow.kt` + `SignupViewModel` | done | Step machine gains `.Code`, retired by a live session exactly like `.Auth`. The floating hydration strip is gone — the banner lives in the screens now |
| `Services/AuthService.swift` / Root gate | — | `SessionManager` + `ArtistantNavHost` / `RootViewModel` | done | `sendPhoneOtp` / `verifyPhoneOtp` / `sendEmailOtp` / `verifyEmailOtp` / `sendPasswordReset` added, the two sends taking `createUser` from the entrance; `signUpWithEmail` also reports `AlreadyRegistered` (GoTrue's empty-identities answer). `AuthGateway` is the interface the auth VM injects, so its branches are unit-testable; every prior method is otherwise unchanged |
| `Services/AppleSignInController.swift` | — | `SessionManager` Custom Tabs + deep link | done | #12 deepLinkError surface on OAuth denial |

## Screens — Browse (M2)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/DiscoverView.swift` | `feature/discover/DiscoverScreen.kt` | **redesigned (DS)** | Sep-2026 light design, screens 02 + 59. Header (city + today) · `SearchBarButton` · category chips · one 262dp `HeroCard` · titled two-up `Tile` rails · `SkeletonPage`. Hero carousel and full-bleed insets retired; "See all" seeds Search via `SearchSeed` |
| `State/DiscoverFeedStore.swift` | `feature/discover/DiscoverViewModel.kt` | **redesigned (DS)** | Hero + 4 rails via 5 concurrent `search_artists`; the availability rail is date-scoped through the 0073 `p_date` AND re-filtered on `days_available` (the repo silently retries without the 0073 dims on an old server). Empty rails are dropped, not drawn |
| `Screens/SearchView.swift` | `feature/search/SearchScreen.kt` | **redesigned (DS)** | Screens 14 / 03 / 57 / 58 in one destination. Browse-vs-results is `hasActiveQuery` × a local `editing` flag. Suggestions carry `search_facets`' real counts; "Notify me when one joins" omitted (no alert store); "Map view" omitted (no coordinates) |
| `Screens/SearchFilterSheet.swift` | `feature/search/SearchFilterSheet.kt` + `CompareByServiceSheet.kt` | **redesigned (DS)** | Screens 15 / 104 (one sheet, two states: chip summary + disclosure rows + histogram + pinned CTA) and 53 (radio compare-by-service). "Must have" toggles omitted — no PA / verification / travel columns |
| `State/SearchStore.swift` | `feature/search/SearchViewModel.kt` | **redesigned (DS)** | Debounce + pagination + filter/histogram, plus `selectService` (radio), `dropFilter(kind)` and the `SearchSeed` collector. Every computed string is a pure function in `SearchLabels.kt`, unit-tested |
| `Screens/ArtistView.swift` | `feature/artist/ArtistProfileScreen.kt` | done | **Redesigned Sep 2026 (AP, design 04/54/55/101/103).** Round portrait + rating pill + three-cell stat strip; packages replace the rate card and the price rides the CTA; skeleton with no nav bar (54); named not-found with a route to Discover (55); no-audio redirect (101); self view swaps the verbs and drops the booking controls (103). Hero pager (PROF-10) still open |
| `Screens/ScoreExplainerView.swift` | `feature/score/ScoreExplainerScreen.kt` | done | **Redesigned Sep 2026 (design 50/79/80).** Score · Stats · Opportunities segments; New is a gig counter and says "not a low score, no score"; a failed read leads with "This isn't your real score"; every opportunity opens the press kit or the wizard |
| `Screens/ScoreBreakdownSheet.swift` | `feature/score/ScoreBreakdownSheet.kt` | done | **Redesigned Sep 2026 (design 99).** Degraded state keeps the server's number and itemises only what the artist row can back (`total_gigs`, `on_time_rate`, the loaded reviews); the rest go under NOT LOADED with no bar to misread as a zero |
| — (new, design 16) | `feature/score/BookabilityScreen.kt` | done | Client-facing audit of one artist's score, pushed from the breakdown sheet. Route `bookability/{artistId}` |
| — (new, design 102) | `feature/artist/ArtistReviewsScreen.kt` | done | Every review for one artist + search + All/5 star/Recent lenses. Three distinct empties: no corpus, nothing in this lens, no match for a query (which quotes the corpus size). Route `artist_reviews/{artistId}` |
| iOS `ReportArtistSheet` | `feature/artist/ArtistProfileSheets.kt` (`ReportArtistSheet`) | done | Design 56. Real `reports` insert via `ReportsRepository.reportArtist`; five profile-specific reasons + optional note; the toast says Sent or Queued and never "received" |
| `Screens/ArtistListView.swift` | `feature/profile/ArtistListScreen.kt` | **redesigned (DS)** | Screens 32 + 112. Kind chips are permanent navigation (switching replaces the destination); a within-list category rail derived from the rows; rows carry the accent score chip and "from ₹x". Failure ≠ empty |

## Screens — Booking (M3)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/BookingView.swift` | `feature/booking/BookingScreen.kt` | **redesigned (BC)** | Screen 05 "Step 1 of 2". Month grid replaces the 14-day strip; opens on the first month with an open day, stops at the current month going back; closed days dim AND refused by the ViewModel. Time/venue/guests stay on this step — 06 is read-only and `start_datetime` needs the time |
| `Screens/CheckoutView.swift` | `feature/booking/CheckoutScreen.kt` | **redesigned (BC)** | Screen 06 "no money in v1". One number (artist fee), accent note on the no-payment terms, "what happens next" card. Date/time moved into the act header; the per-row edit chevrons are gone with them |
| `Screens/ConfirmedView.swift` | `feature/booking/ConfirmedScreen.kt` | **redesigned (BC)** | Screen 07. Two branches: pending reads "Request sent.", confirmed reads "You've got a band on Saturday." with Message + Add to calendar. "Confirmed in 4 minutes" omitted — the row records no answer time |
| — (new) | `feature/booking/MatchConfirmedScreen.kt` | **redesigned (BC)** | Screen 94, route `match_confirmed/{bookingId}`. The chat path's own landing; messaging navigates here from an accepted in-thread quote |
| — (new) | `feature/booking/InvoiceScreen.kt` + `InvoiceLogic.kt` | **redesigned (BC)** | Screen 132, route `invoice/{bookingId}`. "A record, not a tax invoice": fee, Artistant's ₹0, total = fee; persisted platform fee and GST are never printed as charges. Shares text, not a PDF |
| `Screens/GigRequestDetailView.swift` (counter sheet) | `feature/booking/CounterOfferScreen.kt` | **redesigned (BC)** | Screen 61, route `counter_offer/{requestId}` on the ARTIST graph — `gig_requests_update_artist` (mig 0002) is the only UPDATE policy, so a client-side counter is blocked on the backend. The gigs screen keeps its inline sheet until AS is rewritten |
| `Screens/RequestQuoteView.swift` | `feature/booking/RequestQuoteScreen.kt` | **redesigned (BC)** | Screen 17 "the brief, prefilled". Occasion chips + date/start pickers + guests/venue/budget + 500-char note; occasion and start time have no column and travel in the message (`quoteBriefMessage`). Budget is one amount, not the design's range |
| `Screens/BookingDetailView.swift` | `feature/booking/BookingDetailScreen.kt` | **redesigned (BN)** | Five variants behind one route — confirmed (18), awaiting (95), cancelled (83), disputed (96), read-only (97) — plus not-found (84) and the two-stage cancel flow (117 → 52). Run of show is built only from `start_datetime`/`end_datetime`/`venue_notes`; the design's invented load-in and the disputed screen's event history have no schema behind them and are stated as unavailable |
| `Screens/BookingsView.swift` | `feature/bookings/BookingsScreen.kt` | **redesigned (BN)** | Screens 10 / 89 / 122. Upcoming ⁄ Past, three affordances (confirmed card · awaiting row · review row), dismissible name nudge over the empty state, and an offline list rendered from a DataStore snapshot of the night's essentials |
| `Screens/ReviewSheet.swift` | `feature/booking/ReviewSheet.kt` | **redesigned (BN)** | Screens 20 / 98. Stars + a word, four tags onto the real `reviews.categories` keys, optional prose. A missing artist name degrades to the booking reference and never blocks the review. No "post publicly" switch — `reviews` has no visibility column |
| — (design 78) | `feature/bookings/MonthCalendarScreen.kt` | **new (BN)** | The shared calendar as its own destination (`month_calendar`), off the Bookings header. Renders `MonthCalendarCard` + the selected day's schedule |

## Screens — Messages (M4)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/MessagesView.swift` | `feature/messages/MessagesScreen.kt` | done | **Redesign 19/110.** Server inbox + All/Bookings/Inquiries/Support chips + push deep link; rows resync on resume (iOS uses an all-threads Realtime sub for the same job). Rows carry DEAL STATE — a live `gig_requests` quote replaces the preview with amount + expiry. Permanent Artistant Support row in every state |
| `Screens/ChatView.swift` | `feature/messages/ChatScreen.kt` | done | **Redesign 08/70/88.** Realtime + optimistic send/retry; three message states (sent / read / failed-with-retry); trust banner; quote-as-object with Accept/Counter → `match_confirmed/{bookingId}`; accept narrated in three phases |
| `Screens/ThreadDetailsSheet.swift` | `feature/messages/ThreadDetailsSheet.kt` | done | **Redesign 33.** Booking card (status/date/venue/fee/score) or the inquiry banner; archive/mute/mark-unread/star/block rows; report opens the picker |
| — (iOS files reports from the thread sheet) | `feature/messages/ReportConversationSheet.kt` | done | **Redesign 73.** Reason → optional note → Submit, same shape as report-artist. `ConversationReportReasons`; the note reaches `reports.details` |
| — (no iOS equivalent) | `feature/messages/ArchivedScreen.kt` | done | **Redesign 60/111.** Archive is a route, not a sheet. Device-local flag (`threads` has no archived column); the badge rule is printed on the screen and pinned by a test |
| — (no iOS equivalent) | `feature/messages/SafetyCentreScreen.kt` | done | **Redesign 131.** Three rules + one-tap remedies. The design's "Emergency help · local numbers by city" row is NOT drawn — no per-city data exists |
| `Screens/SupportView.swift` (n/a) | `feature/messages/SupportChat.kt` | done | **Redesign 34.** Scripted on-device branch tree that says it is one; full screen, option cards, hand-off to the bookings tab; typed notes → `app_feedback` |
| — (0087 has no iOS screen) | `feature/profile/BlockedAccountsScreen.kt` | done | **Redesign 127.** Copy states what a v1 block actually does (client-side filtering); the design's stronger claim is deliberately not shipped. "Block ≠ report" carries a live link to the inbox |

## Screens — Artist home / gigs (M3/M5)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ArtistHomeView.swift` | `feature/artisthome/ArtistHomeScreen.kt` | done | Earnings sparkline + 14-day busy strip + New requests + quotes + Up next |
| `Screens/ArtistGigsView.swift` | `feature/gigs/ArtistGigsScreen.kt` | done | MonthDayGrid + month-grouped list |
| `Screens/GigRequestDetailView.swift` | `feature/gigs/GigRequestDetailScreen.kt` | done | Accept/Decline/counter |

## Screens — Wizard / EPK (M5)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ArtistWizard/ArtistWizardView.swift` | `feature/wizard/WizardScreen.kt` | done | **Redesigned Sep 2026 (design 72).** Back circle + progress track + "03 / 10" counter; Save & exit is a sheet stating what survives, reachable from every step. Publish: upsert + packages/tech RPCs + service tags + published; gallery/SAF + UploadQueue |
| `Screens/ArtistWizard/ArtistIdentityStep.swift` | `feature/wizard/WizardFormSteps.kt` (Identity) | done | **Redesigned Sep 2026 (design 37).** AppTextField + Chip; public address under the handle; the category's seeded pricing band stated on the step that picks it. Genre stays a free-text field — the design draws chips, the column has no vocabulary |
| `Screens/ArtistWizard/ArtistLocationStep.swift` | `feature/wizard/WizardFormSteps.kt` (Location) | done | **Redesigned Sep 2026 (design 38).** Base city + travel radius + event types. Radius and event types are draft-only (no radius column; no `event_types` writer in this client) and the step says so |
| `Screens/ArtistWizard/ArtistPricingStep.swift` | `feature/wizard/WizardFormSteps.kt` (Pricing) | done | **Redesigned Sep 2026 (design 24).** Fee in, all-in out — the host's total per tier through `BookingMath`. No market-rate line: there is no such aggregate. `replace_packages` on publish |
| `Screens/ArtistWizard/ArtistTechStep.swift` | `feature/wizard/WizardFormSteps.kt` (Tech) | done | **Redesigned Sep 2026 (design 39).** Presets as checkable rows, custom lines as chips. No rider PDF — `tech_rider` stores text rows. `replace_tech_rider` on publish |
| `Screens/ArtistWizard/ArtistAvailabilityStep.swift` | `feature/wizard/WizardFormSteps.kt` (Availability) | done | **Redesigned Sep 2026 (design 40).** Day strip + start-time chips compressed to one badge, shown while still editable. No "notice you need" — no column for it |
| `Screens/ArtistWizard/ArtistCoverStep.swift` | `feature/wizard/WizardMediaSteps.kt` (Cover) | done | **Redesigned Sep 2026 (design 41).** Camera / Library pair; a denied permission routes to the system settings page. Photo only — no video cover path |
| `Screens/EPKView.swift` | `feature/epk/EpkScreen.kt` | done | Packages/tech/links/samples + photo grid/reorder |
| `Screens/Settings/ScoreHistorySheet.swift` | `feature/score/ScoreHistoryScreen.kt` | done | **Redesigned Sep 2026 (design 51)** — a pushed screen, not a sheet. Per-*recomputation* deltas: `score_history` stores no reason column, so no per-event cause is invented. Route `score_history` |
| `Screens/PaywallView.swift` | `feature/paywall/PaywallScreen.kt` | done | Play Billing wired; inert until subscriptionsEnabled |
| `Components/ScoreRing.swift` | `designsystem/component/ScoreRing.kt` | done | New-tier nil handling |
| `Components/Sparkline` | `designsystem/component/Sparkline.kt` | done | |
| `Screens/ArtistWizard/ArtistSocialsStep.swift` | `feature/wizard/WizardMediaSteps.kt` (Socials) | done | **Redesigned Sep 2026 (design 42).** Paste fields, not OAuth — no link endpoint on this backend — and the banner says the links are not verified |
| `Screens/ArtistWizard/ArtistBioStep.swift` | `feature/wizard/WizardMediaSteps.kt` (Bio) | done | **Redesigned Sep 2026 (design 43).** Live counter plus what-good-looks-like guidance at four lengths; service tags published via `updateServiceTags` |
| `Screens/ArtistWizard/ArtistSamplesStep.swift` | `feature/wizard/WizardMediaSteps.kt` (Samples) | done | **Redesigned Sep 2026 (design 44).** Upload state read off `UploadQueue` — queued / uploading / failed with a retry — rather than a bar driven locally. SAF + UploadQueue after go-live |
| `Screens/ArtistWizard/ArtistPreviewStep.swift` | `feature/wizard/WizardPublishSteps.kt` (Preview) | done | **Redesigned Sep 2026 (design 45).** Centred back header; seven rows, each stating its value with an Edit that jumps back to the owning step |
| `Screens/ArtistWizard/ArtistWizardDoneStep.swift` | `feature/wizard/WizardPublishSteps.kt` (Done) | done | **Redesigned Sep 2026 (design 46).** Ends on the public address (copyable) and sets the New-tier expectation off `ScoreBands` |
| `Screens/EPKView.swift` | `feature/epk/EpkScreen.kt` | done | Packages/tech/links/samples + photo grid/reorder + wizard CTA |

## Screens — Profile / Settings / Paywall (M6/M7)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ProfileView.swift` | `feature/profile/ProfileScreen.kt` | done | Identity + settings + calendar + Help/Feedback sheet |
| `Screens/Settings/DataExportView.swift` | `feature/profile/ProfileScreen.kt` (export row) | partial | Inline JSON share + signed URL open; no dedicated sheet |
| `Screens/Settings/ManageAvailabilityView.swift` | `feature/availability/ManageAvailabilityScreen.kt` | done | Days/times chips + seed-failure Save guard |
| `Screens/Settings/ScoreHistorySheet.swift` | `feature/score/ScoreHistoryScreen.kt` | done | **Redesigned Sep 2026 (design 51)** — a pushed screen, not a sheet. Per-*recomputation* deltas: `score_history` stores no reason column, so no per-event cause is invented. Route `score_history` |
| `Screens/PaywallView.swift` | `feature/paywall/PaywallScreen.kt` | done | Play Billing wired; inert until subscriptionsEnabled |

## Navigation / shells

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| Root / role tabs | `ArtistantRoot` + `ClientTabsScaffold` / `ArtistTabsScaffold` | done | All primary tabs wired (no Placeholder) |
| `State/TabRouter.swift` | nav + deep-link pending channels | done | `TabRouter` singleton + client/artist scaffold consumers |
| `ClientRoute.ArtistProfile` / `Search` | `Routes.kt` + NavHost | done | Wired in ClientTabsScaffold |

---

## Repositories

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Repositories/UsersRepository.swift` | `data/repository/UsersRepository.kt` + Fake | done | M1 |
| `Repositories/ArtistsRepository.swift` | `data/repository/ArtistsRepository.kt` + Fake | done | M2 — id-keyed hydrating cache |
| `Repositories/SearchRepository.swift` | `data/repository/SearchRepository.kt` + Fake | done | M2 |
| `Repositories/SearchTypes.swift` | `data/model/SearchTypes.kt` | done | M2 |
| `Repositories/SavedArtistsRepository.swift` | `data/repository/SavedArtistsRepository.kt` + Fake | done | saved_artists upsert/delete/list + SavedStore |
| `Repositories/ReviewsRepository.swift` | `data/repository/ReviewsRepository.kt` + Fake | done | M2 profile (listForArtist) |
| `Repositories/PackagesRepository.swift` | `data/repository/PackagesRepository.kt` + Fake | done | `replace_packages` |
| `Repositories/ArtistMediaRepository.swift` | `data/repository/ArtistMediaRepository.kt` | done | Photo upload + reorder RPC |
| `Repositories/ScoreRepository.swift` | `data/repository/ScoreRepository.kt` + Fake | done | metric_* + score_history |
| `Repositories/BookingsRepository.swift` | `data/repository/BookingsRepository.kt` + Fake | done | M3 — request→accept + calendar ingest |
| `Repositories/RequestsRepository.swift` | `data/repository/RequestsRepository.kt` + Fake | done | M3 |
| `Repositories/MessagesRepository.swift` | `data/repository/MessagesRepository.kt` + Fake | done | Explicit projections, Realtime, receipts |
| `Repositories/SamplesRepository.swift` | `data/repository/SamplesRepository.kt` | done | Append upload + targeted delete |
| `Repositories/TechRiderRepository.swift` | `data/repository/TechRiderRepository.kt` | done | `replace_tech_rider` |
| `Repositories/ArtistLinksRepository.swift` | `data/repository/ArtistLinksRepository.kt` | done | CRUD |

---

## State / stores → ViewModels

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `State/OnboardingStore.swift` / signup | `SignupViewModel` | done | M1 |
| `State/RoleStore.swift` | `AppPreferences` + RootViewModel | partial | Role persisted; store shape differs |
| `State/DiscoverFeedStore.swift` | `DiscoverViewModel` | done | M2 |
| `State/SearchStore.swift` | `SearchViewModel` | partial | M2 |
| `State/SavedStore.swift` | `feature/saved/SavedStore.kt` | done | Optimistic toggle + prefs + sign-out reset. `refreshFromServer()` returns whether the SERVER copy was read (DS) — loaded-and-empty and couldn't-load are the same empty set and the opposite meaning |
| `State/BookingStore.swift` | `BookingViewModel` + `BookingDraftStore` | partial | Draft store + VM; no global booking list cache yet |
| `State/RequestStore.swift` | `ArtistHomeViewModel` (+ RequestsRepository) | done | Open quotes rail on Artist Home |
| `State/MessageStore.swift` | `MessagesViewModel` / `ChatViewModel` | done | Inbox filters + Chat Realtime/optimistic + scroll-back cursor (`loadOlder`); no global MessageStore needed |
| `State/EPKStore.swift` | `EpkViewModel` | done | Packages/tech/links/samples writes |
| `State/ArtistOnboardingStore.swift` | `WizardViewModel` | done | Publish orchestration + pending media |
| `State/EntitlementStore.swift` | `feature/paywall/EntitlementStore.kt` | done | Checkout gate ready; inert until subscriptionsEnabled |
| `State/TabRouter.swift` | `navigation/TabRouter.kt` | done | pendingThread/booking/gigRequest + role tabs |
| `State/Persistence.swift` | `AppPreferences` (DataStore) | done | M0 |
| `State/WizardMediaCache.swift` | `platform/media/WizardMediaCache.kt` | done | cacheDir staging |

---

## Services

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Services/SupabaseClient.swift` | `SupabaseClientFactory` + `SupabaseModule` | done | M0; tier guard |
| `Services/AppEnvironment.swift` | `core/config/AppEnvironment.kt` | partial | M0 + subscriptionsEnabled/legal URLs (flag default off) |
| `Services/AuthService.swift` | `platform/auth/SessionManager.kt` | done | M1 |
| `Services/AccountService.swift` | `data/repository/AccountRepository.kt` + Fake | done | M6 — delete-account + data-export EFs |
| `Services/PushService.swift` | `platform/push/PushService.kt` + MessagingService | partial | claim_device_token FCM register + payload→TabRouter; needs operator google-services.json + send-push FCM |
| `Services/CalendarSyncService.swift` | `platform/calendar/CalendarSyncService.kt` | done | CalendarContract mirror + planner |
| `Services/PermissionsService.swift` | `NotificationPermission` etc. | partial | Notif permission UI M1; calendar runtime grant |
| `Services/UploadQueue.swift` | `platform/media/UploadQueue.kt` | done | JSON snapshot + WorkManager drain + resumeAfterLaunch |
| `Services/VideoTrimmer.swift` | — | deferred | Was ported, deleted Aug 2026: no screen picks video in v1, so it had no caller (and no test). Re-add with the cover-video step, together with `media3-transformer` |
| `Services/Payments/*` | `platform/billing/PlayBillingService.kt` | done | Wired; inert until subscriptionsEnabled |
| `Services/Observability/*` | `Analytics` / `Crash` | partial | Dark-until-key stubs M0 |

---

## Domain / pure logic

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| Booking money math | `domain/booking/BookingMath.kt` | done | M0 |
| Score bands | `domain/score/ScoreBands.kt` | done | M0 |
| Chat redaction regexes | ~~`domain/chat/Redaction.kt`~~ | obsolete | **Deleted** Jul 2026 wave — do not reintroduce. |
| Returning-login router | `domain/auth/ReturningLoginRoute.kt` | done | M1 |
| Auth advance key | `domain/auth/AuthAdvanceKey.kt` | done | M1 |

---

## Design components (subset)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Components/PrimaryButton.swift` | `designsystem/component/PrimaryButton.kt` | done | |
| `Components/Pill.swift` | `designsystem/component/Pill.kt` | done | |
| `Components/CardView.swift` | `designsystem/component/CardView.kt` | done | |
| `Components/HRule` / Section | `HRule.kt` | partial | |
| `Components/ArtistTile.swift` | `designsystem/component/ArtistTile.kt` | done | M2 |
| `Components/MediaContainer.swift` | `designsystem/component/MediaContainer.kt` | done | |
| `Components/EmptyStateView.swift` | `designsystem/component/EmptyState.kt` | done | M2 |
| `Components/Avatar.swift` | `designsystem/component/Avatar.kt` | done | DJB2 hue + initials |
| `Components/Skeleton.swift` | `designsystem/component/Skeleton.kt` | done | |
| `Components/ScoreRing.swift` | `designsystem/component/ScoreRing.kt` | done | New-tier nil handling |
| `Components/Sparkline` | `designsystem/component/Sparkline.kt` | done | |
| `Components/HeaderBar.swift` | `designsystem/component/ScreenTitleBar.kt` + `feature/booking/BookingChrome.kt` (`FunnelHeader`) | done | Tab roots use `ScreenTitleBar`; pushed screens use `FunnelHeader` (back control + centred title). Deliberately not a Material `TopAppBar` |
| `Components/Haptic.swift` | `designsystem/Haptics.kt` | done | 7 verbs + `rememberHaptics()`. Fired at 24 of iOS's 26 non-signup sites. `ReportArtistSheet` closed its two on the Sep-2026 AP redesign (reason tap = selection, submit = warning — not success: the app must not congratulate anyone for filing a complaint). The 2 remaining gaps have no Android surface: per-category review dots, and the thread report picker, which files on the reason tap so there is no separate select moment |
| Theme tokens | `designsystem/theme/*` | **redesigned** | **Sep 2026 — the dark, dual-accent language is retired.** `docs/REDESIGN_2026-09.md` is the token sheet (§2 palette/type/geometry, §4 old→new mapping). Light surfaces, ONE lime accent for both roles (`withRole` is identity), Plus Jakarta Sans + JetBrains Mono in `res/font/`; the editorial serif is gone and `SerifFamily` is a deprecated alias of the sans. Old `AppType`/`AppColors` names survive as aliases so inherited screens compile — the eleven P2 section PRs retire them. Over-media chrome (`glass*`, `inkOnMedia*`, `ArtistGradient`) is deliberately unchanged: photos are still photos |
| Component library v2 | `designsystem/component/*` | done (P1) | `PrimaryButton`, `SecondaryButton`, `IconCircle`, `SearchBar`/`SearchBarButton`, `Chip`/`ChipRail`, `SectionHeader`/`EyebrowLabel`, `Tile`/`MediaSlot`, `HeroCard`, `ListRow`, `Banner` (absorbs `InlineBanner`), `StatusPill`, `EmptyState`, `Skeleton*`, `Toast`/`ToastHost`, `LightTabBar`, `ScreenHeader`, `BackHeader`, `SheetScaffold`, `AppTextField`, `OtpField`. Each has a `@Preview`; none is wired into a screen yet — that is P2's job |
| Month calendar | `designsystem/component/MonthCalendar.kt` | **redesigned (BN)** | Design 78 documents all five day states and this implements them: `MonthDayFill` (Booked · Unavailable · Open · Selected) plus today's ring, with `monthDayFill` as the pure precedence rule. Adds `MonthCalendarCard`, `MonthCalendarLegend` and `DayEventRow`. `MonthDayGrid` keeps its signature for Gigs and gains one optional `unavailableDays` |
| Section BN primitives | `designsystem/component/{DetailHeader,EventTimeline,BottomActionBar,AccentNote}.kt` | **new (BN)** | Left-aligned record header (18/78/83/84/95/96/97/117), the light design's 11dp-dot timeline, the pinned bottom bar (not `dockSurface` — no rounded corners on the page's own edge), and the lime-washed aside six BN screens share |
| Global chrome | `designsystem/component/LightTabBar.kt` | done (P1) | Replaces the floating blurred pill. Opaque, hairline top, four glyphs + a raised accent action circle. Client: Home · Search · [+] · Messages · Profile. Artist: Studio · Gigs · [+] · Messages · Profile. `FloatingTabBar` and `AmbientRoleWash` are deleted |

---

## Explicitly deferred (not this wave)

- Operator Google/Apple dashboard config + `google-services.json` / FCM server path
- Artist profile PROF-10 hero pager (review search/sort shipped Sep 2026 as `ArtistReviewsScreen`)

---

## How to update

When a screen/repo lands: flip Status to `done` or `partial`, add the Android path,
and note any Jul-2026 deltas. Keep this file honest — stale rows hurt more than
missing ones.
