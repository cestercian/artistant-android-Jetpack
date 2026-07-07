# PROJECT_STRUCTURE.md — Artistant Android

The complete directory/package layout for the Android app. See ARCHITECTURE.md
for the decisions behind it.

> **Single Gradle module, package-by-feature.** `// ponytail:` We start with **one
> `:app` module** organized by feature package, not a multi-module Gradle graph.
> The iOS app is a single target; a solo/small team ships faster without
> inter-module wiring, and Compose build times are fine at this size. The package
> boundaries below are drawn so that **promoting a package to its own Gradle
> module is mechanical later** (core/designsystem/data are the natural first
> extractions when build times justify it). Requested folder names from the brief
> — `core, common, designsystem, navigation, features, data, domain, repositories,
> models, services, ui, viewmodels, utils` — are all present; `repositories`,
> `models`, `viewmodels`, and `ui` live **inside** the layer/feature that owns
> them (Android best practice: package-by-feature), rather than as global
> god-folders. The mapping is called out at the end.

---

## 1. Top-level

```
artistant-android/
├── build.gradle.kts                # root build script
├── settings.gradle.kts             # includes :app
├── gradle/
│   └── libs.versions.toml          # version catalog (all deps + versions)
├── local.properties                # SDK path + SECRETS (gitignored)
├── google-services.json            # FCM config (gitignored; per-flavor variants)
├── gradle.properties
├── docs/                           # these planning docs travel with the repo
│   ├── ANDROID_MIGRATION_PLAN.md
│   ├── SCREEN_INVENTORY.md
│   ├── API_MAPPING.md
│   ├── FEATURE_CHECKLIST.md
│   ├── ARCHITECTURE.md
│   ├── PROJECT_STRUCTURE.md
│   ├── IMPLEMENTATION_ROADMAP.md
│   └── RISKS_AND_DECISIONS.md
└── app/
    ├── build.gradle.kts            # module build: flavors dev/staging/prod, BuildConfig
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                # see §3
        │   └── java/in/artistant/app/   # see §2
        ├── dev/                    # flavor-specific (dev google-services.json, res)
        ├── prod/
        ├── test/                   # JVM unit tests (mirrors package tree)
        └── androidTest/            # Compose UI tests + Fake* Hilt module
```

Package root: **`in.artistant.app`** (matches the iOS bundle id `in.artistant.app`).

---

## 2. Source package tree (`app/src/main/java/in/artistant/app/`)

```
in/artistant/app/
│
├── ArtistantApplication.kt            # @HiltAndroidApp; Timber/Sentry/PostHog bootstrap
├── MainActivity.kt                    # single-activity host; sets ArtistantTheme + NavHost
│
├── core/                              # cross-cutting foundations (no feature knowledge)
│   ├── di/                            # Hilt modules
│   │   ├── SupabaseModule.kt          # provides SupabaseClient (flavored URL/key + tier guard)
│   │   ├── RepositoryModule.kt        # @Binds every Repository interface → Supabase impl
│   │   ├── ServiceModule.kt           # Auth, Push, Calendar, Upload, Media, Permissions
│   │   └── DispatcherModule.kt        # @IoDispatcher / @DefaultDispatcher qualifiers
│   ├── config/
│   │   └── AppEnvironment.kt          # reads BuildConfig (SUPABASE_URL, flags, product ids)
│   ├── network/
│   │   ├── SupabaseClientFactory.kt   # createSupabaseClient { install(...) }
│   │   └── errors/AppError.kt         # sealed error hierarchy + PostgREST code mapping
│   ├── result/Result.kt              # thin Result helpers if not using kotlin.Result
│   └── logging/
│       ├── Timber.kt
│       └── PiiScrub.kt                # pure redaction regex (shared w/ Sentry BeforeSend)
│
├── common/                            # tiny shared utilities, no Android-framework deps
│   └── util/
│       ├── Inr.kt                     # formatINR / formatINRShort (en_IN, lakh)
│       ├── DateTime.kt                # SupabaseISO8601, weekday formatting (en_US_POSIX)
│       ├── Ids.kt                     # lowercaseUuid()
│       └── Debounce.kt                # flow debounce helpers (handle-check, search)
│
├── designsystem/                      # the Theme/ port — the whole visual language
│   ├── theme/
│   │   ├── ArtistantTheme.kt          # MaterialTheme wrapper + CompositionLocals
│   │   ├── Color.kt                   # AppColors: bg/bgCard/ink*/brand/status + role accents
│   │   ├── Type.kt                    # AppType ramp + FontFamily (Instrument Serif/Geist[Mono])
│   │   ├── Dimens.kt                  # Space / Size / Radii / AspectRatios
│   │   └── Role.kt                    # AppRole; role→accent mapping (lime/violet)
│   └── component/                     # the 27-component port (reusable composables)
│       ├── button/PrimaryButton.kt, Pill.kt
│       ├── layout/CardView.kt, AppSection.kt, HeaderBar.kt, CtaScrim.kt, KVRow.kt, HRule.kt, FlowRow.kt
│       ├── media/MediaContainer.kt, AutoplayVideo.kt, SpotifyEmbed.kt, Avatar.kt, ArtistTile.kt
│       ├── chart/ScoreRing.kt, Sparkline.kt, StatusTimeline.kt
│       ├── calendar/MonthCalendar.kt, MiniMonthCalendar.kt, DateScroller.kt
│       ├── feedback/Toast.kt, Skeleton.kt, EmptyState.kt, UploadProgressBanner.kt
│       └── picker/MediaSourceSheet.kt, EditArtistLinkSheet.kt
│
├── navigation/                        # app-wide nav graph + routing types
│   ├── ArtistantNavHost.kt            # top-level NavHost; auth-gate → signup/wizard/tabs
│   ├── Routes.kt                      # @Serializable route types (ClientRoute/ArtistRoute)
│   ├── ClientTabsScaffold.kt          # bottom nav: Discover/Bookings/Messages/Profile/Search
│   ├── ArtistTabsScaffold.kt          # bottom nav: Home/Gigs/Messages/EPK
│   └── DeepLinkRouter.kt              # TabRouter analogue: nav-event SharedFlow + pending ids
│
├── data/                              # the data boundary (repositories + wire models)
│   ├── model/                         # domain + wire models (kotlinx.serialization)
│   │   ├── Artist.kt                  # Artist, ArtistPackage, Sample, Review, ArtistGradient
│   │   ├── Booking.kt                 # Booking, BookingStatus, EscrowStatus, PaymentMethod, compute()
│   │   ├── Messaging.kt               # Thread, Message, MessageSender, MessageDelivery
│   │   ├── GigRequest.kt              # GigRequest, StoredRequest, GigRequestStatus
│   │   ├── Media.kt                   # ArtistMediaItem, kind/aspect enums
│   │   ├── Score.kt                   # ScoreBreakdown, ScoreTier, ScoreHistoryPoint
│   │   ├── Search.kt                  # SearchFilters, SearchSort, SearchCursor, SearchPage, facets
│   │   ├── Subscription.kt            # SubscriptionProduct (Play Billing later)
│   │   └── dto/                       # @Serializable DB* row structs (exact column names)
│   │       ├── DbArtist.kt, DbPackage.kt, DbSample.kt, DbArtistMedia.kt ...
│   │       ├── DbBooking.kt, DbBookingWithClient.kt
│   │       ├── DbThread.kt, DbMessage.kt, DbGigRequestWithClient.kt ...
│   │       └── SearchArtistRow.kt, SearchFacetRow.kt, DbScoreMetrics.kt ...
│   └── repository/                    # interface + Supabase impl + Fake twin, per repo
│       ├── ArtistsRepository.kt (+ SupabaseArtistsRepository.kt, FakeArtistsRepository.kt)
│       ├── BookingsRepository.kt      ├── UsersRepository.kt
│       ├── MessagesRepository.kt      ├── RequestsRepository.kt
│       ├── ReviewsRepository.kt       ├── ScoreRepository.kt
│       ├── SearchRepository.kt        ├── ArtistLinksRepository.kt
│       ├── PackagesRepository.kt      ├── SavedArtistsRepository.kt
│       ├── ArtistMediaRepository.kt   ├── SamplesRepository.kt
│       └── TechRiderRepository.kt
│
├── domain/                            # pure logic isolated for unit tests (thin — see ARCH §2)
│   ├── booking/BookingMath.kt         # 5% platform + 18% GST (port of Booking.compute)
│   ├── score/ScoreBands.kt            # ScoreTier bands + <5-gig "New" rule
│   ├── chat/Redaction.kt              # PII redaction regexes + shouldRedact()
│   ├── calendar/SyncPlanner.kt        # pure desired-vs-persisted diff (port of plan())
│   └── auth/ReturningLoginRoute.kt    # routeIn/onboard/degrade decision (pure, testable)
│
├── platform/                          # the Services/ port — OS + third-party integrations
│   ├── auth/
│   │   ├── SessionManager.kt          # supabase-kt Auth wrapper; sessionStatus Flow; signInGeneration
│   │   ├── GoogleSignIn.kt            # Credential Manager → ID token → supabase
│   │   ├── AppleSignIn.kt             # Custom Tabs OAuth web flow + nonce (SecureRandom/SHA-256)
│   │   └── EmailAuth.kt
│   ├── push/
│   │   ├── ArtistantMessagingService.kt   # FirebaseMessagingService: onNewToken + onMessageReceived
│   │   └── PushDeepLink.kt             # maps artistant_* payload keys → DeepLinkRouter
│   ├── calendar/CalendarSyncService.kt # CalendarContract mirror + clash/busy reads
│   ├── permissions/PermissionsController.kt
│   ├── upload/
│   │   ├── UploadWorker.kt             # WorkManager worker (photo/video/audio/publish-flag)
│   │   └── UploadQueue.kt              # enqueue API + policy (foreign-user purge, publish gate)
│   ├── media/
│   │   ├── VideoTrimmer.kt             # Media3 Transformer trim ≤10s + first-frame
│   │   ├── SamplePlayer.kt             # ExoPlayer for bundled/remote audio
│   │   └── MediaRegistry.kt            # category → placeholder drawable/raw
│   ├── billing/                        # Play Billing (dormant v1)
│   │   ├── SubscriptionService.kt      # queryProductDetails/launchBillingFlow/purchases
│   │   └── EntitlementStore.kt         # obfuscatedAccountId binding; entitlement mirror
│   ├── storage/
│   │   ├── AppPreferences.kt           # DataStore (Persistence port; artistant.state.* keys)
│   │   └── WizardMediaCache.kt         # cacheDir/artist-wizard/ pending media
│   └── observability/
│       ├── Analytics.kt                # PostHog wrapper + event allowlist
│       └── Crash.kt                    # Sentry init + BeforeSend PiiScrub
│
├── feature/                           # one package per screen-cluster (UI + ViewModel + state)
│   ├── signup/                        # SHARED onboarding flow
│   │   ├── SignupFlow.kt, WelcomeScreen.kt, RoleScreen.kt, AuthScreen.kt,
│   │   ├── EmailAuthScreen.kt, ProfileScreen.kt, NotifPermissionScreen.kt, DoneScreen.kt, LegalScreen.kt
│   │   ├── SignupViewModel.kt         # onboarding state machine (steps, handle check)
│   │   └── SignupUiState.kt
│   ├── wizard/                        # ARTIST onboarding wizard (11 steps)
│   │   ├── WizardScaffold.kt, steps/*.kt (Identity, Location, Pricing, Tech, Availability,
│   │   │                               Cover, Socials, Bio, Samples, Preview, Done)
│   │   ├── WizardViewModel.kt         # wizard state (flowOrder, per-step validation, pending media)
│   │   └── WizardUiState.kt
│   ├── discover/                      # CLIENT tab: DiscoverScreen + DiscoverViewModel (6 rails)
│   ├── search/                        # CLIENT tab: SearchScreen + FilterSheet + SearchViewModel
│   ├── artist/                        # CLIENT: ArtistProfileScreen + ArtistProfileViewModel (+ loader)
│   ├── booking/                       # CLIENT: BookingScreen, CheckoutScreen, ConfirmedScreen, RequestQuoteScreen
│   ├── bookings/                      # CLIENT tab: BookingsScreen (calendar) + BookingDetailScreen (shared)
│   ├── messages/                      # SHARED tab: MessagesScreen + ChatScreen + Message/ChatViewModel
│   ├── profile/                       # CLIENT tab: ProfileScreen + settings (DataExport, calendar sync)
│   ├── artisthome/                    # ARTIST tab: ArtistHomeScreen + ArtistHomeViewModel (dashboard)
│   ├── gigs/                          # ARTIST tab: ArtistGigsScreen (calendar)
│   ├── gigrequest/                    # ARTIST: GigRequestDetailScreen + accept/decline/counter
│   ├── epk/                           # ARTIST tab: EpkScreen (profile editor) + EpkViewModel
│   ├── score/                         # ScoreExplainer, ScoreBreakdownSheet, ScoreHistorySheet
│   ├── paywall/                       # PaywallScreen (Play Billing; dormant)
│   └── availability/                  # ManageAvailabilityScreen
│
└── ui/                                # shared UI plumbing not tied to one feature
    ├── ArtistantRoot.kt               # auth-gate composable (signed-in? role? setup?)
    ├── ObserveAsEvents.kt             # collect one-shot event Flows in a LaunchedEffect
    ├── Haptics.kt                     # HapticFeedback wrappers (selection/success/error)
    └── modifier/                      # shared Modifiers (pressScale, shimmer, bottomScrim)
```

---

## 3. Resources (`app/src/main/res/`)

```
res/
├── font/                    # OFL brand fonts (same .ttf as iOS)
│   ├── instrument_serif_regular.ttf, instrument_serif_italic.ttf
│   ├── geist_regular.ttf … geist_black.ttf
│   └── geist_mono_medium.ttf … geist_mono_bold.ttf
├── drawable/                # app icon, category placeholders (MediaRegistry), vector icons
├── raw/                     # bundled demo audio (dev flavor only) if a demo mode is kept
├── values/
│   ├── strings.xml          # UI copy + permission rationale strings
│   ├── themes.xml           # base Theme.Material3 (dark), status-bar, splash
│   └── colors.xml           # launch/splash bg #0A0A0A (Color.bg)
├── xml/
│   ├── file_paths.xml       # FileProvider paths (camera capture, export share)
│   └── data_extraction_rules.xml / backup_rules.xml
└── mipmap-*/                # launcher icon densities
```

**AndroidManifest.xml** declares: single `MainActivity` (`android:launchMode`
default, `windowSoftInputMode=adjustResize`), the OAuth deep-link
`intent-filter` (scheme `in.artistant.app`, host `login-callback`) + the calendar
deep-link (`//booking/{id}`), `ArtistantMessagingService` (FCM), a `FileProvider`,
and permissions (`INTERNET`, `CAMERA`, `RECORD_AUDIO`, `ACCESS_*_LOCATION`,
`POST_NOTIFICATIONS`, `READ_CALENDAR`, `WRITE_CALENDAR`, `READ_MEDIA_*` for the
back-compat photo path). Portrait-only, dark-only via theme.

---

## 4. Mapping the brief's requested folders → this layout

| Brief folder | Where it lives here | Note |
|---|---|---|
| `app` | `:app` module + `ArtistantApplication`/`MainActivity` | Single module. |
| `core` | `core/` | DI, config, network, errors, logging. |
| `common` | `common/util/` | Pure utils (INR, datetime, ids). |
| `designsystem` | `designsystem/` | Theme + 27 components. |
| `navigation` | `navigation/` | NavHost, routes, tab scaffolds, deep-link router. |
| `features` | `feature/*` | Package-per-screen-cluster. |
| `data` | `data/` | Repositories + models + DTOs. |
| `domain` | `domain/` | Thin — only isolated pure logic. |
| `repositories` | `data/repository/` | Under `data` (Android convention), not global. |
| `models` | `data/model/` (+ `data/model/dto`) | Domain + wire models. |
| `services` | `platform/` | Renamed "platform" — clearer that it's OS/3rd-party integration. |
| `ui` | `ui/` + each `feature/*` screen | Shared UI plumbing global; screens live with their feature. |
| `viewmodels` | inside each `feature/*` | Co-located with the screen they drive (not a god-folder). |
| `utils` | `common/util/` + `ui/modifier/` | Split by whether they touch Android framework. |

> **Why `viewmodels`/`ui`/`repositories`/`models` aren't top-level god-folders.**
> Package-by-layer ("all viewmodels here, all screens there") scatters one
> feature across five folders and scales badly. Package-by-feature keeps a
> screen, its ViewModel, and its UI state together, so a feature is one place to
> read and one place to change — and it's the modern Android (and Now-in-Android)
> convention. Shared/cross-feature things (design system, data models,
> navigation) stay in their own top-level packages. The brief's flat list is
> honored by name; the *placement* follows best practice, per the brief's own
> instruction to "prefer Android best practices over copying iOS patterns."
