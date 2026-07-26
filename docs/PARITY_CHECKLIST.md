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
| `Screens/Signup/SignupNotifPermissionView.swift` | `feature/signup/NotifPermissionScreen.kt` | done | M1; FCM register deferred M4 |
| `Screens/Signup/SignupDoneView.swift` | `feature/signup/DoneScreen.kt` | done | M1 |
| `Screens/Signup/LegalView.swift` | `feature/signup/LegalScreen.kt` | done | M1 |
| `Screens/Signup/SignupFlowView.swift` | `feature/signup/SignupFlow.kt` + `SignupViewModel` | done | M1 |
| `Services/AuthService.swift` / Root gate | `SessionManager` + `ArtistantNavHost` / `RootViewModel` | done | M1 |
| `Services/AppleSignInController.swift` | `SessionManager` Custom Tabs + deep link | partial | #12 Apple OAuth error handling open |

## Screens — Browse (M2)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/DiscoverView.swift` | `feature/discover/DiscoverScreen.kt` | missing | M2 — rails via concurrent `search_artists` |
| `State/DiscoverFeedStore.swift` | `feature/discover/DiscoverViewModel.kt` | missing | M2 |
| `Screens/SearchView.swift` | `feature/search/SearchScreen.kt` | missing | M2 |
| `Screens/SearchFilterSheet.swift` | `feature/search/SearchFilterSheet.kt` | missing | M2; accordion + histogram |
| `State/SearchStore.swift` | `feature/search/SearchViewModel.kt` | missing | M2 |
| `Screens/ArtistView.swift` | `feature/artist/ArtistProfileScreen.kt` | missing | M2; Book/Message CTAs stub until M3/M4 |
| `Screens/ScoreExplainerView.swift` | `feature/artist/ScoreExplainerScreen.kt` | missing | M2/M5 |
| `Screens/ScoreBreakdownSheet.swift` | `feature/artist/ScoreBreakdownSheet.kt` | missing | M2/M5 |
| `Screens/ArtistListView.swift` | (unused / list helper) | obsolete | Discover/Search replaced roster lists |

## Screens — Booking (M3)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/BookingView.swift` | `feature/booking/BookingScreen.kt` | missing | Request funnel → `pending_confirm` |
| `Screens/CheckoutView.swift` | `feature/booking/CheckoutScreen.kt` | missing | Matchmaker; mock payment dormant |
| `Screens/ConfirmedView.swift` | `feature/booking/ConfirmedScreen.kt` | missing | Copy: “Request sent.” |
| `Screens/RequestQuoteView.swift` | `feature/booking/RequestQuoteScreen.kt` | missing | Gig-request create |
| `Screens/BookingDetailView.swift` | `feature/booking/BookingDetailScreen.kt` | missing | Role-aware; artist Accept/Decline |
| `Screens/BookingsView.swift` | `feature/bookings/BookingsScreen.kt` | missing | Client calendar |
| `Screens/ReviewSheet.swift` | `feature/booking/ReviewSheet.kt` | missing | |

## Screens — Messages (M4)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/MessagesView.swift` | `feature/messages/MessagesScreen.kt` | missing | Verbatim previews — **no** redaction |
| `Screens/ChatView.swift` | `feature/messages/ChatScreen.kt` | missing | System rows + receipts; Airbnb trust banner |
| `Screens/ThreadDetailsSheet.swift` | `feature/messages/ThreadDetailsSheet.kt` | missing | |

## Screens — Artist home / gigs (M3/M5)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ArtistHomeView.swift` | `feature/artisthome/ArtistHomeScreen.kt` | missing | “New requests” rail |
| `Screens/ArtistGigsView.swift` | `feature/gigs/ArtistGigsScreen.kt` | missing | |
| `Screens/GigRequestDetailView.swift` | `feature/gigs/GigRequestDetailScreen.kt` | missing | Accept/Decline/counter |

## Screens — Wizard / EPK (M5)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ArtistWizard/ArtistWizardView.swift` | `feature/wizard/WizardScreen.kt` | missing | |
| `Screens/ArtistWizard/ArtistIdentityStep.swift` | `feature/wizard/IdentityStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistLocationStep.swift` | `feature/wizard/LocationStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistPricingStep.swift` | `feature/wizard/PricingStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistTechStep.swift` | `feature/wizard/TechStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistAvailabilityStep.swift` | `feature/wizard/AvailabilityStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistCoverStep.swift` | `feature/wizard/CoverStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistSocialsStep.swift` | `feature/wizard/SocialsStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistBioStep.swift` | `feature/wizard/BioStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistSamplesStep.swift` | `feature/wizard/SamplesStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistPreviewStep.swift` | `feature/wizard/PreviewStep.kt` | missing | |
| `Screens/ArtistWizard/ArtistWizardDoneStep.swift` | `feature/wizard/DoneStep.kt` | missing | |
| `Screens/EPKView.swift` | `feature/epk/EPKScreen.kt` | missing | |

## Screens — Profile / Settings / Paywall (M6/M7)

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Screens/ProfileView.swift` | `feature/profile/ProfileScreen.kt` | missing | |
| `Screens/Settings/DataExportView.swift` | `feature/settings/DataExportScreen.kt` | missing | |
| `Screens/Settings/ManageAvailabilityView.swift` | `feature/settings/ManageAvailabilityScreen.kt` | missing | |
| `Screens/Settings/ScoreHistorySheet.swift` | `feature/settings/ScoreHistorySheet.kt` | missing | |
| `Screens/PaywallView.swift` | `feature/paywall/PaywallScreen.kt` | missing | Inert behind flag |

## Navigation / shells

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| Root / role tabs | `ArtistantRoot` + `ClientTabsScaffold` / `ArtistTabsScaffold` | partial | Discover + Search + Artist profile wired; other tabs still Placeholder |
| `State/TabRouter.swift` | nav + deep-link pending channels | partial | Routes declared in `Routes.kt`; profile/search registered in tab NavHost |
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
| `Repositories/MessagesRepository.swift` | `data/repository/MessagesRepository.kt` + Fake | missing | M4 — verbatim `body`; `kind`/`action_route`; `thread_reads`; **no** redact |
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
| `State/BookingStore.swift` | `BookingViewModel` | missing | M3 |
| `State/RequestStore.swift` | `RequestViewModel` | missing | M3 |
| `State/MessageStore.swift` | `MessagesViewModel` / `ChatViewModel` | missing | M4 |
| `State/EPKStore.swift` | `EPKViewModel` | missing | M5 |
| `State/ArtistOnboardingStore.swift` | `WizardViewModel` | missing | M5 |
| `State/EntitlementStore.swift` | `EntitlementViewModel` | missing | M7 inert |
| `State/TabRouter.swift` | deep-link pending in Root / nav | missing | M4 |
| `State/Persistence.swift` | `AppPreferences` (DataStore) | done | M0 |
| `State/WizardMediaCache.swift` | `WizardMediaCache` | missing | M5 |

---

## Services

| iOS path | Android target | Status | Notes |
|---|---|---|---|
| `Services/SupabaseClient.swift` | `SupabaseClientFactory` + `SupabaseModule` | done | M0; tier guard |
| `Services/AppEnvironment.swift` | `core/config/AppEnvironment.kt` | done | M0 |
| `Services/AuthService.swift` | `platform/auth/SessionManager.kt` | done | M1 |
| `Services/AccountService.swift` | (delete / anonymize) | missing | M6 |
| `Services/PushService.swift` | FCM service | missing | M4 + backend FCM path |
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
