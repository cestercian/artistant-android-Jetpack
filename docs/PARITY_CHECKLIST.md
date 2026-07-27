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

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/Signup/SignupWelcomeView.swift` | `feature/signup/WelcomeScreen.kt` | done | M1 |
| `Screens/Signup/SignupRoleView.swift` | `feature/signup/RoleScreen.kt` | done | M1 |
| `Screens/Signup/SignupAuthView.swift` | `feature/signup/SignupAuthScreen.kt` | done | M1 |
| `Screens/Signup/EmailAuthView.swift` | `ui/auth/AuthScreen.kt` (email branch) | done | M1 |
| `Screens/Signup/SignupProfileView.swift` | `feature/signup/ProfileScreen.kt` | done | M1 |
| `Screens/Signup/SignupNotifPermissionView.swift` | `feature/signup/NotifPermissionScreen.kt` | done | M1; FCM register via PushService (soft without google-services.json) |
| `Screens/Signup/SignupDoneView.swift` | `feature/signup/DoneScreen.kt` | done | M1 |
| `Screens/Signup/LegalView.swift` | `feature/signup/LegalScreen.kt` | done | M1 |
| `Screens/Signup/SignupFlowView.swift` | `feature/signup/SignupFlow.kt` + `SignupViewModel` | done | M1 |
| `Services/AuthService.swift` / Root gate | `SessionManager` + `ArtistantNavHost` / `RootViewModel` | done | M1 |
| `Services/AppleSignInController.swift` | `SessionManager` Custom Tabs + deep link | done | #12 deepLinkError surface on OAuth denial |

## Screens — Browse (M2)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/DiscoverView.swift` | `feature/discover/DiscoverScreen.kt` | done | M2 — rails via concurrent `search_artists` |
| `State/DiscoverFeedStore.swift` | `feature/discover/DiscoverViewModel.kt` | done | M2 |
| `Screens/SearchView.swift` | `feature/search/SearchScreen.kt` | done | Query + facets + sort + filter sheet badge |
| `Screens/SearchFilterSheet.swift` | `feature/search/SearchFilterSheet.kt` | done | Accordion + histogram + 0073 dims |
| `State/SearchStore.swift` | `feature/search/SearchViewModel.kt` | done | Debounce + pagination + filter/histogram |
| `Screens/ArtistView.swift` | `feature/artist/ArtistProfileScreen.kt` | done | Hero/bio/packages/reviews/dock + saved heart + score breakdown sheet |
| `Screens/ScoreExplainerView.swift` | `feature/score/ScoreExplainerScreen.kt` | done | Self metrics + history; Home → Score |
| `Screens/ScoreBreakdownSheet.swift` | `feature/score/ScoreBreakdownSheet.kt` | done | Client real-world rows from profile chip |
| `Screens/ArtistListView.swift` | `feature/profile/ArtistListScreen.kt` | done | Profile stats destination |
| `Screens/Signup/CommunityCommitmentView` (in SignupFlowView) | `feature/signup/CommunityCommitmentScreen.kt` | done | ACCT-05 pledge gate |

## Screens — Booking (M3)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/BookingView.swift` | `feature/booking/BookingScreen.kt` | done | Request funnel → `pending_confirm` |
| `Screens/CheckoutView.swift` | `feature/booking/CheckoutScreen.kt` | done | Matchmaker; mock payment dormant |
| `Screens/ConfirmedView.swift` | `feature/booking/ConfirmedScreen.kt` | done | Copy: “Request sent.” |
| `Screens/RequestQuoteView.swift` | `feature/booking/RequestQuoteScreen.kt` | done | Gig-request create |
| `Screens/BookingDetailView.swift` | `feature/booking/BookingDetailScreen.kt` | done | Message / Accept-Decline / Getting there / calendar / review |
| `Screens/BookingsView.swift` | `feature/bookings/BookingsScreen.kt` | done | Client calendar list |
| `Screens/ReviewSheet.swift` | `feature/booking/ReviewSheet.kt` | done | Minimal insert sheet |

## Screens — Messages (M4)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/MessagesView.swift` | `feature/messages/MessagesScreen.kt` | done | Server inbox + All/Bookings/Inquiries filters + push deep link |
| `Screens/ChatView.swift` | `feature/messages/ChatScreen.kt` | done | Realtime + optimistic send/retry; system rows + trust banner + details/report |
| `Screens/ThreadDetailsSheet.swift` | `feature/messages/ThreadDetailsSheet.kt` | done | Gig summary + report reasons → `reports` |

## Screens — Artist home / gigs (M3/M5)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ArtistHomeView.swift` | `feature/artisthome/ArtistHomeScreen.kt` | done | Earnings sparkline + 14-day busy strip + New requests + quotes + Up next |
| `Screens/ArtistGigsView.swift` | `feature/gigs/ArtistGigsScreen.kt` | done | MonthDayGrid + month-grouped list |
| `Screens/GigRequestDetailView.swift` | `feature/gigs/GigRequestDetailScreen.kt` | done | Accept/Decline/counter |

## Screens — Wizard / EPK (M5)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ArtistWizard/ArtistWizardView.swift` | `feature/wizard/WizardScreen.kt` | done | Publish: upsert + packages/tech RPCs + published; gallery/SAF + UploadQueue |
| `Screens/ArtistWizard/ArtistIdentityStep.swift` | `feature/wizard/WizardScreen.kt` (Identity) | done | Inline form in host |
| `Screens/ArtistWizard/ArtistLocationStep.swift` | `feature/wizard/WizardScreen.kt` (Location) | done | |
| `Screens/ArtistWizard/ArtistPricingStep.swift` | `feature/wizard/WizardScreen.kt` (Pricing) | done | `replace_packages` on publish |
| `Screens/ArtistWizard/ArtistTechStep.swift` | `feature/wizard/WizardScreen.kt` (Tech) | done | `replace_tech_rider` on publish |
| `Screens/ArtistWizard/ArtistAvailabilityStep.swift` | `feature/wizard/WizardScreen.kt` (Availability) | done | |
| `Screens/ArtistWizard/ArtistCoverStep.swift` | `feature/wizard/WizardScreen.kt` (Cover) | done | Gallery + TakePicture camera + gradient |
| `Screens/EPKView.swift` | `feature/epk/EpkScreen.kt` | done | Packages/tech/links/samples + photo grid/reorder |
| `Screens/Settings/ScoreHistorySheet.swift` | `feature/score/ScoreHistorySheet.kt` | done | Sparkline + delta sheet from explainer |
| `Screens/PaywallView.swift` | `feature/paywall/PaywallScreen.kt` | done | Play Billing wired; inert until subscriptionsEnabled |
| `Components/ScoreRing.swift` | `designsystem/component/ScoreRing.kt` | done | New-tier nil handling |
| `Components/Sparkline` | `designsystem/component/Sparkline.kt` | done | |
| `Screens/ArtistWizard/ArtistSocialsStep.swift` | `feature/wizard/WizardScreen.kt` (Socials) | done | |
| `Screens/ArtistWizard/ArtistBioStep.swift` | `feature/wizard/WizardScreen.kt` (Bio) | done | |
| `Screens/ArtistWizard/ArtistSamplesStep.swift` | `feature/wizard/WizardScreen.kt` (Samples) | done | SAF + UploadQueue after go-live |
| `Screens/ArtistWizard/ArtistPreviewStep.swift` | `feature/wizard/WizardScreen.kt` (Preview) | done | |
| `Screens/ArtistWizard/ArtistWizardDoneStep.swift` | `feature/wizard/WizardScreen.kt` (Done) | done | |
| `Screens/EPKView.swift` | `feature/epk/EpkScreen.kt` | done | Packages/tech/links/samples + photo grid/reorder + wizard CTA |

## Screens — Profile / Settings / Paywall (M6/M7)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ProfileView.swift` | `feature/profile/ProfileScreen.kt` | done | Identity + settings + calendar + Help/Feedback sheet |
| `Screens/Settings/DataExportView.swift` | `feature/profile/ProfileScreen.kt` (export row) | partial | Inline JSON share + signed URL open; no dedicated sheet |
| `Screens/Settings/ManageAvailabilityView.swift` | `feature/availability/ManageAvailabilityScreen.kt` | done | Days/times chips + seed-failure Save guard |
| `Screens/Settings/ScoreHistorySheet.swift` | `feature/score/ScoreHistorySheet.kt` | done | Sparkline + delta sheet from explainer |
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
| `State/SavedStore.swift` | `feature/saved/SavedStore.kt` | done | Optimistic toggle + prefs + sign-out reset |
| `State/BookingStore.swift` | `BookingViewModel` + `BookingDraftStore` | partial | Draft store + VM; no global booking list cache yet |
| `State/RequestStore.swift` | `ArtistHomeViewModel` (+ RequestsRepository) | done | Open quotes rail on Artist Home |
| `State/MessageStore.swift` | `MessagesViewModel` / `ChatViewModel` | done | Inbox filters + Chat Realtime/optimistic; no global MessageStore needed |
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
| `Services/VideoTrimmer.swift` | `platform/media/VideoTrimmer.kt` | done | Media3 Transformer ≤10s + copy fallback |
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
| `Components/HeaderBar.swift` | — | missing | |
| Theme tokens | `designsystem/theme/*` | done | Brand fonts TTF drop still operator (#15) |

---

## Explicitly deferred (not this wave)

- Brand font TTFs in `res/font/` — polish, not a product blocker
- Operator Google/Apple dashboard config + `google-services.json` / FCM server path
- ExoPlayer sample / Spotify embed playback
- Artist profile PROF-* Airbnb extras (hero pager, review search/sort)

---

## How to update

When a screen/repo lands: flip Status to `done` or `partial`, add the Android path,
and note any Jul-2026 deltas. Keep this file honest — stale rows hurt more than
missing ones.
