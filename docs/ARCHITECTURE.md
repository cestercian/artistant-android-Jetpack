# ARCHITECTURE.md — Artistant Android

The target architecture for the native Android rewrite of Artistant (Kotlin +
Jetpack Compose), sharing the existing Supabase backend unchanged. This document
covers the app architecture, every cross-cutting decision, and the recommended
library stack, with the rationale for each choice.

> **Guiding principle.** The iOS app is a clean, protocol-seamed MVVM app with a
> **server-is-truth** data model (no local database — the "GRDB cache" was
> removed as dead code). We copy the *shape* (repository seam, per-role tab
> graphs, design-token system) but adopt **Android-native idioms** (ViewModel +
> StateFlow, Hilt, Navigation-Compose, WorkManager) rather than porting SwiftUI
> patterns literally. We deliberately do **not** introduce complexity the iOS app
> proved it doesn't need (no Room/offline-first, no multi-module Gradle up front).

---

## 1. Stack at a glance

| Concern | Choice | Why |
|---|---|---|
| Language | **Kotlin** (2.0+), JDK 17 | Standard; Compose compiler is a Kotlin plugin from 2.0. |
| UI toolkit | **Jetpack Compose** + Material 3 | The Compose analogue of SwiftUI; declarative, matches the iOS mental model. |
| Architecture | **MVVM + UDF** (unidirectional data flow) | Android-standard; mirrors iOS `ObservableObject` → `@Published` as ViewModel → `StateFlow`. |
| Navigation | **Navigation-Compose** (type-safe routes) | Direct analogue of the two `Route` enums + `NavigationStack(path:)`. |
| DI | **Hilt** | The standard; replaces iOS's `.environmentObject` injection + `.shared` singletons. |
| Backend SDK | **supabase-kt** (`io.github.jan-tennert.supabase`) | The community Kotlin port of supabase-swift: Auth, Postgrest, Realtime, Storage, Functions. 1:1 with the iOS surface. |
| Async | **Coroutines + Flow** | Replaces Swift `async/await` + Combine `@Published`. |
| Serialization | **kotlinx.serialization** | What supabase-kt uses natively; replaces Swift `Codable`. |
| Image loading | **Coil 3** | Compose-native `AsyncImage`; direct replacement for SwiftUI `AsyncImage`. |
| Video/audio | **Media3** (ExoPlayer + Transformer) | Replaces AVPlayer (playback), AVAudioPlayer (samples), AVAssetExportSession (trim). |
| Camera | **CameraX** / capture intents | Replaces `UIImagePickerController`. |
| Media pick | **Photo Picker** (`PickVisualMedia`) + **SAF** (`OpenDocument`) | Replaces PhotosUI + `UIDocumentPickerViewController`. |
| Background work | **WorkManager** | Replaces the hand-rolled `UploadQueue` (persistence + retry are built in). |
| Local storage | **DataStore (Preferences)** | Replaces `UserDefaults`/`Persistence`. **No Room** initially (see §7). |
| Push | **Firebase Cloud Messaging** | Replaces APNs. Requires a backend `send-push` change (see API_MAPPING). |
| Billing | **Google Play Billing** | Replaces StoreKit 2. Dormant in v1, same as iOS. |
| Calendar | **Calendar Provider** + `ACTION_INSERT` | Replaces EventKit / EventKitUI. |
| Auth (Google) | **Credential Manager** + Google Identity | Native Google sign-in → ID token → supabase-kt. |
| Auth (Apple) | **Custom Tabs** OAuth web flow | Sign in with Apple has no native Android SDK. |
| Analytics/crash | **PostHog Android** + **Sentry Android** | Same two SDKs as iOS; dark-until-key. |
| Logging | **Timber** | Tiny, idiomatic; wraps `Log`. |
| Testing | **JUnit + MockK + Turbine + Compose UI Test** | Flow assertions via Turbine; Compose semantics for UI. |
| Build | **Gradle KTS + version catalog** (`libs.versions.toml`) | Standard modern Gradle. |

**Min/target SDK.** `minSdk = 26` (Android 8.0 — clears >95% of active devices and
avoids a pile of pre-Oreo notification/permission back-compat), `targetSdk =
compileSdk = 36` (Android 16 — the only platform installed on the build machine;
`build-tools 36.1.0`). The iOS app
is iOS 26 / iPhone-only / portrait-only / dark-only — the Android app is likewise
**phone-first, portrait, dark-only** (see §8).

---

## 2. Layered architecture

Four layers, data flows top-down, each layer talks only to the one below —
identical discipline to the iOS app (Screens → State → Repositories → Services →
Supabase).

```
┌──────────────────────────────────────────────────────────────┐
│  UI (Compose)         screens + components + design system    │  @Composable
│    observes ▼         one screen = one composable + VM        │
├──────────────────────────────────────────────────────────────┤
│  Presentation         ViewModels — StateFlow<UiState>         │  ViewModel
│    calls ▼            + one-shot event Channel/SharedFlow     │
├──────────────────────────────────────────────────────────────┤
│  Domain (optional)    models + use-cases where logic is       │  pure Kotlin
│    calls ▼            non-trivial (money math, score, redact) │
├──────────────────────────────────────────────────────────────┤
│  Data                 Repository interfaces + Supabase impls  │  interface + impl
│    calls ▼            (+ Fake* twins for tests)               │
├──────────────────────────────────────────────────────────────┤
│  Platform services    Auth, Push(FCM), Calendar, Upload(WM),  │  singletons via Hilt
│                       Media, Permissions, Billing, Observ.    │
└──────────────────────────────────────────────────────────────┘
                              ▼
                    Supabase (shared, unchanged)
```

**Why a thin/optional domain layer.** The iOS app has **no** use-case layer —
logic lives in stores and repos. Most of this app is CRUD over Supabase, so a
full Clean-Architecture use-case-per-action layer would be ceremony. We keep
domain to **pure Kotlin where the logic is genuinely non-trivial and must be
unit-tested in isolation**: `Booking.compute` (5% platform + 18% GST), the
Bookability score bands (`ScoreTier`), the PII **redaction** regexes, and the
calendar **sync planner**. Everything else: ViewModel → Repository directly.
`// ponytail:` use-cases only where the iOS app itself isolated the logic into a
pure, separately-tested function.

---

## 3. Presentation: MVVM + UDF

**Mapping from iOS.** iOS `ObservableObject` stores with `@Published` properties,
injected via `.environmentObject`, become **ViewModels exposing a single
`StateFlow<UiState>`** plus a channel for one-shot events (navigation, toasts,
haptics). Compose collects state with `collectAsStateWithLifecycle()`.

```kotlin
data class DiscoverUiState(
    val rails: DiscoverRails = DiscoverRails(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val search: SearchRepository,
    private val artists: ArtistsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()
    // load() fans out concurrent search_artists calls — mirrors DiscoverFeedStore
}
```

**Two ViewModel scopes** (mirrors iOS's "injected at root" vs "created per-screen"):

- **Screen-scoped ViewModels** (Hilt `@HiltViewModel`, one per screen) — Discover,
  Search, ArtistProfile, Booking, Chat, etc. Analogue of the iOS per-screen
  `@StateObject` (`DiscoverFeedStore`, `SearchStore`).
- **App/activity-scoped holders** for genuinely global state — the **session**
  (auth), the **role/theme** flag, the **tab/deep-link router**, and the
  **upload-queue** and **calendar-sync** observers. iOS injects these once at
  `RootView`; on Android they are `@Singleton` (or `@ActivityRetainedScoped`)
  and either injected into ViewModels or exposed to Compose via a
  `CompositionLocal` for the handful of truly ambient ones (theme role).

**Events, not state, for one-shots.** SwiftUI re-derives navigation from state;
on Android we send navigation/toast/haptic as **one-shot events** through a
`Channel` → `Flow` the screen consumes in a `LaunchedEffect`, so they don't
re-fire on recomposition/rotation.

---

## 4. Data layer: repository seam

The iOS repository seam is the cleanest part of the app and we copy it directly.
Every repository is an **`interface`** with a **`Supabase*` implementation** and a
**`Fake*` implementation** for tests — exactly the iOS `protocol` + real + `Fake*`
pattern. Hilt binds the real impl in production, tests inject fakes.

```kotlin
interface BookingsRepository {
    suspend fun create(draft: BookingDraft): Booking
    suspend fun listForClient(): List<Booking>
    suspend fun listForArtist(): List<Booking>
    suspend fun cancel(id: String): Booking
}

@Singleton
class SupabaseBookingsRepository @Inject constructor(
    private val client: SupabaseClient,
    private val calendarSync: CalendarSyncService,
) : BookingsRepository { /* Postgrest + Functions.invoke("cancel-booking") */ }

@Module @InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindBookings(impl: SupabaseBookingsRepository): BookingsRepository
}
```

**14 repositories** map 1:1 (Bookings, Users, Messages, Requests, Reviews, Score,
Search, ArtistLinks, Packages, SavedArtists, Artists, ArtistMedia, Samples,
TechRider). See API_MAPPING.md for each repo's exact Supabase calls.

**Carry over these load-bearing iOS behaviours** (they are correctness, not style):

- **Lowercase every UUID** before a query (iOS does this everywhere).
- **Explicit column lists on `messages`** — a `select("*")` **403s** because
  `body_raw` is column-revoked (migration 0061). Always
  `select("id,thread_id,sender_id,body,sent_at")`.
- **PostgREST embeds** (`client:users!client_id(full_name)`) via
  `Columns.raw("*, client:users!client_id(full_name)")`.
- **Client-side position tracking** for storage inserts (query `MAX(position)+1`),
  **best-effort storage rollback** on insert failure, and **`23505` retry** on
  unique-position collisions.
- **RPC-atomic wizard writes**: `replace_packages` / `replace_samples` /
  `replace_tech_rider`.

---

## 5. Networking layer

There is **one** backend surface — Supabase — reached entirely through
**supabase-kt**. We do **not** add Retrofit/OkHttp; supabase-kt bundles a Ktor
client and covers PostgREST, Realtime (WebSocket), Storage, and Edge Functions.

```kotlin
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
) {
    install(Auth) { scheme = "in.artistant.app"; host = "login-callback" }
    install(Postgrest)
    install(Realtime)
    install(Storage)
    install(Functions)
}
```

- **Session persistence** is handled by supabase-kt (`SettingsSessionManager` →
  encrypted storage) with automatic token refresh — the analogue of the iOS
  Keychain + auto-refresh. Expose sign-in state as a `Flow` from
  `auth.sessionStatus`.
- **Tier guard (port verbatim):** a `prod` build flavor MUST point at the prod
  host `ouikzcxtetxjuxrygkur.supabase.co`, non-prod MUST NOT — assert at startup
  and crash on mismatch, exactly like iOS `assertBackendMatchesTier`.
- **Config → BuildConfig.** iOS uses xcconfig → Info.plist `$(VAR)` →
  `AppEnvironment`. Android uses **product flavors** (`dev`/`staging`/`prod`) +
  `BuildConfig` fields, with secrets read from `local.properties`/CI (never
  committed). `AppEnvironment` becomes a small `object` reading `BuildConfig`.

---

## 6. Error handling

- **Repositories throw** typed exceptions (sealed `AppError` hierarchy) or return
  `Result<T>` for expected-failure paths that the UI must branch on (mirrors the
  iOS enums like `.handleTaken`, `.alreadyReviewed`, `.notFoundOrUnauthorized`).
  Map PostgREST error codes: `PGRST116` → not-found/unauthorized, `23505` →
  unique-violation, `insufficient_privilege` → guarded-column write.
- **ViewModel catches**, folds into `UiState.error` (a user-facing string) +
  logs the real cause via Timber/Sentry. This mirrors the iOS
  `lastRefreshError`/`loadError`/`breakdownError` per-store pattern.
- **Optimistic writes with rollback** — the iOS pattern in RequestStore/SavedStore
  (write locally, revert on server rejection). Implement as: emit optimistic
  state → call repo → on failure re-emit prior state + surface error.
- **Surface backend guard errors truthfully.** The DB enforces self-booking,
  no-overlap (GiST), and the booking status state machine — inserts/updates throw.
  Catch and show a real message; never swallow.

---

## 7. Caching & offline strategy

**Match the iOS model: server-is-truth, prefs-as-mirror. No local database.**

The iOS app keeps an in-memory copy in each store + a **UserDefaults JSON mirror**
of server-id lists for a warm cold-launch, and an **on-disk cache for
wizard-media in progress**. There is *no* Room/SQLite and near-zero true offline.
We replicate exactly this:

- **In-memory** state in ViewModels/singletons (source of truth for a session).
- **DataStore (Preferences)** for the small persisted snapshots: role, session
  hints, search recents, calendar-sync config, and the v2 "server-id list"
  caches. This is the `Persistence` (`artistant.state.*`) namespace 1:1.
  `wipeAll()` → `dataStore.edit { it.clear() }` on delete-account.
- **`context.cacheDir/artist-wizard/`** for pending wizard media (the
  `WizardMediaCache` analogue): normalize photos via `Bitmap`, probe audio/video
  duration via `MediaMetadataRetriever`. System-evictable — handle file-missing.

> **Decision — no Room yet.** `// ponytail:` the iOS app shipped to production
> with zero offline DB and no user complaints; adding Room here would be building
> infrastructure for a requirement that doesn't exist. **Upgrade path:** if
> offline browsing/read-through caching becomes a real requirement, introduce
> Room as a read-through cache behind the existing repository interfaces — the
> seam already isolates it, so no ViewModel changes. Documented in
> RISKS_AND_DECISIONS.md.

---

## 8. Design system

The iOS design system is fully tokenized and ports cleanly to a Compose theme.
See SCREEN_INVENTORY.md §2 for the full token tables; the architecture points:

- **A single `ArtistantTheme` composable** wrapping `MaterialTheme` with custom
  token objects: `AppColors`, `AppType` (`FontFamily` per role), `Space`,
  `Size`, `Radii`, `AspectRatios`. Exposed via `CompositionLocal`s
  (`LocalAppColors`, etc.) so composables read `AppTheme.colors.brand` the way
  SwiftUI reads `Color.brand`.
- **Dark-only.** Force dark; do not implement a light scheme (iOS is dark-only).
- **Role-reactive accent.** iOS flips `AppTheme.role` (a static) to re-theme every
  brand surface: **client → acid lime `#C8FF00`**, **artist → electric violet
  `#7C5CFF`**. On Android, drive this from a `role` flag in the session/theme
  holder; `ArtistantTheme(role)` selects the accent set. Because a session is
  single-role, recomposing the tree on role change is fine.
- **Brand fonts** Instrument Serif (display/editorial), Geist (sans), Geist Mono
  (numerals) are **OFL** — drop the same `.ttf` into `res/font/` and build three
  `FontFamily`s. The "one italic accent word" headline → an `AnnotatedString`
  builder (`AppType.editorialHeadline`).
- **INR formatting** with `en_IN` grouping (lakh) + a compact `formatINRShort`
  (₹…K / ₹…L) — a pure util, port verbatim.
- **Custom drawing** (ScoreRing arc, Sparkline path, MonthCalendar grid,
  StatusTimeline, DateScroller) → Compose `Canvas`/`drawArc`/`Path` + custom
  `Layout`s. FlowLayout of chips → Compose `FlowRow` (built-in).
- **iOS-26 Liquid Glass** (`.glassEffect`, `.ultraThinMaterial`, minimizing glass
  tab bar) has **no 1:1 Android analogue** — approximate with translucent/blurred
  Material 3 surfaces (or a `RenderEffect` blur on API 31+). Documented as a
  deliberate visual divergence in RISKS_AND_DECISIONS.md.

---

## 9. Permissions

All iOS just-in-time permissions become Android runtime permissions via
`ActivityResultContracts.RequestPermission`, requested at point-of-use (matching
the iOS HIG discipline — never at launch):

| iOS | Android permission | When |
|---|---|---|
| `NSCameraUsageDescription` | `CAMERA` | wizard cover capture |
| `NSMicrophoneUsageDescription` | `RECORD_AUDIO` | video-with-sound capture |
| `NSLocationWhenInUse` | `ACCESS_COARSE/FINE_LOCATION` | "near me" / signup city |
| notifications | `POST_NOTIFICATIONS` (API 33+) | signup notif step |
| `NSCalendarsFullAccess` | `READ_CALENDAR` + `WRITE_CALENDAR` | Profile calendar-sync toggle |
| photo pick | *(none — Photo Picker)* | wizard/EPK photo pick |
| audio pick | *(none — SAF)* | samples pick |

Rationale strings live in code/resources (no Info.plist equivalent). Model a
small `PermissionsController` mirroring the iOS `PermissionsService`.

---

## 10. Background work

- **Upload queue → WorkManager.** The iOS `UploadQueue` hand-rolls persistence
  (Application-Support JSON snapshot), 3-attempt exponential backoff,
  crash-loop protection, and auth-gated resume. **WorkManager gives all of this
  for free**: `OneTimeWorkRequest` per media task chained into a batch, a
  `ForegroundInfo` for long uploads, `NetworkType.CONNECTED` constraint, built-in
  backoff, and survival across process death. Port the *policy* (foreign-user
  purge on account switch; don't flip `published=true` while any upload failed)
  as Worker logic. `// ponytail:` WorkManager replaces ~300 lines of manual queue.
- **No other background modes.** iOS declares **no** `UIBackgroundModes` (push is
  standard remote-notification; scoring/reminders are server cron). Android needs
  nothing beyond WorkManager + FCM — no foreground service except during an
  active upload batch.

---

## 11. Logging & analytics hooks

- **Logging:** Timber, `plant(DebugTree)` in debug only. Route nothing PII to logs.
- **Analytics (PostHog):** port the iOS wrapper exactly — an **event allowlist**
  (`app_open`, `signup_complete`, `booking_created`, `booking_paid`,
  `message_sent`), **non-PII properties only**, `identify(userId)` on sign-in,
  `reset()` on sign-out (DPDP §11). Dark until `POSTHOG_API_KEY` is set.
- **Crash (Sentry):** `sentry-android` with a `BeforeSend` that runs the **same
  pure PII-scrub regex** as iOS (`SentryConfig.scrub` — emails → phones →
  handle-URLs, order-sensitive). Opaque user id only. Dark until `SENTRY_DSN` set.
- Both wrappers behind a small interface so a no-op impl runs when keys are
  absent — mirrors the iOS `#if canImport` gating.

---

## 12. Testing strategy (summary; full plan in FEATURE_CHECKLIST.md)

- **Unit** (JUnit + MockK + Turbine): the pure logic the iOS app unit-tests —
  `Booking.compute`, score bands, redaction regexes, calendar planner, ISO8601
  round-trip, INR formatting, returning-login routing. Repositories tested
  against fakes; ViewModels tested by asserting `StateFlow` emissions (Turbine).
- **UI** (Compose UI Test + semantics): the analogue of the ~76 XCUITests — the
  `Fake*` repositories are swapped via a test Hilt module + launch args, exactly
  like the iOS `-uitest-*` harness.
- **Screenshot** (optional): Roborazzi/Paparazzi for the design-system components
  and key screens.

---

## 13. What we deliberately do NOT build

`// ponytail:` explicit non-goals, so a future session doesn't "helpfully" add them:

- **No Kotlin Multiplatform / shared module** — user requirement: fully native,
  separate app. (Backend is the only shared thing.)
- **No Room / offline-first** initially — iOS proved it unnecessary (§7).
- **No multi-module Gradle** up front — start single-module, package-by-feature
  (see PROJECT_STRUCTURE.md); promote to modules only when build times hurt.
- **No custom DI framework / service locator** — Hilt.
- **No Retrofit** — supabase-kt is the only network client.
- **No payments code in v1** — matchmaker model; Play Billing is a dormant seam,
  same as the iOS StoreKit seam.
- **No generic "BaseViewModel"/"BaseScreen" abstractions** — one interface with
  one implementation is not an abstraction worth having.

---

## 14. Recommended libraries (Phase 9) — with rationale

| Area | Library | Version line | Why this one |
|---|---|---|---|
| Compose | `androidx.compose:compose-bom` + Material 3 | 2024.10+ BOM | The standard; BOM keeps Compose artifacts aligned. |
| Navigation | `androidx.navigation:navigation-compose` | 2.8+ | Type-safe routes (serializable) = the `Route` enum analogue. |
| DI | `com.google.dagger:hilt-android` | 2.52+ | De-facto Android DI; ViewModel + WorkManager integration. |
| Backend | `io.github.jan-tennert.supabase:{auth-kt,postgrest-kt,realtime-kt,storage-kt,functions-kt}` | 3.x | The Kotlin Supabase SDK; 1:1 with supabase-swift. |
| HTTP engine | `io.ktor:ktor-client-okhttp` | (matches supabase-kt) | Ktor engine supabase-kt needs; OkHttp engine is robust on Android. |
| Serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.7+ | supabase-kt's native (de)serializer; replaces `Codable`. |
| Coroutines | `kotlinx-coroutines-android` | 1.9+ | Async foundation. |
| Images | `io.coil-kt.coil3:coil-compose` | 3.x | Compose-native `AsyncImage`; replaces SwiftUI `AsyncImage`; supports video-frame + GIF. |
| Video/audio | `androidx.media3:{media3-exoplayer,media3-ui,media3-transformer}` | 1.4+ | Playback (AVPlayer/AVAudioPlayer) + trim/transcode (AVAssetExportSession). |
| Camera | `androidx.camera:camera-*` (CameraX) | 1.4+ | Modern camera; replaces `UIImagePickerController`. Or capture intents for the simple path. |
| Media pick | `androidx.activity` Photo Picker (`PickVisualMedia`) | (activity 1.9+) | No-permission image/video pick; replaces PhotosUI. |
| Background | `androidx.work:work-runtime-ktx` | 2.9+ | Upload queue; persistence + retry built in. |
| Local storage | `androidx.datastore:datastore-preferences` | 1.1+ | Replaces UserDefaults/Persistence. |
| Push | `com.google.firebase:firebase-messaging` (Firebase BoM) | BoM 33+ | APNs → FCM. Requires backend send-push change. |
| Billing | `com.android.billingclient:billing-ktx` | 7.x | StoreKit → Play Billing. Dormant in v1. |
| Auth (Google) | `androidx.credentials:credentials` + `googleid` | 1.3+ | Credential Manager Google sign-in → ID token. |
| Auth (Apple/web) | `androidx.browser:browser` (Custom Tabs) | 1.8+ | Apple OAuth web flow (no native SDK). |
| Location | `com.google.android.gms:play-services-location` | 21+ | FusedLocationProvider; replaces CoreLocation. |
| Analytics | `com.posthog:posthog-android` | 3.x | Same SDK family as iOS; dark-until-key. |
| Crash | `io.sentry:sentry-android` | 7.x | Same as iOS; `BeforeSend` PII scrub. |
| Logging | `com.jakewharton.timber:timber` | 5.x | Tiny idiomatic logger. |
| Test | `junit4`, `io.mockk:mockk`, `app.cash.turbine:turbine`, `androidx.compose.ui:ui-test-junit4` | current | Unit + Flow + Compose UI. |
| Test (screenshot, opt.) | `io.github.takahirom.roborazzi` | current | Design-system regression. |

> **Maps not needed.** The iOS app uses **no** MapKit — location is a city string
> + a "near me" filter, not a map view. So **no Google Maps SDK** is required.
> (Listed here because Phase 9 asks; the answer is "omit it.")

**Build setup:** Gradle KTS, a `libs.versions.toml` version catalog, and the
Compose compiler via the Kotlin 2.0 `compose` plugin. KSP (not kapt) for Hilt.
