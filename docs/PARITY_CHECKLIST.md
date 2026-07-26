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

**Android current:** M0 + M1 complete; **M2 Browse landed** on `feature/m2-browse`
(Discover + Search + Artist profile). Next: M3 booking request→accept.

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
| `Services/AppleSignInController.swift` | `SessionManager` Custom Tabs + deep link | partial | #12 Apple OAuth error handling open |

## Screens — Browse (M2)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/DiscoverView.swift` | `feature/discover/DiscoverScreen.kt` | done | M2 — rails via concurrent `search_artists` |
| `State/DiscoverFeedStore.swift` | `feature/discover/DiscoverViewModel.kt` | done | M2 |
| `Screens/SearchView.swift` | `feature/search/SearchScreen.kt` | partial | M2 — query + facets + sort; filter sheet/histogram deferred |
| `Screens/SearchFilterSheet.swift` | `feature/search/SearchFilterSheet.kt` | missing | M2 follow-up; accordion + histogram |
| `State/SearchStore.swift` | `feature/search/SearchViewModel.kt` | partial | M2 — debounce + pagination; 0073 dims deferred |
| `Screens/ArtistView.swift` | `feature/artist/ArtistProfileScreen.kt` | partial | M2 — hero/bio/packages/reviews/dock; Book/Message stubbed; Request quote wired M3 |
| `Screens/ScoreExplainerView.swift` | `feature/artist/ScoreExplainerScreen.kt` | missing | M2/M5 |
| `Screens/ScoreBreakdownSheet.swift` | `feature/artist/ScoreBreakdownSheet.kt` | missing | M2/M5 |
| `Screens/ArtistListView.swift` | (unused / list helper) | obsolete | Discover/Search replaced roster lists |

## Screens — Booking (M3)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/BookingView.swift` | `feature/booking/BookingScreen.kt` | done | Request funnel → `pending_confirm` |
| `Screens/CheckoutView.swift` | `feature/booking/CheckoutScreen.kt` | done | Matchmaker; mock payment dormant |
| `Screens/ConfirmedView.swift` | `feature/booking/ConfirmedScreen.kt` | done | Copy: “Request sent.” |
| `Screens/RequestQuoteView.swift` | `feature/booking/RequestQuoteScreen.kt` | done | Gig-request create |
| `Screens/BookingDetailView.swift` | `feature/booking/BookingDetailScreen.kt` | partial | Role-aware; artist Accept/Decline; simplified vs iOS glass sheet |
| `Screens/BookingsView.swift` | `feature/bookings/BookingsScreen.kt` | done | Client calendar list |
| `Screens/ReviewSheet.swift` | `feature/booking/ReviewSheet.kt` | done | Minimal insert sheet |

## Screens — Messages (M4)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/MessagesView.swift` | `feature/messages/MessagesScreen.kt` | partial | Server inbox + push pendingThread deep link; filters deferred |
| `Screens/ChatView.swift` | `feature/messages/ChatScreen.kt` | partial | Realtime + optimistic send/retry; system rows + trust banner; report/details deferred |
| `Screens/ThreadDetailsSheet.swift` | `feature/messages/ThreadDetailsSheet.kt` | missing | |

## Screens — Artist home / gigs (M3/M5)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ArtistHomeView.swift` | `feature/artisthome/ArtistHomeScreen.kt` | partial | M3 — "New requests" rail; full dashboard deferred |
| `Screens/ArtistGigsView.swift` | `feature/gigs/ArtistGigsScreen.kt` | partial | M3 — month list; full calendar grid deferred |
| `Screens/GigRequestDetailView.swift` | `feature/gigs/GigRequestDetailScreen.kt` | done | Accept/Decline/counter |

## Screens — Wizard / EPK (M5)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ArtistWizard/ArtistWizardView.swift` | `feature/wizard/WizardScreen.kt` | partial | M5 scaffold — inline steps, no CameraX |
| `Screens/ArtistWizard/ArtistIdentityStep.swift` | `feature/wizard/WizardScreen.kt` (Identity) | partial | Inline form in host |
| `Screens/ArtistWizard/ArtistLocationStep.swift` | `feature/wizard/WizardScreen.kt` (Location) | partial | |
| `Screens/ArtistWizard/ArtistPricingStep.swift` | `feature/wizard/WizardScreen.kt` (Pricing) | partial | packages table write deferred |
| `Screens/ArtistWizard/ArtistTechStep.swift` | `feature/wizard/WizardScreen.kt` (Tech) | partial | tech_rider write deferred |
| `Screens/ArtistWizard/ArtistAvailabilityStep.swift` | `feature/wizard/WizardScreen.kt` (Availability) | partial | |
| `Screens/ArtistWizard/ArtistCoverStep.swift` | `feature/wizard/WizardScreen.kt` (Cover) | partial | CameraX placeholder + gradient |
| `Screens/ArtistWizard/ArtistSocialsStep.swift` | `feature/wizard/WizardScreen.kt` (Socials) | partial | |
| `Screens/ArtistWizard/ArtistBioStep.swift` | `feature/wizard/WizardScreen.kt` (Bio) | partial | |
| `Screens/ArtistWizard/ArtistSamplesStep.swift` | `feature/wizard/WizardScreen.kt` (Samples) | partial | SAF/upload placeholder |
| `Screens/ArtistWizard/ArtistPreviewStep.swift` | `feature/wizard/WizardScreen.kt` (Preview) | partial | |
| `Screens/ArtistWizard/ArtistWizardDoneStep.swift` | `feature/wizard/WizardScreen.kt` (Done) | partial | |
| `Screens/EPKView.swift` | `feature/epk/EpkScreen.kt` | partial | Read-only shell + Edit in wizard CTA |

## Screens — Profile / Settings / Paywall (M6/M7)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ProfileView.swift` | `feature/profile/ProfileScreen.kt` | partial | Identity + settings rows; stats/saved/calendar sync deferred |
| `Screens/Settings/DataExportView.swift` | `feature/profile/ProfileScreen.kt` (export row) | partial | Inline JSON share + signed URL open; no dedicated sheet |
| `Screens/Settings/ManageAvailabilityView.swift` | `feature/profile/ProfileScreen.kt` (stub) | partial | Placeholder toast only |
| `Screens/Settings/ScoreHistorySheet.swift` | `feature/settings/ScoreHistorySheet.kt` | missing | |
| `Screens/PaywallView.swift` | `feature/paywall/PaywallScreen.kt` | partial | Inert behind `subscriptionsEnabled=false` |

## Navigation / shells

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| Root / role tabs | `ArtistantRoot` + `ClientTabsScaffold` / `ArtistTabsScaffold` | partial | Discover + Search + Artist profile wired; other tabs still Placeholder |
| `State/TabRouter.swift` | nav + deep-link pending channels | done | `TabRouter` singleton + client/artist scaffold consumers |
| `ClientRoute.ArtistProfile` / `Search` | `Routes.kt` + NavHost | partial | Wired as string routes in ClientTabsScaffold |

---

## Repositories

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Repositories/UsersRepository.swift` | `data/repository/UsersRepository.kt` + Fake | done | M1 |
| `Repositories/ArtistsRepository.swift` | `data/repository/ArtistsRepository.kt` + Fake | done | M2 — id-keyed hydrating cache |
| `Repositories/SearchRepository.swift` | `data/repository/SearchRepository.kt` + Fake | done | M2 |
| `Repositories/SearchTypes.swift` | `data/model/SearchTypes.kt` | done | M2 |
| `Repositories/SavedArtistsRepository.swift` | `data/repository/SavedArtistsRepository.kt` + Fake | missing | M2 profile rails / Saved |
| `Repositories/ReviewsRepository.swift` | `data/repository/ReviewsRepository.kt` + Fake | done | M2 profile (listForArtist) |
| `Repositories/PackagesRepository.swift` | `data/repository/PackagesRepository.kt` + Fake | missing | M2/M5 |
| `Repositories/ArtistMediaRepository.swift` | `data/repository/ArtistMediaRepository.kt` | missing | M2 covers / M5 upload |
| `Repositories/ScoreRepository.swift` | `data/repository/ScoreRepository.kt` + Fake | missing | M2/M5 |
| `Repositories/BookingsRepository.swift` | `data/repository/BookingsRepository.kt` + Fake | missing | M3 — request→accept |
| `Repositories/RequestsRepository.swift` | `data/repository/RequestsRepository.kt` + Fake | missing | M3 |
| `Repositories/MessagesRepository.swift` | `data/repository/MessagesRepository.kt` + Fake | partial | Explicit projections, 0072 fallback, receipts; Realtime INSERT subscribe wired |
| `Repositories/SamplesRepository.swift` | `data/repository/SamplesRepository.kt` | missing | M5 |
| `Repositories/TechRiderRepository.swift` | `data/repository/TechRiderRepository.kt` | missing | M5 |
| `Repositories/ArtistLinksRepository.swift` | `data/repository/ArtistLinksRepository.kt` | missing | M5 |

---

## State / stores → ViewModels

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `State/OnboardingStore.swift` / signup | `SignupViewModel` | done | M1 |
| `State/RoleStore.swift` | `AppPreferences` + RootViewModel | partial | Role persisted; store shape differs |
| `State/DiscoverFeedStore.swift` | `DiscoverViewModel` | done | M2 |
| `State/SearchStore.swift` | `SearchViewModel` | partial | M2 |
| `State/SavedStore.swift` | `SavedViewModel` / prefs | missing | M2/M6 |
| `State/BookingStore.swift` | `BookingViewModel` + `BookingDraftStore` | partial | Draft store + VM; no global booking list cache yet |
| `State/RequestStore.swift` | `RequestViewModel` | missing | M3 |
| `State/MessageStore.swift` | `MessagesViewModel` / `ChatViewModel` | partial | Inbox + Chat with Realtime/optimistic reconcile; no global MessageStore yet |
| `State/EPKStore.swift` | `EpkViewModel` | partial | M5 scaffold — fetch-only shell |
| `State/ArtistOnboardingStore.swift` | `WizardViewModel` | partial | M5 scaffold — no persistence/media |
| `State/EntitlementStore.swift` | `feature/paywall/EntitlementStore.kt` | partial | M7 inert — always not-subscribed when flag off |
| `State/TabRouter.swift` | `navigation/TabRouter.kt` | done | pendingThread/booking/gigRequest + role tabs |
| `State/Persistence.swift` | `AppPreferences` (DataStore) | done | M0 |
| `State/WizardMediaCache.swift` | `WizardMediaCache` | missing | M5 |

---

## Services

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Services/SupabaseClient.swift` | `SupabaseClientFactory` + `SupabaseModule` | done | M0; tier guard |
| `Services/AppEnvironment.swift` | `core/config/AppEnvironment.kt` | partial | M0 + subscriptionsEnabled/legal URLs (flag default off) |
| `Services/AuthService.swift` | `platform/auth/SessionManager.kt` | done | M1 |
| `Services/AccountService.swift` | `data/repository/AccountRepository.kt` + Fake | done | M6 — delete-account + data-export EFs |
| `Services/PushService.swift` | `platform/push/PushService.kt` + MessagingService | partial | claim_device_token FCM register + payload→TabRouter; needs operator google-services.json + send-push FCM |
| `Services/CalendarSyncService.swift` | CalendarContract mirror | missing | M6 |
| `Services/PermissionsService.swift` | `NotificationPermission` etc. | partial | Notif permission UI M1 |
| `Services/UploadQueue.swift` | WorkManager upload | missing | M5 |
| `Services/VideoTrimmer.swift` | Media3 Transformer | missing | M5 |
| `Services/Payments/*` | Play Billing seam | missing | M7 dormant |
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
| `Components/MediaContainer.swift` | `designsystem/component/MediaContainer.kt` | missing | M2 |
| `Components/EmptyStateView.swift` | `designsystem/component/EmptyState.kt` | done | M2 |
| `Components/Avatar.swift` | `designsystem/component/Avatar.kt` | missing | M2 |
| `Components/Skeleton.swift` | `designsystem/component/Skeleton.kt` | missing | M2 |
| `Components/ScoreRing.swift` | `designsystem/component/ScoreRing.kt` | missing | M2 |
| `Components/HeaderBar.swift` | — | missing | |
| Theme tokens | `designsystem/theme/*` | done | Brand fonts TTF drop still operator (#15) |

---

## Explicitly deferred (not this wave)

- M3 booking request→accept + role-aware BookingDetail / artist Accept
- M4 chat without redaction (system messages, receipts, ThreadContext)
- Artist Home “New requests”, wizard, EPK, calendar sync, FCM client, Play Billing
- Brand font TTFs in `res/font/` — polish, not M2 blocker
- Operator Google/Apple dashboard config (issues #12 / #15)

---

## How to update

When a screen/repo lands: flip Status to `done` or `partial`, add the Android path,
and note any Jul-2026 deltas. Keep this file honest — stale rows hurt more than
missing ones.
